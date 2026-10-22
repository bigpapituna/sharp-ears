package com.bigpapituna.sharpears.ui.chords

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.data.LessonStore
import com.bigpapituna.sharpears.engine.Chord
import com.bigpapituna.sharpears.engine.ChordType

/**
 * Debug only (`--es screen popsongs --es view cards`): the "Songs that use these chords" card, as it looks after a pack of
 * C, F, G and Am is finished, on a plain page. Not reachable in a release. The home's card for songs by chords went in 2.7:
 * the Songs tab is the way in.
 */
@Composable
fun PopCardsDemo(lessons: LessonStore) {
    val learned = setOf(Chord(0, ChordType.MAJOR), Chord(5, ChordType.MAJOR), Chord(7, ChordType.MAJOR), Chord(9, ChordType.MINOR))
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SongsForChordsCard(PopCatalogue.songs, learned, onOpen = {})
        }
    }
}
