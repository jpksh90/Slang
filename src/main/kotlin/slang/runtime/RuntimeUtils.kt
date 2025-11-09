package slang.runtime

import slang.hlir.Operator

// Evaluate binary operations on Values
fun evaluateBinaryOp(
    left: Value,
    op: Operator,
    right: Value,
): Value =
    when (op) {
        Operator.PLUS -> {
            when {
                left is Value.NumberValue && right is Value.NumberValue -> Value.NumberValue(left.value + right.value)
                left is Value.StringValue && right is Value.StringValue -> Value.StringValue(left.value + right.value)
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
                if (right.value == 0.0) throw RuntimeException("Division by zero")
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

        Operator.EQ -> Value.BoolValue(valuesEqual(left, right))
        Operator.NEQ -> Value.BoolValue(!valuesEqual(left, right))
        Operator.LT ->
            if (left is Value.NumberValue &&
                right is Value.NumberValue
            ) {
                Value.BoolValue(left.value < right.value)
            } else {
                throw RuntimeException(
                    "Type error in comparison",
                )
            }

        Operator.GT ->
            if (left is Value.NumberValue &&
                right is Value.NumberValue
            ) {
                Value.BoolValue(left.value > right.value)
            } else {
                throw RuntimeException(
                    "Type error in comparison",
                )
            }

        Operator.LEQ ->
            if (left is Value.NumberValue &&
                right is Value.NumberValue
            ) {
                Value.BoolValue(left.value <= right.value)
            } else {
                throw RuntimeException(
                    "Type error in comparison",
                )
            }

        Operator.GEQ ->
            if (left is Value.NumberValue &&
                right is Value.NumberValue
            ) {
                Value.BoolValue(left.value >= right.value)
            } else {
                throw RuntimeException(
                    "Type error in comparison",
                )
            }

        Operator.AND ->
            if (left is Value.BoolValue &&
                right is Value.BoolValue
            ) {
                Value.BoolValue(left.value && right.value)
            } else {
                throw RuntimeException(
                    "Type error in AND operation",
                )
            }

        Operator.OR ->
            if (left is Value.BoolValue &&
                right is Value.BoolValue
            ) {
                Value.BoolValue(left.value || right.value)
            } else {
                throw RuntimeException(
                    "Type error in OR operation",
                )
            }
    }

fun valuesEqual(
    left: Value,
    right: Value,
): Boolean =
    when {
        left is Value.NumberValue && right is Value.NumberValue -> left.value == right.value
        left is Value.BoolValue && right is Value.BoolValue -> left.value == right.value
        left is Value.StringValue && right is Value.StringValue -> left.value == right.value
        left is Value.NoneValue && right is Value.NoneValue -> true
        else -> false
    }

fun valueToString(value: Value): String =
    when (value) {
        is Value.NumberValue ->
            if (value.value == value.value.toLong().toDouble()) {
                value.value
                    .toLong()
                    .toString()
            } else {
                value.value.toString()
            }

        is Value.BoolValue -> value.value.toString()
        is Value.StringValue -> value.value
        is Value.FunctionValue -> "<function>"
        is Value.RecordValue -> "{${value.fields.entries.joinToString(", ") { "${it.key}: ${valueToString(it.value)}" }}}"
        is Value.ArrayValue -> "[${value.elements.joinToString(", ") { valueToString(it) }}]"
        is Value.RefValue -> "<ref:${value.ref}>"
        is Value.NoneValue -> "None"
    }
