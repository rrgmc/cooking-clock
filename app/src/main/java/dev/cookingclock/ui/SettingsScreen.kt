package dev.cookingclock.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import dev.cookingclock.data.AlarmProfile
import dev.cookingclock.data.AppState
import dev.cookingclock.data.ClockController

fun ringtoneTitle(context: Context, uri: String?): String {
    if (uri == null) return "Silent"
    return runCatching { RingtoneManager.getRingtone(context, Uri.parse(uri))?.getTitle(context) }.getOrNull()
        ?: "Unknown sound"
}

/** Lists the alarm profiles. Each profile has a sound and a vibrate setting. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: AppState, onBack: () -> Unit) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<AlarmProfile?>(null) }
    var deleting by remember { mutableStateOf<AlarmProfile?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alarm profiles") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editing = AlarmProfile(
                        id = ClockController.newProfileId(),
                        name = "",
                        soundUri = AppState.defaultProfile().soundUri,
                        vibrate = true,
                    )
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add profile") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(state.profiles, key = { it.id }) { p ->
                val isDefault = p.id == AppState.DEFAULT_PROFILE_ID
                Card(Modifier.fillMaxWidth().clickable { editing = p }) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name + if (isDefault) " (default)" else "", fontWeight = FontWeight.Bold)
                            Text(
                                ringtoneTitle(context, p.soundUri) + if (p.vibrate) ", vibrate" else "",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (!isDefault) {
                            IconButton(onClick = { deleting = p }) { Icon(Icons.Default.Delete, contentDescription = "Delete profile") }
                        }
                    }
                }
            }
        }
    }

    editing?.let { p ->
        ProfileDialog(
            profile = p,
            onSave = { ClockController.saveProfile(context, it); editing = null },
            onDismiss = { editing = null },
        )
    }
    deleting?.let { p ->
        val users = state.clocks.count { it.profileId == p.id }
        ConfirmDialog(
            title = "Delete profile?",
            text = "The profile \"${p.name}\" will be deleted." +
                if (users > 0) " $users clock(s) using it will switch to the default profile." else "",
            confirmLabel = "Delete",
            onConfirm = { ClockController.deleteProfile(context, p.id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun ProfileDialog(profile: AlarmProfile, onSave: (AlarmProfile) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(profile.name) }
    var sound by remember { mutableStateOf(profile.soundUri) }
    var vibrate by remember { mutableStateOf(profile.vibrate) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.let {
                IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            sound = uri?.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (profile.name.isEmpty()) "New profile" else "Edit profile") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(12.dp))
                Text("Sound", style = MaterialTheme.typography.titleSmall)
                OutlinedButton(
                    onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE or RingtoneManager.TYPE_NOTIFICATION)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, sound?.let { Uri.parse(it) })
                        }
                        picker.launch(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(ringtoneTitle(context, sound)) }
                Spacer(Modifier.size(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Vibrate", Modifier.weight(1f))
                    Switch(checked = vibrate, onCheckedChange = { vibrate = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(profile.copy(name = name.trim(), soundUri = sound, vibrate = vibrate)) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
