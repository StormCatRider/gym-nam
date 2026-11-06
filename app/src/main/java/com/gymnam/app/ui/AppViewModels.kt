// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the "factory" that builds each screen's ViewModel.
//
// WHAT'S A VIEWMODEL? Every screen is split in two:
//  - the SCREEN file (...Screen.kt) = what you SEE (buttons, cards, text).
//  - the VIEWMODEL file (...ViewModel.kt) = the BRAIN behind it: loads data,
//    does the math, saves what you tap. It survives phone rotation, so you
//    never lose what you were typing.
// This factory hands each ViewModel the tools it needs (database, settings...).
// If you add a new screen with a ViewModel, add one "initializer { ... }" here.
// ==============================================================================
package com.gymnam.app.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gymnam.app.GymNamApp
import com.gymnam.app.ui.add.FoodEditorViewModel
import com.gymnam.app.ui.fridge.FridgeItemViewModel
import com.gymnam.app.ui.fridge.FridgeViewModel
import com.gymnam.app.ui.recipes.RecipeDetailViewModel
import com.gymnam.app.ui.recipes.RecipeEditorViewModel
import com.gymnam.app.ui.picker.PickerViewModel
import com.gymnam.app.ui.progress.ProgressViewModel
import com.gymnam.app.ui.quick.QuickAddViewModel
import com.gymnam.app.ui.settings.SettingsViewModel
import com.gymnam.app.ui.today.TodayViewModel

// Small helper: "get me the app's toolbox (GymNamApp)".
private fun CreationExtras.app(): GymNamApp =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as GymNamApp

/** One factory for every screen's ViewModel. */
val AppViewModelFactory = viewModelFactory {
    // One line per ViewModel. createSavedStateHandle() = the screen's arguments
    // (e.g. WHICH food to edit), passed from the navigation.
    initializer { TodayViewModel(app().db.foodDao(), app().db.savedDao(), app().db.trackingDao(), app().settings) }
    initializer { FoodEditorViewModel(createSavedStateHandle(), app().db.foodDao(), app().db.savedDao()) }
    initializer {
        PickerViewModel(createSavedStateHandle(), app().db.foodDao(), app().db.savedDao(),
            app().catalog, app().off, app().settings)
    }
    initializer { SettingsViewModel(app()) }
    initializer { ProgressViewModel(app().db.foodDao(), app().db.trackingDao(), app().settings) }
    initializer { QuickAddViewModel(app().db.trackingDao()) }
    initializer {
        FridgeViewModel(app().db.fridgeDao(), app().db.recipeDao(), app().db.foodDao(),
            app().builtInRecipes, app().mealDb, app().settings)
    }
    initializer {
        FridgeItemViewModel(createSavedStateHandle(), app().db.fridgeDao(), app().db.savedDao(), app().off, app().settings)
    }
    initializer {
        RecipeDetailViewModel(createSavedStateHandle(), app().db.recipeDao(), app().db.fridgeDao(),
            app().db.foodDao(), app().builtInRecipes, app().mealDb)
    }
    initializer { RecipeEditorViewModel(createSavedStateHandle(), app().db.recipeDao(), app().db.savedDao(), app().catalog) }
}
