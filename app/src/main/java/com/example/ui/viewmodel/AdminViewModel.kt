package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.ai.StoryAiService
import com.example.data.model.Story
import com.example.data.model.StoryChat
import com.example.data.model.UserProfile
import com.example.data.remote.SceneStorageService
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

sealed interface CoverGenState {
    data object Idle : CoverGenState
    data object Loading : CoverGenState
    data class Ready(val bitmap: Bitmap, val fileUri: String) : CoverGenState
    data class Error(val message: String) : CoverGenState
}

class AdminViewModel(
    private val repository: StoryRepository,
    private val aiService: StoryAiService,
    private val storageService: SceneStorageService,
) : ViewModel() {

    val storiesState: StateFlow<UiState<List<Story>>> = repository.observeAllStoriesAdmin()
        .map<List<Story>, UiState<List<Story>>> { UiState.Success(it) }
        .catch { error ->
            val fsError = unwrapFirestoreError(error)
            if (fsError == null && error is CancellationException) throw error
            emit(UiState.Error((fsError ?: error).message ?: "Failed to load stories."))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val usersState: StateFlow<UiState<List<UserProfile>>> = repository.observeAllUsersAdmin()
        .map<List<UserProfile>, UiState<List<UserProfile>>> { UiState.Success(it) }
        .catch { error ->
            val fsError = unwrapFirestoreError(error)
            if (fsError == null && error is CancellationException) throw error
            emit(UiState.Error((fsError ?: error).message ?: "Failed to load users."))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val chatsState: StateFlow<UiState<List<StoryChat>>> = repository.observeAllChatsAdmin()
        .map<List<StoryChat>, UiState<List<StoryChat>>> { UiState.Success(it) }
        .catch { error ->
            val fsError = unwrapFirestoreError(error)
            if (fsError == null && error is CancellationException) throw error
            emit(UiState.Error((fsError ?: error).message ?: "Failed to load chats."))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _coverGenState = MutableStateFlow<CoverGenState>(CoverGenState.Idle)
    val coverGenState: StateFlow<CoverGenState> = _coverGenState.asStateFlow()

    fun saveStory(
        existingId: String?,
        title: String,
        description: String,
        systemPrompt: String,
        coverImage: String,
        genre: String,
        openingLine: String,
        isActive: Boolean,
        onComplete: () -> Unit,
    ) {
        if (title.isBlank() || description.isBlank() || systemPrompt.isBlank()) {
            _statusMessage.value = "Title, description, and system prompt are required."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val story = Story(
                    id = existingId.orEmpty(),
                    title = title,
                    description = description,
                    systemPrompt = systemPrompt,
                    coverImage = coverImage.ifBlank { "preset:mystic_realm" },
                    genre = genre.ifBlank { "Adventure" },
                    openingLine = openingLine,
                    isActive = isActive,
                )
                if (existingId.isNullOrBlank()) {
                    repository.createStory(story)
                    _statusMessage.value = "Story '${story.title}' created!"
                } else {
                    repository.updateStory(story)
                    _statusMessage.value = "Story '${story.title}' updated!"
                }
                onComplete()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save story", e)
                _statusMessage.value = e.localizedMessage ?: "Failed to save story."
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun toggleStoryActive(story: Story) {
        viewModelScope.launch {
            try {
                repository.updateStory(story.copy(isActive = !story.isActive))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusMessage.value = e.localizedMessage ?: "Failed to update story status."
            }
        }
    }

    fun deleteStory(storyId: String) {
        viewModelScope.launch {
            try {
                repository.deleteStory(storyId)
                _statusMessage.value = "Story deleted."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusMessage.value = e.localizedMessage ?: "Failed to delete story."
            }
        }
    }

    fun seedStarterStories() {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                repository.seedDefaultStoriesIfEmpty()
                _statusMessage.value = "Starter adventure stories ready."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusMessage.value = e.localizedMessage ?: "Could not seed stories."
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun generateCoverArt(context: Context, prompt: String) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            _coverGenState.value = CoverGenState.Loading
            try {
                val bitmap = aiService.generateSceneImage("Netflix movie poster key art, vertical adventure cover: $prompt")
                val uploaded = storageService.uploadStoryCover(
                    context = context,
                    storyKey = prompt.take(24),
                    bitmap = bitmap,
                )
                if (uploaded.note != null) {
                    _statusMessage.value = uploaded.note
                }
                _coverGenState.value = CoverGenState.Ready(bitmap, uploaded.url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Cover generation failed", e)
                _coverGenState.value = CoverGenState.Error(e.localizedMessage ?: "Cover generation failed.")
            }
        }
    }

    fun editCoverArt(context: Context, instruction: String) {
        val current = _coverGenState.value as? CoverGenState.Ready ?: return
        if (instruction.isBlank()) return
        viewModelScope.launch {
            _coverGenState.value = CoverGenState.Loading
            try {
                val edited = aiService.editSceneImage(current.bitmap, instruction)
                val uploaded = storageService.uploadStoryCover(
                    context = context,
                    storyKey = instruction.take(24),
                    bitmap = edited,
                )
                if (uploaded.note != null) {
                    _statusMessage.value = uploaded.note
                }
                _coverGenState.value = CoverGenState.Ready(edited, uploaded.url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Cover edit failed", e)
                _coverGenState.value = CoverGenState.Error(e.localizedMessage ?: "Cover edit failed.")
            }
        }
    }

    fun buildSystemPromptFromDescription(title: String, genre: String, description: String): String {
        val cleanTitle = title.trim().ifBlank { "New Adventure" }
        val cleanGenre = genre.trim().ifBlank { "Adventure" }
        val cleanDesc = description.trim()
        return "You are the Game Master of '$cleanTitle' ($cleanGenre). Premise: $cleanDesc. STRICT RULES: Pure adventure, mystery, survival, and exploration ONLY — strictly NO romance. Write cinematic scenes in natural Hinglish or English matching the player's language. Always call the player by their saved name, react directly to custom messages, and end with 3 clear action choices A, B, C."
    }

    fun resetCoverGen() {
        _coverGenState.value = CoverGenState.Idle
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    companion object {
        private const val TAG = "AdminViewModel"

        fun provideFactory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                AdminViewModel(
                    repository = StoryRepository(db),
                    aiService = StoryAiService(),
                    storageService = SceneStorageService(),
                )
            }
        }
    }
}
