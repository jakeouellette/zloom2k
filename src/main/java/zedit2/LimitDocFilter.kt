package zedit2

import javax.swing.text.AttributeSet
import javax.swing.text.BadLocationException
import javax.swing.text.DocumentFilter

// From https://stackoverflow.com/questions/10136794/limiting-the-number-of-characters-in-a-jtextfield
class LimitDocFilter(private val maxChars: Int) : DocumentFilter() {
    @Throws(BadLocationException::class)
    override fun replace(fb: FilterBypass, offset: Int, length: Int, text: String, attrs: AttributeSet) {
        var text = text
        val currentLength = fb.document.length
        val overLimit = (currentLength + text.length) - maxChars - length
        if (overLimit > 0) {
            text = text.substring(0, text.length - overLimit)
        }
        if (!text.isEmpty()) {
            super.replace(fb, offset, length, text, attrs)
        }
    }
}
