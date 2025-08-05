package org.koitharu.kotatsu.parsers.site.ru

import androidx.collection.ArrayMap
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import org.json.JSONArray
import org.koitharu.kotatsu.parsers.InternalParsersApi
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("MANGA_OVH", "Манга ОВХ", "ru")
fun JSONArray.joinToString(
    separator: CharSequence = ", ",
    transform: (JSONObject) -> String
): String {
    val sb = StringBuilder()
    for (i in 0 until length()) {
        if (i > 0) sb.append(separator)
        sb.append(transform(getJSONObject(i)))
    }
    return sb.toString()
}
internal class MangaOVHParser(
	context: MangaLoaderContext,
) : PagedMangaParser(context, MangaParserSource.MANGA_OVH, pageSize = 20) {

	override val availableSortOrders: Set<SortOrder> =
		EnumSet.of(
			SortOrder.POPULARITY,
			SortOrder.RATING,
			SortOrder.UPDATED,
			SortOrder.NEWEST,
		)

	@InternalParsersApi
	override val configKeyDomain = ConfigKey.Domain("zenmanga.me")

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = true,
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.of(
			MangaState.UPCOMING,
			MangaState.PAUSED,
			MangaState.ONGOING,
			MangaState.FINISHED,
		),
		availableContentRating = EnumSet.allOf(ContentRating::class.java),
	)

	init {
		paginator.firstPage = 0
		searchPaginator.firstPage = 0
	}

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url =
			urlBuilder("api")
				.addPathSegment("v2")
				.addPathSegment("books")
				.addQueryParameter("page", page.toString())
				.addQueryParameter("size", pageSize.toString())
				.addQueryParameter("type", "COMIC")
		when {
			filter.query.isNullOrEmpty() -> {
				url.addQueryParameter(
					"sort",
					when (order) {
						SortOrder.UPDATED -> "updatedAt,desc"
						SortOrder.POPULARITY -> "viewsCount,desc"
						SortOrder.RATING -> "likesCount,desc"
						SortOrder.NEWEST -> "createdAt,desc"
						else -> throw IllegalArgumentException("Unsupported $order")
					},
				)
				if (filter.tags.isNotEmpty()) {
					url.addQueryParameter("labelsInclude", filter.tags.joinToString(",") { it.key })
				}
				if (filter.tagsExclude.isNotEmpty()) {
					url.addQueryParameter("labelsExclude", filter.tags.joinToString(",") { it.key })
				}
				if (filter.states.isNotEmpty()) {
					url.addQueryParameter(
						"status",
						filter.states.joinToString(",") {
							when (it) {
								MangaState.ONGOING -> "ONGOING"
								MangaState.FINISHED -> "DONE"
								MangaState.ABANDONED -> ""
								MangaState.PAUSED -> "FROZEN"
								MangaState.UPCOMING -> "ANNOUNCE"
								else -> throw IllegalArgumentException("$it not supported")
							}
						},
					)
				}
				if (filter.contentRating.isNotEmpty()) {
					url.addQueryParameter(
						"contentStatus",
						filter.contentRating.joinToString(",") {
							when (it) {
								ContentRating.SAFE -> "SAFE"
								ContentRating.SUGGESTIVE -> "UNSAFE,EROTIC"
								ContentRating.ADULT -> "PORNOGRAPHIC"
							}
						},
					)
				}
			}

			else -> {
				url.addQueryParameter("search", filter.query)
			}
		}
		val ja = webClient.httpGet(url.build()).parseJsonArray()
		return ja.mapJSON { jo -> jo.toManga() }
	}

	override suspend fun getDetails(manga: Manga): Manga =
		coroutineScope {
			val chaptersDeferred = async { getChapters(manga.url) }
			val url =
				urlBuilder("api")
					.addPathSegment("v2")
					.addPathSegment("books")
					.addPathSegment(manga.url)
			val jo = webClient.httpGet(url.build()).parseJson()
			val isNsfwSource = jo.getStringOrNull("contentStatus").isNsfw()
			Manga(
				id = generateUid(jo.getString("id")),
				title = jo.getJSONObject("name").getString("ru"),
				altTitles = setOfNotNull(jo.getJSONObject("name").getStringOrNull("en")),
				url = jo.getString("id"),
				publicUrl = "https://$domain/manga/${jo.getString("slug")}",
				rating = jo.getFloatOrDefault("averageRating", -10f) / 10f,
				contentRating = if (isNsfwSource) ContentRating.ADULT else null,
				coverUrl = jo.getString("poster"),
				tags = jo.getJSONArray("labels").mapJSONToSet { it.toMangaTag() },
				state = jo.getStringOrNull("status")?.toMangaState(),
				authors = jo.getJSONArray("relations").asTypedList<JSONObject>().mapNotNullToSet {
					if (it.getStringOrNull("type") == "AUTHOR") {
						it.getJSONObject("publisher").getStringOrNull("name")
					} else {
						null
					}
				},
				source = source,
				largeCoverUrl = null,
				description = jo.getString("description").nl2br(),
				chapters = chaptersDeferred.await(),
			)
		}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val url =
			urlBuilder("api")
				.addPathSegment("v2")
				.addPathSegment("chapters")
				.addPathSegment(chapter.url)
		val json = webClient.httpGet(url.build()).parseJson()
		return json.getJSONArray("pages").mapJSON { jo ->
			MangaPage(
				id = generateUid(jo.getString("id")),
				url = jo.getString("image"),
				preview = null,
				source = source,
			)
		}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val url = urlBuilder("api").addPathSegment("label")
		val json = webClient.httpGet(url.build()).parseJson()
		return json.getJSONArray("content").mapJSONToSet { jo ->
			MangaTag(
				title = jo.getJSONObject("name").getString("ru").toTitleCase(sourceLocale),
				key = jo.getString("slug"),
				source = source,
			)
		}
	}

	override suspend fun getRelatedManga(seed: Manga): List<Manga> {
		val url =
			urlBuilder("api")
				.addPathSegment("book")
				.addPathSegment(seed.url)
				.addPathSegment("related")
		val ja = webClient.httpGet(url.build()).parseJsonArray()
		return ja.mapJSON { jo -> jo.toManga() }
	}

	override suspend fun getPageUrl(page: MangaPage): String = page.url +"?width=1200&type=webp&quality=75"

	private suspend fun getChapters(mangaId: String): List<MangaChapter> {
    val url = urlBuilder("api")
        .addPathSegment("v2")
        .addPathSegment("chapters")
        .addQueryParameter("bookId", mangaId)
    val ja = webClient.httpGet(url.build()).parseJsonArray()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.ROOT)

    return ja.map { jo ->
        val chapterObj = jo as JSONObject
        val number = chapterObj.optDouble("number", 0.0).toFloat()
        val volume = chapterObj.optInt("volume", 0)
        val branchId = chapterObj.getString("branchId")

        // Получаем переводчиков из publishers главы
        val scanlatorFromChapter = if (chapterObj.has("publishers")) {
            chapterObj.getJSONArray("publishers")
                .joinToString(", ") { it.getString("name") }
        } else {
            null
        }

        // Если в главе нет publishers, получаем из ветки
        val scanlator = scanlatorFromChapter ?: getBranchName(branchId)

        MangaChapter(
            id = generateUid(chapterObj.getString("id")),
            title = chapterObj.optString("name", null),
            number = number,
            volume = volume,
            url = chapterObj.getString("id"),
            scanlator = scanlator,
            uploadDate = dateFormat.parseSafe(chapterObj.getString("createdAt")),
            branch = branchId,
            source = source,
        )
    }.reversed()
}

	private suspend fun getBranchName(id: String): String? = runCatchingCancellable {
    val url = urlBuilder("api")
        .addPathSegment("branch")
        .addPathSegment(id)
    val json = webClient.httpGet(url.build()).parseJson()
    if (json.has("publishers")) {
        json.getJSONArray("publishers")
            .joinToString(", ") { it.getString("name") }
    } else {
        null
    }
}.getOrNull()

	private fun String.toMangaState() =
		when (this.uppercase(Locale.ROOT)) {
			"DONE" -> MangaState.FINISHED
			"ONGOING" -> MangaState.ONGOING
			"FROZEN" -> MangaState.PAUSED
			"ANNOUNCE" -> MangaState.UPCOMING
			else -> null
		}

	private fun String?.isNsfw() =
		this.equals("EROTIC", ignoreCase = true) ||
			this.equals("PORNOGRAPHIC", ignoreCase = true)

	private fun JSONObject.toMangaTag() =
		MangaTag(
			title = getString("name").toTitleCase(sourceLocale),
			key = getString("slug"),
			source = source,
		)

	private fun JSONObject.toManga(): Manga {
		val isNsfwSource = getStringOrNull("contentStatus").isNsfw()
		return Manga(
			id = generateUid(getString("id")),
			title = getJSONObject("name").getString("ru"),
			altTitles = setOfNotNull(getJSONObject("name").getStringOrNull("en")),
			url = getString("id"),
			publicUrl = "https://$domain/manga/${getString("slug")}",
			rating = getFloatOrDefault("averageRating", -10f) / 10f,
			contentRating = if (isNsfwSource) ContentRating.ADULT else null,
			coverUrl = getString("poster"),
			tags = setOf(),
			state = getStringOrNull("status")?.toMangaState(),
			authors = emptySet(),
			source = source,
		)
	}
}
