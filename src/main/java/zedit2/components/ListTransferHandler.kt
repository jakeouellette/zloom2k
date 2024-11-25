package zedit2.components

import zedit2.model.Board
import zedit2.util.Logger
import zedit2.util.Logger.TAG
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.TransferHandler


class ListTransferHandler( val onBoardSwap : (Int, Int) -> Unit) : TransferHandler() {
    private var indices: IntArray? = null
    private var addIndex = -1 //Location where items were added
    private var addCount = 0 //Number of items added.

    /**
     * We only support importing strings.
     */
    override fun canImport(info: TransferSupport)
    : Boolean {
        // Check for String flavor
        if (!info.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            return false
        }
        return true
    }

    /**
     * Bundle up the selected items in a single list for export.
     * Each line is separated by a newline.
     */
    override fun createTransferable(c: JComponent): Transferable {
        val list = c as JList<*>
        indices = list.selectedIndices
        val selection = list.selectedIndex
        Logger.i(TAG) { "${selection.javaClass}"}
        return StringSelection(selection.toString())
    }

    /**
     * We support both copy and move actions.
     */
    override fun getSourceActions(c: JComponent): Int {
        return MOVE
    }

    /**
     * Perform the actual import.  This demo only supports drag and drop.
     */
    override fun importData(info: TransferSupport): Boolean {
        if (!info.isDrop) {

            return false
        }
        Logger.i(TAG) {"Drop dl:${info.dropLocation} da:${info.dropAction} td:${info.transferable.getTransferData(DataFlavor.stringFlavor) as String}"}
        val targetIndex = Integer.valueOf(info.transferable.getTransferData(DataFlavor.stringFlavor) as String)
        onBoardSwap(targetIndex, (info.dropLocation as JList.DropLocation).index)

        return true
//
//        val list = info.component as JList<String>
//        val listModel = list.model as DefaultListModel<String>
//        val dl = info.dropLocation as JList.DropLocation
//        var index = dl.index
//        val insert = dl.isInsert
//
//        // Get the string that is being dropped.
//        val t = info.transferable
//        val data: String
//        try {
//            data = t.getTransferData(DataFlavor.stringFlavor) as String
//        } catch (e: Exception) {
//            return false
//        }
//
//
//        // Wherever there is a newline in the incoming data,
//        // break it into a separate item in the list.
//        val values = data.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
//
//        addIndex = index
//        addCount = values.size
//
//
//        // Perform the actual import.
//        for (i in values.indices) {
//            if (insert) {
//                listModel.add(index++, values[i])
//            } else {
//                // If the items go beyond the end of the current
//                // list, add them in.
//                if (index < listModel.size) {
//                    listModel.set(index++, values[i])
//                } else {
//                    listModel.add(index++, values[i])
//                }
//            }
//        }
//        return true
    }

    /**
     * Remove the items moved from the list.
     */
    override fun exportDone(c: JComponent, data: Transferable, action: Int) {
//        val source = c as JList<*>
//        val listModel = source.model as DefaultListModel<*>
//
//        if (action == MOVE) {
//            for (i in indices!!.indices.reversed()) {
//                listModel.remove(indices!![i])
//            }
//        }
//
//        indices = null
//        addCount = 0
//        addIndex = -1
    }
}