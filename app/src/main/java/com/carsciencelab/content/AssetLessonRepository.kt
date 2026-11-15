package com.carsciencelab.content

import android.content.res.AssetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 앱에 포함한 `assets/lessons/` 아래의 콘텐츠를 읽는다. 네트워크를 쓰지 않는다. */
class AssetLessonRepository(private val assets: AssetManager) : LessonRepository {

    override suspend fun load(): LessonCatalog = withContext(Dispatchers.IO) {
        LessonContentParser.parseCatalog(read(INDEX_FILE)) { name -> read(name) }
    }

    private fun read(name: String): String =
        assets.open("$DIRECTORY/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }

    private companion object {
        const val DIRECTORY = "lessons"
        const val INDEX_FILE = "index.json"
    }
}
