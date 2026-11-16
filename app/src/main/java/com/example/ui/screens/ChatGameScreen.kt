package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ai.LiveVoiceState
import com.example.data.model.ChatMessage
import com.example.ui.components.InlineSegmentCoilImage
import com.example.ui.components.StoryCoverImage
import com.example.ui.components.StorySceneCoilBanner
import com.example.ui.theme.ArcaneGold
import com.example.ui.theme.ChatAiBorder
import com.example.ui.theme.ChatAiBubble
import com.example.ui.theme.ChatUserBorder
import com.example.ui.theme.ChatUserBubble
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricCyanContainer
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.MysticViolet
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBackground
import com.example.ui.theme.VoidBorder
import com.example.ui.theme.VoidSurface
import com.example.ui.theme.VoidSurfaceElevated
import com.example.ui.viewmodel.ChatGameUiData
import com.example.ui.viewmodel.ChatGameViewModel
import com.example.ui.viewmodel.SceneArtState
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatGameScreen(
    viewModel: ChatGameViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val chatUiState by viewModel.chatUiState.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val isSummarizing by viewModel.isSummarizing.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val sceneArtState by viewModel.sceneArtState.collectAsStateWithLifecycle()
    val illustratingMessageId by viewModel.illustratingMessageId.collectAsStateWithLifecycle()
    val autoIllustrateEnabled by viewModel.autoIllustrateEnabled.collectAsStateWithLifecycle()
    val storageNotice by viewModel.storageNotice.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val playerName by viewModel.playerName.collectAsStateWithLifecycle()

    var customInput by remember { mutableStateOf("") }
    var showSummaryDialog by remember { mutableStateOf(false) }
    var showArtSheet by remember { mutableStateOf(false) }
    var artPromptDraft by remember { mutableStateOf("") }
    var artTargetMessageId by remember { mutableStateOf<String?>(null) }
    var isSceneStageExpanded by remember { mutableStateOf(true) }
    var showRestartConfirm by remember { mutableStateOf(false) }
    var micPermissionError by remember { mutableStateOf<String?>(null) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            micPermissionError = null
            viewModel.startLiveVoice(context)
        } else {
            micPermissionError = "Microphone permission is needed for live voice story mode."
        }
    }

    val listState = rememberLazyListState()
    val uiData = (chatUiState as? UiState.Success)?.data

    LaunchedEffect(uiData?.messages?.size, isSending) {
        val count = uiData?.messages?.size ?: 0
        if (count > 0) {
            listState.animateScrollToItem(count - 1)
        }
    }

    Scaffold(
        containerColor = VoidBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VoidSurface,
                    titleContentColor = TextPrimary,
                ),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.stopLiveVoice()
                            onBack()
                        },
                        modifier = Modifier.testTag("chat_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to stories",
                            tint = TextPrimary,
                        )
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (uiData != null) {
                            StoryCoverImage(
                                coverImage = uiData.story.coverImage,
                                contentDescription = uiData.story.title,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape),
                            )
                        }
                        Column {
                            Text(
                                text = uiData?.story?.title ?: "Loading Story...",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (isSending) "AI Storyteller typing..." else "Playing as $playerName",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSending) ArcaneGold else ElectricCyan,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (voiceState is LiveVoiceState.Active || voiceState is LiveVoiceState.Connecting) {
                                viewModel.stopLiveVoice()
                            } else {
                                val hasMic = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasMic) {
                                    viewModel.startLiveVoice(context)
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        modifier = Modifier.testTag("live_voice_button"),
                    ) {
                        Icon(
                            imageVector = if (voiceState is LiveVoiceState.Active) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Live Voice Conversation",
                            tint = when (voiceState) {
                                is LiveVoiceState.Active -> SuccessEmerald
                                is LiveVoiceState.Connecting -> ArcaneGold
                                else -> ElectricCyan
                            },
                        )
                    }
                    IconButton(
                        onClick = {
                            artPromptDraft = uiData?.latestImagePrompt.orEmpty()
                            artTargetMessageId = uiData?.activeSegmentMessageId
                            showArtSheet = true
                            if (sceneArtState is SceneArtState.Idle && artPromptDraft.isNotBlank()) {
                                viewModel.generateSceneArt(artPromptDraft, artTargetMessageId)
                            }
                        },
                        modifier = Modifier.testTag("scene_art_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Create and Edit Scene Art",
                            tint = MysticViolet,
                        )
                    }
                    IconButton(
                        onClick = { showSummaryDialog = true },
                        modifier = Modifier.testTag("story_memory_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.HistoryEdu,
                            contentDescription = "Story Memory Summary",
                            tint = TextSecondary,
                        )
                    }
                    IconButton(
                        onClick = { showRestartConfirm = true },
                        modifier = Modifier.testTag("restart_story_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Restart Story",
                            tint = TextSecondary,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            // Live Voice HUD Banner
            AnimatedVisibility(visible = voiceState !is LiveVoiceState.Idle || micPermissionError != null) {
                LiveVoiceHudBanner(
                    voiceState = voiceState,
                    permissionError = micPermissionError,
                    onStop = {
                        micPermissionError = null
                        viewModel.stopLiveVoice()
                    },
                )
            }

            if (isSummarizing) {
                Surface(
                    color = MysticViolet.copy(alpha = 0.16f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Updating 20-message adventure memory summary...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MysticViolet,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }

            if (errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = errorMessage.orEmpty(),
                            color = ErrorCrimson,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.dismissError() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss error",
                                tint = ErrorCrimson,
                            )
                        }
                    }
                }
            }

            if (storageNotice != null) {
                Surface(
                    color = VoidSurfaceElevated,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = storageNotice.orEmpty(),
                            color = ElectricCyan,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.dismissStorageNotice() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss storage notice",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            when (val state = chatUiState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ArcaneGold)
                    }
                }
                is UiState.Error -> {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(text = state.message, color = ErrorCrimson)
                    }
                }
                is UiState.Success -> {
                    val data = state.data

                    // Collapsible Current Segment Scene Illustration Stage (Coil + Firebase Storage)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(VoidSurface.copy(alpha = 0.72f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.clickable { isSceneStageExpanded = !isSceneStageExpanded },
                            ) {
                                Icon(
                                    imageVector = if (isSceneStageExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Toggle scene illustration",
                                    tint = ArcaneGold,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "CURRENT SCENE STAGE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ArcaneGold,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            TextButton(
                                onClick = { viewModel.toggleAutoIllustrate() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            ) {
                                Text(
                                    text = if (autoIllustrateEnabled) "Auto Art: ON" else "Auto Art: OFF",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (autoIllustrateEnabled) SuccessEmerald else TextMuted,
                                )
                            }
                        }

                        AnimatedVisibility(visible = isSceneStageExpanded) {
                            StorySceneCoilBanner(
                                sceneImageUrl = data.activeSegmentImageUrl,
                                fallbackCoverImage = data.story.coverImage,
                                sceneCaption = data.latestImagePrompt,
                                isIllustrating = illustratingMessageId != null,
                                onIllustrateClick = {
                                    artPromptDraft = data.latestImagePrompt
                                    artTargetMessageId = data.activeSegmentMessageId
                                    showArtSheet = true
                                    if (data.activeSegmentImageUrl.isBlank()) {
                                        viewModel.generateSceneArt(artPromptDraft, artTargetMessageId)
                                    }
                                },
                                modifier = Modifier
                                    .padding(top = 4.dp, bottom = 4.dp)
                                    .testTag("current_scene_coil_banner"),
                            )
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(data.messages, key = { it.id }) { msg ->
                            WhatsAppStoryBubble(
                                message = msg,
                                playerName = playerName,
                                isIllustratingThis = illustratingMessageId == msg.id,
                                onVisualizeClick = if (msg.role == "model") {
                                    {
                                        artPromptDraft = msg.imagePrompt.ifBlank { msg.content.take(220) }
                                        artTargetMessageId = msg.id
                                        showArtSheet = true
                                        viewModel.generateSceneArt(artPromptDraft, msg.id)
                                    }
                                } else null,
                            )
                        }

                        if (isSending) {
                            item(key = "typing_indicator") {
                                TypingBubble()
                            }
                        }
                    }

                    // A/B/C Quick Options (Suggestions — not compulsory!)
                    if (!isSending && data.currentOptions.isNotEmpty()) {
                        OptionsSuggestionStrip(
                            options = data.currentOptions,
                            onOptionClick = { chosenText ->
                                viewModel.sendPlayerMessage(chosenText)
                            },
                        )
                    }

                    // Always-Visible Custom Message Input Bar ("Apna jawab likho...")
                    CustomReplyInputBar(
                        value = customInput,
                        onValueChange = { customInput = it },
                        enabled = !isSending,
                        onSend = {
                            val toSend = customInput
                            customInput = ""
                            viewModel.sendPlayerMessage(toSend)
                        },
                    )
                }
            }
        }
    }

    if (showSummaryDialog && uiData != null) {
        MemorySummaryDialog(
            data = uiData,
            onDismiss = { showSummaryDialog = false },
        )
    }

    if (showRestartConfirm) {
        AlertDialog(
            onDismissRequest = { showRestartConfirm = false },
            containerColor = VoidSurfaceElevated,
            title = { Text("Restart This Adventure?", color = TextPrimary) },
            text = {
                Text(
                    "Is story ki saari chat aur memory clear ho jayegi. Baaki stories safe rahengi.",
                    color = TextSecondary,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestartConfirm = false
                        viewModel.restartStory()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorCrimson),
                ) {
                    Text("Restart", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
        )
    }

    if (showArtSheet) {
        SceneArtBottomSheet(
            sceneArtState = sceneArtState,
            initialPrompt = artPromptDraft,
            onGenerate = { prompt -> viewModel.generateSceneArt(prompt, artTargetMessageId) },
            onEdit = { editInstruction -> viewModel.editSceneArt(editInstruction, artTargetMessageId) },
            onDismiss = { showArtSheet = false },
        )
    }
}

@Composable
private fun WhatsAppStoryBubble(
    message: ChatMessage,
    playerName: String,
    isIllustratingThis: Boolean,
    onVisualizeClick: (() -> Unit)?,
) {
    val isUser = message.role == "user"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 330.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp,
                    )
                )
                .background(if (isUser) ChatUserBubble else ChatAiBubble)
                .border(
                    width = 1.dp,
                    color = if (isUser) ChatUserBorder else ChatAiBorder,
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp,
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = if (isUser) playerName else "GAME MASTER",
                style = MaterialTheme.typography.labelSmall,
                color = if (isUser) SuccessEmerald else ArcaneGold,
                fontWeight = FontWeight.Bold,
            )
            if (!isUser && message.sceneImageUrl.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                InlineSegmentCoilImage(
                    sceneImageUrl = message.sceneImageUrl,
                    contentDescription = message.imagePrompt.ifBlank { "Story segment illustration" },
                    onClick = { onVisualizeClick?.invoke() },
                    modifier = Modifier.testTag("segment_coil_image_${message.id}"),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
            )
            if (onVisualizeClick != null) {
                Row(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onVisualizeClick)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (isIllustratingThis) {
                        CircularProgressIndicator(
                            color = MysticViolet,
                            modifier = Modifier.size(13.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MysticViolet,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Text(
                        text = when {
                            isIllustratingThis -> "Painting & saving scene..."
                            message.sceneImageUrl.isNotBlank() -> "Re-paint Scene"
                            else -> "Illustrate Scene"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MysticViolet,
                    )
                }
            }
        }
    }
}

@Composable
private fun TypingBubble() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(ChatAiBubble)
            .border(1.dp, ChatAiBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = ArcaneGold,
            strokeWidth = 2.dp,
        )
        Text(
            text = "Storyteller agla scene likh raha hai...",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
    }
}

