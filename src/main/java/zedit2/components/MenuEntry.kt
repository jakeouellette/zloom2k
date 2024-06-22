package zedit2.components

import java.awt.event.ActionListener
import javax.swing.JCheckBoxMenuItem
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.KeyStroke
import javax.swing.event.ChangeListener

class MenuEntry {
    private var title: String? = null
    private var key: String? = null
    private var act: ActionListener? = null
    private var cAct: ChangeListener? = null
    private val type: Int
    private var init = false
    private var submenu: JMenu? = null

    constructor(title: String?, key: String?, act: ActionListener?) {
        type = TYPE_MENUITEM
        this.title = title
        this.key = key
        this.act = act
    }

    constructor(title: String?, act: ChangeListener?, init: Boolean) {
        type = TYPE_CHECKBOX
        this.title = title
        this.cAct = act
        this.init = init
    }

    constructor() {
        type = TYPE_SEPARATOR
    }

    constructor(submenu: JMenu, key: String?) {
        type = TYPE_SUBMENU
        this.title = submenu.text
        this.submenu = submenu
        this.key = key
    }

    private fun setTitle(item: JMenuItem?) {
        var text = title

        // TODO(jakeouellette): Added a check for "" to avoid NPE
        if (key != null && key != "") {
            val keyStr = GlobalEditor.getString("K_$key")

            val keyStroke = KeyStroke.getKeyStroke(keyStr)
            val keyString = Util.keyStrokeString(keyStroke)
            if (keyString != null) {
                text = String.format("%s (%s)", title, keyString)
            }
        }
        item!!.text = text
    }

    fun addToJMenu(menu: JMenu) {
        when (type) {
            TYPE_MENUITEM -> {
                val item = JMenuItem()
                setTitle(item)
                item.addActionListener(act)
                menu.add(item)
            }

            TYPE_CHECKBOX -> {
                val menuItem = JCheckBoxMenuItem()
                setTitle(menuItem)
                menuItem.isSelected = init
                menuItem.addChangeListener(cAct)
                menu.add(menuItem)
            }

            TYPE_SEPARATOR -> {
                menu.addSeparator()
            }

            TYPE_SUBMENU -> {
                setTitle(submenu)
                menu.add(submenu)
            }

            else -> throw RuntimeException("Invalid item value")
        }
    }

    companion object {
        private const val TYPE_MENUITEM = 1
        private const val TYPE_SEPARATOR = 2
        private const val TYPE_CHECKBOX = 3
        private const val TYPE_SUBMENU = 4
    }
}
