package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.Locale

@MangaSourceParser("MUNDO_MANHWA", "Mundo Manhwa", "es")
internal class MundoManhwa(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MUNDO_MANHWA, "mundomanhwa.com", 10) {

	override val datePattern = "MMMM d, yyyy"
	override val sourceLocale: Locale = Locale("es")
}