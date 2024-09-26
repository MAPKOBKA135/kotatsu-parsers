package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("BESTMANHUACOM", "BestManhua.com", "en")
internal class BestManhuaCom(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.BESTMANHUACOM, "bestmanhua.com", 10) {
	override val withoutAjax = true
}
