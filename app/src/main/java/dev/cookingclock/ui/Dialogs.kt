package dev.cookingclock.ui

import android.widget.NumberPicker
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.sp
import dev.cookingclock.data.AlarmProfile
import dev.cookingclock.data.AppState
import dev.cookingclock.data.CLOCK_COLORS
import dev.cookingclock.data.ClockController
import dev.cookingclock.data.CookingClock

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Lets the user type a name or pick one from the saved list. [onPick] receives the name and
 * whether to add it to the saved names. Saved names can be deleted here, after confirmation.
 */
@Composable
fun NameDialog(
    title: String,
    initial: String,
    names: List<String>,
    onPick: (name: String, save: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(initial) }
    var save by remember { mutableStateOf(true) }
    var toDelete by remember { mutableStateOf<String?>(null) }
    val filtered = if (text.isBlank() || text == initial) names
    else names.filter { it.contains(text.trim(), ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onPick(text.trim(), save) }),
                )
                Row(
                    Modifier.fillMaxWidth().toggleable(save, role = Role.Checkbox) { save = it },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = save, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                    Text("Add to saved names")
                }
                Text("Saved names", style = MaterialTheme.typography.labelMedium)
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(filtered, key = { it }) { name ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(name, false) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(name, Modifier.weight(1f).padding(vertical = 12.dp, horizontal = 4.dp))
                            IconButton(onClick = { toDelete = name }) {
                                Icon(Icons.Default.Close, contentDescription = "Delete name $name")
                            }
                        }
                        HorizontalDivider()
                    }
                }
                if (filtered.isEmpty()) {
                    Text(
                        if (names.isEmpty()) "No saved names." else "No matching names.",
                        Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(text.trim(), save) }, enabled = text.isNotBlank()) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    toDelete?.let { name ->
        ConfirmDialog(
            title = "Delete name?",
            text = "\"$name\" will be removed from the saved names.",
            confirmLabel = "Delete",
            onConfirm = { ClockController.deleteName(context, name); toDelete = null },
            onDismiss = { toDelete = null },
        )
    }
}

private val PRESET_MINUTES = listOf(1, 5, 10, 15, 20, 30)

/**
 * Sets the current value of a clock with hour, minute and second wheels, or with a preset.
 * Nothing changes until OK is pressed.
 */
@Composable
fun TimeDialog(clock: CookingClock, now: Long, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val initial = remember { (clock.remainingAt(now) + 999) / 1000 }
    var hours by remember { mutableIntStateOf((initial / 3600).toInt().coerceAtMost(23)) }
    var minutes by remember { mutableIntStateOf(((initial % 3600) / 60).toInt()) }
    var seconds by remember { mutableIntStateOf((initial % 60).toInt()) }
    val totalMs = ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(clock.name) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WheelPicker(hours, 23, "Hours") { hours = it }
                    Text(":", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    WheelPicker(minutes, 59, "Minutes") { minutes = it }
                    Text(":", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    WheelPicker(seconds, 59, "Seconds") { seconds = it }
                }
                Spacer(Modifier.size(12.dp))
                PRESET_MINUTES.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (m in row) {
                            FilledTonalButton(
                                onClick = { hours = 0; minutes = m; seconds = 0 },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                            ) { Text("$m min", maxLines = 1, softWrap = false) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { ClockController.setTime(context, clock.id, totalMs); onDismiss() },
                enabled = totalMs > 0,
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The platform [NumberPicker] wheel, showing two-digit values from 0 to [max]. */
@Composable
private fun WheelPicker(value: Int, max: Int, label: String, onChange: (Int) -> Unit) {
    AndroidView(
        factory = { ctx ->
            NumberPicker(ctx).apply {
                minValue = 0
                maxValue = max
                // Displayed values instead of a formatter: a formatter skips the first draw.
                displayedValues = Array(max + 1) { "%02d".format(it) }
                wrapSelectorWheel = true
                descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                contentDescription = label
                setOnValueChangedListener { _, _, new -> onChange(new) }
            }
        },
        update = { if (it.value != value) it.value = value },
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

/** Background color and alarm profile of one clock. */
@Composable
fun ClockOptionsDialog(clock: CookingClock, profiles: List<AlarmProfile>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(clock.name) },
        text = {
            Column {
                Text("Background color", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.size(8.dp))
                CLOCK_COLORS.chunked(5).forEach { rowColors ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        rowColors.forEach { color ->
                            ColorSwatch(color, selected = clock.color == color) {
                                ClockController.setColor(context, clock.id, color)
                            }
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                }
                Spacer(Modifier.size(8.dp))
                Text("Alarm profile", style = MaterialTheme.typography.titleSmall)
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(profiles, key = { it.id }) { p ->
                        val selected = (clock.profileId ?: AppState.DEFAULT_PROFILE_ID) == p.id
                        Row(
                            Modifier.fillMaxWidth().selectable(selected) {
                                ClockController.setProfile(context, clock.id, if (p.id == AppState.DEFAULT_PROFILE_ID) null else p.id)
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
                            Text(p.name)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun ColorSwatch(color: Int?, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
    else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    Box(
        Modifier
            .size(40.dp)
            .border(border, CircleShape)
            .padding(3.dp)
            .background(if (color == null) Color.White else Color(color), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            selected -> Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color(0xFF201A17))
            color == null -> Text("none", color = Color.Gray, fontSize = 10.sp)
        }
    }
}
