import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import org.approvaltests.Approvals
import slast.ast.ASTBuilder
import slast.ast.prettyPrint
import java.net.URL
import kotlin.test.Test

class AstBuilderTests {

    fun loadTestProgram(name: String): URL? {
        return AstBuilderTests::class.java.getResource("/$name")
    }

    fun buildAst(input: String): String {
        val lexer = SimpleLangLexer(ANTLRInputStream(input))
        val parser = SimpleLangParser(CommonTokenStream(lexer))
        val tree = parser.prog()
        val astBuilder = ASTBuilder()
        return astBuilder.visit(tree).prettyPrint()
    }

    @Test
    fun testCase1() {
        val program = loadTestProgram("exponent.sl")
        if (program != null) {
            val ast = buildAst(program.readText())
            Approvals.verify(ast)
        }
    }

    @Test
    fun testCase2() {
        val program = loadTestProgram("facl.sl")
        if (program != null) {
            val ast = buildAst(program.readText())
            Approvals.verify(ast)
        }
    }

    @Test
    fun testCase3() {
        val program = loadTestProgram("gcd.sl")
        if (program != null) {
            val ast = buildAst(program.readText())
            Approvals.verify(ast)
        }
    }

    @Test
    fun testCase4() {
        val program = loadTestProgram("minmax.sl")
        if (program != null) {
            val ast = buildAst(program.readText())
            Approvals.verify(ast)
        }
    }

    @Test
    fun testCase5() {
        val program = loadTestProgram("disallowed.sl")
        if (program != null) {
            val ast = buildAst(program.readText())
            Approvals.verify(ast)
        }
    }

    @Test
    fun testCase6() {
        val program = loadTestProgram("sum_prod.sl")
        if (program != null) {
            val ast = buildAst(program.readText())
            Approvals.verify(ast)
        }
    }
}