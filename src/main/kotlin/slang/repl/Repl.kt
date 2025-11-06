package slang.repl

import slang.parser.StringParserInterface
import slang.slast.*
import slang.slast.Function

const val PROMPT = "> "

sealed class Value {
    data class NumberValue(val value: Double) : Value()
    data class BoolValue(val value: Boolean) : Value()
    data class StringValue(val value: String) : Value()
    data class FunctionValue(val params: List<String>, val body: BlockStmt, val closure: Map<String, Value>) : Value()
    data class RecordValue(val fields: Map<String, Value>) : Value()
    data class ArrayValue(val elements: List<Value>) : Value()
    data class RefValue(val ref: Int) : Value()
    object NoneValue : Value()
}

// Functional control flow results
sealed class ControlFlow {
    data class Normal(val value: Value = Value.NoneValue) : ControlFlow()
    data class Return(val value: Value) : ControlFlow()
    object Break : ControlFlow()
    object Continue : ControlFlow()
}

// Interpreter state with immutable data structures
data class InterpreterState(
    val env: Map<String, Value> = emptyMap(),
    val heap: Map<Int, Value> = emptyMap(),
    val nextRef: Int = 0
)

class Repl {
    private val interpreter = Interpreter()

    fun start() {
        println("Welcome to the Slang REPL!")
        tailrec fun loop(state: InterpreterState): Unit {
            print(PROMPT)
            val input = readlnOrNull() ?: return
            if (input.trim().isEmpty()) return loop(state)
            if (input == "exit") return
            
            try {
                val program = StringParserInterface(input)
                val ast = SlastBuilder(program.compilationUnit).compilationUnit
                val newState = interpreter.interpret(ast, state)
                loop(newState)
            } catch (e: Exception) {
                println("Error: ${e.message}")
                if (e !is RuntimeException) e.printStackTrace()
                loop(state)
            }
        }
        loop(InterpreterState())
    }
}

fun main() {
    val repl = Repl()
    repl.start()
}

class Interpreter {
    
    fun interpret(program: CompilationUnit, state: InterpreterState = InterpreterState()): InterpreterState {
        return program.stmt.fold(state) { currentState, stmt ->
            val (newState, _) = executeStmt(stmt, currentState)
            newState
        }
    }

