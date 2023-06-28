package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("MANHWALOVER", "ManhwaLover", "en")
internal class ManhwaLover(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANHWALOVER, pageSize = 20, searchPageSize = 20) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("manhwalover.com")

	override val listUrl: String
		get() = "/manga"
	override val tableMode: Boolean
		get() = false

	override val isNsfwSource: Boolean = true

	override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
}