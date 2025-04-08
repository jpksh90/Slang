package slang.parser

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer


data class Error(val lineCol: LineColumn, val message: String) {
    override fun toString(): String {
        return "$lineCol - $message"
    }
}

class SlangParserErrorListener : BaseErrorListener() {
    val errors = mutableListOf<Error>()

    override fun syntaxError(
        recognizer: Recognizer<*, *>?,
        offendingSymbol: Any?,
        line: Int,
        charPositionInLine: Int,
        msg: String?,
        e: RecognitionException?
    ) {
        val error = Error(LineColumn(line, charPositionInLine), msg ?: "Unknown error")
        errors.add(error)
    }

    fun addError(error: Error) {
        errors.add(error)
    }

    fun getErrorsSorted() : List<Error> {
        return errors.sortedBy { it.lineCol }
    }
}