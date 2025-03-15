package slast.ast

import SlangBaseVisitor
import SlangLexer
import SlangParser
import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream

class IRBuilder() : SlangBaseVisitor<SlastNode>() {

    override fun visitLetExpr(ctx: SlangParser.LetExprContext): SlastNode {
        return LetStmt(ctx.ID().text, visit(ctx.expr()) as Expr)
    }

    override fun visitAssignExpr(ctx: SlangParser.AssignExprContext): SlastNode {
        return AssignStmt(visit(ctx.lhs()) as Expr, visit(ctx.expr()) as Expr)
    }

    override fun visitFunPure(ctx: SlangParser.FunPureContext): SlastNode {
        val params = ctx.paramList()?.ID()?.map { it.text } ?: emptyList()
        return FunPureStmt(ctx.ID().text, params, visit(ctx.expr()) as Expr)
    }

    override fun visitFunImpure(ctx: SlangParser.FunImpureContext): SlastNode {
        val params = ctx.paramList()?.ID()?.map { it.text } ?: emptyList()
        val body = ctx.stmt().map { visit(it) as Stmt }
        return FunImpureStmt(ctx.ID().text, params, BlockStmt(body))
    }

    override fun visitWhileStmt(ctx: SlangParser.WhileStmtContext): SlastNode {
        val condition = visit(ctx.expr()) as Expr
        val body = BlockStmt(ctx.blockStmt().stmt().map { visit(it) as Stmt })
        return WhileStmt(condition, body)
    }

    override fun visitPrintStmt(ctx: SlangParser.PrintStmtContext): SlastNode {
        val args = ctx.argList()?.expr()?.map { visit(it) as Expr } ?: emptyList()
        return PrintStmt(args)
    }

    override fun visitIfThenElseStmt(ctx: SlangParser.IfThenElseStmtContext): SlastNode {
        val condition = visit(ctx.expr()) as Expr
        val thenBody = BlockStmt(ctx.blockStmt(0).stmt().map { visit(it) as Stmt })
        val elseBody = BlockStmt(ctx.blockStmt(1).stmt().map { visit(it) as Stmt })
        return IfStmt(condition, thenBody, elseBody)
    }

    override fun visitExprStmt(ctx: SlangParser.ExprStmtContext): SlastNode {
        return ExprStmt(visit(ctx.expr()) as Expr)
    }

    override fun visitReturnStmt(ctx: SlangParser.ReturnStmtContext): SlastNode {
        return ReturnStmt(visit(ctx.expr()) as Expr)
    }

    override fun visitIntExpr(ctx: SlangParser.IntExprContext): SlastNode {
        return NumberLiteral(ctx.NUMBER().text.toDouble())
    }

    override fun visitBoolExpr(ctx: SlangParser.BoolExprContext): SlastNode {
        return BoolLiteral(ctx.BOOL().text.toBoolean())
    }

    override fun visitVarExpr(ctx: SlangParser.VarExprContext): SlastNode {
        return VarExpr(ctx.ID().text)
    }

    override fun visitReadInputExpr(ctx: SlangParser.ReadInputExprContext): SlastNode {
        return ReadInputExpr
    }

    override fun visitFuncCallExpr(ctx: SlangParser.FuncCallExprContext): SlastNode {
        val args = ctx.argList()?.expr()?.map { visit(it) as Expr } ?: emptyList()
        val target = visit(ctx.expr()) as Expr
        if (target is VarExpr) {
            return FuncCallExpr(target.name, args)
        } else {
            return FuncCallExpr("unresolved", args)
        }
    }

    override fun visitArithmeticExpr(ctx: SlangParser.ArithmeticExprContext): SlastNode {
        return BinaryExpr(visit(ctx.expr(0)) as Expr, Operator.fromValue(ctx.op.text), visit(ctx.expr(1)) as Expr)
    }

    override fun visitComparisonExpr(ctx: SlangParser.ComparisonExprContext): SlastNode {
        return BinaryExpr(visit(ctx.expr(0)) as Expr, Operator.fromValue(ctx.op.text), visit(ctx.expr(1)) as Expr)
    }

    override fun visitBooleanExpr(ctx: SlangParser.BooleanExprContext): SlastNode {
        return BinaryExpr(visit(ctx.expr(0)) as Expr, Operator.fromValue(ctx.op.text), visit(ctx.expr(1)) as Expr)
    }

