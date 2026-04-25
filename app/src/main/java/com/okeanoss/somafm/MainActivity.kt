package com.okeanoss.somafm

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.okeanoss.somafm.models.SomaChannel
import com.okeanoss.somafm.service.RadioService
import com.okeanoss.somafm.ui.viewmodel.SomaFMViewModel
import com.okeanoss.somafm.worker.SupportWorker

// RENK PALETI (PREMIUM DARK)
val DarkGray = Color(0xFF212121)
val MediumGray = Color(0xFF424242)
val AccentColor = Color(0xFFF5F5F5)

class MainActivity : ComponentActivity() {
    private val viewModel: SomaFMViewModel by viewModels()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController by mutableStateOf<MediaController?>(null)
    private var currentMediaId by mutableStateOf<String?>(null)
    private var isPlayingState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SupportWorker.schedule(this)

        val sessionToken = SessionToken(this, ComponentName(this, RadioService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            val controller = controllerFuture?.get()
            mediaController = controller
            controller?.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    currentMediaId = mediaItem?.mediaId
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    isPlayingState = isPlaying
                }
            })
        }, MoreExecutors.directExecutor())

        setContent {
            SomaFMTheme {
                val navController = rememberNavController()
                LaunchedEffect(intent) {
                    if (intent?.getStringExtra("navigate_to") == "about") navController.navigate("about")
                }
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") { SomaFMApp(viewModel, mediaController, currentMediaId, isPlayingState, navController) }
                    composable("about") { AboutScreen(viewModel, navController) }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SomaFMApp(viewModel: SomaFMViewModel, controller: MediaController?, currentMediaId: String?, isPlaying: Boolean, navController: NavController) {
    LaunchedEffect(Unit) { if (viewModel.channels.isEmpty()) viewModel.fetchChannels() }
    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(DarkGray)) {
                CenterAlignedTopAppBar(
                    title = { Text("OkeanossFM", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent, titleContentColor = Color.White),
                    actions = {
                        IconButton(onClick = { navController.navigate("about") }) { Icon(Icons.Default.Settings, contentDescription = "Ayarlar", tint = Color.White) }
                    }
                )
                TextField(
                    value = viewModel.searchQuery,
                    onValueChange = { viewModel.updateSearch(it) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Kanal ara...", color = Color.LightGray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    colors = TextFieldDefaults.colors(focusedContainerColor = MediumGray, unfocusedContainerColor = MediumGray, focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color.White, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
            }
        },
        bottomBar = {
            if (currentMediaId != null) {
                val songInfo = viewModel.songMetadata[currentMediaId] ?: "Canlı Yayın..."
                val channelName = viewModel.channels.find { it.id == currentMediaId }?.title ?: "OkeanossFM"
                NowPlayingBar(channelName, songInfo, isPlaying, controller)
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues).background(Color(0xFF121212))) {
            when {
                viewModel.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
                viewModel.errorMessage != null -> ErrorContent(viewModel)
                else -> ChannelList(viewModel.filteredChannels, viewModel.favoriteIds, currentMediaId, isPlaying, viewModel::toggleFavorite, controller, viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(viewModel: SomaFMViewModel, navController: NavController) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Uygulama Bilgileri") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Geri") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Icon(Icons.Default.DeveloperMode, contentDescription = null, modifier = Modifier.size(80.dp), tint = DarkGray)
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Okyanus Arslan", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(text = "Okeanoss - Network & Agency", fontSize = 14.sp, color = Color.Gray)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkGray, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Versiyon Denetleyici", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(text = viewModel.updateStatus, color = Color.LightGray, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(16.dp))
                    Row {
                        Button(onClick = { viewModel.checkForUpdates() }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = DarkGray)) { Text("Güncelleme Denetle") }
                        if (viewModel.updateUrl != null) {
                            Spacer(Modifier.width(12.dp))
                            Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(viewModel.updateUrl))) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) { Text("İndir") }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://okeanoss.com"))) }, modifier = Modifier.weight(1f)) { Text("Web Sitesi", fontSize = 12.sp) }
                OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("mailto:android.devel@okeanoss.com") }) }, modifier = Modifier.weight(1f)) { Text("Destek", fontSize = 12.sp) }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text(text = "SomaFM altyapısı ile güçlendirilmiştir.", fontSize = 10.sp, color = Color.LightGray)
            Text(text = "Versiyon: 1.0.1 (Android 16)", fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
fun NowPlayingBar(channelName: String, songInfo: String, isPlaying: Boolean, controller: MediaController?) {
    Box(modifier = Modifier.fillMaxWidth().height(85.dp).background(DarkGray).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
            Icon(Icons.Default.Radio, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = channelName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(text = songInfo, color = Color.LightGray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { if (isPlaying) controller?.pause() else controller?.play() }) {
                Icon(if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(45.dp))
            }
        }
    }
}

@Composable
fun ChannelList(channels: List<SomaChannel>, favoriteIds: Set<String>, currentMediaId: String?, isPlaying: Boolean, onToggleFav: (SomaChannel) -> Unit, controller: MediaController?, viewModel: SomaFMViewModel) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(channels, key = { it.id }) { channel -> 
            val isCurrent = currentMediaId == channel.id
            ChannelItem(channel, favoriteIds.contains(channel.id), isCurrent, isPlaying && isCurrent, onToggleFav, controller, viewModel) 
        }
    }
}

@Composable
fun ChannelItem(channel: SomaChannel, isFavorite: Boolean, isCurrent: Boolean, isPlaying: Boolean, onToggleFav: (SomaChannel) -> Unit, controller: MediaController?, viewModel: SomaFMViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(), 
        shape = RoundedCornerShape(16.dp), 
        colors = CardDefaults.cardColors(containerColor = if (isCurrent) MediumGray else Color(0xFF1E1E1E), contentColor = Color.White)
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = channel.imageUrl, contentDescription = null, imageLoader = viewModel.imageLoader, modifier = Modifier.size(65.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = channel.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(text = channel.description, fontSize = 13.sp, color = Color.LightGray, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { onToggleFav(channel) }) { Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Favori", tint = if (isFavorite) Color.White else Color.Gray) }
            IconButton(
                onClick = {
                    if (isCurrent && isPlaying) controller?.pause()
                    else {
                        val streamUrl = "https://ice1.somafm.com/${channel.id}-128-aac"
                        val mediaItem = MediaItem.Builder().setMediaId(channel.id).setUri(streamUrl).setMediaMetadata(MediaMetadata.Builder().setTitle(channel.title).setArtist("OkeanossFM").build()).build()
                        controller?.setMediaItem(mediaItem)
                        controller?.prepare()
                        controller?.play()
                    }
                }
            ) { Icon(if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp)) }
        }
    }
}

@Composable
fun ErrorContent(viewModel: SomaFMViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).background(Color.Black), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = viewModel.errorMessage ?: "Bir hata oluştu", color = Color.White, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { viewModel.fetchChannels() }, colors = ButtonDefaults.buttonColors(containerColor = DarkGray)) { Text("Tekrar Dene", color = Color.White) }
    }
}

@Composable
fun SomaFMTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = DarkGray, secondary = Color.White, background = Color.Black), content = content)
}
