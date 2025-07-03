package codegen

import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ACC_STATIC
import slang.codegen.dsl.Class
import java.nio.file.Files
import java.nio.file.Paths

fun Class.writeToFile(outputDir: String = "output") {
    val path = Paths.get(outputDir, "${name}.class")
    Files.write(path, toByteArray())
    println("Written to file " + path.toAbsolutePath())
}


fun main() {
    // Stub class to implement the generator
    val klass = Class("Person").apply {
        method("main", "([Ljava/lang/String;)V", ACC_PUBLIC or ACC_STATIC) {
            add {
                getStatic("java/lang/System", "out", "Ljava/io/PrintStream;")
                ldc("Counter is available as public field.")
                invokeVirtual("java/io/PrintStream", "println", "(Ljava/lang/String;)V")
                returnVoid()
            }
        }
    }
    klass.writeToFile()

}