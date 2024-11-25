package zedit2.components

import zedit2.util.Logger
import zedit2.util.Logger.TAG
import java.awt.event.MouseEvent
import javax.swing.JList
import javax.swing.ListModel
import javax.swing.SwingUtilities
import javax.swing.event.MouseInputAdapter


class BoardDragAndDrop(val myList : JList<String>, val onBoardSwap : (Int, Int) -> Unit) : MouseInputAdapter() {
    private var mouseDragging = false
    private var dragSourceIndex = 0

    override fun mousePressed(e: MouseEvent?) {
        if (SwingUtilities.isLeftMouseButton(e)) {
            Logger.i(TAG) { "Dragging starting... ${myList.model.size}" }
            dragSourceIndex = myList.getSelectedIndex()
            if (dragSourceIndex < (myList.model.size - 1)) {
                Logger.i(TAG) { "Dragging source index... " }
                mouseDragging = true
            }
        }
    }

    override fun mouseReleased(e: MouseEvent?) {
        Logger.i(TAG) { "Dragging ended... ${myList.model.size}" }
        if (e == null) {
            return
        }
        if (mouseDragging) {
            val currentIndex: Int = myList.locationToIndex(e.getPoint())


            if (currentIndex != dragSourceIndex && currentIndex < (myList.model.size - 1)) {
                val dragTargetIndex: Int = myList.getSelectedIndex()
                onBoardSwap(dragTargetIndex, dragSourceIndex)

            }
        }
        mouseDragging = false
    }

    override fun mouseDragged(e: MouseEvent) {
        if (mouseDragging) {

        }
    }
}
