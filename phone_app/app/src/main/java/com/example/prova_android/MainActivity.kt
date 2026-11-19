package com.example.prova_android

import android.Manifest
import android.app.Application
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.prova_android.ui.theme.Prova_androidTheme

class BleViewModel(application: Application) : AndroidViewModel(application) {
    val bleManager = Esp32BleManager(application)

    override fun onCleared() {
        bleManager.close()
        super.onCleared()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val bleViewModel = ViewModelProvider(this)[BleViewModel::class.java]
        setContent {
            Prova_androidTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BleControlScreen(
                        bleManager = bleViewModel.bleManager,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun BleControlScreen(
    bleManager: Esp32BleManager,
    modifier: Modifier = Modifier
) {
    var textToSend by remember { mutableStateOf("LED_ON") }

    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            bleManager.startScanAndConnect()
        } else {
            bleManager.onPermissionsDenied()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "ESP32 BLE Controller",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = "Status: ${bleManager.statusText}",
            style = MaterialTheme.typography.bodyMedium
        )

        if (bleManager.lastMessage.isNotEmpty()) {
            Text(
                text = "Last received: ${bleManager.lastMessage}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                bleManager.isConnected -> {
                    OutlinedButton(onClick = { bleManager.disconnect() }) {
                        Text("Disconnect")
                    }
                }
                bleManager.isScanning -> {
                    OutlinedButton(onClick = { bleManager.stopScan() }) {
                        Text("Stop Scan")
                    }
                }
                else -> {
                    Button(onClick = { permissionLauncher.launch(requiredPermissions) }) {
                        Text("Scan & Connect")
                    }
                }
            }
        }

        FitnessDashboard(data = bleManager.fitnessData)

        HorizontalDivider()

        OutlinedTextField(
            value = textToSend,
            onValueChange = { textToSend = it },
            label = { Text("Message to ESP32") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = { bleManager.sendText(textToSend) },
            enabled = bleManager.isConnected
        ) {
            Text("Send to ESP32")
        }
    }
}

@Composable
fun BpmCard(bpm: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = "Heart Rate", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$bpm BPM",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
fun StepsCard(steps: Int, goal: Int) {
    val progress = if (goal > 0) (steps.toFloat() / goal.toFloat()).coerceIn(0f, 1f) else 0f

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = "Daily Steps", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$steps / $goal",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
            )
        }
    }
}

@Composable
fun FitnessDashboard(data: FitnessData) {
    var selectedMode by remember { mutableStateOf(DisplayMode.ALL) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DisplayMode.entries.forEach { mode ->
                FilterChip(
                    selected = selectedMode == mode,
                    onClick = { selectedMode = mode },
                    label = { Text(mode.name.replace("_", " ")) }
                )
            }
        }

        when (selectedMode) {
            DisplayMode.ALL -> {
                BpmCard(bpm = data.bpm)
                StepsCard(steps = data.steps, goal = data.stepGoal)
            }
            DisplayMode.BPM_ONLY -> BpmCard(bpm = data.bpm)
            DisplayMode.STEPS_ONLY -> StepsCard(steps = data.steps, goal = data.stepGoal)
        }
    }
}
