package zedit2.components

import java.awt.image.BufferedImage

interface ImageRetriever {
    fun extractCharImage(
        chr: Int,
        col: Int,
        zoomx: Int,
        zoomy: Int,
        blinkingTime: Boolean,
        pattern: String
    ) : BufferedImage
}