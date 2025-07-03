package slast

import SlangBaseVisitor
import SlangLexer
import SlangParser
import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import slang.parser.SlangParserErrorListener


class SlastBuilder(ctx: SlangParser.CompilationUnitContext) {
    val compilationUnit: slast.CompilationUnit

    init {
        val irBuilder = _root_ide_package_.slast.SlastBuilder.IRBuilder()
        compilationUnit = irBuilder.visit(ctx) as slast.CompilationUnit
    }

    class IRBuilder : SlangBaseVisitor<slast.SlastNode>() {

        private fun createSourceCodeInfo(ctx: ParserRuleContext): slast.SourceCodeInfo {
            return _root_ide_package_.slast.SourceCodeInfo(
                ctx.start.line,
                ctx.stop.line,
                ctx.start.charPositionInLine,
                ctx.stop.charPositionInLine
            )
        }

        override fun visitLetExpr(ctx: SlangParser.LetExprContext): slast.SlastNode {
            val expr = _root_ide_package_.slast.LetStmt(ctx.ID().text, visit(ctx.expr()) as slast.Expr)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitAssignExpr(ctx: SlangParser.AssignExprContext): _root_ide_package_.slast.SlastNode {
            val expr = _root_ide_package_.slast.AssignStmt(
                visit(ctx.lhs()) as _root_ide_package_.slast.Expr,
                visit(ctx.expr()) as _root_ide_package_.slast.Expr
            )
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitFunPureStmt(ctx: SlangParser.FunPureStmtContext): _root_ide_package_.slast.SlastNode {
            val params = ctx.paramList()?.ID()?.map { it.text } ?: emptyList()
            val id = ctx.ID()
            val body =
                _root_ide_package_.slast.BlockStmt(listOf(_root_ide_package_.slast.ReturnStmt(visit(ctx.expr()) as _root_ide_package_.slast.Expr)))
            val expr = _root_ide_package_.slast.Function(id.text, params, body)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitFunImpureStmt(ctx: SlangParser.FunImpureStmtContext): _root_ide_package_.slast.SlastNode {
            val params = ctx.paramList()?.ID()?.map { it.text } ?: emptyList()
            val body = ctx.stmt().map { visit(it) as _root_ide_package_.slast.Stmt }
            val expr =
                _root_ide_package_.slast.Function(ctx.ID().text, params, _root_ide_package_.slast.BlockStmt(body))
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }


        override fun visitFunAnonymousPureExpr(ctx: SlangParser.FunAnonymousPureExprContext): _root_ide_package_.slast.SlastNode {
            val params = ctx.paramList()?.ID()?.map { it.text } ?: emptyList()
            val body =
                _root_ide_package_.slast.BlockStmt(listOf(_root_ide_package_.slast.ReturnStmt(visit(ctx.expr()) as _root_ide_package_.slast.Expr)))
            val expr = _root_ide_package_.slast.InlinedFunction(params, body)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitFunAnonymousImpureExpr(ctx: SlangParser.FunAnonymousImpureExprContext): _root_ide_package_.slast.SlastNode {
            val params = ctx.paramList()?.ID()?.map { it.text } ?: emptyList()
            val body = ctx.stmt().map { visit(it) as _root_ide_package_.slast.Stmt }
            val expr = _root_ide_package_.slast.InlinedFunction(params, _root_ide_package_.slast.BlockStmt(body))
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitWhileStmt(ctx: SlangParser.WhileStmtContext): _root_ide_package_.slast.SlastNode {
            val condition = visit(ctx.expr()) as _root_ide_package_.slast.Expr
            val body = _root_ide_package_.slast.BlockStmt(
                ctx.blockStmt().stmt().map { visit(it) as _root_ide_package_.slast.Stmt })
            val expr = _root_ide_package_.slast.WhileStmt(condition, body)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitPrintStmt(ctx: SlangParser.PrintStmtContext): _root_ide_package_.slast.SlastNode {
            val args = ctx.argList()?.expr()?.map { visit(it) as _root_ide_package_.slast.Expr } ?: emptyList()
            val expr = _root_ide_package_.slast.PrintStmt(args)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitIfThenElseStmt(ctx: SlangParser.IfThenElseStmtContext): _root_ide_package_.slast.SlastNode {
            val condition = visit(ctx.expr()) as _root_ide_package_.slast.Expr
            val thenBody = _root_ide_package_.slast.BlockStmt(
                ctx.blockStmt(0).stmt().map { visit(it) as _root_ide_package_.slast.Stmt })
            val elseBody = _root_ide_package_.slast.BlockStmt(
                ctx.blockStmt(1).stmt().map { visit(it) as _root_ide_package_.slast.Stmt })
            val expr = _root_ide_package_.slast.IfStmt(condition, thenBody, elseBody)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitExprStmt(ctx: SlangParser.ExprStmtContext): _root_ide_package_.slast.SlastNode {
            val expr = _root_ide_package_.slast.ExprStmt(visit(ctx.expr()) as _root_ide_package_.slast.Expr)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitReturnStmt(ctx: SlangParser.ReturnStmtContext): _root_ide_package_.slast.SlastNode {
            val expr = _root_ide_package_.slast.ReturnStmt(visit(ctx.expr()) as _root_ide_package_.slast.Expr)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitIntExpr(ctx: SlangParser.IntExprContext): _root_ide_package_.slast.SlastNode {
            val expr = _root_ide_package_.slast.NumberLiteral(ctx.NUMBER().text.toDouble())
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitBoolExpr(ctx: SlangParser.BoolExprContext): _root_ide_package_.slast.SlastNode {
            val expr = _root_ide_package_.slast.BoolLiteral(ctx.BOOL().text.toBoolean())
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitVarExpr(ctx: SlangParser.VarExprContext): _root_ide_package_.slast.SlastNode {
            val expr = _root_ide_package_.slast.VarExpr(ctx.ID().text)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitReadInputExpr(ctx: SlangParser.ReadInputExprContext): _root_ide_package_.slast.SlastNode {
            val expr = _root_ide_package_.slast.ReadInputExpr
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitFuncCallExpr(ctx: SlangParser.FuncCallExprContext): _root_ide_package_.slast.SlastNode {
            val args = ctx.argList()?.expr()?.map { visit(it) as _root_ide_package_.slast.Expr } ?: emptyList()
            val target = visit(ctx.expr()) as _root_ide_package_.slast.Expr
            val expr = if (target is _root_ide_package_.slast.VarExpr) {
                _root_ide_package_.slast.NamedFunctionCall(target.name, args)
            } else {
                _root_ide_package_.slast.ExpressionFunctionCall(target, args)
            }
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitArithmeticExpr(ctx: SlangParser.ArithmeticExprContext): _root_ide_package_.slast.SlastNode {
            val expr =
                _root_ide_package_.slast.BinaryExpr(
                    visit(ctx.expr(0)) as _root_ide_package_.slast.Expr,
                    _root_ide_package_.slast.Operator.Companion.fromValue(ctx.op.text),
                    visit(ctx.expr(1)) as _root_ide_package_.slast.Expr
                )
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitComparisonExpr(ctx: SlangParser.ComparisonExprContext): _root_ide_package_.slast.SlastNode {
            val expr =
                _root_ide_package_.slast.BinaryExpr(
                    visit(ctx.expr(0)) as _root_ide_package_.slast.Expr,
                    _root_ide_package_.slast.Operator.Companion.fromValue(ctx.op.text),
                    visit(ctx.expr(1)) as _root_ide_package_.slast.Expr
                )
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitBooleanExpr(ctx: SlangParser.BooleanExprContext): _root_ide_package_.slast.SlastNode {
            val expr =
                _root_ide_package_.slast.BinaryExpr(
                    visit(ctx.expr(0)) as _root_ide_package_.slast.Expr,
                    _root_ide_package_.slast.Operator.Companion.fromValue(ctx.op.text),
                    visit(ctx.expr(1)) as _root_ide_package_.slast.Expr
                )
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitIfExpr(ctx: SlangParser.IfExprContext): _root_ide_package_.slast.SlastNode {
            val expr = _root_ide_package_.slast.IfExpr(
                visit(ctx.expr(0)) as _root_ide_package_.slast.Expr,
                visit(ctx.expr(1)) as _root_ide_package_.slast.Expr,
                visit(ctx.expr(2)) as _root_ide_package_.slast.Expr
            )
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitParenExpr(ctx: SlangParser.ParenExprContext): _root_ide_package_.slast.SlastNode {
            val expr = _root_ide_package_.slast.ParenExpr(visit(ctx.expr()) as _root_ide_package_.slast.Expr)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitForStmt(ctx: SlangParser.ForStmtContext): _root_ide_package_.slast.SlastNode {
            val initialization = _root_ide_package_.slast.AssignStmt(
                visit(ctx.ID()) as _root_ide_package_.slast.Expr,
                visit(ctx.expr(0)) as _root_ide_package_.slast.Expr
            )
            val condition = visit(ctx.expr(1)) as _root_ide_package_.slast.Expr
            val update = visit(ctx.expr(2)) as _root_ide_package_.slast.Stmt
            val body = ctx.blockStmt().stmt().map { visit(it) as _root_ide_package_.slast.Stmt }
            val expr = _root_ide_package_.slast.BlockStmt(
                listOf(
                    initialization,
                    _root_ide_package_.slast.WhileStmt(condition, _root_ide_package_.slast.BlockStmt(body + update))
                )
            )
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitCompilationUnit(ctx: SlangParser.CompilationUnitContext): _root_ide_package_.slast.SlastNode {
            val expr = if (ctx.stmt() == null) {
                _root_ide_package_.slast.CompilationUnit(emptyList())
            } else {
                val statements = ctx.stmt().map { visit(it) as _root_ide_package_.slast.Stmt }
                _root_ide_package_.slast.CompilationUnit(statements)
            }
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitNoneValue(ctx: SlangParser.NoneValueContext): _root_ide_package_.slast.SlastNode {
            val expr = _root_ide_package_.slast.NoneValue
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitRecordExpr(ctx: SlangParser.RecordExprContext): _root_ide_package_.slast.SlastNode {
            val recordIds = ctx.recordElems().ID()
            val recordExprs = ctx.recordElems().expr()
            val recordElementPairs = mutableListOf<Pair<String, _root_ide_package_.slast.Expr>>()
            for (i in 0 until recordIds.size) {
                recordElementPairs.addLast(Pair(recordIds[i].text, visit(recordExprs[i]) as _root_ide_package_.slast.Expr))
            }
            val expr = _root_ide_package_.slast.Record(recordElementPairs)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitStringExpr(ctx: SlangParser.StringExprContext): _root_ide_package_.slast.SlastNode {
            val expr = _root_ide_package_.slast.StringLiteral(ctx.STRING().text)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitRefExpr(ctx: SlangParser.RefExprContext): _root_ide_package_.slast.SlastNode {
            val expr = _root_ide_package_.slast.RefExpr(visit(ctx.expr()) as _root_ide_package_.slast.Expr)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitDerefExpr(ctx: SlangParser.DerefExprContext): _root_ide_package_.slast.SlastNode {
            val expr = _root_ide_package_.slast.DerefExpr(visit(ctx.deref()) as _root_ide_package_.slast.Expr)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitDeref(ctx: SlangParser.DerefContext): _root_ide_package_.slast.SlastNode {
            val expr = visit(ctx.expr())
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitLhs(ctx: SlangParser.LhsContext): _root_ide_package_.slast.SlastNode {
            val expr = if (ctx.deref() != null) {
                _root_ide_package_.slast.DerefExpr(visit(ctx.deref()) as _root_ide_package_.slast.Expr)
            } else if (ctx.fieldAccess() != null) {
                visit(ctx.fieldAccess())
            } else {
                _root_ide_package_.slast.VarExpr(ctx.ID().text)
            }
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitPrimaryExprWrapper(ctx: SlangParser.PrimaryExprWrapperContext): _root_ide_package_.slast.SlastNode {
            val expr = visit(ctx.primaryExpr())
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitFieldAccess(ctx: SlangParser.FieldAccessContext): _root_ide_package_.slast.SlastNode {
            val expr = _root_ide_package_.slast.FieldAccess(
                visit(ctx.expr()) as _root_ide_package_.slast.Expr,
                _root_ide_package_.slast.VarExpr(ctx.ID().text)
            )
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitFieldAccessExpr(ctx: SlangParser.FieldAccessExprContext): _root_ide_package_.slast.SlastNode {
            val expr = _root_ide_package_.slast.FieldAccess(
                visit(ctx.expr()) as _root_ide_package_.slast.Expr,
                _root_ide_package_.slast.VarExpr(ctx.ID().text)
            )
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitDoWhileStmt(ctx: SlangParser.DoWhileStmtContext): _root_ide_package_.slast.SlastNode {
            val body = visit(ctx.blockStmt()) as _root_ide_package_.slast.Stmt
            val condition = visit(ctx.expr())
            val expr = _root_ide_package_.slast.BlockStmt(
                listOf(
                    body,
                    _root_ide_package_.slast.WhileStmt(
                        condition as _root_ide_package_.slast.Expr,
                        body as _root_ide_package_.slast.BlockStmt
                    )
                )
            )
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitBlockStmt(ctx: SlangParser.BlockStmtContext): _root_ide_package_.slast.SlastNode {
            val expr = _root_ide_package_.slast.BlockStmt(ctx.stmt().map { visit(it) as _root_ide_package_.slast.Stmt })
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitStructStmt(ctx: SlangParser.StructStmtContext): _root_ide_package_.slast.SlastNode {
            val id = ctx.ID().text
            val fields = HashMap<String, _root_ide_package_.slast.Expr>()
            val methods = mutableListOf<_root_ide_package_.slast.Function>()

            for (argument in ctx.constructorMembers().ID()) {
                val fieldName = argument.text
                fields[fieldName] = _root_ide_package_.slast.NoneValue
            }

            for (member in ctx.structMember()) {
                if (member is SlangParser.StructFieldContext) {
                    val fieldName = member.ID().text
                    val fieldExpr = visit(member.expr()) as _root_ide_package_.slast.Expr
                    fields[fieldName] = fieldExpr
                }

                if (member is SlangParser.StructMethodPureContext) {
                    methods.addLast(visit(member) as _root_ide_package_.slast.Function)
                }

                if (member is SlangParser.StructMethodImpureContext) {
                    methods.addLast(visit(member) as _root_ide_package_.slast.Function)
                }
            }
            val expr = _root_ide_package_.slast.StructStmt(id, methods, fields)
            expr.sourceCodeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitBreakStmt(ctx: SlangParser.BreakStmtContext?): _root_ide_package_.slast.SlastNode {
            return _root_ide_package_.slast.Break
        }

        override fun visitContinueStmt(ctx: SlangParser.ContinueStmtContext?): _root_ide_package_.slast.SlastNode {
            return _root_ide_package_.slast.Continue
        }

    }
}

fun main() {
    val x = "fun apply_n(f, n, x) => if (n == 0) then x else f(apply_n(f, n - 1, f(x)));".trimMargin()

    fun parseProgram(input: String): _root_ide_package_.slast.SlastNode {
        val parser = SlangParser(CommonTokenStream(SlangLexer(ANTLRInputStream(input))))

        parser.removeErrorListeners()
        val errorListener = SlangParserErrorListener()
        parser.addErrorListener(errorListener)

        val parseTree = parser.compilationUnit()

        val IRBuilder = _root_ide_package_.slast.SlastBuilder.IRBuilder()
        val ast = IRBuilder.visit(parseTree) as _root_ide_package_.slast.CompilationUnit
        return ast
    }

    print(parseProgram(x))

}