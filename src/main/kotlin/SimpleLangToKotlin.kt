import slast.*

class SimpleLangToKotlin {
    fun generate(ast: SlastNode): String {
        return when (ast) {
            is Program -> ast.stmt.joinToString("\n") { generate(it) }
            is LetStmt -> "var ${ast.name} = ${generate(ast.expr)}"
            is AssignStmt -> "${generate(ast.lhs)} = ${generate(ast.expr)}"
            is IfStmt -> {
                val elsePart = ast.elseBody?.let { " else { ${generate(it)} }" } ?: ""
                "if (${generate(ast.condition)}) { ${generate(ast.thenBody)} }$elsePart"
            }
            is WhileStmt -> "while (${generate(ast.condition)}) { ${generate(ast.body)} }"
            is PrintStmt -> "println(${ast.args.joinToString(", ") { generate(it) }})"
            is BlockStmt -> ast.stmts.joinToString("\n") { generate(it) }
            is IntExpr -> ast.value.toString()
            is BoolExpr -> ast.value.toString()
            is VarExpr -> ast.name
            is BinaryExpr -> "(${generate(ast.left)} ${ast.op} ${generate(ast.right)})"
            else -> throw IllegalArgumentException("Unknown AST node type")
        }
    }
}

fun main(args: Array<String>) {

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
//    val generator = SimpleLangToKotlin()
//    println(generator.generate(ast))
//}
