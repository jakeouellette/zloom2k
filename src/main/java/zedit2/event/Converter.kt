package zedit2.event

import zedit2.util.ConvObj
import zedit2.util.ZType
import zedit2.components.DosCanvas
import zedit2.components.Util
import java.awt.image.BufferedImage
import java.util.*
import java.util.function.Consumer
import javax.swing.SwingUtilities

class Converter(
    private val szzt: Boolean,
    private val outw: Int,
    private val outh: Int,
    private val mw: Int,
    private val mh: Int,
    private val maxObjs: Int,
    scaledImage: BufferedImage
) {
    private val scaledImage: BufferedImage = scaledImage.getSubimage(0, 0, scaledImage.width, scaledImage.height)
    private var blinking = true

    private val elementIdsList = ArrayList<Int>()
    private val elementCharsList = ArrayList<Int>()
    private lateinit var elementIds: IntArray
    private lateinit var elementChars: IntArray
    private lateinit var palette: ByteArray
    private lateinit var charset: ByteArray
    private lateinit var colChrCombos: Array<BooleanArray>
    private lateinit var charRmse: DoubleArray
    private lateinit var objRmse: DoubleArray
    private lateinit var objChr: Array<IntArray>
    private lateinit var objCol: Array<IntArray>

    private lateinit var charGfx: Array<ByteArray>

    private var running = false
    private var thread: Thread? = null
    private var callback: ConverterCallback? = null
    private var checkVal = 0

    fun addElement(id: Int, chr: Int) {
        elementIdsList.add(id)
        elementCharsList.add(chr)
    }

    fun beginConvert() {
        thread = Thread { this.convertThread() }
        running = true
        thread!!.start()
    }

    private fun convertThread() {
        finaliseSetup()

        val coords = ArrayList<ArrayList<Int>>()
        for (y in 0 until outh) {
            for (x in 0 until outw) {
                coords.add(Util.pair(x, y))
            }
        }
        coords.parallelStream().forEach(Consumer { integers: ArrayList<Int> ->
            convChar(
                integers[0], integers[1]
            )
        })

        if (maxObjs > 0) {
            val best = PriorityQueue<ConvObj>()
            for (y in 0 until outh) {
                for (x in 0 until outw) {
                    val pos = y * outw + x
                    val objImprovement = charRmse[pos] - objRmse[pos]
                    best.add(ConvObj(objImprovement, x, y))
                }
            }
            for (i in 0 until maxObjs) {
                if (!running) return
                val conv = best.poll() ?: break
                if (conv.rmseImprovement <= 0.0000) break
                //System.out.printf("%dth best: rmse improvement %f\n", i, conv.getRmseImprovement());
                val x = conv.x
                val y = conv.y
                val id = ZType.OBJECT
                val chr = objChr[y][x]
                val col = objCol[y][x]
                callback!!.converted(checkVal, x, y, id, col, chr, col)
            }
        }

        if (running) callback!!.finished(checkVal)
    }


    private fun finaliseSetup() {
        // Write this into a proper array so we can access it faster

        elementIds = IntArray(elementIdsList.size)
        elementChars = IntArray(elementCharsList.size)
        for (i in elementIds.indices) {
            elementIds[i] = elementIdsList[i]
            elementChars[i] = elementCharsList[i]
        }

        // Reduce charset down to macroblocks
        val gfxW = DosCanvas.CHAR_W / mw * DosCanvas.CHAR_COUNT
        val gfxH = DosCanvas.CHAR_H / mh
        charGfx = Array(gfxH) { ByteArray(gfxW) }
        val add = (112 / mw / mh).toByte()
        for (row in 0 until DosCanvas.CHAR_H) {
            for (chr in 0 until DosCanvas.CHAR_COUNT) {
                val charByte = charset[chr * DosCanvas.CHAR_H + row]
                for (col in 0 until DosCanvas.CHAR_W) {
                    val mask = (128 shr col).toByte()
                    if ((charByte.toInt() and mask.toInt()) != 0) {
                        charGfx[row / mh][(chr * DosCanvas.CHAR_W + col) / mw] =
                            (charGfx[row / mh][(chr * DosCanvas.CHAR_W + col) / mw] + add).toByte()
                    }
                }
            }
        }

        // Allocate room for rmse storage
        charRmse = DoubleArray(outw * outh)
        objRmse = DoubleArray(outw * outh)
        objChr = Array(outh) { IntArray(outw) }
        objCol = Array(outh) { IntArray(outw) }

        // Generate col/chr combos
        val colRange = if (blinking) 128 else 256
        colChrCombos = Array(colRange) { BooleanArray(DosCanvas.CHAR_COUNT) }

        for (i in 0 until colRange) {
            Arrays.fill(colChrCombos[i], true)
        }
    }

    private fun convChar(charX: Int, charY: Int) {
        if (!running) return
        val fullrgb = scaledImage.getRGB(
            charX * DosCanvas.CHAR_W,
            charY * DosCanvas.CHAR_H,
            DosCanvas.CHAR_W,
            DosCanvas.CHAR_H,
            null,
            0,
            DosCanvas.CHAR_W
        )
        val lab = Array(DosCanvas.CHAR_H / mh) { Array(DosCanvas.CHAR_W / mw) { DoubleArray(3) } }
        for (y in 0 until DosCanvas.CHAR_H) {
            for (x in 0 until DosCanvas.CHAR_W) {
                val rgb = fullrgb[y * DosCanvas.CHAR_W + x]
                val r = (rgb and 0xFF0000) shr 16
                val g = (rgb and 0x00FF00) shr 8
                val b = rgb and 0x0000FF
                val l = Util.rgbToLab(r, g, b)
                for (c in 0..2) {
                    lab[y / mh][x / mw][c] += l[c]
                }
            }
        }
        val div = (mw * mh).toDouble()
        for (y in 0 until DosCanvas.CHAR_H / mh) {
            for (x in 0 until DosCanvas.CHAR_W / mw) {
                for (c in 0..2) {
                    lab[y][x][c] /= div
                }
            }
        }
        val colRange = if (blinking) 128 else 256
        var lowest = Double.MAX_VALUE
        var bestChr = -1
        var bestId = -1
        var bestCol = -1
        var bestVCol = -1
        val combos = colChrCombos.clone()

        for (elementIdx in elementChars.indices) {
            val elementId = elementIds[elementIdx]
            val elementChar = elementChars[elementIdx]

            val textColour = ZType.getTextColour(szzt, elementId)
            if (textColour == -1) {
                // Not text, so use the char
                for (col in 0 until (if (elementId == ZType.EMPTY) 1 else colRange)) {
                    if (!running) return
                    combos[col][elementChar] = false
                    val rmse = cmpChr(elementChar, col, lab)
                    if (rmse < lowest) {
                        lowest = rmse
                        bestId = elementId
                        bestChr = elementChar
                        bestCol = col
                        bestVCol = col
                    }
                }
            } else {
                // Text, so use the col
                if (textColour < colRange) {
                    for (chr in 0 until DosCanvas.CHAR_COUNT) {
                        if (!running) return
                        combos[textColour][chr] = false
                        val rmse = cmpChr(chr, textColour, lab)
                        if (rmse < lowest) {
                            lowest = rmse
                            bestId = elementId
                            bestChr = chr
                            bestCol = chr
                            bestVCol = textColour
                        }
                    }
                }
            }
        }
        run {
            val chr = bestChr
            val col = bestCol
            val id = bestId
            val vcol = bestVCol
            charRmse[charY * outw + charX] = lowest
            if (!running) return
            SwingUtilities.invokeLater { callback!!.converted(checkVal, charX, charY, id, col, chr, vcol) }
        }

        if (maxObjs > 0) {
            lowest = Double.MAX_VALUE

            for (col in 0 until colRange) {
                for (chr in 0 until DosCanvas.CHAR_COUNT) {
                    if (!running) return
                    if (combos[col][chr]) {
                        val rmse = cmpChr(chr, col, lab)
                        if (rmse < lowest) {
                            lowest = rmse
                            bestChr = chr
                            bestCol = col
                        }
                    }
                }
            }

            objRmse[charY * outw + charX] = lowest
            objChr[charY][charX] = bestChr
            objCol[charY][charX] = bestCol
        }
    }

    private fun palLab(col: Int): DoubleArray {
        var r = palette[col * 3 + 0].toInt()
        var g = palette[col * 3 + 1].toInt()
        var b = palette[col * 3 + 2].toInt()
        r = r * 255 / 63
        g = g * 255 / 63
        b = b * 255 / 63
        return Util.rgbToLab(r, g, b)
    }

    private fun cmpChr(elementChar: Int, col: Int, lab: Array<Array<DoubleArray>>): Double {
        val bg = palLab(col / 16)
        val fg = palLab(col % 16)
        val offX = elementChar * DosCanvas.CHAR_W / mw
        var rmse = 0.0
        for (y in 0 until DosCanvas.CHAR_H / mh) {
            for (x in 0 until DosCanvas.CHAR_W / mw) {
                val fgMult = charGfx[y][x + offX] / 112.0
                val bgMult = (112 - charGfx[y][x + offX]) / 112.0
                for (c in 0..2) {
                    val diff = bg[c] * bgMult + fg[c] * fgMult - lab[y][x][c]
                    rmse += diff * diff
                }
            }
        }
        return rmse
    }

    fun stop() {
        if (!running) return
        running = false
        try {
            thread!!.join()
        } catch (ignored: InterruptedException) {
        }
    }

    fun setGfx(canvas: DosCanvas) {
        palette = canvas.paletteData!!.clone()
        charset = canvas.charset.clone()
    }

    fun setBlink(blinking: Boolean) {
        this.blinking = blinking
    }

    fun setCallback(converterCallback: ConverterCallback?) {
        this.callback = converterCallback
    }

    fun setCheckVal(checkVal: Int) {
        this.checkVal = checkVal
    }
}
