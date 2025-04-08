package slang.parser

import SlangLexer
import SlangParser
import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.reflections.Reflections
import org.slf4j.LoggerFactory
import java.io.File

data class LineColumn(val line: Int, val column: Int) : Comparable<LineColumn> {
    override fun compareTo(other: LineColumn): Int {
        return if (line == other.line) {
            column - other.column
        } else {
            line - other.line
        }
    }

    override fun toString(): String {
        return "$line:$column"
    }
}

fun ParserRuleContext.lineColumn() = LineColumn(this.start.line, this.start.charPositionInLine)

class Parser(file: File) {
    var compilationUnit: SlangParser.CompilationUnitContext
    var parser: SlangParser
    private val errorListener = SlangParserErrorListener()
    private val ruleManager = CompilationRuleManager()

    private val logger = LoggerFactory.getLogger(Parser::class.java)


    init {
        val input = file.inputStream().bufferedReader().use { it.readText() }
        val lexer = SlangLexer(ANTLRInputStream(input))
        parser = SlangParser(CommonTokenStream(lexer))
        parser.addErrorListener(SlangParserErrorListener())
        parser.addErrorListener(errorListener)
        compilationUnit = parser.compilationUnit()
        initializeCompilationRules()
    }

    fun getErrors(): List<Error> {
        return errorListener.getErrorsSorted()
    }

    // This method is used to parse the compilation unit and apply the rules. Basic syntax has been handled by Antlr
    fun parse() : Boolean {
        ruleManager.applyRules(compilationUnit)
        return errorListener.errors.isEmpty()
    }

    private fun initializeCompilationRules() {
        val reflections = Reflections("slang.parser")
        for (rule in reflections.getSubTypesOf(CompilationRule::class.java)) {
            val annotation = rule.getAnnotation(ParserRule::class.java) ?: continue

            if (!annotation.enabled) {
                logger.warn("Rule ${rule.simpleName} is not enabled.")
                continue
            }

            val constructor = rule.getConstructor(SlangParserErrorListener::class.java)
            val ruleInstance = constructor.newInstance(errorListener)
            ruleManager.addRule(ruleInstance)
        }
    }
}



