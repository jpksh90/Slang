package slang.repl

import slang.hlir.ProgramUnit
import slang.runtime.Interpreter

class ConcreteInterpreter {
    private val interpreter = Interpreter()

    fun interpret(unit: ProgramUnit) {
        interpreter.interpret(unit)
    }
}
