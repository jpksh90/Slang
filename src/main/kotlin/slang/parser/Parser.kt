package slang.parser

import SlangBaseVisitor
import SlangLexer
import SlangParser
import SlangParser.ExprContext
import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import java.io.File

abstract class CompilationRule(val errorListener: SlangParserErrorListener) : SlangBaseVisitor<Boolean>()

class Parser(file: File) {
    var compilationUnit: SlangParser.CompilationUnitContext? = null
    var parser: SlangParser
    val errorListener = SlangParserErrorListener()

    val compilationRules = mutableListOf<CompilationRule>()

    init {
        val input = file.inputStream().bufferedReader().use { it.readText() }
        val lexer = SlangLexer(ANTLRInputStream(input))
        parser = SlangParser(CommonTokenStream(lexer))
        parser.addErrorListener(SlangParserErrorListener())
        parser.addErrorListener(errorListener)
        compilationUnit = parser.compilationUnit()

        // Initialize the compilation rules here
        addCompilationRule(BreakContinueChecker(errorListener))
        addCompilationRule(InvalidOperandsInBinaryOperator(errorListener))
    }

    fun getErrors() : List<String> {
        return errorListener.errors
    }

    private fun addCompilationRule(rule: CompilationRule) {
        compilationRules.addLast(rule)
    }

    fun parse(): SlangParser.CompilationUnitContext? {
        // evaluation of all compilation rules on the compilation unit should be true
        val compilationRulesStatus = compilationRules.fold(true) { acc, rule ->
            acc && rule.visit(compilationUnit)
        }

        if (compilationRulesStatus == false) {
            compilationUnit = null
        }

        return compilationUnit
    }
}

class BreakContinueChecker(errorListener: SlangParserErrorListener) : CompilationRule(errorListener) {
    private val loopContextStack = ArrayDeque<Boolean>()

    override fun visitWhileStmt(ctx: SlangParser.WhileStmtContext): Boolean {
        loopContextStack.add(true)
        val result = visitChildren(ctx)
        loopContextStack.removeAt(loopContextStack.size - 1)
        return result
    }

    override fun visitForStmt(ctx: SlangParser.ForStmtContext): Boolean {
        loopContextStack.add(true)
        val result = visitChildren(ctx)
        loopContextStack.removeAt(loopContextStack.size - 1)
        return result
    }

    override fun visitDoWhileStmt(ctx: SlangParser.DoWhileStmtContext?): Boolean {
        loopContextStack.add(true)
        val result = visitChildren(ctx)
        loopContextStack.removeAt(loopContextStack.size - 1)
        return result
    }

    override fun visitBreakStmt(ctx: SlangParser.BreakStmtContext): Boolean {
        if (loopContextStack.isEmpty()) {
            errorListener.errors.add("Break statement not within a loop at line ${ctx.start.line}:${ctx.start
                .charPositionInLine} - ${ctx.text}")
        }
        return visitChildren(ctx)
    }

    override fun visitContinueStmt(ctx: SlangParser.ContinueStmtContext): Boolean {
        if (loopContextStack.isEmpty()) {
            errorListener.errors.add("Continue statement not within a loop at line ${ctx.start.line}:${ctx.start
                .charPositionInLine} - ${ctx.text}")
        }
        return visitChildren(ctx)
    }
}

class InvalidOperandsInBinaryOperator(errorListener: SlangParserErrorListener) : CompilationRule(errorListener) {

    private fun isInvalidOperand(ctx: ParserRuleContext) : Boolean {
        return ctx is SlangParser.FunAnonymousPureExprContext || ctx is SlangParser.FunAnonymousImpureExprContext
                || ctx is SlangParser.ReadInputExprContext
    }

    private fun check(operand1: ParserRuleContext, operand2: ParserRuleContext, type: String) : Boolean {
        if (isInvalidOperand(operand1)) {
            errorListener.errors.add(
                "${operand1.text} cannot be used as operand in $type expression at line " +
                        "${operand1.start.line}:${operand1.start.charPositionInLine} - ${operand1.text}"
            )
            return false
        }
        if (isInvalidOperand(operand2)) {
            errorListener.errors.add(
                "${operand2.text} function cannot be used as operand in $type expression at line " +
                        "${operand2.start.line}:${operand2.start.charPositionInLine} - ${operand2.text}"
            )
            return false
        }
        return true
    }


    override fun visitArithmeticExpr(ctx: SlangParser.ArithmeticExprContext): Boolean {
        if (ctx.expr().size != 2) {
            check(ctx.expr(0), ctx.expr(1), "arithmetic")
            return visitChildren(ctx)
        }
        return visitChildren(ctx)
    }

    override fun visitComparisonExpr(ctx: SlangParser.ComparisonExprContext): Boolean {
        if (ctx.expr().size == 2) {
            check(ctx.expr(0), ctx.expr(1), "comparison")
            return visitChildren(ctx)
        }
        return true; // it's ok to pass the rules
    }

    override fun visitBooleanExpr(ctx: SlangParser.BooleanExprContext): Boolean {
        if (ctx.expr().size != 2) {
            check(ctx.expr(0), ctx.expr(1), "boolean")
            return visitChildren(ctx)
        }
        return visitChildren(ctx)
    }
}