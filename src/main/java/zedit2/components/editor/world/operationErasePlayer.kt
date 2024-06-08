package zedit2.components.editor.world

import zedit2.components.WorldEditor


internal fun WorldEditor.operationErasePlayer() {
    val x = boardXOffset + currentBoard!!.getStat(0)!!.x - 1
    val y = boardYOffset + currentBoard!!.getStat(0)!!.y - 1

    erasePlayer(currentBoard)
    addRedraw(x, y, x, y)

    afterModification()
}