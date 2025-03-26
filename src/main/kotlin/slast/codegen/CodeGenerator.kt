package slast.codegen

import com.sun.org.apache.bcel.internal.generic.ALOAD
import com.sun.org.apache.bcel.internal.generic.INVOKESPECIAL
import com.sun.org.apache.bcel.internal.generic.RETURN
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import org.slf4j.LoggerFactory
import slast.ast.*
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.createFile
import kotlin.io.path.exists

const val VERSION_NUMBER = 65


// use this one to decompile: https://javap.yawk.at/#r8gQXX
class CodeGenerator(val compilationUnit: CompilationUnit) : ASTVisitor<Unit> {

    val logger = LoggerFactory.getLogger(CodeGenerator::class.java)

    private val buildDirectory = Files.createDirectory(Paths.get(".", "build"))

    val anonymousRecords = mutableMapOf<Record, String>()

    private val classesToWrite = mutableListOf<Pair<ClassWriter, String>>()

    private val classWriterStack = ArrayDeque<ClassWriter>()

    private val methodVisitorStack = ArrayDeque<MethodVisitor>()

    private fun putClassForWriting(cw: ClassWriter, className: String) {
        classesToWrite.add(Pair(cw, className))
    }

    private fun writeClasses() {
        classesToWrite.forEach { (cw, cn) ->
            if (buildDirectory != null) {
                val filePath = buildDirectory.resolve("$cn.class")
                if (!Files.exists(filePath)) {
                    filePath.createFile()
                }
                assert(filePath.exists())
                val bytes = cw.toByteArray()
                val fos = FileOutputStream(filePath.toString())
                fos.write(bytes)
                fos.close()
                logger.debug("Created class {}", filePath)
            } else {
                logger.error("Failed to create build directory")
            }
        }
    }

    fun generate() {
        visitProgram(compilationUnit)
        println("Writing classes in $buildDirectory ...")
        writeClasses()
        println("...done")
    }

    private fun pushClassWriter(cw: ClassWriter) {
        classWriterStack.addLast(cw)
    }

    private fun popClassWriter() {
        classWriterStack.removeLast()
    }

    private fun getCurrentClassWriter() {
        classWriterStack.last()
    }

    private fun pushMethodVisitor(mv: MethodVisitor) {
        methodVisitorStack.addLast(mv)
    }

    private fun popMethodVisitor() {
        methodVisitorStack.removeLast()
    }

    private fun getCurrentMethodVisitor() {
        methodVisitorStack.last()
    }

    fun translateNumberLiteral(literal: NumberLiteral): Double {
        return literal.value
    }

    fun translateStringLiteral(literal: StringLiteral): String {
        return literal.value
    }

    fun translateBooleanLiteral(literal: BoolLiteral): Boolean {
        return literal.value
    }

    override fun visitLetStmt(stmt: LetStmt): Unit {
        TODO("Not yet implemented")
    }

    override fun visitAssignStmt(stmt: AssignStmt): Unit {
        TODO("Not yet implemented")
    }

    override fun visitFunPureStmt(stmt: FunPureStmt): Unit {
        TODO("Not yet implemented")
    }

    override fun visitFunImpureStmt(stmt: FunImpureStmt): Unit {
        TODO("Not yet implemented")
    }

    override fun visitWhileStmt(stmt: WhileStmt): Unit {
        TODO("Not yet implemented")
    }

    override fun visitPrintStmt(stmt: PrintStmt): Unit {
        TODO("Not yet implemented")
    }

    override fun visitIfStmt(stmt: IfStmt): Unit {
        TODO("Not yet implemented")
    }

    override fun visitExprStmt(stmt: ExprStmt): Unit {
        TODO("Not yet implemented")
    }

    override fun visitReturnStmt(stmt: ReturnStmt): Unit {
        TODO("Not yet implemented")
    }

    override fun visitBlockStmt(stmt: BlockStmt): Unit {
        TODO("Not yet implemented")
    }

    override fun visitIntExpr(expr: NumberLiteral): Unit {
        TODO("Not yet implemented")
    }

