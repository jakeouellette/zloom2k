package zedit2.model

class Undo(var insert: Boolean, var pos: Int, var text: String, var caret: Int)
