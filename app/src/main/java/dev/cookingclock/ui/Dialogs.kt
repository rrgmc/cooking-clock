package dev.cookingclock.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cookingclock.data.AlarmProfile
import dev.cookingclock.data.AppState
import dev.cookingclock.data.CLOCK_COLORS
import dev.cookingclock.data.ClockController
import dev.cookingclock.data.CookingClock
import dev.cookingclock.data.formatDuration

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
 * Lets the user type a name or pick one from the saved list. Typed names are added to the
 * list by [ClockController]. Saved names can be deleted here, after confirmation.
 */
@Composable
fun NameDialog(
    title: String,
    initial: String,
    names: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(initial) }
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
                    keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onPick(text.trim()) }),
                )
                Spacer(Modifier.size(8.dp))
                Text("Saved names", style = MaterialTheme.typography.labelMedium)
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(filtered, key = { it }) { name ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(name) },
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
                        if (names.isEmpty()) "No saved names." else "No matching names. The typed name will be saved.",
                        Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(text.trim()) }, enabled = text.isNotBlank()) { Text("OK") }
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

/** Changes the current value of a clock in steps of 1 or 5 minutes. */
@Composable
fun TimeDialog(clock: CookingClock, now: Long, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(clock.name) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatDuration(clock.remainingAt(now)),
                    fontSize = 48.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.size(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (m in listOf(-5, -1, 1, 5)) {
                        FilledTonalButton(
                            onClick = { ClockController.adjust(context, clock.id, m) },
                            modifier = Modifier.weight(1f),
                        ) { Text(if (m > 0) "+$m" else "$m") }
                    }
                }
                Text("minutes", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
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
