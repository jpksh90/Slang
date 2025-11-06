package slang.repl

import slang.parser.StringParserInterface
import slang.slast.*
import slang.slast.Function

const val PROMPT = "> "

sealed class Value {
    data class NumberValue(val value: Double) : Value()
    data class BoolValue(val value: Boolean) : Value()
    data class StringValue(val value: String) : Value()
    data class FunctionValue(val params: List<String>, val body: BlockStmt, val closure: MutableMap<String, Value>) : Value()
    data class RecordValue(val fields: Map<String, Value>) : Value()
    data class ArrayValue(val elements: MutableList<Value>) : Value()
    data class RefValue(val ref: Int) : Value()
    object NoneValue : Value()
}

class ReturnException(val value: Value) : Exception()
class BreakException : Exception()
class ContinueException : Exception()

class Repl {
    private val interpreter = Interpreter()

    fun start() {
        println("Welcome to the Slang REPL!")
        while (true) {
            print(PROMPT)
            val input = readlnOrNull() ?: break
            if (input.trim().isEmpty()) continue
            if (input == "exit") break
            try {
                val program = StringParserInterface(input)
                val ast = SlastBuilder(program.compilationUnit).compilationUnit
                interpreter.interpret(ast)
            } catch (e: ReturnException) {
                println("Error: Return statement outside function")
            } catch (e: Exception) {
                println("Error: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}

fun main() {
    val repl = Repl()
    repl.start()
}

class Interpreter {
    private val globalEnv = mutableMapOf<String, Value>()
    private val heap = mutableMapOf<Int, Value>()
    private var nextRef = 0

    fun interpret(program: CompilationUnit) {
        for (stmt in program.stmt) {
            executeStmt(stmt, globalEnv)
        }
    }

    private fun executeStmt(stmt: Stmt, env: MutableMap<String, Value>) {
        when (stmt) {
            is LetStmt -> {
                val value = evaluateExpr(stmt.expr, env)
                env[stmt.name] = value
            }
            is AssignStmt -> {
                val value = evaluateExpr(stmt.expr, env)
                when (val lhs = stmt.lhs) {
                    is VarExpr -> {
                        if (env.containsKey(lhs.name)) {
                            env[lhs.name] = value
                        } else {
                            throw RuntimeException("Variable ${lhs.name} not defined")
                        }
                    }
                    is DerefExpr -> {
                        val refValue = evaluateExpr(lhs.expr, env)
                        if (refValue is Value.RefValue) {
                            heap[refValue.ref] = value
                        } else {
                            throw RuntimeException("Expected reference in deref assignment")
                        }
                    }
                    is FieldAccess -> {
                        // Handle field assignment in records
                        val record = evaluateExpr(lhs.lhs, env)
                        if (record is Value.RecordValue && lhs.rhs is VarExpr) {
                            val mutableFields = record.fields.toMutableMap()
                            mutableFields[lhs.rhs.name] = value
                            // Update the record
                            when (val target = lhs.lhs) {
                                is VarExpr -> env[target.name] = Value.RecordValue(mutableFields)
                                else -> throw RuntimeException("Cannot assign to field of non-variable")
                            }
                        } else {
                            throw RuntimeException("Invalid field access in assignment")
                        }
                    }
                    is ArrayAccess -> {
                        val arrayValue = evaluateExpr(lhs.array, env)
                        val indexValue = evaluateExpr(lhs.index, env)
                        if (arrayValue is Value.ArrayValue && indexValue is Value.NumberValue) {
                            val index = indexValue.value.toInt()
                            if (index >= 0 && index < arrayValue.elements.size) {
                                arrayValue.elements[index] = value
                            } else {
                                throw RuntimeException("Array index out of bounds")
                            }
                        } else {
                            throw RuntimeException("Invalid array access in assignment")
                        }
                    }
                    else -> throw RuntimeException("Invalid left-hand side in assignment")
                }
            }
            is Function -> {
                // For recursive functions, we need to add the function to the environment
                // before creating the closure, so it can reference itself
                val functionValue = Value.FunctionValue(stmt.params, stmt.body, env.toMutableMap())
                env[stmt.name] = functionValue
                // Update the closure to include the function itself
                functionValue.closure[stmt.name] = functionValue
            }
            is WhileStmt -> {
                while (true) {
                    val condition = evaluateExpr(stmt.condition, env)
                    if (condition !is Value.BoolValue || !condition.value) break
                    try {
                        executeStmt(stmt.body, env)
                    } catch (e: BreakException) {
                        break
                    } catch (e: ContinueException) {
                        continue
                    }
                }
            }
            is PrintStmt -> {
                val values = stmt.args.map { evaluateExpr(it, env) }
                println(values.joinToString(" ") { valueToString(it) })
            }
            is IfStmt -> {
                val condition = evaluateExpr(stmt.condition, env)
                if (condition is Value.BoolValue && condition.value) {
                    executeStmt(stmt.thenBody, env)
                } else {
                    executeStmt(stmt.elseBody, env)
                }
            }
            is ExprStmt -> {
                evaluateExpr(stmt.expr, env)
            }
            is ReturnStmt -> {
                val value = evaluateExpr(stmt.expr, env)
                throw ReturnException(value)
            }
            is BlockStmt -> {
                for (s in stmt.stmts) {
                    executeStmt(s, env)
                }
            }
            is DerefStmt -> {
                val value = evaluateExpr(stmt.rhs, env)
                val refValue = evaluateExpr(stmt.lhs, env)
                if (refValue is Value.RefValue) {
                    heap[refValue.ref] = value
                } else {
                    throw RuntimeException("Expected reference in deref assignment")
                }
            }
            is StructStmt -> {
                // Create a constructor function for the struct
                val fields = stmt.fields.toMutableMap()
                val methods = stmt.functions
                // For now, treat structs as records with methods
                env[stmt.id] = Value.RecordValue(fields.mapValues { evaluateExpr(it.value, env) })
            }
            is Break -> throw BreakException()
            is Continue -> throw ContinueException()
        }
    }

    private fun evaluateExpr(expr: Expr, env: MutableMap<String, Value>): Value {
        return when (expr) {
            is NumberLiteral -> Value.NumberValue(expr.value)
            is BoolLiteral -> Value.BoolValue(expr.value)
            is StringLiteral -> {
                // Remove quotes from string
                val str = expr.value.trim('"')
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                Value.StringValue(str)
            }
            is VarExpr -> {
                env[expr.name] ?: throw RuntimeException("Variable ${expr.name} not defined")
            }
            is ReadInputExpr -> {
                val input = readlnOrNull()?.trim() ?: ""
                // Try to parse as number, otherwise return as string
                input.toDoubleOrNull()?.let { Value.NumberValue(it) } ?: Value.StringValue(input)
            }
            is BinaryExpr -> {
                val left = evaluateExpr(expr.left, env)
                val right = evaluateExpr(expr.right, env)
                evaluateBinaryOp(left, expr.op, right)
            }
            is IfExpr -> {
                val condition = evaluateExpr(expr.condition, env)
                if (condition is Value.BoolValue && condition.value) {
                    evaluateExpr(expr.thenExpr, env)
                } else {
                    evaluateExpr(expr.elseExpr, env)
                }
            }
            is ParenExpr -> evaluateExpr(expr.expr, env)
            is NoneValue -> Value.NoneValue
            is Record -> {
                val fields = expr.expression.associate { (name, e) ->
                    name to evaluateExpr(e, env)
                }
                Value.RecordValue(fields)
            }
            is RefExpr -> {
                val value = evaluateExpr(expr.expr, env)
                val ref = nextRef++
                heap[ref] = value
                Value.RefValue(ref)
            }
            is DerefExpr -> {
                val refValue = evaluateExpr(expr.expr, env)
                if (refValue is Value.RefValue) {
                    heap[refValue.ref] ?: throw RuntimeException("Invalid reference")
                } else {
                    throw RuntimeException("Expected reference in deref")
                }
            }
            is FieldAccess -> {
                val record = evaluateExpr(expr.lhs, env)
                if (record is Value.RecordValue && expr.rhs is VarExpr) {
                    record.fields[expr.rhs.name] ?: throw RuntimeException("Field ${expr.rhs.name} not found")
                } else {
                    throw RuntimeException("Invalid field access")
                }
            }
            is ArrayInit -> {
                val elements = expr.elements.map { evaluateExpr(it, env) }.toMutableList()
                Value.ArrayValue(elements)
            }
            is ArrayAccess -> {
                val arrayValue = evaluateExpr(expr.array, env)
                val indexValue = evaluateExpr(expr.index, env)
                if (arrayValue is Value.ArrayValue && indexValue is Value.NumberValue) {
                    val index = indexValue.value.toInt()
                    if (index >= 0 && index < arrayValue.elements.size) {
                        arrayValue.elements[index]
                    } else {
                        throw RuntimeException("Array index out of bounds")
                    }
                } else {
                    throw RuntimeException("Invalid array access")
                }
            }
            is InlinedFunction -> {
                Value.FunctionValue(expr.params, expr.body, env.toMutableMap())
            }
            is NamedFunctionCall -> {
                val function = env[expr.name] ?: throw RuntimeException("Function ${expr.name} not defined")
                val args = expr.arguments.map { evaluateExpr(it, env) }
                callFunction(function, args)
            }
            is ExpressionFunctionCall -> {
                val function = evaluateExpr(expr.target, env)
                val args = expr.arguments.map { evaluateExpr(it, env) }
                callFunction(function, args)
            }
            else -> throw RuntimeException("Unsupported expression type: ${expr::class.simpleName} at ${expr.sourceCodeInfo}")
        }
    }

    private fun callFunction(function: Value, args: List<Value>): Value {
        if (function !is Value.FunctionValue) {
            throw RuntimeException("Expected function value")
        }
        if (function.params.size != args.size) {
            throw RuntimeException("Function expects ${function.params.size} arguments but got ${args.size}")
        }
        
        // Create new environment with closure + parameters
        val callEnv = function.closure.toMutableMap()
        for (i in function.params.indices) {
            callEnv[function.params[i]] = args[i]
        }
        
        // Execute function body
        return try {
            for (stmt in function.body.stmts) {
                executeStmt(stmt, callEnv)
            }
            Value.NoneValue
        } catch (e: ReturnException) {
            e.value
        }
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
