package zedit2.components.editor.world

import zedit2.components.WorldEditor
import zedit2.model.SelectionModeConfiguration
import zedit2.util.Logger
import zedit2.util.Logger.TAG
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.SwingUtilities
import kotlin.math.min


internal fun WorldEditor.operationBlockEnd() {
    val savedBlockstartx = blockStartX
    val savedBlockstarty = blockStartY
    val savedCursorx = cursorX
    val savedCursory = cursorY
    val lastSelectionModeConfiguration = this.selectionModeConfiguration
    if (lastSelectionModeConfiguration != null) {
        operateOnMenuItem(lastSelectionModeConfiguration.description)
        return
    }
    val popupMenu = JPopupMenu("Choose block command")
    popupMenu.addPopupMenuListener(this)
    val menuItems = SelectionModeConfiguration.entries.map { it.description }
    val listener = ActionListener { e: ActionEvent ->
        if (blockStartX != savedBlockstartx || blockStartY != savedBlockstarty || cursorX != savedCursorx || cursorY != savedCursory) return@ActionListener
        val menuItem = (e.source as JMenuItem).text
        operateOnMenuItem(menuItem)
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

fun WorldEditor.operateOnMenuItem(menuItem: String) {
    when (menuItem) {
        SelectionModeConfiguration.COPY.description -> blockCopy(false)
        SelectionModeConfiguration.COPY_REPEATED.description -> blockCopy(true)
        SelectionModeConfiguration.MOVE.description -> blockMove()
        SelectionModeConfiguration.CLEAR.description -> blockClear()
        SelectionModeConfiguration.FLIP.description -> blockFlip(false)
        SelectionModeConfiguration.MIRROR.description -> blockFlip(true)
        SelectionModeConfiguration.PAINT.description -> blockPaint()
        else -> {}
    }
}