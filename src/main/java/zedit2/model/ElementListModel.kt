package zedit2.model

import zedit2.components.GlobalEditor
import zedit2.util.StringsSelection
import java.awt.BorderLayout
import java.awt.Color
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.IOException
import java.util.stream.IntStream
import javax.swing.*

class ElementListModel(ge: GlobalEditor, f: Int) : AbstractListModel<String>() {
    private val itemVec = ArrayList<String>()

    init {
        IntStream.iterate(0) { i: Int -> i + 1 }
            .mapToObj { i: Int -> String.format("F%d_MENU_%d", f, i) }
            .map { key: String? -> GlobalEditor.getString(key, "") }.takeWhile { `val`: String -> !`val`.isEmpty() }
            .forEach { `val`: String -> itemVec.add(`val`) }
    }

    fun clear() {
        if (itemVec.size == 0) return
        val lastItem = itemVec.size - 1
        itemVec.clear()
        fireIntervalRemoved(this, 0, lastItem)
    }

    private fun remove(toRemove: IntArray) {
        if (toRemove.size == 0) return
        for (i in toRemove.indices.reversed()) {
            val idx = toRemove[i]
            itemVec.removeAt(idx)
        }
        fireIntervalRemoved(this, toRemove[0], toRemove[toRemove.size - 1])
    }

    fun insert(dropIdx: Int, insertItems: List<String>, dropOnto: Boolean) {
        var dropIdx = dropIdx
        if (insertItems.isEmpty()) return
        val posStart: Int
        var posEnd: Int
        if (!dropOnto) {
            for (str in insertItems) {
                itemVec.add(str)
            }
            posStart = itemVec.size - insertItems.size
            posEnd = itemVec.size - 1
        } else {
            posStart = dropIdx
            posEnd = dropIdx
            for (str in insertItems) {
                itemVec.add(dropIdx, str)
                posEnd = dropIdx
                dropIdx++
            }
        }
        fireIntervalAdded(this, posStart, posEnd)
    }

    override fun getSize(): Int {
        return itemVec.size
    }

    override fun getElementAt(index: Int): String {
        return itemVec[index]
    }

    companion object {
        @JvmStatic
        val renderer: ListCellRenderer<in String>
            get() {
                return ListCellRenderer { list: JList<out String>, value: String, index: Int, isSelected: Boolean, cellHasFocus: Boolean ->
                    var value = value
                    var isSelected = isSelected
                    if (!list.hasFocus()) {
                        isSelected = false
                    }
                    val panel = JPanel(BorderLayout())

                    val openBrace = value.indexOf('(')
                    if (openBrace != -1) {
                        val closeBrace = value.lastIndexOf(')')
                        value = if (closeBrace + 1 == value.length) {
                            value.substring(0, openBrace)
                        } else {
                            value.substring(closeBrace + 1)
                        }
                    } else {
                        val exclPoint = value.indexOf('!')
                        if (exclPoint != -1) {
                            value = value.substring(exclPoint + 1)
                        }
                    }

                    val lbl = JLabel(value)
                    if (isSelected) {
                        lbl.foreground = Color.WHITE
                        panel.background = Color.BLUE
                    } else {
                        panel.background = Color.WHITE
                    }
                    if (cellHasFocus) {
                        panel.border = BorderFactory.createEtchedBorder()
                    }
                    panel.add(lbl, BorderLayout.WEST)
                    panel
                }
            }

        @JvmStatic
        val transferHandler: TransferHandler
            get() {
                val transferHandler: TransferHandler = object : TransferHandler() {
                    override fun getSourceActions(c: JComponent): Int {
                        return MOVE
                    }

                    override fun createTransferable(source: JComponent): Transferable {
                        val sourceList = source as JList<String>
                        // TODO(jakeouellette): Decide if this can be truly null.
                        val data = sourceList.selectedValuesList ?: return throw RuntimeException("Unexpected empty selected values")
                        return StringsSelection(data)
                    }

                    override fun exportDone(source: JComponent, data: Transferable, action: Int) {
                        if (data == null) return
                        if (action != MOVE) return
                        val sourceList = source as JList<String>
                        val model = sourceList.model as ElementListModel
                        model.remove(sourceList.selectedIndices)
                    }

                    override fun canImport(support: TransferSupport): Boolean {
                        return support.isDataFlavorSupported(StringsSelection.flavor)
                    }

                    override fun importData(support: TransferSupport): Boolean {
                        println("importData")
                        if (!canImport(support)) return false
                        println("passed")
                        val dropOn = support.component as JList<String>
                        val model = dropOn.model as ElementListModel
                        val loc = support.dropLocation.dropPoint
                        val dropIdx = dropOn.locationToIndex(loc)
                        var dropOnto = false
                        if (dropIdx != -1) {
                            dropOnto = dropOn.getCellBounds(dropIdx, dropIdx).contains(loc)
                        }
                        val tr = support.transferable
                        try {
                            val strings = tr.getTransferData(StringsSelection.flavor) as List<String>
                            model.insert(dropIdx, strings, dropOnto)
                        } catch (e: UnsupportedFlavorException) {
                            return false
                        } catch (e: IOException) {
                            return false
                        }
                        return true
                    }
                }
                val img = BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB)

                transferHandler.dragImage = img
                return transferHandler
            }
    }
}
