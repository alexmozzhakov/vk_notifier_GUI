import javafx.application.Application
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.KeyCode
import javafx.scene.layout.GridPane
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import javafx.scene.text.Text
import javafx.stage.Stage
import kotlinx.coroutines.experimental.*
import java.sql.DriverManager
import java.sql.Statement
import java.util.*

/**
 * Created by alex.
 */

fun main(args: Array<String>) {
    Application.launch(Main::class.java, *args)
}

fun createJob(checker: Checker, delayTime: Long): Job {
    return launch {
        while (isActive) {
            checker.check()
            delay(delayTime)
        }
    }
}


interface OnlineNotifier {
    fun notifyStatus()
}

class MacOnlineNotifier(private var name: String) : OnlineNotifier {
    override fun notifyStatus() {
        Runtime.getRuntime().exec(
                arrayOf("osascript", "-e",
                        "display notification \"User $name went online\" with title \"VK notifier\" sound name \"chime\""
                ))
    }
}

private var jobs = ArrayList<Job>()


class Main : Application() {

    private val list = ListView<String>()
    private val userTextField = TextField()
    private var statement: Statement? = null
    private val appName = "VK notifier"
    private val platformOs = System.getProperty("os.name")

    @Throws(Exception::class)
    override fun start(primaryStage: Stage) {
        primaryStage.title = appName

        when (platformOs) {
            "Mac OS X" -> MacMenuCreator(appName)
            else -> NullMenuCreator()

        }.apply { createMenu() }

        val connection = DriverManager.getConnection("jdbc:sqlite:./connections.db")
        statement = connection.createStatement()
        statement!!.queryTimeout = 30  // set timeout to 30 sec.
        statement!!.executeUpdate("create table if not exists users (id string, name string, UNIQUE(id, name))")

        val grid = GridPane()
        grid.alignment = Pos.CENTER
        grid.hgap = 10.0
        grid.vgap = 10.0
        grid.padding = Insets(25.0, 25.0, 25.0, 25.0)

        val sceneTitle = Text("Enter ids to monitor:")
        sceneTitle.font = Font.font("Tahoma", FontWeight.NORMAL, 20.0)
        grid.add(sceneTitle, 0, 0, 2, 1)

        val userName = Label("Page address:")
        grid.add(userName, 0, 1)

        val iconHistory = Image(javaClass.getResourceAsStream("hist.png"))
        val btnHistory = Button()
        btnHistory.graphic = ImageView(iconHistory)
        grid.add(btnHistory, 2, 0)

        userTextField.onKeyPressed = EventHandler { if (it.code == KeyCode.ENTER) addPageToMonitoringIfNotExists(userTextField.text.trim()) }
        grid.add(userTextField, 0, 2)


        val btnAdd = Button("Add")
        grid.add(btnAdd, 2, 2)
        btnAdd.onAction = EventHandler { addPageToMonitoringIfNotExists(userTextField.text.trim()) }

        grid.add(list, 0, 3)

        val btnClear = Button("Clear")
        grid.add(btnClear, 2, 3)
        btnClear.onAction = EventHandler {
            list.items.clear()
            runBlocking {
                println("Clear")
                jobs.forEach { it.cancelAndJoin() }
            }
        }

        val scene = Scene(grid, 380.0, 500.0)
        primaryStage.scene = scene

        btnHistory.onAction = EventHandler { selectFromHistory(primaryStage, scene) }
        primaryStage.show()
    }

    private fun selectFromHistory(primaryStage: Stage, prevScene: Scene) {
        val grid = GridPane()
        grid.alignment = Pos.CENTER
        grid.hgap = 10.0
        grid.vgap = 10.0
        grid.padding = Insets(25.0, 25.0, 25.0, 25.0)

        val scene = Scene(grid, 380.0, 500.0)

        val sceneTitle = Text("Select ids to monitor:")
        sceneTitle.font = Font.font("Tahoma", FontWeight.NORMAL, 20.0)
        grid.add(sceneTitle, 0, 0, 2, 1)

        val listView = ListView<String>()
        val historyList: ObservableList<String> = FXCollections.observableArrayList()

        listView.items = historyList

        val rs = statement!!.executeQuery("select * from users")
        while (rs.next()) historyList.add("${rs.getString("name")} (${rs.getString("id")})")

        listView.selectionModel.selectionMode = SelectionMode.MULTIPLE

        grid.add(listView, 0, 1)
        val button = Button("Add")
        button.onAction = EventHandler {
            val selectedIndices = listView.selectionModel.selectedIndices
            primaryStage.scene = prevScene
            val resultSet = statement!!.executeQuery("SELECT * FROM users")
            var i = 0
            while (resultSet.next()) {
                if (i in selectedIndices) addPageToMonitoringIfNotExists(rs.getString("id").trim())
                i++
            }
        }
        grid.add(button, 2, 1)
        primaryStage.scene = scene
        // Hide this current window (if this is what you want)
    }

    private fun addPageToMonitoringIfNotExists(userId: String) {
        if (!list.items.contains(userId.trim())) {
            println("Added $userId to monitoring")
            list.items.add(userId)
            val pageDownloader = PageDownloader(Profile(userId))
            val job = createJob(VkOnlineChecker(pageDownloader), 5000L)
            jobs.add(job)
        }
    }
}