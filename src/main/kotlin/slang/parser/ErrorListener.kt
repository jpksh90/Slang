package slang.parser

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer


data class Error(val line: Int, val charPositionInLine: Int, val message: String) {
    override fun toString(): String {
        return "$line:$charPositionInLine - $message"
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
        val error = Error(line, charPositionInLine, msg ?: "Unknown error")
        errors.add(error)
    }

    fun addError(line: Int, charPositionInLine: Int, message: String) {
        errors.add(Error(line, charPositionInLine, message))
    }
}