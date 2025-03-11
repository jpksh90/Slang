package slast.visualizer

import SimpleLangLexer
import SimpleLangParser
import com.formdev.flatlaf.FlatDarculaLaf
import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.Theme
import org.fife.ui.rtextarea.RTextScrollPane
import slast.ast.*
import slast.ast.Record
import java.awt.BorderLayout
import java.awt.GridLayout
import java.io.InputStream
import java.util.*
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode


fun parseProgram(input: String, errorListModel: DefaultListModel<String>): SlastNode {
    val parser = SimpleLangParser(CommonTokenStream(SimpleLangLexer(ANTLRInputStream(input))))

    parser.removeErrorListeners()
    val errorListener = CustomErrorListener()
    parser.addErrorListener(errorListener)

    val parseTree = parser.compilationUnit()

    SwingUtilities.invokeLater {
        errorListModel.clear()
        errorListModel.addAll(errorListener.errors)
    }

    val astBuilder = ASTBuilder()
    val ast = astBuilder.visit(parseTree) as CompilationUnit
    return ast
}

fun SlastNode.toTreeNode(): DefaultMutableTreeNode {
    return when (this) {
        is CompilationUnit -> DefaultMutableTreeNode("Program").apply { stmt.forEach { add(it.toTreeNode()) } }
        is LetStmt -> DefaultMutableTreeNode("LetStmt(${this.prettyPrint()})").apply {
            add(DefaultMutableTreeNode("id=${name}"))
            add(expr.toTreeNode())
        }

        is AssignStmt -> DefaultMutableTreeNode("AssignStmt(${this.prettyPrint()})").apply {
            add(lhs.toTreeNode())
            add(expr.toTreeNode())
        }

        is FunPureStmt -> DefaultMutableTreeNode("FunPure(${name})").apply {
            params.forEach { add(DefaultMutableTreeNode(it)) }
            add(body.toTreeNode())
        }

        is FunImpureStmt -> DefaultMutableTreeNode("FunImpure(${name})").apply {
            params.forEach { add(DefaultMutableTreeNode(it)) }
            add(body.toTreeNode())
        }

        is WhileStmt -> DefaultMutableTreeNode("While").apply {
            add(condition.toTreeNode())
            add(body.toTreeNode())
        }

        is PrintStmt -> DefaultMutableTreeNode("PrintStmt").apply {
            args.forEach { add(it.toTreeNode()) }
        }

        is IfStmt -> DefaultMutableTreeNode("IfStmt").apply {
            add(condition.toTreeNode())
            add(thenBody.toTreeNode())
            add(elseBody.toTreeNode())
        }

        is ExprStmt -> DefaultMutableTreeNode("ExprStmt").apply { add(expr.toTreeNode()) }
        is ReturnStmt -> DefaultMutableTreeNode("ReturnStmt").apply { add(expr.toTreeNode()) }
        is BlockStmt -> DefaultMutableTreeNode("BlockStmt").apply { stmts.forEach { add(it.toTreeNode()) } }
        is NumberLiteral -> DefaultMutableTreeNode("Number($value)")
        is BoolLiteral -> DefaultMutableTreeNode("Boolean($value)")
        is VarExpr -> DefaultMutableTreeNode("VarExpr($name)")
        is ReadInputExpr -> DefaultMutableTreeNode("ReadInputExpr")
        is FuncCallExpr -> DefaultMutableTreeNode("FuncCall(${target})").apply { args.forEach { add(it.toTreeNode()) } }
        is BinaryExpr -> DefaultMutableTreeNode("BinaryExpr(${this.prettyPrint()})").apply {
            add(left.toTreeNode())
            add(DefaultMutableTreeNode("op=${op}"))
            add(right.toTreeNode())
        }

        is IfExpr -> DefaultMutableTreeNode("IfExpr").apply {
            add(condition.toTreeNode())
            add(thenExpr.toTreeNode())
            add(elseExpr.toTreeNode())
        }

        is ParenExpr -> DefaultMutableTreeNode("ParenExpr(${this.prettyPrint()})").apply { add(expr.toTreeNode()) }
        is NoneValue -> DefaultMutableTreeNode("$this")
        is Record -> DefaultMutableTreeNode("Record").apply {
            expression.forEach {
                add(DefaultMutableTreeNode("ID(${it.first})"))
                add(DefaultMutableTreeNode("Expr(${it.second.prettyPrint()})").apply {
                    add(it.second.toTreeNode())
                })
            }
        }

        is StringLiteral -> DefaultMutableTreeNode("StringExpr($value)")
        is DerefExpr -> DefaultMutableTreeNode("DerefExpr(${expr.toTreeNode()})")
        is RefExpr -> DefaultMutableTreeNode("RefExpr(${expr.toTreeNode()})")
        is DerefStmt -> DefaultMutableTreeNode("DerefStmt(${this.prettyPrint()})").apply {
            add(lhs.toTreeNode()); add(
            rhs.toTreeNode
                ()
        )
        }

        is FieldAccess -> DefaultMutableTreeNode("FieldAccess(${this.prettyPrint()})").apply {
            add(lhs.toTreeNode())
            add(DefaultMutableTreeNode(rhs))
        }
    }
}


