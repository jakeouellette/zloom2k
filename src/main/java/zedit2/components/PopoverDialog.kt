package zedit2.components

import zedit2.util.Logger
import zedit2.util.Logger.TAG
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
                Logger.i(TAG) {"Focus Gained. $e"}
            }

            override fun windowLostFocus(e: WindowEvent) {
                Logger.i(TAG) {"Focus lost. $e"}
                if (e.oppositeWindow != null && SwingUtilities.isDescendingFrom(e.oppositeWindow, this@PopoverDialog)) {
                    Logger.i(TAG) {"Descending..."}
                    return
                }
//                this@ClickAwayDialog.isVisible = false
                this@PopoverDialog.dispose()

            }
        })
    }
}