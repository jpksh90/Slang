package slang.typeinfer

import slang.common.CodeInfo
import slang.common.Result
import slang.hlir.Expr
import slang.hlir.Operator
import slang.hlir.ProgramUnit
import slang.hlir.Stmt

/**
 * Typing environment: maps variable names to polymorphic type schemes.
 * Immutable — extending returns a new environment.
 */
class TypeEnv(
    private val bindings: Map<String, TypeScheme> = emptyMap(),
) {
    operator fun get(name: String): TypeScheme? = bindings[name]

    fun extend(
        name: String,
        scheme: TypeScheme,
    ): TypeEnv = TypeEnv(bindings + (name to scheme))

    fun extend(pairs: List<Pair<String, TypeScheme>>): TypeEnv = TypeEnv(bindings + pairs)

    /** Free type-variable ids across all schemes in the environment. */
    fun freeVars(): Set<Int> = bindings.values.flatMap { freeVars(it.type) - it.vars }.toSet()
}

/**
 * Hindley-Milner type inference engine for Slang HLIR.
 *
 * Implements Algorithm W with let-polymorphism.
 */
class HindleyMilnerInference {
    private var nextId = 0
    private val errors = mutableListOf<TypeError>()

    fun fresh(): SlangType.TVar = SlangType.TVar(nextId++)

    // ---- Generalize / Instantiate ----

    /** Generalize a type w.r.t. variables NOT free in the environment. */
    fun generalize(
        env: TypeEnv,
        t: SlangType,
    ): TypeScheme {
        val envFree = env.freeVars()
        val typeVars = freeVars(t) - envFree
        return TypeScheme(typeVars, t)
    }

