package com.zanoni.lardr.ui.screens.store

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zanoni.lardr.data.model.ConflictStrategy
import com.zanoni.lardr.data.model.Ingredient
import com.zanoni.lardr.data.model.Recipe
import com.zanoni.lardr.data.model.RecipeIngredient
import com.zanoni.lardr.data.model.StarredIngredient
import com.zanoni.lardr.data.model.Store
import com.zanoni.lardr.data.repository.StoreRepository
import com.zanoni.lardr.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.zanoni.lardr.data.repository.UserRepository
import com.zanoni.lardr.data.model.User
import com.zanoni.lardr.data.repository.AuthRepository
import java.util.UUID

data class StoreUiState(
    val store: Store? = null,
    val isLoading: Boolean = true,
    val isDataLoaded: Boolean = false,
    val error: String? = null,
    val snackbarMessage: String? = null,
    val currentTab: StoreTab = StoreTab.SHOPPING_LIST,
    val shoppingListItems: List<Ingredient> = emptyList(),
    val boughtItems: List<Ingredient> = emptyList(),
    val starredIngredients: List<StarredIngredient> = emptyList(),
    val recipes: List<Recipe> = emptyList(),
    val showConflictDialog: Boolean = false,
    val conflictStarred: StarredIngredient? = null,
    val conflictExisting: Ingredient? = null,
    val friends: List<User> = emptyList(),
    // Recipe conflict queue
    val conflictQueue: List<RecipeConflict> = emptyList(),
    val currentRecipeConflict: RecipeConflict? = null,
    val showRecipeConflictDialog: Boolean = false,
    val isAddingRecipe: Boolean = false,  // Prevent double-click
    val pendingInviteUserIds: List<String> = emptyList()
)

data class RecipeConflict(
    val recipeIngredient: RecipeIngredient,
    val existingIngredient: Ingredient,
    val recipeId: String,
    val recipeName: String
)

enum class StoreTab {
    SHOPPING_LIST,
    STARRED,
    RECIPES
}

