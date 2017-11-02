import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.put
import javafx.application.Application
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.control.TextField
import javafx.scene.input.KeyCode
import javafx.scene.layout.GridPane
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import javafx.scene.text.Text
import javafx.stage.Stage
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
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

class PageDownloader(private val profile: Profile) {

    fun downloadPage(): Document = Jsoup.connect("https://vk.com/${profile.pageName}").userAgent(userAgent).get()

    companion object {
        val userAgent = "Mozilla/5.0 (Linux; Android 4.1.1; Nexus 7 Build/JRO03D) AppleWebKit/535.19 " +
                "(KHTML, like Gecko) Chrome/18.0.1025.166 Safari/535.19"

    }
}


class Main : Application() {

    private val list = ListView<String>()
    private val userTextField = TextField()


    @Throws(Exception::class)
    override fun start(primaryStage: Stage) {
        primaryStage.title = "VK notifier"

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

        userTextField.onKeyPressed = EventHandler { if (it.code == KeyCode.ENTER) addPageToMonitoringIfNotExists() }
        grid.add(userTextField, 0, 2)


        val btn = Button("Add")
        grid.add(btn, 2, 2)
        btn.onAction = EventHandler { addPageToMonitoringIfNotExists() }

        grid.add(list, 0, 3)

        val testBtn = Button("Clear")
        grid.add(testBtn, 2, 3)
        testBtn.onAction = EventHandler { list.items.clear();executor.shutdownNow() }

        val scene = Scene(grid, 380.0, 500.0)
        primaryStage.scene = scene

        primaryStage.show()
    }

    private fun addPageToMonitoringIfNotExists() {
        if (!list.items.contains(userTextField.text)) {
            list.items.add(userTextField.text)
            executor.shutdownNow()
            executor = Executors.newFixedThreadPool(list.items.size)
            pds.add(PageDownloader(Profile(userTextField.text)))
            pds.map {
                Runnable {
                    while (true) {
                        val str = jsonObject()
                        try {
                            val document = it.downloadPage()
                            val data = DocumentOnlineChecker(document).isOnline()
                            val name = data.first
                            println("${SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(Date())}: Checking $name")
                            val userOnline = data.second
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