    /** Instantiate a type scheme with fresh variables. */
    fun instantiate(scheme: TypeScheme): SlangType {
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

    // ---- Public entry point ----

    fun inferProgram(program: ProgramUnit): List<TypeError> {
        errors.clear()
        var env = TypeEnv()
        for (module in program.stmt) {
            // First pass: register all top-level functions (excluding __module__main__)
            val moduleMain = module.functions.find { it.name == "__module__main__" }
            for (fn in module.functions) {
                if (fn !== moduleMain) {
                    env = inferFunctionDecl(fn, env)
                }
            }
            // Second pass: infer the module main body
            if (moduleMain != null) {
                inferBlock(moduleMain.body, env)
            }
        }
        return errors
    }

    // ---- Functions ----

    private fun inferFunctionDecl(
        fn: Stmt.Function,
        env: TypeEnv,
    ): TypeEnv {
        val paramTypes = fn.params.map { fresh() as SlangType }
        val retType: SlangType = fresh()
        val funType = SlangType.TFun(paramTypes, retType)

        // Extend env with the function name (monomorphic, for recursion)
        val innerEnv =
            env
                .extend(fn.name, TypeScheme(emptySet(), funType))
                .extend(fn.params.zip(paramTypes).map { (n, t) -> n to TypeScheme(emptySet(), t) })

        val bodyType = inferBlock(fn.body, innerEnv)
        safeUnify(retType, bodyType, fn.codeInfo)

        // Generalize in the outer env
        val scheme = generalize(env, funType)
        return env.extend(fn.name, scheme)
    }

    // ---- Statements ----

    /**
     * Infer types for a block and return the type of the last expression / return.
     * For blocks that produce no value, returns TUnit.
     */
    private fun inferBlock(
        block: Stmt.BlockStmt,
        env: TypeEnv,
    ): SlangType {
        var currentEnv = env
        var resultType: SlangType = SlangType.TUnit
        for (stmt in block.stmts) {
            val (newEnv, ty) = inferStmt(stmt, currentEnv)
            currentEnv = newEnv
            resultType = ty
        }
        return resultType
    }

    /**
     * Returns (possibly extended env, type produced by this statement).
     * For statements that don't produce a value the type is TUnit.
     * For return statements the type is the type of the returned expression.
     */
    private fun inferStmt(
        stmt: Stmt,
        env: TypeEnv,
    ): Pair<TypeEnv, SlangType> =
        when (stmt) {
            is Stmt.LetStmt -> {
                val exprType = inferExpr(stmt.expr, env)
                val scheme = generalize(env, exprType)
                Pair(env.extend(stmt.name, scheme), SlangType.TUnit)
            }

            is Stmt.AssignStmt -> {
                val lhsType = inferExpr(stmt.lhs, env)
                val rhsType = inferExpr(stmt.expr, env)
                safeUnify(lhsType, rhsType, stmt.codeInfo)
                Pair(env, SlangType.TUnit)
            }

            is Stmt.ExprStmt -> {
                val t = inferExpr(stmt.expr, env)
                Pair(env, t)
            }

            is Stmt.ReturnStmt -> {
                val t = inferExpr(stmt.expr, env)
                Pair(env, t)
            }

            is Stmt.PrintStmt -> {
                stmt.args.forEach { inferExpr(it, env) }
                Pair(env, SlangType.TUnit)
            }

            is Stmt.IfStmt -> {
                val condType = inferExpr(stmt.condition, env)
                safeUnify(condType, SlangType.TBool, stmt.codeInfo)
                val thenType = inferBlock(stmt.thenBody, env)
                val elseType = inferBlock(stmt.elseBody, env)
                safeUnify(thenType, elseType, stmt.codeInfo)
                Pair(env, thenType)
            }

            is Stmt.WhileStmt -> {
                val condType = inferExpr(stmt.condition, env)
                safeUnify(condType, SlangType.TBool, stmt.codeInfo)
                inferBlock(stmt.body, env)
                Pair(env, SlangType.TUnit)
            }

            is Stmt.BlockStmt -> {
                val t = inferBlock(stmt, env)
                Pair(env, t)
            }

            is Stmt.Function -> {
                val newEnv = inferFunctionDecl(stmt, env)
                Pair(newEnv, SlangType.TUnit)
            }

            is Stmt.DerefStmt -> {
                val refType = inferExpr(stmt.lhs, env)
                val valType = inferExpr(stmt.rhs, env)
                val inner: SlangType = fresh()
                safeUnify(refType, SlangType.TRef(inner), stmt.codeInfo)
                safeUnify(inner, valType, stmt.codeInfo)
                Pair(env, SlangType.TUnit)
            }

            is Stmt.StructStmt -> {
                val fieldTypes = stmt.fields.mapValues { inferExpr(it.value, env) }
                val recordType = SlangType.TRecord(fieldTypes)
                val scheme = generalize(env, recordType)
                Pair(env.extend(stmt.id, scheme), SlangType.TUnit)
            }

            is Stmt.Break -> Pair(env, SlangType.TUnit)
            is Stmt.Continue -> Pair(env, SlangType.TUnit)
        }

    // ---- Expressions ----

    private fun inferExpr(
        expr: Expr,
        env: TypeEnv,
    ): SlangType =
        when (expr) {
            is Expr.NumberLiteral -> SlangType.TNum
            is Expr.BoolLiteral -> SlangType.TBool
            is Expr.StringLiteral -> SlangType.TString
            is Expr.NoneValue -> SlangType.TNone

            is Expr.VarExpr -> {
                val scheme = env[expr.name]
                if (scheme != null) {
                    instantiate(scheme)
                } else {
                    errors.add(TypeError(expr.codeInfo, "Undefined variable: ${expr.name}"))
                    fresh()
                }
            }

            is Expr.ReadInputExpr -> fresh() // could be Num or String

            is Expr.BinaryExpr -> inferBinaryExpr(expr, env)

            is Expr.IfExpr -> {
                val condType = inferExpr(expr.condition, env)
                safeUnify(condType, SlangType.TBool, expr.codeInfo)
                val thenType = inferExpr(expr.thenExpr, env)
                val elseType = inferExpr(expr.elseExpr, env)
                safeUnify(thenType, elseType, expr.codeInfo)
                thenType
            }

            is Expr.ParenExpr -> inferExpr(expr.expr, env)

            is Expr.InlinedFunction -> {
                val paramTypes = expr.params.map { fresh() as SlangType }
                val innerEnv =
                    env.extend(
                        expr.params.zip(paramTypes).map { (n, t) -> n to TypeScheme(emptySet(), t) },
                    )
                val bodyType = inferBlock(expr.body, innerEnv)
                SlangType.TFun(paramTypes, bodyType)
            }

            is Expr.NamedFunctionCall -> {
                val funType = env[expr.name]
                if (funType == null) {
                    errors.add(TypeError(expr.codeInfo, "Undefined function: ${expr.name}"))
                    fresh()
                } else {
                    val instType = instantiate(funType)
                    val argTypes = expr.arguments.map { inferExpr(it, env) }
                    val retType: SlangType = fresh()
                    safeUnify(instType, SlangType.TFun(argTypes, retType), expr.codeInfo)
                    retType
                }
            }

            is Expr.ExpressionFunctionCall -> {
                val targetType = inferExpr(expr.target, env)
                val argTypes = expr.arguments.map { inferExpr(it, env) }
                val retType: SlangType = fresh()
                safeUnify(targetType, SlangType.TFun(argTypes, retType), expr.codeInfo)
                retType
            }

            is Expr.ArrayInit -> {
                val elemType: SlangType = fresh()
                for (el in expr.elements) {
                    val t = inferExpr(el, env)
                    safeUnify(elemType, t, expr.codeInfo)
                }
                SlangType.TArray(elemType)
            }

            is Expr.ArrayAccess -> {
                val arrType = inferExpr(expr.array, env)
                val idxType = inferExpr(expr.index, env)
                val elemType: SlangType = fresh()
                safeUnify(arrType, SlangType.TArray(elemType), expr.codeInfo)
                safeUnify(idxType, SlangType.TNum, expr.codeInfo)
                elemType
            }

            is Expr.Record -> {
                val fieldTypes = expr.expression.associate { (name, e) -> name to inferExpr(e, env) }
                SlangType.TRecord(fieldTypes)
            }

            is Expr.FieldAccess -> {
                val recordType = inferExpr(expr.lhs, env)
                // We know rhs is always VarExpr (from the AST builder)
                val fieldName = (expr.rhs as Expr.VarExpr).name
                val fieldType: SlangType = fresh()
                // For records, we need structural access; try to unify if already a record
                val pruned = prune(recordType)
                if (pruned is SlangType.TRecord) {
                    val ft = pruned.fields[fieldName]
                    if (ft != null) {
                        safeUnify(fieldType, ft, expr.codeInfo)
                    } else {
                        errors.add(TypeError(expr.codeInfo, "Record has no field '$fieldName'"))
                    }
                }
                // If it's a type variable, we can't know the fields yet — return fresh
                fieldType
            }

            is Expr.RefExpr -> {
                val innerType = inferExpr(expr.expr, env)
                SlangType.TRef(innerType)
            }

            is Expr.DerefExpr -> {
                val refType = inferExpr(expr.expr, env)
                val innerType: SlangType = fresh()
                safeUnify(refType, SlangType.TRef(innerType), expr.codeInfo)
                innerType
            }
        }

    // ---- Binary expressions ----

    private fun inferBinaryExpr(
        expr: Expr.BinaryExpr,
        env: TypeEnv,
    ): SlangType {
        val leftType = inferExpr(expr.left, env)
        val rightType = inferExpr(expr.right, env)

        return when (expr.op) {
            Operator.PLUS -> {
                // PLUS works on Num+Num or String+String; default to unifying both sides
                val resultType: SlangType = fresh()
                safeUnify(leftType, resultType, expr.codeInfo)
                safeUnify(rightType, resultType, expr.codeInfo)
                resultType
            }
            Operator.MINUS, Operator.TIMES, Operator.DIV, Operator.MOD -> {
                safeUnify(leftType, SlangType.TNum, expr.codeInfo)
                safeUnify(rightType, SlangType.TNum, expr.codeInfo)
                SlangType.TNum
            }
            Operator.LT, Operator.GT, Operator.LEQ, Operator.GEQ -> {
                safeUnify(leftType, SlangType.TNum, expr.codeInfo)
                safeUnify(rightType, SlangType.TNum, expr.codeInfo)
                SlangType.TBool
            }
            Operator.EQ, Operator.NEQ -> {
                safeUnify(leftType, rightType, expr.codeInfo)
                SlangType.TBool
            }
            Operator.AND, Operator.OR -> {
                safeUnify(leftType, SlangType.TBool, expr.codeInfo)
                safeUnify(rightType, SlangType.TBool, expr.codeInfo)
                SlangType.TBool
            }
        }
    }

    // ---- Helpers ----

    /** Unify with error collection rather than throwing. */
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
}

/**
 * Convenience function: run type inference on a program, returning errors.
 */
fun typeCheck(program: ProgramUnit): List<TypeError> = HindleyMilnerInference().inferProgram(program)

/**
 * Pipeline-compatible transform that runs type inference.
 * Passes the ProgramUnit through unchanged on success.
 */
class TypeCheckTransform : slang.common.Transform<ProgramUnit, ProgramUnit> {
    override fun transform(input: ProgramUnit): Result<ProgramUnit, List<slang.parser.CompilerError>> {
        val errors = typeCheck(input)
        return if (errors.isEmpty()) {
            Result.ok(input)
        } else {
            Result.err(errors.map { slang.parser.CompilerError(it.location, it.message ?: "Type error") })
        }
    }
}
