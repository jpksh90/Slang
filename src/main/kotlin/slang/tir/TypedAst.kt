package slang.tir

import slang.common.CodeInfo
import slang.common.CodeInfo.Companion.generic
import slang.hlir.Operator
import slang.typeinfer.SlangType

/**
 * Base class for all Typed Intermediate Representation (TIR) AST nodes.
 * Every node carries source location and a resolved type.
 */
sealed class TirNode {
    var codeInfo: CodeInfo = generic
    abstract val type: SlangType
}

data class TirProgramUnit(
    val modules: List<TirModule>,
    override val type: SlangType = SlangType.TUnit,
) : TirNode()

data class TirModule(
    val functions: List<TirStmt.Function>,
    val inlinedFuncs: List<TirExpr.InlinedFunction>,
    override val type: SlangType = SlangType.TUnit,
) : TirNode()

sealed class TirStmt : TirNode() {
    data class LetStmt(
        val name: String,
        val expr: TirExpr,
        override val type: SlangType = SlangType.TUnit,
    ) : TirStmt()

    data class AssignStmt(
        val lhs: TirExpr,
        val expr: TirExpr,
        override val type: SlangType = SlangType.TUnit,
    ) : TirStmt()

    data class WhileStmt(
        val condition: TirExpr,
        val body: BlockStmt,
        override val type: SlangType = SlangType.TUnit,
    ) : TirStmt()

    data class PrintStmt(
        val args: List<TirExpr>,
        override val type: SlangType = SlangType.TUnit,
    ) : TirStmt()

    data class IfStmt(
        val condition: TirExpr,
        val thenBody: BlockStmt,
        val elseBody: BlockStmt,
        override val type: SlangType,
    ) : TirStmt()

    data class ExprStmt(
        val expr: TirExpr,
        override val type: SlangType,
    ) : TirStmt()

    data class ReturnStmt(
        val expr: TirExpr,
        override val type: SlangType,
    ) : TirStmt()

    data class BlockStmt(
        val stmts: List<TirStmt>,
        override val type: SlangType,
    ) : TirStmt()

    data class DerefStmt(
        val lhs: TirExpr,
        val rhs: TirExpr,
        override val type: SlangType = SlangType.TUnit,
    ) : TirStmt()

    data class StructStmt(
        val id: String,
        val functions: List<Function>,
        val fields: HashMap<String, TirExpr>,
        override val type: SlangType = SlangType.TUnit,
    ) : TirStmt()

    object Break : TirStmt() {
        override val type: SlangType = SlangType.TUnit
    }

    object Continue : TirStmt() {
        override val type: SlangType = SlangType.TUnit
    }

    data class Function(
        val name: String,
        val params: List<Pair<String, SlangType>>,
        val body: BlockStmt,
        val returnType: SlangType,
        override val type: SlangType,
    ) : TirStmt()
}

sealed class TirExpr : TirNode() {
    data class NumberLiteral(
        val value: Double,
    ) : TirExpr() {
        override val type: SlangType = SlangType.TNum
    }

    data class BoolLiteral(
        val value: Boolean,
    ) : TirExpr() {
        override val type: SlangType = SlangType.TBool
    }

    data class VarExpr(
        val name: String,
        override val type: SlangType,
    ) : TirExpr()

    data class ReadInputExpr(
        override val type: SlangType,
    ) : TirExpr()

    data class BinaryExpr(
        val left: TirExpr,
        val op: Operator,
        val right: TirExpr,
        override val type: SlangType,
    ) : TirExpr()

    data class IfExpr(
        val condition: TirExpr,
        val thenExpr: TirExpr,
        val elseExpr: TirExpr,
        override val type: SlangType,
    ) : TirExpr()

    data class ParenExpr(
        val expr: TirExpr,
        override val type: SlangType,
    ) : TirExpr()

    data class NoneValue(
        override val type: SlangType = SlangType.TNone,
    ) : TirExpr()

    data class Record(
        val expression: List<Pair<String, TirExpr>>,
        override val type: SlangType,
    ) : TirExpr()

    data class StringLiteral(
        val value: String,
    ) : TirExpr() {
        override val type: SlangType = SlangType.TString
    }

    data class RefExpr(
        val expr: TirExpr,
        override val type: SlangType,
    ) : TirExpr()

    data class DerefExpr(
        val expr: TirExpr,
        override val type: SlangType,
    ) : TirExpr()

    data class FieldAccess(
        val lhs: TirExpr,
        val rhs: TirExpr,
        override val type: SlangType,
    ) : TirExpr()

    data class ArrayInit(
        val elements: List<TirExpr>,
        override val type: SlangType,
    ) : TirExpr()

    data class ArrayAccess(
        val array: TirExpr,
        val index: TirExpr,
        override val type: SlangType,
    ) : TirExpr()

    sealed class FuncCallExpr : TirExpr()

    data class NamedFunctionCall(
        val name: String,
        val arguments: List<TirExpr>,
        override val type: SlangType,
    ) : FuncCallExpr()

