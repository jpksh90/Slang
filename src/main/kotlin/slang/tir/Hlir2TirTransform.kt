package slang.tir

import slang.common.CodeInfo
import slang.common.Result
import slang.common.Transform
import slang.hlir.Expr
import slang.hlir.Operator
import slang.hlir.ProgramUnit
import slang.hlir.Stmt
import slang.parser.CompilerError
import slang.typeinfer.SlangType
import slang.typeinfer.TypeEnv
import slang.typeinfer.TypeError
import slang.typeinfer.TypeScheme
import slang.typeinfer.freeVars
import slang.typeinfer.prune
import slang.typeinfer.unify

/**
 * Lowers an HLIR [ProgramUnit] into a typed [TirProgramUnit] by running
 * Hindley-Milner type inference and attaching resolved types to every node.
 *
 * The lowering re-walks the HLIR AST with a fresh [HindleyMilnerInference]
 * instance, mirroring the inference algorithm but also building the TIR tree.
 */
class Hlir2TirTransform : Transform<ProgramUnit, TirProgramUnit> {
    override fun transform(input: ProgramUnit): Result<TirProgramUnit, List<CompilerError>> {
        val lowering = TirLowering()
        val tir = lowering.lowerProgram(input)
        val errors = lowering.errors
        return if (errors.isEmpty()) {
            Result.ok(tir)
        } else {
            Result.err(errors.map { CompilerError(it.location, it.message ?: "Type error") })
        }
    }
}

/**
 * Stateful lowering engine. Mirrors [HindleyMilnerInference] but produces
 * [TirNode] trees with resolved [SlangType] on every node.
 */
class TirLowering {
    private var nextId = 0
    val errors = mutableListOf<TypeError>()

    private fun fresh(): SlangType.TVar = SlangType.TVar(nextId++)

    // ---- Generalize / Instantiate (same as HindleyMilnerInference) ----

    private fun generalize(
        env: TypeEnv,
        t: SlangType,
    ): TypeScheme {
        val envFree = env.freeVars()
        val typeVars = freeVars(t) - envFree
        return TypeScheme(typeVars, t)
    }

    private fun instantiate(scheme: TypeScheme): SlangType {
        val subst = scheme.vars.associateWith { fresh() as SlangType }
        return applySubst(subst, scheme.type)
    }

    private fun applySubst(
        subst: Map<Int, SlangType>,
        t: SlangType,
    ): SlangType {
        val p = prune(t)
        return when (p) {
            is SlangType.TVar -> subst[p.id] ?: p
            is SlangType.TFun -> SlangType.TFun(p.params.map { applySubst(subst, it) }, applySubst(subst, p.ret))
            is SlangType.TArray -> SlangType.TArray(applySubst(subst, p.elem))
            is SlangType.TRef -> SlangType.TRef(applySubst(subst, p.inner))
            is SlangType.TRecord -> SlangType.TRecord(p.fields.mapValues { applySubst(subst, it.value) })
            else -> p
        }
    }

    private fun safeUnify(
        a: SlangType,
        b: SlangType,
        location: CodeInfo,
    ) {
        try {
            unify(a, b, location)
        } catch (e: TypeError) {
            errors.add(e)
        }
    }

    /** Resolve a type to its fully pruned form for storage in TIR nodes. */
    private fun resolve(t: SlangType): SlangType = prune(t)

    // ---- Program ----

    fun lowerProgram(program: ProgramUnit): TirProgramUnit {
        var env = TypeEnv()
        val modules =
            program.stmt.map { module ->
                val moduleMain = module.functions.find { it.name == "__module__main__" }
                val tirFunctions = mutableListOf<TirStmt.Function>()

                // First pass: register and lower all top-level functions (excluding __module__main__)
                for (fn in module.functions) {
                    if (fn !== moduleMain) {
                        val (newEnv, tirFn) = lowerFunctionDecl(fn, env)
                        env = newEnv
                        tirFunctions.add(tirFn)
                    }
                }

                // Second pass: lower the module main body
                if (moduleMain != null) {
                    val (_, tirMain) = lowerFunctionDecl(moduleMain, env)
                    tirFunctions.add(0, tirMain)
                }

                val tirInlined = module.inlinedFuncs.map { lowerExpr(it, env) as TirExpr.InlinedFunction }

                TirModule(tirFunctions, tirInlined).also { it.codeInfo = module.codeInfo }
            }
        return TirProgramUnit(modules).also { it.codeInfo = program.codeInfo }
    }

