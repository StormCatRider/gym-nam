// ==============================================================================
// PLAIN-ENGLISH GUIDE
// WHAT THIS FILE IS: the mood log on the Today screen.
//  - MoodCard:  "How do you feel?" with 5 faces (😄 🙂 😐 😕 🤢). Tapping one opens the
//               sheet below. Under it, today's check-ins (long-press one to delete it).
//  - MoodSheet: pick how you feel, tick symptoms, and tick the foods you think caused
//               it ("this made me sick"). The food list shows what you ate today and
//               yesterday. Saved with the time, so the calendar and the Me page's
//               "possible triggers" can use it.
// The data side is data/Mood.kt; saving goes through TodayViewModel.addMood().
// ==============================================================================
package com.gymnam.app.ui.today

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gymnam.app.R
import com.gymnam.app.data.Mood
import com.gymnam.app.data.MoodEntry
import com.gymnam.app.data.Symptom
import com.gymnam.app.ui.common.SoftCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Label for each mood and symptom, in your language. */
@Composable
fun moodLabel(m: Mood): String = stringResource(when (m) {
    Mood.GREAT -> R.string.mood_great; Mood.GOOD -> R.string.mood_good; Mood.OKAY -> R.string.mood_okay
    Mood.MEH -> R.string.mood_meh; Mood.SICK -> R.string.mood_sick
})

@Composable
fun symptomLabel(s: Symptom): String = stringResource(when (s) {
    Symptom.NAUSEA -> R.string.sym_nausea; Symptom.BLOATING -> R.string.sym_bloating
    Symptom.STOMACH_PAIN -> R.string.sym_stomach_pain; Symptom.DIARRHEA -> R.string.sym_diarrhea
    Symptom.HEARTBURN -> R.string.sym_heartburn; Symptom.HEADACHE -> R.string.sym_headache
    Symptom.RASH -> R.string.sym_rash; Symptom.TIRED -> R.string.sym_tired
})

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MoodCard(moods: List<MoodEntry>, onPick: (Mood) -> Unit, onLongPress: (MoodEntry) -> Unit) {
    val timeFmt = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    SoftCard(padding = 16.dp) {
        Text("💭  " + stringResource(R.string.mood_title), style = MaterialTheme.typography.titleMedium)
        // The 5 faces, best to worst. Each is a big round button.
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Mood.entries.forEach { m ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(onClick = { onPick(m) }, shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)) {
                        Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(m.emoji, fontSize = 24.sp)
                        }
                    }
                    Text(moodLabel(m), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        // Today's check-ins: "2:15 PM  🤢  Sick · Nausea · from: Milk"
        moods.forEach { e ->
            val time = Instant.ofEpochMilli(e.createdAt).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFmt)
            val details = buildList {
                addAll(e.symptomList.map { symptomLabel(it) })
                if (e.foodList.isNotEmpty()) add(stringResource(R.string.mood_from, e.foodList.joinToString(", ")))
                if (e.note.isNotBlank()) add("“${e.note}”")
            }.joinToString(" · ")
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp).clip(MaterialTheme.shapes.small)
                    .combinedClickable(onClick = {}, onLongClick = { onLongPress(e) }),
                verticalAlignment = Alignment.Top
            ) {
                Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(64.dp))
                Text(e.moodEnum.emoji, fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(moodLabel(e.moodEnum), style = MaterialTheme.typography.bodyMedium)
                    if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * The check-in sheet. initial = the face you tapped; preselected = a food that's already
 * ticked (when you came from a food's "This made me sick" button).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MoodSheet(
    initial: Mood,
    preselected: String?,
    choices: List<String>,
    onSave: (mood: Int, symptoms: List<String>, foods: List<String>, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    var mood by remember { mutableStateOf(initial) }
    val symptoms = remember { mutableStateListOf<Symptom>() }
    val foods = remember { mutableStateListOf<String>().apply { if (preselected != null) add(preselected) } }
    var note by remember { mutableStateOf("") }
    // Show the preselected food even if it's not in today's/yesterday's list.
    val allChoices = remember(choices, preselected) { (listOfNotNull(preselected) + choices).distinct() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()).imePadding()
        ) {
            Text("💭  " + stringResource(R.string.mood_title), style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Mood.entries.forEach { m ->
                    FilterChip(mood == m, { mood = m }, label = { Text(m.emoji, fontSize = 22.sp) }, shape = CircleShape)
                }
            }
            Text(moodLabel(mood), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))

            Text(stringResource(R.string.mood_symptoms), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 14.dp))
            FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Symptom.entries.forEach { sy ->
                    FilterChip(sy in symptoms, { if (sy in symptoms) symptoms.remove(sy) else symptoms.add(sy) },
                        label = { Text(sy.emoji + " " + symptomLabel(sy)) }, shape = CircleShape)
                }
            }

            Text(stringResource(R.string.mood_which_food), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 14.dp))
            if (allChoices.isEmpty()) Text(stringResource(R.string.mood_no_foods), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            allChoices.take(20).forEach { f ->
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                        .combinedClickableCompat { if (f in foods) foods.remove(f) else foods.add(f) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(f in foods, { if (it) foods.add(f) else foods.remove(f) })
                    Text(f, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            OutlinedTextField(note, { note = it.take(200) }, Modifier.fillMaxWidth().padding(top = 10.dp),
                label = { Text(stringResource(R.string.mood_note)) }, shape = MaterialTheme.shapes.medium)
            Text(stringResource(R.string.mood_doctor_note), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            Button(
                onClick = { onSave(mood.level, symptoms.map { it.id }, foods.toList(), note); onDismiss() },
                shape = CircleShape, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
            ) { Text(stringResource(R.string.save)) }
        }
    }
}

/** A plain tap handler (kept separate so the row and the checkbox toggle the same way). */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier = this.combinedClickable(onClick = onClick)
