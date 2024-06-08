package zedit2.event

import zedit2.model.Board
import zedit2.model.WorldData

interface OnBoardUpdatedCallback {
    fun onBoardUpdated(worldData : WorldData, boardList : List<Board>, currentBoard : Int)
}