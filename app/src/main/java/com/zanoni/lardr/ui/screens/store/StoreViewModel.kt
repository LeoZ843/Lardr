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
import kotlinx.coroutines.Job
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
    val conflictQueue: List<RecipeConflict> = emptyList(),
    val totalRecipeConflicts: Int = 0,
    val currentRecipeConflict: RecipeConflict? = null,
    val showRecipeConflictDialog: Boolean = false,
    val isAddingRecipe: Boolean = false,
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

    private var loadJob: Job? = null

    private val _uiState = MutableStateFlow(StoreUiState())
    val uiState: StateFlow<StoreUiState> = _uiState.asStateFlow()

    init {
        loadStore()
        loadFriends()
        loadSentInvites()
    }

    private fun loadStore() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, isDataLoaded = false)

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
            val userId = authRepository.getCurrentUserId() ?: return@launch
            userRepository.observeFriends(userId).collect { friends ->
                _uiState.value = _uiState.value.copy(friends = friends)
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

    fun addIngredient(name: String, quantity: String, periodicity: Int? = null, conflictStrategy: ConflictStrategy = ConflictStrategy.ASK) {
        val trimmedName = name.trim()
        viewModelScope.launch {
            val allListItems = _uiState.value.shoppingListItems + _uiState.value.boughtItems

            if (periodicity != null) {
                // Adding as starred ingredient — also adds to shopping list immediately.
                val starred = StarredIngredient(
                    id = UUID.randomUUID().toString(),
                    name = trimmedName,
                    periodicity = periodicity,
                    defaultQuantity = quantity,
                    conflictStrategy = conflictStrategy.name,
                    lastAddedWeek = null
                )
                storeRepository.addStarredIngredient(storeId, starred)

                // Only add to shopping list if not already present (bought or active).
                val alreadyInList = allListItems.any { it.name.equals(trimmedName, ignoreCase = true) }
                if (!alreadyInList) {
                    val ingredient = Ingredient(
                        id = UUID.randomUUID().toString(),
                        name = trimmedName,
                        quantity = quantity,
                        bought = false,
                        addedBy = starred.id,
                        addedAt = System.currentTimeMillis()
                    )
                    storeRepository.addIngredientToShoppingList(storeId, ingredient)
                }
            } else {
                // Plain shopping list addition — guard against duplicates not caught by the dialog
                // (e.g. name already exists in bought items, or race condition).
                val alreadyInList = allListItems.any { it.name.equals(trimmedName, ignoreCase = true) }
                if (alreadyInList) return@launch

                val ingredient = Ingredient(
                    id = UUID.randomUUID().toString(),
                    name = trimmedName,
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
            val recipeId = UUID.randomUUID().toString()
            val recipeIngredients = ingredients.map { (ingredientName, qty) ->
                RecipeIngredient(name = ingredientName, quantity = qty)
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
        if (_uiState.value.isAddingRecipe) return

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isAddingRecipe = true)

                val recipe = _uiState.value.recipes.find { it.id == recipeId } ?: return@launch

                val allExistingIngredients = _uiState.value.shoppingListItems + _uiState.value.boughtItems
                val existingMap = allExistingIngredients.associateBy { it.name.lowercase() }

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
                                id = UUID.randomUUID().toString(),
                                name = recipeIngredient.name,
                                quantity = recipeIngredient.quantity ?: "",
                                bought = false,
                                addedBy = recipe.id,
                                addedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }

                // Parse the stored strategy string; default to ASK so duplicates surface.
                val strategy: ConflictStrategy = recipe.conflictStrategy
                    ?.let { runCatching { ConflictStrategy.valueOf(it) }.getOrNull() }
                    ?: ConflictStrategy.ASK

                when (strategy) {
                    ConflictStrategy.ASK -> {
                        if (newIngredients.isNotEmpty()) {
                            val result = storeRepository.addIngredientsToShoppingList(storeId, newIngredients)
                            if (result is Result.Error) {
                                _uiState.value = _uiState.value.copy(
                                    snackbarMessage = "Error adding ingredients: ${result.exception.message}"
                                )
                                return@launch
                            }
                        }

                        if (duplicates.isNotEmpty()) {
                            _uiState.value = _uiState.value.copy(
                                conflictQueue = duplicates,
                                totalRecipeConflicts = duplicates.size,
                                currentRecipeConflict = duplicates.first(),
                                showRecipeConflictDialog = true
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(
                                snackbarMessage = "Added ${newIngredients.size} ingredients from \"${recipe.name}\""
                            )
                        }
                    }

                    ConflictStrategy.IGNORE -> {
                        if (newIngredients.isEmpty()) {
                            _uiState.value = _uiState.value.copy(
                                snackbarMessage = "All ingredients from \"${recipe.name}\" are already in your list"
                            )
                        } else {
                            val result = storeRepository.addIngredientsToShoppingList(storeId, newIngredients)
                            if (result is Result.Error) {
                                _uiState.value = _uiState.value.copy(
                                    snackbarMessage = "Error adding ingredients: ${result.exception.message}"
                                )
                            } else {
                                _uiState.value = _uiState.value.copy(
                                    snackbarMessage = if (duplicates.isNotEmpty()) {
                                        "Added ${newIngredients.size} new, ignored ${duplicates.size} duplicates"
                                    } else {
                                        "Added ${newIngredients.size} ingredients from \"${recipe.name}\""
                                    }
                                )
                            }
                        }
                    }

                    ConflictStrategy.INCREASE -> {
                        if (newIngredients.isNotEmpty()) {
                            val result = storeRepository.addIngredientsToShoppingList(storeId, newIngredients)
                            if (result is Result.Error) {
                                _uiState.value = _uiState.value.copy(
                                    snackbarMessage = "Error adding ingredients: ${result.exception.message}"
                                )
                                return@launch
                            }
                        }
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
                        if (newIngredients.isNotEmpty()) {
                            val result = storeRepository.addIngredientsToShoppingList(storeId, newIngredients)
                            if (result is Result.Error) {
                                _uiState.value = _uiState.value.copy(
                                    snackbarMessage = "Error adding ingredients: ${result.exception.message}"
                                )
                                return@launch
                            }
                        }
                        duplicates.forEach { conflict ->
                            storeRepository.updateIngredient(
                                storeId,
                                conflict.existingIngredient.copy(
                                    quantity = conflict.recipeIngredient.quantity ?: "",
                                    addedBy = recipe.id,
                                    addedAt = System.currentTimeMillis()
                                )
                            )
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
                _uiState.value = _uiState.value.copy(isAddingRecipe = false)
            }
        }
    }

    fun addStarredIngredientToList(starredId: String) {
        viewModelScope.launch {
            val starred = _uiState.value.starredIngredients.find { it.id == starredId } ?: return@launch

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
                        try {
                            applyConflictStrategy(starred, existing, strategy)
                            val message = when (strategy) {
                                ConflictStrategy.REPLACE -> "\"${starred.name}\" quantity replaced"
                                ConflictStrategy.IGNORE -> "\"${starred.name}\" already in list, skipped"
                                ConflictStrategy.INCREASE -> "\"${starred.name}\" quantity increased"
                                ConflictStrategy.ASK -> null
                            }
                            if (message != null) {
                                _uiState.value = _uiState.value.copy(snackbarMessage = message)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("StoreViewModel", "addStarredIngredientToList error: ${e.message}", e)
                            _uiState.value = _uiState.value.copy(snackbarMessage = "Failed to update \"${starred.name}\"")
                        }
                    }
                }
            }
        }
    }

    /**
     * Close the dialog immediately (optimistic), then apply the strategy and optionally
     * persist the preference. This prevents the dialog from staying open if a network
     * call inside the coroutine throws.
     */
    fun resolveConflict(strategy: ConflictStrategy, remember: Boolean) {
        val starred = _uiState.value.conflictStarred ?: return
        val existing = _uiState.value.conflictExisting ?: return

        // Dismiss the dialog right away so the UI is never stuck waiting on a network call.
        _uiState.value = _uiState.value.copy(
            showConflictDialog = false,
            conflictStarred = null,
            conflictExisting = null
        )

        viewModelScope.launch {
            try {
                if (remember) {
                    storeRepository.updateStarredIngredient(
                        storeId,
                        starred.copy(conflictStrategy = strategy.name)
                    )
                }
                applyConflictStrategy(starred, existing, strategy)
                if (remember) {
                    _uiState.value = _uiState.value.copy(
                        snackbarMessage = "Preference saved for \"${starred.name}\""
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("StoreViewModel", "resolveConflict error: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    error = "Failed to apply change: ${e.message}"
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
        val current = _uiState.value.currentRecipeConflict ?: return
        val remaining = _uiState.value.conflictQueue.drop(1)

        // Dismiss the current dialog entry immediately before any async work.
        if (applyToAll || remaining.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                showRecipeConflictDialog = false,
                currentRecipeConflict = null,
                conflictQueue = emptyList(),
                totalRecipeConflicts = 0
            )
        } else {
            _uiState.value = _uiState.value.copy(
                conflictQueue = remaining,
                currentRecipeConflict = remaining.first()
            )
        }

        viewModelScope.launch {
            try {
                applyRecipeConflictStrategy(current, strategy)

                if (applyToAll && remaining.isNotEmpty()) {
                    remaining.forEach { conflict ->
                        applyRecipeConflictStrategy(conflict, strategy)
                    }
                    _uiState.value = _uiState.value.copy(
                        snackbarMessage = "Applied to all ${remaining.size + 1} duplicates"
                    )
                } else if (remaining.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        snackbarMessage = "All duplicates resolved"
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("StoreViewModel", "resolveRecipeConflict error: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    error = "Failed to apply change: ${e.message}"
                )
            }
        }
    }

    fun dismissRecipeConflictDialog() {
        val remaining = _uiState.value.conflictQueue.size
        _uiState.value = _uiState.value.copy(
            showRecipeConflictDialog = false,
            currentRecipeConflict = null,
            conflictQueue = emptyList(),
            totalRecipeConflicts = 0,
            snackbarMessage = if (remaining > 0) "$remaining duplicate(s) skipped" else null
        )
    }

    private suspend fun applyRecipeConflictStrategy(conflict: RecipeConflict, strategy: ConflictStrategy) {
        when (strategy) {
            ConflictStrategy.IGNORE -> Unit
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
                storeRepository.updateIngredient(
                    storeId,
                    conflict.existingIngredient.copy(
                        quantity = conflict.recipeIngredient.quantity ?: "",
                        addedBy = conflict.recipeId,
                        addedAt = System.currentTimeMillis()
                    )
                )
            }
            ConflictStrategy.ASK -> Unit
        }
    }

    private suspend fun applyConflictStrategy(
        starred: StarredIngredient,
        existing: Ingredient,
        strategy: ConflictStrategy
    ) {
        when (strategy) {
            ConflictStrategy.REPLACE -> {
                storeRepository.updateIngredient(
                    storeId,
                    existing.copy(
                        quantity = starred.defaultQuantity,
                        addedBy = starred.id,
                        addedAt = System.currentTimeMillis()
                    )
                )
            }
            ConflictStrategy.IGNORE -> Unit
            ConflictStrategy.INCREASE -> {
                val newQuantity = combineQuantities(existing.quantity, starred.defaultQuantity)
                storeRepository.updateIngredient(storeId, existing.copy(quantity = newQuantity))
            }
            ConflictStrategy.ASK -> Unit
        }
    }

    private fun combineQuantities(qty1: String, qty2: String): String {
        if (qty1.isBlank()) return qty2
        if (qty2.isBlank()) return qty1

        val num1 = qty1.trim().toDoubleOrNull()
        val num2 = qty2.trim().toDoubleOrNull()

        return if (num1 != null && num2 != null) {
            val sum = num1 + num2
            if (sum == sum.toLong().toDouble()) sum.toLong().toString() else sum.toString()
        } else {
            "${qty1.trim()} + ${qty2.trim()}"
        }
    }

    fun markIngredientAsBought(ingredientId: String) {
        val ingredient = _uiState.value.shoppingListItems.find { it.id == ingredientId }
        if (ingredient != null) {
            _uiState.value = _uiState.value.copy(
                shoppingListItems = _uiState.value.shoppingListItems.filter { it.id != ingredientId },
                boughtItems = _uiState.value.boughtItems + ingredient.copy(bought = true)
            )
        }
        viewModelScope.launch {
            try {
                storeRepository.markIngredientAsBought(storeId, ingredientId, true)
            } catch (e: Exception) {
                android.util.Log.e("StoreViewModel", "markIngredientAsBought failed: ${e.message}", e)
            }
        }
    }

    fun markIngredientAsNotBought(ingredientId: String) {
        val ingredient = _uiState.value.boughtItems.find { it.id == ingredientId }
        if (ingredient != null) {
            _uiState.value = _uiState.value.copy(
                boughtItems = _uiState.value.boughtItems.filter { it.id != ingredientId },
                shoppingListItems = _uiState.value.shoppingListItems + ingredient.copy(bought = false)
            )
        }
        viewModelScope.launch {
            try {
                storeRepository.markIngredientAsBought(storeId, ingredientId, false)
            } catch (e: Exception) {
                android.util.Log.e("StoreViewModel", "markIngredientAsNotBought failed: ${e.message}", e)
            }
        }
    }

    fun deleteIngredient(ingredientId: String) {
        _uiState.value = _uiState.value.copy(
            shoppingListItems = _uiState.value.shoppingListItems.filter { it.id != ingredientId },
            boughtItems = _uiState.value.boughtItems.filter { it.id != ingredientId }
        )
        viewModelScope.launch {
            try {
                storeRepository.deleteIngredient(storeId, ingredientId)
            } catch (e: Exception) {
                android.util.Log.e("StoreViewModel", "deleteIngredient failed: ${e.message}", e)
            }
        }
    }

    fun deleteStarredIngredient(starredId: String) {
        viewModelScope.launch {
            storeRepository.deleteStarredIngredient(storeId, starredId)
        }
    }

    fun updateStarredIngredient(starredId: String, name: String, quantity: String, periodicity: Int?, conflictStrategy: ConflictStrategy) {
        viewModelScope.launch {
            val starred = _uiState.value.starredIngredients.find { it.id == starredId } ?: return@launch
            val result = storeRepository.updateStarredIngredient(
                storeId,
                starred.copy(name = name, defaultQuantity = quantity, periodicity = periodicity, conflictStrategy = conflictStrategy.name)
            )
            if (result is Result.Error) {
                android.util.Log.e("StoreViewModel", "updateStarredIngredient failed: ${result.exception.message}", result.exception)
                _uiState.value = _uiState.value.copy(snackbarMessage = "Failed to save changes: ${result.exception.message}")
            }
        }
    }

    fun updateIngredient(ingredientId: String, name: String, quantity: String, isStarred: Boolean, periodicity: Int?, conflictStrategy: ConflictStrategy = ConflictStrategy.ASK) {
        viewModelScope.launch {
            val current = _uiState.value.shoppingListItems.find { it.id == ingredientId }
                ?: _uiState.value.boughtItems.find { it.id == ingredientId }
                ?: return@launch

            val updated = current.copy(name = name, quantity = quantity)
            storeRepository.updateIngredient(storeId, updated)

            if (isStarred) {
                if (current.addedBy == "manual") {
                    val starred = StarredIngredient(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        periodicity = periodicity,
                        defaultQuantity = quantity,
                        conflictStrategy = conflictStrategy.name,
                        lastAddedWeek = null
                    )
                    storeRepository.addStarredIngredient(storeId, starred)
                    storeRepository.updateIngredient(storeId, updated.copy(addedBy = starred.id))
                } else {
                    val starred = _uiState.value.starredIngredients.find { it.id == current.addedBy }
                    if (starred != null) {
                        storeRepository.updateStarredIngredient(
                            storeId,
                            starred.copy(name = name, periodicity = periodicity, defaultQuantity = quantity, conflictStrategy = conflictStrategy.name)
                        )
                    }
                }
            } else {
                if (current.addedBy != "manual") {
                    storeRepository.deleteStarredIngredient(storeId, current.addedBy)
                    storeRepository.updateIngredient(storeId, updated.copy(addedBy = "manual"))
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