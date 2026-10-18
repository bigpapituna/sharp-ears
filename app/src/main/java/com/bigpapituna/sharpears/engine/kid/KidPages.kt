package com.bigpapituna.sharpears.engine.kid

import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_PAGES
import com.bigpapituna.sharpears.engine.playground.PlaygroundPage
import com.bigpapituna.sharpears.engine.world.Place
import com.bigpapituna.sharpears.engine.playground.Place as PagePlace

/**
 * The Playground's pages (engine/playground/Pages.kt) as the kid's world shows them (2.2): each page is drawn as its
 * place's scene, drops that place's stickers and fills that place's album page. Everything here reads
 * [PLAYGROUND_PAGES], so a new page needs no code.
 */
object KidPages {
    /** The scene, sticker set and album page a page's place is drawn with. */
    fun worldPlace(place: PagePlace): Place = when (place) {
        PagePlace.GARDEN -> Place.GARDEN
        PagePlace.POND -> Place.POND
        PagePlace.CONCERT_HALL -> Place.CONCERT
    }

    fun worldPlace(page: PlaygroundPage): Place = worldPlace(page.place)

    /** The page [lessonId] is on, or null outside the Playground. */
    fun pageOf(lessonId: String, pages: List<PlaygroundPage> = PLAYGROUND_PAGES): PlaygroundPage? = pages.firstOrNull { lessonId in it.lessonIds }

    /** Where a finished lesson's sticker comes from: its page's place; null outside the Playground (the album picks). */
    fun dropPlace(lessonId: String, pages: List<PlaygroundPage> = PLAYGROUND_PAGES): Place? = pageOf(lessonId, pages)?.let(::worldPlace)

    /** The lesson [lessonId] as (page index, index on the page), or null outside the Playground. */
    fun locate(lessonId: String, pages: List<PlaygroundPage> = PLAYGROUND_PAGES): Pair<Int, Int>? {
        pages.forEachIndexed { p, page -> page.lessonIds.indexOf(lessonId).takeIf { it >= 0 }?.let { return p to it } }
        return null
    }

    /** The lesson at [n] (1-based) along all pages in order, as (page index, index on the page); null past the end. */
    fun locate(n: Int, pages: List<PlaygroundPage> = PLAYGROUND_PAGES): Pair<Int, Int>? {
        if (n < 1) return null
        var left = n - 1
        pages.forEachIndexed { p, page ->
            if (left < page.lessonIds.size) return p to left
            left -= page.lessonIds.size
        }
        return null
    }

    /** "the garden": a page's title inside a sentence ("Mia finished the garden!"). */
    fun inSentence(page: PlaygroundPage): String = page.title.replaceFirstChar { it.lowercase() }
}
