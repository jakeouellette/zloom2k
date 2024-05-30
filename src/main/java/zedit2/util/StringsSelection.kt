package zedit2.util

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.IOException

class StringsSelection(val strings: List<String>) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> {
        return arrayOf(flavor)
    }

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean {
        return (Companion.flavor.equals(flavor))
    }

    @Throws(UnsupportedFlavorException::class, IOException::class)
    override fun getTransferData(flavor: DataFlavor): Any {
        if (!isDataFlavorSupported(flavor)) {
            throw UnsupportedFlavorException(flavor)
        }
        return strings
    }

    companion object {
        val flavor: DataFlavor = DataFlavor(MutableList::class.java, "application/x-array-of-strings")
    }
}
