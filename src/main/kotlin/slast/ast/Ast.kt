package slast.ast

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
            is NumberLiteral -> visitor.visitIntExpr(this)
            is BoolLiteral -> visitor.visitBoolExpr(this)
            is VarExpr -> visitor.visitVarExpr(this)
            is ReadInputExpr -> visitor.visitReadInputExpr(this)
            is FuncCallExpr -> visitor.visitFuncCallExpr(this)
            is BinaryExpr -> visitor.visitBinaryExpr(this)
            is IfExpr -> visitor.visitIfExpr(this)
            is ParenExpr -> visitor.visitParenExpr(this)
            is CompilationUnit -> visitor.visitProgram(this)
            is BlockStmt -> visitor.visitBlockStmt(this)
            is NoneValue -> visitor.visitNoneValue(this)
            is Record -> visitor.visitRecord(this)
            is StringLiteral -> visitor.visitStringExpr(this)
            is DerefExpr -> visitor.visitDerefExpr(this)
            is RefExpr -> visitor.visitRefExpr(this)
            is DerefStmt -> visitor.visitDerefStmt(this)
            is FieldAccess -> visitor.visitFieldAccessExpr(this)
        }
    }
}

enum class Operator {
    PLUS, MINUS, TIMES, DIV, MOD, EQ, NEQ, LT, GT, LEQ, GEQ, AND, OR;

    companion object {
        fun fromValue(op: String): Operator {
            return when (op) {
                "+" -> PLUS
                "-" -> MINUS
                "*" -> TIMES
                "/" -> DIV
                "%" -> MOD
                "==" -> EQ
                "!=" -> NEQ
                "<" -> LT
                ">" -> GT
                "<=" -> LEQ
                ">=" -> GEQ
                "&&" -> AND
                "||" -> OR
                else -> throw IllegalArgumentException("Unknown operator: $op")
            }
        }
    }

    override fun toString(): String {
        return when (this) {
            PLUS -> "+"
            MINUS -> "-"
            TIMES -> "*"
            DIV -> "/"
            MOD -> "%"
            EQ -> "=="
            NEQ -> "!="
            LT -> "<"
            GT -> ">"
            LEQ -> "<="
            GEQ -> ">="
            AND -> "&&"
            OR -> "||"
        }
    }
}

data class CompilationUnit(val stmt: List<Stmt>) : SlastNode() {
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

data class AssignStmt(val lhs: Expr, val expr: Expr) : Stmt() {
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
data class DerefStmt(val lhs: Expr, val rhs: Expr) : Stmt()

// Expressions
sealed class Expr : SlastNode()
data class NumberLiteral(val value: Double) : Expr()
data class BoolLiteral(val value: Boolean) : Expr()
data class VarExpr(val name: String) : Expr()
data object ReadInputExpr : Expr()
data class FuncCallExpr(val target: String, val args: List<Expr>) : Expr()
data class BinaryExpr(val left: Expr, val op: Operator, val right: Expr) : Expr()
data class IfExpr(val condition: Expr, val thenExpr: Expr, val elseExpr: Expr) : Expr()
data class ParenExpr(val expr: Expr) : Expr()
data object NoneValue : Expr()
data class Record(val expression: List<Pair<String, Expr>>) : Expr()
data class StringLiteral(val value: String) : Expr()
data class RefExpr(val expr: Expr) : Expr()
data class DerefExpr(val expr: Expr) : Expr()
data class FieldAccess(val lhs: Expr, val rhs: Expr) : Expr()

fun SlastNode.prettyPrint(tabStop: Int = 0): String {
    val indent = "  ".repeat(tabStop)
    return when (this) {
        is BinaryExpr -> "${left.prettyPrint()} ${op} ${right.prettyPrint()}"
        is BoolLiteral -> value.toString()
        is FuncCallExpr -> "$target(${args.joinToString(", ") { it.prettyPrint() }})"
        is IfExpr -> "if (${condition.prettyPrint()}) then ${thenExpr.prettyPrint()} else ${elseExpr.prettyPrint()}"
        is NumberLiteral -> value.toString()
        is ParenExpr -> "(${expr.prettyPrint()})"
        ReadInputExpr -> "readInput()"
        is VarExpr -> name
        is CompilationUnit -> stmt.joinToString("\n") { it.prettyPrint() }
        is AssignStmt -> "$indent${lhs.prettyPrint()} = ${expr.prettyPrint()};"
        is BlockStmt -> "$indent{\n" + stmts.joinToString("\n") { it.prettyPrint(tabStop + 1) } + "\n$indent}"
        is ExprStmt -> "$indent${expr.prettyPrint()};"
        is FunImpureStmt -> "$indent fun $name(${params.joinToString(", ")}) {\n" + body.prettyPrint(tabStop + 1) + "\n$indent}"
        is NoneValue -> "None"
        is FunPureStmt -> "$indent fun $name(${params.joinToString(", ")}) => ${body.prettyPrint()}"
        is IfStmt -> "$indent if (${condition.prettyPrint()}) {\n" + thenBody.prettyPrint(tabStop + 1) + "\n$indent} else {\n" + elseBody.prettyPrint(tabStop + 1) + "\n$indent}"
        is LetStmt -> "$indent let $name = ${expr.prettyPrint()};"
        is PrintStmt -> "$indent print(${args.joinToString(", ") { it.prettyPrint() }});"
        is ReturnStmt -> "$indent return ${expr.prettyPrint()};"
        is WhileStmt -> "$indent while (${condition.prettyPrint()}) {\n" + body.prettyPrint(tabStop + 1) + "\n$indent}"
        is Record -> "$indent{\n" + expression.joinToString("\n") { "$indent  ${it.first} : ${it.second.prettyPrint()}" } + "\n$indent}"
        is StringLiteral -> "\"$value\""
        is DerefExpr -> "deref(${expr.prettyPrint()})"
        is RefExpr -> "ref(${expr.prettyPrint()})"
        is DerefStmt -> "$indent deref(${lhs.prettyPrint()}) = ${rhs.prettyPrint()};"
        is FieldAccess -> "${lhs.prettyPrint()}.${rhs}"
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

    val parseTree = parser.compilationUnit()
    val IRBuilder = IRBuilder()
    val ast = IRBuilder.visit(parseTree) as CompilationUnit
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


