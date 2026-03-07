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

    override fun getStoresForUser(userId: String): Flow<List<Store>> =
        dataSource.observeArrayContains("stores", "memberIds", userId, Store::class.java)

    override fun observeStore(storeId: String): Flow<Store?> =
        dataSource.observeDocument("stores", storeId, Store::class.java)

    // createStore awaits because the ViewModel navigates to the store immediately after.
    override suspend fun createStore(name: String, ownerId: String): Result<Store> {
        return try {
            val storeId = UUID.randomUUID().toString()
            val store = Store(id = storeId, name = name, ownerId = ownerId, memberIds = listOf(ownerId))
            dataSource.setDocument("stores", storeId, store)
            Result.Success(store)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun deleteStore(storeId: String): Result<Unit> {
        return try {
            dataSource.deleteDocumentAsync("stores", storeId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun updateStoreName(storeId: String, name: String): Result<Unit> {
        return try {
            dataSource.updateDocumentAsync("stores", storeId, mapOf("name" to name))
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun addMemberToStore(storeId: String, userId: String): Result<Unit> {
        return try {
            dataSource.addArrayValueAsync("stores", storeId, "memberIds", userId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun removeMemberFromStore(storeId: String, userId: String): Result<Unit> {
        return try {
            dataSource.removeArrayValueAsync("stores", storeId, "memberIds", userId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // ─── Shopping list (all fire-and-forget) ──────────────────────────────────

    override suspend fun addIngredientToShoppingList(
        storeId: String,
        ingredient: Ingredient,
        currentList: List<Ingredient>
    ): Result<Unit> {
        return try {
            dataSource.updateDocumentAsync("stores", storeId, mapOf("shoppingList" to currentList + ingredient))
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun addStarredIngredientToList(
        storeId: String,
        starred: StarredIngredient,
        existingItem: Ingredient?,
        strategy: ConflictStrategy,
        currentList: List<Ingredient>
    ): Result<Unit> {
        return try {
            val updatedList: List<Ingredient> = when {
                existingItem == null -> currentList + Ingredient(
                    id = UUID.randomUUID().toString(),
                    name = starred.name,
                    quantity = starred.defaultQuantity,
                    bought = false,
                    addedBy = starred.id,
                    addedAt = System.currentTimeMillis()
                )
                strategy == ConflictStrategy.IGNORE -> return Result.Success(Unit)
                strategy == ConflictStrategy.INCREASE -> currentList.map { item ->
                    if (item.id == existingItem.id) {
                        item.copy(quantity = combineQuantities(item.quantity, starred.defaultQuantity))
                    } else item
                }
                strategy == ConflictStrategy.REPLACE -> currentList.map { item ->
                    if (item.id == existingItem.id) {
                        item.copy(
                            quantity = starred.defaultQuantity,
                            addedBy = starred.id,
                            addedAt = System.currentTimeMillis()
                        )
                    } else item
                }
                else -> return Result.Error(Exception("Unexpected strategy: $strategy"))
            }
            dataSource.updateDocumentAsync("stores", storeId, mapOf("shoppingList" to updatedList))
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun addRecipeIngredientsToList(
        storeId: String,
        recipe: Recipe,
        currentList: List<Ingredient>,
        conflictResolutions: Map<String, ConflictStrategy>
    ): Result<Unit> {
        return try {
            val existingMap = currentList.associateBy { it.name.lowercase() }
            val toAdd = mutableListOf<Ingredient>()
            val toUpdate = mutableListOf<Ingredient>()

            recipe.ingredients.forEach { ri ->
                val existing = existingMap[ri.name.lowercase()]
                if (existing == null) {
                    toAdd.add(
                        Ingredient(
                            id = UUID.randomUUID().toString(),
                            name = ri.name,
                            quantity = ri.quantity ?: "",
                            bought = false,
                            addedBy = recipe.id,
                            addedAt = System.currentTimeMillis()
                        )
                    )
                } else {
                    when (conflictResolutions[ri.name] ?: ConflictStrategy.IGNORE) {
                        ConflictStrategy.IGNORE -> Unit
                        ConflictStrategy.INCREASE -> toUpdate.add(
                            existing.copy(quantity = combineQuantities(existing.quantity, ri.quantity ?: ""))
                        )
                        ConflictStrategy.REPLACE -> toUpdate.add(
                            existing.copy(
                                quantity = ri.quantity ?: "",
                                addedBy = recipe.id,
                                addedAt = System.currentTimeMillis()
                            )
                        )
                        ConflictStrategy.ASK -> Unit
                    }
                }
            }

            if (toAdd.isEmpty() && toUpdate.isEmpty()) return Result.Success(Unit)

            val updatedList = currentList.map { existing ->
                toUpdate.find { it.id == existing.id } ?: existing
            } + toAdd

            dataSource.updateDocumentAsync("stores", storeId, mapOf("shoppingList" to updatedList))
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun updateIngredient(
        storeId: String,
        ingredient: Ingredient,
        currentList: List<Ingredient>
    ): Result<Unit> {
        return try {
            val updatedList = currentList.map { if (it.id == ingredient.id) ingredient else it }
            dataSource.updateDocumentAsync("stores", storeId, mapOf("shoppingList" to updatedList))
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun deleteIngredient(
        storeId: String,
        ingredientId: String,
        currentList: List<Ingredient>
    ): Result<Unit> {
        return try {
            dataSource.updateDocumentAsync(
                "stores", storeId,
                mapOf("shoppingList" to currentList.filter { it.id != ingredientId })
            )
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun markIngredientAsBought(
        storeId: String,
        ingredientId: String,
        bought: Boolean,
        currentList: List<Ingredient>
    ): Result<Unit> {
        return try {
            val updatedList = currentList.map { if (it.id == ingredientId) it.copy(bought = bought) else it }
            dataSource.updateDocumentAsync("stores", storeId, mapOf("shoppingList" to updatedList))
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // ─── Starred ingredients (fire-and-forget) ────────────────────────────────

    override suspend fun addStarredIngredient(storeId: String, ingredient: StarredIngredient): Result<Unit> {
        return try {
            dataSource.addArrayValueAsync("stores", storeId, "starredIngredients", ingredient)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun updateStarredIngredient(
        storeId: String,
        ingredient: StarredIngredient,
        currentStarred: List<StarredIngredient>
    ): Result<Unit> {
        return try {
            val updatedList = currentStarred.map { if (it.id == ingredient.id) ingredient else it }
            dataSource.updateDocumentAsync("stores", storeId, mapOf("starredIngredients" to updatedList))
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // arrayRemove silently fails if any field serializes differently (null vs absent),
    // so always use read-modify-write — but currentStarred is passed in so no getDocument.
    override suspend fun deleteStarredIngredient(
        storeId: String,
        ingredientId: String,
        currentStarred: List<StarredIngredient>
    ): Result<Unit> {
        return try {
            val updatedList = currentStarred.filter { it.id != ingredientId }
            dataSource.updateDocumentAsync("stores", storeId, mapOf("starredIngredients" to updatedList))
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // ─── Recipes (fire-and-forget) ────────────────────────────────────────────

    override suspend fun addRecipe(storeId: String, recipe: Recipe): Result<Unit> {
        return try {
            dataSource.addArrayValueAsync("stores", storeId, "recipes", recipe)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun updateRecipe(
        storeId: String,
        recipe: Recipe,
        currentRecipes: List<Recipe>
    ): Result<Unit> {
        return try {
            val updatedList = currentRecipes.map { if (it.id == recipe.id) recipe else it }
            dataSource.updateDocumentAsync("stores", storeId, mapOf("recipes" to updatedList))
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun deleteRecipe(
        storeId: String,
        recipeId: String,
        currentRecipes: List<Recipe>
    ): Result<Unit> {
        return try {
            val updatedList = currentRecipes.filter { it.id != recipeId }
            dataSource.updateDocumentAsync("stores", storeId, mapOf("recipes" to updatedList))
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // ─── Weekly reset (getDocument allowed — background path, not user-interactive) ──

    override suspend fun processWeeklyReset(storeId: String, currentWeek: Int): Result<Unit> {
        return try {
            val store = dataSource.getDocument("stores", storeId, Store::class.java)
                ?: return Result.Error(Exception("Store not found"))

            val newIngredients = store.starredIngredients
                .filter { starred ->
                    starred.periodicity != null &&
                            (starred.lastAddedWeek == null || currentWeek - starred.lastAddedWeek >= starred.periodicity)
                }
                .map { starred ->
                    Ingredient(
                        id = UUID.randomUUID().toString(),
                        name = starred.name,
                        quantity = starred.defaultQuantity,
                        bought = false,
                        addedBy = starred.id,
                        addedAt = System.currentTimeMillis()
                    )
                }

            val updatedStarred = store.starredIngredients.map { starred ->
                if (starred.periodicity != null && newIngredients.any { it.addedBy == starred.id }) {
                    starred.copy(lastAddedWeek = currentWeek)
                } else starred
            }

            dataSource.updateDocumentAsync(
                "stores", storeId, mapOf(
                    "shoppingList" to (store.shoppingList + newIngredients),
                    "starredIngredients" to updatedStarred
                )
            )
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun combineQuantities(qty1: String, qty2: String): String {
        if (qty1.isBlank()) return qty2
        if (qty2.isBlank()) return qty1
        val num1 = qty1.trim().toDoubleOrNull()
        val num2 = qty2.trim().toDoubleOrNull()
        return if (num1 != null && num2 != null) {
            val sum = num1 + num2
            if (sum == sum.toLong().toDouble()) sum.toLong().toString() else sum.toString()
        } else "${qty1.trim()} + ${qty2.trim()}"
    }
}