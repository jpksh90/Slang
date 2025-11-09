package slang.parser

import SlangLexer
import SlangParser
import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import slang.common.CodeInfo
import slang.common.Result
import slang.common.Transform
import java.io.File

fun ParserRuleContext.lineColumn() =
    CodeInfo(
        this.start.line,
        this.stop.line,
        this.start.charPositionInLine,
        this.stop.charPositionInLine,
    )

typealias ParseErrors = List<CompilerError>
typealias ParseTree = SlangParser.CompilationUnitContext

open class Parser {
    private val errorListener = SlangParserErrorListener()
    private val ruleManager = CompilationRuleManager()

    fun parse(input: CharStream): Result<ParseTree, ParseErrors> {
        val lexer = SlangLexer(input)
        val parser = SlangParser(CommonTokenStream(lexer))
        parser.addErrorListener(errorListener)
        ruleManager.discoverCompilationRules("slang.parser", errorListener)
        val compilationUnit = parser.compilationUnit()
        ruleManager.applyRules(compilationUnit)
        val errors = errorListener.errors
        return if (errors.isNotEmpty()) {
            Result.err(errors)
        } else {
            Result.ok(compilationUnit)
        }
    }
}

class String2ParseTreeTransformer : Transform<String, ParseTree> {
    override fun transform(input: String): Result<ParseTree, ParseErrors> = Parser().parse(ANTLRInputStream(input))
}

class File2ParseTreeTransformer : Transform<File, ParseTree> {
    override fun transform(input: File): Result<ParseTree, ParseErrors> = Parser().parse(ANTLRInputStream(input.inputStream()))
}
