package slang.ui

import java.awt.Color
import java.awt.Component
import java.awt.Font
import javax.swing.DefaultListCellRenderer
import javax.swing.JLabel
import javax.swing.JList

class ErrorListCellRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
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
