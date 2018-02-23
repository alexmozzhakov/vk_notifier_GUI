import org.jsoup.Jsoup
import org.jsoup.nodes.Document

data class Profile(var pageName: String)

class PageDownloader(var profile: Profile) {

    fun downloadPage(): Document = Jsoup.connect("https://vk.com/${profile.pageName}").userAgent(userAgent).get()

    companion object {
        const val userAgent = "Mozilla/5.0 (Linux; Android 4.1.1; Nexus 7 Build/JRO03D) AppleWebKit/535.19 " +
                "(KHTML, like Gecko) Chrome/18.0.1025.166 Safari/535.19"

    }
}