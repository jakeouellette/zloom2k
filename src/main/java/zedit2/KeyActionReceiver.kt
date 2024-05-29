package zedit2

import java.awt.event.ActionEvent

interface KeyActionReceiver {
    fun keyAction(actionName: String?, e: ActionEvent?)
}