    data class ExpressionFunctionCall(
        val target: TirExpr,
        val arguments: List<TirExpr>,
        override val type: SlangType,
    ) : FuncCallExpr()

    data class InlinedFunction(
        val params: List<Pair<String, SlangType>>,
        val body: TirStmt.BlockStmt,
        override val type: SlangType,
    ) : TirExpr()
}

// ---- Pretty-printing with type annotations ----

fun TirNode.prettyPrint(tabStop: Int = 0): String {
    val indent = "  ".repeat(tabStop)
    return when (this) {
        is TirExpr.BinaryExpr -> "(${left.prettyPrint()} $op ${right.prettyPrint()}) : $type"
        is TirExpr.BoolLiteral -> "$value : $type"
        is TirExpr.ExpressionFunctionCall -> "${target.prettyPrint()}(${arguments.joinToString(", ") { it.prettyPrint() }}) : $type"
        is TirExpr.NamedFunctionCall -> "$name(${arguments.joinToString(", ") { it.prettyPrint() }}) : $type"
        is TirExpr.IfExpr -> "(if (${condition.prettyPrint()}) then ${thenExpr.prettyPrint()} else ${elseExpr.prettyPrint()}) : $type"
        is TirExpr.NumberLiteral -> "$value : $type"
        is TirExpr.ParenExpr -> "(${expr.prettyPrint()}) : $type"
        is TirExpr.ReadInputExpr -> "readInput() : $type"
        is TirExpr.VarExpr -> "$name : $type"
        is TirProgramUnit -> modules.joinToString("\n") { it.prettyPrint() }
        is TirStmt.AssignStmt -> "$indent${lhs.prettyPrint()} = ${expr.prettyPrint()};"
        is TirStmt.BlockStmt -> "$indent{\n" + stmts.joinToString("\n") { it.prettyPrint(tabStop + 1) } + "\n$indent}"
        is TirStmt.ExprStmt -> "$indent${expr.prettyPrint()};"
        is TirStmt.Function -> {
            val paramStr = params.joinToString(", ") { "${it.first}: ${it.second}" }
            "$indent fun $name($paramStr): $returnType {\n" + body.prettyPrint(tabStop + 1) + "\n$indent}"
        }
        is TirExpr.NoneValue -> "None : $type"
        is TirExpr.InlinedFunction -> {
            val paramStr = params.joinToString(", ") { "${it.first}: ${it.second}" }
            "$indent fun($paramStr) => ${body.prettyPrint()} : $type"
        }
        is TirStmt.IfStmt ->
            "$indent if (${condition.prettyPrint()}) {\n" + thenBody.prettyPrint(tabStop + 1) + "\n$indent} else {\n" +
                elseBody.prettyPrint(tabStop + 1) + "\n$indent}"
        is TirStmt.LetStmt -> "$indent let $name = ${expr.prettyPrint()};"
        is TirStmt.PrintStmt -> "$indent print(${args.joinToString(", ") { it.prettyPrint() }});"
        is TirStmt.ReturnStmt -> "$indent return ${expr.prettyPrint()};"
        is TirStmt.WhileStmt -> "$indent while (${condition.prettyPrint()}) {\n" + body.prettyPrint(tabStop + 1) + "\n$indent}"
        is TirExpr.Record ->
            "$indent{\n" + expression.joinToString("\n") { "$indent  ${it.first} : ${it.second.prettyPrint()}" } +
                "\n$indent} : $type"
        is TirExpr.StringLiteral -> "\"$value\" : $type"
        is TirExpr.DerefExpr -> "deref(${expr.prettyPrint()}) : $type"
        is TirExpr.RefExpr -> "ref(${expr.prettyPrint()}) : $type"
        is TirStmt.DerefStmt -> "$indent deref(${lhs.prettyPrint()}) = ${rhs.prettyPrint()};"
        is TirExpr.FieldAccess -> "${lhs.prettyPrint()}.${rhs.prettyPrint()} : $type"
        is TirStmt.StructStmt ->
            "$indent struct $id {\n" + functions.joinToString("\n") { it.prettyPrint(tabStop + 1) } + "\n$indent}" +
                fields.entries.joinToString("\n") { "$indent ${it.key} : ${it.value.prettyPrint()}" }
        is TirExpr.ArrayAccess -> "${array.prettyPrint()}[${index.prettyPrint()}] : $type"
        is TirExpr.ArrayInit -> "$indent [${elements.joinToString(", ") { it.prettyPrint() }}] : $type"
        is TirStmt.Break -> "$indent break;"
        is TirStmt.Continue -> "$indent continue;"
        is TirModule -> {
            val funcsStr = functions.joinToString("\n") { it.prettyPrint(tabStop) }
            val inlinedStr = inlinedFuncs.joinToString("\n") { it.prettyPrint(tabStop) }
            listOf(funcsStr, inlinedStr).filter { it.isNotBlank() }.joinToString("\n\n")
        }
    }
}
