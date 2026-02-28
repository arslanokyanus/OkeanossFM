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
import java.net.UnknownHostException

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
        // Bildirim planlamasını buraya değil, bir defaya mahsus MainActivity'ye taşıyacağız.
    }

    private fun loadFavorites() {
        viewModelScope.launch {
            favoriteDao.getAllFavorites()
                .catch { e -> errorMessage = "Veritabanı Hatası: ${e.message}" }
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
                        // Özel eşleşme kuralları
                        if (cleanKey.contains("deepspace")) processed["deepspaceone"] = value
                        if (cleanKey.contains("defcon")) processed["defcon"] = value
                    }
                    songMetadata = processed
                } catch (e: Exception) {
                    // Sessizce logla
                }
                delay(15000)
            }
        }
    }

    fun fetchChannels() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val response = apiService.getChannels()
                channels = response.channels.map { channel ->
                    val cleanUrl = channel.imageUrl
                        .replace("api.somafm.com", "somafm.com")
                        .replace("http://", "https://")
                    channel.copy(id = channel.id.lowercase().trim(), imageUrl = cleanUrl)
                }
                updateSearch(searchQuery)
            } catch (e: UnknownHostException) {
                errorMessage = "İnternet bağlantısı yok."
            } catch (e: Exception) {
                errorMessage = "Sunucuya bağlanılamadı."
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

    var isNewVersionReady by mutableStateOf(false)
    fun checkGitHubUpdates() {
        viewModelScope.launch {
            // İleride version.json okuma eklenecek
            delay(1000)
        }
    }
}
