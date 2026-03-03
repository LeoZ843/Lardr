package com.zanoni.lardr.data.repository

import com.zanoni.lardr.data.model.ConflictStrategy
import com.zanoni.lardr.data.model.Ingredient
import com.zanoni.lardr.data.model.Recipe
import com.zanoni.lardr.data.model.RecipeIngredient
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

    suspend fun addIngredientToShoppingList(storeId: String, ingredient: Ingredient): Result<Unit>
    suspend fun addIngredientsToShoppingList(storeId: String, ingredients: List<Ingredient>): Result<Unit>
    suspend fun updateIngredient(storeId: String, ingredient: Ingredient): Result<Unit>
    suspend fun deleteIngredient(storeId: String, ingredientId: String): Result<Unit>
    suspend fun markIngredientAsBought(storeId: String, ingredientId: String, bought: Boolean): Result<Unit>

    suspend fun addStarredIngredient(storeId: String, starredIngredient: StarredIngredient): Result<Unit>
    suspend fun updateStarredIngredient(storeId: String, starredIngredient: StarredIngredient): Result<Unit>
    suspend fun deleteStarredIngredient(storeId: String, starredIngredientId: String): Result<Unit>

    suspend fun addRecipe(storeId: String, recipe: Recipe): Result<Unit>
    suspend fun updateRecipe(storeId: String, recipe: Recipe): Result<Unit>
    suspend fun deleteRecipe(storeId: String, recipeId: String): Result<Unit>
    suspend fun addRecipeToShoppingList(
        storeId: String,
        recipeId: String,
        selectedIngredients: List<String>,
        conflictResolution: Map<String, ConflictStrategy>
    ): Result<Unit>

    suspend fun processWeeklyReset(storeId: String, currentWeek: Int): Result<Unit>
    suspend fun getConflictsForWeek(storeId: String, currentWeek: Int): Result<List<Pair<String, String>>>

    fun observeStore(storeId: String): Flow<Store?>
}