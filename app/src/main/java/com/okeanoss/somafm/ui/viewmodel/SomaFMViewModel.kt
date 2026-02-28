package com.okeanoss.somafm.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
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
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SomaFMViewModel(application: Application) : AndroidViewModel(application) {

    var channels by mutableStateOf<List<SomaChannel>>(emptyList())
    var filteredChannels by mutableStateOf<List<SomaChannel>>(emptyList())
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var searchQuery by mutableStateOf("")
    var favoriteIds by mutableStateOf<Set<String>>(emptySet())
    var songMetadata by mutableStateOf<Map<String, String>>(emptyMap())

    private val db by lazy {
        Room.databaseBuilder(application, SomaDatabase::class.java, "soma_db")
            .fallbackToDestructiveMigration()
            .build()
    }
    private val favoriteDao by lazy { db.favoriteDao() }

    private val retrofit = Retrofit.Builder()
        .baseUrl(SomaFMService.BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    private val apiService = retrofit.create(SomaFMService::class.java)

    init {
        loadFavorites()
        startMetadataPolling()
        SupportWorker.schedule(application) // Bildirimi planla
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
                        val cleanKey = key.lowercase().replace("-", "").trim()
                        processed[cleanKey] = value
                        // Özel kural: SomaFM'in değişken ID yapısı için
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
                    // GÖRSEL TAMİRİ: SSL ve URL sorunlarını çözmek için
                    val cleanId = channel.id.lowercase().trim()
                    val cleanUrl = channel.imageUrl
                        .replace("api.somafm.com", "somafm.com")
                        .replace("http://", "https://")
                    channel.copy(id = cleanId, imageUrl = cleanUrl)
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
        filteredChannels = if (query.isEmpty()) channels else channels.filter { it.title.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true) }
    }

    fun toggleFavorite(channel: SomaChannel) {
        viewModelScope.launch(Dispatchers.IO) {
            val isFav = favoriteIds.contains(channel.id)
            if (isFav) {
                favoriteDao.deleteFavorite(FavoriteChannel(channel.id, channel.title, channel.description, channel.imageUrl))
            } else {
                favoriteDao.insertFavorite(FavoriteChannel(channel.id, channel.title, channel.description, channel.imageUrl))
            }
        }
    }

    // GITHUB OTONOM GÜNCELLEME
    var isNewVersionReady by mutableStateOf(false)
    fun checkGitHubUpdates() {
        viewModelScope.launch {
            // TODO: Fetch version.json from GitHub
            delay(1500)
            // isNewVersionReady = true 
        }
    }
}
