import org.jsoup.nodes.Document

interface OnlineChecker {
    fun isOnline(): Pair<String, Boolean>
}

class DocumentOnlineChecker(private val document: Document) : OnlineChecker {
    override fun isOnline() =
            document.body().getElementsByClass("op_header").text() to
                    (document.body().getElementsByClass("pp_last_activity").text() == "Online")

}