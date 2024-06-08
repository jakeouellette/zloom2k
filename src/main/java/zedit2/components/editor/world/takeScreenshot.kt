package zedit2.components.editor.world

import zedit2.components.GlobalEditor
import zedit2.components.Util.evalConfigDir
import zedit2.components.WorldEditor
import zedit2.util.CP437.toUnicode
import java.awt.Color
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO
import javax.swing.JOptionPane


internal fun WorldEditor.takeScreenshot() {
    val boardBuffer = canvas.getBoardBuffer(worldData.isSuperZZT)
    val now = LocalDateTime.now()
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val timeFormatter = DateTimeFormatter.ofPattern("HH-mm-ss")
    val screenshotNameTemplate = GlobalEditor.getString("SCREENSHOT_NAME", "Screenshot {date} {time}.png")
    var screenshotFilename = screenshotNameTemplate.replace("{date}", dateFormatter.format(now))
    screenshotFilename = screenshotFilename.replace("{time}", timeFormatter.format(now))
    screenshotFilename = screenshotFilename.replace("{worldname}", toUnicode(worldData.name))
    val currentBoardName = if (currentBoard == null) "(no board)" else toUnicode(currentBoard!!.getName())
    screenshotFilename = screenshotFilename.replace("{boardname}", currentBoardName)
    screenshotFilename = screenshotFilename.replace("{boardnum}", boardIdx.toString())

    //var screenshotFilename = String.format("Screenshot %s.png", dtf.format(now));
    val screenshotDir = evalConfigDir(GlobalEditor.getString("SCREENSHOT_DIR", ""))

    val file: File
    if (screenshotDir.isEmpty()) {
        val writer = GlobalEditor.getWriterInLocalDir(screenshotFilename, true)
        if (writer == null) {
            JOptionPane.showMessageDialog(
                frame,
                "Could not find a directory to save the screenshot to",
                "Failed to save screenshot",
                JOptionPane.ERROR_MESSAGE
            )
            return
        }
        file = writer.file
    } else {
        file = Path.of(screenshotDir, screenshotFilename).toFile()
    }
    try {
        ImageIO.write(boardBuffer, "png", file)
        //System.out.println("Saved screenshot to " + writer.getFile().toString());
        editingModePane.display(Color.YELLOW, 1500, "Saved Screenshot")
    } catch (e: IOException) {
        JOptionPane.showMessageDialog(frame, e, "Failed to save screenshot", JOptionPane.ERROR_MESSAGE)
    }
}