package com.zanoni.lardr.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingWritesQueue @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("pending_writes_wal", Context.MODE_PRIVATE)

    // commit() is synchronous — data is guaranteed on disk before this returns.
    fun enqueue(write: PendingWrite) {
        val ids = readIds().toMutableSet().apply { add(write.id) }
        prefs.edit()
            .putStringSet(KEY_IDS, ids)
            .putString(write.id, write.toJson())
            .commit()
    }

    // apply() is fine — only called after Firestore confirms.
    fun dequeue(writeId: String) {
        val ids = readIds().toMutableSet().apply { remove(writeId) }
        prefs.edit()
            .putStringSet(KEY_IDS, ids)
            .remove(writeId)
            .apply()
    }

    fun getAll(): List<PendingWrite> =
        readIds().mapNotNull { id ->
            prefs.getString(id, null)?.let { PendingWrite.fromJson(it) }
        }

    // Remove all previously queued writes of the same type targeting the same
    // store+resource, then enqueue the new one. This prevents stale duplicates
    // from accumulating (e.g. renaming a store twice before either is confirmed).
    fun replaceAndEnqueue(write: PendingWrite, isSameTarget: (PendingWrite) -> Boolean) {
        val toRemove = getAll().filter { isSameTarget(it) }
        val ids = readIds().toMutableSet().apply {
            toRemove.forEach { remove(it.id) }
            add(write.id)
        }
        prefs.edit().also { editor ->
            editor.putStringSet(KEY_IDS, ids)
            toRemove.forEach { editor.remove(it.id) }
            editor.putString(write.id, write.toJson())
        }.commit()
    }

    // Used by Firestore snapshot observers to overlay pending values,
    // preventing a stale snapshot from reverting an unconfirmed write.
    fun getPendingName(storeId: String): String? =
        getAll()
            .lastOrNull { it.type == WriteType.STORE_NAME && it.storeId == storeId }
            ?.params?.get("name")

    fun getPendingBought(storeId: String): Map<String, Boolean> =
        getAll()
            .filter { it.type == WriteType.INGREDIENT_BOUGHT && it.storeId == storeId }
            .associate { it.params.getValue("ingredientId") to it.params.getValue("bought").toBoolean() }

    private fun readIds(): Set<String> =
        prefs.getStringSet(KEY_IDS, emptySet())?.toSet() ?: emptySet()

    companion object {
        private const val KEY_IDS = "write_ids"
    }
}