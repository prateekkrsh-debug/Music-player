package com.prateek.musicplayer

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import com.prateek.musicplayer.data.HistoryEntity
import com.prateek.musicplayer.data.PlaylistEntity
import com.prateek.musicplayer.playback.PlaybackService
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PlayerRoot() }
    }
}

@Composable
private fun PlayerRoot() {
    val app = LocalContext.current.applicationContext as MusicApp
    val theme by app.themeMode.collectAsState(initial = "system")
    val amoled by app.amoled.collectAsState(initial = false)
    val dark = theme == "dark" || (theme == "system" && androidx.compose.foundation.isSystemInDarkTheme())
    val scheme = when {
        amoled && dark -> darkColorScheme(background = Color.Black, surface = Color.Black, primary = Color(0xFFE53935))
        dark -> darkColorScheme(primary = Color(0xFFE53935))
        else -> lightColorScheme(primary = Color(0xFFE53935))
    }
    MaterialTheme(colorScheme = scheme) { MusicShell() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicShell() {
    val context = LocalContext.current
    val app = context.applicationContext as MusicApp
    val scope = rememberCoroutineScope()
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var songs by remember { mutableStateOf(emptyList<Song>()) }
    var scanning by remember { mutableStateOf(false) }
    var fillingTags by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf("home") }
    var nowOpen by remember { mutableStateOf(false) }
    var playingSong by remember { mutableStateOf<Song?>(null) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Song?>(null) }
    var queueOpen by remember { mutableStateOf(false) }
    var playlistTarget by remember { mutableStateOf<Song?>(null) }
    var newPlaylist by remember { mutableStateOf("") }
    var progress by remember { mutableFloatStateOf(0f) }
    var position by remember { mutableStateOf(0L) }
    val excluded by app.excluded.collectAsState(initial = emptySet())
    val sort by app.sort.collectAsState(initial = "title")
    val sortAsc by app.sortAsc.collectAsState(initial = true)
    val pauseDisconnect by app.pauseOnDisconnect.collectAsState(initial = true)
    val welcomeDone by app.welcomeDone.collectAsState(initial = false)
    val favorites by app.database.dao().favorites().collectAsState(initial = emptyList())
    val playlists by app.database.dao().playlists().collectAsState(initial = emptyList())
    val recent by app.database.dao().recent().collectAsState(initial = emptyList())
    val most by app.database.dao().mostPlayed().collectAsState(initial = emptyList())
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
        if (it) scope.launch { app.setWelcomeDone() }
    }
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    DisposableEffect(Unit) {
        context.startService(Intent(context, PlaybackService::class.java))
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            controller = runCatching { future.get() }.getOrNull()
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            controller?.release()
            MediaController.releaseFuture(future)
        }
    }
    LaunchedEffect(permissionGranted) {
        if (!permissionGranted) return@LaunchedEffect
        scanning = true
        error = null
        songs = runCatching { scanSongs(context) }.getOrElse {
            error = it.message ?: "Library scan failed"
            emptyList()
        }
        scanning = false
        TagCache.load(context)
        fillMissingTags(context, songs) { fillingTags = it }
    }
    LaunchedEffect(controller) {
        val player = controller ?: return@LaunchedEffect
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            progress = if (player.duration > 0) position.toFloat() / player.duration else 0f
            delay(400)
        }
    }

    val library = remember(songs, excluded, sort, sortAsc) {
        songs.filter { it.folder !in excluded }.sortedWith(songComparator(sort, sortAsc))
    }
    val currentId = controller?.currentMediaItem?.mediaId?.toLongOrNull()
    val fromPlayer = library.firstOrNull { it.id == currentId } ?: songs.firstOrNull { it.id == currentId }
    val current = fromPlayer ?: playingSong

    fun play(list: List<Song>, start: Int) {
        val player = controller ?: return
        if (list.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val song = list[start.coerceIn(0, list.lastIndex)]
        playingSong = song
        nowOpen = true
        context.startService(Intent(context, PlaybackService::class.java))
        player.setMediaItems(list.map { it.toMediaItem() }, start.coerceIn(0, list.lastIndex), 0L)
        player.prepare()
        player.playWhenReady = true
        player.play()
        scope.launch {
            val old = app.database.dao().historyFor(song.id)
            app.database.dao().upsertHistory(
                HistoryEntity(song.id, System.currentTimeMillis(), (old?.playCount ?: 0) + 1),
            )
        }
    }

    if (!welcomeDone || !permissionGranted) {
        Welcome(permissionGranted) {
            permissionLauncher.launch(audioPermission())
        }
        return
    }

    Scaffold(
        bottomBar = {
            Column {
                current?.let {
                    MiniPlayer(it, controller?.isPlaying == true, { nowOpen = true }, { toggle(controller) }, { controller?.seekToNext() })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    NavItem("home", "Home", Icons.Default.Home, tab) { tab = it }
                    NavItem("songs", "Songs", Icons.Default.MusicNote, tab) { tab = it }
                    NavItem("playlists", "Soundkeep", Icons.Default.QueueMusic, tab) { tab = it }
                    NavItem("folders", "Folders", Icons.Default.Folder, tab) { tab = it }
                    NavItem("settings", "Settings", Icons.Default.Settings, tab) { tab = it }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                "home" -> HomeScreen(library, recent, most, favorites.toSet(), scanning, fillingTags, error, { play(library, library.indexOfFirst { song -> song.id == it.id }.coerceAtLeast(0)) }, { tab = it }, {
                    scope.launch {
                        TagCache.tags = emptyMap()
                        context.filesDir.resolve("music-tags.txt").delete()
                        fillMissingTags(context, songs, force = true) { fillingTags = it }
                    }
                })
                "songs" -> SongList(library, query, current?.id, { query = it }, { play(library, library.indexOfFirst { song -> song.id == it.id }.coerceAtLeast(0)) }, { selected = it })
                "playlists" -> SoundkeepPage()
                "folders" -> GroupScreen(library.groupBy { it.folder }, excluded, { play(it, 0) }, { folder, include ->
                    scope.launch {
                        app.setExcluded(if (include) excluded - folder else excluded + folder)
                    }
                })
                else -> SettingsScreen(
                    theme = app.themeMode.collectAsState(initial = "system").value,
                    amoled = app.amoled.collectAsState(initial = false).value,
                    sort = sort,
                    sortAsc = sortAsc,
                    pauseDisconnect = pauseDisconnect,
                    onTheme = { scope.launch { app.setTheme(it) } },
                    onAmoled = { scope.launch { app.setAmoled(it) } },
                    onSort = { scope.launch { app.setSort(it) } },
                    onSortAsc = { scope.launch { app.setSortAsc(it) } },
                    onPause = { scope.launch { app.setPauseOnDisconnect(it) } },
                    onRescan = {
                        scope.launch {
                            scanning = true
                            songs = scanSongs(context)
                            scanning = false
                        }
                    },
                    onEqualizer = { openEqualizer(context, controller) },
                    onSleep = { minutes ->
                        val player = controller ?: return@SettingsScreen
                        if (minutes == 0) {
                            player.sendCustomCommand(SessionCommand(PlaybackService.ACTION_SLEEP_END, Bundle.EMPTY), Bundle.EMPTY)
                        } else {
                            player.sendCustomCommand(
                                SessionCommand(PlaybackService.ACTION_SLEEP, Bundle.EMPTY),
                                Bundle().apply { putInt(PlaybackService.EXTRA_MINUTES, minutes) },
                            )
                        }
                        Toast.makeText(context, if (minutes == 0) "Sleep at end of song" else "Sleep in $minutes min", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }

    BackHandler(enabled = nowOpen || queueOpen || selected != null || playlistTarget != null) {
        when {
            playlistTarget != null -> playlistTarget = null
            selected != null -> selected = null
            queueOpen -> queueOpen = false
            nowOpen -> nowOpen = false
        }
    }

    if (nowOpen && current != null && controller != null) {
        NowPlaying(
            song = current,
            player = controller!!,
            progress = progress,
            position = position,
            favorite = current.id in favorites,
            onBack = { nowOpen = false },
            onFavorite = {
                scope.launch {
                    if (current.id in favorites) app.database.dao().unfavorite(current.id) else app.database.dao().favorite(com.prateek.musicplayer.data.FavoriteEntity(current.id))
                }
            },
            onQueue = { queueOpen = true },
        )
    }
    selected?.let { song ->
        SongSheet(song, song.id in favorites, {
            selected = null
            play(library, library.indexOfFirst { it.id == song.id }.coerceAtLeast(0))
        }, {
            controller?.addMediaItem(controller!!.currentMediaItemIndex + 1, song.toMediaItem())
            selected = null
        }, {
            controller?.addMediaItem(song.toMediaItem())
            selected = null
        }, {
            scope.launch {
                if (song.id in favorites) app.database.dao().unfavorite(song.id) else app.database.dao().favorite(com.prateek.musicplayer.data.FavoriteEntity(song.id))
            }
            selected = null
        }, { playlistTarget = song; selected = null }, {
            val send = Intent(Intent.ACTION_SEND).setType("audio/*").putExtra(Intent.EXTRA_STREAM, song.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, "Share song"))
            selected = null
        }, { deleteSong(context, song.uri); selected = null })
    }
    if (queueOpen && controller != null) {
        QueueSheet(controller!!, {
            controller?.stop()
            controller?.clearMediaItems()
            playingSong = null
            nowOpen = false
            queueOpen = false
        }) { queueOpen = false }
    }
    playlistTarget?.let { song ->
        AlertDialog(
            onDismissRequest = { playlistTarget = null },
            title = { Text("Add to playlist") },
            text = {
                Column {
                    playlists.forEach { playlist ->
                        Text(playlist.name, modifier = Modifier.fillMaxWidth().clickable {
                            scope.launch { app.database.dao().addPlaylistSong(com.prateek.musicplayer.data.PlaylistSongEntity(playlist.id, song.id, 0)) }
                            playlistTarget = null
                        }.padding(8.dp))
                    }
                    OutlinedTextField(newPlaylist, { newPlaylist = it }, label = { Text("New playlist") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val id = app.database.dao().insertPlaylist(PlaylistEntity(name = newPlaylist.ifBlank { "Playlist" }))
                        app.database.dao().addPlaylistSong(com.prateek.musicplayer.data.PlaylistSongEntity(id, song.id, 0))
                        newPlaylist = ""
                        playlistTarget = null
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton({ playlistTarget = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun Welcome(granted: Boolean, onAllow: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Music Player", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("This app plays music already saved on your phone. It needs permission to read audio files. It does not need an account or internet.")
        Spacer(Modifier.height(20.dp))
        if (!granted) Button(onClick = onAllow) { Text("Allow music access") }
        else Text("Permission granted. Loading library.")
        TextButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        }) { Text("Open app settings") }
    }
}

@Composable
private fun HomeScreen(
    songs: List<Song>,
    recent: List<HistoryEntity>,
    most: List<HistoryEntity>,
    favorites: Set<Long>,
    scanning: Boolean,
    fillingTags: Boolean,
    error: String?,
    onPlay: (Song) -> Unit,
    onTab: (String) -> Unit,
    onRefreshTags: () -> Unit,
) {
    val tags = TagCache.tags
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onRefreshTags, enabled = !fillingTags) {
                    Icon(Icons.Default.Refresh, "Find album details")
                }
            }
        }
        if (scanning) item { Text("Scanning music") }
        if (fillingTags) item { Text("Finding artist, album, and cover art") }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (songs.isEmpty() && !scanning) item { Text("No music found. Add MP3 files to the phone, then rescan in Settings.") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Quick("Songs", songs.size) { onTab("songs") }
                Quick("Favorites", favorites.size) { onTab("playlists") }
                Quick("Folders", songs.map { it.folder }.distinct().size) { onTab("folders") }
            }
        }
        songSection("Recently played", songs.filter { song -> recent.any { it.songId == song.id } }.take(8), onPlay)
        songSection("Most played", songs.filter { song -> most.any { it.songId == song.id } }.take(8), onPlay)
        songSection("Recently added", songs.sortedByDescending { it.dateAdded }.take(8), onPlay)
    }
}

private fun LazyListScope.songSection(title: String, songs: List<Song>, onPlay: (Song) -> Unit) {
    if (songs.isEmpty()) return
    item {
        Text(title, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(songs, key = { it.id }) { song ->
                val tag = TagCache.tags[song.id]
                Column(Modifier.width(120.dp).clickable { onPlay(song) }) {
                    Artwork(song, Modifier.size(120.dp))
                    Text(tag?.title ?: song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(tag?.artist ?: song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Quick(label: String, count: Int, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick).padding(16.dp)) {
        Text("$count", fontWeight = FontWeight.Bold)
        Text(label)
    }
}

@Composable
private fun SongList(songs: List<Song>, query: String, playingId: Long?, onQuery: (String) -> Unit, onPlay: (Song) -> Unit, onMore: (Song) -> Unit) {
    val tags = TagCache.tags
    val shown = songs.filter { song ->
        val tag = tags[song.id]
        query.isBlank() || song.title.contains(query, true) || song.artist.contains(query, true) || song.album.contains(query, true) ||
            tag?.title?.contains(query, true) == true || tag?.artist?.contains(query, true) == true || tag?.album?.contains(query, true) == true
    }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)) {
        item {
            OutlinedTextField(query, onQuery, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Search, null) }, label = { Text("Search songs, artists, albums") }, singleLine = true)
            Text("${shown.size} songs", modifier = Modifier.padding(vertical = 12.dp))
        }
        if (shown.isEmpty()) item { Text("No matching songs.") }
        items(shown, key = { it.id }) { song ->
            SongRow(song, song.id == playingId, { onPlay(song) }, { onMore(song) })
        }
    }
}

@Composable
private fun GroupScreen(groups: Map<String, List<Song>>, excluded: Set<String>, onPlay: (List<Song>) -> Unit, onToggle: (String, Boolean) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(groups.keys.sorted(), key = { it }) { name ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { onPlay(groups[name].orEmpty()) }) {
                    Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${groups[name].orEmpty().size} songs", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = name !in excluded, onCheckedChange = { onToggle(name, it) })
            }
        }
    }
}

@Composable
private fun PlaylistScreen(playlists: List<PlaylistEntity>, favorites: Set<Long>, songs: List<Song>, onPlayIds: (Set<Long>) -> Unit, onCreate: (String) -> Unit, onDelete: (Long) -> Unit) {
    var name by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<PlaylistEntity?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Favorites", modifier = Modifier.clickable { onPlayIds(favorites) }.padding(vertical = 8.dp)) }
        items(playlists, key = { it.id }) { playlist ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(playlist.name, modifier = Modifier.weight(1f).clickable { })
                TextButton({ confirm = playlist }) { Text("Delete") }
            }
        }
        item {
            OutlinedTextField(name, { name = it }, label = { Text("New playlist name") })
            Button(onClick = { if (name.isNotBlank()) { onCreate(name); name = "" } }, modifier = Modifier.padding(top = 8.dp)) { Text("Create playlist") }
        }
    }
    confirm?.let { playlist ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Delete ${playlist.name}?") },
            text = { Text("This removes the playlist, not the songs on the phone.") },
            confirmButton = { TextButton({ onDelete(playlist.id); confirm = null }) { Text("Delete") } },
            dismissButton = { TextButton({ confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingsScreen(
    theme: String,
    amoled: Boolean,
    sort: String,
    sortAsc: Boolean,
    pauseDisconnect: Boolean,
    onTheme: (String) -> Unit,
    onAmoled: (Boolean) -> Unit,
    onSort: (String) -> Unit,
    onSortAsc: (Boolean) -> Unit,
    onPause: (Boolean) -> Unit,
    onRescan: () -> Unit,
    onEqualizer: () -> Unit,
    onSleep: (Int) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Appearance", style = MaterialTheme.typography.titleMedium) }
        item { ChoiceRow("Theme", listOf("system", "light", "dark"), theme, onTheme) }
        item { SwitchRow("AMOLED black", "Use a black background in dark mode", amoled, onAmoled) }
        item { Text("Library", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
        item { ChoiceRow("Sort", listOf("title", "artist", "album", "added", "duration"), sort, onSort) }
        item { SwitchRow("Ascending", "Turn off for Z to A", sortAsc, onSortAsc) }
        item { TextButton(onClick = onRescan) { Text("Rescan library") } }
        item { Text("Playback", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
        item { SwitchRow("Pause when unplugged", "Pause if headphones or Bluetooth disconnect", pauseDisconnect, onPause) }
        item { TextButton(onClick = onEqualizer) { Text("Open equalizer") } }
        item { Text("Sleep timer", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 45, 60, 0).forEach { minutes ->
                    Text(if (minutes == 0) "End" else "$minutes", modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { onSleep(minutes) }.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }
        item { Text("Version 2.6", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp)) }
        item { Text("Credit @Prateek/Lucky", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun NowPlaying(song: Song, player: MediaController, progress: Float, position: Long, favorite: Boolean, onBack: () -> Unit, onFavorite: () -> Unit, onQueue: () -> Unit) {
    val duration = if (player.duration > 0) player.duration else song.durationMs
    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
            Text("Now playing", color = Color(0xFFBDBDBD), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(onClick = onFavorite) {
                Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite", tint = if (favorite) Color(0xFFE53935) else Color.White)
            }
        }
        Artwork(song, Modifier.fillMaxWidth().padding(top = 18.dp).aspectRatio(1f))
        val tag = TagCache.tags[song.id]
        Text(tag?.title ?: song.title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 22.dp))
        Text(tag?.artist ?: song.artist, color = Color(0xFFBDBDBD), fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        if (!tag?.album.isNullOrBlank()) Text(tag?.album.orEmpty(), color = Color(0xFF8A8A8A), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Slider(
            value = progress.coerceIn(0f, 1f),
            onValueChange = { player.seekTo((duration.coerceAtLeast(1L) * it).toLong()) },
            modifier = Modifier.padding(top = 8.dp),
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color(0xFF3A3A3A)),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position), color = Color(0xFFBDBDBD), fontSize = 12.sp)
            Text(formatTime(duration), color = Color(0xFFBDBDBD), fontSize = 12.sp)
        }
        var shuffled by remember(song.id) { mutableStateOf(player.shuffleModeEnabled) }
        var repeatMode by remember(song.id) { mutableIntStateOf(player.repeatMode) }
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton({
                shuffled = !shuffled
                if (shuffled) shuffleUpcoming(player) else player.shuffleModeEnabled = false
            }) {
                Icon(Icons.Default.Shuffle, "Shuffle", tint = if (shuffled) Color.White else Color(0xFF8A8A8A))
            }
            IconButton({ player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0L)) }) {
                Icon(Icons.Default.Replay10, "Back 10 seconds", tint = Color.White)
            }
            IconButton({ player.seekToPrevious() }) { Icon(Icons.Default.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(34.dp)) }
            Box(
                Modifier.size(74.dp).clip(CircleShape).background(Color.White).clickable { toggle(player) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play", tint = Color.Black, modifier = Modifier.size(40.dp))
            }
            IconButton({ player.seekToNext() }) { Icon(Icons.Default.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(34.dp)) }
            IconButton({ player.seekTo((player.currentPosition + 10_000).coerceAtMost(duration.coerceAtLeast(0L))) }) {
                Icon(Icons.Default.Forward10, "Forward 10 seconds", tint = Color.White)
            }
            IconButton({
                repeatMode = when (repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                player.repeatMode = repeatMode
            }) {
                Icon(
                    if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    "Repeat",
                    tint = if (repeatMode == Player.REPEAT_MODE_OFF) Color(0xFF8A8A8A) else Color.White,
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
            IconButton(onClick = onQueue) { Icon(Icons.Default.QueueMusic, "Queue", tint = Color.White) }
        }
    }
}

@Composable
private fun MiniPlayer(song: Song, playing: Boolean, onOpen: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .shadow(18.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF2A2A2E))
            .clickable(onClick = onOpen)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(song, Modifier.size(44.dp))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            val tag = TagCache.tags[song.id]
            Text(tag?.title ?: song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(tag?.artist ?: song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(0xFFBDBDBD))
        }
        IconButton(onClick = onToggle) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play", tint = Color.White) }
        IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, "Next", tint = Color.White) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongSheet(song: Song, favorite: Boolean, onPlay: () -> Unit, onNext: () -> Unit, onQueue: () -> Unit, onFavorite: () -> Unit, onPlaylist: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onPlay, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(song.title, fontWeight = FontWeight.Bold)
            Text("${song.artist} · ${song.album}")
            Text("Folder: ${song.folder}")
            Text("Duration: ${formatTime(song.durationMs)} · ${song.sizeBytes / 1024} KB")
            TextButton(onClick = onPlay) { Text("Play") }
            TextButton(onClick = onNext) { Text("Play next") }
            TextButton(onClick = onQueue) { Text("Add to queue") }
            TextButton(onClick = onFavorite) { Text(if (favorite) "Remove favorite" else "Favorite") }
            TextButton(onClick = onPlaylist) { Text("Add to playlist") }
            TextButton(onClick = onShare) { Text("Share") }
            TextButton(onClick = onDelete) { Text("Delete from device") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(player: MediaController, onClear: () -> Unit, onClose: () -> Unit) {
    val count = player.mediaItemCount
    ModalBottomSheet(onDismissRequest = onClose) {
        LazyColumn(Modifier.navigationBarsPadding().padding(16.dp)) {
            item { TextButton(onClick = onClear) { Text("Clear queue") } }
            items(count) { index ->
                val item = player.getMediaItemAt(index)
                Text(item.mediaMetadata.title?.toString() ?: "Song", modifier = Modifier.fillMaxWidth().clickable { player.seekTo(index, 0) }.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun SongRow(song: Song, playing: Boolean, onClick: () -> Unit, onMore: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (playing) Color(0xFF3A1E22) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(song, Modifier.size(48.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            val tag = TagCache.tags[song.id]
            Text(tag?.title ?: song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, color = if (playing) Color(0xFFFF8A80) else Color.Unspecified)
            Text("${tag?.artist ?: song.artist} · ${formatTime(song.durationMs)}", maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onMore) { Icon(Icons.Default.MoreVert, "More") }
    }
}

@Composable
private fun Artwork(song: Song, modifier: Modifier) {
    val context = LocalContext.current
    val embedded by produceState<ByteArray?>(initialValue = null, song.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, song.uri)
                    retriever.embeddedPicture
                } finally {
                    retriever.release()
                }
            }.getOrNull()
        }
    }
    val remote = TagCache.tags[song.id]?.artUrl
    val model = embedded ?: remote ?: albumArtUri(song.albumId)
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFF1C1C1C)), contentAlignment = Alignment.Center) {
        if (embedded == null && remote.isNullOrBlank()) Icon(Icons.Default.MusicNote, null, tint = Color(0xFF6E6E6E), modifier = Modifier.size(48.dp))
        AsyncImage(model = model, contentDescription = song.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun NavItem(id: String, label: String, icon: ImageVector, selected: String, onClick: (String) -> Unit) {
    Column(Modifier.clickable { onClick(id) }.padding(horizontal = 6.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = if (selected == id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, maxLines = 1, color = if (selected == id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun ChoiceRow(title: String, values: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column {
        Text(title)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            values.forEach { value ->
                Text(
                    value,
                    modifier = Modifier.clip(CircleShape).background(if (value == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant).clickable { onSelect(value) }.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = if (value == selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun Song.toMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(TagCache.tags[id]?.title ?: title)
                .setArtist(TagCache.tags[id]?.artist ?: artist)
                .setAlbumTitle(TagCache.tags[id]?.album ?: album)
                .setArtworkUri(TagCache.tags[id]?.artUrl?.let { Uri.parse(it) } ?: albumArtUri(albumId))
                .build(),
        )
        .build()
}

private fun toggle(player: MediaController?) {
    if (player == null) return
    if (player.isPlaying) player.pause() else player.play()
}

private fun songComparator(sort: String, asc: Boolean): Comparator<Song> {
    val base = when (sort) {
        "artist" -> compareBy<Song> { it.artist.lowercase() }
        "album" -> compareBy { it.album.lowercase() }
        "added" -> compareBy { it.dateAdded }
        "duration" -> compareBy { it.durationMs }
        else -> compareBy { it.title.lowercase() }
    }
    return if (asc) base else base.reversed()
}

private fun audioPermission(): String {
    return if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
}

private fun openEqualizer(context: android.content.Context, player: MediaController?) {
    val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
        putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        player?.let { putExtra(AudioEffect.EXTRA_AUDIO_SESSION, it.sessionActivity?.hashCode() ?: 0) }
    }
    if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
    else Toast.makeText(context, "This phone has no equalizer app", Toast.LENGTH_SHORT).show()
}

private fun deleteSong(context: android.content.Context, uri: Uri) {
    runCatching {
        if (Build.VERSION.SDK_INT >= 30) {
            val pending = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
            context.startActivity(Intent(Intent.ACTION_VIEW).apply { })
            (context as? ComponentActivity)?.startIntentSenderForResult(pending.intentSender, 41, null, 0, 0, 0)
        } else {
            context.contentResolver.delete(uri, null, null)
        }
    }.onFailure {
        Toast.makeText(context, "Could not delete: ${it.message}", Toast.LENGTH_LONG).show()
    }
}


private suspend fun fillMissingTags(context: android.content.Context, songs: List<Song>, force: Boolean = false, onBusy: (Boolean) -> Unit) {
    onBusy(true)
    songs.filter { force || TagCache.tags[it.id] == null }.forEach { song ->
        val found = withContext(Dispatchers.IO) { runCatching { TagLookup.search(song.title) }.getOrNull() }
        if (found != null) {
            TagCache.tags = TagCache.tags + (song.id to found)
            TagCache.save(context)
        }
        delay(120)
    }
    onBusy(false)
}


private fun shuffleUpcoming(player: MediaController) {
    val count = player.mediaItemCount
    if (count < 2) {
        player.shuffleModeEnabled = false
        return
    }
    val currentIndex = player.currentMediaItemIndex.coerceIn(0, count - 1)
    val items = (0 until count).map { player.getMediaItemAt(it) }
    val upcoming = items.filterIndexed { index, _ -> index != currentIndex }.shuffled()
    val position = player.currentPosition.coerceAtLeast(0L)
    val wasPlaying = player.isPlaying
    player.shuffleModeEnabled = false
    player.setMediaItems(listOf(items[currentIndex]) + upcoming, 0, position)
    player.prepare()
    if (wasPlaying) player.play()
}


@Composable
private fun SoundkeepPage() {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient = WebViewClient()
                loadUrl("https://songs-prateek.grok.me")
            }
        },
    )
}
