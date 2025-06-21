package slang.repl

import slang.parser.StringParserInterface
import slang.slast.*
import slang.slast.Function

const val PROMPT = "> "
class Repl {
    private val memory = Memory
    private val interpreter = Interpreter(memory)

    fun start() {
        println("Welcome to the Slang REPL!")
        while (true) {
            print(PROMPT)
            val input = readlnOrNull() ?: break
            if (input.trim().isEmpty()) continue
            if (input == "exit") break
            try {
                val program = StringParserInterface(input)
                val ast = SlastBuilder(program.compilationUnit).compilationUnit
                interpreter.interpret(ast)
            } catch (e: Exception) {
                println("Error: ${e.message}")
            }
        }
    }
}

fun main() {
    val repl = Repl()
    repl.start()
}

class TypeResolver(val memory: Memory, expression: Expr) : ASTVisitor<Type> {
    override fun visitLetStmt(stmt: LetStmt): Type {
        if (memory.valueMap.containsKey(stmt.name)) {
            return memory.valueMap[stmt.name]!!.type
        } else {
            // compute the type of the expression
            return stmt.expr.accept(this)
        }
    }

    override fun visitAssignStmt(stmt: AssignStmt): Type {
        return stmt.expr.accept(this)
    }

    override fun visitInlinedFunction(stmt: InlinedFunction): Type {
        return Type.FUNCTION
    }

    fun visitNumberLiteral(expr: NumberLiteral) : Type {
        return Type.NUMBER
    }

    override fun visitFunction(stmt: Function): Type {
        return Type.FUNCTION
    }

    override fun visitWhileStmt(stmt: WhileStmt): Type {
        return Type.NONE
    }

    override fun visitPrintStmt(stmt: PrintStmt): Type {
        return Type.NONE
    }

    override fun visitIfStmt(stmt: IfStmt): Type {
        return Type.NONE
    }

    override fun visitExprStmt(stmt: ExprStmt): Type {
        return stmt.expr.accept(this)
    }

    override fun visitReturnStmt(stmt: ReturnStmt): Type {
        return stmt.expr.accept(this)
    }

    override fun visitBlockStmt(stmt: BlockStmt): Type {
        return Type.NONE
    }

    override fun visitIntExpr(expr: NumberLiteral): Type {
        return Type.NUMBER
    }

    override fun visitBoolExpr(expr: BoolLiteral): Type {
        return Type.BOOL
    }

    override fun visitVarExpr(expr: VarExpr): Type {
        return memory.valueMap.get(expr.name)?.type ?: Type.NONE
    }

    override fun visitReadInputExpr(expr: ReadInputExpr): Type {
        return Type.NONE
    }

    override fun visitFuncCallExpr(expr: FuncCallExpr): Type {
        return Type.ANY
    }

    override fun visitBinaryExpr(expr: BinaryExpr): Type {
        when (expr.op) {
            Operator.PLUS -> TODO()
            Operator.MINUS -> TODO()
            Operator.TIMES -> TODO()
            Operator.DIV -> TODO()
            Operator.MOD -> TODO()
            Operator.EQ -> TODO()
            Operator.NEQ -> TODO()
            Operator.LT -> TODO()
            Operator.GT -> TODO()
            Operator.LEQ -> TODO()
            Operator.GEQ -> TODO()
            Operator.AND -> TODO()
            Operator.OR -> TODO()
        }
    }

    override fun visitIfExpr(expr: IfExpr): Type {
        TODO("Not yet implemented")
    }

    override fun visitParenExpr(expr: ParenExpr): Type {
        TODO("Not yet implemented")
    }

    override fun visitProgram(program: CompilationUnit): Type {
        TODO("Not yet implemented")
    }

    override fun visitNoneValue(expr: NoneValue): Type {
        TODO("Not yet implemented")
    }

    override fun visitRecord(expr: Record): Type {
        TODO("Not yet implemented")
    }

    override fun visitStringExpr(expr: StringLiteral): Type {
        TODO("Not yet implemented")
    }

    override fun visitRefExpr(expr: RefExpr): Type {
        TODO("Not yet implemented")
    }

    override fun visitDerefExpr(expr: DerefExpr): Type {
        TODO("Not yet implemented")
    }

