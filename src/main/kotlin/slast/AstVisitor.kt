package slast

interface ASTVisitor<T> {
    fun visitLetStmt(stmt: LetStmt): T
    fun visitAssignStmt(stmt: AssignStmt): T
    fun visitFunPureStmt(stmt: FunPureStmt): T
    fun visitFunImpureStmt(stmt: FunImpureStmt): T
    fun visitWhileStmt(stmt: WhileStmt): T
    fun visitPrintStmt(stmt: PrintStmt): T
    fun visitIfStmt(stmt: IfStmt): T
    fun visitExprStmt(stmt: ExprStmt): T
    fun visitReturnStmt(stmt: ReturnStmt): T
    fun visitBlockStmt(stmt: BlockStmt): T

    fun visitIntExpr(expr: IntExpr): T
    fun visitBoolExpr(expr: BoolExpr): T
    fun visitVarExpr(expr: VarExpr): T
    fun visitReadInputExpr(expr: ReadInputExpr): T
    fun visitFuncCallExpr(expr: FuncCallExpr): T
    fun visitBinaryExpr(expr: BinaryExpr): T
    fun visitIfExpr(expr: IfExpr): T
    fun visitParenExpr(expr: ParenExpr): T
    fun visitProgram(program: Program): T
    fun visitNoneValue(expr: NoneValue): T

    fun visitRecord(expr: Record): T
    fun visitStringExpr(expr: StringExpr): T

    fun visitRefExpr(expr: RefExpr): T
    fun visitDerefExpr(expr: DerefExpr): T
    fun visitDerefStmt(stmt: DerefStmt): T
    fun visitFieldAccessExpr(expr: FieldAccess): T
}


