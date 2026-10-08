package com.bigpapituna.sharpears.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.engine.COURSE
import com.bigpapituna.sharpears.engine.CourseLesson
import com.bigpapituna.sharpears.engine.CourseLevel
import com.bigpapituna.sharpears.engine.Mode

/** Lessons in course order, for "next lesson" and numbering. */
private val ALL = COURSE.flatMap { lv -> lv.lessons.map { lv to it } }

/** The Learn tab: a "next lesson" panel, then each level's lessons as plain numbered rows. */
@Composable
fun LearnScreen(done: (String) -> Boolean, onOpen: (CourseLesson) -> Unit) {
    val next = ALL.firstOrNull { !done(it.second.id) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 20.dp)) {
                Text("Learn", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 20.dp, bottom = 16.dp).semantics { heading() })
            }
        }
        item {
            Column(
                Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp).clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (next == null) {
                    Text("Course complete", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("Every lesson is done", style = MaterialTheme.typography.headlineMedium)
                    Text("Keep going with the songs and the daily chord practice.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val (level, lesson) = next
                    Text("${level.title}, lesson ${ALL.indexOf(next) + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(lesson.title, style = MaterialTheme.typography.headlineMedium)
                    Text(lesson.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(8.dp))
                    Button(onClick = { onOpen(lesson) }) { Text(if (ALL.indexOf(next) == 0) "Start" else "Continue") }
                }
            }
        }
        COURSE.forEach { level ->
            item(key = level.id) { LevelHeader(level, level.lessons.count { done(it.id) }) }
            items(level.lessons, key = { it.id }) { lesson -> LessonRow(ALL.indexOfFirst { it.second == lesson } + 1, lesson, done(lesson.id), onOpen) }
        }
    }
}

@Composable
private fun LevelHeader(level: CourseLevel, done: Int) {
    Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(level.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
            Text("$done of ${level.lessons.size}", style = Numeric.merge(MaterialTheme.typography.labelLarge),
                color = if (done == level.lessons.size) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(level.about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LessonRow(number: Int, lesson: CourseLesson, done: Boolean, onOpen: (CourseLesson) -> Unit) {
    Row(
        Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 8.dp).heightIn(min = 64.dp).clip(RoundedCornerShape(12.dp))
            .clickable { onOpen(lesson) }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("$number", style = Numeric.merge(MaterialTheme.typography.titleMedium), color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(lesson.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(lesson.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (done) Icon(painterResource(R.drawable.ic_check), contentDescription = "Done", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

/**
 * One lesson: what it teaches, then "Practice it" (its exercise in the player) or "Open the song".
 * Exercises finish themselves (a Perform exercise needs its pass mark); song lessons are marked done by hand.
 */
@Composable
fun LessonScreen(lesson: CourseLesson, done: Boolean, onPractice: () -> Unit, onSong: () -> Unit, onMarkDone: () -> Unit,
                 onNext: (() -> Unit)?, onBack: () -> Unit) {
    val (level, _) = ALL.first { it.second == lesson }
    val ex = lesson.exercise
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
            Text("${level.title}, lesson ${ALL.indexOfFirst { it.second == lesson } + 1}", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            Text(lesson.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            lesson.body.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
            Spacer(Modifier.size(4.dp))
            if (ex != null) {
                Text(
                    when {
                        ex.mode == Mode.PERFORM -> "Perform mode: the notes keep moving. Score ${ex.pass}% or more to finish the lesson."
                        else -> "Wait mode: the song waits for each note. Play it to the end to finish the lesson."
                    } + if (ex.practice.name != "BOTH") " ${ex.practice.name.lowercase().replaceFirstChar { it.uppercase() }} hand only." else " Both hands.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onPractice) {
                    Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(6.dp)); Text("Practice it")
                }
            }
            if (lesson.songId != null) {
                if (ex == null) Button(onClick = onSong) { Text("Open the song") } else TextButton(onClick = onSong) { Text("Open the song") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (done) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("Done", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                } else TextButton(onClick = onMarkDone) { Text("Mark as done") }
                Spacer(Modifier.weight(1f))
                onNext?.let { TextButton(onClick = it) { Text("Next lesson") } }
            }
        }
    }
}

/** The lesson after [lesson] in course order, if any. */
fun nextLesson(lesson: CourseLesson): CourseLesson? = ALL.indexOfFirst { it.second == lesson }.let { ALL.getOrNull(it + 1)?.second }
