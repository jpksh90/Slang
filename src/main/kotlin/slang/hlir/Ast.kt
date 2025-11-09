package slang.hlir

import SlangLexer
import SlangParser
import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import slang.common.CodeInfo
import slang.common.CodeInfo.Companion.generic

sealed class SlastNode {
    var codeInfo: CodeInfo = generic
}

enum class Operator {
    PLUS,
    MINUS,
    TIMES,
    DIV,
    MOD,
    EQ,
    NEQ,
    LT,
    GT,
    LEQ,
    GEQ,
    AND,
    OR,
    ;

    companion object {
        fun fromValue(op: String): Operator =
            when (op) {
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

    override fun toString(): String =
        when (this) {
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

data class ProgramUnit(
    val stmt: List<Stmt>,
) : SlastNode()

sealed class Stmt : SlastNode() {
    data class LetStmt(
        val name: String,
        val expr: Expr,
    ) : Stmt()

    data class AssignStmt(
        val lhs: Expr,
        val expr: Expr,
    ) : Stmt()

    data class WhileStmt(
        val condition: Expr,
        val body: BlockStmt,
    ) : Stmt()

    data class PrintStmt(
        val args: List<Expr>,
    ) : Stmt()

    data class IfStmt(
        val condition: Expr,
        val thenBody: BlockStmt,
        val elseBody: BlockStmt,
    ) : Stmt()

    data class ExprStmt(
        val expr: Expr,
    ) : Stmt()

    data class ReturnStmt(
        val expr: Expr,
    ) : Stmt()

    data class BlockStmt(
        val stmts: List<Stmt>,
    ) : Stmt()

    data class DerefStmt(
        val lhs: Expr,
        val rhs: Expr,
    ) : Stmt()

    data class StructStmt(
        val id: String,
        val functions: List<Function>,
        val fields: HashMap<String, Expr>,
    ) : Stmt()

    object Break : Stmt()

    object Continue : Stmt()

    data class Function(
        val name: String,
        val params: List<String>,
        val body: BlockStmt,
    ) : Stmt()
}

sealed class Expr : SlastNode() {
    data class NumberLiteral(
        val value: Double,
    ) : Expr()

    data class BoolLiteral(
        val value: Boolean,
    ) : Expr()

    data class VarExpr(
        val name: String,
    ) : Expr()

    data object ReadInputExpr : Expr()

    data class BinaryExpr(
        val left: Expr,
        val op: Operator,
        val right: Expr,
    ) : Expr()

    data class IfExpr(
        val condition: Expr,
        val thenExpr: Expr,
        val elseExpr: Expr,
    ) : Expr()

    data class ParenExpr(
        val expr: Expr,
    ) : Expr()

    data object NoneValue : Expr()

    data class Record(
        val expression: List<Pair<String, Expr>>,
    ) : Expr()

    data class StringLiteral(
        val value: String,
    ) : Expr()

    data class RefExpr(
        val expr: Expr,
    ) : Expr()

    data class DerefExpr(
        val expr: Expr,
    ) : Expr()

    data class FieldAccess(
        val lhs: Expr,
        val rhs: Expr,
    ) : Expr()

    data class ArrayInit(
        val elements: List<Expr>,
    ) : Expr()

    data class ArrayAccess(
        val array: Expr,
        val index: Expr,
    ) : Expr()

    sealed class FuncCallExpr : Expr()

    data class NamedFunctionCall(
        val name: String,
        val arguments: List<Expr>,
    ) : FuncCallExpr()

    data class ExpressionFunctionCall(
        val target: Expr,
        val arguments: List<Expr>,
    ) : FuncCallExpr()

    data class InlinedFunction(
        val params: List<String>,
        val body: Stmt.BlockStmt,
    ) : Expr()
}

fun SlastNode.prettyPrint(tabStop: Int = 0): String {
    val indent = "  ".repeat(tabStop)
    return when (this) {
        is Expr.BinaryExpr -> "${left.prettyPrint()} $op ${right.prettyPrint()}"
        is Expr.BoolLiteral -> value.toString()
        is Expr.ExpressionFunctionCall -> "$target(${arguments.joinToString(", ") { it.prettyPrint() }})"
        is Expr.NamedFunctionCall -> "$name(${arguments.joinToString(", ") { it.prettyPrint() }})"
        is Expr.IfExpr -> "if (${condition.prettyPrint()}) then ${thenExpr.prettyPrint()} else ${elseExpr.prettyPrint()}"
        is Expr.NumberLiteral -> value.toString()
        is Expr.ParenExpr -> "(${expr.prettyPrint()})"
        Expr.ReadInputExpr -> "readInput()"
        is Expr.VarExpr -> name
        is ProgramUnit -> stmt.joinToString("\n") { it.prettyPrint() }
        is Stmt.AssignStmt -> "$indent${lhs.prettyPrint()} = ${expr.prettyPrint()};"
        is Stmt.BlockStmt -> "$indent{\n" + stmts.joinToString("\n") { it.prettyPrint(tabStop + 1) } + "\n$indent}"
        is Stmt.ExprStmt -> "$indent${expr.prettyPrint()};"
        is Stmt.Function -> "$indent fun $name(${params.joinToString(", ")}) {\n" + body.prettyPrint(tabStop + 1) + "\n$indent}"
        is Expr.NoneValue -> "None"
        is Expr.InlinedFunction -> "$indent inline_fun (${params.joinToString(", ")}) => ${body.prettyPrint()}"
        is Stmt.IfStmt ->
            "$indent if (${condition.prettyPrint()}) {\n" + thenBody.prettyPrint(tabStop + 1) + "\n$indent} else {\n" +
                elseBody.prettyPrint(
                    tabStop + 1,
                ) + "\n$indent}"

        is Stmt.LetStmt -> "$indent let $name = ${expr.prettyPrint()};"
        is Stmt.PrintStmt -> "$indent print(${args.joinToString(", ") { it.prettyPrint() }});"
        is Stmt.ReturnStmt -> "$indent return ${expr.prettyPrint()};"
        is Stmt.WhileStmt -> "$indent while (${condition.prettyPrint()}) {\n" + body.prettyPrint(tabStop + 1) + "\n$indent}"
        is Expr.Record ->
            "$indent{\n" + expression.joinToString("\n") { "$indent  ${it.first} : ${it.second.prettyPrint()}" } +
                "\n$indent}"
        is Expr.StringLiteral -> "\"$value\""
        is Expr.DerefExpr -> "deref(${expr.prettyPrint()})"
        is Expr.RefExpr -> "ref(${expr.prettyPrint()})"
        is Stmt.DerefStmt -> "$indent deref(${lhs.prettyPrint()}) = ${rhs.prettyPrint()};"
        is Expr.FieldAccess -> "${lhs.prettyPrint()}.${rhs.prettyPrint()}"
        is Stmt.StructStmt ->
            "$indent struct $id {\n" + functions.joinToString("\n") { it.prettyPrint(tabStop + 1) } + "\n$indent}" +
                fields.entries.joinToString(
                    "\n",
                ) { "$indent ${it.key} : ${it.value.prettyPrint()}" }

        is Expr.ArrayAccess -> "${array.prettyPrint()}[${index.prettyPrint()}]"
        is Expr.ArrayInit -> "$indent [${elements.joinToString(", ") { it.prettyPrint() }}]"
        is Stmt.Break -> "$indent break;"
        is Stmt.Continue -> "$indent continue;"
    }
}

fun main() {
    val inputCode =
        """
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
    println(string2hlir(inputCode))
}
