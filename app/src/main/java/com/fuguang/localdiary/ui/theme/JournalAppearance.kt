package com.fuguang.localdiary.ui.theme

import android.content.Context

enum class JournalAppearance(val title: String, val description: String) {
    TIMELINE("简约时序", "浅色纸面 · 直线时间轴"),
    PAPER("纸页手记", "日期纸签 · 轻盈撕纸边缘");

    companion object {
        fun read(context: Context): JournalAppearance {
            val value = context.getSharedPreferences("fuguang_settings", Context.MODE_PRIVATE)
                .getString("journal_appearance", TIMELINE.name)
            return entries.firstOrNull { it.name == value } ?: TIMELINE
        }
    }

    fun save(context: Context) {
        context.getSharedPreferences("fuguang_settings", Context.MODE_PRIVATE)
            .edit().putString("journal_appearance", name).apply()
    }
}
