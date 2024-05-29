package zedit2

import java.awt.Image
import javax.swing.ImageIcon

class BlinkingImageIcon(var img1: Image, var img2: Image) : ImageIcon(img1) {
    fun blink(blinkState: Boolean) {
        if (!blinkState) {
            this.image = img1
        } else {
            this.image = img2
        }
    }
}