    private fun executeStmt(stmt: Stmt, state: InterpreterState): Pair<InterpreterState, ControlFlow> {
        return when (stmt) {
            is LetStmt -> {
                val (newState, value) = evaluateExpr(stmt.expr, state)
                val updatedEnv = newState.env + (stmt.name to value)
                Pair(newState.copy(env = updatedEnv), ControlFlow.Normal(value))
            }
            
            is AssignStmt -> {
                val (stateAfterExpr, value) = evaluateExpr(stmt.expr, state)
                val newState = when (val lhs = stmt.lhs) {
                    is VarExpr -> {
                        if (stateAfterExpr.env.containsKey(lhs.name)) {
                            stateAfterExpr.copy(env = stateAfterExpr.env + (lhs.name to value))
                        } else {
                            throw RuntimeException("Variable ${lhs.name} not defined")
                        }
                    }
                    is DerefExpr -> {
                        val (stateAfterDeref, refValue) = evaluateExpr(lhs.expr, stateAfterExpr)
                        if (refValue is Value.RefValue) {
                            stateAfterDeref.copy(heap = stateAfterDeref.heap + (refValue.ref to value))
                        } else {
                            throw RuntimeException("Expected reference in deref assignment")
                        }
                    }
                    is FieldAccess -> {
                        val (stateAfterRecord, record) = evaluateExpr(lhs.lhs, stateAfterExpr)
                        if (record is Value.RecordValue && lhs.rhs is VarExpr) {
                            val updatedFields = record.fields + (lhs.rhs.name to value)
                            val updatedRecord = Value.RecordValue(updatedFields)
                            when (val target = lhs.lhs) {
                                is VarExpr -> stateAfterRecord.copy(
                                    env = stateAfterRecord.env + (target.name to updatedRecord)
                                )
                                else -> throw RuntimeException("Cannot assign to field of non-variable")
                            }
                        } else {
                            throw RuntimeException("Invalid field access in assignment")
                        }
                    }
                    is ArrayAccess -> {
                        val (stateAfterArray, arrayValue) = evaluateExpr(lhs.array, stateAfterExpr)
                        val (stateAfterIndex, indexValue) = evaluateExpr(lhs.index, stateAfterArray)
                        if (arrayValue is Value.ArrayValue && indexValue is Value.NumberValue) {
                            val index = indexValue.value.toInt()
                            if (index >= 0 && index < arrayValue.elements.size) {
                                val updatedElements = arrayValue.elements.toMutableList().apply { set(index, value) }
                                val updatedArray = Value.ArrayValue(updatedElements)
                                when (val target = lhs.array) {
                                    is VarExpr -> stateAfterIndex.copy(
                                        env = stateAfterIndex.env + (target.name to updatedArray)
                                    )
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
                Pair(newState, ControlFlow.Normal())
            }
            
            is Function -> {
                // Create a function value with the current environment as closure
                // For recursive functions, we'll add the function name to the environment
                // and update the closure when we call it
                val functionValue = Value.FunctionValue(stmt.params, stmt.body, state.env)
                val updatedEnv = state.env + (stmt.name to functionValue)
                Pair(state.copy(env = updatedEnv), ControlFlow.Normal())
            }
            
            is WhileStmt -> {
                tailrec fun loop(currentState: InterpreterState): Pair<InterpreterState, ControlFlow> {
                    val (stateAfterCondition, condition) = evaluateExpr(stmt.condition, currentState)
                    return if (condition is Value.BoolValue && condition.value) {
                        val (newState, flow) = executeStmt(stmt.body, stateAfterCondition)
                        when (flow) {
                            is ControlFlow.Break -> Pair(newState, ControlFlow.Normal())
                            is ControlFlow.Continue -> loop(newState)
                            is ControlFlow.Return -> Pair(newState, flow)
                            is ControlFlow.Normal -> loop(newState)
                        }
                    } else {
                        Pair(stateAfterCondition, ControlFlow.Normal())
                    }
                }
                loop(state)
            }
            
            is PrintStmt -> {
                val (newState, values) = stmt.args.fold(Pair(state, emptyList<Value>())) { (s, vals), arg ->
                    val (nextState, value) = evaluateExpr(arg, s)
                    Pair(nextState, vals + value)
                }
                println(values.joinToString(" ") { valueToString(it) })
                Pair(newState, ControlFlow.Normal())
            }
            
            is IfStmt -> {
                val (stateAfterCondition, condition) = evaluateExpr(stmt.condition, state)
                if (condition is Value.BoolValue && condition.value) {
                    executeStmt(stmt.thenBody, stateAfterCondition)
                } else {
                    executeStmt(stmt.elseBody, stateAfterCondition)
                }
            }
            
            is ExprStmt -> {
                val (newState, value) = evaluateExpr(stmt.expr, state)
                Pair(newState, ControlFlow.Normal(value))
            }
            
            is ReturnStmt -> {
                val (newState, value) = evaluateExpr(stmt.expr, state)
                Pair(newState, ControlFlow.Return(value))
            }
            
            is BlockStmt -> {
                stmt.stmts.fold(Pair(state, ControlFlow.Normal() as ControlFlow)) { (currentState, flow), s ->
                    when (flow) {
                        is ControlFlow.Normal -> executeStmt(s, currentState)
                        else -> Pair(currentState, flow) // Propagate non-normal control flow
                    }
                }
            }
            
            is DerefStmt -> {
                val (stateAfterLhs, refValue) = evaluateExpr(stmt.lhs, state)
                val (stateAfterRhs, value) = evaluateExpr(stmt.rhs, stateAfterLhs)
                if (refValue is Value.RefValue) {
                    val updatedHeap = stateAfterRhs.heap + (refValue.ref to value)
                    Pair(stateAfterRhs.copy(heap = updatedHeap), ControlFlow.Normal())
                } else {
                    throw RuntimeException("Expected reference in deref assignment")
                }
            }
            
            is StructStmt -> {
                val (newState, evaluatedFields) = stmt.fields.entries.fold(
                    Pair(state, emptyMap<String, Value>())
                ) { (s, fields), (name, expr) ->
                    val (nextState, value) = evaluateExpr(expr, s)
                    Pair(nextState, fields + (name to value))
                }
                val recordValue = Value.RecordValue(evaluatedFields)
                val updatedEnv = newState.env + (stmt.id to recordValue)
                Pair(newState.copy(env = updatedEnv), ControlFlow.Normal())
            }
            
            is Break -> Pair(state, ControlFlow.Break)
            is Continue -> Pair(state, ControlFlow.Continue)
        }
    }

    private fun evaluateExpr(expr: Expr, state: InterpreterState): Pair<InterpreterState, Value> {
        return when (expr) {
            is NumberLiteral -> Pair(state, Value.NumberValue(expr.value))
            
            is BoolLiteral -> Pair(state, Value.BoolValue(expr.value))
            
            is StringLiteral -> {
                val str = expr.value.trim('"')
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                Pair(state, Value.StringValue(str))
            }
            
            is VarExpr -> {
                val value = state.env[expr.name] 
                    ?: throw RuntimeException("Variable ${expr.name} not defined")
                Pair(state, value)
            }
            
            is ReadInputExpr -> {
                val input = readlnOrNull()?.trim() ?: ""
                val value = input.toDoubleOrNull()?.let { Value.NumberValue(it) } 
                    ?: Value.StringValue(input)
                Pair(state, value)
            }
            
            is BinaryExpr -> {
                val (stateAfterLeft, left) = evaluateExpr(expr.left, state)
                val (stateAfterRight, right) = evaluateExpr(expr.right, stateAfterLeft)
                Pair(stateAfterRight, evaluateBinaryOp(left, expr.op, right))
            }
            
            is IfExpr -> {
                val (stateAfterCondition, condition) = evaluateExpr(expr.condition, state)
                if (condition is Value.BoolValue && condition.value) {
                    evaluateExpr(expr.thenExpr, stateAfterCondition)
                } else {
                    evaluateExpr(expr.elseExpr, stateAfterCondition)
                }
            }
            
            is ParenExpr -> evaluateExpr(expr.expr, state)
            
            is NoneValue -> Pair(state, Value.NoneValue)
            
            is Record -> {
                val (newState, fields) = expr.expression.fold(
                    Pair(state, emptyMap<String, Value>())
                ) { (s, fieldsMap), (name, e) ->
                    val (nextState, value) = evaluateExpr(e, s)
                    Pair(nextState, fieldsMap + (name to value))
                }
                Pair(newState, Value.RecordValue(fields))
            }
            
            is RefExpr -> {
                val (stateAfterExpr, value) = evaluateExpr(expr.expr, state)
                val ref = stateAfterExpr.nextRef
                val updatedHeap = stateAfterExpr.heap + (ref to value)
                val newState = stateAfterExpr.copy(heap = updatedHeap, nextRef = ref + 1)
                Pair(newState, Value.RefValue(ref))
            }
            
            is DerefExpr -> {
                val (newState, refValue) = evaluateExpr(expr.expr, state)
                if (refValue is Value.RefValue) {
                    val value = newState.heap[refValue.ref] 
                        ?: throw RuntimeException("Invalid reference")
                    Pair(newState, value)
                } else {
                    throw RuntimeException("Expected reference in deref")
                }
            }
            
            is FieldAccess -> {
                val (newState, record) = evaluateExpr(expr.lhs, state)
                if (record is Value.RecordValue && expr.rhs is VarExpr) {
                    val value = record.fields[expr.rhs.name] 
                        ?: throw RuntimeException("Field ${expr.rhs.name} not found")
                    Pair(newState, value)
                } else {
                    throw RuntimeException("Invalid field access")
                }
            }
            
            is ArrayInit -> {
                val (newState, elements) = expr.elements.fold(
                    Pair(state, emptyList<Value>())
                ) { (s, elems), e ->
                    val (nextState, value) = evaluateExpr(e, s)
                    Pair(nextState, elems + value)
                }
                Pair(newState, Value.ArrayValue(elements))
            }
            
            is ArrayAccess -> {
                val (stateAfterArray, arrayValue) = evaluateExpr(expr.array, state)
                val (stateAfterIndex, indexValue) = evaluateExpr(expr.index, stateAfterArray)
                if (arrayValue is Value.ArrayValue && indexValue is Value.NumberValue) {
                    val index = indexValue.value.toInt()
                    if (index >= 0 && index < arrayValue.elements.size) {
                        Pair(stateAfterIndex, arrayValue.elements[index])
                    } else {
                        throw RuntimeException("Array index out of bounds")
                    }
                } else {
                    throw RuntimeException("Invalid array access")
                }
            }
            
            is InlinedFunction -> {
                val functionValue = Value.FunctionValue(expr.params, expr.body, state.env)
                Pair(state, functionValue)
            }
            
            is NamedFunctionCall -> {
                val function = state.env[expr.name] 
                    ?: throw RuntimeException("Function ${expr.name} not defined")
                val (stateAfterArgs, args) = expr.arguments.fold(
                    Pair(state, emptyList<Value>())
                ) { (s, argsList), arg ->
                    val (nextState, value) = evaluateExpr(arg, s)
                    Pair(nextState, argsList + value)
                }
                callFunction(function, args, stateAfterArgs, expr.name)
            }
            
            is ExpressionFunctionCall -> {
                val (stateAfterTarget, function) = evaluateExpr(expr.target, state)
                val (stateAfterArgs, args) = expr.arguments.fold(
                    Pair(stateAfterTarget, emptyList<Value>())
                ) { (s, argsList), arg ->
                    val (nextState, value) = evaluateExpr(arg, s)
                    Pair(nextState, argsList + value)
                }
                callFunction(function, args, stateAfterArgs, null)
            }
            
            else -> {
                val location = if (expr.sourceCodeInfo.lineStart >= 0) {
                    " at line ${expr.sourceCodeInfo.lineStart}"
                } else {
                    ""
                }
                throw RuntimeException("Unsupported expression type: ${expr::class.simpleName}$location")
            }
        }
    }

    private fun callFunction(function: Value, args: List<Value>, state: InterpreterState, functionName: String? = null): Pair<InterpreterState, Value> {
        if (function !is Value.FunctionValue) {
            throw RuntimeException("Expected function value")
        }
        if (function.params.size != args.size) {
            throw RuntimeException("Function expects ${function.params.size} arguments but got ${args.size}")
        }
        
        // Create new environment with closure + parameters
        // For recursive functions, include the function itself in the environment
        val paramBindings = function.params.zip(args).toMap()
        val recursiveBinding = if (functionName != null) mapOf(functionName to function) else emptyMap()
        val callEnv = function.closure + recursiveBinding + paramBindings
        val callState = state.copy(env = callEnv)
        
        // Execute function body
        val (finalState, flow) = executeStmt(function.body, callState)
        val returnValue = when (flow) {
            is ControlFlow.Return -> flow.value
            is ControlFlow.Normal -> flow.value
            is ControlFlow.Break -> throw RuntimeException("Break statement outside loop")
            is ControlFlow.Continue -> throw RuntimeException("Continue statement outside loop")
        }
        
        // Restore original environment but keep heap changes
        return Pair(state.copy(heap = finalState.heap, nextRef = finalState.nextRef), returnValue)
    }

    private fun evaluateBinaryOp(left: Value, op: Operator, right: Value): Value {
        return when (op) {
            Operator.PLUS -> {
                when {
                    left is Value.NumberValue && right is Value.NumberValue ->
                        Value.NumberValue(left.value + right.value)
                    left is Value.StringValue && right is Value.StringValue ->
                        Value.StringValue(left.value + right.value)
                    else -> throw RuntimeException("Type error in addition")
                }
            }
            Operator.MINUS -> {
                if (left is Value.NumberValue && right is Value.NumberValue) {
                    Value.NumberValue(left.value - right.value)
                } else {
                    throw RuntimeException("Type error in subtraction")
                }
            }
            Operator.TIMES -> {
                if (left is Value.NumberValue && right is Value.NumberValue) {
                    Value.NumberValue(left.value * right.value)
                } else {
                    throw RuntimeException("Type error in multiplication")
                }
            }
            Operator.DIV -> {
                if (left is Value.NumberValue && right is Value.NumberValue) {
                    if (right.value == 0.0) {
                        throw RuntimeException("Division by zero")
                    }
                    Value.NumberValue(left.value / right.value)
                } else {
                    throw RuntimeException("Type error in division")
                }
            }
            Operator.MOD -> {
                if (left is Value.NumberValue && right is Value.NumberValue) {
                    Value.NumberValue(left.value % right.value)
                } else {
                    throw RuntimeException("Type error in modulo")
                }
            }
            Operator.EQ -> {
                Value.BoolValue(valuesEqual(left, right))
            }
            Operator.NEQ -> {
                Value.BoolValue(!valuesEqual(left, right))
            }
            Operator.LT -> {
                if (left is Value.NumberValue && right is Value.NumberValue) {
                    Value.BoolValue(left.value < right.value)
                } else {
                    throw RuntimeException("Type error in comparison")
                }
            }
            Operator.GT -> {
                if (left is Value.NumberValue && right is Value.NumberValue) {
                    Value.BoolValue(left.value > right.value)
                } else {
                    throw RuntimeException("Type error in comparison")
                }
            }
            Operator.LEQ -> {
                if (left is Value.NumberValue && right is Value.NumberValue) {
                    Value.BoolValue(left.value <= right.value)
                } else {
                    throw RuntimeException("Type error in comparison")
                }
            }
            Operator.GEQ -> {
                if (left is Value.NumberValue && right is Value.NumberValue) {
                    Value.BoolValue(left.value >= right.value)
                } else {
                    throw RuntimeException("Type error in comparison")
                }
            }
            Operator.AND -> {
                if (left is Value.BoolValue && right is Value.BoolValue) {
                    Value.BoolValue(left.value && right.value)
                } else {
                    throw RuntimeException("Type error in AND operation")
                }
            }
            Operator.OR -> {
                if (left is Value.BoolValue && right is Value.BoolValue) {
                    Value.BoolValue(left.value || right.value)
                } else {
                    throw RuntimeException("Type error in OR operation")
                }
            }
        }
    }

    private fun valuesEqual(left: Value, right: Value): Boolean {
        return when {
            left is Value.NumberValue && right is Value.NumberValue -> left.value == right.value
            left is Value.BoolValue && right is Value.BoolValue -> left.value == right.value
            left is Value.StringValue && right is Value.StringValue -> left.value == right.value
            left is Value.NoneValue && right is Value.NoneValue -> true
            else -> false
        }
    }

    private fun valueToString(value: Value): String {
        return when (value) {
            is Value.NumberValue -> {
                // Format numbers nicely (remove .0 for integers)
                if (value.value == value.value.toLong().toDouble()) {
                    value.value.toLong().toString()
                } else {
                    value.value.toString()
                }
            }
            is Value.BoolValue -> value.value.toString()
            is Value.StringValue -> value.value
            is Value.FunctionValue -> "<function>"
            is Value.RecordValue -> "{${value.fields.entries.joinToString(", ") { "${it.key}: ${valueToString(it.value)}" }}}"
            is Value.ArrayValue -> "[${value.elements.joinToString(", ") { valueToString(it) }}]"
            is Value.RefValue -> "<ref:${value.ref}>"
            is Value.NoneValue -> "None"
        }
    }
}
