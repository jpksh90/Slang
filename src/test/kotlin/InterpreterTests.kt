import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import slang.parser.StringParserInterface
import slang.repl.Interpreter
import slang.slast.SlastBuilder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InterpreterTests {
    private val originalOut = System.out
    private val originalIn = System.`in`
    private val outputStream = ByteArrayOutputStream()

    @BeforeEach
    fun setUp() {
        System.setOut(PrintStream(outputStream))
    }

    @AfterEach
    fun tearDown() {
        System.setOut(originalOut)
        System.setIn(originalIn)
    }

    private fun runProgram(code: String, input: String = ""): String {
        if (input.isNotEmpty()) {
            System.setIn(ByteArrayInputStream(input.toByteArray()))
        }
        outputStream.reset()
        
        val parser = StringParserInterface(code)
        val ast = SlastBuilder(parser.compilationUnit).compilationUnit
        val interpreter = Interpreter()
        interpreter.interpret(ast)
        
        return outputStream.toString().trim()
    }

    @Test
    fun testSimpleArithmetic() {
        val code = """
            let x = 10;
            let y = 5;
            let sum = x + y;
            let product = x * y;
            print(sum);
            print(product);
        """.trimIndent()
        
        val output = runProgram(code)
        assertEquals("15\n50", output)
    }

    @Test
    fun testFactorial() {
        val code = """
            fun factorial(n) => if (n == 0) then 1 else n * factorial(n - 1);
            let fact = factorial(5);
            print(fact);
        """.trimIndent()
        
        val output = runProgram(code)
        assertEquals("120", output)
    }

    @Test
    fun testReadInput() {
        val code = """
            let num = readInput();
            let doubled = num * 2;
            print(doubled);
        """.trimIndent()
        
        val output = runProgram(code, "5\n")
        assertEquals("10", output)
    }

    @Test
    fun testIfElse() {
        val code = """
            let a = 10;
            let b = 5;
            if (a > b) {
                print(a);
            } else {
                print(b);
            }
        """.trimIndent()
        
        val output = runProgram(code)
        assertEquals("10", output)
    }

    @Test
    fun testWhileLoop() {
        val code = """
            let i = 1;
            while (i <= 3) {
                print(i);
                i = i + 1;
            }
        """.trimIndent()
        
        val output = runProgram(code)
        assertEquals("1\n2\n3", output)
    }

    @Test
    fun testIfExpression() {
        val code = """
            let x = 5;
            let result = if (x > 0) then 1 else -1;
            print(result);
        """.trimIndent()
        
        val output = runProgram(code)
        assertEquals("1", output)
    }

    @Test
    fun testBooleanOperations() {
        val code = """
            let a = true;
            let b = false;
            let and_result = a && b;
            let or_result = a || b;
            print(and_result);
            print(or_result);
        """.trimIndent()
        
        val output = runProgram(code)
        assertEquals("false\ntrue", output)
    }

    @Test
    fun testComparison() {
        val code = """
            let a = 10;
            let b = 5;
            print(a > b);
            print(a < b);
            print(a == b);
            print(a != b);
        """.trimIndent()
        
        val output = runProgram(code)
        assertEquals("true\nfalse\nfalse\ntrue", output)
    }

    @Test
    fun testImpureFunction() {
        val code = """
            fun compute(x) {
                let result = x * 2;
                return result;
            }
            let value = compute(5);
            print(value);
        """.trimIndent()
        
        val output = runProgram(code)
        assertEquals("10", output)
    }

    @Test
    fun testHigherOrderFunction() {
        val code = """
            fun apply(f, x) => f(x);
            fun double(x) => x * 2;
            let result = apply(double, 5);
            print(result);
        """.trimIndent()
        
        val output = runProgram(code)
        assertEquals("10", output)
    }

    @Test
    fun testAnonymousFunction() {
        val code = """
            let add = fun(a, b) => a + b;
            let result = add(3, 4);
            print(result);
        """.trimIndent()
        
        val output = runProgram(code)
        assertEquals("7", output)
    }

    @Test
    fun testGCD() {
        val code = """
            fun mod(a, b) => if (a < b) then a else mod(a-b, b);
            fun gcd(a, b) => if (b == 0) then a else gcd(b, mod(a,b));
            let result = gcd(48, 18);
            print(result);
        """.trimIndent()
        
        val output = runProgram(code)
        assertEquals("6", output)
    }

    @Test
    fun testStringOperations() {
        val code = """
            let greeting = "Hello";
            let name = "World";
            let message = greeting + name;
            print(message);
        """.trimIndent()
        
        val output = runProgram(code)
        assertEquals("HelloWorld", output)
    }

    @Test
    fun testArrayInit() {
        val code = """
            let arr = (1, 2, 3);
            print(arr);
        """.trimIndent()
        
        val output = runProgram(code)
        assertTrue(output.contains("1") && output.contains("2") && output.contains("3"))
    }

    @Test
    fun testDoWhile() {
        val code = """
            let a = 1;
            do {
                print(a);
                a = a + 1;
            } while (a < 4);
        """.trimIndent()
        
        val output = runProgram(code)
        assertEquals("1\n2\n3", output)
    }
}
