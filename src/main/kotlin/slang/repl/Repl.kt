package slang.repl

import slang.hlir.ParseTree2HlirTrasnformer
import slang.parser.String2ParseTreeTransformer
import slang.runtime.Interpreter
import slang.runtime.InterpreterState
import slang.common.invoke
import slang.common.then

const val PROMPT = "> "

class Repl {
    private val interpreter = Interpreter()

    fun start() {
        println("Welcome to the Slang REPL!")
        var state = InterpreterState()
        while (true) {
            print(PROMPT)
            val input = readlnOrNull() ?: break
            if (input.trim().isEmpty()) continue
            if (input == "exit") break

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
