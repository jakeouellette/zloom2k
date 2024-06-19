package zedit2.model

import zedit2.components.BufferManager
import zedit2.components.BufferManager.Companion.getBufferDataString
import zedit2.components.DosCanvas
import zedit2.components.GlobalEditor
import zedit2.model.spatial.Pos
import zedit2.util.Logger
import zedit2.util.Logger.TAG
import java.awt.Color
import java.awt.Component
import java.awt.image.BufferedImage
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import kotlin.math.max

class BufferModel(private val manager: BufferManager,
                  private val prefix: String,
                  private val canvas : DosCanvas,
                  private val globalEditor : GlobalEditor
) : AbstractListModel<String?>(),
    ListCellRenderer<String?> {
    private var size = 0
    private var selected: Int
    var listeners: ArrayList<ListDataListener> = ArrayList()

    init {
        selected = globalEditor.currentBufferNum
        updateBuffer(true)
    }

    override fun getSize(): Int {
        return size
    }

    override fun getElementAt(index: Int): String? {
        val bufferNum = idxToBufNum(index)
        val key = String.format(prefix + "BUF_%d", bufferNum)
        return if (globalEditor.isKey(key)) {
            globalEditor.getString(key)
        } else {

            null
        }
    }

    override fun addListDataListener(l: ListDataListener) {
        listeners.add(l)
    }

    override fun removeListDataListener(l: ListDataListener) {
        listeners.remove(l)
    }

    override fun getListCellRendererComponent(
        list: JList<out String?>,
        value: String?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        val bufferNum = idxToBufNum(index)
        val isSel = bufferNum == selected
        val BUFFER_W = 32
        val BUFFER_H = 32

        val img = BufferedImage(BUFFER_W, BUFFER_H, BufferedImage.TYPE_INT_RGB)
        val g = img.graphics
        val yOff = g.fontMetrics.ascent
        g.color = Color(0x7F7F7F)
        g.fillRect(0, 0, BUFFER_W, BUFFER_H)

        val bufImg = getClipImage(canvas, value)
        if (bufImg != null) {
            val imgW = bufImg.width
            val imgH = bufImg.height
            var scale = 0
            if (imgW * 4 <= BUFFER_W && imgH * 4 <= BUFFER_H) scale = 4
            else if (imgW * 3 <= BUFFER_W && imgH * 3 <= BUFFER_H) scale = 3
            else if (imgW * 2 <= BUFFER_W && imgH * 2 <= BUFFER_H) scale = 2
            else if (imgW <= BUFFER_W && imgH <= BUFFER_H) scale = 1

            val scaleW: Int
            val scaleH: Int

            if (scale == 0) {
                val imgL = max(imgW.toDouble(), imgH.toDouble()).toInt()
                val factor = 1.0 * BUFFER_W / imgL
                scaleW = Math.round(imgW * factor).toInt()
                scaleH = Math.round(imgH * factor).toInt()
            } else {
                scaleW = imgW * scale
                scaleH = imgH * scale
            }
            val offsetX = (BUFFER_W - scaleW) / 2
            val offsetY = (BUFFER_H - scaleH) / 2
            g.drawImage(bufImg, offsetX, offsetY, offsetX + scaleW, offsetY + scaleH, 0, 0, imgW, imgH, null)
        }

        if (isSel) {
            g.color = Color.BLACK
            g.drawRect(1, 1, BUFFER_W - 3, BUFFER_H - 3)
            g.color = Color.RED
            g.drawRect(2, 2, BUFFER_W - 5, BUFFER_H - 5)
            g.color = Color.YELLOW
            g.drawRect(3, 3, BUFFER_W - 7, BUFFER_H - 7)
            g.color = Color.RED
            g.drawRect(4, 4, BUFFER_W - 9, BUFFER_H - 9)
            g.color = Color.BLACK
            g.drawRect(5, 5, BUFFER_W - 11, BUFFER_H - 11)
        }
        if (isSelected) {
            g.color = Color(0x7F5FFFFF, true)
            g.drawRect(0, 0, BUFFER_W - 1, BUFFER_H - 1)
        }

        if (bufferNum < 10) {
            g.color = Color.BLACK
            g.drawString(bufferNum.toString(), 1 + 1, yOff - 2 + 1)
            g.color = Color.WHITE
            g.drawString(bufferNum.toString(), 1, yOff - 2)
        }


        return JLabel(ImageIcon(img))
        //return new JLabel(String.format("%s%d%s", isSel ? "*" : "", bufferNum, containsSomething ? " X" : ""));
    }

    fun updateBuffer(initializing :Boolean) {
        var bufMax = globalEditor.getInt(prefix + "BUF_MAX", 0)
        if (globalEditor.isKey(prefix + "BUF_0") && bufMax == 0) bufMax = 9
        else if (bufMax > 0) bufMax = bufNumToIdx(bufMax)
        bufMax = (bufMax + 10) / 5 * 5
        val oldSize = size
        size = max(bufMax.toDouble(), 10.0).toInt()
        if (size > oldSize) {
            updateListeners(ListDataEvent.INTERVAL_ADDED, oldSize, size - 1)
        } else if (size < oldSize) {
            updateListeners(ListDataEvent.INTERVAL_REMOVED, size, oldSize - 1)
        }
        if (size != oldSize && !initializing) {
            manager.resizeList()
        }
    }

    fun updateBuffer(bufferNum: Int) {
        var bufferNum = bufferNum
        bufferNum = bufNumToIdx(bufferNum)
        val withinOldRange = bufferNum < size
        updateBuffer(false)
        if (withinOldRange) {
            updateListeners(ListDataEvent.CONTENTS_CHANGED, bufferNum, bufferNum)
        }
    }

    private fun updateListeners(event: Int, first: Int, last: Int) {
        for (listener in listeners) {
            val e = ListDataEvent(this, event, first, last)
            listener.contentsChanged(e)
        }
    }

    fun updateSelected(num: Int) {
        val oldSelected = selected
        selected = num
        if (oldSelected >= 0) updateBuffer(oldSelected)
        if (selected >= 0) updateBuffer(selected)
    }

    fun bufNumToIdx(num: Int): Int {
        return if (num == 0) 9
        else if (num <= 9) num - 1
        else num
    }

    fun idxToBufNum(idx: Int): Int {
        return if (idx < 9) idx + 1
        else if (idx == 9) 0
        else idx
    }

    fun remove(idx: Int) {
        //System.out.printf("remove(%d)\n", idx);
        if (idx < 0) return
        val bufNum = idxToBufNum(idx)
        val key = String.format(prefix + "BUF_%d", bufNum)
        var bufMax = globalEditor.getInt(prefix + "BUF_MAX", 0)
        if (selected == bufNum) {
            globalEditor.clearBufferSelected()
            selected = -1
        }
        globalEditor.removeKey(key)
        if (bufNum == bufMax && bufMax > 0) {
            while (bufMax > 0) {
                bufMax--
                if (globalEditor.isKey(String.format(prefix + "BUF_%d", bufMax))) break
            }
            globalEditor.setInt(prefix + "BUF_MAX", bufMax)
        }
        updateBuffer(bufNum)
    }

    fun add(dropIndex: Int, fromIndex: Int, data: String?): Boolean {
        throw UnsupportedOperationException("Insert not supported")
    }

    fun set(dropIndex: Int, fromIndex: Int, data: String?): Boolean {
        //System.out.printf("set(%d, %d, %s)\n", dropIndex, fromIndex, data);
        if (dropIndex == fromIndex) return false
        if (dropIndex < 0) return false
        val bufNum = idxToBufNum(dropIndex)
        val key = String.format(prefix + "BUF_%d", bufNum)
        val bufMax = globalEditor.getInt(prefix + "BUF_MAX", 0)
        if (bufNum > bufMax) {
            globalEditor.setInt(prefix + "BUF_MAX", bufNum)
        }
        val encodedBuffer = getBufferDataString(data!!)
        globalEditor.setString(key, encodedBuffer)
        updateBuffer(bufNum)
        return true
    }

    companion object {
        @JvmStatic
        fun getClipImage(canvas: DosCanvas, value: String?): BufferedImage? {
            if (value != null) {
                val clip = Clip.decode(value)
                val dim = clip.dim
                val tiles = clip.tiles
                val bb = BufferBoard(clip.isSzzt, dim)
                for (y in 0 until dim.h) {
                    for (x in 0 until dim.w) {
                        val xy = Pos(x,y)
                        Logger.i(TAG) { "setTile $xy"}
                        bb.setTile(xy, tiles[xy.arrayIdx(dim.w)])
                    }
                }
                val chars = ByteArray(1)
                val cols = ByteArray(1)
                val gfx = StringBuilder()
                //canvas.extractCharImage()
                var recCol = 0
                for (y in 0 until dim.h) {
                    for (x in 0 until dim.w) {
                        val xy = Pos(x,y)
                        bb.drawCharacter(cols, chars, 0, xy)
                        var c = ((chars[0].toInt() and 0xFF) shl 8) or (cols[0].toInt() and 0xFF)
                        if (chars[0].toInt() == 0) {
                            recCol = cols[0].toInt() and 0xFF
                            c = '$'.code
                        }
                        gfx.append(c.toChar())
                    }
                }
                return canvas.extractCharImageWH(
                    0, recCol,
                    1, 1, false, gfx.toString(), dim
                )
            } else {
                return null
            }
        }
    }
}
