import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.choice
import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.Tag
import slast.ast.CustomErrorListener
import slast.ast.IRBuilder
import slast.ast.SlastNode
import slast.ast.prettyPrint
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.readText

private const val BYTECODE_OPT = "bytecode"

private const val AST_OPT = "ast"

private const val IR_OPT = "ir"

class SlangcCLI : CliktCommand("slangc") {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val inputFile by argument(help = "Input file")
    private val stage by option("-o", "--output-format", help = "Run till stage").choice(AST_OPT, BYTECODE_OPT, IR_OPT)
        .default(BYTECODE_OPT)
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

        val fileContents = file.readText()

        val lexer = SlangLexer(ANTLRInputStream(fileContents))
        val parser = SlangParser(CommonTokenStream(lexer))
        val errorListener = CustomErrorListener()
        parser.addErrorListener(errorListener)
        val parseTree = parser.compilationUnit()

        for (error in errorListener.errors) {
            println(error)
        }

        if (stage == AST_OPT) {
            println(dumpAst(parseTree))
        }

        val irBuilder = IRBuilder()
        val irTree = irBuilder.visit(parseTree)
        if (stage == IR_OPT) {
           println(irTree.prettyPrint())
        }

        if (stage == BYTECODE_OPT) {
            TODO("Bytecode generation has not been implemented")
        }

    }
}

fun main(args: Array<String>) = SlangcCLI().main(args)
