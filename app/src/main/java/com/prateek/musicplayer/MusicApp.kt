package com.prateek.musicplayer

import android.app.Application
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.prateek.musicplayer.data.MusicDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Application.store by preferencesDataStore("player_settings")

class MusicApp : Application() {
    lateinit var database: MusicDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(this, MusicDatabase::class.java, "music.db").build()
    }

    val themeMode: Flow<String> get() = store.data.map { it[Keys.theme] ?: "system" }
    val amoled: Flow<Boolean> get() = store.data.map { it[Keys.amoled] ?: false }
    val sort: Flow<String> get() = store.data.map { it[Keys.sort] ?: "title" }
    val sortAsc: Flow<Boolean> get() = store.data.map { it[Keys.sortAsc] ?: true }
    val excluded: Flow<Set<String>> get() = store.data.map { it[Keys.excluded] ?: emptySet() }
    val pauseOnDisconnect: Flow<Boolean> get() = store.data.map { it[Keys.pauseDisconnect] ?: true }
    val resumeOnCar: Flow<Boolean> get() = store.data.map { it[Keys.resumeOnCar] ?: true }
    val resumeOnLaunch: Flow<Boolean> get() = store.data.map { it[Keys.resume] ?: true }
    val crossfadeSeconds: Flow<Int> get() = store.data.map { it[Keys.crossfade] ?: 0 }
    val welcomeDone: Flow<Boolean> get() = store.data.map { it[Keys.welcome] ?: false }

    suspend fun setTheme(value: String) = store.edit { it[Keys.theme] = value }
    suspend fun setAmoled(value: Boolean) = store.edit { it[Keys.amoled] = value }
    suspend fun setSort(value: String) = store.edit { it[Keys.sort] = value }
    suspend fun setSortAsc(value: Boolean) = store.edit { it[Keys.sortAsc] = value }
    suspend fun setExcluded(value: Set<String>) = store.edit { it[Keys.excluded] = value }
    suspend fun setPauseOnDisconnect(value: Boolean) = store.edit { it[Keys.pauseDisconnect] = value }
    suspend fun setResumeOnCar(value: Boolean) = store.edit { it[Keys.resumeOnCar] = value }
    suspend fun setting(key: String, default: Boolean): Boolean {
        val pref = booleanPreferencesKey(key)
        return store.data.first()[pref] ?: default
    }
    suspend fun setResume(value: Boolean) = store.edit { it[Keys.resume] = value }
    suspend fun setCrossfade(seconds: Int) = store.edit { it[Keys.crossfade] = seconds }
    suspend fun setWelcomeDone() = store.edit { it[Keys.welcome] = true }

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val amoled = booleanPreferencesKey("amoled")
        val sort = stringPreferencesKey("sort")
        val sortAsc = booleanPreferencesKey("sort_asc")
        val excluded = stringSetPreferencesKey("excluded")
        val pauseDisconnect = booleanPreferencesKey("pause_disconnect")
        val resumeOnCar = booleanPreferencesKey("resume_on_car")
        val resume = booleanPreferencesKey("resume")
        val crossfade = intPreferencesKey("crossfade")
        val welcome = booleanPreferencesKey("welcome")
    }
}
