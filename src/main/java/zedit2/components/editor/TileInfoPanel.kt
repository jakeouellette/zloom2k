package zedit2.components.editor

import net.miginfocom.swing.MigLayout
import zedit2.components.DosCanvas
import zedit2.model.BlinkingImageIcon
import zedit2.model.Board
import zedit2.model.Tile
import zedit2.model.WorldData
import zedit2.util.Constants
import zedit2.util.Constants.EDITOR_FONT
import zedit2.util.ZType
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.border.TitledBorder
import kotlin.math.max
import kotlin.math.min

class TileInfoPanel(
    val dosCanvas: DosCanvas,
    worldData: WorldData,
    title: String,
    cursorTile: Tile,
    currentBoard: Board,
    onBlinkingImageIconAdded: (BlinkingImageIcon) -> Unit
) : JPanel(MigLayout()) {
    init {
        val tileLabel = createLabel(worldData, cursorTile, onBlinkingImageIconAdded)

        //this.setText("<html>Test</html>");
        //this.add(label);
        this.border =
            TitledBorder(null, title, TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION, EDITOR_FONT, null)

        this.add(tileLabel, "north")
        if (cursorTile.stats.isNotEmpty()) {
            val tileInfoPanel = JPanel(MigLayout("", "[][]", ""))
//            var firstStat = true

            for (stat in cursorTile.stats) {
                val statId = stat.statId

                class JFontLabel(s : String) : JLabel(s) {
                    init {
                        this.font = EDITOR_FONT
                    }
                }
                fun JPanel.addTwo(string: String, string2 : String, constraint : String? = "") {
                    add(JFontLabel(string))
                    add(JFontLabel(string2), constraint)
                }
                val statIdText = if (statId != -1) "${statId}" else ""
                with(tileInfoPanel) {
                    addTwo("StatId:", statIdText)
                    addTwo("Cycle:", "${stat.cycle}", "wrap")
                    addTwo("X-Step:", "${stat.stepX}")
                    addTwo("Y-Step:", "${stat.stepY}", "wrap")
                    addTwo("Param 1:", "${stat.p1}")
                    addTwo("Follower:", "${stat.follower}", "wrap")
                    addTwo("Param 2:", "${stat.p2}")
                    addTwo("Leader:", "${stat.leader}", "wrap")
                    addTwo("Param 3:", "${stat.p3}")
                    addTwo("Instr. Ptr:", "${stat.ip}", "wrap")

                    // Code
                    val codeLen = stat.codeLength
                    if (codeLen != 0) {
                        if (codeLen >= 0) {
                            addTwo("Code length:", "${codeLen}")
                        } else {
                            val boundTo = -codeLen
                            var appendMessage = ""
                            if (boundTo < currentBoard.statCount) {
                                val boundToStat = currentBoard.getStat(boundTo)
                                appendMessage = " @ " + boundToStat!!.x + "," + boundToStat.y
                            }
                            addTwo("Bound to:", "#$boundTo$appendMessage")
                        }
                    }
                    // TODO(jakeouellette): I busted this.
                    val bgcol = dosCanvas.htmlColour(stat.uco / 16)
                    val fgcol = dosCanvas.htmlColour(stat.uco % 16)
                    add(JFontLabel("Under:"), "wrap")
                    add(JFontLabel("<html>" +
                            "<span bgcolor=\"${bgcol}\" color=\"${fgcol}\">" +
                            "&nbsp;&nbsp;■&nbsp;&nbsp;" +
                            "</span>" +
                            "</html>")
                    )
add(JFontLabel(ZType.getName(worldData.isSuperZZT,  stat.uid)))
//
                // TODO(jakeouellette) add edit buttons here
                }
                tileInfoPanel.font = EDITOR_FONT
                this.add(tileInfoPanel)


                }

//                if (!firstStat) {
//                }
//                firstStat = false

        }
    }

    private fun createLabel(
        worldData: WorldData,
        cursorTile: Tile,
        onBlinkingImageIconAdded: (BlinkingImageIcon) -> Unit
    ): JLabel {
        val szzt = worldData.isSuperZZT
        val chr = ZType.getChar(szzt, cursorTile)
        val col = ZType.getColour(szzt, cursorTile)
        val name = ZType.getName(szzt, cursorTile.id)
        val chBg = ((cursorTile.col) or (32 shl 8)).toChar()
        val chFg = ((cursorTile.col) or (254 shl 8)).toChar()
        val pattern = "@ $chBg$chFg$chBg"
        // TODO(jakeouellette): I disabled these when trying to remove globals
        val imgBlinkOff = dosCanvas.extractCharImage(chr, col, 2, 2, false, pattern)
        val imgBlinkOn = dosCanvas.extractCharImage(chr, col, 2, 2, true, pattern)
        val tileLabelIcon = BlinkingImageIcon(imgBlinkOff, imgBlinkOn)

        val tileLabel = object : JLabel() {
            init {
                //        tileLabel.font = Font(Font.SANS_SERIF, Font.BOLD, 14)
                this.font = EDITOR_FONT
                this.icon = tileLabelIcon
                this.text = name
            }

            override fun getMinimumSize(): Dimension {

                    val superSize = super.getMinimumSize()
                    return Dimension(max(superSize.width, 250), superSize.height)

            }
        }



        return tileLabel
    }
}