    // ---- Functions ----

    private fun lowerFunctionDecl(
        fn: Stmt.Function,
        env: TypeEnv,
    ): Pair<TypeEnv, TirStmt.Function> {
        val paramTypes = fn.params.map { fresh() as SlangType }
        val retType: SlangType = fresh()
        val funType = SlangType.TFun(paramTypes, retType)

        val innerEnv =
            env
                .extend(fn.name, TypeScheme(emptySet(), funType))
                .extend(fn.params.zip(paramTypes).map { (n, t) -> n to TypeScheme(emptySet(), t) })

        val (tirBlock, bodyType) = lowerBlock(fn.body, innerEnv)
        safeUnify(retType, bodyType, fn.codeInfo)

        val scheme = generalize(env, funType)
        val newEnv = env.extend(fn.name, scheme)

        val typedParams = fn.params.zip(paramTypes).map { (n, t) -> n to resolve(t) }
        val resolvedFunType = resolve(funType)
        val tirFn =
            TirStmt.Function(
                name = fn.name,
                params = typedParams,
                body = tirBlock,
                returnType = resolve(retType),
                type = resolvedFunType,
            )
        tirFn.codeInfo = fn.codeInfo
        return Pair(newEnv, tirFn)
    }

    // ---- Blocks ----

    private fun lowerBlock(
        block: Stmt.BlockStmt,
        env: TypeEnv,
    ): Pair<TirStmt.BlockStmt, SlangType> {
        var currentEnv = env
        var resultType: SlangType = SlangType.TUnit
        val tirStmts = mutableListOf<TirStmt>()
        for (stmt in block.stmts) {
            val (newEnv, tirStmt, ty) = lowerStmt(stmt, currentEnv)
            currentEnv = newEnv
            resultType = ty
            tirStmts.add(tirStmt)
        }
        val tirBlock = TirStmt.BlockStmt(tirStmts, resolve(resultType))
        tirBlock.codeInfo = block.codeInfo
        return Pair(tirBlock, resultType)
    }

    // ---- Statements ----

    private data class StmtResult(
        val env: TypeEnv,
        val tirStmt: TirStmt,
        val type: SlangType,
    )

