package zedit2.components

import java.awt.Dialog
import java.awt.Frame
import java.awt.Window
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import javax.swing.*

class PopoverDialog : JDialog{
    constructor(owner: Frame?) : super(owner)
    constructor(owner : Dialog?) : super(owner)
    constructor() : super()
    constructor(owner : Window?) : super(owner)
    init {
        this.defaultCloseOperation = DISPOSE_ON_CLOSE
        this.isAlwaysOnTop = true
        this.addWindowFocusListener(object : WindowFocusListener {
            override fun windowGainedFocus(e: WindowEvent) {
                //do nothing
            }

            override fun windowLostFocus(e: WindowEvent) {
                if (e.oppositeWindow != null && SwingUtilities.isDescendingFrom(e.oppositeWindow, this@PopoverDialog)) {
                    return
                }
//                this@ClickAwayDialog.isVisible = false
                this@PopoverDialog.dispose()

            }
        })
    }
}