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
                    Log.w(tag, "Replay failed for ${write.id}, will retry on next start: ${e.message}")
                }
            }
        }
    }

    // ─── Public enqueue helpers ───────────────────────────────────────────────

    fun createStore(store: Store) {
        val write = PendingWrite(
            id = UUID.randomUUID().toString(),
            type = WriteType.STORE_CREATE,
            storeId = store.id,
            params = mapOf(
                "name" to store.name,
                "ownerId" to store.ownerId,
                "memberIds" to store.memberIds.joinToString(",")
            )
        )
        queue.replaceAndEnqueue(write) { existing ->
            existing.type == WriteType.STORE_CREATE && existing.storeId == store.id
        }
        executeAsync(write)
    }

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
        // Cancel any pending bought toggle for the same ingredient — irrelevant if deleted.
        queue.getAll()
            .filter {
                it.storeId == storeId &&
                        it.type == WriteType.INGREDIENT_BOUGHT &&
                        it.params["ingredientId"] == ingredientId
            }
            .forEach { queue.dequeue(it.id) }
        queue.replaceAndEnqueue(write) { existing ->
            existing.type == WriteType.INGREDIENT_DELETE &&
                    existing.storeId == storeId &&
                    existing.params["ingredientId"] == ingredientId
        }
        executeAsync(write)
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private fun executeAsync(write: PendingWrite) {
        applicationScope.launch {
            try {
                execute(write)
                queue.dequeue(write.id)
            } catch (e: Exception) {
                Log.e(tag, "Write ${write.type} ${write.id} failed: ${e.message}", e)
                // Intentionally not dequeuing — will be replayed on next start.
            }
        }
    }

    private suspend fun execute(write: PendingWrite) {
        when (write.type) {
            WriteType.STORE_CREATE -> {
                val store = Store(
                    id = write.storeId,
                    name = write.params.getValue("name"),
                    ownerId = write.params.getValue("ownerId"),
                    memberIds = write.params.getValue("memberIds").split(",")
                )
                dataSource.setDocument("stores", write.storeId, store)
            }

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