    private fun lowerStmt(
        stmt: Stmt,
        env: TypeEnv,
    ): StmtResult =
        when (stmt) {
            is Stmt.LetStmt -> {
                val tirExpr = lowerExpr(stmt.expr, env)
                val exprType = tirExpr.type
                val scheme = generalize(env, exprType)
                val tirStmt = TirStmt.LetStmt(stmt.name, tirExpr)
                tirStmt.codeInfo = stmt.codeInfo
                StmtResult(env.extend(stmt.name, scheme), tirStmt, SlangType.TUnit)
            }

            is Stmt.AssignStmt -> {
                val tirLhs = lowerExpr(stmt.lhs, env)
                val tirRhs = lowerExpr(stmt.expr, env)
                safeUnify(tirLhs.type, tirRhs.type, stmt.codeInfo)
                val tirStmt = TirStmt.AssignStmt(tirLhs, tirRhs)
                tirStmt.codeInfo = stmt.codeInfo
                StmtResult(env, tirStmt, SlangType.TUnit)
            }

            is Stmt.ExprStmt -> {
                val tirExpr = lowerExpr(stmt.expr, env)
                val tirStmt = TirStmt.ExprStmt(tirExpr, resolve(tirExpr.type))
                tirStmt.codeInfo = stmt.codeInfo
                StmtResult(env, tirStmt, tirExpr.type)
            }

            is Stmt.ReturnStmt -> {
                val tirExpr = lowerExpr(stmt.expr, env)
                val tirStmt = TirStmt.ReturnStmt(tirExpr, resolve(tirExpr.type))
                tirStmt.codeInfo = stmt.codeInfo
                StmtResult(env, tirStmt, tirExpr.type)
            }

            is Stmt.PrintStmt -> {
                val tirArgs = stmt.args.map { lowerExpr(it, env) }
                val tirStmt = TirStmt.PrintStmt(tirArgs)
                tirStmt.codeInfo = stmt.codeInfo
                StmtResult(env, tirStmt, SlangType.TUnit)
            }

            is Stmt.IfStmt -> {
                val tirCond = lowerExpr(stmt.condition, env)
                safeUnify(tirCond.type, SlangType.TBool, stmt.codeInfo)
                val (tirThen, thenType) = lowerBlock(stmt.thenBody, env)
                val (tirElse, elseType) = lowerBlock(stmt.elseBody, env)
                safeUnify(thenType, elseType, stmt.codeInfo)
                val tirStmt = TirStmt.IfStmt(tirCond, tirThen, tirElse, resolve(thenType))
                tirStmt.codeInfo = stmt.codeInfo
                StmtResult(env, tirStmt, thenType)
            }

            is Stmt.WhileStmt -> {
                val tirCond = lowerExpr(stmt.condition, env)
                safeUnify(tirCond.type, SlangType.TBool, stmt.codeInfo)
                val (tirBody, _) = lowerBlock(stmt.body, env)
                val tirStmt = TirStmt.WhileStmt(tirCond, tirBody)
                tirStmt.codeInfo = stmt.codeInfo
                StmtResult(env, tirStmt, SlangType.TUnit)
            }

            is Stmt.BlockStmt -> {
                val (tirBlock, blockType) = lowerBlock(stmt, env)
                StmtResult(env, tirBlock, blockType)
            }

            is Stmt.Function -> {
                val (newEnv, tirFn) = lowerFunctionDecl(stmt, env)
                StmtResult(newEnv, tirFn, SlangType.TUnit)
            }

            is Stmt.DerefStmt -> {
                val tirLhs = lowerExpr(stmt.lhs, env)
                val tirRhs = lowerExpr(stmt.rhs, env)
                val inner: SlangType = fresh()
                safeUnify(tirLhs.type, SlangType.TRef(inner), stmt.codeInfo)
                safeUnify(inner, tirRhs.type, stmt.codeInfo)
                val tirStmt = TirStmt.DerefStmt(tirLhs, tirRhs)
                tirStmt.codeInfo = stmt.codeInfo
                StmtResult(env, tirStmt, SlangType.TUnit)
            }

            is Stmt.StructStmt -> {
                val tirFields = HashMap<String, TirExpr>()
                val fieldTypes = mutableMapOf<String, SlangType>()
                for ((name, expr) in stmt.fields) {
                    val tirExpr = lowerExpr(expr, env)
                    tirFields[name] = tirExpr
                    fieldTypes[name] = tirExpr.type
                }
                val recordType = SlangType.TRecord(fieldTypes)
                val scheme = generalize(env, recordType)
                val tirFunctions = stmt.functions.map { lowerFunctionDecl(it, env).second }
                val tirStmt = TirStmt.StructStmt(stmt.id, tirFunctions, tirFields)
                tirStmt.codeInfo = stmt.codeInfo
                StmtResult(env.extend(stmt.id, scheme), tirStmt, SlangType.TUnit)
            }

            is Stmt.Break -> {
                val tirStmt = TirStmt.Break
                StmtResult(env, tirStmt, SlangType.TUnit)
            }

            is Stmt.Continue -> {
                val tirStmt = TirStmt.Continue
                StmtResult(env, tirStmt, SlangType.TUnit)
            }
        }

    // ---- Expressions ----

