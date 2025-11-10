import com.github.ajalt.clikt.testing.test
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import slang.repl.Repl
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CLITest {
    @Test
    fun `test run file with interpreter`(
        @TempDir tempDir: File,
    ) {
        // Create a simple test file
        val testFile = File(tempDir, "test.slang")
        testFile.writeText(
            """
            let x = 10;
            let y = 20;
            print(x + y);
            """.trimIndent(),
        )

        // Capture output
        val outputStream = ByteArrayOutputStream()
        System.setOut(PrintStream(outputStream))

        // Run the CLI
        val cli = SlangCLI()
        val result = cli.test(testFile.absolutePath)

        // Verify
        assertEquals(0, result.statusCode)
        assertTrue(outputStream.toString().contains("30"))
    }

    @Test
    fun `test HLIR output to stdout`(
        @TempDir tempDir: File,
    ) {
        // Create a simple test file
        val testFile = File(tempDir, "test.slang")
        testFile.writeText("let x = 5;")

        // Run the CLI with --hlir flag
        val cli = SlangCLI()
        val result = cli.test("--hlir ${testFile.absolutePath}")

        // Verify
        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("codeInfo"))
    }

    @Test
    fun `test HLIR output to file`(
        @TempDir tempDir: File,
    ) {
        // Create a simple test file
        val testFile = File(tempDir, "test.slang")
        testFile.writeText("let x = 5;")

        val outputFile = File(tempDir, "output.yaml")

        // Run the CLI with --hlir and -o flags
        val cli = SlangCLI()
        val result = cli.test("--hlir ${testFile.absolutePath} -o ${outputFile.absolutePath}")

        // Verify
        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("HLIR written to:"))
        assertTrue(outputFile.exists())
        assertTrue(outputFile.readText().contains("codeInfo"))
    }

    @Test
    fun `test file not found error`() {
        val cli = SlangCLI()
        val result = cli.test("nonexistent.slang")

        // Verify
        assertEquals(0, result.statusCode)
        assertTrue(result.stderr.contains("File not found"))
    }

    @Test
    fun `test no arguments shows usage`() {
        val cli = SlangCLI()
        val result = cli.test("")

        // Verify
        assertEquals(0, result.statusCode)
        assertTrue(result.stderr.contains("Usage"))
    }

    @Test
    fun `test help option`() {
        val cli = SlangCLI()
        val result = cli.test("--help")

        // Verify
        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("Usage"))
        assertTrue(result.output.contains("--hlir"))
        assertTrue(result.output.contains("-o"))
    }

    @Test
    fun `test version option`() {
        val cli = SlangCLI()
        val result = cli.test("--version")

        // Verify
        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("1.0"))
    }
}
