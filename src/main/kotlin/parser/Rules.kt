package parser

import SlangParser
import org.antlr.v4.runtime.ParserRuleContext

@ParserRule("DisallowedBreakContinue", "Checks for break and continue statements outside of loops")
class BreakContinueChecker(errorListener: SlangParserErrorListener) : CompilationRule(errorListener) {
    private val loopContextStack = ArrayDeque<Boolean>()

    override fun enterWhileStmt(ctx: SlangParser.WhileStmtContext) {
        loopContextStack.add(true)
    }

    override fun exitWhileStmt(ctx: SlangParser.WhileStmtContext) {
        loopContextStack.removeLast()
    }

    override fun enterDoWhileStmt(ctx: SlangParser.DoWhileStmtContext) {
        loopContextStack.add(true)
    }

    override fun exitDoWhileStmt(ctx: SlangParser.DoWhileStmtContext) {
        loopContextStack.removeLast()
    }

    override fun enterBreakStmt(ctx: SlangParser.BreakStmtContext) {
        if (loopContextStack.isEmpty()) {
            logCompilationError(ctx.lineColumn(), "Break statement not within a loop")
        }
    }

    override fun enterContinueStmt(ctx: SlangParser.ContinueStmtContext) {
        if (loopContextStack.isEmpty()) {
            logCompilationError(ctx.lineColumn(), "Continue statement not within a loop")
        }
    }
}

@ParserRule("InvalidOperandsInBinaryOperator", "Checks for invalid operands in binary operators")
class InvalidOperandsInBinaryOperator(errorListener: SlangParserErrorListener) : CompilationRule(errorListener) {

    private fun isInvalidOperand(ctx: ParserRuleContext): Boolean {
        return ctx is SlangParser.FunAnonymousPureExprContext || ctx is SlangParser.FunAnonymousImpureExprContext
                || ctx is SlangParser.ReadInputExprContext
    }

    private fun validateOperands(operand1: ParserRuleContext, operand2: ParserRuleContext, type: String) {
        if (isInvalidOperand(operand1)) {
            logCompilationError(operand1.lineColumn(), "${operand1.text} cannot be used as operand in $type operation")
        }
        if (isInvalidOperand(operand2)) {
            logCompilationError(operand2.lineColumn(), "${operand2.text} cannot be used as operand in $type operation")
        }
    }

    override fun enterArithmeticExpr(ctx: SlangParser.ArithmeticExprContext) {
        if (ctx.expr().size == 2) {
            validateOperands(ctx.expr(0), ctx.expr(1), "arithmetic")
        }
    }

    override fun enterComparisonExpr(ctx: SlangParser.ComparisonExprContext) {
        if (ctx.expr().size == 2) {
            validateOperands(ctx.expr(0), ctx.expr(1), "comparison")
        }
    }

    override fun enterBooleanExpr(ctx: SlangParser.BooleanExprContext) {
        if (ctx.expr().size == 2) {
            validateOperands(ctx.expr(0), ctx.expr(1), "boolean")
        }
    }
}

@ParserRule("ValidateScope", "Checks for duplicate variables in the same scope")
class ValidateScope(errorListener: SlangParserErrorListener) : CompilationRule(errorListener) {
    // structs are defined on the global scope. Nested structs are not supported.
    private val compilationUnitStack = ArrayDeque<SlangParser.CompilationUnitContext>()
    private var variableScopes = ArrayDeque<MutableSet<String>>()

    override fun enterStructStmt(ctx: SlangParser.StructStmtContext) {
        if (SymbolTable.structDeclarations.containsValue(ctx)) {
            logCompilationError(ctx.lineColumn(), "Struct ${ctx.ID().text} already declared")
        } else {
            val compilationUnit = compilationUnitStack.last()
            SymbolTable.structDeclarations[compilationUnit] = ctx
        }
    }

    override fun enterLetExpr(ctx: SlangParser.LetExprContext) {
        val currentScope = variableScopes.last()
        val id = ctx.ID().text
        if (currentScope.contains(id)) {
            logCompilationError(ctx.lineColumn(), "Variable $id already declared in this scope")
        } else {
            currentScope.add(id)
        }
    }

    override fun enterBlockStmt(ctx: SlangParser.BlockStmtContext) {
        variableScopes.add(mutableSetOf())
    }

    override fun exitBlockStmt(ctx: SlangParser.BlockStmtContext?) {
        variableScopes.removeLast()
    }

    override fun enterCompilationUnit(ctx: SlangParser.CompilationUnitContext) {
        variableScopes.add(mutableSetOf())
        compilationUnitStack.add(ctx)
    }

    override fun exitCompilationUnit(ctx: SlangParser.CompilationUnitContext) {
        variableScopes.removeLast()
        compilationUnitStack.removeLast()
    }
}

