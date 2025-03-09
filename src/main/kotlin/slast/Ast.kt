package slast

import SimpleLangLexer
import SimpleLangParser
import org.antlr.v4.runtime.*

sealed class SlastNode {
    open fun<T> accept(visitor: ASTVisitor<T?>): T? {
        return when (this) {
            is LetStmt -> visitor.visitLetStmt(this)
            is AssignStmt -> visitor.visitAssignStmt(this)
            is FunPureStmt -> visitor.visitFunPureStmt(this)
            is FunImpureStmt -> visitor.visitFunImpureStmt(this)
            is WhileStmt -> visitor.visitWhileStmt(this)
            is PrintStmt -> visitor.visitPrintStmt(this)
            is IfStmt -> visitor.visitIfStmt(this)
            is ExprStmt -> visitor.visitExprStmt(this)
            is ReturnStmt -> visitor.visitReturnStmt(this)
            is IntExpr -> visitor.visitIntExpr(this)
            is BoolExpr -> visitor.visitBoolExpr(this)
            is VarExpr -> visitor.visitVarExpr(this)
            is ReadInputExpr -> visitor.visitReadInputExpr(this)
            is FuncCallExpr -> visitor.visitFuncCallExpr(this)
            is BinaryExpr -> visitor.visitBinaryExpr(this)
            is IfExpr -> visitor.visitIfExpr(this)
            is ParenExpr -> visitor.visitParenExpr(this)
            is Program -> visitor.visitProgram(this)
            is BlockStmt -> visitor.visitBlockStmt(this)
            is NoneValue -> visitor.visitNoneValue(this)
        }
    }
}

data class Program(val stmt: List<Stmt>) : SlastNode() {
    fun collectFunctionDeclarations() : List<String> {
        val pureFunctions = stmt.filterIsInstance<FunPureStmt>().map { it.name }
        val impureFunctions = stmt.filterIsInstance<FunImpureStmt>().map { it.name }
        return pureFunctions + impureFunctions
    }
}

// Statements
sealed class Stmt : SlastNode()
data class LetStmt(val name: String, val expr: Expr) : Stmt() {
    override fun <T> accept(visitor: ASTVisitor<T?>): T? {
        return expr.accept(visitor)
    }
}

data class AssignStmt(val name: String, val expr: Expr) : Stmt() {
    override fun <T> accept(visitor: ASTVisitor<T?>): T? {
        return expr.accept(visitor)
    }
}

data class FunPureStmt(val name: String, val params: List<String>, val body: Expr) : Stmt() {
    override fun <T> accept(visitor: ASTVisitor<T?>): T? {
        return body.accept(visitor)
    }
}

data class FunImpureStmt(val name: String, val params: List<String>, val body: BlockStmt) : Stmt() {
    override fun <T> accept(visitor: ASTVisitor<T?>): T? {
        return body.accept(visitor)
    }
}
data class WhileStmt(val condition: Expr, val body: BlockStmt) : Stmt() {
    override fun <T> accept(visitor: ASTVisitor<T?>): T? {
        return body.accept(visitor)
    }
}

data class PrintStmt(val args: List<Expr>) : Stmt() {
    override fun <T> accept(visitor: ASTVisitor<T?>): T? {
        return null
    }
}
data class IfStmt(val condition: Expr, val thenBody: BlockStmt, val elseBody: BlockStmt) : Stmt()
data class ExprStmt(val expr: Expr) : Stmt()
data class ReturnStmt(val expr: Expr) : Stmt()
data class BlockStmt(val stmts: List<Stmt>) : Stmt()

// Expressions
sealed class Expr : SlastNode()
data class IntExpr(val value: Int) : Expr()
data class BoolExpr(val value: Boolean) : Expr()
data class VarExpr(val name: String) : Expr()
data object ReadInputExpr : Expr()
data class FuncCallExpr(val name: String, val args: List<Expr>) : Expr()
data class BinaryExpr(val left: Expr, val op: String, val right: Expr) : Expr()
data class IfExpr(val condition: Expr, val thenExpr: Expr, val elseExpr: Expr) : Expr()
data class ParenExpr(val expr: Expr) : Expr()
data object NoneValue : Expr()
data class Record(val expression: List<Pair<String, Expr>>) : Expr()

