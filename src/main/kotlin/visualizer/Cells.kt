package visualizer

import java.awt.Color
import java.awt.Component
import java.awt.Font
import javax.swing.*
import javax.swing.tree.DefaultTreeCellRenderer


class ErrorListCellRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
    ): Component {
        val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel

        if (isSelected) {
            list.foreground = Color.GRAY
        } else {
            label.foreground = Color.RED
        }
        label.font = label.font.deriveFont(Font.ITALIC)
        return label
    }
}



class ASTTreeCellRenderer : DefaultTreeCellRenderer() {
    private val arrowIcon = ImageIcon("▶")

    override fun getTreeCellRendererComponent(
        tree: JTree?, value: Any?, selected: Boolean, expanded: Boolean,
        leaf: Boolean, row: Int, hasFocus: Boolean
    ): Component {
        val label = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus) as JLabel
        val expandedIcon = UIManager.getIcon("Tree.expandedIcon") // Open folder icon
        val collapsedIcon = UIManager.getIcon("Tree.collapsedIcon") // Closed folder icon
        val leafIcon = UIManager.getIcon("Tree.leafIcon") // Default leaf icon

        if (leaf) {
            label.setIcon(leafIcon);
        } else if (expanded) {
            label.setIcon(expandedIcon);
        } else {
            label.setIcon(collapsedIcon);
        }
        return this
    }
}