package com.zanoni.lardr.data.local

import android.util.Log
import com.zanoni.lardr.data.model.Store
import com.zanoni.lardr.data.remote.FirebaseDataSource
import com.zanoni.lardr.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingWritesManager @Inject constructor(
    val queue: PendingWritesQueue,
    private val dataSource: FirebaseDataSource,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    private val tag = "PendingWritesManager"

    // Called from Application.onCreate. Replays writes that were enqueued
    // but never confirmed because the process was killed mid-flight.
    // The "only replay if interrupted" guarantee comes from the queue itself:
    // an entry is only present if enqueue() was called but dequeue() was not.
    fun replayPendingWrites() {
        val pending = queue.getAll()
        if (pending.isEmpty()) return
        Log.d(tag, "Replaying ${pending.size} pending write(s) after restart")
        applicationScope.launch {
            pending.forEach { write ->
                try {
                    execute(write)
                    queue.dequeue(write.id)
                    Log.d(tag, "Replayed ${write.type} ${write.id}")
                } catch (e: Exception) {
                    Log.w(
                        tag,
                        "Replay failed for ${write.id}, will retry on next start: ${e.message}"
                    )
                }
            }
        }
    }

    // ─── Public enqueue helpers ───────────────────────────────────────────────

    fun updateStoreName(storeId: String, newName: String) {
        val write = PendingWrite(
            id = UUID.randomUUID().toString(),
            type = WriteType.STORE_NAME,
            storeId = storeId,
            params = mapOf("name" to newName)
        )
        queue.replaceAndEnqueue(write) { existing ->
            existing.type == WriteType.STORE_NAME && existing.storeId == storeId
        }
        executeAsync(write)
    }

    fun markIngredientBought(storeId: String, ingredientId: String, bought: Boolean) {
        val write = PendingWrite(
            id = UUID.randomUUID().toString(),
            type = WriteType.INGREDIENT_BOUGHT,
            storeId = storeId,
            params = mapOf("ingredientId" to ingredientId, "bought" to bought.toString())
        )
        queue.replaceAndEnqueue(write) { existing ->
            existing.type == WriteType.INGREDIENT_BOUGHT &&
                    existing.storeId == storeId &&
                    existing.params["ingredientId"] == ingredientId
        }
        executeAsync(write)
    }

    fun deleteIngredient(storeId: String, ingredientId: String) {
        val write = PendingWrite(
            id = UUID.randomUUID().toString(),
            type = WriteType.INGREDIENT_DELETE,
            storeId = storeId,
            params = mapOf("ingredientId" to ingredientId)
        )
        // If there is a pending bought toggle for this ingredient it is now irrelevant.
        val idsToRemove = queue.getAll().filter { existing ->
            existing.storeId == storeId &&
                    existing.type == WriteType.INGREDIENT_BOUGHT &&
                    existing.params["ingredientId"] == ingredientId
        }
        idsToRemove.forEach { queue.dequeue(it.id) }

        queue.replaceAndEnqueue(write) { existing ->
            existing.type == WriteType.INGREDIENT_DELETE &&
                    existing.storeId == storeId &&
                    existing.params["ingredientId"] == ingredientId
        }
        executeAsync(write)
    }

    // Launches in applicationScope so it outlives the ViewModel.
    // enqueue() has already committed to disk, so a process kill between
    // here and dequeue() results in a replay on next launch — not data loss.
    private fun executeAsync(write: PendingWrite) {
        applicationScope.launch {
            try {
                execute(write)
                queue.dequeue(write.id)
            } catch (e: Exception) {
                Log.w(tag, "Write ${write.id} failed, queued for replay: ${e.message}")
            }
        }
    }

    // All Firestore writes here use the awaited updateDocument so Firestore's
    // local SQLite persistence commits before this suspend function returns.
    private suspend fun execute(write: PendingWrite) {
        when (write.type) {
            WriteType.STORE_NAME -> {
                dataSource.updateDocument(
                    collection = "stores",
                    documentId = write.storeId,
                    updates = mapOf("name" to write.params.getValue("name"))
                )
            }

            WriteType.INGREDIENT_BOUGHT -> {
                val store = dataSource.getDocument("stores", write.storeId, Store::class.java)
                    ?: throw Exception("Store ${write.storeId} not found during replay")
                val ingredientId = write.params.getValue("ingredientId")
                val bought = write.params.getValue("bought").toBoolean()
                val updatedList = store.shoppingList.map {
                    if (it.id == ingredientId) it.copy(bought = bought) else it
                }
                dataSource.updateDocument(
                    collection = "stores",
                    documentId = write.storeId,
                    updates = mapOf("shoppingList" to updatedList)
                )
            }

            WriteType.INGREDIENT_DELETE -> {
                val store = dataSource.getDocument("stores", write.storeId, Store::class.java)
                    ?: throw Exception("Store ${write.storeId} not found during replay")
                val ingredientId = write.params.getValue("ingredientId")
                val updatedList = store.shoppingList.filter { it.id != ingredientId }
                dataSource.updateDocument(
                    collection = "stores",
                    documentId = write.storeId,
                    updates = mapOf("shoppingList" to updatedList)
                )
            }
        }
    }
}