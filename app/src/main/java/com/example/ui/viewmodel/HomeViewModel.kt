package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.model.Story
import com.example.data.model.StoryChat
import com.example.data.model.UserProfile
import com.example.data.repository.StoryRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class HomeViewModel(
    private val repository: StoryRepository,
    private val currentUserId: String,
    private val userEmail: String?,
) : ViewModel() {

    val isAdminUser: Boolean = userEmail.equals(StoryRepository.ADMIN_EMAIL, ignoreCase = true)

    val storiesState: StateFlow<UiState<List<Story>>> = repository.observeActiveStories()
        .map<List<Story>, UiState<List<Story>>> { stories ->
            if (stories.isEmpty()) {
                UiState.Success(StoryRepository.DEFAULT_STARTER_STORIES)
            } else {
                UiState.Success(stories)
            }
        }
        .catch { error ->
            val fsError = unwrapFirestoreError(error)
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "Failed to observe stories", root)
            emit(UiState.Error(root.message ?: "Failed to load stories."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading,
        )

    val userProfileState: StateFlow<UiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error ->
            val fsError = unwrapFirestoreError(error)
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "Failed to observe user profile", root)
            emit(UiState.Error(root.message ?: "Failed to load profile."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading,
        )

    val userChatsState: StateFlow<Map<String, StoryChat>> = repository.observeUserChats(currentUserId)
        .map { chats -> chats.associateBy { it.storyId } }
        .catch { error ->
            val fsError = unwrapFirestoreError(error)
            if (fsError == null && error is CancellationException) throw error
            Log.w(TAG, "Failed to observe user chats", fsError ?: error)
            emit(emptyMap())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyMap(),
        )

    private val _isSavingName = MutableStateFlow(false)
    val isSavingName: StateFlow<Boolean> = _isSavingName.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    init {
        if (isAdminUser) {
            viewModelScope.launch {
                try {
                    repository.seedDefaultStoriesIfEmpty()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Starter seed skipped or failed: ${e.message}")
                }
            }
        }
    }

    fun savePlayerName(name: String, onSaved: () -> Unit = {}) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _actionError.value = "Please enter your adventure name."
            return
        }
        viewModelScope.launch {
            _isSavingName.value = true
            _actionError.value = null
            try {
                repository.saveUserProfile(name = trimmed, email = userEmail)
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error saving player name", e)
                _actionError.value = e.localizedMessage ?: "Could not save player name."
            } finally {
                _isSavingName.value = false
            }
        }
    }

    fun clearError() {
        _actionError.value = null
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    companion object {
        private const val TAG = "HomeViewModel"

        fun provideFactory(
            currentUserId: String,
            userEmail: String?,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                HomeViewModel(
                    repository = StoryRepository(db),
                    currentUserId = currentUserId,
                    userEmail = userEmail,
                )
            }
        }
    }
}
