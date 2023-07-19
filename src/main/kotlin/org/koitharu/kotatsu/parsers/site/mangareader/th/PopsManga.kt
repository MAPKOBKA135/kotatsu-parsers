package org.koitharu.kotatsu.parsers.site.mangareader.th

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.text.SimpleDateFormat
import java.util.Locale

@MangaSourceParser("POPSMANGA", "PopsManga", "th")
internal class PopsManga(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.POPSMANGA, pageSize = 20, searchPageSize = 14) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("popsmanga.com")

	override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("th", "TH"))
}