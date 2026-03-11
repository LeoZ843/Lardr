package com.zanoni.lardr.ui.screens.store

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zanoni.lardr.data.local.PendingWritesManager
import com.zanoni.lardr.data.local.StoreCache
import com.zanoni.lardr.data.model.ConflictStrategy
import com.zanoni.lardr.data.model.Ingredient
import com.zanoni.lardr.data.model.Recipe
import com.zanoni.lardr.data.model.RecipeIngredient
import com.zanoni.lardr.data.model.StarredIngredient
import com.zanoni.lardr.data.model.Store
import com.zanoni.lardr.data.model.User
import com.zanoni.lardr.data.repository.AuthRepository
import com.zanoni.lardr.data.repository.Result
import com.zanoni.lardr.data.repository.StoreRepository
import com.zanoni.lardr.data.repository.UserRepository
import com.zanoni.lardr.di.ApplicationScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

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
    val conflictQueue: List<RecipeConflict> = emptyList(),
    val totalRecipeConflicts: Int = 0,
    val currentRecipeConflict: RecipeConflict? = null,
    val showRecipeConflictDialog: Boolean = false,
    val pendingRecipeId: String? = null,
    val friends: List<User> = emptyList(),
    val pendingInviteUserIds: List<String> = emptyList(),
    val isAddingRecipe: Boolean = false,
    val pendingStarredAdds: Set<String> = emptySet()
)

data class RecipeConflict(
    val recipeIngredient: RecipeIngredient,
    val existingIngredient: Ingredient,
    val recipeId: String,
    val recipeName: String
)

enum class StoreTab { SHOPPING_LIST, STARRED, RECIPES }

