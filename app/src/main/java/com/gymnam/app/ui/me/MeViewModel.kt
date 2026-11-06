// ==============================================================================
// PLAIN-ENGLISH GUIDE
// WHAT THIS FILE IS: the brain of the Me page.
//  - Reads your settings (name, photo, profile, allergies, diets) and latest weight.
//  - Saves changes the moment you make them (no Save button needed).
//  - Finds "possible triggers": foods that keep showing up when you log that a meal
//    made you feel bad (see data/Mood.kt -> suspectCounts).
// ==============================================================================
package com.gymnam.app.ui.me

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymnam.app.GymNamApp
import com.gymnam.app.data.AppSettings
import com.gymnam.app.data.ProfilePhoto
import com.gymnam.app.data.Suspect
import com.gymnam.app.data.WeightEntry
import com.gymnam.app.data.suspectCounts
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MeViewModel(private val app: GymNamApp) : ViewModel() {
    private val store = app.settings

    val settings: StateFlow<AppSettings?> = store.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val latestWeight: StateFlow<WeightEntry?> = app.db.trackingDao().latestWeight()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Foods named in 2+ "felt bad" check-ins, most frequent first. */
    val suspects: StateFlow<List<Suspect>> = app.db.moodDao().badWithFoods().map(::suspectCounts)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setName(name: String) = viewModelScope.launch { store.setUserName(name) }

    /** Turns one allergy on/off. */
    fun toggleAllergy(id: String, current: Set<String>) = viewModelScope.launch {
        store.setAllergies(if (id in current) current - id else current + id)
    }

    fun toggleDiet(id: String, current: Set<String>) = viewModelScope.launch {
        store.setDiets(if (id in current) current - id else current + id)
    }

    fun setOther(text: String) = viewModelScope.launch { store.setOtherAllergies(text) }

    /** Saves the picked photo privately; the new "version" number tells every avatar to reload. */
    fun savePhoto(uri: Uri, onFail: () -> Unit) = viewModelScope.launch {
        if (ProfilePhoto.save(app, uri)) store.setPhotoVersion(System.currentTimeMillis()) else onFail()
    }

    fun removePhoto() = viewModelScope.launch {
        ProfilePhoto.delete(app)
        store.setPhotoVersion(0)
    }
}
