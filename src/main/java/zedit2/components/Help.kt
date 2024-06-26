package zedit2.components

import zedit2.util.Logger
import zedit2.util.Logger.TAG
import java.awt.Component
import java.awt.Desktop
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.IOException
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL
import javax.swing.JDialog
import javax.swing.JEditorPane
import javax.swing.JScrollPane
import javax.swing.event.HyperlinkEvent

class Help(val imageRetriever: ImageRetriever, val relativeFrame: Component) {
    private var dialog: JDialog? = null
    private var currentFile: String? = null

    init {
        if (helpInstance != null) {
            helpInstance!!.dialog!!.toFront()
        } else {
            openHelp()
        }
    }

    private fun openHelp() {
        dialog = JDialog()
        dialog!!.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
        dialog!!.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent) {
                helpInstance = null
            }
        })

        val editorPane = JEditorPane()
        val scrollPane = JScrollPane(editorPane)
        editorPane.isEditable = false
        var url: URL? = null
        try {
            url = Main::class.java.classLoader.getResource("help/index.html")
            currentFile = url.file
            editorPane.page = url
            val w = GlobalEditor.getInt("HELPBROWSER_WIDTH")
            val h = GlobalEditor.getInt("HELPBROWSER_HEIGHT")
            editorPane.preferredSize = Dimension(w, h)
            dialog!!.title = "ZEdit2 help"
            dialog!!.setIconImage(imageRetriever.extractCharImage('?'.code, 0x9F, 1, 1, false, "$"))
            editorPane.addHyperlinkListener { e ->
                if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    try {
                        if (e.url.toString().startsWith("http")) {
                            Desktop.getDesktop().browse(e.url.toURI())
                        } else {
                            val ref = e.url.ref
                            Logger.i(TAG) { "ref: $ref" }
                            if (e.url.file != currentFile) {
                                Logger.i(TAG) {"Loading page"}
                                editorPane.page = e.url
                            }
                            editorPane.scrollToReference(ref)
                        }
                    } catch (ignored: IOException) {
                    } catch (ignored: URISyntaxException) {
                    }
                }
            }
            dialog!!.add(scrollPane)
            dialog!!.pack()
            dialog!!.setLocationRelativeTo(relativeFrame)
            dialog!!.isVisible = true
            helpInstance = this
        } catch (e: MalformedURLException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        //var res = Main.class.getResource("help/test.html");
    }

    companion object {
        private var helpInstance: Help? = null
    }
}
