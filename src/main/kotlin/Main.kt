import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.choice
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.Tag
import slang.parser.FileParserInterface
import slang.repl.ConcreteInterpreter
import slang.slast.*
import java.nio.file.Files
import java.nio.file.Paths

private const val RUN_OPT = "run"

private const val AST_OPT = "ast"

private const val IR_OPT = "ir"

class SlangCLI : CliktCommand("slangc") {

    private val logger = LoggerFactory.getLogger("slangc")

    private val inputFile by argument(help = "Input file")
    private val stage by option("-o", "--output-format", help = "Run till stage").choice(AST_OPT, RUN_OPT, IR_OPT)
        .default(RUN_OPT)
    private val verbose by option("-v", "--verbose", help = "Enable verbose output").flag()

    init {
        versionOption("1.0")
    }

    private fun dumpAst(tree: SlangParser.CompilationUnitContext) : String {
        val dumperOptions = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            isPrettyFlow = true
        }
        val yaml = Yaml(dumperOptions)
        val yamlString = yaml.dumpAs(tree, Tag.MAP, DumperOptions.FlowStyle.BLOCK)
        return yamlString
    }

    private fun dumpIR(unit: SlastNode) : String {
        val dumperOptions = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            isPrettyFlow = true
        }
        val yaml = Yaml(dumperOptions)
        val yamlString = yaml.dumpAs(unit, Tag.MAP, DumperOptions.FlowStyle.BLOCK)
        return yamlString
    }

    override fun run() {
        if (verbose) {
            println("Input file: $inputFile")
            println("Output format: $stage")
        }

        val file = Paths.get(inputFile).toAbsolutePath()

        if (Files.exists(file).not()) {
            logger.error("File $inputFile does not exist")
            return
        }

        val parser = FileParserInterface(file.toFile())
        val parseTree = parser.parse()
        
        if (parseTree.not()) {
            logger.error("Failed to parse the input file")
            for (error in parser.getErrors()) {
                logger.error(error.toString())
            }
            return
        }

        if (stage == AST_OPT) {
            println(dumpAst(parser.compilationUnit))
            return
        }

        val irTree = SlastBuilder(parser.compilationUnit).compilationUnit
        if (stage == IR_OPT) {
           println(irTree.prettyPrint())
           return
        }

        if (stage == RUN_OPT) {
            val interpreter = ConcreteInterpreter()
            try {
                interpreter.interpret(irTree)
            } catch (e: Exception) {
                logger.error("Runtime error: ${e.message}")
                if (verbose) {
                    e.printStackTrace()
                }
            }
        }

    }
}

fun main(args: Array<String>) = SlangCLI().main(args)
