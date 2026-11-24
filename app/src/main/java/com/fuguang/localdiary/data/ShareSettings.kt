package com.fuguang.localdiary.data

import android.content.Context
import org.json.JSONObject

data class ShareSettings(val template:String="POETRY",val font:Float=36f,val spacing:Float=1.6f)

object ShareSettingsStore {
    fun read(context:Context):ShareSettings {
        val p=context.getSharedPreferences("shiling_share",0)
        return ShareSettings(p.getString("template","POETRY")?:"POETRY",p.getFloat("font",36f).coerceIn(28f,48f),p.getFloat("spacing",1.6f).coerceIn(1.2f,2f))
    }
    fun save(context:Context,value:ShareSettings) {
        context.getSharedPreferences("shiling_share",0).edit().putString("template",value.template).putFloat("font",value.font).putFloat("spacing",value.spacing).apply()
    }
    fun export(context:Context):JSONObject = read(context).let { JSONObject().put("template",it.template).put("font",it.font.toDouble()).put("spacing",it.spacing.toDouble()) }
    fun decode(json:JSONObject):ShareSettings {
        val font=json.optDouble("font",36.0);val spacing=json.optDouble("spacing",1.6)
        require(font.isFinite()&&font in 28.0..48.0&&spacing.isFinite()&&spacing in 1.2..2.0) { "备份中的分享参数无效" }
        return ShareSettings(json.optString("template","POETRY"),font.toFloat(),spacing.toFloat())
    }
}
