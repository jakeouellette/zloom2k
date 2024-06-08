package zedit2.components.editor.world

import zedit2.components.WorldEditor
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.SwingUtilities


internal fun WorldEditor.operationBlockEnd() {
    val savedBlockstartx = blockStartX
    val savedBlockstarty = blockStartY
    val savedCursorx = cursorX
    val savedCursory = cursorY
    val popupMenu = JPopupMenu("Choose block command")
    popupMenu.addPopupMenuListener(this)
    val menuItems = arrayOf(
        "Copy block", "Copy block (repeated)", "Move block", "Clear block", "Flip block",
        "Mirror block", "Paint block"
    )
    val listener = ActionListener { e: ActionEvent ->
        if (blockStartX != savedBlockstartx || blockStartY != savedBlockstarty || cursorX != savedCursorx || cursorY != savedCursory) return@ActionListener
        val menuItem = (e.source as JMenuItem).text
        when (menuItem) {
            "Copy block" -> blockCopy(false)
            "Copy block (repeated)" -> blockCopy(true)
            "Move block" -> blockMove()
            "Clear block" -> blockClear()
            "Flip block" -> blockFlip(false)
            "Mirror block" -> blockFlip(true)
            "Paint block" -> blockPaint()
            else -> {}
        }
    }

    for (item in menuItems) {
        val menuItem = JMenuItem(item)
        menuItem.addActionListener(listener)
        popupMenu.add(menuItem)
    }
    popupMenu.show(
        frame, (frame.width - popupMenu.preferredSize.width) / 2,
        (frame.height - popupMenu.preferredSize.height) / 2
    )

    // From https://stackoverflow.com/a/7754567
    SwingUtilities.invokeLater {
        popupMenu.dispatchEvent(
            KeyEvent(popupMenu, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_DOWN, '\u0000')
        )
    }
}