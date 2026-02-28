package com.okeanoss.somafm.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.okeanoss.somafm.api.SomaFMService
import com.okeanoss.somafm.database.FavoriteChannel
import com.okeanoss.somafm.database.SomaDatabase
import com.okeanoss.somafm.models.SomaChannel
import com.okeanoss.somafm.worker.SupportWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URL

class SomaFMViewModel(application: Application) : AndroidViewModel(application) {

    var channels by mutableStateOf<List<SomaChannel>>(emptyList())
    var filteredChannels by mutableStateOf<List<SomaChannel>>(emptyList())
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var searchQuery by mutableStateOf("")
    var favoriteIds by mutableStateOf<Set<String>>(emptySet())
    var songMetadata by mutableStateOf<Map<String, String>>(emptyMap())

    // GIF Desteği için ImageLoader
    val imageLoader = ImageLoader.Builder(application)
        .components {
            if (Build.VERSION.SDK_INT >= 28) {
                add(ImageDecoderDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }
        .build()

    private val db by lazy {
        Room.databaseBuilder(application, SomaDatabase::class.java, "soma_db")
            .fallbackToDestructiveMigration()
            .build()
    }
    private val favoriteDao by lazy { db.favoriteDao() }

    private val apiService by lazy {
        Retrofit.Builder()
            .baseUrl(SomaFMService.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SomaFMService::class.java)
    }

    init {
        loadFavorites()
        startMetadataPolling()
    }

    private fun loadFavorites() {
        viewModelScope.launch {
            favoriteDao.getAllFavorites()
                .catch { e -> errorMessage = "Veritabanı Hatası" }
                .collect { favorites ->
                    favoriteIds = favorites.map { it.id }.toSet()
                }
        }
    }

    private fun startMetadataPolling() {
        viewModelScope.launch {
            while (isActive) {
                try {
                    val rawMeta = apiService.getSongs()
                    val processed = mutableMapOf<String, String>()
                    rawMeta.forEach { (key, value) ->
                        val cleanKey = key.lowercase().trim()
                        processed[cleanKey] = value
                        // DERİN EŞLEŞME (DeepSpaceOne, Defcon vb. için)
                        if (cleanKey.contains("deepspace")) processed["deepspaceone"] = value
                        if (cleanKey.contains("defcon")) processed["defcon"] = value
                    }
                    songMetadata = processed
                } catch (e: Exception) {}
                delay(10000)
            }
        }
    }

    fun fetchChannels() {
        viewModelScope.launch {
            isLoading = true
            try {
                val response = apiService.getChannels()
                channels = response.channels.map { channel ->
                    val cleanUrl = channel.imageUrl
                        .replace("api.somafm.com", "somafm.com")
                        .replace("http://", "https://")
                    channel.copy(id = channel.id.lowercase().trim(), imageUrl = cleanUrl)
                }
                updateSearch(searchQuery)
            } catch (e: Exception) {
                errorMessage = "Bağlantı Hatası"
            } finally {
                isLoading = false
            }
        }
    }

    fun updateSearch(query: String) {
        searchQuery = query
        filteredChannels = if (query.isEmpty()) channels else channels.filter {
            it.title.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true)
        }
    }

    fun toggleFavorite(channel: SomaChannel) {
        viewModelScope.launch(Dispatchers.IO) {
            if (favoriteIds.contains(channel.id)) {
                favoriteDao.deleteFavorite(FavoriteChannel(channel.id, channel.title, channel.description, channel.imageUrl))
            } else {
                favoriteDao.insertFavorite(FavoriteChannel(channel.id, channel.title, channel.description, channel.imageUrl))
            }
        }
    }

    // --- GITHUB OTONOM GÜNCELLEME MANTIĞI ---
    var updateStatus by mutableStateOf("Güncel")
    var updateUrl by mutableStateOf<String?>(null)

    fun checkForUpdates() {
        viewModelScope.launch(Dispatchers.IO) {
            updateStatus = "Denetleniyor..."
            try {
                val jsonUrl = "https://raw.githubusercontent.com/arslanokyanus/OkeanossFM/master/version.json"
                val content = URL(jsonUrl).readText()
                val json = Json.parseToJsonElement(content).jsonObject
                
                val latestCode = json["latestVersionCode"]?.jsonPrimitive?.content?.toInt() ?: 0
                val latestName = json["latestVersionName"]?.jsonPrimitive?.content ?: ""
                val downloadUrl = json["updateUrl"]?.jsonPrimitive?.content
                
                // Mevcut version code (Şu anlık 1 kabul ediyoruz)
                if (latestCode > 1) {
                    updateStatus = "Yeni Sürüm: $latestName"
                    updateUrl = downloadUrl
                } else {
                    updateStatus = "Uygulama Güncel"
                }
            } catch (e: Exception) {
                updateStatus = "Hata oluştu"
            }
        }
    }
}
