package com.zanoni.lardr.ui.screens.store

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zanoni.lardr.data.model.Ingredient
import com.zanoni.lardr.data.model.StarredIngredient
import com.zanoni.lardr.ui.components.AddIngredientDialog
import com.zanoni.lardr.ui.components.AddRecipeDialog
import com.zanoni.lardr.ui.components.BottomNavBar
import com.zanoni.lardr.ui.components.ConflictDialog
import com.zanoni.lardr.ui.components.ConflictResolutionDialog
import com.zanoni.lardr.ui.components.RecipeConflictResolutionDialog
import com.zanoni.lardr.ui.components.EditIngredientDialog
import com.zanoni.lardr.ui.components.ErrorScreen
import com.zanoni.lardr.ui.components.LoadingScreen
import com.zanoni.lardr.ui.components.UpdateStoreDialog
import com.zanoni.lardr.ui.screens.store.tabs.RecipesTab
import com.zanoni.lardr.ui.screens.store.tabs.ShoppingListTab
import com.zanoni.lardr.ui.screens.store.tabs.StarredIngredientsTab
import androidx.compose.material.icons.filled.Share
import com.zanoni.lardr.ui.components.ShareStoreDialog

data class StoreTabItem(
    val tab: StoreTab,
    val icon: ImageVector,
    val label: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    viewModel: StoreViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showShareDialog by remember { mutableStateOf(false) }

    var showUpdateDialog by remember { mutableStateOf(false) }
    var showAddIngredientDialog by remember { mutableStateOf(false) }
    var addIngredientInitialStarred by remember { mutableStateOf(false) }
    var showAddRecipeDialog by remember { mutableStateOf(false) }
    var showEditIngredientDialog by remember { mutableStateOf(false) }
    var ingredientToEdit by remember { mutableStateOf<Ingredient?>(null) }
    var showEditStarredDialog by remember { mutableStateOf(false) }
    var starredToEdit by remember { mutableStateOf<StarredIngredient?>(null) }

    val tabs = listOf(
        StoreTabItem(StoreTab.SHOPPING_LIST, Icons.Default.ShoppingCart, "List"),
        StoreTabItem(StoreTab.STARRED, Icons.Default.Star, "Starred"),
        StoreTabItem(StoreTab.RECIPES, Icons.Filled.Star, "Recipes")
    )

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(uiState.store?.name ?: "Store") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showShareDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share store"
                        )
                    }
                    IconButton(onClick = { showUpdateDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit store"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            BottomNavBar(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    when (route) {
                        "home" -> onNavigateBack()
                        "settings" -> onNavigateToSettings()
                        "add" -> {
                            when (uiState.currentTab) {
                                StoreTab.SHOPPING_LIST -> {
                                    addIngredientInitialStarred = false
                                    showAddIngredientDialog = true
                                }

                                StoreTab.STARRED -> {
                                    addIngredientInitialStarred = true
                                    showAddIngredientDialog = true
                                }

                                StoreTab.RECIPES -> showAddRecipeDialog = true
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    when (uiState.currentTab) {
                        StoreTab.SHOPPING_LIST -> {
                            addIngredientInitialStarred = false
                            showAddIngredientDialog = true
                        }

                        StoreTab.STARRED -> {
                            addIngredientInitialStarred = true
                            showAddIngredientDialog = true
                        }

                        StoreTab.RECIPES -> showAddRecipeDialog = true
                    }
                },
                containerColor = MaterialTheme.colorScheme.tertiary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add",
                    tint = MaterialTheme.colorScheme.onTertiary
                )
            }
        }
    ) { paddingValues ->
        when {
            !uiState.isDataLoaded && uiState.error == null -> {
                LoadingScreen(
                    message = "Loading store...",
                    modifier = Modifier.padding(paddingValues)
                )
            }

            uiState.error != null -> {
                ErrorScreen(
                    message = uiState.error!!,
                    onRetry = { viewModel.retryLoad() },
                    modifier = Modifier.padding(paddingValues)
                )
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    TabRow(
                        selectedTabIndex = tabs.indexOfFirst { it.tab == uiState.currentTab },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        tabs.forEach { tabItem ->
                            Tab(
                                selected = uiState.currentTab == tabItem.tab,
                                onClick = { viewModel.selectTab(tabItem.tab) },
                                icon = {
                                    Icon(
                                        imageVector = tabItem.icon,
                                        contentDescription = tabItem.label
                                    )
                                },
                                text = { Text(tabItem.label) }
                            )
                        }
                    }

                    when (uiState.currentTab) {
                        StoreTab.SHOPPING_LIST -> {
                            ShoppingListTab(
                                items = uiState.shoppingListItems,
                                boughtItems = uiState.boughtItems,
                                onMarkAsBought = { viewModel.markIngredientAsBought(it) },
                                onMarkAsNotBought = { viewModel.markIngredientAsNotBought(it) },
                                onDeleteIngredient = { viewModel.deleteIngredient(it) },
                                onEditIngredient = { ingredient ->
                                    ingredientToEdit = ingredient
                                    showEditIngredientDialog = true
                                }
                            )
                        }

                        StoreTab.STARRED -> {
                            StarredIngredientsTab(
                                starredIngredients = uiState.starredIngredients,
                                onAddToList = { viewModel.addStarredIngredientToList(it) },
                                onEdit = { starred ->
                                    starredToEdit = starred
                                    showEditStarredDialog = true
                                }
                            )
                        }

                        StoreTab.RECIPES -> {
                            RecipesTab(
                                recipes = uiState.recipes,
                                onRecipeClick = { /* TODO: Show recipe details */ },
                                onAddRecipeToList = { viewModel.addRecipeToShoppingList(it) }
                            )
                        }
                    }
                }
            }
        }

        if (showShareDialog) {
            ShareStoreDialog(
                storeName = uiState.store?.name ?: "",
                friends = uiState.friends,
                onDismiss = { showShareDialog = false },
                onConfirm = { friendIds ->
                    viewModel.shareStore(friendIds)
                    showShareDialog = false
                },
                onNavigateToFriends = { onNavigate("friends") }
            )
        }

        if (showUpdateDialog) {
            UpdateStoreDialog(
                storeName = uiState.store?.name ?: "",
                onDismiss = { showUpdateDialog = false },
                onUpdateName = { newName ->
                    viewModel.updateStoreName(newName)
                    showUpdateDialog = false
                },
                onDelete = {
                    viewModel.deleteStore()
                    onNavigateBack()
                }
            )
        }

        if (showAddIngredientDialog) {
            AddIngredientDialog(
                initialIsStarred = addIngredientInitialStarred,
                existingNames = if (addIngredientInitialStarred) {
                    // For starred: check starred names to prevent duplicates
                    uiState.starredIngredients.map { it.name }
                } else {
                    // For shopping list: check shopping list names to prevent duplicates
                    uiState.shoppingListItems.map { it.name }
                },
                onDismiss = { showAddIngredientDialog = false },
                onAdd = { name, quantity, periodicity ->
                    viewModel.addIngredient(name, quantity, periodicity)
                    showAddIngredientDialog = false
                }
            )
        }

        if (showAddRecipeDialog) {
            AddRecipeDialog(
                onDismiss = { showAddRecipeDialog = false },
                onAdd = { name, ingredients, periodicity ->
                    viewModel.addRecipe(name, ingredients, periodicity)
                    showAddRecipeDialog = false
                }
            )
        }

        if (showEditIngredientDialog && ingredientToEdit != null) {
            val ingredient = ingredientToEdit!!
            val starredIngredient = uiState.starredIngredients.find { it.id == ingredient.addedBy }

            EditIngredientDialog(
                ingredient = ingredient,
                initialStarred = starredIngredient != null,
                initialPeriodicity = starredIngredient?.periodicity,
                onDismiss = {
                    showEditIngredientDialog = false
                    ingredientToEdit = null
                },
                onUpdate = { name, quantity, periodicity ->
                    viewModel.updateIngredient(ingredient.id, name, quantity, periodicity)
                    showEditIngredientDialog = false
                    ingredientToEdit = null
                },
                onDelete = {
                    viewModel.deleteIngredient(ingredient.id)
                    showEditIngredientDialog = false
                    ingredientToEdit = null
                }
            )
        }

        if (uiState.showConflictDialog) {
            ConflictResolutionDialog(
                ingredientName = uiState.conflictStarred?.name ?: "",
                existingQuantity = uiState.conflictExisting?.quantity ?: "",
                newQuantity = uiState.conflictStarred?.defaultQuantity ?: "",
                onDismiss = {
                    viewModel.dismissConflictDialog()
                },
                onResolve = { strategy, remember ->
                    viewModel.resolveConflict(strategy, remember)
                }
            )
        }

        if (uiState.showRecipeConflictDialog && uiState.currentRecipeConflict != null) {
            val conflict = uiState.currentRecipeConflict!!
            val currentIndex = uiState.conflictQueue.indexOf(conflict)

            RecipeConflictResolutionDialog(
                ingredientName = conflict.recipeIngredient.name,
                existingQuantity = conflict.existingIngredient.quantity,
                newQuantity = conflict.recipeIngredient.quantity ?: "",
                currentIndex = currentIndex,
                totalConflicts = uiState.conflictQueue.size,
                onDismiss = {
                    viewModel.dismissRecipeConflictDialog()
                },
                onResolve = { strategy, applyToAll ->
                    viewModel.resolveRecipeConflict(strategy, applyToAll)
                }
            )
        }

        if (showEditStarredDialog && starredToEdit != null) {
            val starred = starredToEdit!!

            EditIngredientDialog(
                starredIngredient = starred,
                onDismiss = {
                    showEditStarredDialog = false
                    starredToEdit = null
                },
                onUpdate = { name, quantity, periodicity ->
                    viewModel.updateStarredIngredient(starred.id, name, quantity, periodicity)
                    showEditStarredDialog = false
                    starredToEdit = null
                },
                onDelete = {
                    viewModel.deleteStarredIngredient(starred.id)
                    showEditStarredDialog = false
                    starredToEdit = null
                }
            )
        }

        if (uiState.showConflictDialog && uiState.conflictStarred != null && uiState.conflictExisting != null) {
            ConflictDialog(
                ingredientName = uiState.conflictStarred!!.name,
                existingQuantity = uiState.conflictExisting!!.quantity,
                newQuantity = uiState.conflictStarred!!.defaultQuantity,
                onDismiss = { viewModel.dismissConflictDialog() },
                onResolve = { strategy, remember ->
                    viewModel.resolveConflict(strategy, remember)
                }
            )
        }
    }
}