    private fun lowerExpr(
        expr: Expr,
        env: TypeEnv,
    ): TirExpr {
        val tirExpr: TirExpr =
            when (expr) {
                is Expr.NumberLiteral -> TirExpr.NumberLiteral(expr.value)
                is Expr.BoolLiteral -> TirExpr.BoolLiteral(expr.value)
                is Expr.StringLiteral -> TirExpr.StringLiteral(expr.value)
                is Expr.NoneValue -> TirExpr.NoneValue()

                is Expr.VarExpr -> {
                    val scheme = env[expr.name]
                    val ty =
                        if (scheme != null) {
                            instantiate(scheme)
                        } else {
                            errors.add(TypeError(expr.codeInfo, "Undefined variable: ${expr.name}"))
                            fresh()
                        }
                    TirExpr.VarExpr(expr.name, resolve(ty))
                }

                is Expr.ReadInputExpr -> TirExpr.ReadInputExpr(resolve(fresh()))

                is Expr.BinaryExpr -> lowerBinaryExpr(expr, env)

                is Expr.IfExpr -> {
                    val tirCond = lowerExpr(expr.condition, env)
                    safeUnify(tirCond.type, SlangType.TBool, expr.codeInfo)
                    val tirThen = lowerExpr(expr.thenExpr, env)
                    val tirElse = lowerExpr(expr.elseExpr, env)
                    safeUnify(tirThen.type, tirElse.type, expr.codeInfo)
                    TirExpr.IfExpr(tirCond, tirThen, tirElse, resolve(tirThen.type))
                }

                is Expr.ParenExpr -> {
                    val tirInner = lowerExpr(expr.expr, env)
                    TirExpr.ParenExpr(tirInner, resolve(tirInner.type))
                }

                is Expr.InlinedFunction -> {
                    val paramTypes = expr.params.map { fresh() as SlangType }
                    val innerEnv =
                        env.extend(
                            expr.params.zip(paramTypes).map { (n, t) -> n to TypeScheme(emptySet(), t) },
                        )
                    val (tirBody, bodyType) = lowerBlock(expr.body, innerEnv)
                    val funType = SlangType.TFun(paramTypes, bodyType)
                    val typedParams = expr.params.zip(paramTypes).map { (n, t) -> n to resolve(t) }
                    TirExpr.InlinedFunction(typedParams, tirBody, resolve(funType))
                }

                is Expr.NamedFunctionCall -> {
                    val funScheme = env[expr.name]
                    if (funScheme == null) {
                        errors.add(TypeError(expr.codeInfo, "Undefined function: ${expr.name}"))
                        val tirArgs = expr.arguments.map { lowerExpr(it, env) }
                        TirExpr.NamedFunctionCall(expr.name, tirArgs, resolve(fresh()))
                    } else {
                        val instType = instantiate(funScheme)
                        val tirArgs = expr.arguments.map { lowerExpr(it, env) }
                        val argTypes = tirArgs.map { it.type }
                        val retType: SlangType = fresh()
                        safeUnify(instType, SlangType.TFun(argTypes, retType), expr.codeInfo)
                        TirExpr.NamedFunctionCall(expr.name, tirArgs, resolve(retType))
                    }
                }

                is Expr.ExpressionFunctionCall -> {
                    val tirTarget = lowerExpr(expr.target, env)
                    val tirArgs = expr.arguments.map { lowerExpr(it, env) }
                    val argTypes = tirArgs.map { it.type }
                    val retType: SlangType = fresh()
                    safeUnify(tirTarget.type, SlangType.TFun(argTypes, retType), expr.codeInfo)
                    TirExpr.ExpressionFunctionCall(tirTarget, tirArgs, resolve(retType))
                }

                is Expr.ArrayInit -> {
                    val elemType: SlangType = fresh()
                    val tirElems =
                        expr.elements.map { e ->
                            val tirE = lowerExpr(e, env)
                            safeUnify(elemType, tirE.type, expr.codeInfo)
                            tirE
                        }
                    TirExpr.ArrayInit(tirElems, resolve(SlangType.TArray(elemType)))
                }

                is Expr.ArrayAccess -> {
                    val tirArray = lowerExpr(expr.array, env)
                    val tirIndex = lowerExpr(expr.index, env)
                    val elemType: SlangType = fresh()
                    safeUnify(tirArray.type, SlangType.TArray(elemType), expr.codeInfo)
                    safeUnify(tirIndex.type, SlangType.TNum, expr.codeInfo)
                    TirExpr.ArrayAccess(tirArray, tirIndex, resolve(elemType))
                }

                is Expr.Record -> {
                    val tirFields =
                        expr.expression.map { (name, e) ->
                            name to lowerExpr(e, env)
                        }
                    val fieldTypes = tirFields.associate { (name, tirE) -> name to tirE.type }
                    TirExpr.Record(tirFields, resolve(SlangType.TRecord(fieldTypes)))
                }

                is Expr.FieldAccess -> {
                    val tirLhs = lowerExpr(expr.lhs, env)
                    val fieldName = (expr.rhs as Expr.VarExpr).name
                    val fieldType: SlangType = fresh()
                    val pruned = prune(tirLhs.type)
                    if (pruned is SlangType.TRecord) {
                        val ft = pruned.fields[fieldName]
                        if (ft != null) {
                            safeUnify(fieldType, ft, expr.codeInfo)
                        } else {
                            errors.add(TypeError(expr.codeInfo, "Record has no field '$fieldName'"))
                        }
                    }
                    val tirRhs = TirExpr.VarExpr(fieldName, resolve(fieldType))
                    tirRhs.codeInfo = expr.rhs.codeInfo
                    TirExpr.FieldAccess(tirLhs, tirRhs, resolve(fieldType))
                }

                is Expr.RefExpr -> {
                    val tirInner = lowerExpr(expr.expr, env)
                    TirExpr.RefExpr(tirInner, resolve(SlangType.TRef(tirInner.type)))
                }

                is Expr.DerefExpr -> {
                    val tirInner = lowerExpr(expr.expr, env)
                    val innerType: SlangType = fresh()
                    safeUnify(tirInner.type, SlangType.TRef(innerType), expr.codeInfo)
                    TirExpr.DerefExpr(tirInner, resolve(innerType))
                }
            }
        tirExpr.codeInfo = expr.codeInfo
        return tirExpr
    }

