package com.okeanoss.somafm.models.remote

import kotlinx.serialization.Serializable

/**
 * Uzaktan gönderilen reklam veya tanıtım mesajı.
 */
@Serializable
data class RemoteAnnouncement(
    val id: Int? = null,
    val title: String,
    val message: String,
    val actionUrl: String? = null,
    val isActive: Boolean = false
)

/**
 * Uygulama sürüm kontrol modeli.
 */
@Serializable
data class AppVersionInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val updateUrl: String,
    val isMandatory: Boolean = false
)
