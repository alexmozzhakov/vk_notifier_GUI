import de.codecentric.centerdevice.MenuToolkit
import javafx.scene.control.Menu
import javafx.scene.control.MenuBar
import javafx.scene.control.MenuItem
import javafx.scene.control.SeparatorMenuItem

interface MenuCreator {
    fun createMenu()
}

class NullMenuCreator : MenuCreator {
    override fun createMenu() = Unit
}

class MacMenuCreator(private var appName: String) : MenuCreator {
    override fun createMenu() {
        val tk = MenuToolkit.toolkit()

        val bar = MenuBar()

        // Application Menu
        // TBD: services menu
        val appMenu = Menu(appName) // Name for appMenu can't be set at
        // Runtime
        val aboutItem = tk.createAboutMenuItem(appName)
        val preferencesItem = MenuItem("Preferences...")
        appMenu.items.addAll(aboutItem, SeparatorMenuItem(), preferencesItem, SeparatorMenuItem(),
                tk.createHideMenuItem(appName), tk.createHideOthersMenuItem(), tk.createUnhideAllMenuItem(),
                SeparatorMenuItem(), tk.createQuitMenuItem(appName))

        // File Menu (items TBD)
        val fileMenu = Menu("File")
        val newItem = MenuItem("New...")
        fileMenu.items.addAll(newItem, SeparatorMenuItem(), tk.createCloseWindowMenuItem())

        val windowMenu = Menu("Window")
        windowMenu.items.addAll(tk.createMinimizeMenuItem(),
                SeparatorMenuItem(), tk.createBringAllToFrontItem())


        bar.menus.addAll(appMenu, fileMenu, windowMenu)

        tk.autoAddWindowMenuItems(windowMenu)
        tk.setGlobalMenuBar(bar)
    }
}