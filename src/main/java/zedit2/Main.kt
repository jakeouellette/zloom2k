package zedit2

import java.io.File
import java.io.IOException
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.UIManager

class Main : Runnable {
    override fun run() {
        try {
            //Util.removeAltProcessor();
            //var editor = new WorldEditor(ge, false);
            var defaultFile: String
            defaultFile = Util.evalConfigDir(GlobalEditor.getString("LOAD_FILE", ""))
            if (args.size > 0) {
                defaultFile = args[args.size - 1]
            }
            val editor: WorldEditor
            if (!defaultFile.isEmpty()) {
                editor = WorldEditor(GlobalEditor, File(defaultFile))
            } else {
                val defaultSzzt = GlobalEditor.getBoolean("DEFAULT_SZZT", false)
                editor = WorldEditor(GlobalEditor, defaultSzzt)
            }
        } catch (e: IOException) {
            JOptionPane.showMessageDialog(null, e, "Error loading world", JOptionPane.ERROR_MESSAGE)
            e.printStackTrace()
            System.exit(1)
        } catch (e: WorldCorruptedException) {
            JOptionPane.showMessageDialog(null, e, "Error loading world", JOptionPane.ERROR_MESSAGE)
            e.printStackTrace()
            System.exit(1)
        }
    }

    companion object {
        const val VERSION: String = "0.39loom"
        lateinit var args: Array<String>
        @JvmStatic
        fun main(args: Array<String>) {
            //System.setProperty("sun.java2d.opengl", "True");
            System.setProperty("sun.java2d.translaccel", "true")
            System.setProperty("sun.java2d.ddforcevram", "true")
            //System.setProperty("sun.java2d.trace", "log");
            Companion.args = args

            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
            } catch (e: Exception) {
                println("Failed to set system L&F")
            }
            //JFrame.setDefaultLookAndFeelDecorated(false);
            SwingUtilities.invokeLater(Main())
        }
    }
}
