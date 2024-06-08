package zedit2.components.editor.world

import zedit2.components.GlobalEditor
import zedit2.components.WorldEditor
import zedit2.components.editor.operation.BufferOperation
import java.awt.Color

class BufferOperationImpl(val editor: WorldEditor) : BufferOperation {
    override fun operationGetFromBuffer(bufferNum: Int) {
        val key = String.format(editor.prefix() + "BUF_%d", bufferNum)
        if (GlobalEditor.isKey(key)) {
            GlobalEditor.decodeBuffer(GlobalEditor.getString(key))
            GlobalEditor.setBufferPos(bufferNum, editor.currentBufferManager)
            editor.currentBufferManager.updateBuffer(bufferNum)
            editor.afterUpdate()
            editor.editingModePane.display(Color.PINK, 750, "Loaded from buffer #$bufferNum")
        }
    }
}