@HiltViewModel
class StoreViewModel @Inject constructor(
    private val storeRepository: StoreRepository,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val storeCache: StoreCache,
    private val pendingWritesManager: PendingWritesManager,
    @ApplicationScope private val applicationScope: CoroutineScope,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val storeId: String = checkNotNull(savedStateHandle["storeId"])
    private var loadJob: Job? = null

    private val _uiState = MutableStateFlow(buildInitialState())
    val uiState: StateFlow<StoreUiState> = _uiState.asStateFlow()

    private fun buildInitialState(): StoreUiState {
        val cached = storeCache.get(storeId)
        return if (cached != null) {
            StoreUiState(
                store = cached,
                shoppingListItems = cached.shoppingList.filter { !it.bought },
                boughtItems = cached.shoppingList.filter { it.bought },
                starredIngredients = cached.starredIngredients,
                recipes = cached.recipes,
                isLoading = false,
                isDataLoaded = true
            )
        } else {
            StoreUiState(isLoading = true, isDataLoaded = false)
        }
    }

    init {
        loadStore()
        loadFriends()
        loadSentInvites()
    }

    // ─── Store loading (viewModelScope — cancelled on nav away) ──────────────

    private fun loadStore() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (!_uiState.value.isDataLoaded) {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            }
            try {
                storeRepository.observeStore(storeId).collect { store ->
                    if (store == null) {
                        storeCache.remove(storeId)
                        if (_uiState.value.isDataLoaded) {
                            _uiState.value = _uiState.value.copy(error = "Store not found.")
                        }
                        return@collect
                    }
                    storeCache.put(store)
                    val pendingBought = pendingWritesManager.queue.getPendingBought(storeId)
                    val correctedList = if (pendingBought.isEmpty()) {
                        store.shoppingList
                    } else {
                        store.shoppingList.map { ingredient ->
                            pendingBought[ingredient.id]?.let { ingredient.copy(bought = it) } ?: ingredient
                        }
                    }
                    val pendingName = pendingWritesManager.queue.getPendingName(storeId)
                    val correctedStore = if (pendingName != null) store.copy(name = pendingName) else store

                    _uiState.value = _uiState.value.copy(
                        store = correctedStore,
                        shoppingListItems = correctedList.filter { !it.bought },
                        boughtItems = correctedList.filter { it.bought },
                        starredIngredients = store.starredIngredients,
                        recipes = store.recipes,
                        isLoading = false,
                        isDataLoaded = true,
                        error = null
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("StoreViewModel", "loadStore error: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isDataLoaded = true,
                    error = "Error loading store: ${e.message}"
                )
            }
        }
    }

    fun retryLoad() = loadStore()

    // ─── Social (viewModelScope — UI only) ───────────────────────────────────

    private fun loadSentInvites() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch
            userRepository.getSentStoreInvites(userId).collect { invites ->
                _uiState.value = _uiState.value.copy(
                    pendingInviteUserIds = invites.filter { it.storeId == storeId }.map { it.invitedUserId }
                )
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
        val store = _uiState.value.store ?: return
        applicationScope.launch {
            friendIds.forEach { friendId ->
                userRepository.sendStoreInvite(
                    storeId = store.id,
                    storeName = store.name,
                    friendId = friendId
                )
            }
        }
    }

    // ─── Navigation ──────────────────────────────────────────────────────────

    fun selectTab(tab: StoreTab) {
        _uiState.value = _uiState.value.copy(currentTab = tab)
    }

    // ─── Manual ingredient add ────────────────────────────────────────────────

    fun addIngredient(
        name: String,
        quantity: String,
        isStarred: Boolean,
        periodicity: Int? = null,
        conflictStrategy: ConflictStrategy = ConflictStrategy.ASK
    ) {
        val trimmedName = name.trim()
        val currentList = _uiState.value.shoppingListItems + _uiState.value.boughtItems

        if (isStarred) {
            val starred = StarredIngredient(
                id = UUID.randomUUID().toString(),
                name = trimmedName,
                periodicity = periodicity,
                defaultQuantity = quantity,
                conflictStrategy = conflictStrategy.name,
                lastAddedWeek = null
            )
            val newIngredient = if (!currentList.any { it.name.equals(trimmedName, ignoreCase = true) }) {
                Ingredient(
                    id = UUID.randomUUID().toString(),
                    name = trimmedName,
                    quantity = quantity,
                    bought = false,
                    addedBy = starred.id,
                    addedAt = System.currentTimeMillis()
                )
            } else null

            _uiState.value = _uiState.value.copy(
                starredIngredients = _uiState.value.starredIngredients + starred,
                shoppingListItems = if (newIngredient != null)
                    _uiState.value.shoppingListItems + newIngredient
                else
                    _uiState.value.shoppingListItems
            )
            syncCache()

            applicationScope.launch {
                storeRepository.addStarredIngredient(storeId, starred)
                if (newIngredient != null) {
                    storeRepository.addIngredientToShoppingList(storeId, newIngredient, currentList)
                }
            }
        } else {
            if (currentList.any { it.name.equals(trimmedName, ignoreCase = true) }) return
            val ingredient = Ingredient(
                id = UUID.randomUUID().toString(),
                name = trimmedName,
                quantity = quantity,
                bought = false,
                addedBy = "manual",
                addedAt = System.currentTimeMillis()
            )
            _uiState.value = _uiState.value.copy(
                shoppingListItems = _uiState.value.shoppingListItems + ingredient
            )
            syncCache()
            applicationScope.launch {
                storeRepository.addIngredientToShoppingList(storeId, ingredient, currentList)
            }
        }
    }

    // ─── Starred → list ───────────────────────────────────────────────────────

    fun addStarredIngredientToList(starredId: String) {
        if (_uiState.value.pendingStarredAdds.contains(starredId)) return

        val starred = _uiState.value.starredIngredients.find { it.id == starredId } ?: return
        val currentList = _uiState.value.shoppingListItems + _uiState.value.boughtItems
        val existing = currentList.find { it.name.equals(starred.name, ignoreCase = true) }
        val strategy = runCatching { ConflictStrategy.valueOf(starred.conflictStrategy) }
            .getOrDefault(ConflictStrategy.ASK)

        if (strategy == ConflictStrategy.ASK && existing != null) {
            _uiState.value = _uiState.value.copy(
                showConflictDialog = true,
                conflictStarred = starred,
                conflictExisting = existing
            )
            return
        }

        val optimisticList: List<Ingredient>? = when {
            existing == null -> currentList + Ingredient(
                id = UUID.randomUUID().toString(),
                name = starred.name,
                quantity = starred.defaultQuantity,
                bought = false,
                addedBy = starred.id,
                addedAt = System.currentTimeMillis()
            )
            strategy == ConflictStrategy.IGNORE -> null
            strategy == ConflictStrategy.INCREASE -> currentList.map { item ->
                if (item.id == existing.id) item.copy(quantity = combineQuantities(item.quantity, starred.defaultQuantity))
                else item
            }
            strategy == ConflictStrategy.REPLACE -> currentList.map { item ->
                if (item.id == existing.id) item.copy(quantity = starred.defaultQuantity, addedBy = starred.id, addedAt = System.currentTimeMillis())
                else item
            }
            else -> null
        }

        val successMessage = when {
            existing == null -> null
            strategy == ConflictStrategy.REPLACE -> "\"${starred.name}\" quantity replaced"
            strategy == ConflictStrategy.IGNORE -> "\"${starred.name}\" already in list, skipped"
            strategy == ConflictStrategy.INCREASE -> "\"${starred.name}\" quantity increased"
            else -> null
        }

        if (optimisticList != null) {
            _uiState.value = _uiState.value.copy(
                shoppingListItems = optimisticList.filter { !it.bought },
                boughtItems = optimisticList.filter { it.bought },
                snackbarMessage = successMessage,
                pendingStarredAdds = _uiState.value.pendingStarredAdds + starredId
            )
            syncCache()
        } else {
            _uiState.value = _uiState.value.copy(
                snackbarMessage = successMessage,
                pendingStarredAdds = _uiState.value.pendingStarredAdds + starredId
            )
        }

        applicationScope.launch {
            try {
                storeRepository.addStarredIngredientToList(storeId, starred, existing, strategy, currentList)
            } finally {
                _uiState.value = _uiState.value.copy(
                    pendingStarredAdds = _uiState.value.pendingStarredAdds - starredId
                )
            }
        }
    }

    fun resolveConflict(strategy: ConflictStrategy, remember: Boolean) {
        val starred = _uiState.value.conflictStarred ?: return
        val existing = _uiState.value.conflictExisting ?: return
        val currentList = _uiState.value.shoppingListItems + _uiState.value.boughtItems
        val currentStarred = _uiState.value.starredIngredients

        val optimisticList: List<Ingredient> = when (strategy) {
            ConflictStrategy.IGNORE -> currentList
            ConflictStrategy.INCREASE -> currentList.map { item ->
                if (item.id == existing.id) item.copy(quantity = combineQuantities(item.quantity, starred.defaultQuantity))
                else item
            }
            ConflictStrategy.REPLACE -> currentList.map { item ->
                if (item.id == existing.id) item.copy(quantity = starred.defaultQuantity, addedBy = starred.id, addedAt = System.currentTimeMillis())
                else item
            }
            ConflictStrategy.ASK -> currentList
        }

        val successMessage = when (strategy) {
            ConflictStrategy.REPLACE -> "\"${starred.name}\" quantity replaced"
            ConflictStrategy.IGNORE -> "\"${starred.name}\" already in list, skipped"
            ConflictStrategy.INCREASE -> "\"${starred.name}\" quantity increased"
            ConflictStrategy.ASK -> null
        }.let { base -> if (remember && base != null) "$base — preference saved" else base }

        _uiState.value = _uiState.value.copy(
            showConflictDialog = false,
            conflictStarred = null,
            conflictExisting = null,
            shoppingListItems = optimisticList.filter { !it.bought },
            boughtItems = optimisticList.filter { it.bought },
            snackbarMessage = successMessage,
            pendingStarredAdds = _uiState.value.pendingStarredAdds + starred.id
        )
        syncCache()

        applicationScope.launch {
            try {
                if (remember) {
                    storeRepository.updateStarredIngredient(
                        storeId,
                        starred.copy(conflictStrategy = strategy.name),
                        currentStarred
                    )
                }
                storeRepository.addStarredIngredientToList(storeId, starred, existing, strategy, currentList)
            } finally {
                _uiState.value = _uiState.value.copy(
                    pendingStarredAdds = _uiState.value.pendingStarredAdds - starred.id
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

    // ─── Recipe → list ────────────────────────────────────────────────────────

    fun addRecipeToShoppingList(recipeId: String) {
        if (_uiState.value.isAddingRecipe) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAddingRecipe = true)
            try {
                val recipe = _uiState.value.recipes.find { it.id == recipeId } ?: return@launch
                val currentList = _uiState.value.shoppingListItems + _uiState.value.boughtItems

                val recipeStrategy = recipe.conflictStrategy
                    ?.let { runCatching { ConflictStrategy.valueOf(it) }.getOrNull() }
                    ?: ConflictStrategy.ASK

                if (recipeStrategy != ConflictStrategy.ASK) {
                    val allResolutions = recipe.ingredients.associate { it.name to recipeStrategy }
                    val optimisticList = applyResolutionsLocally(recipe, currentList, allResolutions)
                    _uiState.value = _uiState.value.copy(
                        shoppingListItems = optimisticList.filter { !it.bought },
                        boughtItems = optimisticList.filter { it.bought },
                        snackbarMessage = "Added ingredients from \"${recipe.name}\""
                    )
                    syncCache()
                    applicationScope.launch {
                        storeRepository.addRecipeIngredientsToList(storeId, recipe, currentList, allResolutions)
                    }
                    return@launch
                }

                val existingMap = currentList.associateBy { it.name.lowercase() }
                val conflicts = recipe.ingredients.mapNotNull { ri ->
                    existingMap[ri.name.lowercase()]?.let { RecipeConflict(ri, it, recipe.id, recipe.name) }
                }
                val nonConflicting = recipe.ingredients.filter { existingMap[it.name.lowercase()] == null }

                if (nonConflicting.isNotEmpty()) {
                    val newItems = nonConflicting.map { ri ->
                        Ingredient(
                            id = UUID.randomUUID().toString(),
                            name = ri.name,
                            quantity = ri.quantity ?: "",
                            bought = false,
                            addedBy = recipe.id,
                            addedAt = System.currentTimeMillis()
                        )
                    }
                    _uiState.value = _uiState.value.copy(
                        shoppingListItems = _uiState.value.shoppingListItems + newItems
                    )
                    syncCache()
                    applicationScope.launch {
                        storeRepository.addRecipeIngredientsToList(
                            storeId, recipe.copy(ingredients = nonConflicting), currentList, emptyMap()
                        )
                    }
                }

                if (conflicts.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        snackbarMessage = "Added ${nonConflicting.size} ingredients from \"${recipe.name}\""
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        conflictQueue = conflicts,
                        totalRecipeConflicts = conflicts.size,
                        currentRecipeConflict = conflicts.first(),
                        showRecipeConflictDialog = true,
                        pendingRecipeId = recipeId
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("StoreViewModel", "addRecipeToShoppingList error: ${e.message}", e)
                _uiState.value = _uiState.value.copy(snackbarMessage = "Error: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isAddingRecipe = false)
            }
        }
    }

    fun resolveRecipeConflict(strategy: ConflictStrategy, applyToAll: Boolean) {
        val current = _uiState.value.currentRecipeConflict ?: return
        val remaining = _uiState.value.conflictQueue.drop(1)
        val currentList = _uiState.value.shoppingListItems + _uiState.value.boughtItems

        val toResolve = if (applyToAll) listOf(current) + remaining else listOf(current)
        val resolutions = toResolve.associate { it.recipeIngredient.name to strategy }

        val miniRecipe = Recipe(
            id = current.recipeId,
            name = current.recipeName,
            ingredients = toResolve.map { it.recipeIngredient }
        )
        val optimisticList = applyResolutionsLocally(miniRecipe, currentList, resolutions)

        val snackbar = when {
            applyToAll -> "Applied to all ${toResolve.size} duplicates"
            remaining.isEmpty() -> "All duplicates resolved"
            else -> null
        }

        _uiState.value = _uiState.value.copy(
            shoppingListItems = optimisticList.filter { !it.bought },
            boughtItems = optimisticList.filter { it.bought },
            snackbarMessage = snackbar,
            showRecipeConflictDialog = if (applyToAll || remaining.isEmpty()) false else true,
            currentRecipeConflict = if (applyToAll || remaining.isEmpty()) null else remaining.first(),
            conflictQueue = if (applyToAll || remaining.isEmpty()) emptyList() else remaining,
            totalRecipeConflicts = if (applyToAll || remaining.isEmpty()) 0 else _uiState.value.totalRecipeConflicts,
            pendingRecipeId = if (applyToAll || remaining.isEmpty()) null else _uiState.value.pendingRecipeId
        )
        syncCache()
        applicationScope.launch {
            storeRepository.addRecipeIngredientsToList(storeId, miniRecipe, currentList, resolutions)
        }
    }

    fun dismissRecipeConflictDialog() {
        val remaining = _uiState.value.conflictQueue.size
        _uiState.value = _uiState.value.copy(
            showRecipeConflictDialog = false,
            currentRecipeConflict = null,
            conflictQueue = emptyList(),
            totalRecipeConflicts = 0,
            pendingRecipeId = null,
            snackbarMessage = if (remaining > 0) "$remaining duplicate(s) skipped" else null
        )
    }

    // ─── Create recipe ────────────────────────────────────────────────────────

    fun addRecipe(name: String, ingredients: List<Pair<String, String>>, periodicity: Int?) {
        val recipe = Recipe(
            id = UUID.randomUUID().toString(),
            name = name,
            ingredients = ingredients.map { (n, qty) -> RecipeIngredient(name = n, quantity = qty) },
            periodicity = periodicity,
            conflictStrategy = null,
            lastAddedWeek = null
        )
        _uiState.value = _uiState.value.copy(recipes = _uiState.value.recipes + recipe)
        syncCache()
        applicationScope.launch {
            storeRepository.addRecipe(storeId, recipe)
        }
    }

    // ─── Shopping list mutations ──────────────────────────────────────────────

    fun markIngredientAsBought(ingredientId: String) {
        val ingredient = _uiState.value.shoppingListItems.find { it.id == ingredientId } ?: return
        _uiState.value = _uiState.value.copy(
            shoppingListItems = _uiState.value.shoppingListItems.filter { it.id != ingredientId },
            boughtItems = _uiState.value.boughtItems + ingredient.copy(bought = true)
        )
        syncCache()
        pendingWritesManager.markIngredientBought(storeId, ingredientId, true)
    }

    fun markIngredientAsNotBought(ingredientId: String) {
        val ingredient = _uiState.value.boughtItems.find { it.id == ingredientId } ?: return
        _uiState.value = _uiState.value.copy(
            boughtItems = _uiState.value.boughtItems.filter { it.id != ingredientId },
            shoppingListItems = _uiState.value.shoppingListItems + ingredient.copy(bought = false)
        )
        syncCache()
        pendingWritesManager.markIngredientBought(storeId, ingredientId, false)
    }

    fun deleteIngredient(ingredientId: String) {
        val newList = (_uiState.value.shoppingListItems + _uiState.value.boughtItems)
            .filter { it.id != ingredientId }
        _uiState.value = _uiState.value.copy(
            shoppingListItems = newList.filter { !it.bought },
            boughtItems = newList.filter { it.bought }
        )
        syncCache()
        pendingWritesManager.deleteIngredient(storeId, ingredientId)
    }

    fun updateIngredient(
        ingredientId: String,
        name: String,
        quantity: String,
        isStarred: Boolean,
        periodicity: Int?,
        conflictStrategy: ConflictStrategy = ConflictStrategy.ASK
    ) {
        val currentList = _uiState.value.shoppingListItems + _uiState.value.boughtItems
        val currentStarred = _uiState.value.starredIngredients
        val current = currentList.find { it.id == ingredientId } ?: return
        val updated = current.copy(name = name, quantity = quantity)

        val newList = currentList.map { if (it.id == ingredientId) updated else it }
        _uiState.value = _uiState.value.copy(
            shoppingListItems = newList.filter { !it.bought },
            boughtItems = newList.filter { it.bought }
        )
        syncCache()

        applicationScope.launch {
            storeRepository.updateIngredient(storeId, updated, currentList)

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
                    _uiState.value = _uiState.value.copy(
                        starredIngredients = _uiState.value.starredIngredients + starred
                    )
                    storeRepository.addStarredIngredient(storeId, starred)
                    storeRepository.updateIngredient(storeId, updated.copy(addedBy = starred.id), currentList)
                } else {
                    val starred = currentStarred.find { it.id == current.addedBy }
                    if (starred != null) {
                        val updatedStarred = starred.copy(
                            name = name,
                            periodicity = periodicity,
                            defaultQuantity = quantity,
                            conflictStrategy = conflictStrategy.name
                        )
                        _uiState.value = _uiState.value.copy(
                            starredIngredients = currentStarred.map { if (it.id == starred.id) updatedStarred else it }
                        )
                        storeRepository.updateStarredIngredient(storeId, updatedStarred, currentStarred)
                    }
                }
            } else {
                if (current.addedBy != "manual") {
                    _uiState.value = _uiState.value.copy(
                        starredIngredients = currentStarred.filter { it.id != current.addedBy }
                    )
                    storeRepository.deleteStarredIngredient(storeId, current.addedBy, currentStarred)
                    storeRepository.updateIngredient(storeId, updated.copy(addedBy = "manual"), currentList)
                }
            }
        }
    }

    // ─── Starred mutations ────────────────────────────────────────────────────

    fun updateStarredIngredient(
        starredId: String,
        name: String,
        quantity: String,
        periodicity: Int?,
        conflictStrategy: ConflictStrategy
    ) {
        val currentStarred = _uiState.value.starredIngredients
        val starred = currentStarred.find { it.id == starredId } ?: return
        val updatedStarred = starred.copy(
            name = name,
            defaultQuantity = quantity,
            periodicity = periodicity,
            conflictStrategy = conflictStrategy.name
        )
        _uiState.value = _uiState.value.copy(
            starredIngredients = currentStarred.map { if (it.id == starredId) updatedStarred else it }
        )
        syncCache()
        applicationScope.launch {
            storeRepository.updateStarredIngredient(storeId, updatedStarred, currentStarred)
        }
    }

    fun deleteStarredIngredient(starredId: String) {
        val currentStarred = _uiState.value.starredIngredients
        _uiState.value = _uiState.value.copy(
            starredIngredients = currentStarred.filter { it.id != starredId }
        )
        syncCache()
        applicationScope.launch {
            storeRepository.deleteStarredIngredient(storeId, starredId, currentStarred)
        }
    }

    // ─── Recipe mutations ─────────────────────────────────────────────────────

    fun deleteRecipe(recipeId: String) {
        val currentRecipes = _uiState.value.recipes
        _uiState.value = _uiState.value.copy(recipes = currentRecipes.filter { it.id != recipeId })
        syncCache()
        applicationScope.launch {
            storeRepository.deleteRecipe(storeId, recipeId, currentRecipes)
        }
    }

    fun updateRecipe(
        recipeId: String,
        name: String,
        ingredients: List<Pair<String, String>>,
        periodicity: Int?
    ) {
        val currentRecipes = _uiState.value.recipes
        val existing = currentRecipes.find { it.id == recipeId } ?: return
        val updated = existing.copy(
            name = name,
            ingredients = ingredients.map { (n, qty) ->
                RecipeIngredient(name = n, quantity = qty)
            },
            periodicity = periodicity
        )
        _uiState.value = _uiState.value.copy(
            recipes = currentRecipes.map { if (it.id == recipeId) updated else it }
        )
        syncCache()
        applicationScope.launch {
            storeRepository.updateRecipe(storeId, updated, currentRecipes)
        }
    }

    // ─── Store mutations ──────────────────────────────────────────────────────

    fun updateStoreName(newName: String) {
        val currentStore = _uiState.value.store ?: return
        val updatedStore = currentStore.copy(name = newName)
        _uiState.value = _uiState.value.copy(store = updatedStore)
        syncCache()
        pendingWritesManager.updateStoreName(storeId, newName)
    }

    fun deleteStore() {
        applicationScope.launch { storeRepository.deleteStore(storeId) }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun applyResolutionsLocally(
        recipe: Recipe,
        currentList: List<Ingredient>,
        resolutions: Map<String, ConflictStrategy>
    ): List<Ingredient> {
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
                when (resolutions[ri.name] ?: ConflictStrategy.IGNORE) {
                    ConflictStrategy.IGNORE -> Unit
                    ConflictStrategy.INCREASE -> toUpdate.add(
                        existing.copy(quantity = combineQuantities(existing.quantity, ri.quantity ?: ""))
                    )
                    ConflictStrategy.REPLACE -> toUpdate.add(
                        existing.copy(quantity = ri.quantity ?: "", addedBy = recipe.id, addedAt = System.currentTimeMillis())
                    )
                    ConflictStrategy.ASK -> Unit
                }
            }
        }

        return currentList.map { existing ->
            toUpdate.find { it.id == existing.id } ?: existing
        } + toAdd
    }

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

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
    fun clearSnackbar() { _uiState.value = _uiState.value.copy(snackbarMessage = null) }

    private fun syncCache() {
        val current = _uiState.value
        val store = current.store ?: return
        storeCache.put(
            store.copy(
                shoppingList = current.shoppingListItems + current.boughtItems,
                starredIngredients = current.starredIngredients,
                recipes = current.recipes
            )
        )
    }
}