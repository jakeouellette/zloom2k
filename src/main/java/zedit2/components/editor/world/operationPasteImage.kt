package zedit2.components.editor.world

import zedit2.components.ConvertImage
import zedit2.components.WorldEditor

internal fun WorldEditor.operationPasteImage() {
    val image = clipboardImage ?: return

    ConvertImage(this, image)
}