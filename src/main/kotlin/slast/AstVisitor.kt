package slast

interface ASTVisitor<T> {
    fun visitLetStmt(stmt: LetStmt): T
    fun visitAssignStmt(stmt: AssignStmt): T
    fun visitInlinedFunction(stmt: InlinedFunction): T
    fun visitFunction(stmt: Function): T
    fun visitWhileStmt(stmt: WhileStmt): T
    fun visitPrintStmt(stmt: PrintStmt): T
    fun visitIfStmt(stmt: IfStmt): T
    fun visitExprStmt(stmt: ExprStmt): T
    fun visitReturnStmt(stmt: ReturnStmt): T
    fun visitBlockStmt(stmt: BlockStmt): T

    fun visitIntExpr(expr: NumberLiteral): T
    fun visitBoolExpr(expr: BoolLiteral): T
    fun visitVarExpr(expr: VarExpr): T
    fun visitReadInputExpr(expr: ReadInputExpr): T
    fun visitFuncCallExpr(expr: FuncCallExpr): T
    fun visitBinaryExpr(expr: BinaryExpr): T
    fun visitIfExpr(expr: IfExpr): T
    fun visitParenExpr(expr: ParenExpr): T
    fun visitProgram(program: CompilationUnit): T
    fun visitNoneValue(expr: NoneValue): T

    fun visitRecord(expr: Record): T
    fun visitStringExpr(expr: StringLiteral): T

    fun visitRefExpr(expr: RefExpr): T
    fun visitDerefExpr(expr: DerefExpr): T
    fun visitDerefStmt(stmt: DerefStmt): T
    fun visitFieldAccessExpr(expr: FieldAccess): T
    fun visitStructStmt(expr: StructStmt) : T
    fun visitArrayInit(expr: ArrayInit) : T
    fun visitArrayAccess(expr: ArrayAccess) : T
    fun visitBreak(arg: Break): T
    fun visitContinue(arg: Continue): T
}

abstract class BaseASTVisitor<T> : ASTVisitor<T> {
    protected open fun defaultVisit(): T {
        throw UnsupportedOperationException("Operation not supported")
    }

    override fun visitLetStmt(stmt: LetStmt): T = defaultVisit()
    override fun visitAssignStmt(stmt: AssignStmt): T = defaultVisit()
    override fun visitInlinedFunction(stmt: InlinedFunction): T = defaultVisit()
    override fun visitFunction(stmt: Function): T = defaultVisit()
    override fun visitWhileStmt(stmt: WhileStmt): T = defaultVisit()
    override fun visitPrintStmt(stmt: PrintStmt): T = defaultVisit()
    override fun visitIfStmt(stmt: IfStmt): T = defaultVisit()
    override fun visitExprStmt(stmt: ExprStmt): T = defaultVisit()
    override fun visitReturnStmt(stmt: ReturnStmt): T = defaultVisit()
    override fun visitBlockStmt(stmt: BlockStmt): T = defaultVisit()

    override fun visitIntExpr(expr: NumberLiteral): T = defaultVisit()
    override fun visitBoolExpr(expr: BoolLiteral): T = defaultVisit()
    override fun visitVarExpr(expr: VarExpr): T = defaultVisit()
    override fun visitReadInputExpr(expr: ReadInputExpr): T = defaultVisit()
    override fun visitFuncCallExpr(expr: FuncCallExpr): T = defaultVisit()
    override fun visitBinaryExpr(expr: BinaryExpr): T = defaultVisit()
    override fun visitIfExpr(expr: IfExpr): T = defaultVisit()
    override fun visitParenExpr(expr: ParenExpr): T = defaultVisit()
    override fun visitProgram(program: CompilationUnit): T = defaultVisit()
    override fun visitNoneValue(expr: NoneValue): T = defaultVisit()

    override fun visitRecord(expr: Record): T = defaultVisit()
    override fun visitStringExpr(expr: StringLiteral): T = defaultVisit()

    override fun visitRefExpr(expr: RefExpr): T = defaultVisit()
    override fun visitDerefExpr(expr: DerefExpr): T = defaultVisit()
    override fun visitDerefStmt(stmt: DerefStmt): T = defaultVisit()
    override fun visitFieldAccessExpr(expr: FieldAccess): T = defaultVisit()
}



