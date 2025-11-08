package slang.runtime

import slang.slast.BlockStmt

// Canonical runtime Value types for the interpreter
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

