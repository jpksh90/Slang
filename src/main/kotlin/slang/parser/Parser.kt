package slang.parser

import SlangBaseVisitor
import SlangLexer
import SlangParser
import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import java.io.File

abstract class CompilationRule(val errorListener: SlangParserErrorListener) : SlangBaseVisitor<Boolean>()

class Parser(file: File) {
    var compilationUnit: SlangParser.CompilationUnitContext? = null
    var parser: SlangParser
    private val errorListener = SlangParserErrorListener()

    private val compilationRules = mutableListOf<CompilationRule>()

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

    fun getErrors() : List<Error> {
        return errorListener.errors.sortedBy { it.line }.sortedBy { it.charPositionInLine }
    }

    private fun addCompilationRule(rule: CompilationRule) {
        compilationRules.addLast(rule)
    }

    fun parse(): SlangParser.CompilationUnitContext? {
        // run the structs scope analyzer on the compilation unit
        val structScopeAnalyzer = ScopeAnalyzer(errorListener)
        structScopeAnalyzer.visit(compilationUnit)

        // evaluation of all compilation rules on the compilation unit should be true
        val compilationRulesStatus = compilationRules.fold(true) { acc, rule ->
            acc && rule.visit(compilationUnit)
        }

        if (!compilationRulesStatus) {
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
            errorListener.errors.add(Error(ctx.start.line, ctx.start.charPositionInLine,
                "Break statement not within a loop"))
        }
        return visitChildren(ctx)
    }

    override fun visitContinueStmt(ctx: SlangParser.ContinueStmtContext): Boolean {
        if (loopContextStack.isEmpty()) {
            errorListener.errors.add(
                Error(
                    ctx.start.line, ctx.start.charPositionInLine,
                    "Continue statement not within a loop"
                )
            )
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
                Error(
                    operand1.start.line,
                    operand1.start.charPositionInLine,
                    "${operand1.text} cannot be used as operand in $type operation"
                )
            )
            return false
        }
        if (isInvalidOperand(operand2)) {
            errorListener.errors.add(
                Error(
                    operand2.start.line,
                    operand2.start.charPositionInLine,
                    "${operand2.text} cannot be used as operand in $type operation"
                )
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

class ScopeAnalyzer(private val errorListener: SlangParserErrorListener) : SlangBaseVisitor<Nothing>() {
    // structs are defined on the global scope. Nested structs are not supported.
    val compilationUnitStack = ArrayDeque<SlangParser.CompilationUnitContext>()
    var variableScopes = ArrayDeque<MutableSet<String>>()

    override fun visitStructStmt(ctx: SlangParser.StructStmtContext): Nothing {
        if (SymbolTable.structDeclarations.containsValue(ctx)) {
            errorListener.addError(
                ctx.start.line,
                ctx.start.charPositionInLine,
                "Struct ${ctx.ID().text} already declared"
            )
        } else {
            val compilationUnit = compilationUnitStack.last()
            SymbolTable.structDeclarations[compilationUnit] = ctx
        }
        return visitChildren(ctx)
    }

    override fun visitLetExpr(ctx: SlangParser.LetExprContext): Nothing {
        val currentScope = variableScopes.last()
        val id = ctx.ID().text
        if (currentScope.contains(id)) {
            errorListener.addError(
                ctx.start.line,
                ctx.start.charPositionInLine,
                "Variable $id already declared in this scope"
            )
        } else {
            currentScope.add(id)
        }
        return visitChildren(ctx)
    }

    override fun visitCompilationUnit(ctx: SlangParser.CompilationUnitContext): Nothing {
        variableScopes.add(mutableSetOf())
        compilationUnitStack.add(ctx)
        val result = visitChildren(ctx)
        variableScopes.removeLast()
        return result
    }

    override fun visitBlockStmt(ctx: SlangParser.BlockStmtContext): Nothing {
        variableScopes.add(mutableSetOf())
        val result = visitChildren(ctx)
        variableScopes.removeLast()
        return result
    }
}

