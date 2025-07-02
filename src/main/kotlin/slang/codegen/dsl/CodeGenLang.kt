package slang.codegen.dsl

import javassist.ClassPool
import javassist.CtClass
import javassist.CtField
import javassist.CtMethod
import javassist.CtNewConstructor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.V1_8



// int access, String name, String descriptor, String signature, Object value
class Field(val name: String, val type: String) {

    fun translate(context: CtClass) : CtField {
        return CtField(CtClass.intType, name, context)
    }
}


class Method(val mv: MethodVisitor) {

    fun getStatic(owner: String, name: String, desc: String) {
        mv.visitFieldInsn(Opcodes.GETSTATIC, owner, name, desc)
    }

    fun ldc(value: Any) {
        mv.visitLdcInsn(value)
    }

    fun invokeVirtual(owner: String, name: String, desc: String) {
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, name, desc, false)
    }

    fun returnVoid() {
        mv.visitInsn(Opcodes.RETURN)
    }

    fun add(instruction: Method.() -> Unit) {
        this.instruction()
    }

    fun emit(block: Method.() -> Unit) {
        mv.visitCode()
        add(block)
        mv.visitMaxs(-1, -1)
        mv.visitEnd()
    }

}

class Class(val name : String, private val superClass : String?, private val interfaces : Array<String>?)  {

    constructor(name: String) : this(name, null, null)

    private val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)

    init {
        cw.visit(V1_8, ACC_PUBLIC, name, null, superClass, interfaces)
    }


    fun method(name: String, descriptor: String, access: Int = ACC_PUBLIC, code: Method.() -> Unit) {
        val mv = cw.visitMethod(access, name, descriptor, null, null)
        Method(mv).emit(code)
    }

    fun toByteArray(): ByteArray {
        cw.visitEnd()
        return cw.toByteArray()
    }

}


