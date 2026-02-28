package com.okeanoss.somafm.api

import com.okeanoss.somafm.models.SomaChannelsResponse
import retrofit2.http.GET

/**
 * SomaFM'in API'sinden veri çekmek için kullanılan Retrofit arayüzü.
 */
interface SomaFMService {
    /**
     * Tüm SomaFM kanallarını bir liste halinde döndürür.
     */
    @GET("channels.json")
    suspend fun getChannels(): SomaChannelsResponse

    /**
     * O an çalan şarkı bilgilerini getirir.
     */
    @GET("songs.json")
    suspend fun getSongs(): Map<String, String>

    companion object {
        const val BASE_URL = "https://somafm.com/"
    }
}
