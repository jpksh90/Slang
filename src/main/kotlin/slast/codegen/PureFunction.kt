package slast.codegen

import jdk.internal.org.objectweb.asm.Opcodes.DADD
import jdk.internal.org.objectweb.asm.Opcodes.DSUB
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import slast.ast.*
import slast.ast.Record
import java.io.FileOutputStream



class PureFunction(private val function: FunPureStmt) {

    private val argumentsToPosition = HashMap<String, Int>()
    private val className = function.name
    private val cw = ClassWriter(0)
    private var mv : MethodVisitor
    private var stackLocCounter : Int = 0

    init {
        function.params.forEachIndexed { index, param ->
            argumentsToPosition[param] = index
            stackLocCounter += 1
        }
        val objectType : Type = Type.getObjectType("java/lang/Double")
        val argumentsType = function.params.map { objectType }.toTypedArray()
        val methodDescriptor = Type.getMethodDescriptor(objectType, *argumentsType)
        cw.visit(VERSION_NUMBER, ACC_PUBLIC + ACC_SUPER, className, null, "java/lang/Object", null)
        mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "call", methodDescriptor, null, null)
    }

    private fun translateMethodBody(body: Expr) {
        when (body) {
            is BinaryExpr -> {
                translateMethodBody(body.left)
                translateMethodBody(body.right)
                when (body.op) {
                    Operator.PLUS -> mv.visitInsn(DADD)
                    Operator.MINUS -> mv.visitInsn(DSUB)
                    else -> TODO()
                }
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false)

            }
            is BoolLiteral -> mv.visitInsn(
                when (body.value) {
                    true -> ICONST_1
                    false -> ICONST_0
                }
            )
            is DerefExpr -> TODO()
            is FieldAccess -> TODO()
            is FuncCallExpr -> TODO()
            is IfExpr -> TODO()
            NoneValue -> mv.visitInsn(ACONST_NULL)
            is NumberLiteral -> {
                when (body.value) {
                    0.0 -> mv.visitInsn(DCONST_0)
                    1.0 -> mv.visitInsn(DCONST_1)
                    else -> mv.visitLdcInsn(body.value)
                }
                stackLocCounter += 1
            }
            is ParenExpr -> {
                translateMethodBody(body.expr)
            }
            ReadInputExpr -> TODO()
            is Record -> TODO()
            is RefExpr -> TODO()
            is StringLiteral -> {
                mv.visitLdcInsn(body.value)
                mv.visitVarInsn(ASTORE, stackLocCounter)
                mv.visitVarInsn(ALOAD, stackLocCounter)
                stackLocCounter += 1
            }
            is VarExpr -> {
                val position = argumentsToPosition[body.name]
                if (position != null) {
                    mv.visitVarInsn(ALOAD, position)
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false)
                } else {
                    assert(false)
                    mv.visitTypeInsn(NEW, "java/lang/IllegalStateException");
                    mv.visitInsn(DUP);
                    mv.visitLdcInsn("Invalid state!");
                    mv.visitMethodInsn(INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V", false);
                    mv.visitInsn(ATHROW);
                }
            }
        }
    }

    fun translate()  {
        mv.visitCode()
        translateMethodBody(function.body)
        mv.visitInsn(ARETURN)
        mv.visitEnd()
        cw.visitEnd()
        FileOutputStream("Test.class").use { fos ->
            fos.write(cw.toByteArray())
        }
    }
}

fun main() {
    val functionBody: Expr = BinaryExpr(VarExpr("a"), "+", BinaryExpr(VarExpr("b"), "-", NumberLiteral(1781.0)))
    val function = FunPureStmt("Test", listOf("a", "b"), functionBody)
    PureFunction(function).translate()
}