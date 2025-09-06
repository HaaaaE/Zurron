package compose.project.zurron

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class NoteType {
    Normal, Video
}

@Serializable
data class NoteData(
    val url: String,
    val type: NoteType,
    val title: String,
    val desc: String,
    val video: String? = null,
    val images: List<String> = listOf()
)

@Serializable
data class InitialState(
    val note: NoteState
)

@Serializable
data class NoteState(
    val currentNoteId: String,
    val noteDetailMap: Map<String, NoteDetail>
)

@Serializable
data class NoteDetail(
    val note: NoteInfo
)

@Serializable
data class NoteInfo(
    val type: String,
    val title: String,
    val desc: String,
    val user: UserInfo,
    val interactInfo: InteractInfo
)

@Serializable
data class UserInfo(
    val userId: String,
    val nickname: String,
    val avatar: String
)

@Serializable
data class InteractInfo(
    val likedCount: String,
    val commentCount: String,
    val collectedCount: String
)

/**
 * 纯 KMP：不显式指定 Engine，由各平台 sourceSet 提供。
 * - androidMain: 添加 ktor-client-okhttp
 * - iosMain: 添加 ktor-client-darwin
 * - jsMain: 添加 ktor-client-js
 */
class XhsFetcher(
    private val client: HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                }
            )
        }
        // Ktor: 用 DefaultRequest 插件设置通用 Header
        install(DefaultRequest) {
            header(
                "User-Agent",
                "Mozilla/5.0 (KMP) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123 Safari/537.36"
            )
            header(
                HttpHeaders.Accept,
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
            )
        }
        expectSuccess = true
    }
) {
    private val jsonFormatter = Json { prettyPrint = true }

    @Suppress("unused")
    private fun printJsonString(jsonString: String) {
        println(jsonFormatter.encodeToString(Json.parseToJsonElement(jsonString)))
    }

    suspend fun extract(link: String): NoteData {
        val html: String = client.get(link).body()
        val regex = """window\.__INITIAL_STATE__=(.*?)</script>""".toRegex()

        // 2. Find the first match in the HTML string
        val matchResult = regex.find(html)

        var result: NoteData? = null

        // 3. Extract the captured JSON string
        if (matchResult != null && matchResult.groupValues.size > 1) {
            val jsonString = matchResult.groupValues[1]

            // --- Next Step: Parse the JSON String ---
            // Now you can parse this string into actual Kotlin objects
            // using a library like kotlinx.serialization

            val json = Json { ignoreUnknownKeys = true } // Important: To avoid errors from fields you don't define

            try {
                val initialState = json.decodeFromString<InitialState>(jsonString)
                val noteId = initialState.note.currentNoteId
                val noteDetails = initialState.note.noteDetailMap[noteId] ?: throw Exception("Note details not found.")

                result = NoteData(
                    url = link,
                    type = if (noteDetails.note.type == "video") NoteType.Video else NoteType.Normal,
                    title = noteDetails.note.title,
                    desc = noteDetails.note.desc,
                )
            } catch (e: Exception) {
                println("\nFailed to parse JSON: ${e.message}")
            }
        } else {
            println("Could not find the __INITIAL_STATE__ JSON in the HTML.")
        }

        val metas = parseMeta(html)

        if (result == null) {
            // 优化：提取标题并移除网站后缀，更干净
            val title = (metas["og:title"] ?: metas["twitter:title"])
                ?.removeSuffix(" - 小红书")
                ?.trim()

            val desc = metas["description"] ?: metas["og:description"] ?: metas["twitter:description"]
            val type = metas["og:type"]
            result = NoteData(
                url = link,
                type = if (type == "video") NoteType.Video else NoteType.Normal,
                title = title ?: "",
                desc = desc ?: "",
            )
        }
        if (result.type == NoteType.Video) {
            result = result.copy(video = metas["og:video"] ?: metas["twitter:video"])
        }
        // 修正：使用专用方法提取所有图片，避免被 Map 覆盖
        val images = extractAllImages(html)
        return result.copy(images = images)
    }

    /**
     * 修正：专门用于提取所有 og:image 和 twitter:image 的内容。
     * 这样可以避免 parseMeta 中 Map key 覆盖的问题。
     */
    private fun extractAllImages(html: String): List<String> {
        val images = mutableListOf<String>()
        val imageMetaRegex = Regex(
            """<meta\s+[^>]*?(?:property|name)\s*=\s*["'](og:image|twitter:image)["'][^>]*?\s+content\s*=\s*["']([^"']*)["']""",
            RegexOption.IGNORE_CASE
        )
        for (match in imageMetaRegex.findAll(html)) {
            match.groupValues.getOrNull(2)?.let { images.add(it) }
        }
        return images.distinct()
    }

    // 解析 <meta ...>，支持 property / name
    private fun parseMeta(html: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val metaRegex = Regex("""<meta\s+[^>]*>""", RegexOption.IGNORE_CASE)
        val nameRe = Regex("""\bname\s*=\s*["']?([^"'>\s]+)""", RegexOption.IGNORE_CASE)
        val propRe = Regex("""\bproperty\s*=\s*["']?([^"'>\s]+)""", RegexOption.IGNORE_CASE)
        val contentRe = Regex("""\bcontent\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)

        for (tag in metaRegex.findAll(html)) {
            val t = tag.value
            val key = propRe.find(t)?.groupValues?.getOrNull(1)
                ?: nameRe.find(t)?.groupValues?.getOrNull(1)
                ?: continue
            val content = contentRe.find(t)?.groupValues?.getOrNull(1) ?: continue
            map[key.lowercase()] = content
        }
        return map
    }
}

fun main() = runBlocking {
    val fetcher = XhsFetcher()
    val data =
        fetcher.extract("https://www.xiaohongshu.com/explore/68abee19000000001b033166?note_flow_source=wechat&xsec_token=CBSBHSW5rUoZbeBpvmvoZy1eVYnGJOEtaUeiEgOc5Ezk8=")
    // 这里拿到的是公开元数据与简易正文
    val ddtt =
        fetcher.extract("https://www.xiaohongshu.com/explore/627e29bf000000000102ff57?note_flow_source=wechat&xsec_token=CBuK-T8DUTVg8qBlgro0uc5vpgwEX7yVdGpvgNuIBeG-o=")
    ddtt.pp()
    data.pp()
}