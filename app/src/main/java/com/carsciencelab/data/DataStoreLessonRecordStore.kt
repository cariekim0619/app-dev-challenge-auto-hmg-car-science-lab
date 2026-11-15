package com.carsciencelab.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.labDataStore: DataStore<Preferences> by preferencesDataStore(name = "lab_records")

/**
 * 기록을 기기의 DataStore에 보관한다. 서버로 보내지 않는다.
 * 저장 내용 전체를 글 한 건으로 넣고, 형식은 [StoredLabDataCodec]이 맡는다.
 */
class DataStoreLessonRecordStore(context: Context) : LessonRecordStore {

    private val dataStore = context.applicationContext.labDataStore

    override val data: Flow<StoredLabData> = dataStore.data
        .catch { error ->
            // 파일을 읽지 못하면 빈 기록으로 시작한다.
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> StoredLabDataCodec.decode(preferences[STATE_KEY]) }

    override suspend fun update(transform: (StoredLabData) -> StoredLabData) {
        dataStore.edit { preferences ->
            val current = StoredLabDataCodec.decode(preferences[STATE_KEY])
            preferences[STATE_KEY] = StoredLabDataCodec.encode(transform(current))
        }
    }

    private companion object {
        val STATE_KEY = stringPreferencesKey("lab_state_v1")
    }
}
