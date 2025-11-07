package slang.repl

import slang.parser.StringParserInterface
import slang.slast.*
import slang.slast.Function

const val PROMPT = "> "

sealed class ConcreteValue {
    data class NumberValue(val value: Double) : ConcreteValue()
    data class BoolValue(val value: Boolean) : ConcreteValue()
    data class StringValue(val value: String) : ConcreteValue()
    data class FunctionValue(val params: List<String>, val body: BlockStmt, val closure: Map<String, ConcreteValue>) : ConcreteValue()
    data class RecordValue(val fields: Map<String, ConcreteValue>) : ConcreteValue()
    data class ArrayValue(val elements: List<ConcreteValue>) : ConcreteValue()
    data class RefValue(val ref: Int) : ConcreteValue()
    object NoneValue : ConcreteValue()
}

// Functional control flow results
sealed class ControlFlow<T> {
    data class Normal<T>(val value: T) : ControlFlow<T>()
    data class Return<T>(val value: T) : ControlFlow<T>()
    class Break<T> : ControlFlow<T>()
    class Continue<T> : ControlFlow<T>()
}

// Interpreter state with immutable data structures
data class InterpreterState<T>(
    val env: Map<String, T> = emptyMap(),
    val heap: Map<Int, T> = emptyMap(),
    val nextRef: Int = 0
)

class Repl {
    private val interpreter = ConcreteInterpreter()

