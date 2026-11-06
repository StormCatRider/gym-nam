// ==============================================================================
// PLAIN-ENGLISH GUIDE
// WHAT THIS FILE IS: the "Me" page (tap your photo/logo in the top bar). Top to bottom:
//  1. Your photo (tap the 📷 to add or change it) and your name or nickname.
//  2. Body basics the app actually uses for your goals: sex, age, height, weight,
//     body fat (optional), activity and goal. "Edit" re-opens the goal setup.
//  3. Allergies (milk, peanuts, ... + "Other" words you type) and diet choices
//     (vegetarian, vegan, no pork, lactose-free). Foods and recipes that match get ⚠️.
//  4. Possible triggers: foods that keep appearing when you log feeling bad.
// Everything here stays on your phone. The brain is MeViewModel.kt.
// ==============================================================================
package com.gymnam.app.ui.me

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymnam.app.R
import com.gymnam.app.data.ActivityLevel
import com.gymnam.app.data.Allergens
import com.gymnam.app.data.GoalType
import com.gymnam.app.data.LocalMetric
import com.gymnam.app.data.Sex
import com.gymnam.app.data.Units
import com.gymnam.app.data.fmtNum
import com.gymnam.app.ui.common.Avatar
import com.gymnam.app.ui.common.SoftCard
import com.gymnam.app.ui.nav.InnerTopBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeScreen(vm: MeViewModel, snackbar: SnackbarHostState, contentPadding: PaddingValues, onBack: () -> Unit, onEditBody: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val latest by vm.latestWeight.collectAsStateWithLifecycle()
    val suspects by vm.suspects.collectAsStateWithLifecycle()
    val metric = LocalMetric.current
    val scope = rememberCoroutineScope()
    val photoFailMsg = stringResource(R.string.photo_failed)

    // Android's Photo Picker: you choose ONE image; the app gets only that image.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.savePhoto(it) { scope.launch { snackbar.showSnackbar(photoFailMsg) } } }
    }
    fun pickPhoto() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    // The name box keeps its own text while you type; it's saved 0.6 s after you stop.
    var name by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(s?.userName) { if (name == null) s?.let { name = it.userName } }
    LaunchedEffect(name) { val n = name ?: return@LaunchedEffect; delay(600); if (n != s?.userName) vm.setName(n) }
    var other by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(s?.otherAllergies) { if (other == null) s?.let { other = it.otherAllergies } }
    LaunchedEffect(other) { val o = other ?: return@LaunchedEffect; delay(600); if (o != s?.otherAllergies) vm.setOther(o) }

    Column(Modifier.fillMaxSize()) {
        InnerTopBar(stringResource(R.string.me_title), onBack)
        val settings = s ?: return@Column
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Photo + name
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        Avatar(settings.photoVersion, 104.dp, onClick = ::pickPhoto, ring = false)
                        Surface(
                            onClick = ::pickPhoto, shape = CircleShape, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.BottomEnd).size(34.dp)
                        ) { Box(contentAlignment = Alignment.Center) { Text("📷") } }
                    }
                    Row {
                        TextButton(onClick = ::pickPhoto) {
                            Text(stringResource(if (settings.photoVersion == 0L) R.string.photo_add else R.string.photo_change))
                        }
                        if (settings.photoVersion != 0L) TextButton(onClick = vm::removePhoto) { Text(stringResource(R.string.photo_remove)) }
                    }
                    OutlinedTextField(
                        name.orEmpty(), { name = it.take(30) }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(stringResource(R.string.me_name)) }, shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                    )
                    Text(stringResource(R.string.me_private), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp))
                }
            }

            // 2. Body basics (read-only here; "Edit" opens the goal setup, which also recalculates)
            item {
                SoftCard {
                    Text("🧍  " + stringResource(R.string.me_body), style = MaterialTheme.typography.titleMedium)
                    val p = settings.profile
                    if (p == null) {
                        Text(stringResource(R.string.me_body_empty), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    } else {
                        val nf = NumberFormat.getIntegerInstance()
                        Field(stringResource(R.string.me_sex_age),
                            stringResource(if (p.sex == Sex.MALE) R.string.sex_male else R.string.sex_female) + " · " + p.age)
                        Field(stringResource(R.string.height), Units.height(p.heightIn, metric))
                        Field(stringResource(R.string.weight), Units.weight(latest?.lb ?: p.weightLb, metric))
                        Field(stringResource(R.string.body_fat), p.bodyFatPct?.let { fmtNum(it) + " %" } ?: "—")
                        Field(stringResource(R.string.me_activity), stringResource(when (p.activity) {
                            ActivityLevel.SEDENTARY -> R.string.act_sedentary; ActivityLevel.LIGHT -> R.string.act_light
                            ActivityLevel.MODERATE -> R.string.act_moderate; ActivityLevel.ACTIVE -> R.string.act_active
                            ActivityLevel.VERY_ACTIVE -> R.string.act_very_active
                        }))
                        val rate = if (p.goal == GoalType.MAINTAIN) "" else " · " +
                            (if (p.goal == GoalType.LOSE) "−" else "+") +
                            (if (metric) fmtNum(Math.round(p.rateLbWeek / Units.LB_PER_KG * 100) / 100.0) + " kg" else fmtNum(p.rateLbWeek) + " lb") +
                            stringResource(R.string.per_week_short)
                        Field(stringResource(R.string.goal), stringResource(when (p.goal) {
                            GoalType.LOSE -> R.string.goal_lose; GoalType.MAINTAIN -> R.string.goal_maintain; GoalType.GAIN -> R.string.goal_gain
                        }) + rate)
                        Field(stringResource(R.string.me_daily_goal), nf.format(settings.targets.calories) + " kcal", last = true)
                    }
                    FilledTonalButton(onClick = onEditBody, shape = CircleShape, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        Text(stringResource(R.string.me_edit_body))
                    }
                }
            }

            // 3. Allergies & diet
            item {
                SoftCard {
                    Text("⚠️  " + stringResource(R.string.me_allergies), style = MaterialTheme.typography.titleMedium)
                    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Allergens.allergyFlags.forEach { f ->
                            FilterChip(f.id in settings.allergies, { vm.toggleAllergy(f.id, settings.allergies) },
                                label = { Text(f.emoji + " " + stringResource(f.nameRes)) }, shape = CircleShape)
                        }
                    }
                    OutlinedTextField(
                        other.orEmpty(), { other = it.take(120) }, Modifier.fillMaxWidth().padding(top = 4.dp),
                        label = { Text(stringResource(R.string.me_other_allergies)) },
                        placeholder = { Text(stringResource(R.string.me_other_hint)) },
                        singleLine = true, shape = MaterialTheme.shapes.medium
                    )
                    Text("🥗  " + stringResource(R.string.me_diet), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 14.dp))
                    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Allergens.dietFlags.forEach { f ->
                            FilterChip(f.id in settings.diets, { vm.toggleDiet(f.id, settings.diets) },
                                label = { Text(f.emoji + " " + stringResource(f.nameRes)) }, shape = CircleShape)
                        }
                    }
                    // The "check the label" reminder, always visible here.
                    Text(stringResource(R.string.warn_how_it_works), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                    Text(stringResource(R.string.warn_check_label), style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                }
            }

            // 4. Possible triggers from the mood log
            item {
                SoftCard {
                    Text("🤢  " + stringResource(R.string.me_triggers), style = MaterialTheme.typography.titleMedium)
                    if (suspects.isEmpty()) {
                        Text(stringResource(R.string.me_triggers_empty), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    } else {
                        suspects.forEachIndexed { i, sp ->
                            Field(sp.food, stringResource(R.string.times_n, sp.times), last = i == suspects.lastIndex)
                        }
                    }
                    Text(stringResource(R.string.me_triggers_note), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

/** One "label ........ value" line with a thin divider under it. */
@Composable
private fun Field(label: String, value: String, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
    if (!last) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}
