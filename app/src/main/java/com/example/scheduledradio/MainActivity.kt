package com.example.scheduledradio

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        RadioScheduler.scheduleAll(this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RadioSchedulerScreen()
                }
            }
        }
    }
}

@Composable
fun RadioSchedulerScreen() {
    val context = LocalContext.current
    val prefs = remember { RadioPreferences(context) }

    var streamUrl by remember { mutableStateOf(prefs.streamUrl) }
    var isEnabled by remember { mutableStateOf(prefs.isScheduleEnabled) }
    var volumeLevel by remember { mutableFloatStateOf(prefs.volumeLevel) }
    var isMuted by remember { mutableStateOf(prefs.isMuted) }
    var statusMessage by remember { mutableStateOf("Alarms scheduled for 6:25 AM & 6:15 PM") }

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        alarmManager.canScheduleExactAlarms()
    } else true

    val isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)

    fun syncAudioWithService() {
        val intent = Intent(context, RadioPlaybackService::class.java).apply {
            action = RadioPlaybackService.CMD_UPDATE_AUDIO
        }
        context.startService(intent)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Sarath's Radio",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        if (!canScheduleExact || !isIgnoringBatteryOptimizations) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Action Required for Reliable Playback",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    if (!canScheduleExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Button(onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }) {
                            Text("Allow Exact Alarms")
                        }
                    }
                    if (!isIgnoringBatteryOptimizations) {
                        Button(onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }) {
                            Text("Disable Battery Optimization")
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = streamUrl,
            onValueChange = {
                streamUrl = it
                prefs.streamUrl = it
            },
            label = { Text("Direct Stream URL (.mp3, .aac, .m3u8)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Daily Schedule", style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { checked ->
                            isEnabled = checked
                            prefs.isScheduleEnabled = checked
                            if (checked) {
                                RadioScheduler.scheduleAll(context)
                                statusMessage = "Daily alarms active"
                            } else {
                                RadioScheduler.cancelAll(context)
                                statusMessage = "Alarms paused"
                            }
                        }
                    )
                }

                HorizontalDivider()
                Text("• Morning Slot: 06:25 AM – 06:55 AM (30 mins)")
                Text("• Evening Slot: 06:15 PM – 06:30 PM (15 mins)")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Audio Controls", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (isMuted) "MUTED" else "${(volumeLevel * 100).roundToInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }

                LinearProgressIndicator(
                    progress = { if (isMuted) 0f else volumeLevel },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val newVol = (volumeLevel - 0.1f).coerceIn(0f, 1f)
                            volumeLevel = newVol
                            prefs.volumeLevel = newVol
                            if (newVol == 0f) {
                                isMuted = true
                                prefs.isMuted = true
                            }
                            syncAudioWithService()
                        }
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Volume Down")
                        Spacer(Modifier.width(4.dp))
                        Text("Vol -")
                    }

                    Button(
                        modifier = Modifier.weight(1.1f),
                        colors = if (isMuted) {
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        } else {
                            ButtonDefaults.buttonColors()
                        },
                        onClick = {
                            isMuted = !isMuted
                            prefs.isMuted = isMuted
                            syncAudioWithService()
                        }
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = if (isMuted) "Unmute" else "Mute"
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (isMuted) "Unmute" else "Mute")
                    }

                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val newVol = (volumeLevel + 0.1f).coerceIn(0f, 1f)
                            volumeLevel = newVol
                            prefs.volumeLevel = newVol
                            if (isMuted && newVol > 0f) {
                                isMuted = false
                                prefs.isMuted = false
                            }
                            syncAudioWithService()
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Volume Up")
                        Spacer(Modifier.width(4.dp))
                        Text("Vol +")
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    val intent = Intent(context, RadioPlaybackService::class.java).apply {
                        action = RadioPlaybackService.CMD_PLAY
                        putExtra(RadioPlaybackService.EXTRA_URL, streamUrl)
                    }
                    ContextCompat.startForegroundService(context, intent)
                    statusMessage = "Testing stream..."
                }
            ) {
                Text("Test Play")
            }

            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    val intent = Intent(context, RadioPlaybackService::class.java).apply {
                        action = RadioPlaybackService.CMD_STOP
                    }
                    context.startService(intent)
                    statusMessage = "Stream stopped"
                }
            ) {
                Text("Stop Now")
            }
        }

        Text(
            text = "Status: $statusMessage",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
