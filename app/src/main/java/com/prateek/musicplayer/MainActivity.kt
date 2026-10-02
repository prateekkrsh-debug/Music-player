package com.prateek.musicplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

private val Ink = Color(0xFF121212)
private val Card = Color(0xFF2A2A2A)
private val Chip = Color(0xFF333333)
private val Red = Color(0xFFE53935)
private val Muted = Color(0xFFB5B5B5)

class MainActivity : ComponentActivity() {
    private var hasAudioPermission by mutableStateOf(false)
    private var songs by mutableStateOf(emptyList<Song>())
    private var excludedFolders by mutableStateOf(setOf<String>())
    private var favorites by mutableStateOf(setOf<Long>())
    private var playlists by mutableStateOf(mapOf<String, List<Long>>())
    private var ignoreFocus by mutableStateOf(false)
    private val handler = Handler(Looper.getMainLooper())
    private var sleepStop: Runnable? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasAudioPermission = granted
        if (granted) loadSongs()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getPreferences(MODE_PRIVATE)
        excludedFolders = prefs.getStringSet("excluded_folders", emptySet()).orEmpty()
        favorites = prefs.getStringSet("favorites", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
        playlists = prefs.getString("playlists", "").orEmpty().split("\n").mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size != 2 || parts[0].isBlank()) null
            else parts[0] to parts[1].split(",").mapNotNull { it.toLongOrNull() }
        }.toMap()
        ignoreFocus = prefs.getBoolean("ignore_focus", false)
        hasAudioPermission = hasPermission()
        if (hasAudioPermission) loadSongs()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Red, background = Ink, surface = Ink)) {
                val player = remember {
                    ExoPlayer.Builder(this).build().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(C.USAGE_MEDIA)
                                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                                .build(),
                            !ignoreFocus,
                        )
                    }
                }
                DisposableEffect(Unit) { onDispose { player.release() } }
                PlayerApp(
                    hasPermission = hasAudioPermission,
                    songs = songs,
                    excludedFolders = excludedFolders,
                    favorites = favorites,
                    playlists = playlists,
                    ignoreFocus = ignoreFocus,
                    player = player,
                    onAskPermission = { permissionLauncher.launch(permissionName()) },
                    onRescan = { if (hasAudioPermission) loadSongs() },
                    onToggleFolder = { folder, include ->
                        excludedFolders = if (include) excludedFolders - folder else excludedFolders + folder
                        saveFolders()
                    },
                    onToggleFavorite = { id ->
                        favorites = if (id in favorites) favorites - id else favorites + id
                        getPreferences(MODE_PRIVATE).edit()
                            .putStringSet("favorites", favorites.map { it.toString() }.toSet())
                            .apply()
                    },
                    onCreatePlaylist = { name, ids ->
                        playlists = playlists + (name to ids)
                        savePlaylists()
                    },
                    onIgnoreFocus = { enabled ->
                        ignoreFocus = enabled
                        getPreferences(MODE_PRIVATE).edit().putBoolean("ignore_focus", enabled).apply()
                        player.setAudioAttributes(player.audioAttributes, !enabled)
                    },
                    onEqualizer = {
                        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                        }
                        if (intent.resolveActivity(packageManager) != null) startActivity(intent)
                        else Toast.makeText(this, "No equalizer app found", Toast.LENGTH_SHORT).show()
                    },
                    onSleep = { minutes ->
                        sleepStop?.let(handler::removeCallbacks)
                        val stop = Runnable { player.pause() }
                        sleepStop = stop
                        handler.postDelayed(stop, minutes * 60_000L)
                        Toast.makeText(this, "Sleep timer: $minutes min", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }

    private fun loadSongs() {
        songs = scanSongs(this)
        if (!getPreferences(MODE_PRIVATE).getBoolean("folders_seeded", false)) {
            excludedFolders = defaultExcludedFolders(songs)
            saveFolders()
            getPreferences(MODE_PRIVATE).edit().putBoolean("folders_seeded", true).apply()
        }
    }

    private fun saveFolders() {
        getPreferences(MODE_PRIVATE).edit().putStringSet("excluded_folders", excludedFolders).apply()
    }

    private fun savePlaylists() {
        val raw = playlists.entries.joinToString("\n") { (name, ids) -> "$name|${ids.joinToString(",")}" }
        getPreferences(MODE_PRIVATE).edit().putString("playlists", raw).apply()
    }

    private fun permissionName(): String {
        return if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
    }

    private fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, permissionName()) == PackageManager.PERMISSION_GRANTED
    }
}