    override fun visitBoolExpr(expr: BoolLiteral): Unit {
        TODO("Not yet implemented")
    }

    override fun visitVarExpr(expr: VarExpr): Unit {
        TODO("Not yet implemented")
    }

    override fun visitReadInputExpr(expr: ReadInputExpr): Unit {
        TODO("Not yet implemented")
    }

    override fun visitFuncCallExpr(expr: FuncCallExpr): Unit {
        TODO("Not yet implemented")
    }

    override fun visitBinaryExpr(expr: BinaryExpr): Unit {
        TODO("Not yet implemented")
    }

    override fun visitIfExpr(expr: IfExpr): Unit {
        TODO("Not yet implemented")
    }

    override fun visitParenExpr(expr: ParenExpr): Unit {
        TODO("Not yet implemented")
    }

    override fun visitProgram(program: CompilationUnit): Unit {
        TODO("Not yet implemented")
    }

    override fun visitNoneValue(expr: NoneValue): Unit {
        TODO("Not yet implemented")
    }

    override fun visitRecord(expr: Record): Unit {
        /**
         * For each record, let t = {a : 1, b: 2, c: None}
         *
         * Create a class
         *
         * AnonRecord {
         *  Object a = ...
         *  Object b = ...
         *  Object c = ....
         * }
         *
         * AnonRecord t = new AnonRecord()
         */

        if (anonymousRecords.containsKey(expr)) {
            return
        }

        val className = "AnonRecord${expr.hashCode()}"
        anonymousRecords[expr] = className
        val cw = ClassWriter(0)
        cw.visit(VERSION_NUMBER, ACC_PUBLIC + ACC_SUPER, className, null, "java/lang/Object", null)
        pushClassWriter(cw)

        // adds the fields of the record
        val identifiers = expr.getIdentifiers()
        for (identifier in identifiers) {
            cw.visitField(ACC_PUBLIC, identifier, "Ljava/lang/Object;", null, null)
        }

        val mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null)
        pushMethodVisitor(mv)
        mv.visitCode()

        // create the constructor
        mv.visitVarInsn(ALOAD, 0)
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)

        // create the fields
        /**
         *          4: aload_0
         *          5: new           #7                  // class java/lang/Double
         *          8: dup
         *          9: dconst_1
         *         10: invokespecial #9                  // Method java/lang/Double."<init>":(D)V
         *         13: putfield      #12                 // Field a:Ljava/lang/Object;
         *         16: aload_0
         *         17: new           #18                 // class java/lang/String
         *         20: dup
         *         21: ldc           #20                 // String this
         *         23: invokespecial #22                 // Method java/lang/String."<init>":(Ljava/lang/String;)V
         *         26: putfield      #25                 // Field b:Ljava/lang/Object;
         */
        for (identifier in identifiers) {
            // we need to infer the type of the expression here.... It requires implementing a type system
            mv.visitVarInsn(ALOAD, 0)
            mv.visitTypeInsn(NEW, "java/lang/Object")
            mv.visitInsn(DUP)
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            mv.visitFieldInsn(PUTFIELD, className, identifier, "Ljava/lang/Object;")
        }
        mv.visitInsn(RETURN)
        mv.visitMaxs(1, 1)
        mv.visitEnd()
        putClassForWriting(cw, className)
    }

    override fun visitStringExpr(expr: StringLiteral): Unit {
        TODO("Not yet implemented")
    }

    override fun visitRefExpr(expr: RefExpr): Unit {
        TODO("Not yet implemented")
    }

    override fun visitDerefExpr(expr: DerefExpr): Unit {
        TODO("Not yet implemented")
    }

    override fun visitDerefStmt(stmt: DerefStmt): Unit {
        TODO("Not yet implemented")
    }

    override fun visitFieldAccessExpr(expr: FieldAccess): Unit {
        TODO("Not yet implemented")
    }


}

fun main() {
    val functionBody : Expr = BinaryExpr(VarExpr("a"), Operator.PLUS, VarExpr("b"))
    val function = FunPureStmt("test", listOf("a", "b"), functionBody)

}