package zedit2.util

import zedit2.components.GlobalEditor
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileFilter

object FileUtil {
    fun getFileChooser(exts: Array<String>, desc: String): JFileChooser {
        val fileChooser = JFileChooser()
        val filter: FileFilter = object : FileFilter() {
            override fun accept(f: File): Boolean {
                if (f.isDirectory) return true
                val name = f.name
                val extPos = name.lastIndexOf('.')
                if (extPos != -1) {
                    val ext = name.substring(extPos + 1)
                    for (validExt in exts) {
                        if (ext.equals(validExt, ignoreCase = true)) return true
                    }
                }
                return false
            }

            override fun getDescription(): String {
                return desc
            }
        }
        fileChooser.fileFilter = filter
        fileChooser.currentDirectory = GlobalEditor.defaultDirectory
        //fileChooser.setCurrentDirectory(new File("C:\\Users\\" + System.getProperty("user.name") + "\\Dropbox\\YHWH\\zzt\\zzt\\"));
        return fileChooser
    }
}