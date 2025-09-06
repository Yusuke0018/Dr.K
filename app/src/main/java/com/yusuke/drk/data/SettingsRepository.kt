package com.yusuke.drk.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

object SettingsRepository {
    private val KEY_UNIT_MILES = booleanPreferencesKey("unit_miles")

    fun unitMiles(context: Context): Flow<Boolean> = context.dataStore.data.map { it[KEY_UNIT_MILES] ?: false }

    suspend fun setUnitMiles(context: Context, miles: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_UNIT_MILES] = miles
        }
    }
}

