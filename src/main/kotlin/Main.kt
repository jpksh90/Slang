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
import slang.hlir.SlastNode
import slang.parser.File2ParseTreeTransformer
import java.nio.file.Paths

private const val RUN_OPT = "run"

private const val AST_OPT = "ast"

private const val IR_OPT = "ir"

class SlangCLI : CliktCommand("slangc") {
    private val inputFile by argument(help = "Input file")
    private val stage by option("-o", "--output-format", help = "Run till stage")
        .choice(AST_OPT, RUN_OPT, IR_OPT)
        .default(RUN_OPT)
    private val verbose by option("-v", "--verbose", help = "Enable verbose output").flag()

    init {
        versionOption("1.0")
    }

    private fun dumpAst(tree: SlangParser.CompilationUnitContext): String {
        val dumperOptions =
            DumperOptions().apply {
                defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
                isPrettyFlow = true
            }
        val yaml = Yaml(dumperOptions)
        val yamlString = yaml.dumpAs(tree, Tag.MAP, DumperOptions.FlowStyle.BLOCK)
        return yamlString
    }

    private fun dumpIR(unit: SlastNode): String {
        val dumperOptions =
            DumperOptions().apply {
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
        val parseTree = File2ParseTreeTransformer().transform(file.toFile())
    }
}

fun main(args: Array<String>) = SlangCLI().main(args)
