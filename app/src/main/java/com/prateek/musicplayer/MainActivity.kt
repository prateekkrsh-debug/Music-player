package com.prateek.musicplayer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class MainActivity : ComponentActivity() {
    private var hasAudioPermission by mutableStateOf(false)
    private var songs by mutableStateOf(emptyList<Song>())
    private var excludedFolders by mutableStateOf(setOf<String>())

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasAudioPermission = granted
        if (granted) songs = scanSongs(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        excludedFolders = getPreferences(MODE_PRIVATE)
            .getStringSet("excluded_folders", emptySet())
            .orEmpty()
        hasAudioPermission = hasPermission()
        if (hasAudioPermission) songs = scanSongs(this)

        setContent {
            MaterialTheme {
                val player = remember {
                    ExoPlayer.Builder(this).build()
                }
                DisposableEffect(Unit) {
                    onDispose { player.release() }
                }
                PlayerScreen(
                    hasPermission = hasAudioPermission,
                    songs = songs,
                    excludedFolders = excludedFolders,
                    player = player,
                    onAskPermission = { permissionLauncher.launch(permissionName()) },
                    onRescan = { if (hasAudioPermission) songs = scanSongs(this) },
                    onToggleFolder = { folder, include ->
                        excludedFolders = if (include) {
                            excludedFolders - folder
                        } else {
                            excludedFolders + folder
                        }
                        getPreferences(MODE_PRIVATE).edit()
                            .putStringSet("excluded_folders", excludedFolders)
                            .apply()
                    },
                )
            }
        }
    }

    private fun permissionName(): String {
        return if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    private fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, permissionName()) ==
            PackageManager.PERMISSION_GRANTED
    }
}

@Composable
private fun PlayerScreen(
    hasPermission: Boolean,
    songs: List<Song>,
    excludedFolders: Set<String>,
    player: ExoPlayer,
    onAskPermission: () -> Unit,
    onRescan: () -> Unit,
    onToggleFolder: (String, Boolean) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var showFolders by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf<Song?>(null) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableFloatStateOf(0f) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val visible = songs.filter { song ->
        song.folder !in excludedFolders &&
            (query.isBlank() ||
                song.title.contains(query, ignoreCase = true) ||
                song.artist.contains(query, ignoreCase = true))
    }
    val folders = songs.map { it.folder }.distinct().sorted()

    fun playSong(song: Song) {
        current = song
        player.setMediaItem(MediaItem.fromUri(song.uri))
        player.prepare()
        player.play()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Music Player", style = MaterialTheme.typography.headlineSmall)
            if (!hasPermission) {
                Text("Allow music access so the app can find MP3 files on this phone.")
                Button(onClick = onAskPermission) { Text("Allow music access") }
                return@Column
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search songs") },
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showFolders = !showFolders }) {
                    Text(if (showFolders) "Hide folders" else "Include / exclude folders")
                }
                TextButton(onClick = onRescan) { Text("Rescan") }
            }
            if (showFolders) {
                Text("Uncheck a folder to hide its songs.")
                folders.forEach { folder ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = folder !in excludedFolders,
                            onCheckedChange = { checked -> onToggleFolder(folder, checked) },
                        )
                        Text(folder)
                    }
                }
            }
            Text("${visible.size} songs")
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(visible, key = { it.id }) { song ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { playSong(song) }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(song.title)
                        Text("${song.artist} · ${song.folder}")
                    }
                    HorizontalDivider()
                }
            }
            current?.let { song ->
                Text(song.title)
                Slider(
                    value = position,
                    onValueChange = { position = it },
                    onValueChangeFinished = {
                        val duration = player.duration.coerceAtLeast(1L)
                        player.seekTo((duration * position).toLong())
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val index = visible.indexOfFirst { it.id == song.id }
                        if (index > 0) playSong(visible[index - 1])
                    }) { Text("Previous") }
                    TextButton(onClick = {
                        if (player.isPlaying) player.pause() else player.play()
                    }) { Text(if (playing) "Pause" else "Play") }
                    TextButton(onClick = {
                        val index = visible.indexOfFirst { it.id == song.id }
                        if (index >= 0 && index < visible.lastIndex) playSong(visible[index + 1])
                    }) { Text("Next") }
                }
            }
        }
    }
}
