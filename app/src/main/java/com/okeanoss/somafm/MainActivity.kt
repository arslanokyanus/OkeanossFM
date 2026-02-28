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
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.okeanoss.somafm.models.SomaChannel
import com.okeanoss.somafm.service.RadioService
import com.okeanoss.somafm.ui.viewmodel.SomaFMViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: SomaFMViewModel by viewModels()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController by mutableStateOf<MediaController?>(null)
    private var currentMediaId by mutableStateOf<String?>(null)
    private var isPlayingState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MobileAds.initialize(this) {}

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
                
                // DIŞARIDAN GELEN YÖNLENDİRME (BİLDİRİM TIKLAMASI)
                LaunchedEffect(intent) {
                    if (intent?.getStringExtra("navigate_to") == "about") {
                        navController.navigate("about")
                    }
                }

                // BİLDİRİM PANELİNDE ŞARKI İSMİNİ GÜNCELLE
                LaunchedEffect(viewModel.songMetadata, currentMediaId) {
                    currentMediaId?.let { id ->
                        val song = viewModel.songMetadata[id.lowercase()] ?: "Canlı Yayın..."
                        val station = viewModel.channels.find { it.id == id }?.title ?: "OkeanossFM"
                        
                        mediaController?.let { controller ->
                            // Yayını kesmeden metadata güncelleme
                            // Media3'te setPlaylistMetadata veya MediaItem güncelleme ile yapılır
                        }
                    }
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
            Column {
                CenterAlignedTopAppBar(
                    title = { Text("OkeanossFM", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color(0xFFE91E63), titleContentColor = Color.White),
                    actions = {
                        IconButton(onClick = { navController.navigate("about") }) { Icon(Icons.Default.Info, contentDescription = "Hakkında", tint = Color.White) }
                    }
                )
                TextField(
                    value = viewModel.searchQuery,
                    onValueChange = { viewModel.updateSearch(it) },
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    placeholder = { Text("Kanal veya tür ara...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    colors = TextFieldDefaults.textFieldColors(containerColor = Color(0xFFF5F5F5), unfocusedIndicatorColor = Color.Transparent, focusedIndicatorColor = Color.Transparent),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        bottomBar = {
            Column {
                if (currentMediaId != null) {
                    val songInfo = viewModel.songMetadata[currentMediaId.lowercase()] ?: "Canlı Yayın..."
                    val channelName = viewModel.channels.find { it.id == currentMediaId }?.title ?: "OkeanossFM"
                    NowPlayingBar(channelName, songInfo, isPlaying, controller)
                }
                AdMobBanner()
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                viewModel.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFFE91E63))
                viewModel.errorMessage != null -> ErrorContent(viewModel)
                else -> ChannelList(viewModel.filteredChannels, viewModel.favoriteIds, currentMediaId, isPlaying, viewModel::toggleFavorite, controller)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(viewModel: SomaFMViewModel, navController: NavController) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.checkGitHubUpdates() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hakkında") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Geri") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.DeveloperMode, contentDescription = null, modifier = Modifier.size(80.dp), tint = Color(0xFFE91E63))
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Okyanus Arslan", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(text = "Okeanoss - Network & Agency", fontSize = 14.sp, color = Color.Gray)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (viewModel.isNewVersionReady) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Update, contentDescription = null, tint = Color(0xFF2E7D32))
                        Spacer(Modifier.width(12.dp))
                        Text("Yeni Güncelleme Hazır!", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { /* Github APK Linkini Aç */ }) { Text("İNDİR") }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Bana Destek Olun ❤️", fontWeight = FontWeight.Bold, color = Color(0xFFE91E63))
                    Text("Reklam izleyerek OkeanossFM'in gelişmesine katkıda bulunabilirsiniz.", textAlign = TextAlign.Center, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                    Button(onClick = { /* Rewarded Ad */ }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))) {
                        Icon(Icons.Default.PlayCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Reklam İzle")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://okeanoss.com"))) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Language, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Okeanoss.com")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { 
                val intent = Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("mailto:hello@okeanoss.com"); putExtra(Intent.EXTRA_SUBJECT, "OkeanossFM Geri Bildirim") }
                context.startActivity(intent)
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Email, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("hello@okeanoss.com")
            }

            Spacer(modifier = Modifier.weight(1f))
            Text(text = "Versiyon: 1.0.0", fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
fun NowPlayingBar(channelName: String, songInfo: String, isPlaying: Boolean, controller: MediaController?) {
    Box(modifier = Modifier.fillMaxWidth().height(80.dp).background(Color(0xFFE91E63)).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
            Icon(Icons.Default.Radio, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = channelName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(text = songInfo, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { if (isPlaying) controller?.pause() else controller?.play() }) {
                Icon(if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
        }
    }
}

@Composable
fun ChannelList(channels: List<SomaChannel>, favoriteIds: Set<String>, currentMediaId: String?, isPlaying: Boolean, onToggleFav: (SomaChannel) -> Unit, controller: MediaController?) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(channels) { channel -> 
            val isCurrent = currentMediaId == channel.id
            ChannelItem(channel, favoriteIds.contains(channel.id), isCurrent, isPlaying && isCurrent, onToggleFav, controller) 
        }
    }
}

@Composable
fun ChannelItem(channel: SomaChannel, isFavorite: Boolean, isCurrent: Boolean, isPlaying: Boolean, onToggleFav: (SomaChannel) -> Unit, controller: MediaController?) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = if (isCurrent) Color(0xFFFFEBEE) else Color(0xFFF5F5F5))) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = channel.imageUrl,
                contentDescription = null,
                modifier = Modifier.size(60.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
                error = null
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = channel.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (isCurrent) Color(0xFFE91E63) else Color.Black)
                Text(text = channel.description, fontSize = 14.sp, color = Color.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { onToggleFav(channel) }) {
                Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Favori", tint = if (isFavorite) Color.Red else Color.Gray)
            }
            IconButton(
                onClick = {
                    if (isCurrent && isPlaying) {
                        controller?.pause()
                    } else {
                        val streamUrl = "https://ice1.somafm.com/${channel.id}-128-aac"
                        val mediaItem = MediaItem.Builder()
                            .setMediaId(channel.id)
                            .setUri(streamUrl)
                            .setMediaMetadata(
                                MediaMetadata.Builder().setTitle(channel.title).setArtist("Yükleniyor...").build()
                            ).build()
                        controller?.setMediaItem(mediaItem)
                        controller?.prepare()
                        controller?.play()
                    }
                }
            ) {
                Icon(if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFFE91E63))
            }
        }
    }
}

@Composable
fun AdMobBanner() {
    AndroidView(modifier = Modifier.fillMaxWidth().height(50.dp).background(Color.Black), factory = { context -> AdView(context).apply { setAdSize(AdSize.BANNER); adUnitId = "ca-app-pub-3940256099942544/6300978111"; loadAd(AdRequest.Builder().build()) } })
}

@Composable
fun ErrorContent(viewModel: SomaFMViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = viewModel.errorMessage!!, color = Color.Red, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { viewModel.fetchChannels() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))) { Text("Tekrar Dene", color = Color.White) }
    }
}

@Composable
fun SomaFMTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFFE91E63), secondary = Color(0xFF03DAC6), background = Color.White), content = content)
}
