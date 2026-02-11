package slang.repl

import slang.common.invoke
import slang.common.then
import slang.hlir.ParseTree2HlirTrasnformer
import slang.parser.String2ParseTreeTransformer
import slang.runtime.ConcreteState
import slang.runtime.Interpreter

const val PROMPT = "slang> "

class Repl {
    private val interpreter = Interpreter()

    fun start() {
        println("Slang REPL! Enter 'exit' or 'quit' to quit.")
        var state = ConcreteState()
        while (true) {
            print(PROMPT)
            val input = readlnOrNull() ?: break
            if (input.trim().isEmpty()) continue
            if (input == "exit" || input == "quit") break

            try {
                val compilerPipeline = String2ParseTreeTransformer() then ParseTree2HlirTrasnformer()
                val ast = compilerPipeline.invoke(input).getOrNull()
                if (ast != null) {
                    state = interpreter.interpret(ast, state)
                }
            } catch (e: Exception) {
                println("Error: ${e.message}")
                if (e !is RuntimeException) e.printStackTrace()
            }
        }
    }
}

fun main() {
    val repl = Repl()
    repl.start()
}
