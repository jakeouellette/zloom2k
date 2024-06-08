package zedit2.components.editor.world

import zedit2.components.About
import zedit2.components.WorldEditor

internal fun WorldEditor.menuAbout() {
    About(this.frameForRelativePositioning)
}