import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.put
import java.text.SimpleDateFormat
import java.util.*

private val map = mutableMapOf<String, Boolean>()
private val platformOs = System.getProperty("os.name")

abstract class Checker {
    abstract fun check()
}

class VkOnlineChecker(private val pageDownloader: PageDownloader) : Checker() {
    override fun check() {
        val str = jsonObject()
        try {
            val document = pageDownloader.downloadPage()
            val data = DocumentOnlineChecker(document).isOnline()
            val name = data.first
            if (name.isNotEmpty()) {
                val userOnline = data.second
                println("${SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(Date())}: Checking $name - $userOnline")
                if (map[name] != userOnline) {
                    str.put(data)
                    map[name] = userOnline
                    if (userOnline && platformOs == "Mac OS X") MacOnlineNotifier(name).notifyStatus()
                }
            } else {
                println("${SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(Date())}: Can't get a name for \"${pageDownloader.profile.pageName}\"")
            }
        } catch (e: Exception) {
            println(e)
        }
    }
}
