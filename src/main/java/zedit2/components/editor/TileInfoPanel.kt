package zedit2.components.editor

import zedit2.components.DosCanvas
import zedit2.model.BlinkingImageIcon
import zedit2.model.Board
import zedit2.model.Tile
import zedit2.model.WorldData
import zedit2.util.Constants
import zedit2.util.Constants.EDITOR_FONT
import zedit2.util.ZType
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.border.TitledBorder

class TileInfoPanel(
    val dosCanvas: DosCanvas,
    worldData: WorldData,
    title: String,
    cursorTile: Tile,
    currentBoard: Board,
    onBlinkingImageIconAdded: (BlinkingImageIcon) -> Unit
) : JPanel(BorderLayout()) {
    init {
        val tileLabel = createLabel(worldData, cursorTile, onBlinkingImageIconAdded)

        //this.setText("<html>Test</html>");
        //this.add(label);
        this.border =
            TitledBorder(null, title, TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION, EDITOR_FONT, null)

        this.add(tileLabel, BorderLayout.NORTH)
        if (cursorTile.stats.isNotEmpty()) {
            val tileInfoBox = JLabel()
            val tileInfo = StringBuilder()
            tileInfo.append("<html>")

            var firstStat = true

            for (stat in cursorTile.stats) {
                if (!firstStat) {
                    tileInfo.append("<hr></hr>")
                }
                firstStat = false

                tileInfo.append("<table>")
                tileInfo.append("<tr>")
                val statId = stat.statId

                tileInfo.append(
                    String.format(
                        "<th align=\"left\">%s</th><td>%s</td>",
                        "Stat ID:",
                        if (statId != -1) statId else ""
                    )
                )
                tileInfo.append(String.format("<th align=\"left\">%s</th><td>%d</td>", "Cycle:", stat.cycle))
                tileInfo.append("</tr><tr>")
                tileInfo.append(String.format("<th align=\"left\">%s</th><td>%d</td>", "X-Step:", stat.stepX))
                tileInfo.append(String.format("<th align=\"left\">%s</th><td>%d</td>", "Y-Step:", stat.stepY))
                tileInfo.append("</tr><tr>")
                tileInfo.append(String.format("<th align=\"left\">%s</th><td>%d</td>", "Param 1:", stat.p1))
                tileInfo.append(
                    String.format(
                        "<th align=\"left\">%s</th><td>%d</td>",
                        "Follower:",
                        stat.follower
                    )
                )
                tileInfo.append("</tr><tr>")
                tileInfo.append(String.format("<th align=\"left\">%s</th><td>%d</td>", "Param 2:", stat.p2))
                tileInfo.append(String.format("<th align=\"left\">%s</th><td>%d</td>", "Leader:", stat.leader))
                tileInfo.append("</tr><tr>")
                tileInfo.append(String.format("<th align=\"left\">%s</th><td>%d</td>", "Param 3:", stat.p3))
                tileInfo.append(String.format("<th align=\"left\">%s</th><td>%d</td>", "Instr. Ptr:", stat.ip))
                tileInfo.append("</tr><tr>")
                // Code
                val codeLen = stat.codeLength
                if (codeLen != 0) {
                    if (codeLen >= 0) {
                        tileInfo.append(
                            String.format(
                                "<th colspan=\"2\" align=\"left\">%s</th><td colspan=\"2\">%d</td>",
                                "Code length:",
                                codeLen
                            )
                        )
                    } else {
                        val boundTo = -codeLen
                        var appendMessage = ""
                        if (boundTo < currentBoard.statCount) {
                            val boundToStat = currentBoard.getStat(boundTo)
                            appendMessage = " @ " + boundToStat!!.x + "," + boundToStat.y
                        }
                        tileInfo.append(
                            String.format(
                                "<th align=\"left\">%s</th><td colspan=\"3\">%s</td>",
                                "Bound to:",
                                "#$boundTo$appendMessage"
                            )
                        )
                    }
                    tileInfo.append("</tr><tr>")
                }
                tileInfo.append("<th align=\"left\">Under:</th>")
                // TODO(jakeouellette): I busted this.
                val bgcol = dosCanvas.htmlColour(stat.uco / 16)
                val fgcol = dosCanvas.htmlColour(stat.uco % 16)
                tileInfo.append(
                    String.format(
                        "<td align=\"left\"><span bgcolor=\"%s\" color=\"%s\">&nbsp;&nbsp;■&nbsp;&nbsp;</span></td>",
                        bgcol,
                        fgcol
                    )
                )

                tileInfo.append(
                    String.format(
                        "<td align=\"left\" colspan=\"2\">%s</td>",
                        ZType.getName(worldData.isSuperZZT, stat.uid)
                    )
                )
                tileInfo.append("</tr>")
                tileInfo.append("</table>")
            }

            tileInfo.append("</html>")
            tileInfoBox.horizontalAlignment = SwingConstants.LEFT
            tileInfoBox.text = tileInfo.toString()
            tileInfoBox.font = EDITOR_FONT
            // TODO(jakeouellette) add edit buttons here
            this.add(tileInfoBox, BorderLayout.CENTER)
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
        }



        return tileLabel
    }
}

