import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.put
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
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.sql.DriverManager
import java.sql.Statement
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

private val pds = arrayListOf<PageDownloader>()
private var executor = Executors.newWorkStealingPool()
private val map = mutableMapOf<String, Boolean>()


interface OnlineChecker {
    fun isOnline(): Pair<String, Boolean>
}

class DocumentOnlineChecker(private val document: Document) : OnlineChecker {
    override fun isOnline() =
            document.body().getElementsByClass("op_header").text() to
                    (document.body().getElementsByClass("pp_last_activity").text() == "Online")

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

data class Profile(var pageName: String)

class PageDownloader(var profile: Profile) {

    fun downloadPage(): Document = Jsoup.connect("https://vk.com/${profile.pageName}").userAgent(userAgent).get()

    companion object {
        val userAgent = "Mozilla/5.0 (Linux; Android 4.1.1; Nexus 7 Build/JRO03D) AppleWebKit/535.19 " +
                "(KHTML, like Gecko) Chrome/18.0.1025.166 Safari/535.19"

    }
}


class Main : Application() {

    private val list = ListView<String>()
    private val userTextField = TextField()
    private var statement: Statement? = null


    @Throws(Exception::class)
    override fun start(primaryStage: Stage) {
        primaryStage.title = "VK notifier"

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
        btnClear.onAction = EventHandler { list.items.clear();executor.shutdownNow();pds.clear() }

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
                if (i in selectedIndices) addPageToMonitoringIfNotExists(rs.getString("id"))
                i++
            }
        }
        grid.add(button, 2, 1)
        primaryStage.scene = scene
        // Hide this current window (if this is what you want)
    }

    private fun addPageToMonitoringIfNotExists(userId: String) {
        if (!list.items.contains(userId)) {
            list.items.add(userId)
            executor.shutdownNow()
            executor = Executors.newFixedThreadPool(list.items.size)
            pds.add(PageDownloader(Profile(userId)))
            pds.map {
                Runnable {
                    var firstTime = true
                    while (true) {
                        val str = jsonObject()
                        try {
                            val document = it.downloadPage()
                            val data = DocumentOnlineChecker(document).isOnline()
                            val name = data.first
                            if (firstTime) {
                                statement!!.executeUpdate("INSERT OR IGNORE INTO users VALUES(\"${it.profile.pageName}\", \"$name\")")
                                firstTime = false
                            }
                            val userOnline = data.second
                            println("${SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(Date())}: Checking $name - $userOnline")
                            if (map[name] != userOnline) {
                                str.put(data)
                                map[name] = userOnline
                                if (userOnline && System.getProperty("os.name") == "Mac OS X") MacOnlineNotifier(name).notifyStatus()
                            }
                        } catch (e: Exception) {
                            println(e)
                        }

                        Thread.sleep(5_000L)
                    }
                }
            }.forEach { executor.execute(it) }
        }
    }

    override fun stop() {
        executor.shutdownNow()
    }
}

fun main(args: Array<String>) {
    Application.launch(Main::class.java, *args)
}