    fun start() {
        println("Welcome to the Slang REPL!")
        tailrec fun loop(state: InterpreterState<ConcreteValue>): Unit {
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

abstract class Interpreter<T> {
    
    abstract fun executeStmt(stmt: Stmt, state: InterpreterState<T>): Pair<InterpreterState<T>, ControlFlow<T>>
    
    abstract fun evaluateExpr(expr: Expr, state: InterpreterState<T>): Pair<InterpreterState<T>, T>
    
    fun interpret(program: CompilationUnit, state: InterpreterState<T> = InterpreterState()): InterpreterState<T> {
        return program.stmt.fold(state) { currentState, stmt ->
            val (newState, _) = executeStmt(stmt, currentState)
            newState
        }
    }
}

class ConcreteInterpreter : Interpreter<ConcreteValue>() {
    
    override fun executeStmt(stmt: Stmt, state: InterpreterState<ConcreteValue>): Pair<InterpreterState<ConcreteValue>, ControlFlow<ConcreteValue>> {
        return executeStmtImpl(stmt, state)
    }
    
    override fun evaluateExpr(expr: Expr, state: InterpreterState<ConcreteValue>): Pair<InterpreterState<ConcreteValue>, ConcreteValue> {
        return evaluateExprImpl(expr, state)
    }
}

// Function for executing statements with ConcreteValue
private fun ConcreteInterpreter.executeStmtImpl(stmt: Stmt, state: InterpreterState<ConcreteValue>): Pair<InterpreterState<ConcreteValue>, ControlFlow<ConcreteValue>> {
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
                        if (refValue is ConcreteValue.RefValue) {
                            stateAfterDeref.copy(heap = stateAfterDeref.heap + (refValue.ref to value))
                        } else {
                            throw RuntimeException("Expected reference in deref assignment")
                        }
                    }
                    is FieldAccess -> {
                        val (stateAfterRecord, record) = evaluateExpr(lhs.lhs, stateAfterExpr)
                        if (record is ConcreteValue.RecordValue && lhs.rhs is VarExpr) {
                            val updatedFields = record.fields + (lhs.rhs.name to value)
                            val updatedRecord = ConcreteValue.RecordValue(updatedFields)
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
                        if (arrayValue is ConcreteValue.ArrayValue && indexValue is ConcreteValue.NumberValue) {
                            val index = indexValue.value.toInt()
                            if (index >= 0 && index < arrayValue.elements.size) {
                                // Functional update: create new list with updated element
                                val updatedElements = arrayValue.elements.mapIndexed { i, elem -> 
                                    if (i == index) value else elem 
                                }
                                val updatedArray = ConcreteValue.ArrayValue(updatedElements)
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
                Pair(newState, ControlFlow.Normal(ConcreteValue.NoneValue))
            }
            
            is Function -> {
                // Create a function value with the current environment as closure
                // For recursive functions, we'll add the function name to the environment
                // and update the closure when we call it
                val functionValue = ConcreteValue.FunctionValue(stmt.params, stmt.body, state.env)
                val updatedEnv = state.env + (stmt.name to functionValue)
                Pair(state.copy(env = updatedEnv), ControlFlow.Normal(ConcreteValue.NoneValue))
            }
            
            is WhileStmt -> {
                tailrec fun loop(currentState: InterpreterState<ConcreteValue>): Pair<InterpreterState<ConcreteValue>, ControlFlow<ConcreteValue>> {
                    val (stateAfterCondition, condition) = evaluateExpr(stmt.condition, currentState)
                    return if (condition is ConcreteValue.BoolValue && condition.value) {
                        val (newState, flow) = executeStmt(stmt.body, stateAfterCondition)
                        when (flow) {
                            is ControlFlow.Break -> Pair(newState, ControlFlow.Normal(ConcreteValue.NoneValue))
                            is ControlFlow.Continue -> loop(newState)
                            is ControlFlow.Return -> Pair(newState, flow)
                            is ControlFlow.Normal -> loop(newState)
                        }
                    } else {
                        Pair(stateAfterCondition, ControlFlow.Normal(ConcreteValue.NoneValue))
                    }
                }
                loop(state)
            }
            
            is PrintStmt -> {
                val (newState, values) = stmt.args.fold(Pair(state, emptyList<ConcreteValue>())) { (s, vals), arg ->
                    val (nextState, value) = evaluateExpr(arg, s)
                    Pair(nextState, vals + value)
                }
                println(values.joinToString(" ") { valueToString(it) })
                Pair(newState, ControlFlow.Normal(ConcreteValue.NoneValue))
            }
            
            is IfStmt -> {
                val (stateAfterCondition, condition) = evaluateExpr(stmt.condition, state)
                if (condition is ConcreteValue.BoolValue && condition.value) {
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
                val initialFlow: ControlFlow<ConcreteValue> = ControlFlow.Normal(ConcreteValue.NoneValue)
                stmt.stmts.fold(Pair(state, initialFlow)) { (currentState, flow), s ->
                    when (flow) {
                        is ControlFlow.Normal -> executeStmt(s, currentState)
                        else -> Pair(currentState, flow) // Propagate non-normal control flow
                    }
                }
            }
            
            is DerefStmt -> {
                val (stateAfterLhs, refValue) = evaluateExpr(stmt.lhs, state)
                val (stateAfterRhs, value) = evaluateExpr(stmt.rhs, stateAfterLhs)
                if (refValue is ConcreteValue.RefValue) {
                    val updatedHeap = stateAfterRhs.heap + (refValue.ref to value)
                    Pair(stateAfterRhs.copy(heap = updatedHeap), ControlFlow.Normal(ConcreteValue.NoneValue))
                } else {
                    throw RuntimeException("Expected reference in deref assignment")
                }
            }
            
            is StructStmt -> {
                val (newState, evaluatedFields) = stmt.fields.entries.fold(
                    Pair(state, emptyMap<String, ConcreteValue>())
                ) { (s, fields), (name, expr) ->
                    val (nextState, value) = evaluateExpr(expr, s)
                    Pair(nextState, fields + (name to value))
                }
                val recordValue = ConcreteValue.RecordValue(evaluatedFields)
                val updatedEnv = newState.env + (stmt.id to recordValue)
                Pair(newState.copy(env = updatedEnv), ControlFlow.Normal(ConcreteValue.NoneValue))
            }
            
            is Break -> Pair(state, ControlFlow.Break())
            is Continue -> Pair(state, ControlFlow.Continue())
        }
    }

// Function for evaluating expressions with ConcreteValue
private fun ConcreteInterpreter.evaluateExprImpl(expr: Expr, state: InterpreterState<ConcreteValue>): Pair<InterpreterState<ConcreteValue>, ConcreteValue> {
        return when (expr) {
            is NumberLiteral -> Pair(state, ConcreteValue.NumberValue(expr.value))
            
            is BoolLiteral -> Pair(state, ConcreteValue.BoolValue(expr.value))
            
            is StringLiteral -> {
                val str = expr.value.trim('"')
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                Pair(state, ConcreteValue.StringValue(str))
            }
            
            is VarExpr -> {
                val value = state.env[expr.name] 
                    ?: throw RuntimeException("Variable ${expr.name} not defined")
                Pair(state, value)
            }
            
            is ReadInputExpr -> {
                val input = readlnOrNull()?.trim() ?: ""
                val value = input.toDoubleOrNull()?.let { ConcreteValue.NumberValue(it) } 
                    ?: ConcreteValue.StringValue(input)
                Pair(state, value)
            }
            
            is BinaryExpr -> {
                val (stateAfterLeft, left) = evaluateExpr(expr.left, state)
                val (stateAfterRight, right) = evaluateExpr(expr.right, stateAfterLeft)
                Pair(stateAfterRight, evaluateBinaryOp(left, expr.op, right))
            }
            
            is IfExpr -> {
                val (stateAfterCondition, condition) = evaluateExpr(expr.condition, state)
                if (condition is ConcreteValue.BoolValue && condition.value) {
                    evaluateExpr(expr.thenExpr, stateAfterCondition)
                } else {
                    evaluateExpr(expr.elseExpr, stateAfterCondition)
                }
            }
            
            is ParenExpr -> evaluateExpr(expr.expr, state)
            
            is NoneValue -> Pair(state, ConcreteValue.NoneValue)
            
            is Record -> {
                val (newState, fields) = expr.expression.fold(
                    Pair(state, emptyMap<String, ConcreteValue>())
                ) { (s, fieldsMap), (name, e) ->
                    val (nextState, value) = evaluateExpr(e, s)
                    Pair(nextState, fieldsMap + (name to value))
                }
                Pair(newState, ConcreteValue.RecordValue(fields))
            }
            
            is RefExpr -> {
                val (stateAfterExpr, value) = evaluateExpr(expr.expr, state)
                val ref = stateAfterExpr.nextRef
                val updatedHeap = stateAfterExpr.heap + (ref to value)
                val newState = stateAfterExpr.copy(heap = updatedHeap, nextRef = ref + 1)
                Pair(newState, ConcreteValue.RefValue(ref))
            }
            
            is DerefExpr -> {
                val (newState, refValue) = evaluateExpr(expr.expr, state)
                if (refValue is ConcreteValue.RefValue) {
                    val value = newState.heap[refValue.ref] 
                        ?: throw RuntimeException("Invalid reference")
                    Pair(newState, value)
                } else {
                    throw RuntimeException("Expected reference in deref")
                }
            }
            
            is FieldAccess -> {
                val (newState, record) = evaluateExpr(expr.lhs, state)
                if (record is ConcreteValue.RecordValue && expr.rhs is VarExpr) {
                    val value = record.fields[expr.rhs.name] 
                        ?: throw RuntimeException("Field ${expr.rhs.name} not found")
                    Pair(newState, value)
                } else {
                    throw RuntimeException("Invalid field access")
                }
            }
            
            is ArrayInit -> {
                val (newState, elements) = expr.elements.fold(
                    Pair(state, emptyList<ConcreteValue>())
                ) { (s, elems), e ->
                    val (nextState, value) = evaluateExpr(e, s)
                    Pair(nextState, elems + value)
                }
                Pair(newState, ConcreteValue.ArrayValue(elements))
            }
            
            is ArrayAccess -> {
                val (stateAfterArray, arrayValue) = evaluateExpr(expr.array, state)
                val (stateAfterIndex, indexValue) = evaluateExpr(expr.index, stateAfterArray)
                if (arrayValue is ConcreteValue.ArrayValue && indexValue is ConcreteValue.NumberValue) {
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
                val functionValue = ConcreteValue.FunctionValue(expr.params, expr.body, state.env)
                Pair(state, functionValue)
            }
            
            is NamedFunctionCall -> {
                val function = state.env[expr.name] 
                    ?: throw RuntimeException("Function ${expr.name} not defined")
                val (stateAfterArgs, args) = expr.arguments.fold(
                    Pair(state, emptyList<ConcreteValue>())
                ) { (s, argsList), arg ->
                    val (nextState, value) = evaluateExpr(arg, s)
                    Pair(nextState, argsList + value)
                }
                callFunction(function, args, stateAfterArgs, expr.name)
            }
            
            is ExpressionFunctionCall -> {
                val (stateAfterTarget, function) = evaluateExpr(expr.target, state)
                val (stateAfterArgs, args) = expr.arguments.fold(
                    Pair(stateAfterTarget, emptyList<ConcreteValue>())
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

// Helper function for calling functions
private fun ConcreteInterpreter.callFunction(function: ConcreteValue, args: List<ConcreteValue>, state: InterpreterState<ConcreteValue>, functionName: String? = null): Pair<InterpreterState<ConcreteValue>, ConcreteValue> {
        if (function !is ConcreteValue.FunctionValue) {
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

// Helper function for evaluating binary operations (pure function, no state needed)
private fun evaluateBinaryOp(left: ConcreteValue, op: Operator, right: ConcreteValue): ConcreteValue {
        return when (op) {
            Operator.PLUS -> {
                when {
                    left is ConcreteValue.NumberValue && right is ConcreteValue.NumberValue ->
                        ConcreteValue.NumberValue(left.value + right.value)
                    left is ConcreteValue.StringValue && right is ConcreteValue.StringValue ->
                        ConcreteValue.StringValue(left.value + right.value)
                    else -> throw RuntimeException("Type error in addition")
                }
            }
            Operator.MINUS -> {
                if (left is ConcreteValue.NumberValue && right is ConcreteValue.NumberValue) {
                    ConcreteValue.NumberValue(left.value - right.value)
                } else {
                    throw RuntimeException("Type error in subtraction")
                }
            }
            Operator.TIMES -> {
                if (left is ConcreteValue.NumberValue && right is ConcreteValue.NumberValue) {
                    ConcreteValue.NumberValue(left.value * right.value)
                } else {
                    throw RuntimeException("Type error in multiplication")
                }
            }
            Operator.DIV -> {
                if (left is ConcreteValue.NumberValue && right is ConcreteValue.NumberValue) {
                    if (right.value == 0.0) {
                        throw RuntimeException("Division by zero")
                    }
                    ConcreteValue.NumberValue(left.value / right.value)
                } else {
                    throw RuntimeException("Type error in division")
                }
            }
            Operator.MOD -> {
                if (left is ConcreteValue.NumberValue && right is ConcreteValue.NumberValue) {
                    ConcreteValue.NumberValue(left.value % right.value)
                } else {
                    throw RuntimeException("Type error in modulo")
                }
            }
            Operator.EQ -> {
                ConcreteValue.BoolValue(valuesEqual(left, right))
            }
            Operator.NEQ -> {
                ConcreteValue.BoolValue(!valuesEqual(left, right))
            }
            Operator.LT -> {
                if (left is ConcreteValue.NumberValue && right is ConcreteValue.NumberValue) {
                    ConcreteValue.BoolValue(left.value < right.value)
                } else {
                    throw RuntimeException("Type error in comparison")
                }
            }
            Operator.GT -> {
                if (left is ConcreteValue.NumberValue && right is ConcreteValue.NumberValue) {
                    ConcreteValue.BoolValue(left.value > right.value)
                } else {
                    throw RuntimeException("Type error in comparison")
                }
            }
            Operator.LEQ -> {
                if (left is ConcreteValue.NumberValue && right is ConcreteValue.NumberValue) {
                    ConcreteValue.BoolValue(left.value <= right.value)
                } else {
                    throw RuntimeException("Type error in comparison")
                }
            }
            Operator.GEQ -> {
                if (left is ConcreteValue.NumberValue && right is ConcreteValue.NumberValue) {
                    ConcreteValue.BoolValue(left.value >= right.value)
                } else {
                    throw RuntimeException("Type error in comparison")
                }
            }
            Operator.AND -> {
                if (left is ConcreteValue.BoolValue && right is ConcreteValue.BoolValue) {
                    ConcreteValue.BoolValue(left.value && right.value)
                } else {
                    throw RuntimeException("Type error in AND operation")
                }
            }
            Operator.OR -> {
                if (left is ConcreteValue.BoolValue && right is ConcreteValue.BoolValue) {
                    ConcreteValue.BoolValue(left.value || right.value)
                } else {
                    throw RuntimeException("Type error in OR operation")
                }
            }
        }
    }

// Helper function for comparing values (pure function)
private fun valuesEqual(left: ConcreteValue, right: ConcreteValue): Boolean {
        return when {
            left is ConcreteValue.NumberValue && right is ConcreteValue.NumberValue -> left.value == right.value
            left is ConcreteValue.BoolValue && right is ConcreteValue.BoolValue -> left.value == right.value
            left is ConcreteValue.StringValue && right is ConcreteValue.StringValue -> left.value == right.value
            left is ConcreteValue.NoneValue && right is ConcreteValue.NoneValue -> true
            else -> false
        }
    }

// Helper function for converting values to strings (pure function)
private fun valueToString(value: ConcreteValue): String {
        return when (value) {
            is ConcreteValue.NumberValue -> {
                // Format numbers nicely (remove .0 for integers)
                if (value.value == value.value.toLong().toDouble()) {
                    value.value.toLong().toString()
                } else {
                    value.value.toString()
                }
            }
            is ConcreteValue.BoolValue -> value.value.toString()
            is ConcreteValue.StringValue -> value.value
            is ConcreteValue.FunctionValue -> "<function>"
            is ConcreteValue.RecordValue -> "{${value.fields.entries.joinToString(", ") { "${it.key}: ${valueToString(it.value)}" }}}"
            is ConcreteValue.ArrayValue -> "[${value.elements.joinToString(", ") { valueToString(it) }}]"
            is ConcreteValue.RefValue -> "<ref:${value.ref}>"
            is ConcreteValue.NoneValue -> "None"
        }
    }