@HiltViewModel
class StoreViewModel @Inject constructor(
    private val storeRepository: StoreRepository,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val storeId: String = checkNotNull(savedStateHandle["storeId"])

    private val _uiState = MutableStateFlow(StoreUiState())
    val uiState: StateFlow<StoreUiState> = _uiState.asStateFlow()

    init {
        loadStore()
        loadFriends()
        loadSentInvites()
    }

    private fun loadStore() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, isDataLoaded = false)

            // Timeout: if no data in 10 seconds, show error
            val timeoutJob = launch {
                kotlinx.coroutines.delay(10_000)
                if (!_uiState.value.isDataLoaded) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isDataLoaded = true,
                        error = "Could not load store. Check your connection."
                    )
                }
            }

            try {
                storeRepository.observeStore(storeId)
                    .collect { store ->
                        timeoutJob.cancel()

                        if (store == null) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                isDataLoaded = true,
                                error = "Store not found."
                            )
                            return@collect
                        }

                        _uiState.value = _uiState.value.copy(
                            store = store,
                            shoppingListItems = store.shoppingList.filter { !it.bought },
                            boughtItems = store.shoppingList.filter { it.bought },
                            starredIngredients = store.starredIngredients,
                            recipes = store.recipes,
                            isLoading = false,
                            isDataLoaded = true,
                            error = null
                        )
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                timeoutJob.cancel()
                // Normal navigation away — do nothing
            } catch (e: Exception) {
                timeoutJob.cancel()
                android.util.Log.e("StoreViewModel", "loadStore error: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isDataLoaded = true,
                    error = "Error loading store: ${e.message}"
                )
            }
        }
    }

    private fun loadSentInvites() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch
            userRepository.getSentStoreInvites(userId).collect { invites ->
                val pendingForThisStore = invites
                    .filter { it.storeId == storeId }
                    .map { it.invitedUserId }
                _uiState.value = _uiState.value.copy(pendingInviteUserIds = pendingForThisStore)
            }
        }
    }

    private fun loadFriends() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId()
            if (userId != null) {
                userRepository.observeFriends(userId).collect { friends ->
                    _uiState.value = _uiState.value.copy(friends = friends)
                }
            }
        }
    }

    fun shareStore(friendIds: List<String>) {
        viewModelScope.launch {
            val store = _uiState.value.store ?: return@launch

            friendIds.forEach { friendId ->
                userRepository.sendStoreInvite(
                    storeId = store.id,
                    storeName = store.name,
                    friendId = friendId
                )
            }
        }
    }

    fun selectTab(tab: StoreTab) {
        _uiState.value = _uiState.value.copy(currentTab = tab)
    }

    fun addIngredient(name: String, quantity: String, periodicity: Int? = null) {
        viewModelScope.launch {
            // If periodicity is set, this is a starred ingredient
            if (periodicity != null) {
                // Add to starred ingredients
                val starred = StarredIngredient(
                    id = java.util.UUID.randomUUID().toString(),
                    name = name,
                    periodicity = periodicity,
                    defaultQuantity = quantity,
                    conflictStrategy = ConflictStrategy.ASK.name,
                    lastAddedWeek = null
                )
                storeRepository.addStarredIngredient(storeId, starred)

                // Also add to shopping list immediately
                val ingredient = Ingredient(
                    id = java.util.UUID.randomUUID().toString(),
                    name = name,
                    quantity = quantity,
                    bought = false,
                    addedBy = starred.id,
                    addedAt = System.currentTimeMillis()
                )
                storeRepository.addIngredientToShoppingList(storeId, ingredient)
            } else {
                // Just add to shopping list
                val ingredient = Ingredient(
                    id = java.util.UUID.randomUUID().toString(),
                    name = name,
                    quantity = quantity,
                    bought = false,
                    addedBy = "manual",
                    addedAt = System.currentTimeMillis()
                )
                storeRepository.addIngredientToShoppingList(storeId, ingredient)
            }
        }
    }

    fun addRecipe(name: String, ingredients: List<Pair<String, String>>, periodicity: Int?) {
        viewModelScope.launch {
            val recipeId = java.util.UUID.randomUUID().toString()
            val recipeIngredients = ingredients.map { (ingredientName, quantity) ->
                RecipeIngredient(
                    name = ingredientName,
                    quantity = quantity
                )
            }

            val recipe = Recipe(
                id = recipeId,
                name = name,
                ingredients = recipeIngredients,
                periodicity = periodicity,
                conflictStrategy = null,
                lastAddedWeek = null
            )

            storeRepository.addRecipe(storeId, recipe)
        }
    }

    fun addRecipeToShoppingList(recipeId: String) {
        // Prevent double-clicks
        if (_uiState.value.isAddingRecipe) {
            android.util.Log.d("StoreViewModel", "Already adding recipe, ignoring duplicate call")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isAddingRecipe = true)

                val recipe = _uiState.value.recipes.find { it.id == recipeId }
                if (recipe == null) {
                    _uiState.value = _uiState.value.copy(isAddingRecipe = false)
                    return@launch
                }

                android.util.Log.d("StoreViewModel", "Adding recipe to list: ${recipe.name}")

                // Get existing ingredients from BOTH shopping list AND bought items
                val allExistingIngredients = _uiState.value.shoppingListItems + _uiState.value.boughtItems
                val existingMap = allExistingIngredients.associateBy { it.name.lowercase() }

                // Separate into new and duplicate ingredients
                val newIngredients = mutableListOf<Ingredient>()
                val duplicates = mutableListOf<RecipeConflict>()

                recipe.ingredients.forEach { recipeIngredient ->
                    val existing = existingMap[recipeIngredient.name.lowercase()]
                    if (existing != null) {
                        duplicates.add(
                            RecipeConflict(
                                recipeIngredient = recipeIngredient,
                                existingIngredient = existing,
                                recipeId = recipe.id,
                                recipeName = recipe.name
                            )
                        )
                    } else {
                        newIngredients.add(
                            Ingredient(
                                id = java.util.UUID.randomUUID().toString(),
                                name = recipeIngredient.name,
                                quantity = recipeIngredient.quantity ?: "",
                                bought = false,
                                addedBy = recipe.id,
                                addedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }

                // Handle duplicates based on recipe's conflict strategy
                val strategy = recipe.conflictStrategy ?: ConflictStrategy.IGNORE

                when (strategy) {
                    ConflictStrategy.ASK -> {
                        // Add new ingredients immediately and await result
                        if (newIngredients.isNotEmpty()) {
                            val result = storeRepository.addIngredientsToShoppingList(storeId, newIngredients)
                            if (result is Result.Error) {
                                _uiState.value = _uiState.value.copy(
                                    snackbarMessage = "Error adding ingredients: ${result.exception.message}"
                                )
                                return@launch
                            }
                        }

                        // Queue conflicts and show dialog for first one
                        if (duplicates.isNotEmpty()) {
                            _uiState.value = _uiState.value.copy(
                                conflictQueue = duplicates,
                                currentRecipeConflict = duplicates.first(),
                                showRecipeConflictDialog = true
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(
                                snackbarMessage = "Added ${newIngredients.size} ingredients from '${recipe.name}'"
                            )
                        }
                    }
                    ConflictStrategy.IGNORE -> {
                        // Add only new ingredients
                        if (newIngredients.isEmpty()) {
                            _uiState.value = _uiState.value.copy(
                                snackbarMessage = "All ingredients from '${recipe.name}' are already in your list"
                            )
                        } else {
                            val result = storeRepository.addIngredientsToShoppingList(storeId, newIngredients)
                            if (result is Result.Error) {
                                _uiState.value = _uiState.value.copy(
                                    snackbarMessage = "Error adding ingredients: ${result.exception.message}"
                                )
                            } else {
                                if (duplicates.isNotEmpty()) {
                                    _uiState.value = _uiState.value.copy(
                                        snackbarMessage = "Added ${newIngredients.size} new, ignored ${duplicates.size} duplicates"
                                    )
                                } else {
                                    _uiState.value = _uiState.value.copy(
                                        snackbarMessage = "Added ${newIngredients.size} ingredients from '${recipe.name}'"
                                    )
                                }
                            }
                        }
                    }
                    ConflictStrategy.INCREASE -> {
                        // Add new ingredients
                        if (newIngredients.isNotEmpty()) {
                            val result = storeRepository.addIngredientsToShoppingList(storeId, newIngredients)
                            if (result is Result.Error) {
                                _uiState.value = _uiState.value.copy(
                                    snackbarMessage = "Error adding ingredients: ${result.exception.message}"
                                )
                                return@launch
                            }
                        }
                        // Update existing with combined quantities
                        duplicates.forEach { conflict ->
                            val combined = combineQuantities(
                                conflict.existingIngredient.quantity,
                                conflict.recipeIngredient.quantity ?: ""
                            )
                            storeRepository.updateIngredient(
                                storeId,
                                conflict.existingIngredient.copy(quantity = combined)
                            )
                        }
                        _uiState.value = _uiState.value.copy(
                            snackbarMessage = "Added ${newIngredients.size} new, increased ${duplicates.size} existing quantities"
                        )
                    }
                    ConflictStrategy.REPLACE -> {
                        // Add new ingredients
                        if (newIngredients.isNotEmpty()) {
                            val result = storeRepository.addIngredientsToShoppingList(storeId, newIngredients)
                            if (result is Result.Error) {
                                _uiState.value = _uiState.value.copy(
                                    snackbarMessage = "Error adding ingredients: ${result.exception.message}"
                                )
                                return@launch
                            }
                        }
                        // Replace existing
                        duplicates.forEach { conflict ->
                            val replacement = Ingredient(
                                id = conflict.existingIngredient.id,
                                name = conflict.recipeIngredient.name,
                                quantity = conflict.recipeIngredient.quantity ?: "",
                                bought = conflict.existingIngredient.bought,
                                addedBy = recipe.id,
                                addedAt = System.currentTimeMillis()
                            )
                            storeRepository.updateIngredient(storeId, replacement)
                        }
                        _uiState.value = _uiState.value.copy(
                            snackbarMessage = "Added ${newIngredients.size} new, replaced ${duplicates.size} existing"
                        )
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e("StoreViewModel", "Error adding recipe: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    snackbarMessage = "Error adding recipe: ${e.message}"
                )
            } finally {
                // Reset flag to allow next add
                _uiState.value = _uiState.value.copy(isAddingRecipe = false)
            }
        }
    }

    fun addStarredIngredientToList(starredId: String) {
        viewModelScope.launch {
            val starred = _uiState.value.starredIngredients.find { it.id == starredId }
                ?: return@launch

            // Check both active and bought items — bought items are still "in the list"
            val existing = (_uiState.value.shoppingListItems + _uiState.value.boughtItems).find {
                it.name.equals(starred.name, ignoreCase = true)
            }

            when {
                existing == null -> {
                    storeRepository.addIngredientToShoppingList(
                        storeId,
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
                starred.conflictStrategy == ConflictStrategy.ASK.name -> {
                    _uiState.value = _uiState.value.copy(
                        showConflictDialog = true,
                        conflictStarred = starred,
                        conflictExisting = existing
                    )
                }
                else -> {
                    val strategy = runCatching {
                        ConflictStrategy.valueOf(starred.conflictStrategy)
                    }.getOrDefault(ConflictStrategy.ASK)

                    if (strategy == ConflictStrategy.ASK) {
                        _uiState.value = _uiState.value.copy(
                            showConflictDialog = true,
                            conflictStarred = starred,
                            conflictExisting = existing
                        )
                    } else {
                        applyConflictStrategy(starred, existing, strategy)
                    }
                }
            }
        }
    }

    fun resolveConflict(strategy: ConflictStrategy, remember: Boolean) {
        viewModelScope.launch {
            val starred = _uiState.value.conflictStarred
            val existing = _uiState.value.conflictExisting

            if (starred != null && existing != null) {
                // Update strategy if remember is checked
                if (remember) {
                    storeRepository.updateStarredIngredient(
                        storeId,
                        starred.copy(conflictStrategy = strategy.name)
                    )
                }

                // Apply the strategy
                applyConflictStrategy(starred, existing, strategy)

                // Close dialog
                _uiState.value = _uiState.value.copy(
                    showConflictDialog = false,
                    conflictStarred = null,
                    conflictExisting = null
                )
            }
        }
    }

    fun dismissConflictDialog() {
        _uiState.value = _uiState.value.copy(
            showConflictDialog = false,
            conflictStarred = null,
            conflictExisting = null
        )
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    fun resolveRecipeConflict(strategy: ConflictStrategy, applyToAll: Boolean) {
        viewModelScope.launch {
            val current = _uiState.value.currentRecipeConflict ?: return@launch
            val remaining = _uiState.value.conflictQueue.drop(1)

            // Apply strategy to current conflict
            applyRecipeConflictStrategy(current, strategy)

            if (applyToAll && remaining.isNotEmpty()) {
                // Apply same strategy to all remaining conflicts
                remaining.forEach { conflict ->
                    applyRecipeConflictStrategy(conflict, strategy)
                }

                // Close dialog and show completion message
                _uiState.value = _uiState.value.copy(
                    showRecipeConflictDialog = false,
                    currentRecipeConflict = null,
                    conflictQueue = emptyList(),
                    snackbarMessage = "Applied ${strategy.name.lowercase()} to all ${_uiState.value.conflictQueue.size} duplicates"
                )
            } else if (remaining.isNotEmpty()) {
                // Show next conflict
                _uiState.value = _uiState.value.copy(
                    conflictQueue = remaining,
                    currentRecipeConflict = remaining.first()
                )
            } else {
                // All conflicts resolved
                _uiState.value = _uiState.value.copy(
                    showRecipeConflictDialog = false,
                    currentRecipeConflict = null,
                    conflictQueue = emptyList()
                )
            }
        }
    }

    fun dismissRecipeConflictDialog() {
        // User cancelled - ignore remaining conflicts
        _uiState.value = _uiState.value.copy(
            showRecipeConflictDialog = false,
            currentRecipeConflict = null,
            conflictQueue = emptyList(),
            snackbarMessage = "Cancelled - ${_uiState.value.conflictQueue.size} duplicates ignored"
        )
    }

    private suspend fun applyRecipeConflictStrategy(conflict: RecipeConflict, strategy: ConflictStrategy) {
        when (strategy) {
            ConflictStrategy.IGNORE -> {
                // Do nothing
            }
            ConflictStrategy.INCREASE -> {
                val combined = combineQuantities(
                    conflict.existingIngredient.quantity,
                    conflict.recipeIngredient.quantity ?: ""
                )
                storeRepository.updateIngredient(
                    storeId,
                    conflict.existingIngredient.copy(quantity = combined)
                )
            }
            ConflictStrategy.REPLACE -> {
                val replacement = Ingredient(
                    id = conflict.existingIngredient.id,
                    name = conflict.recipeIngredient.name,
                    quantity = conflict.recipeIngredient.quantity ?: "",
                    bought = conflict.existingIngredient.bought,
                    addedBy = conflict.recipeId,
                    addedAt = System.currentTimeMillis()
                )
                storeRepository.updateIngredient(storeId, replacement)
            }
            ConflictStrategy.ASK -> {
                // Should not reach here
            }
        }
    }

    private suspend fun applyConflictStrategy(
        starred: StarredIngredient,
        existing: Ingredient,
        strategy: ConflictStrategy
    ) {
        when (strategy) {
            ConflictStrategy.REPLACE -> {
                // Delete existing and add new from starred
                storeRepository.deleteIngredient(storeId, existing.id)
                val newIngredient = Ingredient(
                    id = java.util.UUID.randomUUID().toString(),
                    name = starred.name,
                    quantity = starred.defaultQuantity,
                    bought = false,
                    addedBy = starred.id,
                    addedAt = System.currentTimeMillis()
                )
                storeRepository.addIngredientToShoppingList(storeId, newIngredient)
            }
            ConflictStrategy.IGNORE -> {
                // Do nothing
            }
            ConflictStrategy.INCREASE -> {
                // Combine quantities
                val newQuantity = combineQuantities(existing.quantity, starred.defaultQuantity)
                val updated = existing.copy(quantity = newQuantity)
                storeRepository.updateIngredient(storeId, updated)
            }
            ConflictStrategy.ASK -> {
                // Should not reach here
            }
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

    fun markIngredientAsBought(ingredientId: String) {
        android.util.Log.d("StoreViewModel", "Marking ingredient as bought: $ingredientId")

        // Optimistic update - move to bought section immediately
        val ingredient = _uiState.value.shoppingListItems.find { it.id == ingredientId }

        if (ingredient != null) {
            val updated = ingredient.copy(bought = true)
            _uiState.value = _uiState.value.copy(
                shoppingListItems = _uiState.value.shoppingListItems.filter { it.id != ingredientId },
                boughtItems = _uiState.value.boughtItems + updated
            )
        }

        // Sync to server in background (with cancellation protection)
        viewModelScope.launch {
            try {
                storeRepository.markIngredientAsBought(storeId, ingredientId, true)
                android.util.Log.d("StoreViewModel", "Successfully marked as bought")
            } catch (e: Exception) {
                android.util.Log.e("StoreViewModel", "Background mark failed: ${e.message}", e)
                // Flow listener will restore correct state if needed
            }
        }
    }

    fun markIngredientAsNotBought(ingredientId: String) {
        android.util.Log.d("StoreViewModel", "Marking ingredient as NOT bought: $ingredientId")

        // Optimistic update - move back to shopping list immediately
        val ingredient = _uiState.value.boughtItems.find { it.id == ingredientId }

        if (ingredient != null) {
            val updated = ingredient.copy(bought = false)
            _uiState.value = _uiState.value.copy(
                boughtItems = _uiState.value.boughtItems.filter { it.id != ingredientId },
                shoppingListItems = _uiState.value.shoppingListItems + updated
            )
        }

        // Sync to server in background (with cancellation protection)
        viewModelScope.launch {
            try {
                storeRepository.markIngredientAsBought(storeId, ingredientId, false)
                android.util.Log.d("StoreViewModel", "Successfully marked as NOT bought")
            } catch (e: Exception) {
                android.util.Log.e("StoreViewModel", "Background unmark failed: ${e.message}", e)
                // Flow listener will restore correct state if needed
            }
        }
    }

    fun deleteIngredient(ingredientId: String) {
        // Optimistic update - remove from UI immediately
        val ingredient = _uiState.value.shoppingListItems.find { it.id == ingredientId }
            ?: _uiState.value.boughtItems.find { it.id == ingredientId }

        if (ingredient != null) {
            _uiState.value = _uiState.value.copy(
                shoppingListItems = _uiState.value.shoppingListItems.filter { it.id != ingredientId },
                boughtItems = _uiState.value.boughtItems.filter { it.id != ingredientId }
            )
        }

        // Sync to server in background (with cancellation protection)
        viewModelScope.launch {
            try {
                val result = storeRepository.deleteIngredient(storeId, ingredientId)
                if (result is Result.Error) {
                    android.util.Log.e("StoreViewModel", "Background delete failed: ${result.exception.message}")
                    // Flow listener will restore correct state if needed
                }
            } catch (e: Exception) {
                android.util.Log.e("StoreViewModel", "Delete operation cancelled or failed: ${e.message}")
                // This is OK - optimistic update already happened
                // Flow listener will correct state if there's a mismatch
            }
        }
    }

    fun deleteStarredIngredient(starredId: String) {
        viewModelScope.launch {
            storeRepository.deleteStarredIngredient(storeId, starredId)
        }
    }

    fun updateStarredIngredient(starredId: String, name: String, quantity: String, periodicity: Int?) {
        viewModelScope.launch {
            val starred = _uiState.value.starredIngredients.find { it.id == starredId }
            if (starred != null) {
                val updated = starred.copy(
                    name = name,
                    defaultQuantity = quantity,
                    periodicity = periodicity
                )
                storeRepository.updateStarredIngredient(storeId, updated)
            }
        }
    }

    fun updateIngredient(ingredientId: String, name: String, quantity: String, periodicity: Int?) {
        viewModelScope.launch {
            val currentIngredient = _uiState.value.shoppingListItems.find { it.id == ingredientId }
                ?: _uiState.value.boughtItems.find { it.id == ingredientId }

            if (currentIngredient != null) {
                val updated = currentIngredient.copy(
                    name = name,
                    quantity = quantity
                )
                storeRepository.updateIngredient(storeId, updated)

                if (periodicity != null) {
                    if (currentIngredient.addedBy == "manual") {
                        val starred = StarredIngredient(
                            id = java.util.UUID.randomUUID().toString(),
                            name = name,
                            periodicity = periodicity,
                            defaultQuantity = quantity,
                            conflictStrategy = ConflictStrategy.ASK.name,
                            lastAddedWeek = null
                        )
                        storeRepository.addStarredIngredient(storeId, starred)
                        val updatedWithStarred = updated.copy(addedBy = starred.id)
                        storeRepository.updateIngredient(storeId, updatedWithStarred)
                    } else {
                        val starred = _uiState.value.starredIngredients.find { it.id == currentIngredient.addedBy }
                        if (starred != null) {
                            val updatedStarred = starred.copy(
                                name = name,
                                periodicity = periodicity,
                                defaultQuantity = quantity
                            )
                            storeRepository.updateStarredIngredient(storeId, updatedStarred)
                        }
                    }
                } else {
                    if (currentIngredient.addedBy != "manual") {
                        storeRepository.deleteStarredIngredient(storeId, currentIngredient.addedBy)
                        val updatedManual = updated.copy(addedBy = "manual")
                        storeRepository.updateIngredient(storeId, updatedManual)
                    }
                }
            }
        }
    }

    fun deleteRecipe(recipeId: String) {
        viewModelScope.launch {
            storeRepository.deleteRecipe(storeId, recipeId)
        }
    }

    fun updateStoreName(newName: String) {
        viewModelScope.launch {
            storeRepository.updateStoreName(storeId, newName)
        }
    }

    fun deleteStore() {
        viewModelScope.launch {
            storeRepository.deleteStore(storeId)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun retryLoad() {
        loadStore()
    }
}