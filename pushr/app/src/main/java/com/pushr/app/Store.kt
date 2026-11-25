package com.pushr.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class Session(val start: Long, val end: Long, val count: Int)

object Store {
    private const val FILE = "pushr"
    private const val KEY = "sessions"

    fun day(ms: Long): LocalDate =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

    fun all(c: Context): List<Session> {
        val raw = c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val a = JSONArray(raw)
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Session(o.getLong("s"), o.getLong("e"), o.getInt("c"))
        }
    }

    fun add(c: Context, s: Session) {
        if (s.count <= 0) return
        val a = JSONArray()
        (all(c) + s).forEach {
            a.put(JSONObject().put("s", it.start).put("e", it.end).put("c", it.count))
        }
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, a.toString()).apply()
    }

    fun todayTotal(c: Context): Int {
        val t = LocalDate.now()
        return all(c).filter { day(it.start) == t }.sumOf { it.count }
    }

    fun daily(list: List<Session>): Map<LocalDate, Int> =
        list.groupBy { day(it.start) }.mapValues { e -> e.value.sumOf { it.count } }

    /** current streak, best streak */
    fun streaks(list: List<Session>): Pair<Int, Int> {
        val days = list.map { day(it.start) }.toSortedSet()
        if (days.isEmpty()) return 0 to 0
        var d = LocalDate.now()
        if (d !in days) d = d.minusDays(1)
        var cur = 0
        while (d in days) { cur++; d = d.minusDays(1) }
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (x in days) {
            run = if (prev != null && prev.plusDays(1) == x) run + 1 else 1
            if (run > best) best = run
            prev = x
        }
        return cur to best
    }
}
