import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream

enum class Type {
    INT, BOOL, UNKNOWN, UNIT
}


class StrictTypeChecker : SimpleLangBaseVisitor<Type>() {

    private val symbolTable = mutableMapOf<String, Type>()


    override fun visitIntExpr(ctx: SimpleLangParser.IntExprContext): Type {
        return Type.INT
    }

    override fun visitBoolExpr(ctx: SimpleLangParser.BoolExprContext): Type {
        return Type.BOOL
    }

    override fun visitVarExpr(ctx: SimpleLangParser.VarExprContext): Type {
        return Type.UNKNOWN
    }

    override fun visitArithmeticExpr(ctx: SimpleLangParser.ArithmeticExprContext): Type {
        val leftType = visit(ctx.expr(0))
        val rightType = visit(ctx.expr(1))

        val allowedTypes = listOf(Type.INT, Type.UNKNOWN)

        if ( !allowedTypes.contains(leftType) || !allowedTypes.contains(rightType) ) {
            throw IllegalArgumentException("Error: Cannot add any data type to integer in '${ctx.text}' at [${ctx
                .start.line}:${ctx.start.startIndex}] -- [${ctx.stop.line}:${ctx.stop.startIndex}]")
        }
        return Type.INT
    }

    override fun visitBooleanExpr(ctx: SimpleLangParser.BooleanExprContext) = Type.BOOL

    override fun visitComparisonExpr(ctx: SimpleLangParser.ComparisonExprContext): Type = Type.BOOL

    override fun visitLetExpr(ctx: SimpleLangParser.LetExprContext): Type {
        val id = ctx.ID()
        val exprType = visit(ctx.expr())
        symbolTable[id.text] = exprType
        return Type.UNIT
    }

    override fun visitReadInputExpr(ctx: SimpleLangParser.ReadInputExprContext?): Type {
        return Type.UNKNOWN
    }
}

fun main() {
   val program = """
       fun power(base, exp) {
           let result = 1;

           while exp > 0 {
               result = result * base;
               exp = exp - 1;
           }
           return result;
       }

       let b = readInput();
       let e = readInput();
       print(power(b,e));
       let p = true + 1;
   """.trimIndent()

    val inputStream = ANTLRInputStream(program)
    val lexer = SimpleLangLexer(inputStream)
    val tokenStream = CommonTokenStream(lexer)
    val parser = SimpleLangParser(tokenStream)
    val tree = parser.prog()

    try {
        val typeChecker = StrictTypeChecker()
        typeChecker.visit(tree) // Visit the parse tree
        println("✅ Type checking passed! No errors found.")
    } catch (e: IllegalArgumentException) {
        println("❌ Type checking failed: ${e.message}")
    }
}