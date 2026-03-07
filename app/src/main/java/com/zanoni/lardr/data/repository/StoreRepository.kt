package com.zanoni.lardr.data.repository

import com.zanoni.lardr.data.model.ConflictStrategy
import com.zanoni.lardr.data.model.Ingredient
import com.zanoni.lardr.data.model.Recipe
import com.zanoni.lardr.data.model.StarredIngredient
import com.zanoni.lardr.data.model.Store
import kotlinx.coroutines.flow.Flow

interface StoreRepository {
    fun getStoresForUser(userId: String): Flow<List<Store>>
    suspend fun createStore(name: String, ownerId: String): Result<Store>
    suspend fun deleteStore(storeId: String): Result<Unit>
    suspend fun updateStoreName(storeId: String, name: String): Result<Unit>

    suspend fun addMemberToStore(storeId: String, userId: String): Result<Unit>
    suspend fun removeMemberFromStore(storeId: String, userId: String): Result<Unit>

    // All write methods accept current state from ViewModel to avoid getDocument round-trips.
    // Writes are fire-and-forget: they return Success immediately after submitting to the
    // local Firestore cache. observeStore delivers the pending-write snapshot instantly.

    suspend fun addIngredientToShoppingList(
        storeId: String,
        ingredient: Ingredient,
        currentList: List<Ingredient>
    ): Result<Unit>

    suspend fun addStarredIngredientToList(
        storeId: String,
        starred: StarredIngredient,
        existingItem: Ingredient?,
        strategy: ConflictStrategy,
        currentList: List<Ingredient>
    ): Result<Unit>

    suspend fun addRecipeIngredientsToList(
        storeId: String,
        recipe: Recipe,
        currentList: List<Ingredient>,
        conflictResolutions: Map<String, ConflictStrategy>
    ): Result<Unit>

    suspend fun updateIngredient(
        storeId: String,
        ingredient: Ingredient,
        currentList: List<Ingredient>
    ): Result<Unit>

    suspend fun deleteIngredient(
        storeId: String,
        ingredientId: String,
        currentList: List<Ingredient>
    ): Result<Unit>

    suspend fun markIngredientAsBought(
        storeId: String,
        ingredientId: String,
        bought: Boolean,
        currentList: List<Ingredient>
    ): Result<Unit>

    suspend fun addStarredIngredient(storeId: String, ingredient: StarredIngredient): Result<Unit>

    suspend fun updateStarredIngredient(
        storeId: String,
        ingredient: StarredIngredient,
        currentStarred: List<StarredIngredient>
    ): Result<Unit>

    suspend fun deleteStarredIngredient(
        storeId: String,
        ingredientId: String,
        currentStarred: List<StarredIngredient>
    ): Result<Unit>

    suspend fun addRecipe(storeId: String, recipe: Recipe): Result<Unit>

    suspend fun updateRecipe(
        storeId: String,
        recipe: Recipe,
        currentRecipes: List<Recipe>
    ): Result<Unit>

    suspend fun deleteRecipe(
        storeId: String,
        recipeId: String,
        currentRecipes: List<Recipe>
    ): Result<Unit>

    suspend fun processWeeklyReset(storeId: String, currentWeek: Int): Result<Unit>

    fun observeStore(storeId: String): Flow<Store?>
}