package com.okeanoss.somafm.models

import com.google.gson.annotations.SerializedName

/**
 * SomaFM kanal bilgilerini temsil eden veri modeli.
 */
data class SomaChannel(
    val id: String,
    val title: String,
    val description: String,
    @SerializedName("image") val imageUrl: String,
    @SerializedName("largeimage") val largeImageUrl: String?,
    val dj: String?,
    val genre: String?,
    @SerializedName("fastpls") val fastPls: String? // Genellikle AAC/MP3 stream playlist linki
)

/**
 * SomaFM'in channels.json dosyasının üst düzey sarmalayıcısı.
 */
data class SomaChannelsResponse(
    val channels: List<SomaChannel>
)
