package codegen

import jdk.internal.org.objectweb.asm.ClassWriter
import jdk.internal.org.objectweb.asm.MethodVisitor
import jdk.internal.org.objectweb.asm.Opcodes.*
import slast.*
import slast.Function
import java.io.FileOutputStream
import java.util.HashMap



class BytecodeGenVisitor(
    private val cw: ClassWriter,
    private val mv: MethodVisitor,
    private val varTable: MutableMap<String, Int> = mutableMapOf()
) : ASTVisitor<Unit> {

    private val inlinedFunctionName = hashMapOf<InlinedFunction, String>()
    private var inlinedFunctionCounter = -1
    private var nextVar: Int = 1 // 0 is for 'args'

    private fun getInlineFunctionName(function: InlinedFunction) : String =
        inlinedFunctionName.computeIfAbsent(function) {
            "inlineFun${inlinedFunctionCounter+1}"
        }

    override fun visitProgram(program: CompilationUnit) {
        for (stmt in program.stmt) {
            stmt.accept(this)
        }
        mv.visitInsn(RETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()
    }

    override fun visitLetStmt(stmt: LetStmt) {
        stmt.expr.accept(this)
        mv.visitVarInsn(ISTORE, nextVar)
        varTable[stmt.name] = nextVar++
    }

    override fun visitAssignStmt(stmt: AssignStmt) {
        stmt.expr.accept(this)
        val idx = (stmt.lhs as? VarExpr)?.let { varTable[it.name] }
            ?: throw IllegalStateException("Unknown variable")
        mv.visitVarInsn(ISTORE, idx)
    }

    override fun visitPrintStmt(stmt: PrintStmt) {
        mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;")
        stmt.args[0].accept(this)
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false)
    }

    override fun visitIntExpr(expr: NumberLiteral) {
        mv.visitLdcInsn(expr.value.toInt())
    }

    override fun visitVarExpr(expr: VarExpr) {
        val idx = varTable[expr.name] ?: throw IllegalStateException("Unknown variable")
        mv.visitVarInsn(ILOAD, idx)
    }

    override fun visitBinaryExpr(expr: BinaryExpr) {
        expr.left.accept(this)
        expr.right.accept(this)
        when (expr.op) {
            Operator.PLUS -> mv.visitInsn(IADD)
            Operator.MINUS -> mv.visitInsn(ISUB)
            Operator.TIMES -> mv.visitInsn(IMUL)
            Operator.DIV -> mv.visitInsn(IDIV)
            else -> throw UnsupportedOperationException("Operator not supported: ${expr.op}")
        }
    }

    override fun visitStringExpr(expr: StringLiteral) {
        mv.visitLdcInsn(expr.value)
    }

    override fun visitRefExpr(expr: RefExpr) {
        TODO("Not yet implemented")
    }

    override fun visitDerefExpr(expr: DerefExpr) {
        TODO("Not yet implemented")
    }

    override fun visitDerefStmt(stmt: DerefStmt) {
        TODO("Not yet implemented")
    }

    override fun visitFieldAccessExpr(expr: FieldAccess) {
        TODO("Not yet implemented")
    }

    override fun visitStructStmt(expr: StructStmt) {
        TODO("Not yet implemented")
    }

    override fun visitArrayInit(expr: ArrayInit) {
        TODO("Not yet implemented")
    }

    override fun visitArrayAccess(expr: ArrayAccess) {
        TODO("Not yet implemented")
    }

    override fun visitReadInputExpr(expr: ReadInputExpr) {
        // Read a single int from System.in (simplified)
        mv.visitFieldInsn(GETSTATIC, "java/lang/System", "in", "Ljava/io/InputStream;")
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/InputStream", "read", "()I", false)
    }

    override fun visitParenExpr(expr: ParenExpr) {
        expr.expr.accept(this)
    }

    override fun visitNoneValue(expr: NoneValue) {
        mv.visitLdcInsn(0) // Represent None as 0
    }

    override fun visitFuncCallExpr(expr: FuncCallExpr) {
        when (expr) {
            is NamedFunctionCall -> {
                for (arg in expr.arguments) {
                    arg.accept(this)
                }
                mv.visitMethodInsn(INVOKESTATIC, "SlangMain", expr.name, "(${"I".repeat(expr.arguments.size)})I", false)
            }
            is ExpressionFunctionCall -> {
                // Assume target is a VarExpr referring to a function name
                val targetName = (expr.target as? VarExpr)?.name
                if (targetName != null) {
                    for (arg in expr.arguments) {
                        arg.accept(this)
                    }
                    mv.visitMethodInsn(INVOKESTATIC, "SlangMain", targetName, "(${"I".repeat(expr.arguments.size)})I", false)
                } else {
                    throw UnsupportedOperationException("ExpressionFunctionCall only supports VarExpr as target for now")
                }
            }
        }
    }

    // Implement other visit methods as needed...
    override fun visitInlinedFunction(stmt: InlinedFunction) {
        val name = getInlineFunctionName(stmt)
        val methodVisitor = cw.visitMethod(ACC_PRIVATE or ACC_STATIC, name, "(${"I".repeat(stmt.params.size)})I", null, null)
        val localVarTable = mutableMapOf<String, Int>()
        for ((i, param) in stmt.params.withIndex()) {
            localVarTable[param] = i
        }
        val bodyVisitor = BytecodeGenVisitor(cw, methodVisitor, localVarTable)
        stmt.body.accept(bodyVisitor)
        methodVisitor.visitInsn(IRETURN)
        methodVisitor.visitMaxs(0, 0)
        methodVisitor.visitEnd()
    }
    override fun visitFunction(stmt: Function) {
        val methodVisitor = cw.visitMethod(ACC_PUBLIC or ACC_STATIC, stmt.name, "(${"I".repeat(stmt.params.size)})I", null, null)
        val localVarTable = mutableMapOf<String, Int>()
        for ((i, param) in stmt.params.withIndex()) {
            localVarTable[param] = i
        }
        val bodyVisitor = BytecodeGenVisitor(cw, methodVisitor, localVarTable)
        stmt.body.accept(bodyVisitor)
        methodVisitor.visitInsn(IRETURN)
        methodVisitor.visitMaxs(0, 0)
        methodVisitor.visitEnd()
    }
    override fun visitWhileStmt(stmt: WhileStmt) {
        val startLabel = jdk.internal.org.objectweb.asm.Label()
        val endLabel = jdk.internal.org.objectweb.asm.Label()
        mv.visitLabel(startLabel)
        stmt.condition.accept(this)
        mv.visitJumpInsn(IFEQ, endLabel)
        stmt.body.accept(this)
        mv.visitJumpInsn(GOTO, startLabel)
        mv.visitLabel(endLabel)
    }

    override fun visitIfStmt(stmt: IfStmt) {
        val elseLabel = jdk.internal.org.objectweb.asm.Label()
        val endLabel = jdk.internal.org.objectweb.asm.Label()
        stmt.condition.accept(this)
        mv.visitJumpInsn(IFEQ, elseLabel)
        stmt.thenBody.accept(this)
        mv.visitJumpInsn(GOTO, endLabel)
        mv.visitLabel(elseLabel)
        stmt.elseBody.accept(this)
        mv.visitLabel(endLabel)
    }

    override fun visitBlockStmt(stmt: BlockStmt) {
        for (s in stmt.stmts) {
            s.accept(this)
        }
    }

    override fun visitReturnStmt(stmt: ReturnStmt) {
        stmt.expr.accept(this)
        mv.visitInsn(IRETURN)
    }

    override fun visitBoolExpr(expr: BoolLiteral) {
        mv.visitLdcInsn(if (expr.value) 1 else 0)
    }

    override fun visitExprStmt(stmt: ExprStmt) {
        stmt.expr.accept(this)
        // Discard result if needed (no-op for now)
    }

    override fun visitBreak(arg: Break) {
        // Placeholder: needs loop context to jump to end
        // mv.visitJumpInsn(GOTO, endLabel)
    }

    override fun visitContinue(arg: Continue) {
        // Placeholder: needs loop context to jump to start
        // mv.visitJumpInsn(GOTO, startLabel)
    }

    override fun visitRecord(expr: Record) {
        // Create a new HashMap and store each field
        mv.visitTypeInsn(NEW, "java/util/HashMap")
        mv.visitInsn(DUP)
        mv.visitMethodInsn(INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false)
        for ((field, valueExpr) in expr.expression) {
            mv.visitInsn(DUP) // Duplicate map reference
            mv.visitLdcInsn(field) // Push field name
            valueExpr.accept(this) // Push field value
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashMap", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false)
            mv.visitInsn(POP) // Discard result of put
        }
        // Map reference remains on stack
    }

    override fun visitIfExpr(expr: IfExpr) {
        val elseLabel = jdk.internal.org.objectweb.asm.Label()
        val endLabel = jdk.internal.org.objectweb.asm.Label()
        expr.condition.accept(this)
        mv.visitJumpInsn(IFEQ, elseLabel)
        expr.thenExpr.accept(this)
        mv.visitJumpInsn(GOTO, endLabel)
        mv.visitLabel(elseLabel)
        expr.elseExpr.accept(this)
        mv.visitLabel(endLabel)
    }
}

// Usage example:
fun generateBytecode(ast: CompilationUnit, className: String = "SlangMain") {
    val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
    cw.visit(V1_8, ACC_PUBLIC or ACC_SUPER, className, null, "java/lang/Object", null)
    val mv = cw.visitMethod(ACC_PUBLIC or ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null)
    val visitor = BytecodeGenVisitor(cw, mv)
    ast.accept(visitor)
    val bytes = cw.toByteArray()
    FileOutputStream("$className.class").use { it.write(bytes) }
}