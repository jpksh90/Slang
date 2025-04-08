package slang.parser

import SlangBaseListener
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTreeWalker
import org.slf4j.LoggerFactory

/**
 * Annotation to define a parser rule.
 *
 * @property name The name of the parser rule.
 * @property description A brief description of the parser rule.
 * @property enabled Indicates if the parser rule is enabled. Default is true.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ParserRule(val name: String, val description: String, val enabled: Boolean = true)

/**
 * Base class for compilation rules.
 *
 * @property errorListener The error listener to log compilation errors.
 */
abstract class CompilationRule(private val errorListener: SlangParserErrorListener) : SlangBaseListener() {
    private var result: Boolean = true

    /**
     * Applies the compilation rule to the given parser context.
     *
     * @param ctx The parser context to apply the rule to.
     * @return True if the rule passes, false otherwise.
     */
    fun apply(ctx: ParserRuleContext): Boolean {
        ParseTreeWalker().walk(this, ctx)
        return result
    }

    /**
     * Logs a compilation error.
     *
     * @param error The error to log.
     */
    private fun logCompilationError(error: Error) {
        result = false
        errorListener.addError(error)
    }

    /**
     * Logs a compilation error with specific details.
     *
     * @param line The line number where the error occurred.
     * @param charPositionInLine The character position in the line where the error occurred.
     * @param message The error message.
     */
    fun logCompilationError(lineColumn: LineColumn, message: String) {
        logCompilationError(Error(lineColumn, message))
    }
}


/**
 * Manages the compilation rules.
 */
class CompilationRuleManager {
    private val rules = mutableListOf<CompilationRule>()

    private val logger = LoggerFactory.getLogger(CompilationRule::class.java)


    /**
     * Adds a compilation rule to the manager.
     *
     * @param rule The compilation rule to add.
     */
    fun addRule(rule: CompilationRule) {
        rules.add(rule)
    }

    /**
     * Applies all the compilation rules to the given parser context.
     *
     * @param ctx The parser context to apply the rules to.
     * @return True if all rules pass, false otherwise.
     */
    fun applyRules(ctx: ParserRuleContext): Boolean {
        return rules.fold(true) { acc, rule ->
            val annotation = rule::class.java.getAnnotation(ParserRule::class.java)
            if (annotation != null) {
                logger.debug("Applying rule: ${annotation.name}")
            }
            acc && rule.apply(ctx)
        }
    }
}
