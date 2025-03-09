package slast

import SimpleLangBaseVisitor
import SimpleLangParser

class ASTBuilder : SimpleLangBaseVisitor<SlastNode>() {

    override fun visitLetExpr(ctx: SimpleLangParser.LetExprContext): SlastNode {
        return LetStmt(ctx.ID().text, visit(ctx.expr()) as Expr)
    }

    override fun visitAssignExpr(ctx: SimpleLangParser.AssignExprContext): SlastNode {
        return AssignStmt(ctx.ID().text, visit(ctx.expr()) as Expr)
    }

    override fun visitFunPure(ctx: SimpleLangParser.FunPureContext): SlastNode {
        val params = ctx.paramList()?.ID()?.map { it.text } ?: emptyList()
        return FunPureStmt(ctx.ID().text, params, visit(ctx.expr()) as Expr)
    }

    override fun visitFunImpure(ctx: SimpleLangParser.FunImpureContext): SlastNode {
        val params = ctx.paramList()?.ID()?.map { it.text } ?: emptyList()
        val body = ctx.stmt().map { visit(it) as Stmt }
        return FunImpureStmt(ctx.ID().text, params, BlockStmt(body))
    }

    override fun visitWhileStmt(ctx: SimpleLangParser.WhileStmtContext): SlastNode {
        val condition = visit(ctx.expr()) as Expr
        val body = BlockStmt(ctx.blockStmt().stmt().map { visit(it) as Stmt })
        return WhileStmt(condition, body)
    }

    override fun visitPrintStmt(ctx: SimpleLangParser.PrintStmtContext): SlastNode {
        val args = ctx.argList()?.expr()?.map { visit(it) as Expr } ?: emptyList()
        return PrintStmt(args)
    }

    override fun visitIfThenElseStmt(ctx: SimpleLangParser.IfThenElseStmtContext): SlastNode {
        val condition = visit(ctx.expr()) as Expr
        val thenBody = BlockStmt(ctx.ifStmt().stmt().map { visit(it) as Stmt })
        val elseBody = BlockStmt(ctx.elseStmt().stmt().map { visit(it) as Stmt })
        return IfStmt(condition, thenBody, elseBody)
    }

    override fun visitExprStmt(ctx: SimpleLangParser.ExprStmtContext): SlastNode {
        return ExprStmt(visit(ctx.expr()) as Expr)
    }

    override fun visitReturnStmt(ctx: SimpleLangParser.ReturnStmtContext): SlastNode {
        return ReturnStmt(visit(ctx.expr()) as Expr)
    }

    override fun visitIntExpr(ctx: SimpleLangParser.IntExprContext): SlastNode {
        return IntExpr(ctx.INT().text.toInt())
    }

    override fun visitBoolExpr(ctx: SimpleLangParser.BoolExprContext): SlastNode {
        return BoolExpr(ctx.BOOL().text.toBoolean())
    }

    override fun visitVarExpr(ctx: SimpleLangParser.VarExprContext): SlastNode {
        return VarExpr(ctx.ID().text)
    }

    override fun visitReadInputExpr(ctx: SimpleLangParser.ReadInputExprContext): SlastNode {
        return ReadInputExpr
    }

    override fun visitFuncCallExpr(ctx: SimpleLangParser.FuncCallExprContext): SlastNode {
        val args = ctx.argList()?.expr()?.map { visit(it) as Expr } ?: emptyList()
        return FuncCallExpr(ctx.ID().text, args)
    }

    override fun visitArithmeticExpr(ctx: SimpleLangParser.ArithmeticExprContext): SlastNode {
        return BinaryExpr(visit(ctx.expr(0)) as Expr, ctx.op.text, visit(ctx.expr(1)) as Expr)
    }

    override fun visitComparisonExpr(ctx: SimpleLangParser.ComparisonExprContext): SlastNode {
        return BinaryExpr(visit(ctx.expr(0)) as Expr, ctx.op.text, visit(ctx.expr(1)) as Expr)
    }

    override fun visitBooleanExpr(ctx: SimpleLangParser.BooleanExprContext): SlastNode {
        return BinaryExpr(visit(ctx.expr(0)) as Expr, ctx.op.text, visit(ctx.expr(1)) as Expr)
    }

    override fun visitIfExpr(ctx: SimpleLangParser.IfExprContext): SlastNode {
        return IfExpr(
            visit(ctx.expr(0)) as Expr,
            visit(ctx.expr(1)) as Expr,
            visit(ctx.expr(2)) as Expr
        )
    }

    override fun visitParenExpr(ctx: SimpleLangParser.ParenExprContext): SlastNode {
        return ParenExpr(visit(ctx.expr()) as Expr)
    }

    override fun visitForStmt(ctx: SimpleLangParser.ForStmtContext): SlastNode {
        val initialization = AssignStmt(ctx.ID().text, visit(ctx.expr(0)) as Expr)
        val condition = visit(ctx.expr(1)) as Expr
        val update = visit(ctx.expr(2)) as Stmt
        val body = ctx.blockStmt().stmt().map { visit(it) as Stmt }
        val block = BlockStmt(listOf(initialization, WhileStmt(condition, BlockStmt(body + update))))
        return block
    }

    override fun visitProg(ctx: SimpleLangParser.ProgContext): SlastNode {
        val statements = ctx.stmt().map { visit(it) as Stmt }
        return Program(statements)
    }

    override fun visitNoneValue(ctx: SimpleLangParser.NoneValueContext): SlastNode {
        return NoneValue
    }
}
