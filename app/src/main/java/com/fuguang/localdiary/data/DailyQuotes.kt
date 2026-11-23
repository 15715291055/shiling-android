package com.fuguang.localdiary.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

data class DailyQuote(
    val id: String,
    val text: String,
    val author: String,
    val work: String,
    val sourceUrl: String,
    val translator: String = ""
)

object DailyQuotes {
    @Volatile private var cached: List<DailyQuote>? = null

    fun library(context: Context): List<DailyQuote> = cached ?: synchronized(this) {
        cached ?: run {
            val data = JSONArray(context.assets.open("daily_quotes.json").bufferedReader().use { it.readText() })
            val quotes = (0 until data.length()).map { index ->
                val row = data.getJSONObject(index)
                DailyQuote(row.getString("id"), row.getString("text"), row.getString("author"),
                    row.getString("work"), row.getString("sourceUrl"), row.optString("translator"))
            }
            require(quotes.size >= 1096) { "每日一言词库不足三年" }
            require(quotes.map { it.id }.distinct().size == quotes.size)
            require(quotes.map { normalized(it.text) }.distinct().size == quotes.size) { "每日一言含重复内容" }
            require(quotes.all { it.text.isNotBlank() && it.author.isNotBlank() && it.work.isNotBlank() && it.sourceUrl.startsWith("https://") })
            quotes.also { cached = it }
        }
    }

    fun normalized(text: String) = text.filter { it.isLetterOrDigit() }

    /** Calendar days, not time since last launch. Never wrap the library with modulo. */
    fun indexFor(start: LocalDate, today: LocalDate, count: Int): Int {
        require(count > 0)
        return ChronoUnit.DAYS.between(start, today).coerceIn(0L, (count - 1).toLong()).toInt()
    }

    @Synchronized
    fun today(context: Context, date: LocalDate = LocalDate.now()): DailyQuote {
        val prefs = context.getSharedPreferences("shiling_daily_quote", Context.MODE_PRIVATE)
        if (!prefs.contains("start_epoch_day")) {
            check(prefs.edit().putLong("start_epoch_day", date.toEpochDay()).commit())
        }
        val start = LocalDate.ofEpochDay(prefs.getLong("start_epoch_day", date.toEpochDay()))
        val quotes = library(context)
        val calculated = indexFor(start, date, quotes.size)
        // Clock rollback must not take the reader back through already displayed quotes.
        val index = maxOf(calculated, prefs.getInt("highest_index", 0)).coerceAtMost(quotes.lastIndex)
        if (index != prefs.getInt("highest_index", -1)) {
            check(prefs.edit().putInt("highest_index", index).commit())
        }
        return quotes[index]
    }
}

@Composable
fun rememberDailyQuote(context: Context): DailyQuote? {
    var date by remember { mutableStateOf(LocalDate.now()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) date = LocalDate.now()
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { date = LocalDate.now() }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        owner.lifecycle.addObserver(observer)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { owner.lifecycle.removeObserver(observer); context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(date) {
        val now = ZonedDateTime.now()
        val next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        delay(Duration.between(now, next).toMillis().coerceAtLeast(1000L) + 500)
        date = LocalDate.now()
    }
    var quote by remember { mutableStateOf<DailyQuote?>(null) }
    LaunchedEffect(date) {
        quote = withContext(Dispatchers.IO) { DailyQuotes.today(context, date) }
    }
    return quote
}
