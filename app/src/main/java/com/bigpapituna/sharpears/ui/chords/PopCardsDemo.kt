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
 * Debug only (`--es screen popsongs --es view cards`): the Songs-tab card and the "Songs that use these chords" card, as
 * they look after a pack of C, F, G and Am is finished, on a plain page. Not reachable in a release.
 */
@Composable
fun PopCardsDemo(lessons: LessonStore) {
    val learned = setOf(Chord(0, ChordType.MAJOR), Chord(5, ChordType.MAJOR), Chord(7, ChordType.MAJOR), Chord(9, ChordType.MINOR))
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            PopSongsCard(playable = 2) {}
            SongsForChordsCard(PopCatalogue.songs, learned, onOpen = {})
        }
    }
}
