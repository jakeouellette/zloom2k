package zedit2.components.editor.world

import zedit2.components.WorldEditor


internal fun WorldEditor.operationErasePlayer() {
    val xy = boardPosOffset + currentBoard!!.getStat(0)!!.pos - 1

    erasePlayer(currentBoard)
    addRedraw(xy,xy)

    afterModification()
}