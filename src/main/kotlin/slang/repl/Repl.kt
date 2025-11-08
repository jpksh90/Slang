package slang.repl

import slang.parser.StringParserInterface
import slang.slast.SlastBuilder
import slang.slast.CompilationUnit
import slang.runtime.Interpreter
import slang.runtime.InterpreterState

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
                val parser = StringParserInterface(input)
                val ast = SlastBuilder(parser.compilationUnit).compilationUnit
                state = interpreter.interpret(ast, state)
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
