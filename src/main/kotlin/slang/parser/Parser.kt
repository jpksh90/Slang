package slang.parser

import SlangBaseVisitor
import SlangLexer
import SlangParser
import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import slang.slast.CustomErrorListener
import java.io.File

class Parser(file: File) {
    var compilationUnit: SlangParser.CompilationUnitContext? = null
    var parser: SlangParser
    val errorListener = CustomErrorListener()

    init {
        val input = file.inputStream().bufferedReader().use { it.readText() }
        val lexer = SlangLexer(ANTLRInputStream(input))
        parser = SlangParser(CommonTokenStream(lexer))
        parser.addErrorListener(CustomErrorListener())
        parser.addErrorListener(errorListener)
        compilationUnit = parser.compilationUnit()
    }

    fun getErrors() : List<String> {
        return errorListener.errors
    }

    fun parse(): SlangParser.CompilationUnitContext? {
        val breakContinueChecker = BreakContinueChecker(errorListener)
        if (!breakContinueChecker.visit(compilationUnit)) {
            compilationUnit = null
        }

        return compilationUnit
    }

    class BreakContinueChecker(val errorListener: CustomErrorListener) : SlangBaseVisitor<Boolean>() {
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
}