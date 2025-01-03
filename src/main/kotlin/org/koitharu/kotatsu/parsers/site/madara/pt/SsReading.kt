package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("SSREADING", "SsReading", "pt")
internal class SsReading(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.SSREADING, "ssreading.com.br") {
	override val datePattern: String = "dd 'de' MMM 'de' yyyy"
}
