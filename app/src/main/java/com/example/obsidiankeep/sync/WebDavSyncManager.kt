package com.example.obsidiankeep.sync

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebDavSyncManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isConfigured(): Boolean = prefs.getString(KEY_SERVER_URL, null) != null

    fun getServerUrl(): String? = prefs.getString(KEY_SERVER_URL, null)
    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)
    fun getPassword(): String? = prefs.getString(KEY_PASSWORD, null)
    fun getRemotePath(): String = prefs.getString(KEY_REMOTE_PATH, "/ObsidianKeep/") ?: "/ObsidianKeep/"

    fun configure(url: String, username: String, password: String, remotePath: String) {
        prefs.edit().apply {
            putString(KEY_SERVER_URL, url)
            putString(KEY_USERNAME, username)
            putString(KEY_PASSWORD, password)
            putString(KEY_REMOTE_PATH, remotePath.ifBlank { "/ObsidianKeep/" })
        }.apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    suspend fun pushAll(treeUri: Uri): Int {
        // TODO: реализовать через okhttp/sardine-android WebDAV-клиент.
        // 1. Пройти по всем .md файлам в treeUri (через DocumentFile)
        // 2. Для каждого: UPLOAD на WebDAV сервер (PUT /remotePath/filename.md)
        // 3. Вернуть количество загруженных файлов.
        // Зависимость: implementation("com.github.lookfirst:sardine-java:5.10") + JitPack
        throw UnsupportedOperationException("WebDAV push — TODO. См. комментарий в классе.")
    }

    suspend fun pullAll(treeUri: Uri): Int {
        // TODO: реализовать pull с WebDAV → локальную папку.
        throw UnsupportedOperationException("WebDAV pull — TODO. См. комментарий в классе.")
    }

    companion object {
        private const val PREFS_NAME = "webdav_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_REMOTE_PATH = "remote_path"
    }
}