fun expandAllNodes(tree: JTree) {
    val rowCount = tree.rowCount
    var i = 0
    while (i < rowCount) {
        tree.expandRow(i)
        i++
    }
}
//    private val inputArea = JTextArea(20, 30)

class ASTViewer : JFrame("SimpleLang AST Visualizer") {
    private val inputArea = RSyntaxTextArea(20, 30).apply {
        syntaxEditingStyle = "text/simplelang"
        isCodeFoldingEnabled = true
        tabSize = 4
        isBracketMatchingEnabled = true
        isAutoIndentEnabled = true
    }
    private val parseButton = JButton("Parse")
    private val treePanel = JPanel(BorderLayout())
    private val splitPane: JSplitPane
    private val loadFileButton = JButton("Load File")

    private val errorListModel = DefaultListModel<String>()
    private val errorList = JList(errorListModel)

    private val inputScrollPane = RTextScrollPane(inputArea).apply {
        lineNumbersEnabled = true  // Show line numbers
    }

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        layout = BorderLayout()

//        val iconUrl = javaClass.getResource("/icons/icon.png")
//        this.iconImage = ImageIcon(iconUrl).image

        errorList.cellRenderer = ErrorListCellRenderer()

        val buttonPanel = JPanel(GridLayout(1, 2))
        buttonPanel.add(loadFileButton)
        buttonPanel.add(parseButton)

        val errorPanel = JPanel(BorderLayout())
        errorPanel.border = BorderFactory.createTitledBorder("Compiler Errors")
        errorPanel.add(JScrollPane(errorList), BorderLayout.CENTER)

        val inputPanel = JPanel(BorderLayout())

        inputPanel.add(inputScrollPane, BorderLayout.CENTER)
        inputPanel.add(buttonPanel, BorderLayout.NORTH)
        inputPanel.add(errorPanel, BorderLayout.SOUTH)

        val treeRoot = DefaultMutableTreeNode("AST will appear here")
        val tree = JTree(treeRoot)
        treePanel.add(JScrollPane(tree), BorderLayout.CENTER)
//        tree.cellRenderer = ASTTreeCellRenderer()

        splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, inputPanel, treePanel)
        splitPane.resizeWeight = 0.5
        add(splitPane, BorderLayout.CENTER)

        val themeStream: InputStream? = {}.javaClass.classLoader.getResourceAsStream("themes/dark.xml")

        if (themeStream != null) {
            val theme = Theme.load(themeStream)
            theme.apply(inputArea)
        }

        parseButton.addActionListener {
            val code = inputArea.text
            val ast = parseProgram(code, errorListModel)
            treePanel.removeAll()
            updateTree(ast)
        }

        loadFileButton.addActionListener {
            val fileChooser = JFileChooser()
            val returnValue = fileChooser.showOpenDialog(this)

            if (returnValue == JFileChooser.APPROVE_OPTION) {
                val file = fileChooser.selectedFile
                try {
                    val content = file.readText()
                    inputArea.text = content
                } catch (e: Exception) {
                    JOptionPane.showMessageDialog(
                        this,
                        "Error loading file: ${e.message}",
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }

        setSize(800, 600)
        setLocationRelativeTo(null)
    }

    private fun updateTree(ast: SlastNode) {
        val root = ast.toTreeNode()
        val newTree = JTree(root)
//        newTree.cellRenderer = ASTTreeCellRenderer()
        expandAllNodes(newTree)
        treePanel.removeAll()
        treePanel.add(JScrollPane(newTree), BorderLayout.CENTER)
        treePanel.revalidate()
        treePanel.repaint()
    }
}


fun main() {
    val props = Properties()
    props["text/simplelang"] = "languages.simplelang"
    FlatDarculaLaf.setup()

//    TokenMakerFactory.getDefaultInstance().("text/simplelang", "languages.simplelang")
    SwingUtilities.invokeLater { ASTViewer().isVisible = true }
}
