package slang.parser

import SlangBaseListener
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTreeWalker
import org.reflections.Reflections
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
     * @param lineColumn The line/column where the error occurred.
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

    private val logger = LoggerFactory.getLogger(CompilationRuleManager::class.java)


    /**
     * Adds a compilation rule to the manager.
     *
     * @param rule The compilation rule to add.
     */
    fun addRule(rule: CompilationRule) {
        rules.add(rule)
    }

    /**
     * Discover and add compilation rules automatically by scanning the given package for classes
     * annotated with `@ParserRule` and implementing `CompilationRule`.
     *
     * This requires a no-arg or a single-argument constructor that accepts a `SlangParserErrorListener`.
     * Disabled rules (ParserRule.enabled == false) are skipped.
     *
     * @param packageName package to scan (e.g. "slang.parser")
     * @param errorListener listener instance to pass to rule constructors
     */
    fun addCompilationRulesAutomatically(packageName: String, errorListener: SlangParserErrorListener) {
        try {
            val reflections = Reflections(packageName)

            // Find classes annotated with @ParserRule
            val annotated = reflections.getTypesAnnotatedWith(ParserRule::class.java)

            for (ruleClass in annotated) {
                // Must be assignable to CompilationRule
                if (!CompilationRule::class.java.isAssignableFrom(ruleClass)) {
                    logger.warn("Found @ParserRule on ${ruleClass.name} but it does not extend CompilationRule - skipping")
                    continue
                }

                val annotation = ruleClass.getAnnotation(ParserRule::class.java)
                if (annotation != null && !annotation.enabled) {
                    logger.info("Rule ${ruleClass.simpleName} (${annotation.name}) is disabled by annotation")
                    continue
                }

                try {
                    // Prefer constructor(SlangParserErrorListener)
                    val ctor = ruleClass.getConstructor(SlangParserErrorListener::class.java)
                    val instance = ctor.newInstance(errorListener) as CompilationRule
                    addRule(instance)
                    logger.debug("Registered compilation rule: ${ruleClass.simpleName} (name=${annotation?.name})")
                } catch (e: NoSuchMethodException) {
                    logger.warn("Cannot instantiate rule ${ruleClass.name}: expected constructor(SlangParserErrorListener) not found", e)
                } catch (e: Exception) {
                    logger.error("Failed to instantiate compilation rule ${ruleClass.name}", e)
                }
            }

            // Fallback: also detect subclasses of CompilationRule (in case they aren't annotated)
            val subTypes = reflections.getSubTypesOf(CompilationRule::class.java)
            for (ruleClass in subTypes) {
                // skip classes already processed via annotation
                if (annotated.contains(ruleClass)) continue
                try {
                    val annotation = ruleClass.getAnnotation(ParserRule::class.java)
                    if (annotation != null && !annotation.enabled) {
                        logger.info("Rule ${ruleClass.simpleName} is disabled by annotation")
                        continue
                    }
                    val ctor = ruleClass.getConstructor(SlangParserErrorListener::class.java)
                    val instance = ctor.newInstance(errorListener)
                    addRule(instance)
                    logger.debug("Registered compilation rule (by subtype): ${ruleClass.simpleName}")
                } catch (e: NoSuchMethodException) {
                    logger.warn("Skipping ${ruleClass.name} because it has no suitable constructor(SlangParserErrorListener)", e)
                } catch (e: Exception) {
                    logger.error("Failed to instantiate compilation rule ${ruleClass.name}", e)
                }
            }
        } catch (e: Exception) {
            logger.error("Automatic compilation rule discovery failed", e)
        }
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
