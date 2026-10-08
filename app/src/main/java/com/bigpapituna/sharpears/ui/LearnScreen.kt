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
internal val ALL = COURSE.flatMap { lv -> lv.lessons.map { lv to it } }

/** The Learn tab: a "next lesson" panel, then each level's lessons as plain numbered rows. */
@Composable
fun LearnScreen(done: (String) -> Boolean, onOpen: (CourseLesson) -> Unit, onSightRead: () -> Unit = {}, sightBest: String? = null) {
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
        // Sight-reading sits beside the course: new music each time, so it is never "done".
        item(key = "sight") {
            Row(
                Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 8.dp).padding(top = 12.dp).heightIn(min = 64.dp)
                    .clip(RoundedCornerShape(12.dp)).clickable(onClick = onSightRead).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Sight-read", style = MaterialTheme.typography.titleMedium)
                    Text(sightBest?.let { "Four new bars each time, names hidden. Best $it." } ?: "Four new bars each time, played once with the names hidden.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
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

/** The lesson after [lesson] in course order, if any. */
fun nextLesson(lesson: CourseLesson): CourseLesson? = ALL.indexOfFirst { it.second == lesson }.let { ALL.getOrNull(it + 1)?.second }
