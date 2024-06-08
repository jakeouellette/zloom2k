package zedit2.components.editor.world

import zedit2.components.WorldEditor
import zedit2.components.WorldEditor.Companion.SHOW_NOTHING


internal fun WorldEditor.operationShowTileTypes(showMode: Int) {
    currentlyShowing = if (currentlyShowing == showMode) {
        SHOW_NOTHING
    } else {
        showMode
    }
    afterChangeShowing()
}