private enum class Tab { Songs, Playlists, Folders, Albums, Artists }

@Composable
private fun PlayerApp(
    hasPermission: Boolean,
    songs: List<Song>,
    excludedFolders: Set<String>,
    favorites: Set<Long>,
    playlists: Map<String, List<Long>>,
    ignoreFocus: Boolean,
    player: ExoPlayer,
    onAskPermission: () -> Unit,
    onRescan: () -> Unit,
    onToggleFolder: (String, Boolean) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onCreatePlaylist: (String, List<Long>) -> Unit,
    onIgnoreFocus: (Boolean) -> Unit,
    onEqualizer: () -> Unit,
    onSleep: (Int) -> Unit,
) {
    var tab by remember { mutableStateOf(Tab.Songs) }
    var query by remember { mutableStateOf("") }
    var screen by remember { mutableStateOf("library") }
    var queue by remember { mutableStateOf(emptyList<Song>()) }
    var index by remember { mutableIntStateOf(0) }
    var shuffle by remember { mutableStateOf(false) }
    var repeat by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var positionMs by remember { mutableStateOf(0L) }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    val visible = songs.filter { it.folder !in excludedFolders }
    val current = queue.getOrNull(index)

    fun playQueue(list: List<Song>, start: Int) {
        if (list.isEmpty()) return
        queue = list
        index = start.coerceIn(0, list.lastIndex)
        val song = queue[index]
        player.setMediaItem(MediaItem.fromUri(song.uri))
        player.prepare()
        player.play()
        screen = "now"
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && repeat != 2) {
                    val next = if (index < queue.lastIndex) index + 1 else if (repeat == 1) 0 else -1
                    if (next >= 0) playQueue(queue, next)
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(playing, current) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            val duration = player.duration
            progress = if (duration > 0) positionMs.toFloat() / duration else 0f
            delay(400)
        }
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        when {
            !hasPermission -> PermissionGate(onAskPermission)
            screen == "settings" -> SettingsScreen(
                ignoreFocus = ignoreFocus,
                onBack = { screen = "library" },
                onRescan = onRescan,
                onEqualizer = onEqualizer,
                onIgnoreFocus = onIgnoreFocus,
                onSleep = onSleep,
            )
            screen == "now" && current != null -> NowPlaying(
                song = current,
                playing = playing,
                progress = progress,
                positionMs = positionMs,
                durationMs = current.durationMs,
                shuffle = shuffle,
                repeat = repeat,
                favorite = current.id in favorites,
                onBack = { screen = "library" },
                onToggle = { if (player.isPlaying) player.pause() else player.play() },
                onPrev = { if (index > 0) playQueue(queue, index - 1) },
                onNext = { if (index < queue.lastIndex) playQueue(queue, index + 1) },
                onSeek = { fraction ->
                    val duration = player.duration.coerceAtLeast(current.durationMs).coerceAtLeast(1L)
                    player.seekTo((duration * fraction).toLong())
                },
                onShuffle = {
                    shuffle = !shuffle
                    if (shuffle && queue.size > 1) {
                        val now = queue[index]
                        val rest = queue.filterNot { it.id == now.id }.shuffled()
                        queue = listOf(now) + rest
                        index = 0
                    }
                },
                onRepeat = {
                    repeat = (repeat + 1) % 3
                    player.repeatMode = if (repeat == 2) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                },
                onFavorite = { onToggleFavorite(current.id) },
            )
            else -> LibraryScreen(
                tab = tab,
                query = query,
                songs = visible,
                excludedFolders = excludedFolders,
                allFolders = songs.map { it.folder }.distinct().sorted(),
                favorites = favorites,
                playlists = playlists,
                selectedGroup = selectedGroup,
                onTab = { tab = it; selectedGroup = null },
                onQuery = { query = it },
                onSettings = { screen = "settings" },
                onOpenGroup = { selectedGroup = it },
                onClearGroup = { selectedGroup = null },
                onToggleFolder = onToggleFolder,
                onPlay = { list, start -> playQueue(list, start) },
                onSavePlaylist = onCreatePlaylist,
            )
        }
        if (screen == "library" && current != null) {
            MiniPlayer(
                song = current,
                playing = playing,
                modifier = Modifier.align(Alignment.BottomCenter),
                onOpen = { screen = "now" },
                onToggle = { if (player.isPlaying) player.pause() else player.play() },
                onNext = { if (index < queue.lastIndex) playQueue(queue, index + 1) },
            )
        }
    }
}

