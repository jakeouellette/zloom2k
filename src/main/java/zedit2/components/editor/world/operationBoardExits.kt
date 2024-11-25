package zedit2.components.editor.world

import zedit2.components.Settings.Companion.boardExits
import zedit2.components.WorldEditor

internal fun WorldEditor.operationBoardExits() {
    boardExits(frame, currentBoard, boards, currentBoardIdx)
}