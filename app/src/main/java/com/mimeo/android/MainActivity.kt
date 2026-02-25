package com.mimeo.android

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.mimeo.android.data.AppDatabase
import com.mimeo.android.data.SettingsStore
import com.mimeo.android.model.AppSettings
import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.repository.ItemTextResult
import com.mimeo.android.repository.NowPlayingSession
import com.mimeo.android.repository.PlaybackRepository
import com.mimeo.android.repository.ProgressPostResult
import com.mimeo.android.ui.player.PlayerScreen
import com.mimeo.android.ui.queue.QueueScreen
import com.mimeo.android.work.WorkScheduler
import java.io.IOException
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
    private val repository = PlaybackRepository(
        apiClient = apiClient,
        database = AppDatabase.getInstance(application.applicationContext),
        appContext = application.applicationContext,
    )

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _queueItems = MutableStateFlow<List<PlaybackQueueItem>>(emptyList())
    val queueItems: StateFlow<List<PlaybackQueueItem>> = _queueItems.asStateFlow()

    private val _queueLoading = MutableStateFlow(false)
    val queueLoading: StateFlow<Boolean> = _queueLoading.asStateFlow()

    private val _queueOffline = MutableStateFlow(false)
    val queueOffline: StateFlow<Boolean> = _queueOffline.asStateFlow()

    private val _pendingProgressCount = MutableStateFlow(0)
    val pendingProgressCount: StateFlow<Int> = _pendingProgressCount.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _segmentIndexByItem = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val segmentIndexByItem: StateFlow<Map<Int, Int>> = _segmentIndexByItem.asStateFlow()

    private val _nowPlayingSession = MutableStateFlow<NowPlayingSession?>(null)
    val nowPlayingSession: StateFlow<NowPlayingSession?> = _nowPlayingSession.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.settingsFlow.collect { _settings.value = it }
        }
        WorkScheduler.enqueueProgressSync(application.applicationContext)
        viewModelScope.launch {
            refreshPendingCount()
            flushPendingProgress()
        }
        viewModelScope.launch {
            _nowPlayingSession.value = repository.getSession()
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
                _queueOffline.value = false
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
                val queue = repository.loadQueueAndPrefetch(current.baseUrl, current.apiToken)
                _queueItems.value = queue.items
                _queueOffline.value = false
                _statusMessage.value = "Queue loaded (${queue.count})"
                flushPendingProgress()
            } catch (e: ApiException) {
                _queueOffline.value = false
                _statusMessage.value = if (e.statusCode == 401) "Unauthorized-check token" else e.message
            } catch (e: Exception) {
                _queueOffline.value = isNetworkError(e)
                _statusMessage.value = e.message ?: "Failed to load queue"
            } finally {
                _queueLoading.value = false
                refreshPendingCount()
            }
        }
    }

    fun flushPendingProgress() {
        val current = settings.value
        if (current.apiToken.isBlank()) return
        viewModelScope.launch {
            try {
                val syncResult = repository.flushPendingProgress(current.baseUrl, current.apiToken)
                if (syncResult.flushedCount > 0) {
                    _statusMessage.value = "Synced ${syncResult.flushedCount} queued updates"
                }
                _queueOffline.value = false
                if (syncResult.retryableFailures > 0 && syncResult.pendingCount > 0) {
                    WorkScheduler.enqueueProgressSync(getApplication<Application>().applicationContext)
                }
            } catch (e: Exception) {
                if (isNetworkError(e)) {
                    _queueOffline.value = true
                }
            } finally {
                refreshPendingCount()
            }
        }
    }

    suspend fun fetchItemText(itemId: Int): Result<ItemTextResult> {
        val current = settings.value
        val expectedVersion = expectedActiveVersionFor(itemId)
        return runCatching {
            val loaded = repository.getItemText(current.baseUrl, current.apiToken, itemId, expectedVersion)
            if (loaded.usingCache) {
                _queueOffline.value = true
            } else {
                _queueOffline.value = false
            }
            loaded
        }
    }

    suspend fun postProgress(itemId: Int, percent: Int): Result<ProgressPostResult> {
        val current = settings.value
        return runCatching {
            val result = repository.postProgress(current.baseUrl, current.apiToken, itemId, percent.coerceIn(0, 100))
            if (result.queued) {
                _queueOffline.value = true
            }
            refreshPendingCount()
            result
        }
    }

    suspend fun resolveInitialPlayerItemId(fallbackItemId: Int): Int {
        val existing = nowPlayingSession.value
        val existingItem = existing?.currentItem
        if (existingItem != null) {
            return existingItem.itemId
        }
        val queue = queueItems.value
        if (queue.isNotEmpty()) {
            val session = repository.startSession(queue, fallbackItemId)
            _nowPlayingSession.value = session
            return session.currentItem?.itemId ?: fallbackItemId
        }
        return fallbackItemId
    }

    fun startNowPlayingSession(startItemId: Int) {
        val queue = queueItems.value
        if (queue.isEmpty()) {
            return
        }
        viewModelScope.launch {
            _nowPlayingSession.value = repository.startSession(queue, startItemId)
        }
    }

    fun currentNowPlayingItemId(): Int? = nowPlayingSession.value?.currentItem?.itemId

    suspend fun setNowPlayingCurrentItem(itemId: Int) {
        val session = nowPlayingSession.value ?: return
        val idx = session.items.indexOfFirst { it.itemId == itemId }
        if (idx < 0) return
        _nowPlayingSession.value = repository.setCurrentIndex(idx) ?: session.copy(currentIndex = idx)
    }

    suspend fun nextSessionItemId(currentId: Int): Int? {
        val session = getOrCreateNowPlayingSession(currentId) ?: return null
        val idx = session.items.indexOfFirst { it.itemId == currentId }.let { if (it >= 0) it else session.currentIndex }
        if (idx >= session.items.lastIndex) return null
        val nextIndex = idx + 1
        val updated = repository.setCurrentIndex(nextIndex) ?: session.copy(currentIndex = nextIndex)
        _nowPlayingSession.value = updated
        return updated.currentItem?.itemId
    }

    suspend fun prevSessionItemId(currentId: Int): Int? {
        val session = getOrCreateNowPlayingSession(currentId) ?: return null
        val idx = session.items.indexOfFirst { it.itemId == currentId }.let { if (it >= 0) it else session.currentIndex }
        if (idx <= 0) return null
        val prevIndex = idx - 1
        val updated = repository.setCurrentIndex(prevIndex) ?: session.copy(currentIndex = prevIndex)
        _nowPlayingSession.value = updated
        return updated.currentItem?.itemId
    }

    private suspend fun getOrCreateNowPlayingSession(currentId: Int): NowPlayingSession? {
        nowPlayingSession.value?.let { return it }
        val queue = queueItems.value
        if (queue.isEmpty()) return null
        val session = repository.startSession(queue, currentId)
        _nowPlayingSession.value = session
        return session
    }

    fun getSegmentIndex(itemId: Int): Int = segmentIndexByItem.value[itemId] ?: 0

    fun setSegmentIndex(itemId: Int, segmentIndex: Int) {
        val clamped = segmentIndex.coerceAtLeast(0)
        _segmentIndexByItem.update { previous ->
            previous + (itemId to clamped)
        }
    }

    private suspend fun refreshPendingCount() {
        _pendingProgressCount.value = repository.countPendingProgress()
    }

    private fun expectedActiveVersionFor(itemId: Int): Int? {
        val fromSession = nowPlayingSession.value?.items?.firstOrNull { it.itemId == itemId }?.activeContentVersionId
        if (fromSession != null) return fromSession
        return queueItems.value.firstOrNull { it.itemId == itemId }?.activeContentVersionId
    }

    private fun isNetworkError(error: Exception): Boolean = error is IOException
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