    override fun visitDerefStmt(stmt: DerefStmt): Type {
        TODO("Not yet implemented")
    }

    override fun visitFieldAccessExpr(expr: FieldAccess): Type {
        TODO("Not yet implemented")
    }

    override fun visitStructStmt(expr: StructStmt): Type {
        TODO("Not yet implemented")
    }

    override fun visitArrayInit(expr: ArrayInit): Type {
        TODO("Not yet implemented")
    }

    override fun visitArrayAccess(expr: ArrayAccess): Type {
        TODO("Not yet implemented")
    }

    override fun visitBreak(arg: Break): Type {
        TODO("Not yet implemented")
    }

    override fun visitContinue(arg: Continue): Type {
        TODO("Not yet implemented")
    }

}
class Interpreter(val memory: Memory) : ASTVisitor<Unit> {

    fun interpret(stmt: CompilationUnit) {
        // Interpret the statement
        // This is where the actual interpretation logic will go
        println("Interpreting: $stmt")
    }

    override fun visitLetStmt(stmt: LetStmt) {
        // get the type of the value stored.
        val id = stmt.name
        val expr = stmt.expr
    }

    override fun visitAssignStmt(stmt: AssignStmt) {
        TODO("Not yet implemented")
    }

    override fun visitInlinedFunction(stmt: InlinedFunction) {
        TODO("Not yet implemented")
    }

    override fun visitFunction(stmt: Function) {
        TODO("Not yet implemented")
    }

    override fun visitWhileStmt(stmt: WhileStmt) {
        TODO("Not yet implemented")
    }

    override fun visitPrintStmt(stmt: PrintStmt) {
        TODO("Not yet implemented")
    }

    override fun visitIfStmt(stmt: IfStmt) {
        TODO("Not yet implemented")
    }

    override fun visitExprStmt(stmt: ExprStmt) {
        TODO("Not yet implemented")
    }

    override fun visitReturnStmt(stmt: ReturnStmt) {
        TODO("Not yet implemented")
    }

    override fun visitBlockStmt(stmt: BlockStmt) {
        TODO("Not yet implemented")
    }

    override fun visitIntExpr(expr: NumberLiteral) {
        TODO("Not yet implemented")
    }

    override fun visitBoolExpr(expr: BoolLiteral) {
        TODO("Not yet implemented")
    }

    override fun visitVarExpr(expr: VarExpr) {
        TODO("Not yet implemented")
    }

    override fun visitReadInputExpr(expr: ReadInputExpr) {
        TODO("Not yet implemented")
    }

    override fun visitFuncCallExpr(expr: FuncCallExpr) {
        TODO("Not yet implemented")
    }

    override fun visitBinaryExpr(expr: BinaryExpr) {
        TODO("Not yet implemented")
    }

    override fun visitIfExpr(expr: IfExpr) {
        TODO("Not yet implemented")
    }

    override fun visitParenExpr(expr: ParenExpr) {
        TODO("Not yet implemented")
    }

    override fun visitProgram(program: CompilationUnit) {
        TODO("Not yet implemented")
    }

    override fun visitNoneValue(expr: NoneValue) {
        TODO("Not yet implemented")
    }

    override fun visitRecord(expr: Record) {
        TODO("Not yet implemented")
    }

    override fun visitStringExpr(expr: StringLiteral) {
        TODO("Not yet implemented")
    }

    override fun visitRefExpr(expr: RefExpr) {
        TODO("Not yet implemented")
    }

    override fun visitDerefExpr(expr: DerefExpr) {
        TODO("Not yet implemented")
    }

    override fun visitDerefStmt(stmt: DerefStmt) {
        TODO("Not yet implemented")
    }

    override fun visitFieldAccessExpr(expr: FieldAccess) {
        TODO("Not yet implemented")
    }

    override fun visitStructStmt(expr: StructStmt) {
        TODO("Not yet implemented")
    }

    override fun visitArrayInit(expr: ArrayInit) {
        TODO("Not yet implemented")
    }

    override fun visitArrayAccess(expr: ArrayAccess) {
        TODO("Not yet implemented")
    }

    override fun visitBreak(arg: Break) {
        TODO("Not yet implemented")
    }

    override fun visitContinue(arg: Continue) {
        TODO("Not yet implemented")
    }

}
