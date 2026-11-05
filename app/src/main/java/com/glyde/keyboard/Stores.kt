package com.glyde.keyboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ClipItem(val text: String, val pinned: Boolean, val time: Long)

data class Snippet(val trigger: String, val text: String)

private fun dataPrefs(context: Context) =
    context.applicationContext.getSharedPreferences("glyde_data", Context.MODE_PRIVATE)

/** Clipboard history. Pinned items stay on top and are never trimmed away. */
class ClipboardStore(context: Context) {

    private val sp = dataPrefs(context)
    private val items = ArrayList<ClipItem>()

    init {
        try {
            val arr = JSONArray(sp.getString("clips", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items.add(ClipItem(o.getString("t"), o.optBoolean("p", false), o.optLong("ts", 0L)))
            }
        } catch (e: Exception) {
            items.clear()
        }
    }

    /** Pinned first, then newest first. */
    fun list(): List<ClipItem> =
        items.sortedWith(compareByDescending<ClipItem> { it.pinned }.thenByDescending { it.time })

    fun add(text: String) {
        val t = text.trim()
        if (t.isEmpty() || t.length > 5000) return
        val existing = items.indexOfFirst { it.text == t }
        val pinned = existing >= 0 && items[existing].pinned
        if (existing >= 0) items.removeAt(existing)
        items.add(ClipItem(t, pinned, System.currentTimeMillis()))
        trim()
        save()
    }

    fun togglePin(item: ClipItem) {
        val i = items.indexOfFirst { it.text == item.text }
        if (i < 0) return
        items[i] = items[i].copy(pinned = !items[i].pinned)
        save()
    }

    fun remove(item: ClipItem) {
        items.removeAll { it.text == item.text }
        save()
    }

    fun clearUnpinned() {
        items.removeAll { !it.pinned }
        save()
    }

    private fun trim() {
        val unpinned = items.filter { !it.pinned }.sortedByDescending { it.time }
        if (unpinned.size > 40) {
            val drop = unpinned.drop(40).map { it.text }.toSet()
            items.removeAll { !it.pinned && it.text in drop }
        }
    }

    private fun save() {
        val arr = JSONArray()
        for (c in items) {
            arr.put(JSONObject().put("t", c.text).put("p", c.pinned).put("ts", c.time))
        }
        sp.edit().putString("clips", arr.toString()).apply()
    }
}

/** Text shortcuts: type a trigger such as /mail and press space to expand it. */
class SnippetStore(context: Context) {

    private val sp = dataPrefs(context)

    fun list(): List<Snippet> {
        val out = ArrayList<Snippet>()
        try {
            val arr = JSONArray(sp.getString("snippets", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Snippet(o.getString("k"), o.getString("v")))
            }
        } catch (e: Exception) {
            // ignore corrupt data
        }
        return out
    }

    fun find(trigger: String): Snippet? = list().firstOrNull { it.trigger == trigger }

    fun upsert(oldTrigger: String?, snippet: Snippet) {
        val all = list().filter { it.trigger != oldTrigger && it.trigger != snippet.trigger }.toMutableList()
        all.add(snippet)
        write(all)
    }

    fun remove(trigger: String) {
        write(list().filter { it.trigger != trigger })
    }

    private fun write(all: List<Snippet>) {
        val arr = JSONArray()
        for (s in all) arr.put(JSONObject().put("k", s.trigger).put("v", s.text))
        sp.edit().putString("snippets", arr.toString()).apply()
    }
}
