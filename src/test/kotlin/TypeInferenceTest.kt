import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import slang.hlir.string2hlir
import slang.typeinfer.typeCheck

class TypeInferenceTest {
    private fun assertNoErrors(code: String) {
        val ast =
            string2hlir(code).fold(
                onSuccess = { it },
                onFailure = { throw AssertionError("Parse error: $it") },
            )
        val errors = typeCheck(ast)
        assertTrue(errors.isEmpty(), "Expected no type errors but got:\n${errors.joinToString("\n") { it.message ?: "" }}")
    }

    private fun assertHasError(
        code: String,
        expectedFragment: String,
    ) {
        val ast =
            string2hlir(code).fold(
                onSuccess = { it },
                onFailure = { throw AssertionError("Parse error: $it") },
            )
        val errors = typeCheck(ast)
        assertTrue(errors.isNotEmpty(), "Expected type errors but got none")
        assertTrue(
            errors.any { (it.message ?: "").contains(expectedFragment) },
            "Expected error containing '$expectedFragment' but got:\n${errors.joinToString("\n") { it.message ?: "" }}",
        )
    }

    private fun assertErrorCount(
        code: String,
        count: Int,
    ) {
        val ast =
            string2hlir(code).fold(
                onSuccess = { it },
                onFailure = { throw AssertionError("Parse error: $it") },
            )
        val errors = typeCheck(ast)
        assertEquals(
            count,
            errors.size,
            "Expected $count error(s) but got ${errors.size}:\n${errors.joinToString("\n") { it.message ?: "" }}",
        )
    }

    // ---- Well-typed programs ----

    @Test
    fun testSimpleArithmetic() {
        assertNoErrors(
            """
            let x = 10;
            let y = 5;
            let sum = x + y;
            let product = x * y;
            print(sum);
            print(product);
            """.trimIndent(),
        )
    }

    @Test
    fun testFactorial() {
        assertNoErrors(
            """
            fun factorial(n) => if (n == 0) then 1 else n * factorial(n - 1);
            let fact = factorial(5);
            print(fact);
            """.trimIndent(),
        )
    }

    @Test
    fun testBooleanOps() {
        assertNoErrors(
            """
            let a = true;
            let b = false;
            let c = a && b;
            let d = a || b;
            """.trimIndent(),
        )
    }

    @Test
    fun testHigherOrderFunction() {
        assertNoErrors(
            """
            fun apply(f, x) => f(x);
            fun double(x) => x * 2;
            let result = apply(double, 5);
            print(result);
            """.trimIndent(),
        )
    }

    @Test
    fun testAnonymousFunction() {
        assertNoErrors(
            """
            let add = fun(a, b) => a + b;
            let result = add(3, 4);
            print(result);
            """.trimIndent(),
        )
    }

    @Test
    fun testIfExpression() {
        assertNoErrors(
            """
            let x = 5;
            let result = if (x > 0) then 1 else -1;
            print(result);
            """.trimIndent(),
        )
    }

    @Test
    fun testWhileLoop() {
        assertNoErrors(
            """
            let i = 1;
            while (i <= 3) {
                print(i);
                i = i + 1;
            }
            """.trimIndent(),
        )
    }

    @Test
    fun testGCD() {
        assertNoErrors(
            """
            fun mod(a, b) => if (a < b) then a else mod(a-b, b);
            fun gcd(a, b) => if (b == 0) then a else gcd(b, mod(a,b));
            let result = gcd(48, 18);
            print(result);
            """.trimIndent(),
        )
    }

    @Test
    fun testStringConcat() {
        assertNoErrors(
            """
            let greeting = "Hello";
            let name = "World";
            let message = greeting + name;
            print(message);
            """.trimIndent(),
        )
    }

    @Test
    fun testLetPolymorphism() {
        assertNoErrors(
            """
            fun id(x) => x;
            let a = id(42);
            let b = id(true);
            """.trimIndent(),
        )
    }

    @Test
    fun testIfStatement() {
        assertNoErrors(
            """
            let a = 10;
            let b = 5;
            if (a > b) {
                print(a);
            } else {
                print(b);
            }
            """.trimIndent(),
        )
    }

    @Test
    fun testImpureFunction() {
        assertNoErrors(
            """
            fun compute(x) {
                let result = x * 2;
                return result;
            }
            let value = compute(5);
            print(value);
            """.trimIndent(),
        )
    }

    // ---- Type errors ----

    @Test
    fun testArithmeticOnBool() {
        assertHasError(
            """
            let x = true;
            let y = x * 2;
            """.trimIndent(),
            "Cannot unify",
        )
    }

    @Test
    fun testBooleanOnNumber() {
        assertHasError(
            """
            let x = 1;
            let y = 2;
            let z = x && y;
            """.trimIndent(),
            "Cannot unify",
        )
    }

    @Test
    fun testIfConditionNotBool() {
        assertHasError(
            """
            let x = if (42) then 1 else 2;
            """.trimIndent(),
            "Cannot unify",
        )
    }

    @Test
    fun testIfBranchMismatch() {
        assertHasError(
            """
            let x = if (true) then 1 else false;
            """.trimIndent(),
            "Cannot unify",
        )
    }

    @Test
    fun testWhileConditionNotBool() {
        assertHasError(
            """
            while 42 {
                print(1);
            }
            """.trimIndent(),
            "Cannot unify",
        )
    }

    @Test
    fun testArityMismatch() {
        assertHasError(
            """
            fun f(x) => x + 1;
            let y = f(1, 2);
            """.trimIndent(),
            "arity",
        )
    }

    @Test
    fun testSubtractionOnBool() {
        assertHasError(
            """
            let x = true - false;
            """.trimIndent(),
            "Cannot unify",
        )
    }

    @Test
    fun testComparisonOnBool() {
        assertHasError(
            """
            let x = true < false;
            """.trimIndent(),
            "Cannot unify",
        )
    }

    @Test
    fun testAssignTypeMismatch() {
        assertHasError(
            """
            let x = 1;
            x = true;
            """.trimIndent(),
            "Cannot unify",
        )
    }
}
