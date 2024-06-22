package zedit2.components

import zedit2.components.editor.operation.BufferOperation
import zedit2.model.BufferModel
import zedit2.util.Logger
import zedit2.util.Logger.TAG
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.io.IOException
import javax.swing.*

class BufferManager(val bufferOperation : BufferOperation, prefix : String, dosCanvas : DosCanvas, globalEditor : GlobalEditor) : JList<String?>(), MouseListener {
    //        defaultCloseOperation = DISPOSE_ON_CLOSE
//        addWindowListener(this)
//        setIconImage(editor.canvas.extractCharImage(228, 0x5F, 1, 1, false, "$"))
//        title = "Buffer Manager"
//        contentPane.layout = BorderLayout()
    private var listModel: BufferModel
    val scrollpane = JScrollPane(this)

    init {
//        this.layout = BorderLayout()

        this.layoutOrientation = HORIZONTAL_WRAP

        this.addMouseListener(this)
        this.dropMode = DropMode.ON
        this.dragEnabled = true
        // http://www.java2s.com/example/java/swing/drag-and-drop-custom-transferhandler-for-a-jlist.html
        this.transferHandler = object : TransferHandler() {
            override fun getSourceActions(c: JComponent): Int {
                return COPY_OR_MOVE
            }

            override fun createTransferable(source: JComponent): Transferable {
                val sourceList = source as JList<String>
                // TODO(jakeouellette): confirm this doesn't create a problem by throwing, previously allowed null
                val data = sourceList.selectedValue ?: throw RuntimeException("Unexpected unselected value")
                return StringSelection(String.format("%d:%s", sourceList.selectedIndex, data))
            }

            override fun exportDone(source: JComponent, data: Transferable, action: Int) {
                val sourceList = source as JList<String>
                if (data == null) return
                var from = -1
                try {
                    val stringData = data.getTransferData(DataFlavor.stringFlavor) as String
                    from = getBufferDataIdx(stringData)
                } catch (e: UnsupportedFlavorException) {
                    e.printStackTrace()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                if (action == MOVE && from != -1) {
                    val listModel = sourceList.model as BufferModel
                    listModel.remove(from)
                }
            }

            override fun canImport(support: TransferSupport): Boolean {
                if (!support.isDrop) {
                    return false
                }
                return support.isDataFlavorSupported(DataFlavor.stringFlavor)
            }

            override fun importData(support: TransferSupport): Boolean {
                if (!this.canImport(support)) {
                    return false
                }
                val t = support.transferable
                val data: String
                try {
                    data = t.getTransferData(DataFlavor.stringFlavor) as String
                } catch (e: UnsupportedFlavorException) {
                    e.printStackTrace()
                    return false
                } catch (e: IOException) {
                    e.printStackTrace()
                    return false
                }
                val from = getBufferDataIdx(data)

                val dropLocation = support.dropLocation as JList.DropLocation

                val dropIndex = dropLocation.index
                val targetList = support.component as JList<String>

                // Do insert
                val listModel = targetList.model as BufferModel

                return if (dropLocation.isInsert) {
                    listModel.add(dropIndex, from, data)
                } else {
                    listModel.set(dropIndex, from, data)
                }
            }
        }

//        isResizable = false
//        val scrollPane = JScrollPane(list)
//        contentPane.add(scrollPane, BorderLayout.CENTER)
        listModel = BufferModel(this, prefix, dosCanvas, globalEditor)
        this.setCellRenderer(listModel)
        this.model = listModel

        resizeList()
//        setLocationRelativeTo(editor.frameForRelativePositioning)
//        focusableWindowState = false
//        isAlwaysOnTop = true
//        isVisible = true

        this.scrollpane.preferredSize = this.preferredSize
        this.scrollpane.minimumSize = this.preferredSize
    }

    fun updateBuffer(num: Int) {
        listModel.updateBuffer(num)
    }

    fun updateSelected(num: Int) {
        listModel.updateSelected(num)
    }

//    override fun windowClosed(e: WindowEvent) {
//        editor.removeBufferManager()
//    }

    override fun mouseClicked(e: MouseEvent) {
        val cell = this.locationToIndex(e.point)
        if (cell == -1) return
        val bounds = this.getCellBounds(cell, cell) ?: return
        val x = e.x
        val y = e.y
        if (x >= bounds.x && y >= bounds.y && x < bounds.x + bounds.getWidth() && y < bounds.y + bounds.getHeight()) {
            bufferOperation.operationGetFromBuffer(listModel.idxToBufNum(cell))
        }

        e.consume()
    }

//    override fun windowOpened(e: WindowEvent) {}
//    override fun windowClosing(e: WindowEvent) {}
//    override fun windowIconified(e: WindowEvent) {}
//    override fun windowDeiconified(e: WindowEvent) {}
//    override fun windowActivated(e: WindowEvent) {}
//    override fun windowDeactivated(e: WindowEvent) {}

override fun mousePressed(e: MouseEvent) {
        Logger.i(TAG) { "Buffer Size $preferredSize, ${listModel.size}" }
    }
    override fun mouseReleased(e: MouseEvent) {}
    override fun mouseEntered(e: MouseEvent) {}
    override fun mouseExited(e: MouseEvent) {}

    fun resizeList() {
        Logger.i(TAG) {"Buffer Size $preferredSize, ${listModel.size}"}
            //System.out.println("Before (actual): " + scrollPane.getSize());
            //int beforeHeight = scrollPane.getHeight();
            //int beforeWidth = scrollPane.getWidth();
        this.visibleRowCount = (listModel.size + 4) / 5

            //int afterHeight = scrollPane.getPreferredSize().getHeight();
            //int afterWidth = scrollPane.getPreferredSize().getWidth();
            //int maxHeight = Math.max(beforeHeight, afterHeight);
            //int maxWidth = Math.max(beforeWidth, afterWidth);

            //if (beforeHeight < MAX_HEIGHT && afterHeight > MAX_HEIGHT) {
//                maxHeight = Math.min(maxHeight, MAX_HEIGHT);
//            }
        // TODO(jakeouellette): previously, this pack was called
//            pack()

            //if (maxHeight > MAX_HEIGHT) {
            //    scrollPane.setPreferredSize(new Dimension(maxWidth, maxHeight));
            //}
        Logger.i(TAG) {"visible row count $visibleRowCount"}
    }

    companion object {
        fun getBufferDataIdx(stringData: String): Int {
            val colonPos = stringData.indexOf(':')
            return stringData.substring(0, colonPos).toInt()
        }

        @JvmStatic
        fun getBufferDataString(stringData: String): String {
            val colonPos = stringData.indexOf(':')
            return stringData.substring(colonPos + 1)
        }
    }
}
