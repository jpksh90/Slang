package slang.runtime

import slang.hlir.Expr
import slang.hlir.ProgramUnit
import slang.hlir.Stmt

data class ConcreteState(
    val env: Map<String, Value> = emptyMap(),
    val heap: Map<Int, Value> = emptyMap(),
    val nextRef: Int = 0,
)

class Interpreter {
    fun interpret(
        program: ProgramUnit,
        state: ConcreteState = ConcreteState(),
    ): ConcreteState =
        program.stmt.fold(state) { currentState, stmt ->
            val (newState, _) = executeStmt(stmt, currentState)
            newState
        }

    private fun executeStmt(
        stmt: Stmt,
        state: ConcreteState,
    ): Pair<ConcreteState, ControlFlow> {
        when (stmt) {
            is Stmt.LetStmt -> {
                val (newState, value) = evaluateExpr(stmt.expr, state)
                val updatedEnv = newState.env + (stmt.name to value)
                return Pair(newState.copy(env = updatedEnv), ControlFlow.Normal(value))
            }

            is Stmt.AssignStmt -> {
                val (stateAfterExpr, value) = evaluateExpr(stmt.expr, state)
                val newState =
                    when (val lhs = stmt.lhs) {
                        is Expr.VarExpr -> {
                            if (stateAfterExpr.env.containsKey(lhs.name)) {
                                stateAfterExpr.copy(env = stateAfterExpr.env + (lhs.name to value))
                            } else {
                                throw RuntimeException("Variable ${lhs.name} not defined")
                            }
                        }

                        is Expr.DerefExpr -> {
                            val (stateAfterDeref, refValue) = evaluateExpr(lhs.expr, stateAfterExpr)
                            if (refValue is Value.RefValue) {
                                stateAfterDeref.copy(heap = stateAfterDeref.heap + (refValue.ref to value))
                            } else {
                                throw RuntimeException("Expected reference in deref assignment")
                            }
                        }

                        is Expr.FieldAccess -> {
                            val (stateAfterRecord, record) = evaluateExpr(lhs.lhs, stateAfterExpr)
                            if (record is Value.RecordValue && lhs.rhs is Expr.VarExpr) {
                                val updatedFields = record.fields + (lhs.rhs.name to value)
                                val updatedRecord = Value.RecordValue(updatedFields)
                                when (val target = lhs.lhs) {
                                    is Expr.VarExpr -> stateAfterRecord.copy(env = stateAfterRecord.env + (target.name to updatedRecord))
                                    else -> throw RuntimeException("Cannot assign to field of non-variable")
                                }
                            } else {
                                throw RuntimeException("Invalid field access in assignment")
                            }
                        }

                        is Expr.ArrayAccess -> {
                            val (stateAfterArray, arrayValue) = evaluateExpr(lhs.array, stateAfterExpr)
                            val (stateAfterIndex, indexValue) = evaluateExpr(lhs.index, stateAfterArray)
                            if (arrayValue is Value.ArrayValue && indexValue is Value.NumberValue) {
                                val index = indexValue.value.toInt()
                                if (index >= 0 && index < arrayValue.elements.size) {
                                    val updatedElements =
                                        arrayValue.elements.mapIndexed { i, elem -> if (i == index) value else elem }
                                    val updatedArray = Value.ArrayValue(updatedElements)
                                    when (val target = lhs.array) {
                                        is Expr.VarExpr -> stateAfterIndex.copy(env = stateAfterIndex.env + (target.name to updatedArray))
                                        else -> throw RuntimeException("Cannot update array element")
                                    }
                                } else {
                                    throw RuntimeException("Array index out of bounds")
                                }
                            } else {
                                throw RuntimeException("Invalid array access in assignment")
                            }
                        }

                        else -> throw RuntimeException("Invalid left-hand side in assignment")
                    }
                return Pair(newState, ControlFlow.Normal())
            }

            is Stmt.Function -> {
                val f = stmt
                val functionValue = Value.FunctionValue(f.params, f.body, state.env)
                val updatedEnv = state.env + (f.name to functionValue)
                return Pair(state.copy(env = updatedEnv), ControlFlow.Normal())
            }

            is Stmt.WhileStmt -> {
                var loopState = state
                while (true) {
                    val (stateAfterCondition, condition) = evaluateExpr(stmt.condition, loopState)
                    if (condition is Value.BoolValue && condition.value) {
                        val (newState, flow) = executeStmt(stmt.body, stateAfterCondition)
                        when (flow) {
                            is ControlFlow.Break -> return Pair(newState, ControlFlow.Normal())
                            is ControlFlow.Continue -> {
                                loopState = newState
                                continue
                            }

                            is ControlFlow.Return -> return Pair(newState, flow)
                            is ControlFlow.Normal -> {
                                loopState = newState
                                continue
                            }
                        }
                    } else {
                        return Pair(stateAfterCondition, ControlFlow.Normal())
                    }
                }
            }

            is Stmt.PrintStmt -> {
                val (newState, values) =
                    stmt.args.fold(Pair(state, emptyList<Value>())) { (s, vals), arg ->
                        val (nextState, value) = evaluateExpr(arg, s)
                        Pair(nextState, vals + value)
                    }
                println(values.joinToString(" ") { valueToString(it) })
                return Pair(newState, ControlFlow.Normal())
            }

            is Stmt.IfStmt -> {
                val (stateAfterCondition, condition) = evaluateExpr(stmt.condition, state)
                return if (condition is Value.BoolValue && condition.value) {
                    executeStmt(stmt.thenBody, stateAfterCondition)
                } else {
                    executeStmt(stmt.elseBody, stateAfterCondition)
                }
            }

            is Stmt.ExprStmt -> {
                val (newState, value) = evaluateExpr(stmt.expr, state)
                return Pair(newState, ControlFlow.Normal(value))
            }

            is Stmt.ReturnStmt -> {
                val (newState, value) = evaluateExpr(stmt.expr, state)
                return Pair(newState, ControlFlow.Return(value))
            }

            is Stmt.BlockStmt -> {
                var currentState = state
                var flow: ControlFlow = ControlFlow.Normal()
                for (s in stmt.stmts) {
                    if (flow is ControlFlow.Normal) {
                        val (ns, f) = executeStmt(s, currentState)
                        currentState = ns
                        flow = f
                    } else {
                        break
                    }
                }
                return Pair(currentState, flow)
            }

            is Stmt.DerefStmt -> {
                val (stateAfterLhs, refValue) = evaluateExpr(stmt.lhs, state)
                val (stateAfterRhs, value) = evaluateExpr(stmt.rhs, stateAfterLhs)
                if (refValue is Value.RefValue) {
                    val updatedHeap = stateAfterRhs.heap + (refValue.ref to value)
                    return Pair(stateAfterRhs.copy(heap = updatedHeap), ControlFlow.Normal())
                } else {
                    throw RuntimeException("Expected reference in deref assignment")
                }
            }

            is Stmt.StructStmt -> {
                val (newState, evaluatedFields) =
                    stmt.fields.entries.fold(
                        Pair(
                            state,
                            emptyMap<String, Value>(),
                        ),
                    ) { (s, fields), (name, expr) ->
                        val (nextState, value) = evaluateExpr(expr, s)
                        Pair(nextState, fields + (name to value))
                    }
                val recordValue = Value.RecordValue(evaluatedFields)
                val updatedEnv = newState.env + (stmt.id to recordValue)
                return Pair(newState.copy(env = updatedEnv), ControlFlow.Normal())
            }

            is Stmt.Break -> return Pair(state, ControlFlow.Break)
            is Stmt.Continue -> return Pair(state, ControlFlow.Continue)
        }
    }

