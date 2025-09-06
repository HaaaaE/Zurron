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
    val images: List<Image> = listOf()
)

@Serializable
data class Image(val image: String, val livePhoto: String? = null)

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

@Serializable
data class ImageInfo(
    val imageScene: String? = null,
    val url: String? = null
)

@Serializable
data class NoteInfo(
    val type: String,
    val title: String,
    val desc: String,
    val user: UserInfo,
    val interactInfo: InteractInfo,
    // 新增：图片和视频的解析承载
    val imageList: List<ImageItem>? = null,
    val video: Video? = null
)

@Serializable
data class Video(val media: Media? = null)

@Serializable
data class Media(val stream: Stream? = null)


@Serializable
data class ImageItem(
    val urlDefault: String? = null,
    val urlPre: String? = null,
    val livePhoto: Boolean? = null,
    val infoList: List<ImageInfo>? = null,
    val stream: Stream? = null
)

@Serializable
data class Stream(
    val h264: List<H264>? = null
)

@Serializable
data class H264(
    val masterUrl: String? = null,
    val backupUrls: List<String>? = null
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

            // Parse the JSON String
            val json = Json { ignoreUnknownKeys = true } // Important: To avoid errors from fields you don't define

            try {
                val initialState = json.decodeFromString<InitialState>(jsonString)
                val noteId = initialState.note.currentNoteId
                val noteDetails = initialState.note.noteDetailMap[noteId]
                    ?: throw Exception("Note details not found.")

                // 新增：映射图片与 livephoto
                val images: List<Image> =
                    noteDetails.note.imageList?.map { im ->
                        // 图片 URL 选取优先级：urlDefault -> infoList(WB_DFT) -> infoList(WB_PRV) -> urlPre
                        val imgUrl = im.urlDefault
                            ?: im.infoList?.firstOrNull { it.imageScene == "WB_DFT" }?.url
                            ?: im.infoList?.firstOrNull { it.imageScene == "WB_PRV" }?.url
                            ?: im.urlPre
                            ?: ""

                        // 若为 LivePhoto，则取第一路 h264 的 masterUrl
                        val liveUrl =
                            if (im.livePhoto == true)
                                im.stream?.h264?.firstOrNull()?.masterUrl
                            else null

                        Image(image = imgUrl, livePhoto = liveUrl)
                    } ?: emptyList()

                // 新增：若为视频笔记，抽取视频主地址
                val videoUrl: String? =
                    if (noteDetails.note.type == "video")
                        noteDetails.note.video?.media?.stream?.h264?.firstOrNull()?.masterUrl
                    else null

                result = NoteData(
                    url = link,
                    type = if (noteDetails.note.type == "video") NoteType.Video else NoteType.Normal,
                    title = noteDetails.note.title,
                    desc = noteDetails.note.desc,
                    video = videoUrl,
                    images = images
                )
            } catch (e: Exception) {
                println("\nFailed to parse JSON: ${e.message}")
            }
        } else {
            println("Could not find the __INITIAL_STATE__ JSON in the HTML.")
        }

        return result ?: NoteData(url = link, type = NoteType.Normal, title = "", desc = "")
    }


}

fun main() = runBlocking {
    val fetcher = XhsFetcher()
//    val data =
//        fetcher.extract("https://www.xiaohongshu.com/explore/68abee19000000001b033166?note_flow_source=wechat&xsec_token=CBSBHSW5rUoZbeBpvmvoZy1eVYnGJOEtaUeiEgOc5Ezk8=")
//    // 这里拿到的是公开元数据与简易正文
    val ddtt =
        fetcher.extract("https://www.xiaohongshu.com/explore/627e29bf000000000102ff57?note_flow_source=wechat&xsec_token=CBuK-T8DUTVg8qBlgro0uc5vpgwEX7yVdGpvgNuIBeG-o=")
    ddtt.pp()
//    data.pp()
//    fetcher.extract("https://www.xiaohongshu.com/explore/68bb6365000000001c005d39?app_platform=android&ignoreEngage=true&app_version=8.81.0&share_from_user_hidden=true&xsec_source=app_share&type=video&xsec_token=CBfhA4DVQBJFZ71UAvVtlHW1gmLou2bZ-xEzoUoC_vtPw=&author_share=1&xhsshare=QQ&shareRedId=ODhGMjc-Sj02NzUyOTgwNjY2OTpJSUo7&apptime=1757161724&share_id=8e9ab3665cf8434aa3ff3733ff1695d2&share_channel=qq")
//        .pp()
    fetcher.extract("http://xhslink.com/o/AhMyQVl1fnz ").pp()
}