    override fun visitIfExpr(ctx: SlangParser.IfExprContext): SlastNode {
        return IfExpr(
            visit(ctx.expr(0)) as Expr,
            visit(ctx.expr(1)) as Expr,
            visit(ctx.expr(2)) as Expr
        )
    }

    override fun visitParenExpr(ctx: SlangParser.ParenExprContext): SlastNode {
        return ParenExpr(visit(ctx.expr()) as Expr)
    }

    override fun visitForStmt(ctx: SlangParser.ForStmtContext): SlastNode {
        val initialization = AssignStmt(visit(ctx.ID()) as Expr, visit(ctx.expr(0)) as Expr)
        val condition = visit(ctx.expr(1)) as Expr
        val update = visit(ctx.expr(2)) as Stmt
        val body = ctx.blockStmt().stmt().map { visit(it) as Stmt }
        val block = BlockStmt(listOf(initialization, WhileStmt(condition, BlockStmt(body + update))))
        return block
    }

    override fun visitCompilationUnit(ctx: SlangParser.CompilationUnitContext): SlastNode {
        if (ctx.stmt() == null) {
            return CompilationUnit(emptyList())
        } else {
            val statements = ctx.stmt().map { visit(it) as Stmt }
            return CompilationUnit(statements)
        }
    }

    override fun visitNoneValue(ctx: SlangParser.NoneValueContext): SlastNode {
        return NoneValue
    }

    override fun visitRecordExpr(ctx: SlangParser.RecordExprContext): SlastNode {
        val recordIds = ctx.recordElems().ID()
        val recordExprs = ctx.recordElems().expr()
        val recordElementPairs = mutableListOf<Pair<String, Expr>>()
        for (i in 0 until recordIds.size) {
            recordElementPairs.addLast(Pair(recordIds[i].text, visit(recordExprs[i]) as Expr))
        }
        return Record(recordElementPairs)
    }

    override fun visitStringExpr(ctx: SlangParser.StringExprContext): SlastNode {
        return StringLiteral(ctx.STRING().text)
    }

    override fun visitRefExpr(ctx: SlangParser.RefExprContext): SlastNode {
        return RefExpr(visit(ctx.expr()) as Expr)
    }

    override fun visitDerefExpr(ctx: SlangParser.DerefExprContext): SlastNode {
        return DerefExpr(visit(ctx.deref()) as Expr)
    }

    override fun visitDeref(ctx: SlangParser.DerefContext): SlastNode {
        return visit(ctx.expr())
    }

    override fun visitLhs(ctx: SlangParser.LhsContext): SlastNode {
        if (ctx.deref() != null) {
            return DerefExpr(visit(ctx.deref()) as Expr)
        }

        if (ctx.fieldAccess() != null) {
            return visit(ctx.fieldAccess())
        }
        return VarExpr(ctx.ID().text)
    }

    override fun visitPrimaryExprWrapper(ctx: SlangParser.PrimaryExprWrapperContext): SlastNode {
        return visit(ctx.primaryExpr())
    }

    override fun visitFieldAccess(ctx: SlangParser.FieldAccessContext): SlastNode {
        return FieldAccess(visit(ctx.expr()) as Expr, VarExpr(ctx.ID().text))
    }

    override fun visitFieldAccessExpr(ctx: SlangParser.FieldAccessExprContext): SlastNode {
        return FieldAccess(visit(ctx.expr()) as Expr, VarExpr(ctx.ID().text))
    }

    override fun visitDoWhileStmt(ctx: SlangParser.DoWhileStmtContext): SlastNode {
        val body = visit(ctx.blockStmt()) as Stmt
        val condition = visit(ctx.expr())
        return BlockStmt(listOf(body, WhileStmt(condition as Expr, body as BlockStmt)))
    }

    override fun visitBlockStmt(ctx: SlangParser.BlockStmtContext): SlastNode {
            return BlockStmt(ctx.stmt().map { visit(it) as Stmt })
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

        val IRBuilder = IRBuilder()
        val ast = IRBuilder.visit(parseTree) as CompilationUnit
        return ast
    }

    print(parseProgram(x))

}
