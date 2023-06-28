package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.text.SimpleDateFormat

@MangaSourceParser("DUNIAKOMIK", "Duniakomik", "id")
internal class Duniakomik(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.DUNIAKOMIK, pageSize = 12, searchPageSize = 12) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("duniakomik.id")

	override val listUrl: String
		get() = "/manga"
	override val tableMode: Boolean
		get() = true

	override val isNsfwSource = true

	override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", idLocale)

}