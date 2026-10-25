package com.archiekuo.localmusic

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val vm: LibraryViewModel by viewModels()
    private lateinit var conn: PlayerConnection
    private var pendingDelete: Set<Long> = emptySet()

    private val deleteLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
            if (r.resultCode == Activity.RESULT_OK) {
                val cur = conn.currentId
                if (cur != null && cur in pendingDelete) conn.stopAndClear()
                vm.removeSongs(pendingDelete)
                Toast.makeText(this, "已刪除 ${pendingDelete.size} 首", Toast.LENGTH_SHORT).show()
            }
            pendingDelete = emptySet()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        conn = PlayerConnection(applicationContext)
        conn.connect()
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
        setContent {
            AppTheme {
                App(vm, conn, ::requestDelete, version)
            }
        }
    }

    /** 真的刪檔：Android 會再跳一次系統確認，這是系統規定 */
    private fun requestDelete(songs: List<Song>) {
        if (songs.isEmpty()) return
        pendingDelete = songs.map { it.id }.toSet()
        try {
            val sender = MediaStore.createDeleteRequest(contentResolver, songs.map { it.uri }).intentSender
            deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
        } catch (e: Exception) {
            pendingDelete = emptySet()
            Toast.makeText(this, "沒辦法刪除：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        conn.release()
        super.onDestroy()
    }
}

@Composable
fun App(
    vm: LibraryViewModel,
    conn: PlayerConnection,
    onDelete: (List<Song>) -> Unit,
    version: String,
) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    val audioPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    fun hasPerm() = ContextCompat.checkSelfPermission(ctx, audioPerm) == PackageManager.PERMISSION_GRANTED

    var granted by remember { mutableStateOf(hasPerm()) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasPerm()
    }

    var tab by rememberSaveable { mutableStateOf(0) }
    var filter by rememberSaveable { mutableStateOf("all") }
    var selecting by remember { mutableStateOf(false) }
    var showPlayer by remember { mutableStateOf(false) }
    var showTidy by remember { mutableStateOf(false) }
    var showFolders by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(false) }

    LaunchedEffect(granted) {
        if (granted) vm.refresh(ctx)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = hasPerm()
        if (granted && vm.scanned) vm.refresh(ctx)
    }

    // 播放時每半秒更新一次進度
    LaunchedEffect(conn.isPlaying) {
        while (conn.isPlaying) {
            conn.refreshPosition()
            delay(500)
        }
    }

    // 播滿一段時間才算聽過一次（常聽的記憶）
    LaunchedEffect(conn.currentId) {
        val id = conn.currentId ?: return@LaunchedEffect
        var played = 0L
        while (true) {
            delay(1000)
            if (conn.isPlaying) {
                played += 1000
                val need = minOf(30_000L, (conn.durationMs * 8 / 10).coerceAtLeast(5_000L))
                if (played >= need) {
                    vm.addPlay(id)
                    break
                }
            }
        }
    }

    BackHandler(enabled = showPlayer) { showPlayer = false }
    // 整理畫面自己有 BackHandler
    BackHandler(enabled = selecting && !showPlayer) { selecting = false }

    val ready = granted && vm.scanned && !vm.scanning && vm.onboarded
    val current = vm.songs.firstOrNull { it.id == conn.currentId }

    Box(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .systemBarsPadding()
            .imePadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
        CreditBar(version)
        Box(Modifier.weight(1f).fillMaxWidth()) {
        when {
            !granted -> PermissionScreen(version) {
                val perms = if (Build.VERSION.SDK_INT >= 33) {
                    arrayOf(audioPerm, Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    arrayOf(audioPerm)
                }
                permLauncher.launch(perms)
            }

            vm.scanning || !vm.scanned -> ScanningScreen(version)

            !vm.onboarded -> ScanDoneScreen(
                vm = vm,
                version = version,
                onEnter = { vm.markOnboarded() },
                onTidy = {
                    vm.markOnboarded()
                    showTidy = true
                },
            )

            else -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        0 -> HomeScreen(
                            vm = vm,
                            conn = conn,
                            filter = filter,
                            onFilter = { filter = it },
                            selecting = selecting,
                            onSelecting = { selecting = it },
                            onDelete = onDelete,
                            onFolders = { showFolders = true },
                        )

                        1 -> CategoriesScreen(
                            vm = vm,
                            onOpen = { id ->
                                filter = id
                                tab = 0
                            },
                            onManage = { tab = 2 },
                            onTidy = { showTidy = true },
                        )

                        else -> ManageScreen(vm) { showHidden = true }
                    }
                }
                if (!selecting) {
                    if (current != null) MiniPlayer(conn, current) { showPlayer = true }
                    BottomNav(tab) {
                        tab = it
                        selecting = false
                    }
                }
            }
        }

        if (showFolders && ready) {
            FolderScreen(vm, conn) { showFolders = false }
        }
        if (showHidden && ready) {
            HiddenScreen(vm) { showHidden = false }
        }
        if (showTidy && ready) {
            TidyScreen(vm) { showTidy = false }
        }
        if (showPlayer && ready) {
            PlayerScreen(vm, conn, version) { showPlayer = false }
        }
        }
        }
    }
}
