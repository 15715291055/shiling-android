package com.fuguang.localdiary.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File

class DiaryViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.get(application)
    val diaries = db.diaryDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val footprints = db.footprintDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val moods = db.moodDao().observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tags = db.moodDao().tags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    suspend fun saveMood(item: MoodEntity) = db.moodDao().save(item)
    suspend fun deleteMood(id: String) = db.moodDao().delete(id)
    suspend fun moodAsTag(item: MoodEntity) = db.moodDao().addTag(TagEntity(item.name))

    private suspend fun deleteUnusedImages(paths: List<String>) {
        val referenced = db.diaryDao().getAll().flatMap { it.images() }.toSet()
        val imageDir = File(getApplication<Application>().filesDir, "diary-images").canonicalFile
        paths.filterNot { it in referenced }.forEach { path ->
            val file = File(path).canonicalFile
            if (file.parentFile == imageDir) file.delete()
        }
    }
    suspend fun save(entry: DiaryEntity) = withContext(Dispatchers.IO) {
        val oldImages = db.diaryDao().getById(entry.id)?.images().orEmpty()
        val id = db.diaryDao().upsert(entry)
        entry.tagList().forEach { db.moodDao().addTag(TagEntity(it)) }
        deleteUnusedImages(oldImages.filterNot { it in entry.images() })
        id
    }
    suspend fun delete(entry: DiaryEntity) = withContext(Dispatchers.IO) {
        db.diaryDao().delete(entry)
        deleteUnusedImages(entry.images())
    }
    suspend fun getDiary(id: Long) = withContext(Dispatchers.IO) { db.diaryDao().getById(id) }
    suspend fun addFootprint(item: FootprintEntity) = withContext(Dispatchers.IO) { db.footprintDao().upsert(item) }
    suspend fun deleteFootprints() = withContext(Dispatchers.IO) { db.footprintDao().clearAll() }
    suspend fun deleteFootprint(date: String) = withContext(Dispatchers.IO) { db.footprintDao().deleteByDate(date) }
    suspend fun renameFootprint(date:String,name:String) = withContext(Dispatchers.IO) { db.footprintDao().rename(date,name.trim().take(120)) }
    suspend fun resolvePlaceName(item:FootprintEntity) = withContext(Dispatchers.IO) {
        if(item.placeName.isBlank())try {
            com.fuguang.localdiary.location.PlaceNames.resolve(getApplication<Application>(),item.latitude,item.longitude)?.let {
                db.footprintDao().nameIfEmpty(item.date,it)
            }
        } catch(e:kotlinx.coroutines.CancellationException) {throw e} catch(_:Exception) { /* Manual naming remains available. */ }
    }
    suspend fun allDiaries() = withContext(Dispatchers.IO) { db.diaryDao().getAll() }
    suspend fun allFootprints() = withContext(Dispatchers.IO) { db.footprintDao().getAll() }
    suspend fun importDiary(item: DiaryEntity) = withContext(Dispatchers.IO) { db.diaryDao().insertImported(item) }
    suspend fun importFootprint(item: FootprintEntity) = withContext(Dispatchers.IO) { db.footprintDao().insertImported(item) }

    suspend fun backupSnapshot() = db.withTransaction {
        db.diaryDao().getAll() to db.footprintDao().getAll()
    }

    suspend fun moodSnapshot() = db.withTransaction { db.moodDao().all() to db.moodDao().allTags() }
    suspend fun mergeBackup(diaries: List<DiaryEntity>, footprints: List<FootprintEntity>, moods: List<MoodEntity> = emptyList(), tags: List<TagEntity> = emptyList()): Pair<Int, Int> = db.withTransaction {
        moods.forEach { db.moodDao().importMood(it) }
        tags.forEach { db.moodDao().addTag(it) }
        diaries.flatMap { it.tagList() }.forEach { db.moodDao().addTag(TagEntity(it)) }
        val diaryCount = diaries.count { db.diaryDao().insertImported(it) != -1L }
        val footprintCount = footprints.count { db.footprintDao().insertImported(it) != -1L }
        diaryCount to footprintCount
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        db.diaryDao().clearAll()
        db.footprintDao().clearAll()
        File(getApplication<Application>().filesDir, "diary-images").deleteRecursively()
    }
}
