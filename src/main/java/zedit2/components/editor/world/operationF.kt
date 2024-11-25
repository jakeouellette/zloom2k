package zedit2.components.editor.world

import zedit2.components.WorldEditor
import zedit2.components.WorldEditor.Companion.getFMenuName
import javax.swing.JPopupMenu


internal fun WorldEditor.operationF(f: Int) {
    val popup = JPopupMenu()
    val fMenuName = getFMenuName(f)
    var items = 0
    if (fMenuName != null) {
        val fMenuItems = getFMenuItems(f)
        for (fMenuItem in fMenuItems) {
            popup.add(fMenuItem)
            items++
        }
    }
    popup.addPopupMenuListener(this)
    if (items > 0) {
        popup.show(
            frame, (frame.width - popup.preferredSize.width) / 2,
            (frame.height - popup.preferredSize.height) / 2
        )
    }
}