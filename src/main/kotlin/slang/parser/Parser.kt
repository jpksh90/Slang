package slang.parser

import SlangLexer
import SlangParser
import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import org.reflections.Reflections
import org.slf4j.LoggerFactory
import java.io.File

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

    fun parse(): Boolean {
        return ruleManager.applyRules(compilationUnit)
    }

    private fun initializeCompilationRules() {
        val reflections = Reflections("slang.parser")
        for (rule in reflections.getSubTypesOf(CompilationRule::class.java)) {
            val annotation = rule.getAnnotation(ParserRule::class.java)
                ?: throw IllegalStateException("Rule ${rule.simpleName} does not have a ParserRule annotation.")
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



