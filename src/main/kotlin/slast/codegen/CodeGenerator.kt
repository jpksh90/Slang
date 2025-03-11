package slast.codegen

import com.sun.org.apache.bcel.internal.generic.ALOAD
import com.sun.org.apache.bcel.internal.generic.INVOKESPECIAL
import com.sun.org.apache.bcel.internal.generic.RETURN
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import slast.ast.*


class CodeGenerator(compilationUnit: CompilationUnit) {

    val anonymousRecords = mutableListOf<String>()

    fun translateRecord(expr: Record): ClassWriter {
        /**
         * For each record, let t = {a : 1, b: 2, c: None}
         */
        val className = "Record${expr.hashCode()}"
        val cw = ClassWriter(0)
        cw.visit(52, ACC_PUBLIC + ACC_SUPER, className, null, "java/lang/Object", null)

        val mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null)
        mv.visitCode()
        mv.visitVarInsn(ALOAD, 0)
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        mv.visitInsn(RETURN)
        mv.visitMaxs(1, 1)
        mv.visitEnd()
        return cw
    }

    fun translateNumberLiteral(literal: NumberLiteral) : Double {
        return literal.value
    }

    fun translateStringLiteral(literal: StringLiteral) : String {
        return literal.value
    }

    fun translateBooleanLiteral(literal: BoolLiteral) : Boolean {
        return literal.value
    }

}