package zedit2.components.editor.code

import zedit2.components.WorldEditor
import zedit2.model.Board
import zedit2.model.Stat
import zedit2.model.Tile
import zedit2.util.Logger
import zedit2.util.Logger.TAG
import zedit2.util.ZType
import java.awt.Component
import java.awt.Image
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.image.BufferedImage
import javax.swing.JOptionPane

object CodeEditorFactory {
    fun create(
        tileX : Int,
        tileY : Int,
        editExempt: Boolean,
        relativeFrame: Component,
        worldEditor: WorldEditor,
        icon: Image,
        board: Board,
        stat: Stat?,
        listener: ActionListener = ActionListener { e ->
            if (e!!.actionCommand == "update") {
                Logger.i(TAG) { "$e, $stat" }
                Logger.i(TAG) { "Output:\n" +
                        "$tileX\n" +
                        "$tileY\n" +
                        "$editExempt\n" +
                        "$relativeFrame\n" +
                "${board.getName()}"}
                val source = e.source as CodeEditor
                stat!!.code = source.code
            }
        }


    ) {
        var followStat = stat
        val followedStats = HashSet<Int>()
        var success = false
        var depth = 0
        while (true) {
            if (followedStats.contains(followStat!!.statId)) break
            followedStats.add(followStat.statId)
            val codeLen = followStat.codeLength
            if (codeLen >= 0) {
                success = true
                break
            } else {
                val boundTo = -codeLen
                if (boundTo < board.statCount) {
                    followStat = board.getStat(boundTo)
                    depth++
                } else {
                    break
                }
            }
        }
        if (success) {
            val txt = if (stat!!.statId == -1) {
                "tat"
            } else {
                String.format("tat #%d", stat.statId)
            }
            if (depth > 0) {
                CodeEditor(
                    icon,
                    followStat!!,
                    worldEditor,
                    listener,
                    true,
                    String.format("S%s, bound to stat #%d (read only)", txt, followStat.statId)
                )
            } else {
                var readOnly = false
                val caption: String
                if (tileX == -1 && tileY == -1 && !editExempt) {
                    // Buffer stats get a readonly code editor
                    readOnly = true
                    caption = String.format(
                        "Viewing code of s%s (This stat exists only in the buffer. Editing disabled.)",
                        txt
                    )
                } else {
                    caption = String.format("Editing code of s%s", txt)
                }
                CodeEditor(icon, followStat!!, worldEditor, listener, readOnly, caption)
            }
        } else {
            JOptionPane.showMessageDialog(
                relativeFrame,
                "Unable to reach this object's code.",
                "Unable to reach code",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }
}