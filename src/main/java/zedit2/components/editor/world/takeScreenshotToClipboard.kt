package zedit2.components.editor.world

import zedit2.components.WorldEditor
import java.awt.Color
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException


internal fun WorldEditor.takeScreenshotToClipboard() {
    val boardBuffer = canvas.getBoardBuffer(worldData.isSuperZZT)
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    val transferable: Transferable = object : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> {
            return arrayOf(DataFlavor.imageFlavor)
        }

        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean {
            return flavor.equals(DataFlavor.imageFlavor)
        }

        @Throws(UnsupportedFlavorException::class)
        override fun getTransferData(flavor: DataFlavor): Any {
            if (flavor.equals(DataFlavor.imageFlavor)) return boardBuffer!!
            throw UnsupportedFlavorException(flavor)
        }
    }
    clipboard.setContents(transferable) { clipbrd: Clipboard?, contents: Transferable? -> }
    editingModePane.display(Color.YELLOW, 1500, "Copied Screenshot")
}