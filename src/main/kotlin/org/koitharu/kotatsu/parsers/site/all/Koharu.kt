package org.koitharu.kotatsu.parsers.site.all

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.HttpStatusException
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.core.LegacyPagedMangaParser
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.net.HttpURLConnection
import java.text.SimpleDateFormat
import java.util.*
import org.koitharu.kotatsu.parsers.Broken

@Broken("Need fix getPages + fetchTags + fetchAuthors")
@MangaSourceParser("KOHARU", "Schale.network", type = ContentType.HENTAI)
internal class Koharu(context: MangaLoaderContext) :
	LegacyPagedMangaParser(context, MangaParserSource.KOHARU, 24) {

    override val configKeyDomain = ConfigKey.Domain("niyaniya.moe")
    private val apiSuffix = "api.schale.network"
    
    private var authorMap: Map<String, String>? = null
    
    private val preferredImageResolutionKey = ConfigKey.PreferredImageServer(
        presetValues = mapOf(
            "0" to "Lowest Quality",
            "780" to "Low Quality (780px)",
            "980" to "Medium Quality (980px)",
            "1280" to "High Quality (1280px)",
            "1600" to "Highest Quality (1600px)",
        ),
        defaultValue = "1280",
    )

    override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
        keys.add(preferredImageResolutionKey)
	}

    override fun getRequestHeaders() = super.getRequestHeaders().newBuilder()
		.add("referer", "https://$domain/")
        .add("origin", "https://$domain")
		.build()

    override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.NEWEST,
		SortOrder.POPULARITY,
		SortOrder.POPULARITY_TODAY,
		SortOrder.POPULARITY_WEEK,
        SortOrder.ALPHABETICAL,
        SortOrder.ALPHABETICAL_DESC,
        SortOrder.RATING
	)

    override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isSearchSupported = true,
            isAuthorSearchSupported = true,
            isSearchWithFiltersSupported = true,
            isTagsExclusionSupported = true
		)

    override suspend fun getFilterOptions() = MangaListFilterOptions(
        availableTags = fetchTags()
    )
    
    override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
        val baseUrl = "https://$apiSuffix/books"
        val url = buildString {
            append(baseUrl)
            
            val terms: MutableList<String> = mutableListOf()
            val includedTags: MutableList<String> = mutableListOf()
            val excludedTags: MutableList<String> = mutableListOf()
            
            if (!filter.query.isNullOrEmpty() && filter.query.startsWith("id:")) {
                val ipk = filter.query.removePrefix("id:")
                val response = webClient.httpGet("$baseUrl/detail/$ipk").parseJson()
                return listOf(parseMangaDetail(response))
            }
            
            val sortValue = when (order) {
                SortOrder.POPULARITY, SortOrder.POPULARITY_TODAY -> "8"
                SortOrder.POPULARITY_WEEK -> "9"
                SortOrder.ALPHABETICAL -> "2"
                SortOrder.ALPHABETICAL_DESC -> "2"
                SortOrder.RATING -> "3"
                SortOrder.NEWEST -> "4"
                else -> "4"
            }
            append("?sort=").append(sortValue)
            
            if (!filter.query.isNullOrEmpty()) {
                terms.add("title:\"${filter.query.urlEncoded()}\"")
            }
            
            if (!filter.author.isNullOrEmpty()) {
                val authors = fetchAuthors()
                val authorId = authors[filter.author] ?: 
                            authors.entries.find { it.key.equals(filter.author, ignoreCase = true) }?.value
                
                if (authorId != null) {
                    includedTags.add(authorId)
                } else {
                    terms.add("artist:\"${filter.author.urlEncoded()}\"")
                }
            }
            
            filter.tags.forEach { tag ->
                if (tag.key.startsWith("-")) {
                    excludedTags.add(tag.key.substring(1))
                } else {
                    includedTags.add(tag.key)
                }
            }
            
            if (excludedTags.isNotEmpty()) {
                append("&exclude=").append(excludedTags.joinToString(","))
                append("&e=1")
            }
            
            if (includedTags.isNotEmpty()) {
                append("&include=").append(includedTags.joinToString(","))
                append("&i=1")
            }
            
            append("&page=").append(page)
            
            if (terms.isNotEmpty()) {
                append("&s=").append(terms.joinToString(" ").urlEncoded())
            }
        }
        
        val json = webClient.httpGet(url).parseJson()
        if (json.has("error") || json.has("message")) {
            return emptyList()
        }
        return parseMangaList(json)
    }

    private fun parseMangaList(json: JSONObject): List<Manga> {
        val entries = json.optJSONArray("entries") ?: return emptyList()
        val results = ArrayList<Manga>(entries.length())
        
        for (i in 0 until entries.length()) {
            val entry = entries.getJSONObject(i)
            val id = entry.getInt("id")
            val key = entry.getString("key")
            val url = "$id/$key"
            
            results.add(
                Manga(
                    id = generateUid(url),
                    url = url,
                    publicUrl = "https://$domain/g/$url",
                    title = entry.getString("title"),
                    altTitles = emptySet(),
                    authors = emptySet(),
                    tags = emptySet(),
                    rating = RATING_UNKNOWN,
                    state = MangaState.FINISHED,
                    coverUrl = entry.getJSONObject("thumbnail").getString("path"),
                    contentRating = ContentRating.ADULT,
                    source = source,
                )
            )
        }
        
        return results
    }

    private fun parseMangaDetail(json: JSONObject): Manga {
        val data = json.getJSONObject("data")
        val id = data.getInt("id")
        val key = data.getString("key")
        val url = "$id/$key"
        
        var author: String? = null
        val tags = data.optJSONArray("tags")
        if (tags != null) {
            for (i in 0 until tags.length()) {
                val tag = tags.getJSONObject(i)
                if (tag.getInt("namespace") == 1) {
                    author = tag.getString("name")
                    break
                }
            }
        }
        
        return Manga(
            id = generateUid(url),
            url = url,
            publicUrl = "https://$domain/g/$url",
            title = data.getString("title"),
            altTitles = emptySet(),
            authors = setOfNotNull(author),
            tags = emptySet(),
            rating = RATING_UNKNOWN,
            state = MangaState.FINISHED,
            coverUrl = data.getJSONObject("thumbnails").getJSONObject("main").getString("path"),
            contentRating = ContentRating.ADULT,
            source = source,
        )
    }

    override suspend fun getDetails(manga: Manga): Manga {
        val url = manga.url
        val response = webClient.httpGet("https://$apiSuffix/books/detail/$url").parseJson()
        
        val id = response.getInt("id")
        val key = response.getString("key")
        val mangaUrl = "$id/$key"
        
        val tagsList = mutableSetOf<MangaTag>()
        var author: String? = null
        val tags = response.optJSONArray("tags")
        
        if (tags != null) {
            for (i in 0 until tags.length()) {
                val tag = tags.getJSONObject(i)
                if (tag.has("namespace")) {
                    val namespace = tag.getInt("namespace")
                    val tagName = tag.getString("name")
                    
                    when (namespace) {
                        1 -> {
                            author = tagName
                        }
                        0, 3, 8, 9, 10, 12 -> {
                            tagsList.add(
                                MangaTag(
                                    key = tagName,
                                    title = tagName.toTitleCase(sourceLocale),
                                    source = source
                                )
                            )
                        }
                    }
                } else {
                    val tagName = tag.getString("name")
                    tagsList.add(
                        MangaTag(
                            key = tagName,
                            title = tagName.toTitleCase(sourceLocale),
                            source = source
                        )
                    )
                }
            }
        }
        
        val description = buildString {
            val created = response.getLongOrDefault("created_at", 0L)
            if (created > 0) {
                append("Posted: ").append(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(created)).append("\n")
            }
            
            val thumbnails = response.getJSONObject("thumbnails")
            val pageCount = thumbnails.optJSONArray("entries")?.length() ?: 0
            append("Pages: ").append(pageCount)
        }
        
        val thumbnails = response.getJSONObject("thumbnails")
        val base = thumbnails.getString("base")
        val mainPath = thumbnails.getJSONObject("main").getString("path")
        val coverUrl = base + mainPath
        
        return Manga(
            id = generateUid(mangaUrl),
            url = mangaUrl,
            publicUrl = "https://$domain/g/$mangaUrl",
            title = response.getString("title"),
            altTitles = emptySet(),
            authors = setOfNotNull(author),
            tags = tagsList,
            rating = RATING_UNKNOWN,
            state = MangaState.FINISHED,
            description = description,
            coverUrl = coverUrl,
            contentRating = ContentRating.ADULT,
            source = source,
            chapters = listOf(
                MangaChapter(
                    id = generateUid("$mangaUrl/chapter"),
                    title = "Oneshot",
                    number = 1f,
                    url = mangaUrl,
                    scanlator = null,
                    uploadDate = response.getLongOrDefault("created_at", 0L),
                    branch = null,
                    source = source,
                    volume = 0,
                )
            )
        )
    }

    private var cachedToken: String? = null

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val mangaUrl = chapter.url
        val parts = mangaUrl.split("/")
        if (parts.size < 2) {
            throw Exception("Invalid URL")
        }
        
        val id = parts[0]
        val key = parts[1]
        
        val detailResponse = webClient.httpGet("https://$apiSuffix/books/detail/$id/$key").parseJson()
        
        var currentToken = detailResponse.optString("crt", cachedToken ?: "")
        
        if (currentToken.isEmpty()) {
            try {
                val noTokenResponse = webClient.httpPost(
                    url = "https://$apiSuffix/books/detail/$id/$key".toHttpUrl(),
                    form = emptyMap(),
                    extraHeaders = getRequestHeaders()
                ).parseJson()
                
                currentToken = noTokenResponse.optString("crt", "")
                if (currentToken.isNotEmpty()) {
                    cachedToken = currentToken
                }
            } catch (e: Exception) {
                throw Exception("Cant get auth token!")
            }
        }
        
        if (currentToken.isEmpty()) {
            throw Exception("Cant get auth token!")
        }
        
        val dataResponse = try {
            webClient.httpPost(
                url = "https://$apiSuffix/books/detail/$id/$key?crt=$currentToken".toHttpUrl(),
                form = emptyMap(),
                extraHeaders = getRequestHeaders()
            ).parseJson()
        } catch (e: Exception) {
            throw Exception("Cant send POST request: ${e.message}. Token may be expired.")
        }
        
        val data = try {
            dataResponse.getJSONObject("data").getJSONObject("data")
        } catch (e: Exception) {
            throw Exception("Cant parse image data. Token may be invalid or expired: ${e.message}")
        }
        
        val preferredRes = config[preferredImageResolutionKey] ?: "1280"
        val resolutionOrder = when (preferredRes) {
            "1600" -> listOf("1600", "1280", "0", "980", "780")
            "1280" -> listOf("1280", "1600", "0", "980", "780")
            "980" -> listOf("980", "1280", "0", "1600", "780")
            "780" -> listOf("780", "980", "0", "1280", "1600")
            else -> listOf("0", "1600", "1280", "980", "780")
        }
        
        var selectedImageId: Int? = null
        var selectedPublicKey: String? = null
        var selectedQuality = "0"
        
        for (res in resolutionOrder) {
            if (data.has(res) && !data.isNull(res)) {
                val resData = data.getJSONObject(res)
                if (resData.has("id") && resData.has("key")) {
                    selectedImageId = resData.getInt("id")
                    selectedPublicKey = resData.getString("key")
                    selectedQuality = res
                    break
                }
            }
        }
        
        if (selectedImageId == null || selectedPublicKey == null) {
            throw Exception("Cant find image data")
        }
        
        val imagesResponse = try {
            webClient.httpGet(
                "https://$apiSuffix/books/data/$id/$key/$selectedImageId/$selectedPublicKey/$selectedQuality?crt=$currentToken"
            ).parseJson()
        } catch (e: Exception) {
            throw Exception("Cant fetch image data: ${e.message}")
        }
        
        val base = imagesResponse.getString("base")
        val entries = imagesResponse.getJSONArray("entries")
        
        val pages = ArrayList<MangaPage>(entries.length())
        for (i in 0 until entries.length()) {
            val imagePath = entries.getJSONObject(i).getString("path")
            val fullImageUrl = "$base$imagePath"
            
            pages.add(
                MangaPage(
                    id = generateUid(fullImageUrl),
                    url = fullImageUrl,
                    preview = null,
                    source = source,
                )
            )
        }
        
        return pages
    }

    private suspend fun fetchTags(): Set<MangaTag> {
        val url = "https://$apiSuffix/books/tags/filters"
        val json = try {
            webClient.httpGet(url).parseJson()
        } catch (e: Exception) {
            return emptySet()
        }
        
        val tagsArray = json.optJSONArray("tags") ?: return emptySet()
        
        return tagsArray.mapJSONNotNullToSet { tagObject ->
            if (tagObject.has("namespace")) {
                return@mapJSONNotNullToSet null
            }
            
            if (tagObject.has("id") && tagObject.has("name")) {
                MangaTag(
                    key = tagObject.getString("id"),
                    title = tagObject.getString("name").toTitleCase(sourceLocale),
                    source = source
                )
            } else {
                null
            }
        }
    }

    private suspend fun fetchAuthors(): Map<String, String> {
        if (authorMap != null) {
            return authorMap!!
        }
        
        val url = "https://$apiSuffix/books/tags/filters"
        val json = try {
            webClient.httpGet(url).parseJson()
        } catch (e: Exception) {
            return emptyMap()
        }
        
        val tagsArray = json.optJSONArray("tags") ?: return emptyMap()
        val result = mutableMapOf<String, String>()
        
        for (i in 0 until tagsArray.length()) {
            val tagObject = tagsArray.getJSONObject(i)
            
            if (tagObject.has("namespace") && tagObject.getInt("namespace") == 1) {
                if (tagObject.has("id") && tagObject.has("name")) {
                    val id = tagObject.getString("id")
                    val name = tagObject.getString("name")
                    result[name] = id
                }
            }
        }
        
        authorMap = result
        return result
    }
}