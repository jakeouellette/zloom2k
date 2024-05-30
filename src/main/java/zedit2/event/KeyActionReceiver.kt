package zedit2.event

import java.awt.event.ActionEvent

interface KeyActionReceiver {
    fun keyAction(actionName: String?, e: ActionEvent?)
}
