package slang.slast

import SlangBaseVisitor
import SlangLexer
import SlangParser
import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext


class SlastBuilder(ctx: SlangParser.CompilationUnitContext) {
    val compilationUnit: CompilationUnit

    init {
        val irBuilder = IRBuilder()
        compilationUnit = irBuilder.visit(ctx) as CompilationUnit
    }

    class IRBuilder : SlangBaseVisitor<SlastNode>() {

        private fun createSourceCodeInfo(ctx: ParserRuleContext): SourceCodeInfo {
            return SourceCodeInfo(
                ctx.start.line,
                ctx.stop.line,
                ctx.start.charPositionInLine,
                ctx.stop.charPositionInLine
            )
        }

        override fun visitLetExpr(ctx: SlangParser.LetExprContext): SlastNode {
            val expr = LetStmt(ctx.ID().text, visit(ctx.expr()) as Expr)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitAssignExpr(ctx: SlangParser.AssignExprContext): SlastNode {
            val expr = AssignStmt(visit(ctx.lhs()) as Expr, visit(ctx.expr()) as Expr)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitFunPureStmt(ctx: SlangParser.FunPureStmtContext): SlastNode {
            val params = ctx.paramList()?.ID()?.map { it.text } ?: emptyList()
            val id = ctx.ID()
            val body = BlockStmt(listOf(ReturnStmt(visit(ctx.expr()) as Expr)))
            val expr = Function(id.text, params, body)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitFunImpureStmt(ctx: SlangParser.FunImpureStmtContext): SlastNode {
            val params = ctx.paramList()?.ID()?.map { it.text } ?: emptyList()
            val body = ctx.stmt().map { visit(it) as Stmt }
            val expr = Function(ctx.ID().text, params, BlockStmt(body))
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }


        override fun visitFunAnonymousPureExpr(ctx: SlangParser.FunAnonymousPureExprContext): SlastNode {
            val params = ctx.paramList()?.ID()?.map { it.text } ?: emptyList()
            val body = BlockStmt(listOf(ReturnStmt(visit(ctx.expr()) as Expr)))
            val expr = InlinedFunction(params, body)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitFunAnonymousImpureExpr(ctx: SlangParser.FunAnonymousImpureExprContext): SlastNode {
            val params = ctx.paramList()?.ID()?.map { it.text } ?: emptyList()
            val body = ctx.stmt().map { visit(it) as Stmt }
            val expr = InlinedFunction(params, BlockStmt(body))
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitWhileStmt(ctx: SlangParser.WhileStmtContext): SlastNode {
            val condition = visit(ctx.expr()) as Expr
            val body = BlockStmt(ctx.blockStmt().stmt().map { visit(it) as Stmt })
            val expr = WhileStmt(condition, body)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitPrintStmt(ctx: SlangParser.PrintStmtContext): SlastNode {
            val args = ctx.argList()?.expr()?.map { visit(it) as Expr } ?: emptyList()
            val expr = PrintStmt(args)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitIfThenElseStmt(ctx: SlangParser.IfThenElseStmtContext): SlastNode {
            val condition = visit(ctx.expr()) as Expr
            val thenBody = BlockStmt(ctx.blockStmt(0).stmt().map { visit(it) as Stmt })
            val elseBody = BlockStmt(ctx.blockStmt(1).stmt().map { visit(it) as Stmt })
            val expr = IfStmt(condition, thenBody, elseBody)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitExprStmt(ctx: SlangParser.ExprStmtContext): SlastNode {
            val expr = ExprStmt(visit(ctx.expr()) as Expr)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitReturnStmt(ctx: SlangParser.ReturnStmtContext): SlastNode {
            val expr = ReturnStmt(visit(ctx.expr()) as Expr)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitIntExpr(ctx: SlangParser.IntExprContext): SlastNode {
            val expr = NumberLiteral(ctx.NUMBER().text.toDouble())
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitBoolExpr(ctx: SlangParser.BoolExprContext): SlastNode {
            val expr = BoolLiteral(ctx.BOOL().text.toBoolean())
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitVarExpr(ctx: SlangParser.VarExprContext): SlastNode {
            val expr = VarExpr(ctx.ID().text)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitReadInputExpr(ctx: SlangParser.ReadInputExprContext): SlastNode {
            val expr = ReadInputExpr
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitFuncCallExpr(ctx: SlangParser.FuncCallExprContext): SlastNode {
            val args = ctx.argList()?.expr()?.map { visit(it) as Expr } ?: emptyList()
            val target = visit(ctx.expr()) as Expr
            val expr = if (target is VarExpr) {
                NamedFunctionCall(target.name, args)
            } else {
                ExpressionFunctionCall(target, args)
            }
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitArithmeticExpr(ctx: SlangParser.ArithmeticExprContext): SlastNode {
            val expr =
                BinaryExpr(visit(ctx.expr(0)) as Expr, Operator.fromValue(ctx.op.text), visit(ctx.expr(1)) as Expr)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitComparisonExpr(ctx: SlangParser.ComparisonExprContext): SlastNode {
            val expr =
                BinaryExpr(visit(ctx.expr(0)) as Expr, Operator.fromValue(ctx.op.text), visit(ctx.expr(1)) as Expr)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitBooleanExpr(ctx: SlangParser.BooleanExprContext): SlastNode {
            val expr =
                BinaryExpr(visit(ctx.expr(0)) as Expr, Operator.fromValue(ctx.op.text), visit(ctx.expr(1)) as Expr)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitIfExpr(ctx: SlangParser.IfExprContext): SlastNode {
            val expr = IfExpr(
                visit(ctx.expr(0)) as Expr,
                visit(ctx.expr(1)) as Expr,
                visit(ctx.expr(2)) as Expr
            )
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitParenExpr(ctx: SlangParser.ParenExprContext): SlastNode {
            val expr = ParenExpr(visit(ctx.expr()) as Expr)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitForStmt(ctx: SlangParser.ForStmtContext): SlastNode {
            val initialization = AssignStmt(visit(ctx.ID()) as Expr, visit(ctx.expr(0)) as Expr)
            val condition = visit(ctx.expr(1)) as Expr
            val update = visit(ctx.expr(2)) as Stmt
            val body = ctx.blockStmt().stmt().map { visit(it) as Stmt }
            val expr = BlockStmt(listOf(initialization, WhileStmt(condition, BlockStmt(body + update))))
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitCompilationUnit(ctx: SlangParser.CompilationUnitContext): SlastNode {
            val expr = if (ctx.stmt() == null) {
                CompilationUnit(emptyList())
            } else {
                val statements = ctx.stmt().map { visit(it) as Stmt }
                CompilationUnit(statements)
            }
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitNoneValue(ctx: SlangParser.NoneValueContext): SlastNode {
            val expr = NoneValue
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitRecordExpr(ctx: SlangParser.RecordExprContext): SlastNode {
            val recordIds = ctx.recordElems().ID()
            val recordExprs = ctx.recordElems().expr()
            val recordElementPairs = mutableListOf<Pair<String, Expr>>()
            for (i in 0 until recordIds.size) {
                recordElementPairs.addLast(Pair(recordIds[i].text, visit(recordExprs[i]) as Expr))
            }
            val expr = Record(recordElementPairs)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitStringExpr(ctx: SlangParser.StringExprContext): SlastNode {
            val expr = StringLiteral(ctx.STRING().text)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitRefExpr(ctx: SlangParser.RefExprContext): SlastNode {
            val expr = RefExpr(visit(ctx.expr()) as Expr)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitDerefExpr(ctx: SlangParser.DerefExprContext): SlastNode {
            val expr = DerefExpr(visit(ctx.deref()) as Expr)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitDeref(ctx: SlangParser.DerefContext): SlastNode {
            val expr = visit(ctx.expr())
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitLhs(ctx: SlangParser.LhsContext): SlastNode {
            val expr = if (ctx.deref() != null) {
                DerefExpr(visit(ctx.deref()) as Expr)
            } else if (ctx.fieldAccess() != null) {
                visit(ctx.fieldAccess())
            } else {
                VarExpr(ctx.ID().text)
            }
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitPrimaryExprWrapper(ctx: SlangParser.PrimaryExprWrapperContext): SlastNode {
            val expr = visit(ctx.primaryExpr())
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitFieldAccess(ctx: SlangParser.FieldAccessContext): SlastNode {
            val expr = FieldAccess(visit(ctx.expr()) as Expr, VarExpr(ctx.ID().text))
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitFieldAccessExpr(ctx: SlangParser.FieldAccessExprContext): SlastNode {
            val expr = FieldAccess(visit(ctx.expr()) as Expr, VarExpr(ctx.ID().text))
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitDoWhileStmt(ctx: SlangParser.DoWhileStmtContext): SlastNode {
            val body = visit(ctx.blockStmt()) as Stmt
            val condition = visit(ctx.expr())
            val expr = BlockStmt(listOf(body, WhileStmt(condition as Expr, body as BlockStmt)))
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitBlockStmt(ctx: SlangParser.BlockStmtContext): SlastNode {
            val expr = BlockStmt(ctx.stmt().map { visit(it) as Stmt })
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }
    }
}

fun main() {
    val x = "fun apply_n(f, n, x) => if (n == 0) then x else f(apply_n(f, n - 1, f(x)));".trimMargin()

    fun parseProgram(input: String): SlastNode {
        val parser = SlangParser(CommonTokenStream(SlangLexer(ANTLRInputStream(input))))

        parser.removeErrorListeners()
        val errorListener = CustomErrorListener()
        parser.addErrorListener(errorListener)

        val parseTree = parser.compilationUnit()

        val IRBuilder = SlastBuilder.IRBuilder()
        val ast = IRBuilder.visit(parseTree) as CompilationUnit
        return ast
    }

    print(parseProgram(x))

}