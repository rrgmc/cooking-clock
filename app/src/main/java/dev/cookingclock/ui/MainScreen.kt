package dev.cookingclock.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cookingclock.R
import dev.cookingclock.data.AppState
import dev.cookingclock.data.ClockController
import dev.cookingclock.data.ClockState
import dev.cookingclock.data.CookingClock
import dev.cookingclock.data.formatDuration
import kotlinx.coroutines.delay

/** Which dialog the main screen shows. Only one is open at a time. */
private sealed interface MainDialog {
    data object NewClock : MainDialog
    data class Rename(val id: Int) : MainDialog
    data class Time(val id: Int) : MainDialog
    data class Options(val id: Int) : MainDialog
    data class Delete(val id: Int) : MainDialog
    data object ClearAll : MainDialog
}

/** Current wall-clock time, refreshed several times per second for the countdowns. */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(200)
        }
    }
    return now
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(state: AppState, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val now = rememberNow()
    var dialog by remember { mutableStateOf<MainDialog?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cooking Clock") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                actions = {
                    IconButton(onClick = { ClockController.setKeepScreenOn(context, !state.keepScreenOn) }) {
                        Icon(
                            painterResource(if (state.keepScreenOn) R.drawable.ic_screen_on else R.drawable.ic_screen_off),
                            contentDescription = if (state.keepScreenOn) "Keep screen on: enabled" else "Keep screen on: disabled",
                            tint = if (state.keepScreenOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { dialog = MainDialog.ClearAll }, enabled = state.clocks.isNotEmpty()) {
                        Text("Clear all")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { dialog = MainDialog.NewClock },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add clock") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val ringing = state.clocks.filter { it.state == ClockState.RINGING }
            if (ringing.isNotEmpty()) {
                RingingBanner(ringing.map { it.name }) { ClockController.stopAllRinging(context) }
            }
            if (state.clocks.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No clocks yet. Tap \"Add clock\" to start one.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.clocks, key = { it.id }) { clock ->
                        ClockRow(
                            clock = clock,
                            now = now,
                            onName = { dialog = MainDialog.Rename(clock.id) },
                            onTime = { dialog = MainDialog.Time(clock.id) },
                            onToggle = { ClockController.toggle(context, clock.id) },
                            onReset = { ClockController.reset(context, clock.id) },
                            onDelete = { dialog = MainDialog.Delete(clock.id) },
                            onOptions = { dialog = MainDialog.Options(clock.id) },
                        )
                    }
                }
            }
        }
    }

    val close = { dialog = null }
    fun clock(id: Int) = state.clocks.firstOrNull { it.id == id }
    when (val d = dialog) {
        null -> Unit
        MainDialog.NewClock -> NameDialog(
            title = "New clock",
            initial = "",
            names = state.names,
            onPick = { name -> ClockController.addClock(context, name); close() },
            onDismiss = close,
        )
        is MainDialog.Rename -> clock(d.id)?.let { c ->
            NameDialog(
                title = "Clock name",
                initial = c.name,
                names = state.names,
                onPick = { name -> ClockController.rename(context, c.id, name); close() },
                onDismiss = close,
            )
        } ?: close()
        is MainDialog.Time -> clock(d.id)?.let { c -> TimeDialog(c, now, onDismiss = close) } ?: close()
        is MainDialog.Options -> clock(d.id)?.let { c -> ClockOptionsDialog(c, state.profiles, onDismiss = close) } ?: close()
        is MainDialog.Delete -> clock(d.id)?.let { c ->
            ConfirmDialog(
                title = "Delete clock?",
                text = "The clock \"${c.name}\" will be deleted.",
                confirmLabel = "Delete",
                onConfirm = { ClockController.delete(context, c.id); close() },
                onDismiss = close,
            )
        } ?: close()
        MainDialog.ClearAll -> ConfirmDialog(
            title = "Clear all clocks?",
            text = "All ${state.clocks.size} clocks will be deleted.",
            confirmLabel = "Clear all",
            onConfirm = { ClockController.clearAll(context); close() },
            onDismiss = close,
        )
    }
}

@Composable
private fun RingingBanner(names: List<String>, onStop: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Time's up!", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(names.joinToString(", "))
            }
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("Stop") }
        }
    }
}

@Composable
private fun ClockRow(
    clock: CookingClock,
    now: Long,
    onName: () -> Unit,
    onTime: () -> Unit,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
    onOptions: () -> Unit,
) {
    val ringing = clock.state == ClockState.RINGING
    val container = when {
        ringing -> MaterialTheme.colorScheme.errorContainer
        clock.color != null -> Color(clock.color)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = Color(0xFF201A17)),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                clock.name,
                modifier = Modifier.weight(1f).clickable(onClick = onName).padding(vertical = 12.dp),
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val timeColor = when (clock.state) {
                ClockState.RUNNING -> MaterialTheme.colorScheme.primary
                ClockState.RINGING -> MaterialTheme.colorScheme.error
                else -> Color(0xFF52443C)
            }
            Text(
                if (ringing) "00:00" else formatDuration(clock.remainingAt(now)),
                modifier = Modifier
                    .clickable(onClick = onTime)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                fontSize = 24.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = timeColor,
            )
            IconButton(onClick = onToggle, enabled = clock.state != ClockState.FINISHED && (clock.remainingMs > 0 || clock.state != ClockState.IDLE)) {
                when (clock.state) {
                    ClockState.RUNNING -> Icon(painterResource(R.drawable.ic_pause), contentDescription = "Pause")
                    ClockState.RINGING -> Icon(painterResource(R.drawable.ic_stop), contentDescription = "Stop alarm")
                    else -> Icon(Icons.Default.PlayArrow, contentDescription = "Start")
                }
            }
            Box {
                var menuOpen by remember { mutableStateOf(false) }
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Reset") },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        onClick = { menuOpen = false; onReset() },
                    )
                    DropdownMenuItem(
                        text = { Text("Color and alarm") },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_palette), contentDescription = null) },
                        onClick = { menuOpen = false; onOptions() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}
