package com.fuguang.localdiary.backup

import android.content.Context
import android.graphics.BitmapFactory
import com.fuguang.localdiary.data.ShareSettings
import com.fuguang.localdiary.data.ShareSettingsStore
import com.fuguang.localdiary.ui.theme.AppearanceStore
import com.fuguang.localdiary.ui.theme.JournalAppearance
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object AppearanceBackup {
    const val BACKGROUND_ENTRY="appearance/background"

    fun export(context:Context,zip:ZipOutputStream):JSONObject {
        val settings=JSONObject(context.getSharedPreferences("shiling_appearance",0).getString("settings","{}")?:"{}")
        val image=settings.optString("image").takeIf { it.isNotBlank() }?.let { File(it) }
        if(image!=null) {
            require(image.isFile&&image.length()>0) { "背景图片已丢失，请重新选择或移除背景后再备份" }
            zip.putNextEntry(ZipEntry(BACKGROUND_ENTRY))
            image.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        settings.put("image","") // Never restore a path from another phone.
        return JSONObject().put("settings",settings).put("hasBackground",image!=null)
            .put("journalStyle",JournalAppearance.read(context).name).put("share",ShareSettingsStore.export(context))
    }

    class Prepared(val settings:JSONObject,val style:JournalAppearance,val share:ShareSettings?,val image:File?) {
        fun apply(context:Context) {
            val previous=AppearanceStore.current.image
            context.getSharedPreferences("shiling_appearance",0).edit().putString("settings",settings.toString()).apply()
            AppearanceStore.load(context)
            style.save(context)
            share?.let { ShareSettingsStore.save(context,it) }
            if(previous.isNotBlank()&&previous!=settings.optString("image")) {
                File(previous).takeIf { it.parentFile==context.filesDir&&it.name.startsWith("background-") }?.delete()
            }
        }
    }

    fun prepare(context:Context,json:JSONObject?,background:File?,installed:MutableList<File>):Prepared? {
        if(json==null)return null
        val settings=JSONObject(json.getJSONObject("settings").toString()).put("image","")
        val limits=mapOf("opacity" to 1.0,"cardOpacity" to 1.0,"navigationOpacity" to 1.0,"readingProtection" to 1.0,"blur" to 25.0,"glassBlur" to 30.0)
        limits.forEach { (key,maximum) -> if(settings.has(key)) {
            val value=settings.getDouble(key)
            require(value.isFinite()&&value in 0.0..maximum) { "备份中的外观参数无效" }
        } }
        listOf("background","accent","title","section","auxiliary","body","date").forEach { key ->
            if(settings.has(key))require(settings.getLong(key) in Int.MIN_VALUE.toLong()..0xFFFFFFFFL) { "备份中的颜色无效" }
        }
        val style=JournalAppearance.valueOf(json.getString("journalStyle"))
        val share=json.optJSONObject("share")?.let { ShareSettingsStore.decode(it) }
        val image=if(json.optBoolean("hasBackground")) {
            require(background!=null&&background.length()>0) { "备份缺少背景图片" }
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeFile(background.absolutePath,bounds)
            require(bounds.outWidth>0&&bounds.outHeight>0) { "备份背景图片无效" }
            File(context.filesDir,"background-restored-${UUID.randomUUID()}.img").also {
                installed.add(it);background.copyTo(it);settings.put("image",it.absolutePath)
            }
        } else null
        return Prepared(settings,style,share,image)
    }
}
