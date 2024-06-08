package zedit2.components.editor.world

import zedit2.components.GlobalEditor
import zedit2.components.WorldEditor
import zedit2.util.FileUtil.getFileChooser
import java.awt.Color
import javax.swing.JFileChooser

internal fun WorldEditor.menuSave() {
    if (path != null) {
        val r = saveTo(path!!)
        if (r) editingModePane.display(Color.ORANGE, 1500, "Saved World")
        afterUpdate()
    } else {
        menuSaveAs()
    }
}


internal fun WorldEditor.menuSaveAs(): Boolean {
    val fileChooser = getFileChooser(arrayOf("zzt", "szt", "sav"), "ZZT/Super ZZT world and save files")
    if (path != null) fileChooser.selectedFile = path

    val result = fileChooser.showSaveDialog(frame)
    if (result == JFileChooser.APPROVE_OPTION) {
        val file = fileChooser.selectedFile
        if (saveTo(file)) {
            updateDefaultDir(file)
            path = file
            GlobalEditor.defaultDirectory = path!!
            GlobalEditor.addToRecent(path)
            updateMenu()
            afterUpdate()
            editingModePane.display(Color.ORANGE, 1500, "Saved World")
            return true
        }
    }
    return false
}