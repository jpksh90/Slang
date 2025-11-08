package slang.repl

import slang.runtime.Interpreter
import slang.slast.CompilationUnit

class ConcreteInterpreter {
    private val interpreter = Interpreter()

    fun interpret(unit: CompilationUnit) {
        interpreter.interpret(unit)
    }
}

