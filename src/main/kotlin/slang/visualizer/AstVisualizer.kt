package slang.visualizer

import SlangLexer
import SlangParser
import com.formdev.flatlaf.FlatDarculaLaf
import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.Theme
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory
import org.fife.ui.rtextarea.RTextScrollPane
import slang.parser.SlangParserErrorListener
import slang.slast.*
import slang.slast.Function
import java.awt.BorderLayout
import java.awt.Color
import java.awt.GridLayout
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.*
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode


fun parseProgram(input: String, errorListModel: DefaultListModel<String>): SlastNode {
    val parser = SlangParser(CommonTokenStream(SlangLexer(ANTLRInputStream(input))))

    parser.removeErrorListeners()
    val errorListener = SlangParserErrorListener()
    parser.addErrorListener(errorListener)

    val parseTree = parser.compilationUnit()

    SwingUtilities.invokeLater {
        errorListModel.clear()
        errorListModel.addAll(errorListener.errors)
    }

    val irBuilder = SlastBuilder(parseTree)
    val ast = irBuilder.compilationUnit
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

        is InlinedFunction -> DefaultMutableTreeNode("InlineFunction").apply {
            params.forEach { add(DefaultMutableTreeNode(it)) }
            add(body.toTreeNode())
        }

        is Function -> DefaultMutableTreeNode("FunImpure(${name})").apply {
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
        is NamedFunctionCall -> DefaultMutableTreeNode("FuncCall(${name})").apply { arguments.forEach { add(it.toTreeNode()) } }
        is ExpressionFunctionCall -> DefaultMutableTreeNode("FuncCall(${target})").apply { arguments.forEach { add(it.toTreeNode()) } }
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
            add(lhs.toTreeNode())
            add(rhs.toTreeNode())
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

class ASTViewer : JFrame("Slang AST Visualizer") {
    private val inputArea = RSyntaxTextArea(30, 30).apply {
        syntaxEditingStyle = "text/kotlin"
        isCodeFoldingEnabled = true
        tabSize = 4
        isBracketMatchingEnabled = true
        isAutoIndentEnabled = true

    }


    private val astPanel = JPanel(BorderLayout()).apply {
        background = Color(41, 49, 52)
    }

    private val splitPane: JSplitPane

    private val parseButton = JButton("\uD83C\uDF34 Build Syntax Tree")
    private val openFileButton = JButton("\uD83D\uDCC4 Open File")
    private val saveFileButton = JButton("\uD83D\uDCBE Save File")
    private val runButton = JButton("\uD83D\uDE80 Run Program").apply {
        isEnabled = false
    }

    private val errorListModel = DefaultListModel<String>()
    private val errorList = JList(errorListModel)

    private val statusBar = JPanel(BorderLayout())
    private val statusLabel = JLabel("Ready")

    private val caretPositionLabel = JLabel("0:0")


    private val inputScrollPane = RTextScrollPane(inputArea).apply {
        lineNumbersEnabled = true  // Show line numbers
    }

    private var currentFile : File? = null

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        layout = BorderLayout()
        caretPositionLabel.horizontalTextPosition = SwingConstants.RIGHT

        initializeEditor()

        val buttonPanel = createButtonPanel()
        val errorPanel = createErrorPanel()

        statusBar.add(statusLabel, BorderLayout.CENTER)
        val inputPanel = JPanel()
        inputPanel.layout = BoxLayout(inputPanel, BoxLayout.Y_AXIS)
        inputPanel.add(buttonPanel)
        inputPanel.add(inputScrollPane)
        inputPanel.add(Box.createHorizontalGlue())
        inputPanel.add(caretPositionLabel)
        inputPanel.add(errorPanel)

        val treeRoot = DefaultMutableTreeNode("AST will appear here")
        val tree = JTree(treeRoot)
        astPanel.add(JScrollPane(tree), BorderLayout.CENTER)

        splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, inputPanel, astPanel)
        splitPane.resizeWeight = 0.5
        add(splitPane, BorderLayout.CENTER)
        add(statusBar, BorderLayout.SOUTH)

        loadDarkTheme()

        parseButton.addActionListener {
            val code = inputArea.text
            val ast = parseProgram(code, errorListModel)
            astPanel.removeAll()
            updateTree(ast)
        }
        // Associate this with Ctrl/CMD + P
        parseButton.actionMap.put(
            KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK),
            object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent?) {
                    parseButton.doClick()
                }
            })

        openFileButton.addActionListener {
            val fileChooser = JFileChooser().apply {
                dialogTitle = "Save Slang File"
                fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Slang Files (*.sl)", "sl")
            }
            val returnValue = fileChooser.showOpenDialog(this)

            if (returnValue == JFileChooser.APPROVE_OPTION) {
                val file = fileChooser.selectedFile
                currentFile = file
                try {
                    statusLabel.text = file.name
                    val content = file.readText()
                    inputArea.text = content
                    title = "Slang AST Visualizer - ${file.name}"
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

        openFileButton.actionMap.put(
            KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK),
            object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent?) {
                    parseButton.doClick()
                }
            })

        saveFileButton.apply {
            addActionListener {
                saveFile(inputArea)
                statusLabel.text = "Saved ${currentFile?.name}"
            }
        }

        inputArea.addCaretListener {
            updateCaretPosition()
        }


        setSize(800, 600)
        setLocationRelativeTo(null)
    }

    private fun createErrorPanel(): JPanel {
        val errorPanel = JPanel(BorderLayout())
        errorPanel.border = BorderFactory.createTitledBorder("Compiler Errors")
        errorPanel.add(JScrollPane(errorList), BorderLayout.CENTER)
        return errorPanel
    }

    private fun createButtonPanel(): JPanel {
        val buttonPanel = JPanel(GridLayout(1, 2))
        buttonPanel.add(openFileButton)
        buttonPanel.add(parseButton)
        buttonPanel.add(saveFileButton)
        buttonPanel.add(runButton)
        return buttonPanel
    }

    private fun initializeEditor() {
        val atmf = TokenMakerFactory.getDefaultInstance() as AbstractTokenMakerFactory
        atmf.putMapping("text/Slang", "slast.visualizer.SlangTokenMaker")
        inputArea.syntaxEditingStyle = "text/Slang"
        errorList.cellRenderer = ErrorListCellRenderer()
    }

    private fun loadDarkTheme() {
        val themeStream: InputStream? = {}.javaClass.classLoader.getResourceAsStream("themes/dark.xml")
        if (themeStream != null) {
            val theme = Theme.load(themeStream)
            theme.apply(inputArea)
        }
    }

    private fun saveFile(textArea: RSyntaxTextArea) {
        if (currentFile == null) {
            val fileChooser = JFileChooser().apply {
                dialogTitle = "Save Slang File"
                fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Slang Files (*.sl)", "sl")
            }
            val result = fileChooser.showSaveDialog(null)

            if (result == JFileChooser.APPROVE_OPTION) {
                val file = fileChooser.selectedFile
                val filePath = if (file.extension == "sl") file.absolutePath else "${file.absolutePath}.sl"

                try {
                    file.writeText(textArea.text)
                    JOptionPane.showMessageDialog(null, "File saved: $filePath")
                } catch (e: IOException) {
                    JOptionPane.showMessageDialog(null, "Error saving file: ${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
                }
            }
        } else {
            currentFile!!.writeText(textArea.text)
        }


    }

    private fun updateTree(ast: SlastNode) {
        val root = ast.toTreeNode()
        val newTree = JTree(root)
//        newTree.cellRenderer = ASTTreeCellRenderer()
        expandAllNodes(newTree)
        astPanel.removeAll()
        astPanel.add(JScrollPane(newTree), BorderLayout.CENTER)
        astPanel.revalidate()
        astPanel.repaint()
    }

    private fun updateCaretPosition() {
        val caretPos: Int = inputArea.caretPosition
        val line: Int = inputArea.document.defaultRootElement.getElementIndex(caretPos)
        val column: Int = caretPos - inputArea.document.defaultRootElement.getElement(line).startOffset
        caretPositionLabel.text = (line + 1).toString() + ":" + (column + 1)
    }
}


fun main() {
    val props = Properties()
    props["text/Slang"] = "languages.Slang"
    FlatDarculaLaf.setup()
    SwingUtilities.invokeLater { ASTViewer().isVisible = true }
}