fun SlastNode.prettyPrint(tabStop: Int = 0): String {
    val indent = "  ".repeat(tabStop)
    return when (this) {
        is BinaryExpr -> this.left.prettyPrint() + " " + this.left.prettyPrint()
        is BoolExpr -> this.value.toString()
        is FuncCallExpr -> this.name + "(" + this.args.joinToString(", ") { it.prettyPrint() } + ")"
        is IfExpr -> "ifte(${this.condition.prettyPrint()},${this.condition.prettyPrint()},${this.thenExpr.prettyPrint()})"
        is IntExpr -> this.value.toString()
        is ParenExpr -> "(" + this.expr.prettyPrint() + ")"
        ReadInputExpr -> "readInput()"
        is VarExpr -> this.name
        is Program -> this.stmt.joinToString("\n") { it.prettyPrint() }
        is AssignStmt -> indent + this.name + " = " + this.expr.prettyPrint() + ";"
        is BlockStmt -> "{\n" + this.stmts.joinToString("\n") { it.prettyPrint(tabStop+1) }
        is ExprStmt -> indent + this.expr.prettyPrint() + ";"
        is FunImpureStmt -> indent + "fun ${this.name}( " + this.params.joinToString(", ") + ")\n" + "${indent}{\n" + this
            .body
            .prettyPrint(tabStop+1) + "$indent }"
        is NoneValue -> "None"
        is FunPureStmt -> indent + "fun ${this.name}( " + this.params.joinToString(", ") + ")\n" + "=>" + this.body
        is IfStmt ->  "${indent} if (${this.condition.prettyPrint()}) {\n" + thenBody.prettyPrint(tabStop+1) +
                "$indent }\n" + "$indent else {" + this.elseBody.prettyPrint(tabStop+1) + "$indent }"
        is LetStmt -> "${indent}let ${this.name} = ${this.expr.prettyPrint()};"
        is PrintStmt -> indent + "print(" + this.args.joinToString(", ") { it.prettyPrint() } + ");"
        is ReturnStmt -> indent + "return " + expr.prettyPrint() + ";"
        is WhileStmt -> indent + "while (" + this.condition.prettyPrint() + ") {\n" + body.prettyPrint(tabStop+1) + "$indent }"
    }
}

//fun ASTNode.isFunctionDeclaration(): Boolean {
//    return when (this) {
//        is FunPureStmt -> true
//        is FunImpureStmt -> true
//        else -> false
//    }
//}


fun main() {
    val inputCode = """
        fun power(base, exp) {
            if (base > exp) {
                base = base + 1;
                return base;
            } else {
                return exp;
            }
        }

        fun topper(x) => x+1;

        let x = true;
    """.trimIndent()
    val inputStream = ANTLRInputStream(inputCode)
    val lexer = SimpleLangLexer(inputStream)
    val tokens = CommonTokenStream(lexer)
    val parser = SimpleLangParser(tokens)

    val parseTree = parser.prog()
    val astBuilder = ASTBuilder()
    val ast = astBuilder.visit(parseTree) as Program
//    print(ast.collectFunctionDeclarations())
    println(ast.prettyPrint())
//    print(prettyPrintAST(ast))
//
//    val collectFunctions = ASTVisitor<List<String>> {
//
//    }
}


//fun main() {
//     val inputCode = """
//        fun power(base, exp) {
//            if (base > exp) {
//                base = base + 1;
//                return base;
//            } else {
//                return exp;
//            }
//        }
//
//        fun topper(x) => x+1;
//
//        let x = true;
//    """.trimIndent()
//    val inputStream = ANTLRInputStream(inputCode)
//    val lexer = SimpleLangLexer(inputStream)
//    val tokens = CommonTokenStream(lexer)
//    val parser = SimpleLangParser(tokens)
//
//    val parseTree = parser.prog()
//    val astBuilder = ASTBuilder()
//    val ast = astBuilder.visit(parseTree) as Program
//
//    val generator = SimpleLangToKotlin()
//    println(generator.generate(ast))
//}