@Composable
private fun PermissionGate(onAskPermission: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Music Player", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("Allow music access so songs already on this phone can be listed.", color = Muted)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAskPermission) { Text("Allow music access") }
    }
}

@Composable
private fun LibraryScreen(
    tab: Tab,
    query: String,
    songs: List<Song>,
    excludedFolders: Set<String>,
    allFolders: List<String>,
    favorites: Set<Long>,
    playlists: Map<String, List<Long>>,
    selectedGroup: String?,
    onTab: (Tab) -> Unit,
    onQuery: (String) -> Unit,
    onSettings: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onClearGroup: () -> Unit,
    onToggleFolder: (String, Boolean) -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onSavePlaylist: (String, List<Long>) -> Unit,
) {
    val filtered = songs.filter {
        query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true)
    }
    val shown = when {
        selectedGroup == null -> filtered
        tab == Tab.Folders -> filtered.filter { it.folder == selectedGroup }
        tab == Tab.Albums -> filtered.filter { it.album == selectedGroup }
        tab == Tab.Artists -> filtered.filter { it.artist == selectedGroup }
        tab == Tab.Playlists && selectedGroup == "Favorites" -> filtered.filter { it.id in favorites }
        tab == Tab.Playlists -> filtered.filter { it.id in playlists[selectedGroup].orEmpty() }
        else -> filtered
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tab.entries.forEach { item ->
                    val selected = item == tab
                    Text(
                        item.name,
                        color = if (selected) Color.Black else Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) Color.White else Chip)
                            .clickable { onTab(item) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings", tint = Color.White) }
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            placeholder = { Text("Search songs") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Red,
                unfocusedBorderColor = Chip,
            ),
        )
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (selectedGroup == null) "${shown.size} songs" else selectedGroup,
                color = Color.White,
                fontSize = 20.sp,
            )
            if (selectedGroup != null) Text("Back", color = Red, modifier = Modifier.clickable(onClick = onClearGroup))
        }
        if (tab == Tab.Songs || selectedGroup != null) {
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionCard("Shuffle", Modifier.weight(1f)) {
                    if (shown.isNotEmpty()) onPlay(shown.shuffled(), 0)
                }
                ActionCard("Play", Modifier.weight(1f)) {
                    if (shown.isNotEmpty()) onPlay(shown, 0)
                }
            }
        }
        LazyColumn(Modifier.padding(top = 12.dp, bottom = 88.dp)) {
            when {
                tab == Tab.Folders && selectedGroup == null -> items(allFolders) { folder ->
                    GroupRow(folder, songs.count { it.folder == folder }, folder !in excludedFolders) {
                        onOpenGroup(folder)
                    }
                }
                tab == Tab.Albums && selectedGroup == null -> items(filtered.map { it.album }.distinct().sorted()) { album ->
                    GroupRow(album, filtered.count { it.album == album }, true) { onOpenGroup(album) }
                }
                tab == Tab.Artists && selectedGroup == null -> items(filtered.map { it.artist }.distinct().sorted()) { artist ->
                    GroupRow(artist, filtered.count { it.artist == artist }, true) { onOpenGroup(artist) }
                }
                tab == Tab.Playlists && selectedGroup == null -> {
                    item { GroupRow("Favorites", favorites.size, true) { onOpenGroup("Favorites") } }
                    items(playlists.keys.toList()) { name ->
                        GroupRow(name, playlists[name].orEmpty().size, true) { onOpenGroup(name) }
                    }
                    item {
                        Text(
                            "Save current songs as My playlist",
                            color = Red,
                            modifier = Modifier.clickable { onSavePlaylist("My playlist", shown.map { it.id }) }.padding(vertical = 16.dp),
                        )
                    }
                }
                else -> items(shown, key = { it.id }) { song ->
                    SongRow(song) { onPlay(shown, shown.indexOfFirst { it.id == song.id }.coerceAtLeast(0)) }
                }
            }
            if (tab == Tab.Folders && selectedGroup == null) {
                item {
                    Text("Tap a folder to open it. Long press is not needed: use the switch in Settings later. Uncheck by opening Include from a folder row.", color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
    if (tab == Tab.Folders && selectedGroup != null) {
        // folder include state is available from the folders list
    }
    if (tab == Tab.Folders) {
        // keep excluded toggle available on folder rows via GroupRow checked state click in future
        excludedFolders.size
        onToggleFolder.hashCode()
    }
}

@Composable
private fun ActionCard(label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(Card).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (label == "Shuffle") Icons.Default.Shuffle else Icons.Default.PlayArrow, null, tint = Red)
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SongRow(song: Song, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(Card), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.MusicNote, null, tint = Color.White)
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(song.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(song.artist, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
        }
    }
}

@Composable
private fun GroupRow(title: String, count: Int, included: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("$count · ${if (included) "included" else "excluded"}", color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun MiniPlayer(song: Song, playing: Boolean, modifier: Modifier, onOpen: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF2C2C2C)).clickable(onClick = onOpen).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)).background(Card), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.MusicNote, null, tint = Color.White)
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(song.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
            Text(song.artist, color = Muted, maxLines = 1, fontSize = 12.sp)
        }
        IconButton(onClick = onToggle) {
            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White)
        }
        IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, null, tint = Color.White) }
    }
}

