package slang.common

data class CodeInfo(
    val lineStart: Int,
    val lineEnd: Int,
    val columnStart: Int,
    val columnEnd: Int,
) : Comparable<CodeInfo> {
    override fun compareTo(other: CodeInfo): Int =
        compareValuesBy(
            this,
            other,
            { it.lineStart },
            { it.columnStart },
            { it.lineEnd },
            { it.columnEnd },
        )

    companion object {
        val generic = CodeInfo(-1, -1, -1, -1)
    }

    override fun toString(): String =
        if (this == generic) {
            "@"
        } else {
            "[$lineStart:$columnStart --  $lineEnd:$columnEnd]"
        }
}
