package slang.parser

import org.antlr.v4.runtime.BaseErrorListener
import slang.common.CodeInfo

data class CompilerError(
    val lineCol: CodeInfo,
    val message: String,
) {
    override fun toString(): String = "$lineCol - $message"
}

class SlangParserErrorListener : BaseErrorListener() {
    val errors = mutableListOf<CompilerError>()

//    override fun syntaxError(
//        recognizer: Recognizer<*, *>?,
//        offendingSymbol: Any?,
//        line: Int,
//        charPositionInLine: Int,
//        msg: String?,
//        e: RecognitionException?
//    ) {
//        val error = CompilerError(SourceCodeInfo(line, charPositionInLine), msg ?: "Unknown error")
//        errors.add(error)
//    }

    fun addError(error: CompilerError) {
        errors.add(error)
    }

    fun getErrorsSorted(): List<CompilerError> = errors.sortedBy { it.lineCol }
}