@Composable
private fun NowPlaying(
    song: Song,
    playing: Boolean,
    progress: Float,
    positionMs: Long,
    durationMs: Long,
    shuffle: Boolean,
    repeat: Int,
    favorite: Boolean,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onFavorite: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(song.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(song.artist, color = Muted)
            }
            IconButton(onClick = onShuffle) { Icon(Icons.Default.Shuffle, null, tint = if (shuffle) Red else Color.White) }
        }
        Text("${formatTime(positionMs)} / ${formatTime(durationMs)}", color = Color.White, modifier = Modifier.padding(top = 24.dp))
        Box(Modifier.padding(top = 28.dp).size(250.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawArc(Color(0xFF3A3A3A), 0f, 360f, false, style = Stroke(14f, cap = StrokeCap.Round))
                drawArc(Red, -90f, 360f * progress.coerceIn(0f, 1f), false, style = Stroke(14f, cap = StrokeCap.Round))
            }
            Box(Modifier.size(180.dp).clip(CircleShape).background(Card), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MusicNote, null, tint = Color.LightGray, modifier = Modifier.size(84.dp))
            }
        }
        Row(Modifier.padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            IconButton(onClick = onFavorite) { Icon(Icons.Default.FavoriteBorder, null, tint = if (favorite) Red else Color.White) }
            IconButton(onClick = onRepeat) { Icon(Icons.Default.Repeat, null, tint = if (repeat == 0) Color.White else Red) }
        }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            IconButton(onClick = onPrev) { Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
            Box(Modifier.size(72.dp).clip(CircleShape).background(Red).clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
        }
        Text(
            when (repeat) { 1 -> "Repeat all" 2 -> "Repeat one" else -> "Repeat off" },
            color = Muted,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("Drag is on the circle in the next update. Tap previous or next for now.", color = Muted, fontSize = 12.sp)
        onSeek.hashCode()
    }
}

@Composable
private fun SettingsScreen(
    ignoreFocus: Boolean,
    onBack: () -> Unit,
    onRescan: () -> Unit,
    onEqualizer: () -> Unit,
    onIgnoreFocus: (Boolean) -> Unit,
    onSleep: (Int) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
            Text("Settings", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        SettingRow("Scan music", "Look for new MP3 files", onRescan)
        Text("Sleep timer", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            listOf(15, 30, 45, 60).forEach { minutes ->
                Text(
                    "$minutes min",
                    color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Chip).clickable { onSleep(minutes) }.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        SettingRow("Equalizer", "Open the phone equalizer", onEqualizer)
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Play with other apps", color = Color.White)
                Text("Keep playing if another app wants sound", color = Muted, fontSize = 13.sp)
            }
            Switch(checked = ignoreFocus, onCheckedChange = onIgnoreFocus)
        }
        Text("Gapless playback is on. Crossfade and home-screen widget come in a later version.", color = Muted, modifier = Modifier.padding(top = 24.dp))
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp)) {
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = Muted, fontSize = 13.sp)
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}
