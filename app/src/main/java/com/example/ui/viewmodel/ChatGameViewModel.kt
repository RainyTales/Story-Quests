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
import com.example.data.ai.LiveVoiceManager
import com.example.data.ai.LiveVoiceState
import com.example.data.ai.StoryAiService
import com.example.data.model.ChatMessage
import com.example.data.model.Story
import com.example.data.model.StoryChat
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface SceneArtState {
    data object Idle : SceneArtState
    data object Loading : SceneArtState
    data class Ready(
        val bitmap: Bitmap,
        val prompt: String,
        val storageUrl: String = "",
        val isFirebaseStorage: Boolean = false,
    ) : SceneArtState
    data class Error(val message: String) : SceneArtState
}

data class ChatGameUiData(
    val story: Story,
    val chat: StoryChat?,
    val messages: List<ChatMessage>,
    val summary: String,
    val totalMessageCount: Long,
    val currentOptions: List<String>,
    val latestImagePrompt: String,
    val activeSegmentMessageId: String,
    val activeSegmentImageUrl: String,
)

class ChatGameViewModel(
    private val appContext: Context,
    private val repository: StoryRepository,
    private val aiService: StoryAiService,
    private val storageService: SceneStorageService,
    private val liveVoiceManager: LiveVoiceManager,
    private val currentUserId: String,
    private val storyId: String,
    private val fallbackPlayerName: String,
) : ViewModel() {

    private val _storyState = MutableStateFlow<Story?>(null)
    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _isSummarizing = MutableStateFlow(false)
    val isSummarizing: StateFlow<Boolean> = _isSummarizing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _storageNotice = MutableStateFlow<String?>(null)
    val storageNotice: StateFlow<String?> = _storageNotice.asStateFlow()

    private val _sceneArtState = MutableStateFlow<SceneArtState>(SceneArtState.Idle)
    val sceneArtState: StateFlow<SceneArtState> = _sceneArtState.asStateFlow()

    private val _illustratingMessageId = MutableStateFlow<String?>(null)
    val illustratingMessageId: StateFlow<String?> = _illustratingMessageId.asStateFlow()

    private val _autoIllustrateEnabled = MutableStateFlow(true)
    val autoIllustrateEnabled: StateFlow<Boolean> = _autoIllustrateEnabled.asStateFlow()

    private val _voiceState = MutableStateFlow<LiveVoiceState>(LiveVoiceState.Idle)
    val voiceState: StateFlow<LiveVoiceState> = _voiceState.asStateFlow()

    private val _playerName = MutableStateFlow(fallbackPlayerName)
    val playerName: StateFlow<String> = _playerName.asStateFlow()

    val chatUiState: StateFlow<UiState<ChatGameUiData>> = combine(
        _storyState,
        repository.observeChat(currentUserId, storyId),
        repository.observeUserProfile(currentUserId),
    ) { story, chat, profile ->
        if (!profile?.name.isNullOrBlank()) {
            _playerName.value = profile!!.name
        }
        val resolvedStory = story ?: StoryRepository.DEFAULT_STARTER_STORIES.find { it.id == storyId }
        if (resolvedStory == null) {
            UiState.Loading
        } else {
            val activeMessages = if (chat != null && chat.messages.isNotEmpty()) {
                chat.messages
            } else {
                listOf(buildOpeningMessage(resolvedStory, _playerName.value))
            }
            val latestModelMsg = activeMessages.lastOrNull { it.role == "model" }
            val latestIllustratedUrl = activeMessages
                .lastOrNull { it.role == "model" && it.sceneImageUrl.isNotBlank() }
                ?.sceneImageUrl
                .orEmpty()
            val options = latestModelMsg?.options?.takeIf { it.isNotEmpty() }
                ?: listOf(
                    "Aas-paas dhyan se dekho",
                    "Saamne wale se baat karo",
                    "Apne agle kadam ki taiyari karo"
                )
            val imagePrompt = latestModelMsg?.imagePrompt?.ifBlank { null }
                ?: "Dramatic adventure scene for ${resolvedStory.title}"

            UiState.Success(
                ChatGameUiData(
                    story = resolvedStory,
                    chat = chat,
                    messages = activeMessages,
                    summary = chat?.summary.orEmpty(),
                    totalMessageCount = chat?.messageCount ?: activeMessages.size.toLong(),
                    currentOptions = options,
                    latestImagePrompt = imagePrompt,
                    activeSegmentMessageId = latestModelMsg?.id.orEmpty(),
                    activeSegmentImageUrl = latestModelMsg?.sceneImageUrl?.ifBlank { latestIllustratedUrl } ?: latestIllustratedUrl,
                )
            )
        }
    }.catch { error ->
        val fsError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (fsError == null && error is CancellationException) throw error
        val root = fsError ?: error
        Log.w(TAG, "Error observing chat game", root)
        emit(UiState.Error(root.message ?: "Failed to load chat session."))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Loading,
    )

    init {
        loadStory()
    }

    private fun loadStory() {
        viewModelScope.launch {
            try {
                val remote = repository.getStory(storyId)
                _storyState.value = remote
                    ?: StoryRepository.DEFAULT_STARTER_STORIES.find { it.id == storyId }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Falling back to starter story if available: ${e.message}")
                _storyState.value = StoryRepository.DEFAULT_STARTER_STORIES.find { it.id == storyId }
                if (_storyState.value == null) {
                    _errorMessage.value = e.localizedMessage ?: "Failed to load story."
                }
            }
        }
    }

    fun toggleAutoIllustrate() {
        _autoIllustrateEnabled.value = !_autoIllustrateEnabled.value
    }

    fun sendPlayerMessage(rawInput: String) {
        val text = rawInput.trim()
        if (text.isEmpty() || _isSending.value) return
        val currentState = (chatUiState.value as? UiState.Success)?.data ?: return

        viewModelScope.launch {
            _isSending.value = true
            _errorMessage.value = null
            try {
                val userMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = "user",
                    content = text,
                    timestampMs = System.currentTimeMillis(),
                )
                val historyWithUser = currentState.messages + userMsg

                // Persist user message immediately so UI & Firestore stay in sync
                val countAfterUser = currentState.totalMessageCount + 1L
                repository.saveChatState(
                    storyId = currentState.story.id,
                    storyTitle = currentState.story.title,
                    messages = historyWithUser,
                    summary = currentState.summary,
                    messageCount = countAfterUser,
                )

                // Call Gemini AI with ONLY System Prompt + Player Name + Summary + Last 10 messages
                val turnResult = aiService.generateNextTurn(
                    story = currentState.story,
                    playerName = _playerName.value,
                    summary = currentState.summary,
                    allMessages = currentState.messages,
                    playerMessage = text,
                )

                val modelMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = "model",
                    content = turnResult.narrative,
                    timestampMs = System.currentTimeMillis(),
                    options = turnResult.options,
                    imagePrompt = turnResult.imagePrompt,
                )
                val updatedMessages = historyWithUser + modelMsg
                val newTotalCount = countAfterUser + 1L

                // Auto-generate summary after every 20 messages
                val shouldSummarize = newTotalCount >= 20L &&
                    ((newTotalCount / 20L) > (currentState.totalMessageCount / 20L))

                val updatedSummary = if (shouldSummarize) {
                    _isSummarizing.value = true
                    try {
                        aiService.generateRollingSummary(
                            storyTitle = currentState.story.title,
                            playerName = _playerName.value,
                            previousSummary = currentState.summary,
                            recentMessages = updatedMessages,
                        )
                    } finally {
                        _isSummarizing.value = false
                    }
                } else {
                    currentState.summary
                }

                repository.saveChatState(
                    storyId = currentState.story.id,
                    storyTitle = currentState.story.title,
                    messages = updatedMessages,
                    summary = updatedSummary,
                    messageCount = newTotalCount,
                )

                // Dynamically generate scene art for the new story segment, upload to Firebase Storage,
                // and persist its download URL so Coil loads it into the game screen.
                if (_autoIllustrateEnabled.value && modelMsg.imagePrompt.isNotBlank()) {
                    launch {
                        illustrateSegmentAndUpload(
                            story = currentState.story,
                            targetMessageId = modelMsg.id,
                            promptText = modelMsg.imagePrompt,
                            updateBottomSheet = false,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process story turn", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to generate story continuation."
            } finally {
                _isSending.value = false
            }
        }
    }

    fun generateSceneArt(promptText: String, targetMessageId: String? = null) {
        val cleanPrompt = promptText.trim()
        if (cleanPrompt.isEmpty()) return
        val currentState = (chatUiState.value as? UiState.Success)?.data ?: return
        val resolvedMsgId = targetMessageId?.takeIf { it.isNotBlank() }
            ?: currentState.activeSegmentMessageId

        viewModelScope.launch {
            illustrateSegmentAndUpload(
                story = currentState.story,
                targetMessageId = resolvedMsgId,
                promptText = cleanPrompt,
                updateBottomSheet = true,
            )
        }
    }

    fun editSceneArt(instruction: String, targetMessageId: String? = null) {
        val current = _sceneArtState.value as? SceneArtState.Ready ?: return
        val cleanInstruction = instruction.trim()
        if (cleanInstruction.isEmpty()) return
        val currentState = (chatUiState.value as? UiState.Success)?.data ?: return
        val resolvedMsgId = targetMessageId?.takeIf { it.isNotBlank() }
            ?: currentState.activeSegmentMessageId

        viewModelScope.launch {
            _sceneArtState.value = SceneArtState.Loading
            _illustratingMessageId.value = resolvedMsgId
            try {
                val edited = aiService.editSceneImage(current.bitmap, cleanInstruction)
                val uploaded = storageService.uploadSceneIllustration(
                    context = appContext,
                    userId = currentUserId,
                    storyId = currentState.story.id,
                    messageId = "${resolvedMsgId}_${System.currentTimeMillis()}",
                    bitmap = edited,
                )
                if (uploaded.note != null) {
                    _storageNotice.value = uploaded.note
                }
                attachIllustrationUrlToMessage(
                    story = currentState.story,
                    targetMessageId = resolvedMsgId,
                    imageUrl = uploaded.url,
                )
                _sceneArtState.value = SceneArtState.Ready(
                    bitmap = edited,
                    prompt = cleanInstruction,
                    storageUrl = uploaded.url,
                    isFirebaseStorage = uploaded.isFirebaseStorage,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Scene image editing failed", e)
                _sceneArtState.value = SceneArtState.Error(
                    e.localizedMessage ?: "Could not edit scene illustration."
                )
            } finally {
                _illustratingMessageId.value = null
            }
        }
    }

    private suspend fun illustrateSegmentAndUpload(
        story: Story,
        targetMessageId: String,
        promptText: String,
        updateBottomSheet: Boolean,
    ) {
        if (updateBottomSheet) {
            _sceneArtState.value = SceneArtState.Loading
        }
        _illustratingMessageId.value = targetMessageId
        try {
            val bitmap = aiService.generateSceneImage(promptText)
            val uploaded = storageService.uploadSceneIllustration(
                context = appContext,
                userId = currentUserId,
                storyId = story.id,
                messageId = targetMessageId,
                bitmap = bitmap,
            )
            if (uploaded.note != null) {
                _storageNotice.value = uploaded.note
            }
            attachIllustrationUrlToMessage(
                story = story,
                targetMessageId = targetMessageId,
                imageUrl = uploaded.url,
            )
            if (updateBottomSheet) {
                _sceneArtState.value = SceneArtState.Ready(
                    bitmap = bitmap,
                    prompt = promptText,
                    storageUrl = uploaded.url,
                    isFirebaseStorage = uploaded.isFirebaseStorage,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Scene image generation/upload failed", e)
            if (updateBottomSheet) {
                _sceneArtState.value = SceneArtState.Error(
                    e.localizedMessage ?: "Could not generate scene illustration."
                )
            }
        } finally {
            if (_illustratingMessageId.value == targetMessageId) {
                _illustratingMessageId.value = null
            }
        }
    }

    private suspend fun attachIllustrationUrlToMessage(
        story: Story,
        targetMessageId: String,
        imageUrl: String,
    ) {
        val latestData = (chatUiState.value as? UiState.Success)?.data ?: return
        val updatedMessages = latestData.messages.map { msg ->
            if (msg.id == targetMessageId) msg.copy(sceneImageUrl = imageUrl) else msg
        }
        if (updatedMessages == latestData.messages && latestData.chat != null) return
        repository.saveChatState(
            storyId = story.id,
            storyTitle = story.title,
            messages = updatedMessages,
            summary = latestData.summary,
            messageCount = latestData.totalMessageCount.coerceAtLeast(updatedMessages.size.toLong()),
        )
    }

    fun dismissStorageNotice() {
        _storageNotice.value = null
    }

    fun startLiveVoice(context: Context) {
        val currentState = (chatUiState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _voiceState.value = LiveVoiceState.Connecting
            try {
                liveVoiceManager.startVoiceAdventure(
                    context = context,
                    story = currentState.story,
                    playerName = _playerName.value,
                    summary = currentState.summary,
                    recentMessages = currentState.messages,
                )
                _voiceState.value = LiveVoiceState.Active
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Live voice connection failed", e)
                _voiceState.value = LiveVoiceState.Error(
                    e.localizedMessage ?: "Unable to start Live API voice session."
                )
            }
        }
    }

    fun stopLiveVoice() {
        viewModelScope.launch {
            try {
                liveVoiceManager.stopVoiceAdventure()
            } finally {
                _voiceState.value = LiveVoiceState.Idle
            }
        }
    }

    fun restartStory() {
        viewModelScope.launch {
            try {
                repository.deleteChat(storyId)
                _sceneArtState.value = SceneArtState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Could not reset story chat."
            }
        }
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        kotlinx.coroutines.MainScope().launch {
            liveVoiceManager.stopVoiceAdventure()
        }
    }

    private fun buildOpeningMessage(story: Story, player: String): ChatMessage {
        val opening = story.openingLine.ifBlank {
            "Welcome, $player! Tumhari adventure '${story.title}' ab shuru hoti hai. Saamne ek naya raasta aur ek rahasya khada hai. Tum pehla kadam kya uthaoge?"
        }
        return ChatMessage(
            id = "opening_${story.id}",
            role = "model",
            content = opening,
            timestampMs = System.currentTimeMillis(),
            options = listOf(
                "Aas-paas ka ilaka carefully explore karo",
                "Saamne wale se seedha sawaal pucho",
                "Koi surakshit jagah ya clue dhoondo"
            ),
            imagePrompt = "Opening atmospheric scene of ${story.title}: ${story.description}",
        )
    }

    companion object {
        private const val TAG = "ChatGameViewModel"

        fun provideFactory(
            currentUserId: String,
            storyId: String,
            fallbackPlayerName: String,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                ChatGameViewModel(
                    appContext = app.applicationContext,
                    repository = StoryRepository(db),
                    aiService = StoryAiService(),
                    storageService = SceneStorageService(),
                    liveVoiceManager = LiveVoiceManager(),
                    currentUserId = currentUserId,
                    storyId = storyId,
                    fallbackPlayerName = fallbackPlayerName,
                )
            }
        }
    }
}
