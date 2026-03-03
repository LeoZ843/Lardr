package com.zanoni.lardr.data.repository

import com.zanoni.lardr.data.model.ConflictStrategy
import com.zanoni.lardr.data.model.Ingredient
import com.zanoni.lardr.data.model.Recipe
import com.zanoni.lardr.data.model.StarredIngredient
import com.zanoni.lardr.data.model.Store
import com.zanoni.lardr.data.remote.FirebaseDataSource
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoreRepositoryImpl @Inject constructor(
    private val dataSource: FirebaseDataSource
) : StoreRepository {

    override fun getStoresForUser(userId: String): Flow<List<Store>> {
        return dataSource.observeArrayContains("stores", "memberIds", userId, Store::class.java)
    }

    override fun observeStore(storeId: String): Flow<Store?> {
        return dataSource.observeDocument("stores", storeId, Store::class.java)
    }

    override suspend fun createStore(name: String, ownerId: String): Result<Store> {
        return try {
            val storeId = UUID.randomUUID().toString()
            val store = Store(
                id = storeId,
                name = name,
                ownerId = ownerId,
                memberIds = listOf(ownerId),
                shoppingList = emptyList(),
                starredIngredients = emptyList(),
                recipes = emptyList()
            )

            dataSource.setDocument("stores", storeId, store)
            Result.Success(store)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun deleteStore(storeId: String): Result<Unit> {
        return try {
            dataSource.deleteDocument("stores", storeId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun updateStoreName(storeId: String, name: String): Result<Unit> {
        return try {
            dataSource.updateDocument("stores", storeId, mapOf("name" to name))
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun addMemberToStore(storeId: String, userId: String): Result<Unit> {
        return try {
            dataSource.addArrayValue("stores", storeId, "memberIds", userId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun removeMemberFromStore(storeId: String, userId: String): Result<Unit> {
        return try {
            dataSource.removeArrayValue("stores", storeId, "memberIds", userId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // OPTIMIZED: Use atomic arrayUnion
    override suspend fun addIngredientToShoppingList(storeId: String, ingredient: Ingredient): Result<Unit> {
        return try {
            dataSource.addArrayValue("stores", storeId, "shoppingList", ingredient)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // OPTIMIZED: Batch add multiple ingredients in single write
    override suspend fun addIngredientsToShoppingList(storeId: String, ingredients: List<Ingredient>): Result<Unit> {
        return try {
            android.util.Log.d("StoreRepository", "addIngredientsToShoppingList: Adding ${ingredients.size} ingredients")

            val store = dataSource.getDocument("stores", storeId, Store::class.java)
            if (store == null) {
                android.util.Log.e("StoreRepository", "Store not found: $storeId")
                return Result.Error(Exception("Store not found"))
            }

            android.util.Log.d("StoreRepository", "Current list size: ${store.shoppingList.size}")

            val updatedList = store.shoppingList + ingredients

            android.util.Log.d("StoreRepository", "New list size: ${updatedList.size}, updating Firestore...")

            dataSource.updateDocument("stores", storeId, mapOf("shoppingList" to updatedList))

            android.util.Log.d("StoreRepository", "Successfully completed addIngredientsToShoppingList")

            Result.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("StoreRepository", "ERROR adding ingredients: ${e.message}", e)
            Result.Error(e)
        }
    }

    // For update we need to read-modify-write, but only the specific ingredient
    override suspend fun updateIngredient(storeId: String, ingredient: Ingredient): Result<Unit> {
        return try {
            val store = dataSource.getDocument("stores", storeId, Store::class.java)
                ?: return Result.Error(Exception("Store not found"))

            val updatedList = store.shoppingList.map {
                if (it.id == ingredient.id) ingredient else it
            }

            dataSource.updateDocument("stores", storeId, mapOf("shoppingList" to updatedList))
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun deleteIngredient(storeId: String, ingredientId: String): Result<Unit> {
        return try {
            android.util.Log.d("StoreRepository", "deleteIngredient: $ingredientId")

            val store = dataSource.getDocument("stores", storeId, Store::class.java)
            if (store == null) {
                android.util.Log.w("StoreRepository", "Store not found for delete")
                return Result.Error(Exception("Store not found"))
            }

            // Filter by ID, not remove by object (handles duplicates)
            val updatedList = store.shoppingList.filter { it.id != ingredientId }

            android.util.Log.d("StoreRepository", "Updating list: ${store.shoppingList.size} -> ${updatedList.size}")

            dataSource.updateDocument("stores", storeId, mapOf("shoppingList" to updatedList))

            Result.Success(Unit)
        } catch (e: Exception) {
            // Don't re-throw cancellation - let operation complete for user-initiated actions
            android.util.Log.e("StoreRepository", "ERROR in deleteIngredient: ${e.message}", e)
            Result.Error(e)
        }
    }

    override suspend fun markIngredientAsBought(storeId: String, ingredientId: String, bought: Boolean): Result<Unit> {
        return try {
            android.util.Log.d("StoreRepository", "markIngredientAsBought: $ingredientId, bought=$bought")

            val store = dataSource.getDocument("stores", storeId, Store::class.java)
            if (store == null) {
                android.util.Log.w("StoreRepository", "Store not found for mark")
                return Result.Error(Exception("Store not found"))
            }

            android.util.Log.d("StoreRepository", "Found store with ${store.shoppingList.size} items")

            val ingredient = store.shoppingList.find { it.id == ingredientId }
            if (ingredient == null) {
                android.util.Log.w("StoreRepository", "Ingredient not found: $ingredientId")
                return Result.Error(Exception("Ingredient not found: $ingredientId"))
            }

            android.util.Log.d("StoreRepository", "Found ingredient: ${ingredient.name}")

            val updatedIngredient = ingredient.copy(bought = bought)
            val updatedList = store.shoppingList.map {
                if (it.id == ingredientId) updatedIngredient else it
            }

            android.util.Log.d("StoreRepository", "Updating Firestore with new list (${updatedList.size} items)")

            dataSource.updateDocument("stores", storeId, mapOf("shoppingList" to updatedList))

            android.util.Log.d("StoreRepository", "Successfully updated Firestore")

            Result.Success(Unit)
        } catch (e: Exception) {
            // Don't re-throw cancellation - let operation complete for user-initiated actions
            android.util.Log.e("StoreRepository", "ERROR in markIngredientAsBought: ${e.message}", e)
            Result.Error(e)
        }
    }

    // OPTIMIZED: Use atomic arrayUnion
    override suspend fun addStarredIngredient(storeId: String, starredIngredient: StarredIngredient): Result<Unit> {
        return try {
            dataSource.addArrayValue("stores", storeId, "starredIngredients", starredIngredient)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun updateStarredIngredient(storeId: String, starredIngredient: StarredIngredient): Result<Unit> {
        return try {
            val store = dataSource.getDocument("stores", storeId, Store::class.java)
                ?: return Result.Error(Exception("Store not found"))

            val updatedList = store.starredIngredients.map {
                if (it.id == starredIngredient.id) starredIngredient else it
            }

            dataSource.updateDocument("stores", storeId, mapOf("starredIngredients" to updatedList))
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // OPTIMIZED: Use atomic arrayRemove
    override suspend fun deleteStarredIngredient(storeId: String, starredIngredientId: String): Result<Unit> {
        return try {
            val store = dataSource.getDocument("stores", storeId, Store::class.java)
                ?: return Result.Error(Exception("Store not found"))

            val starred = store.starredIngredients.find { it.id == starredIngredientId }
                ?: return Result.Error(Exception("Starred ingredient not found"))

            dataSource.removeArrayValue("stores", storeId, "starredIngredients", starred)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // OPTIMIZED: Use atomic arrayUnion
    override suspend fun addRecipe(storeId: String, recipe: Recipe): Result<Unit> {
        return try {
            dataSource.addArrayValue("stores", storeId, "recipes", recipe)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun updateRecipe(storeId: String, recipe: Recipe): Result<Unit> {
        return try {
            val store = dataSource.getDocument("stores", storeId, Store::class.java)
                ?: return Result.Error(Exception("Store not found"))

            val updatedList = store.recipes.map {
                if (it.id == recipe.id) recipe else it
            }

            dataSource.updateDocument("stores", storeId, mapOf("recipes" to updatedList))
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // OPTIMIZED: Use atomic arrayRemove
    override suspend fun deleteRecipe(storeId: String, recipeId: String): Result<Unit> {
        return try {
            val store = dataSource.getDocument("stores", storeId, Store::class.java)
                ?: return Result.Error(Exception("Store not found"))

            val recipe = store.recipes.find { it.id == recipeId }
                ?: return Result.Error(Exception("Recipe not found"))

            dataSource.removeArrayValue("stores", storeId, "recipes", recipe)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // OPTIMIZED: Batch add ingredients with single update
    override suspend fun addRecipeToShoppingList(
        storeId: String,
        recipeId: String,
        selectedIngredients: List<String>,
        conflictResolution: Map<String, ConflictStrategy>
    ): Result<Unit> {
        return try {
            val store = dataSource.getDocument("stores", storeId, Store::class.java)
                ?: return Result.Error(Exception("Store not found"))

            val recipe = store.recipes.find { it.id == recipeId }
                ?: return Result.Error(Exception("Recipe not found"))

            val newIngredients = mutableListOf<Ingredient>()
            val updatedIngredients = mutableListOf<Ingredient>()

            recipe.ingredients
                .filter { selectedIngredients.isEmpty() || selectedIngredients.contains(it.name) }
                .forEach { recipeIngredient ->
                    val existingIngredient = store.shoppingList.find {
                        it.name.equals(recipeIngredient.name, ignoreCase = true) && !it.bought
                    }

                    if (existingIngredient != null) {
                        val strategy = conflictResolution[recipeIngredient.name]
                            ?: recipe.conflictStrategy
                            ?: ConflictStrategy.ASK

                        when (strategy) {
                            ConflictStrategy.IGNORE -> {
                                // Keep existing, don't add new
                            }
                            ConflictStrategy.INCREASE -> {
                                val updatedQuantity = combineQuantities(
                                    existingIngredient.quantity,
                                    recipeIngredient.quantity ?: ""
                                )
                                updatedIngredients.add(
                                    existingIngredient.copy(quantity = updatedQuantity)
                                )
                            }
                            ConflictStrategy.REPLACE -> {
                                // Remove existing and add new
                                newIngredients.add(
                                    Ingredient(
                                        id = UUID.randomUUID().toString(),
                                        name = recipeIngredient.name,
                                        quantity = recipeIngredient.quantity ?: "",
                                        bought = false,
                                        addedBy = recipeId,
                                        addedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                            ConflictStrategy.ASK -> {
                                // Should not reach here in repository
                                // Dialog should be shown in UI layer
                            }
                        }
                    } else {
                        newIngredients.add(
                            Ingredient(
                                id = UUID.randomUUID().toString(),
                                name = recipeIngredient.name,
                                quantity = recipeIngredient.quantity ?: "",
                                bought = false,
                                addedBy = recipeId,
                                addedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }

            // SINGLE DATABASE UPDATE with all changes
            val finalList = store.shoppingList
                .map { existing ->
                    updatedIngredients.find { it.id == existing.id } ?: existing
                }
                .filter { existing ->
                    // Remove items that are being replaced
                    newIngredients.none { new ->
                        new.name.equals(existing.name, ignoreCase = true) && !existing.bought
                    }
                } + newIngredients

            dataSource.updateDocument("stores", storeId, mapOf("shoppingList" to finalList))
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun processWeeklyReset(storeId: String, currentWeek: Int): Result<Unit> {
        return try {
            val store = dataSource.getDocument("stores", storeId, Store::class.java)
                ?: return Result.Error(Exception("Store not found"))

            val newIngredients = mutableListOf<Ingredient>()

            store.starredIngredients
                .filter { starred ->
                    starred.periodicity != null &&
                            (starred.lastAddedWeek == null || currentWeek - starred.lastAddedWeek >= starred.periodicity)
                }
                .forEach { starred ->
                    newIngredients.add(
                        Ingredient(
                            id = UUID.randomUUID().toString(),
                            name = starred.name,
                            quantity = starred.defaultQuantity,
                            bought = false,
                            addedBy = starred.id,
                            addedAt = System.currentTimeMillis()
                        )
                    )
                }

            val updatedStarred = store.starredIngredients.map { starred ->
                if (starred.periodicity != null && newIngredients.any { it.addedBy == starred.id }) {
                    starred.copy(lastAddedWeek = currentWeek)
                } else {
                    starred
                }
            }

            // SINGLE DATABASE UPDATE
            dataSource.updateDocument("stores", storeId, mapOf(
                "shoppingList" to (store.shoppingList + newIngredients),
                "starredIngredients" to updatedStarred
            ))

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun getConflictsForWeek(storeId: String, currentWeek: Int): Result<List<Pair<String, String>>> {
        return try {
            val store = dataSource.getDocument("stores", storeId, Store::class.java)
                ?: return Result.Error(Exception("Store not found"))

            val conflicts = mutableListOf<Pair<String, String>>()

            store.starredIngredients
                .filter { starred ->
                    starred.periodicity != null &&
                            (starred.lastAddedWeek == null || currentWeek - starred.lastAddedWeek >= starred.periodicity) &&
                            starred.conflictStrategy == ConflictStrategy.ASK
                }
                .forEach { starred ->
                    val existing = store.shoppingList.find {
                        it.name.equals(starred.name, ignoreCase = true) && !it.bought
                    }
                    if (existing != null) {
                        conflicts.add(starred.id to starred.name)
                    }
                }

            Result.Success(conflicts)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    private fun combineQuantities(qty1: String, qty2: String): String {
        if (qty1.isBlank()) return qty2
        if (qty2.isBlank()) return qty1

        val num1 = qty1.filter { it.isDigit() || it == '.' }.toDoubleOrNull()
        val num2 = qty2.filter { it.isDigit() || it == '.' }.toDoubleOrNull()
        val unit1 = qty1.filter { it.isLetter() || it == ' ' }.trim()
        val unit2 = qty2.filter { it.isLetter() || it == ' ' }.trim()

        return if (num1 != null && num2 != null && unit1.equals(unit2, ignoreCase = true)) {
            "${(num1 + num2).toInt()}$unit1"
        } else {
            "$qty1 + $qty2"
        }
    }
}