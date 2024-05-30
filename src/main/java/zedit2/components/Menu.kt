package zedit2.components

import java.awt.event.ActionListener
import javax.swing.JMenu
import javax.swing.event.ChangeListener

class Menu(@JvmField val title: String) : Iterable<MenuEntry?> {
    private val list = ArrayList<MenuEntry>()

    fun add(entry: MenuEntry) {
        list.add(entry)
    }

    fun add(name: String?, key: String?, act: ActionListener?) {
        list.add(MenuEntry(name, key, act))
    }

    fun add(name: String?, act: ChangeListener?, init: Boolean) {
        list.add(MenuEntry(name, act, init))
    }

    fun add() {
        list.add(MenuEntry())
    }

    fun add(submenu: JMenu, key: String?) {
        list.add(MenuEntry(submenu, key))
    }

    override fun iterator(): MutableIterator<MenuEntry> {
        return list.iterator()
    }
}
