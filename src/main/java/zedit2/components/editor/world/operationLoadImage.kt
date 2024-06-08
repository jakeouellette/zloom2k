package zedit2.components.editor.world

import zedit2.components.ConvertImage
import zedit2.components.WorldEditor
import zedit2.util.FileUtil.getFileChooser
import java.io.IOException
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.JOptionPane


internal fun WorldEditor.operationLoadImage() {
    val fileChooser = getFileChooser(arrayOf("png", "jpg", "jpeg", "gif", "bmp"), "Bitmap image file")
    val result = fileChooser.showOpenDialog(frame)
    if (result == JFileChooser.APPROVE_OPTION) {
        val file = fileChooser.selectedFile
        try {
            val image = ImageIO.read(file) ?: throw RuntimeException("Unrecognised file format")
            ConvertImage(this, image)
        } catch (e: IOException) {
            JOptionPane.showMessageDialog(frame, e, "Error loading image", JOptionPane.ERROR_MESSAGE)
        } catch (e: RuntimeException) {
            JOptionPane.showMessageDialog(frame, e, "Error loading image", JOptionPane.ERROR_MESSAGE)
        }
    }
}