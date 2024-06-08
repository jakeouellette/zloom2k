package zedit2.components.editor.world

import zedit2.components.GlobalEditor
import zedit2.components.WorldEditor
import java.awt.Color
import kotlin.math.max


fun WorldEditor.operationSaveToBuffer(bufferNum: Int) {
    if (blockStartX != -1) {
        // Block selected.
        blockCopy(false)
    }
    val data = GlobalEditor.encodeBuffer()
    setBlockBuffer(0, 0, null, false)
    val key = String.format(prefix() + "BUF_%d", bufferNum)
    GlobalEditor.setString(key, data)
    GlobalEditor.setInt(
        prefix() + "BUF_MAX", max(
            bufferNum.toDouble(),
            GlobalEditor.getInt(prefix() + "BUF_MAX", 0).toDouble()
        ).toInt()
    )
    GlobalEditor.setBufferPos(bufferNum, currentBufferManager)
    currentBufferManager.updateBuffer(bufferNum)
    afterUpdate()
    editingModePane.display(Color.MAGENTA, 1500, "Saved to buffer #$bufferNum")
}