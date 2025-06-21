package slang.repl


enum class Type {
    NUMBER,
    STRING,
    BOOL,
    NONE,
    USER_DEFINED,
    FUNCTION,
    ANY
}

data class MemoryCell(
    val name: String,
    val type: Type,
    val value: Any?
)

object Memory {
    val valueMap = mutableMapOf<String, MemoryCell>()
}