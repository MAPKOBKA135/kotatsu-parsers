package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("DECADENCESCANS", "Decadence Scans", "en")
internal class DecadenceScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.DECADENCESCANS, "reader.decadencescans.com", 10) {

	override val datePattern = "MMMM d, yyyy"
	override val isNsfwSource = true
}