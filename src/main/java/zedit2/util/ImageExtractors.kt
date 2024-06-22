package zedit2.util

import zedit2.components.DosCanvas.Companion.CHAR_H
import zedit2.components.DosCanvas.Companion.CHAR_W
import zedit2.model.spatial.Dim
import zedit2.util.TilePainters.drawTile
import java.awt.image.BufferedImage

object ImageExtractors {
    class ExtractionResponse( val blinkState: Boolean, val image: BufferedImage)

    /**
     * Format of pattern is:
     * @ - character
     * $ - character, but no blink
     * - blank
     * _ - col (bg)
     * # - col (fg)
     * >255 - 0xAABB (A = chr, B = col)
     *
     */
    fun extractCharImage(
        chr: Int,
        col: Int,
        zoomx: Int,
        zoomy: Int,
        blinkingTime: Boolean,
        pattern: String,
        blink: Boolean,
        palette: IntArray,
        charBuffer: BufferedImage?
    ): ExtractionResponse {
        return extractCharImageWH(chr, col, zoomx,zoomy, blinkingTime, pattern, Dim(pattern.length, 1), blink, palette, charBuffer)
    }

    fun extractCharImageWH(
        chr: Int,
        col: Int,
        zoomx: Int,
        zoomy: Int,
        blinkingTime: Boolean,
        pattern: String,
        dim : Dim,
        blink: Boolean,
        palette: IntArray,
        charBuffer: BufferedImage?
    ): ExtractionResponse {
        val img = BufferedImage(CHAR_W * zoomx * dim.w, CHAR_H * zoomy * dim.h, BufferedImage.TYPE_INT_ARGB)
        val blinkSaved = blink
        var blinkState = blink
        for (i in 0 until pattern.length) {
            when (val c = pattern[i]) {
                '@' -> drawTile(
                    img.graphics,
                    chr,
                    col,
                    i % dim.w,
                    i / dim.w,
                    zoomx,
                    zoomy,
                    blink=blinkSaved,
                    blinkingTime=blinkingTime,
                    palette,
                    charBuffer
                )
                '$' -> {
                    blinkState = false
                    drawTile(
                        img.graphics,
                        chr,
                        col,
                        i % dim.w,
                        i / dim.w,
                        zoomx,
                        zoomy,
                        blink=blinkSaved,
                        blinkingTime=blinkingTime,
                        palette,
                        charBuffer
                    )
                    blinkState = blinkSaved
                }

                '_' -> {
                    blinkState = false
                    drawTile(img.graphics, 32, col, i % dim.w, i / dim.w, zoomx,zoomy, blink=blinkSaved, blinkingTime=blinkingTime, palette,
                        charBuffer)
                    blinkState = blinkSaved
                }

                '#' -> {
                    blinkState = false
                    drawTile(img.graphics, 254, col, i % dim.w, i / dim.w, zoomx,zoomy, blink=blinkSaved, blinkingTime=blinkingTime, palette,
                        charBuffer)
                    blinkState = blinkSaved
                }

                ' ' -> {}
                else -> {
                    val cVal = c.code
                    blinkState = false
                    drawTile(
                        img.graphics,
                        (cVal and 0xFF00) shr 8,
                        cVal and 0xFF,
                        i % dim.w,
                        i / dim.w,
                        zoomx,
                        zoomy,
                        blink=blinkSaved, blinkingTime=blinkingTime, palette, charBuffer)

                    blinkState = blinkSaved
                }
            }
        }
        return ExtractionResponse(blinkState, img)
    }
}