    // ---- Binary expressions ----

    private fun lowerBinaryExpr(
        expr: Expr.BinaryExpr,
        env: TypeEnv,
    ): TirExpr {
        val tirLeft = lowerExpr(expr.left, env)
        val tirRight = lowerExpr(expr.right, env)

        val resultType: SlangType =
            when (expr.op) {
                Operator.PLUS -> {
                    val rt: SlangType = fresh()
                    safeUnify(tirLeft.type, rt, expr.codeInfo)
                    safeUnify(tirRight.type, rt, expr.codeInfo)
                    rt
                }
                Operator.MINUS, Operator.TIMES, Operator.DIV, Operator.MOD -> {
                    safeUnify(tirLeft.type, SlangType.TNum, expr.codeInfo)
                    safeUnify(tirRight.type, SlangType.TNum, expr.codeInfo)
                    SlangType.TNum
                }
                Operator.LT, Operator.GT, Operator.LEQ, Operator.GEQ -> {
                    safeUnify(tirLeft.type, SlangType.TNum, expr.codeInfo)
                    safeUnify(tirRight.type, SlangType.TNum, expr.codeInfo)
                    SlangType.TBool
                }
                Operator.EQ, Operator.NEQ -> {
                    safeUnify(tirLeft.type, tirRight.type, expr.codeInfo)
                    SlangType.TBool
                }
                Operator.AND, Operator.OR -> {
                    safeUnify(tirLeft.type, SlangType.TBool, expr.codeInfo)
                    safeUnify(tirRight.type, SlangType.TBool, expr.codeInfo)
                    SlangType.TBool
                }
            }
        return TirExpr.BinaryExpr(tirLeft, expr.op, tirRight, resolve(resultType))
    }
}
