package com.mimeo.android

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mimeo.android.data.ApiClient
import com.mimeo.android.data.ApiException
import com.mimeo.android.data.SettingsStore
import com.mimeo.android.model.AppSettings
import com.mimeo.android.model.ItemTextResponse
import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.ui.player.PlayerScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val vm = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application),
        )[AppViewModel::class.java]
        setContent {
            MaterialTheme {
                MimeoApp(vm)
            }
        }
    }
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsStore = SettingsStore(application.applicationContext)
    private val apiClient = ApiClient()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _queueItems = MutableStateFlow<List<PlaybackQueueItem>>(emptyList())
    val queueItems: StateFlow<List<PlaybackQueueItem>> = _queueItems.asStateFlow()

    private val _queueLoading = MutableStateFlow(false)
    val queueLoading: StateFlow<Boolean> = _queueLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _segmentIndexByItem = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val segmentIndexByItem: StateFlow<Map<Int, Int>> = _segmentIndexByItem.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.settingsFlow.collect { _settings.value = it }
        }
    }

    fun saveSettings(baseUrl: String, token: String) {
        viewModelScope.launch {
            settingsStore.save(baseUrl, token)
            _statusMessage.value = "Settings saved"
        }
    }

    fun testConnection() {
        val current = settings.value
        if (current.apiToken.isBlank()) {
            _statusMessage.value = "Token required"
            return
        }
        viewModelScope.launch {
            try {
                val version = apiClient.getDebugVersion(current.baseUrl, current.apiToken)
                _statusMessage.value = "Connected git_sha=${version.gitSha ?: "unknown"}"
            } catch (e: ApiException) {
                _statusMessage.value = if (e.statusCode == 401) "Unauthorized-check token" else e.message
            } catch (e: Exception) {
                _statusMessage.value = e.message
            }
        }
    }

    fun loadQueue() {
        val current = settings.value
        if (current.apiToken.isBlank()) {
            _statusMessage.value = "Token required"
            return
        }
        viewModelScope.launch {
            _queueLoading.value = true
            try {
                val queue = apiClient.getQueue(current.baseUrl, current.apiToken)
                _queueItems.value = queue.items
                _statusMessage.value = "Queue loaded (${queue.count})"
            } catch (e: ApiException) {
                _statusMessage.value = if (e.statusCode == 401) "Unauthorized-check token" else e.message
            } catch (e: Exception) {
                _statusMessage.value = e.message
            } finally {
                _queueLoading.value = false
            }
        }
    }

    suspend fun fetchItemText(itemId: Int): Result<ItemTextResponse> {
        val current = settings.value
        return runCatching {
            apiClient.getItemText(current.baseUrl, current.apiToken, itemId)
        }
    }

    suspend fun postProgress(itemId: Int, percent: Int): Result<Unit> {
        val current = settings.value
        return runCatching {
            apiClient.postProgress(current.baseUrl, current.apiToken, itemId, percent.coerceIn(0, 100))
        }
    }

    fun nextItemId(currentId: Int): Int? {
        val idx = queueItems.value.indexOfFirst { it.itemId == currentId }
        if (idx < 0) return queueItems.value.firstOrNull()?.itemId
        return queueItems.value.getOrNull(idx + 1)?.itemId
    }

    fun getSegmentIndex(itemId: Int): Int = segmentIndexByItem.value[itemId] ?: 0

    fun setSegmentIndex(itemId: Int, segmentIndex: Int) {
        val clamped = segmentIndex.coerceAtLeast(0)
        _segmentIndexByItem.update { previous ->
            previous + (itemId to clamped)
        }
    }
}

@Composable
private fun MimeoApp(vm: AppViewModel) {
    val nav = rememberNavController()
    val status by vm.statusMessage.collectAsState()

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { nav.navigate("queue") }) { Text("Queue") }
                Button(onClick = { nav.navigate("settings") }) { Text("Settings") }
            }
            status?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = it)
            }
            Spacer(modifier = Modifier.height(8.dp))
            NavHost(navController = nav, startDestination = "queue", modifier = Modifier.fillMaxSize()) {
                composable("settings") {
                    SettingsScreen(vm = vm)
                }
                composable("queue") {
                    QueueScreen(vm = vm, onOpenPlayer = { itemId -> nav.navigate("player/$itemId") })
                }
                composable(
                    "player/{itemId}",
                    arguments = listOf(navArgument("itemId") { type = NavType.IntType }),
                ) { backStack ->
                    val itemId = backStack.arguments?.getInt("itemId") ?: return@composable
                    PlayerScreen(
                        vm = vm,
                        initialItemId = itemId,
                        onOpenItem = { nextId -> nav.navigate("player/$nextId") },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsState()
    var baseUrl by remember(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
    var token by remember(settings.apiToken) { mutableStateOf(settings.apiToken) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Base URL") },
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API Token") },
            visualTransformation = PasswordVisualTransformation(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.saveSettings(baseUrl, token) }) { Text("Save") }
            Button(onClick = {
                vm.saveSettings(baseUrl, token)
                vm.testConnection()
            }) { Text("Test connection") }
        }
        Text("Emulator default: http://10.0.2.2:8000")
    }
}

@Composable
private fun QueueScreen(vm: AppViewModel, onOpenPlayer: (Int) -> Unit) {
    val items by vm.queueItems.collectAsState()
    val loading by vm.queueLoading.collectAsState()

    LaunchedEffect(Unit) {
        vm.loadQueue()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { vm.loadQueue() }) { Text("Refresh queue") }
        if (loading) {
            CircularProgressIndicator()
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { item ->
                val title = item.title?.ifBlank { null } ?: item.url
                val progress = item.lastReadPercent ?: 0
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPlayer(item.itemId) }
                        .padding(8.dp),
                ) {
                    Text(text = title)
                    Text(text = "${item.host ?: ""} progress=$progress%")
                }
            }
        }
    }
}
