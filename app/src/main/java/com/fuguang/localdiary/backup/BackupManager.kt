package com.fuguang.localdiary.backup

import android.content.Context
import android.net.Uri
import com.fuguang.localdiary.data.DiaryEntity
import com.fuguang.localdiary.data.DiaryViewModel
import com.fuguang.localdiary.data.FootprintEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.UUID
import java.time.LocalDate
import java.time.LocalTime

object BackupManager {
    suspend fun export(context: Context, uri: Uri, viewModel: DiaryViewModel): Int = withContext(Dispatchers.IO) {
        val (diaries, footprints) = viewModel.backupSnapshot()
        val root = JSONObject().put("format", "fuguang-backup").put("version", 1)
        val (moods, tags) = viewModel.moodSnapshot()
        root.put("moods", JSONArray().apply { moods.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("icon", it.icon).put("color", it.color)) } })
        root.put("tagLibrary", JSONArray().apply { tags.forEach { put(it.name) } })
        val diaryArray = JSONArray()
        val exportedImages = mutableSetOf<String>()
        ZipOutputStream(context.contentResolver.openOutputStream(uri) ?: error("无法创建备份文件")).use { zip ->
            diaries.forEach { diary ->
                val images = JSONArray()
                diary.images().forEach { path ->
                    val file = File(path)
                    if (file.exists() && file.isFile) {
                        val safeName = file.name
                        images.put(safeName)
                        if (exportedImages.add(safeName)) {
                            zip.putNextEntry(ZipEntry("images/$safeName"))
                            file.inputStream().use { input -> input.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
                diaryArray.put(
                    JSONObject()
                        .put("id", diary.id)
                        .put("date", diary.date)
                        .put("time", diary.time)
                        .put("body", diary.body)
                        .put("tags", diary.tags)
                        .put("mood", diary.mood).put("moodId", diary.moodId).put("moodIcon", diary.moodIcon).put("moodColor", diary.moodColor)
                        .put("images", images)
                        .put("createdAt", diary.createdAt)
                        .put("updatedAt", diary.updatedAt)
                )
            }
            val footprintArray = JSONArray()
            footprints.forEach { item ->
                footprintArray.put(
                    JSONObject()
                        .put("date", item.date)
                        .put("time", item.time)
                        .put("latitude", item.latitude)
                        .put("longitude", item.longitude)
                        .put("accuracyMeters", item.accuracyMeters.toDouble())
                )
            }
            root.put("diaries", diaryArray).put("footprints", footprintArray)
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(root.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        diaries.size
    }

    suspend fun import(context: Context, uri: Uri, viewModel: DiaryViewModel): Pair<Int, Int> = withContext(Dispatchers.IO) {
        // Stage and validate the complete archive before changing private files or Room.
        val staging = File(context.cacheDir, "restore-${UUID.randomUUID()}").apply { mkdirs() }
        val imageDir = File(context.filesDir, "diary-images").apply { mkdirs() }
        val installed = mutableListOf<File>()
        var committed = false
        try {
            var manifest: ByteArray? = null
            val stagedImages = mutableMapOf<String, File>()
            var totalBytes = 0L
            var entries = 0
            val seen = mutableSetOf<String>()
            fun copyLimited(input: java.io.InputStream, output: java.io.OutputStream, limit: Long) {
                val buffer = ByteArray(8192)
                var bytes = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    bytes += count
                    totalBytes += count
                    require(bytes <= limit && totalBytes <= 512L * 1024 * 1024) { "备份文件过大" }
                    output.write(buffer, 0, count)
                }
            }
            ZipInputStream(context.contentResolver.openInputStream(uri) ?: error("无法读取备份文件")).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(++entries <= 10000) { "备份文件条目过多" }
                    require(seen.add(entry.name)) { "备份包含重复文件" }
                    when {
                        entry.isDirectory -> Unit
                        entry.name == "backup.json" -> {
                            val buffer = ByteArrayOutputStream()
                            copyLimited(zip, buffer, 16L * 1024 * 1024)
                            manifest = buffer.toByteArray()
                        }
                        entry.name.startsWith("images/") -> {
                            val name = entry.name.removePrefix("images/")
                            require(name.isNotBlank() && name != "." && name != ".." && '/' !in name && name.none { it.code == 92 }) { "备份图片路径无效" }
                            val file = File(staging, UUID.randomUUID().toString())
                            file.outputStream().use { copyLimited(zip, it, 64L * 1024 * 1024) }
                            stagedImages[name] = file
                        }
                        else -> error("备份包含未知文件")
                    }
                    zip.closeEntry()
                }
            }
            val json = JSONObject(String(manifest ?: error("备份文件缺少 backup.json"), Charsets.UTF_8))
            require(json.optString("format") == "fuguang-backup" && json.getInt("version") == 1) { "不支持此备份格式或版本" }
            val diaryArray = json.getJSONArray("diaries")
            val footprintArray = json.getJSONArray("footprints")
            val (existingDiaries, existingFootprints) = viewModel.backupSnapshot()
            val diaryIds = existingDiaries.mapTo(mutableSetOf()) { it.id }
            val footprintDates = existingFootprints.mapTo(mutableSetOf()) { it.date }
            val imageMapping = mutableMapOf<String, String>()
            val diaries = (0 until diaryArray.length()).mapNotNull { index ->
                val item = diaryArray.getJSONObject(index)
                val id = item.getLong("id")
                require(id > 0) { "日记 ID 无效" }
                val date = item.getString("date").also { LocalDate.parse(it) }
                val time = item.getString("time").also { LocalTime.parse(it) }
                val body = item.getString("body")
                require(body.isNotBlank()) { "日记内容为空" }
                val names = item.getJSONArray("images")
                val paths = (0 until names.length()).map { imageIndex ->
                    val name = names.getString(imageIndex)
                    val staged = stagedImages[name] ?: error("备份缺少图片：$name")
                    imageMapping.getOrPut(name) {
                        val extension = File(name).extension.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) } ?: "img"
                        File(imageDir, "${UUID.randomUUID()}.$extension").absolutePath
                    }.also { require(staged.length() > 0) { "备份图片为空" } }
                }
                if (!diaryIds.add(id)) null else DiaryEntity(
                    id = id, date = date, time = time, body = body,
                    tags = item.optString("tags"), mood = item.optString("mood"), moodId = item.optString("moodId"), moodIcon = item.optString("moodIcon"), moodColor = item.optLong("moodColor"),
                    imagePaths = paths.joinToString("\n"),
                    createdAt = item.getLong("createdAt"), updatedAt = item.getLong("updatedAt")
                )
            }
            val footprints = (0 until footprintArray.length()).mapNotNull { index ->
                val item = footprintArray.getJSONObject(index)
                val date = item.getString("date").also { LocalDate.parse(it) }
                val time = item.getString("time").also { LocalTime.parse(it) }
                val latitude = item.getDouble("latitude")
                val longitude = item.getDouble("longitude")
                val accuracy = item.getDouble("accuracyMeters")
                require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0 && accuracy.isFinite() && accuracy >= 0 && accuracy <= Float.MAX_VALUE) { "足迹坐标无效" }
                if (!footprintDates.add(date)) null else FootprintEntity(date, time, latitude, longitude, accuracy.toFloat())
            }
            val moodArray = json.optJSONArray("moods") ?: JSONArray()
            val importedMoods = (0 until moodArray.length()).map { i ->
                val m = moodArray.getJSONObject(i)
                com.fuguang.localdiary.data.MoodEntity(m.getString("id"), m.getString("name"), m.getString("icon"), m.getLong("color")).also { require(it.id.isNotBlank() && it.name.isNotBlank()) }
            }
            val tagArray = json.optJSONArray("tagLibrary") ?: JSONArray()
            val importedTags = (0 until tagArray.length()).map { com.fuguang.localdiary.data.TagEntity(tagArray.getString(it)) }
            val neededPaths = diaries.flatMap { it.images() }.toSet()
            imageMapping.filterValues { it in neededPaths }.forEach { (name, path) ->
                val destination = File(path)
                installed.add(destination)
                stagedImages.getValue(name).copyTo(destination)
            }
            val result = viewModel.mergeBackup(diaries, footprints, importedMoods, importedTags)
            committed = true
            result
        } finally {
            if (!committed) installed.forEach { it.delete() }
            staging.deleteRecursively()
        }
    }
}
