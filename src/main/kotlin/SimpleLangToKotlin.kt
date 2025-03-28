import slang.slast.*
import slang.slast.Function

class SlangToKotlin {
    fun generate(ast: SlastNode): String {
        return when (ast) {
            is CompilationUnit -> ast.stmt.joinToString("\n") { generate(it) }
            is LetStmt -> "var ${ast.name} = ${generate(ast.expr)}"
            is AssignStmt -> "${generate(ast.lhs)} = ${generate(ast.expr)}"
            is IfStmt -> {
                val elsePart = ast.elseBody.let { " else { ${generate(it)} }" }
                "if (${generate(ast.condition)}) { ${generate(ast.thenBody)} }$elsePart"
            }
            is WhileStmt -> "while (${generate(ast.condition)}) { ${generate(ast.body)} }"
            is PrintStmt -> "println(${ast.args.joinToString(", ") { generate(it) }})"
            is BlockStmt -> ast.stmts.joinToString("\n") { generate(it) }
            is NumberLiteral -> ast.value.toString()
            is BoolLiteral -> ast.value.toString()
            is VarExpr -> ast.name
            is BinaryExpr -> "(${generate(ast.left)} ${ast.op} ${generate(ast.right)})"
            is DerefExpr -> TODO()
            is FieldAccess -> TODO()
            is FuncCallExpr -> TODO()
            is IfExpr -> TODO()
            NoneValue -> TODO()
            is ParenExpr -> TODO()
            ReadInputExpr -> TODO()
            is Record -> TODO()
            is RefExpr -> TODO()
            is StringLiteral -> TODO()
            is DerefStmt -> TODO()
            is ExprStmt -> TODO()
            is Function -> TODO()
            is InlinedFunction -> TODO()
            is ReturnStmt -> TODO()
        }
    }
}

fun main() {

}

//fun main() {
//    val ast = Program(
//        listOf(
//            LetStmt("x", IntExpr(10)),
//            PrintStmt(listOf(VarExpr("x"))),
//            IfStmt(
//                BinaryExpr(VarExpr("x"), ">", IntExpr(5)),
//                PrintStmt(listOf(VarExpr("x"))),
//                PrintStmt(listOf(IntExpr(0)))
//            )
//        )
//    )
//
//    val generator = SlangToKotlin()
//    println(generator.generate(ast))
//}
