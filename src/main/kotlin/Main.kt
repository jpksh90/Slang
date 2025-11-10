import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.Tag
import slang.common.invoke
import slang.common.then
import slang.hlir.ParseTree2HlirTrasnformer
import slang.hlir.ProgramUnit
import slang.parser.File2ParseTreeTransformer
import slang.repl.Repl
import slang.runtime.ConcreteState
import slang.runtime.Interpreter
import java.io.File
import java.nio.file.Paths

class SlangCLI : CliktCommand(name = "slang") {
    private val filename by argument(help = "Slang source file to execute").optional()
    private val hlir by option("--hlir", help = "Output HLIR representation instead of running").flag()
    private val output by option("-o", help = "Output file for HLIR (default: stdout)")

    init {
        versionOption("1.0")
    }

    override fun run() {
        // If a subcommand was invoked, don't execute file logic
        if (currentContext.invokedSubcommand != null) {
            return
        }

        if (filename == null) {
            echo("Usage: slang <filename> or slang repl", err = true)
            echo("Run 'slang --help' for more information", err = true)
            return
        }

        val file = Paths.get(filename!!).toAbsolutePath().toFile()
        if (!file.exists()) {
            echo("Error: File not found: $filename", err = true)
            return
        }

        val transformers = File2ParseTreeTransformer() then ParseTree2HlirTrasnformer()
        val result = transformers.invoke(file)

        result.fold(
            onSuccess = { programUnit ->
                if (hlir) {
                    outputHlir(programUnit)
                } else {
                    runProgram(programUnit)
                }
            },
            onFailure = { errors ->
                echo("Parse errors:", err = true)
                errors.forEach { error ->
                    echo("  $error", err = true)
                }
            },
        )
    }

    private fun outputHlir(programUnit: ProgramUnit) {
        val dumperOptions =
            DumperOptions().apply {
                defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
                isPrettyFlow = true
            }
        val yaml = Yaml(dumperOptions)
        val yamlString = yaml.dumpAs(programUnit, Tag.MAP, DumperOptions.FlowStyle.BLOCK)

        if (output != null) {
            File(output!!).writeText(yamlString)
            echo("HLIR written to: $output")
        } else {
            echo(yamlString)
        }
    }

    private fun runProgram(programUnit: ProgramUnit) {
        try {
            val interpreter = Interpreter()
            interpreter.interpret(programUnit, ConcreteState())
        } catch (e: Exception) {
            echo("Runtime error: ${e.message}", err = true)
            if (e !is RuntimeException) {
                e.printStackTrace()
            }
        }
    }
}

class ReplCommand : CliktCommand(name = "repl") {
    override fun run() {
        val repl = Repl()
        repl.start()
    }
}

fun main(args: Array<String>) {
    if (args.isEmpty() || args[0] == "repl") {
        SlangCLI()
            .subcommands(ReplCommand())
            .main(args)
    } else {
        SlangCLI().main(args)
    }
}