    private fun evaluateExpr(
        expr: Expr,
        state: ConcreteState,
    ): Pair<ConcreteState, Value> {
        when (expr) {
            is Expr.NumberLiteral -> return Pair(state, Value.NumberValue(expr.value))
            is Expr.BoolLiteral -> return Pair(state, Value.BoolValue(expr.value))
            is Expr.StringLiteral -> {
                val str =
                    expr.value
                        .trim('"')
                        .replace("\\n", "\n")
                        .replace("\\t", "\t")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                return Pair(state, Value.StringValue(str))
            }

            is Expr.VarExpr -> {
                val value = state.env[expr.name] ?: throw RuntimeException("Variable ${expr.name} not defined")
                return Pair(state, value)
            }

            is Expr.ReadInputExpr -> {
                val input = readlnOrNull()?.trim() ?: ""
                val value = input.toDoubleOrNull()?.let { Value.NumberValue(it) } ?: Value.StringValue(input)
                return Pair(state, value)
            }

            is Expr.BinaryExpr -> {
                val (stateAfterLeft, left) = evaluateExpr(expr.left, state)
                val (stateAfterRight, right) = evaluateExpr(expr.right, stateAfterLeft)
                return Pair(stateAfterRight, evaluateBinaryOp(left, expr.op, right))
            }

            is Expr.IfExpr -> {
                val (stateAfterCondition, condition) = evaluateExpr(expr.condition, state)
                return if (condition is Value.BoolValue && condition.value) {
                    evaluateExpr(expr.thenExpr, stateAfterCondition)
                } else {
                    evaluateExpr(expr.elseExpr, stateAfterCondition)
                }
            }

            is Expr.ParenExpr -> return evaluateExpr(expr.expr, state)
            is Expr.NoneValue -> return Pair(state, Value.NoneValue)
            is Expr.Record -> {
                val (newState, fields) =
                    expr.expression.fold(
                        Pair(
                            state,
                            emptyMap<String, Value>(),
                        ),
                    ) { (s, fieldsMap), (name, e) ->
                        val (nextState, value) = evaluateExpr(e, s)
                        Pair(nextState, fieldsMap + (name to value))
                    }
                return Pair(newState, Value.RecordValue(fields))
            }

            is Expr.RefExpr -> {
                val (stateAfterExpr, value) = evaluateExpr(expr.expr, state)
                val ref = stateAfterExpr.nextRef
                val updatedHeap = stateAfterExpr.heap + (ref to value)
                val newState = stateAfterExpr.copy(heap = updatedHeap, nextRef = ref + 1)
                return Pair(newState, Value.RefValue(ref))
            }

            is Expr.DerefExpr -> {
                val (newState, refValue) = evaluateExpr(expr.expr, state)
                if (refValue is Value.RefValue) {
                    val value = newState.heap[refValue.ref] ?: throw RuntimeException("Invalid reference")
                    return Pair(newState, value)
                } else {
                    throw RuntimeException("Expected reference in deref")
                }
            }

            is Expr.FieldAccess -> {
                val (newState, record) = evaluateExpr(expr.lhs, state)
                if (record is Value.RecordValue && expr.rhs is Expr.VarExpr) {
                    val value =
                        record.fields[expr.rhs.name] ?: throw RuntimeException("Field ${expr.rhs.name} not found")
                    return Pair(newState, value)
                } else {
                    throw RuntimeException("Invalid field access")
                }
            }

            is Expr.ArrayInit -> {
                val (newState, elements) =
                    expr.elements.fold(Pair(state, emptyList<Value>())) { (s, elems), e ->
                        val (nextState, value) = evaluateExpr(e, s)
                        Pair(nextState, elems + value)
                    }
                return Pair(newState, Value.ArrayValue(elements))
            }

            is Expr.ArrayAccess -> {
                val (stateAfterArray, arrayValue) = evaluateExpr(expr.array, state)
                val (stateAfterIndex, indexValue) = evaluateExpr(expr.index, stateAfterArray)
                if (arrayValue is Value.ArrayValue && indexValue is Value.NumberValue) {
                    val index = indexValue.value.toInt()
                    if (index >= 0 && index < arrayValue.elements.size) {
                        return Pair(stateAfterIndex, arrayValue.elements[index])
                    } else {
                        throw RuntimeException("Array index out of bounds")
                    }
                } else {
                    throw RuntimeException("Invalid array access")
                }
            }

            is Expr.InlinedFunction -> {
                val functionValue = Value.FunctionValue(expr.params, expr.body, state.env)
                return Pair(state, functionValue)
            }

            is Expr.NamedFunctionCall -> {
                val function = state.env[expr.name] ?: throw RuntimeException("Function ${expr.name} not defined")
                val (stateAfterArgs, args) =
                    expr.arguments.fold(
                        Pair(
                            state,
                            emptyList<Value>(),
                        ),
                    ) { (s, argsList), arg ->
                        val (nextState, value) = evaluateExpr(arg, s)
                        Pair(nextState, argsList + value)
                    }
                return callFunction(function, args, stateAfterArgs, expr.name)
            }

            is Expr.ExpressionFunctionCall -> {
                val (stateAfterTarget, function) = evaluateExpr(expr.target, state)
                val (stateAfterArgs, args) =
                    expr.arguments.fold(
                        Pair(
                            stateAfterTarget,
                            emptyList<Value>(),
                        ),
                    ) { (s, argsList), arg ->
                        val (nextState, value) = evaluateExpr(arg, s)
                        Pair(nextState, argsList + value)
                    }
                return callFunction(function, args, stateAfterArgs, null)
            }
        }
    }

    private fun callFunction(
        function: Value,
        args: List<Value>,
        state: ConcreteState,
        functionName: String? = null,
    ): Pair<ConcreteState, Value> {
        if (function !is Value.FunctionValue) throw RuntimeException("Expected function value")
        if (function.params.size !=
            args.size
        ) {
            throw RuntimeException("Function expects ${function.params.size} arguments but got ${args.size}")
        }

        val paramBindings = function.params.zip(args).toMap()
        val recursiveBinding = if (functionName != null) mapOf(functionName to function) else emptyMap()
        val callEnv = function.closure + recursiveBinding + paramBindings
        val callState = state.copy(env = callEnv)

        val (finalState, flow) = executeStmt(function.body, callState)
        val returnValue =
            when (flow) {
                is ControlFlow.Return -> flow.value
                is ControlFlow.Normal -> flow.value
                is ControlFlow.Break -> throw RuntimeException("Break statement outside loop")
                is ControlFlow.Continue -> throw RuntimeException("Continue statement outside loop")
            }

        return Pair(state.copy(heap = finalState.heap, nextRef = finalState.nextRef), returnValue)
    }
}
