package zedit2.components.editor.world

import zedit2.components.Help
import zedit2.components.WorldEditor

internal fun WorldEditor.menuHelp() {
    Help(this.canvas, this.frameForRelativePositioning)
}