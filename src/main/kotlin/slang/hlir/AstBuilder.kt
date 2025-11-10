package slang.hlir

import SlangBaseVisitor
import SlangLexer
import SlangParser
import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import slang.common.CodeInfo
import slang.common.CodeInfo.Companion.generic
import slang.common.Result
import slang.common.Transform
import slang.common.invoke
import slang.common.then
import slang.parser.CompilerError
import slang.parser.File2ParseTreeTransformer
import slang.parser.ParseTree
import slang.parser.SlangParserErrorListener
import slang.parser.String2ParseTreeTransformer
import java.io.File

class SlastBuilder(
    ctx: SlangParser.CompilationUnitContext,
) {
    val program: ProgramUnit

    init {
        val irBuilder = IRBuilder()
        program = irBuilder.visit(ctx) as ProgramUnit
    }

    class IRBuilder : SlangBaseVisitor<SlastNode>() {
        private fun createSourceCodeInfo(ctx: ParserRuleContext): CodeInfo =
            CodeInfo(
                ctx.start.line,
                ctx.stop.line,
                ctx.start.charPositionInLine,
                ctx.stop.charPositionInLine,
            )

        override fun visitLetExpr(ctx: SlangParser.LetExprContext): SlastNode {
            val expr = Stmt.LetStmt(ctx.ID().text, visit(ctx.expr()) as Expr)
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitAssignExpr(ctx: SlangParser.AssignExprContext): SlastNode {
            val expr = Stmt.AssignStmt(visit(ctx.lhs()) as Expr, visit(ctx.expr()) as Expr)
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitFunPureStmt(ctx: SlangParser.FunPureStmtContext): SlastNode {
            val params = ctx.paramList()?.ID()?.map { it.text } ?: emptyList()
            val id = ctx.ID()
            val body = Stmt.BlockStmt(listOf(Stmt.ReturnStmt(visit(ctx.expr()) as Expr)))
            val expr = Stmt.Function(id.text, params, body)
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitFunImpureStmt(ctx: SlangParser.FunImpureStmtContext): SlastNode {
            val params = ctx.paramList()?.ID()?.map { it.text } ?: emptyList()
            val body = ctx.stmt().map { visit(it) as Stmt }
            val expr = Stmt.Function(ctx.ID().text, params, Stmt.BlockStmt(body))
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitFunAnonymousPureExpr(ctx: SlangParser.FunAnonymousPureExprContext): SlastNode {
            val params = ctx.paramList()?.ID()?.map { it.text } ?: emptyList()
            val body = Stmt.BlockStmt(listOf(Stmt.ReturnStmt(visit(ctx.expr()) as Expr)))
            val expr = Expr.InlinedFunction(params, body)
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitFunAnonymousImpureExpr(ctx: SlangParser.FunAnonymousImpureExprContext): SlastNode {
            val params = ctx.paramList()?.ID()?.map { it.text } ?: emptyList()
            val body = ctx.stmt().map { visit(it) as Stmt }
            val expr = Expr.InlinedFunction(params, Stmt.BlockStmt(body))
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitWhileStmt(ctx: SlangParser.WhileStmtContext): SlastNode {
            val condition = visit(ctx.expr()) as Expr
            val body = Stmt.BlockStmt(ctx.blockStmt().stmt().map { visit(it) as Stmt })
            val expr = Stmt.WhileStmt(condition, body)
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitPrintStmt(ctx: SlangParser.PrintStmtContext): SlastNode {
            val args = ctx.argList()?.expr()?.map { visit(it) as Expr } ?: emptyList()
            val expr = Stmt.PrintStmt(args)
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitIfThenElseStmt(ctx: SlangParser.IfThenElseStmtContext): SlastNode {
            val condition = visit(ctx.expr()) as Expr
            val thenBody = Stmt.BlockStmt(ctx.blockStmt(0).stmt().map { visit(it) as Stmt })
            val elseBody = Stmt.BlockStmt(ctx.blockStmt(1).stmt().map { visit(it) as Stmt })
            val expr = Stmt.IfStmt(condition, thenBody, elseBody)
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitExprStmt(ctx: SlangParser.ExprStmtContext): SlastNode {
            val expr = Stmt.ExprStmt(visit(ctx.expr()) as Expr)
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitReturnStmt(ctx: SlangParser.ReturnStmtContext): SlastNode {
            val expr = Stmt.ReturnStmt(visit(ctx.expr()) as Expr)
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitIntExpr(ctx: SlangParser.IntExprContext): SlastNode {
            val expr = Expr.NumberLiteral(ctx.NUMBER().text.toDouble())
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitBoolExpr(ctx: SlangParser.BoolExprContext): SlastNode {
            val expr = Expr.BoolLiteral(ctx.BOOL().text.toBoolean())
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitVarExpr(ctx: SlangParser.VarExprContext): SlastNode {
            val expr = Expr.VarExpr(ctx.ID().text)
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitReadInputExpr(ctx: SlangParser.ReadInputExprContext): SlastNode {
            val expr = Expr.ReadInputExpr
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitFuncCallExpr(ctx: SlangParser.FuncCallExprContext): SlastNode {
            val args = ctx.argList()?.expr()?.map { visit(it) as Expr } ?: emptyList()
            val target = visit(ctx.expr()) as Expr
            val expr =
                if (target is Expr.VarExpr) {
                    Expr.NamedFunctionCall(target.name, args)
                } else {
                    Expr.ExpressionFunctionCall(target, args)
                }
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitArithmeticExpr(ctx: SlangParser.ArithmeticExprContext): SlastNode {
            val expr =
                Expr.BinaryExpr(visit(ctx.expr(0)) as Expr, Operator.fromValue(ctx.op.text), visit(ctx.expr(1)) as Expr)
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitComparisonExpr(ctx: SlangParser.ComparisonExprContext): SlastNode {
            val expr =
                Expr.BinaryExpr(visit(ctx.expr(0)) as Expr, Operator.fromValue(ctx.op.text), visit(ctx.expr(1)) as Expr)
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitBooleanExpr(ctx: SlangParser.BooleanExprContext): SlastNode {
            val expr =
                Expr.BinaryExpr(visit(ctx.expr(0)) as Expr, Operator.fromValue(ctx.op.text), visit(ctx.expr(1)) as Expr)
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitIfExpr(ctx: SlangParser.IfExprContext): SlastNode {
            val expr =
                Expr.IfExpr(
                    visit(ctx.expr(0)) as Expr,
                    visit(ctx.expr(1)) as Expr,
                    visit(ctx.expr(2)) as Expr,
                )
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitParenExpr(ctx: SlangParser.ParenExprContext): SlastNode {
            val expr = Expr.ParenExpr(visit(ctx.expr()) as Expr)
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitForStmt(ctx: SlangParser.ForStmtContext): SlastNode {
            val initialization = Stmt.AssignStmt(visit(ctx.ID()) as Expr, visit(ctx.expr(0)) as Expr)
            val condition = visit(ctx.expr(1)) as Expr
            val update = visit(ctx.expr(2)) as Stmt
            val body = ctx.blockStmt().stmt().map { visit(it) as Stmt }
            val expr = Stmt.BlockStmt(listOf(initialization, Stmt.WhileStmt(condition, Stmt.BlockStmt(body + update))))
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitCompilationUnit(ctx: SlangParser.CompilationUnitContext): SlastNode {
            val expr =
                if (ctx.stmt() == null) {
                    ProgramUnit(emptyList())
                } else {
                    val stmts = ctx.stmt().map { visit(it) as Stmt }
                    val moduleMain = Stmt.Function("__module__main__", emptyList(), Stmt.BlockStmt
                        (stmts.filterNot {
                        it is Stmt.Function || (it is Stmt.ExprStmt && it.expr is Expr.InlinedFunction)
                    }))
                    val topLevelInlineFuncs = stmts.filterIsInstance<Stmt.ExprStmt>().map { it.expr }
                        .filterIsInstance<Expr.InlinedFunction>()
                    val topLevelfuncs = stmts.filterIsInstance<Stmt.Function>()
                    val slangModule = SlangModule(listOf(moduleMain) + topLevelfuncs, topLevelInlineFuncs )
                    ProgramUnit(listOf(slangModule))
                }
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitNoneValue(ctx: SlangParser.NoneValueContext): SlastNode {
            val expr = Expr.NoneValue
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitRecordExpr(ctx: SlangParser.RecordExprContext): SlastNode {
            val recordIds = ctx.recordElems().ID()
            val recordExprs = ctx.recordElems().expr()
            val recordElementPairs = mutableListOf<Pair<String, Expr>>()
            for (i in 0 until recordIds.size) {
                recordElementPairs.addLast(Pair(recordIds[i].text, visit(recordExprs[i]) as Expr))
            }
            val expr = Expr.Record(recordElementPairs)
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitStringExpr(ctx: SlangParser.StringExprContext): SlastNode {
            val expr = Expr.StringLiteral(ctx.STRING().text)
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitRefExpr(ctx: SlangParser.RefExprContext): SlastNode {
            val expr = Expr.RefExpr(visit(ctx.expr()) as Expr)
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitDerefExpr(ctx: SlangParser.DerefExprContext): SlastNode {
            val expr = Expr.DerefExpr(visit(ctx.deref()) as Expr)
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitDeref(ctx: SlangParser.DerefContext): SlastNode {
            val expr = visit(ctx.expr())
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitLhs(ctx: SlangParser.LhsContext): SlastNode {
            val expr =
                if (ctx.deref() != null) {
                    Expr.DerefExpr(visit(ctx.deref()) as Expr)
                } else if (ctx.fieldAccess() != null) {
                    visit(ctx.fieldAccess())
                } else {
                    Expr.VarExpr(ctx.ID().text)
                }
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitPrimaryExprWrapper(ctx: SlangParser.PrimaryExprWrapperContext): SlastNode {
            val expr = visit(ctx.primaryExpr())
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitFieldAccess(ctx: SlangParser.FieldAccessContext): SlastNode {
            val expr = Expr.FieldAccess(visit(ctx.expr()) as Expr, Expr.VarExpr(ctx.ID().text))
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitFieldAccessExpr(ctx: SlangParser.FieldAccessExprContext): SlastNode {
            val expr = Expr.FieldAccess(visit(ctx.expr()) as Expr, Expr.VarExpr(ctx.ID().text))
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitDoWhileStmt(ctx: SlangParser.DoWhileStmtContext): SlastNode {
            val body = visit(ctx.blockStmt()) as Stmt
            val condition = visit(ctx.expr())
            val expr = Stmt.BlockStmt(listOf(body, Stmt.WhileStmt(condition as Expr, body as Stmt.BlockStmt)))
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitBlockStmt(ctx: SlangParser.BlockStmtContext): SlastNode {
            val expr = Stmt.BlockStmt(ctx.stmt().map { visit(it) as Stmt })
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitStructStmt(ctx: SlangParser.StructStmtContext): SlastNode {
            val id = ctx.ID().text
            val fields = HashMap<String, Expr>()
            val methods = mutableListOf<Stmt.Function>()

            for (argument in ctx.constructorMembers().ID()) {
                val fieldName = argument.text
                fields[fieldName] = Expr.NoneValue
            }

            for (member in ctx.structMember()) {
                if (member is SlangParser.StructFieldContext) {
                    val fieldName = member.ID().text
                    val fieldExpr = visit(member.expr()) as Expr
                    fields[fieldName] = fieldExpr
                }

                if (member is SlangParser.StructMethodPureContext) {
                    methods.addLast(visit(member) as Stmt.Function)
                }

                if (member is SlangParser.StructMethodImpureContext) {
                    methods.addLast(visit(member) as Stmt.Function)
                }
            }
            val expr = Stmt.StructStmt(id, methods, fields)
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitArrayLiteralExpression(ctx: SlangParser.ArrayLiteralExpressionContext): SlastNode {
            val elements = ctx.exprList()?.expr()?.map { visit(it) as Expr } ?: emptyList()
            val expr = Expr.ArrayInit(elements)
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitArrayAccessExpr(ctx: SlangParser.ArrayAccessExprContext): SlastNode {
            val array = visit(ctx.expr(0)) as Expr
            val index = visit(ctx.expr(1)) as Expr
            val expr = Expr.ArrayAccess(array, index)
            expr.codeInfo = createSourceCodeInfo(ctx)
            return expr
        }

        override fun visitBreakStmt(ctx: SlangParser.BreakStmtContext?): SlastNode = Stmt.Break

        override fun visitContinueStmt(ctx: SlangParser.ContinueStmtContext?): SlastNode = Stmt.Continue
    }
}

class ParseTree2HlirTrasnformer : Transform<ParseTree, ProgramUnit> {
    override fun transform(input: ParseTree): Result<ProgramUnit, List<CompilerError>> =
        when (val hlir = SlastBuilder.IRBuilder().visit(input)) {
            is ProgramUnit -> Result.ok(hlir)
            else -> Result.err(listOf(CompilerError(generic, "Errors encountered while parsing $input")))
        }
}

fun file2hlir(file: File): Result<ProgramUnit, List<*>> {
    val transformers = File2ParseTreeTransformer() then ParseTree2HlirTrasnformer()
    return transformers.invoke(file)
}

fun string2hlir(string: String): Result<ProgramUnit, List<*>> {
    val transformers = String2ParseTreeTransformer() then ParseTree2HlirTrasnformer()
    return transformers.invoke(string)
}

fun main() {
    val x = "fun apply_n(f, n, x) => if (n == 0) then x else f(apply_n(f, n - 1, f(x)));".trimMargin()

    fun parseProgram(input: String): SlastNode {
        val parser = SlangParser(CommonTokenStream(SlangLexer(ANTLRInputStream(input))))

        parser.removeErrorListeners()
        val errorListener = SlangParserErrorListener()
        parser.addErrorListener(errorListener)

        val parseTree = parser.compilationUnit()

        val builder = SlastBuilder.IRBuilder()
        val ast = builder.visit(parseTree) as ProgramUnit
        return ast
    }

    print(parseProgram(x))
}
