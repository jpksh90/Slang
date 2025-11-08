package slang.runtime

// Interpreter state
data class InterpreterState(
    val env: Map<String, Value> = emptyMap(),
    val heap: Map<Int, Value> = emptyMap(),
    val nextRef: Int = 0
)