@Composable
private fun OptionsSuggestionStrip(
    options: List<String>,
    onOptionClick: (String) -> Unit,
) {
    val letters = listOf("A", "B", "C")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VoidSurface.copy(alpha = 0.88f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "SUGGESTIONS (OR TYPE YOUR OWN BELOW)",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
        )
        options.take(3).forEachIndexed { index, option ->
            val letter = letters.getOrElse(index) { "${index + 1}" }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(VoidSurfaceElevated)
                    .border(1.dp, VoidBorder, RoundedCornerShape(12.dp))
                    .clickable { onOptionClick("$letter) $option") }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("story_option_$letter"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(ArcaneGold.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = letter,
                        style = MaterialTheme.typography.labelLarge,
                        color = ArcaneGold,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = option,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CustomReplyInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onSend: () -> Unit,
) {
    Surface(
        color = VoidSurface,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                placeholder = {
                    Text(
                        text = "Apna jawab likho... (Hinglish / English)",
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = VoidSurfaceElevated,
                    unfocusedContainerColor = VoidSurfaceElevated,
                    focusedBorderColor = ArcaneGold,
                    unfocusedBorderColor = VoidBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("custom_story_input"),
            )

            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(if (enabled && value.isNotBlank()) ArcaneGold else VoidSurfaceElevated)
                    .clickable(enabled = enabled && value.isNotBlank(), onClick = onSend)
                    .testTag("send_story_button"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send custom message",
                    tint = if (enabled && value.isNotBlank()) Color(0xFF1A1100) else TextMuted,
                )
            }
        }
    }
}

@Composable
private fun LiveVoiceHudBanner(
    voiceState: LiveVoiceState,
    permissionError: String?,
    onStop: () -> Unit,
) {
    val bgColor = when (voiceState) {
        is LiveVoiceState.Error -> MaterialTheme.colorScheme.errorContainer
        else -> ElectricCyanContainer
    }
    val textColor = when (voiceState) {
        is LiveVoiceState.Error -> ErrorCrimson
        else -> ElectricCyan
    }
    val statusText = when {
        permissionError != null -> permissionError
        voiceState is LiveVoiceState.Connecting -> "Connecting Gemini 3.8 Live Voice..."
        voiceState is LiveVoiceState.Active -> "Live Voice Active — Speak naturally in Hinglish or English!"
        voiceState is LiveVoiceState.Error -> voiceState.message
        else -> ""
    }

    Surface(color = bgColor, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                tint = textColor,
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onStop) {
                Text("Stop", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MemorySummaryDialog(
    data: ChatGameUiData,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VoidSurfaceElevated,
        title = {
            Text("Story Memory Window", color = TextPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Total Messages: ${data.totalMessageCount} • Next auto-summary at ${((data.totalMessageCount / 20L) + 1L) * 20L}",
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricCyan,
                )
                Text(
                    text = "AI Context: System Prompt + Player Name + Summary + Last 10 messages only.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Surface(
                    color = VoidSurface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = data.summary.ifBlank {
                            "No 20-message summary generated yet. Keep playing — StoryQuest will auto-summarize at 20 messages!"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = ArcaneGold)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SceneArtBottomSheet(
    sceneArtState: SceneArtState,
    initialPrompt: String,
    onGenerate: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var promptText by remember(initialPrompt) { mutableStateOf(initialPrompt) }
    var editText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = VoidSurfaceElevated,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Scene Art Studio (Nano Banana 2.1)",
                style = MaterialTheme.typography.titleLarge,
                color = MysticViolet,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(VoidSurface)
                    .border(1.dp, VoidBorder, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                when (sceneArtState) {
                    is SceneArtState.Idle -> {
                        Text(
                            text = "Enter a prompt below to illustrate the current scene.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    is SceneArtState.Loading -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MysticViolet)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Painting scene with gemini-nano-banana-2.1...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                        }
                    }
                    is SceneArtState.Ready -> {
                        Image(
                            bitmap = sceneArtState.bitmap.asImageBitmap(),
                            contentDescription = "Generated scene artwork",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        if (sceneArtState.storageUrl.isNotBlank()) {
                            Surface(
                                color = VoidBackground.copy(alpha = 0.82f),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp),
                            ) {
                                Text(
                                    text = if (sceneArtState.isFirebaseStorage) {
                                        "Synced to Firebase Storage"
                                    } else {
                                        "Saved to Local Cache"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (sceneArtState.isFirebaseStorage) SuccessEmerald else ElectricCyan,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                    is SceneArtState.Error -> {
                        Text(
                            text = sceneArtState.message,
                            color = ErrorCrimson,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            OutlinedTextField(
                value = promptText,
                onValueChange = { promptText = it },
                label = { Text("Scene Prompt") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onGenerate(promptText) },
                    enabled = sceneArtState !is SceneArtState.Loading && promptText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MysticViolet),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Create Scene Art", color = Color(0xFF14062B), fontWeight = FontWeight.Bold)
                }
            }

            if (sceneArtState is SceneArtState.Ready) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        label = { Text("Edit this image (e.g., add purple storm)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick = {
                            val instruction = editText
                            editText = ""
                            onEdit(instruction)
                        },
                        enabled = editText.isNotBlank(),
                    ) {
                        Text("Edit", color = ElectricCyan)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
