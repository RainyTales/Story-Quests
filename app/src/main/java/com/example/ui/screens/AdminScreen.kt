package com.example.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Story
import com.example.data.model.StoryChat
import com.example.data.model.UserProfile
import com.example.data.repository.StoryRepository
import com.example.ui.components.StoryCoverImage
import com.example.ui.theme.ArcaneGold
import com.example.ui.theme.ElectricCyan
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
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.CoverGenState
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    viewModel: AdminViewModel,
    isAdminUser: Boolean,
    currentUserEmail: String?,
    onBack: () -> Unit,
) {
    val storiesState by viewModel.storiesState.collectAsStateWithLifecycle()
    val usersState by viewModel.usersState.collectAsStateWithLifecycle()
    val chatsState by viewModel.chatsState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val coverGenState by viewModel.coverGenState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }
    var editingStory by remember { mutableStateOf<Story?>(null) }
    var showStoryEditor by remember { mutableStateOf(false) }
    var inspectingChat by remember { mutableStateOf<StoryChat?>(null) }
    var selectedUserFilter by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        containerColor = VoidBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VoidSurface,
                    titleContentColor = TextPrimary,
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("admin_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary,
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "ADMIN STUDIO (/admin)",
                            style = MaterialTheme.typography.titleLarge,
                            color = MysticViolet,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = currentUserEmail ?: "Authenticated Account",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                    }
                },
                actions = {
                    if (isAdminUser) {
                        TextButton(onClick = { viewModel.seedStarterStories() }) {
                            Text("Seed Defaults", color = ArcaneGold)
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (isAdminUser && selectedTab == 0) {
                FloatingActionButton(
                    onClick = {
                        editingStory = null
                        viewModel.resetCoverGen()
                        showStoryEditor = true
                    },
                    containerColor = ArcaneGold,
                    contentColor = Color(0xFF1A1100),
                    modifier = Modifier.testTag("admin_add_story_fab"),
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Story")
                }
            }
        },
    ) { innerPadding ->
        if (!isAdminUser) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(VoidSurface)
                        .border(1.dp, VoidBorder, RoundedCornerShape(20.dp))
                        .padding(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MysticViolet,
                        modifier = Modifier.size(44.dp),
                    )
                    Text(
                        text = "Admin Access Restricted",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                    )
                    Text(
                        text = "Sign in with the project admin account (${StoryRepository.ADMIN_EMAIL}) to manage stories, inspect users, and view player chats.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(containerColor = ArcaneGold),
                    ) {
                        Text("Return to Stories", color = Color(0xFF1A1100))
                    }
                }
            }
            return@Scaffold
        }

        val storiesList = (storiesState as? UiState.Success)?.data.orEmpty()
        val usersList = (usersState as? UiState.Success)?.data.orEmpty()
        val chatsList = (chatsState as? UiState.Success)?.data.orEmpty()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Dashboard Stats Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AdminStatCard(
                    label = "Total Users",
                    value = usersList.size.toString(),
                    icon = Icons.Default.Group,
                    accent = ElectricCyan,
                    modifier = Modifier.weight(1f),
                )
                AdminStatCard(
                    label = "Total Stories",
                    value = storiesList.size.toString(),
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    accent = ArcaneGold,
                    modifier = Modifier.weight(1f),
                )
                AdminStatCard(
                    label = "Total Chats",
                    value = chatsList.size.toString(),
                    icon = Icons.AutoMirrored.Filled.Chat,
                    accent = MysticViolet,
                    modifier = Modifier.weight(1f),
                )
            }

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = VoidSurface,
                contentColor = ArcaneGold,
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Stories (${storiesList.size})") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Users (${usersList.size})") },
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Chats (${chatsList.size})") },
                )
            }

            when (selectedTab) {
                0 -> AdminStoriesTab(
                    storiesState = storiesState,
                    onEdit = {
                        editingStory = it
                        viewModel.resetCoverGen()
                        showStoryEditor = true
                    },
                    onToggleActive = { viewModel.toggleStoryActive(it) },
                    onDelete = { viewModel.deleteStory(it.id) },
                )
                1 -> AdminUsersTab(
                    usersState = usersState,
                    chats = chatsList,
                    onViewUserChats = { uid ->
                        selectedUserFilter = uid
                        selectedTab = 2
                    },
                )
                2 -> AdminChatsTab(
                    chatsState = chatsState,
                    users = usersList,
                    selectedUserId = selectedUserFilter,
                    onClearFilter = { selectedUserFilter = null },
                    onInspectChat = { inspectingChat = it },
                )
            }
        }
    }

    if (showStoryEditor) {
        StoryEditorDialog(
            existing = editingStory,
            isBusy = isBusy,
            coverGenState = coverGenState,
            onGenerateCover = { ctx, prompt -> viewModel.generateCoverArt(ctx, prompt) },
            onEditCover = { ctx, instr -> viewModel.editCoverArt(ctx, instr) },
            onAutoFillPrompt = { title, genre, desc ->
                viewModel.buildSystemPromptFromDescription(title, genre, desc)
            },
            onDismiss = { showStoryEditor = false },
            onSave = { title, desc, sysPrompt, cover, genre, opening, active ->
                viewModel.saveStory(
                    existingId = editingStory?.id,
                    title = title,
                    description = desc,
                    systemPrompt = sysPrompt,
                    coverImage = cover,
                    genre = genre,
                    openingLine = opening,
                    isActive = active,
                    onComplete = { showStoryEditor = false },
                )
            },
        )
    }

    inspectingChat?.let { chat ->
        AdminChatInspectorSheet(
            chat = chat,
            userName = (usersState as? UiState.Success)?.data
                ?.find { it.userId == chat.userId }?.name ?: chat.userId,
            onDismiss = { inspectingChat = null },
        )
    }
}

@Composable
private fun AdminStatCard(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(VoidSurface)
            .border(1.dp, VoidBorder, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
    }
}

@Composable
private fun AdminStoriesTab(
    storiesState: UiState<List<Story>>,
    onEdit: (Story) -> Unit,
    onToggleActive: (Story) -> Unit,
    onDelete: (Story) -> Unit,
) {
    when (storiesState) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = ArcaneGold)
        }
        is UiState.Error -> Box(Modifier.fillMaxSize().padding(20.dp), Alignment.Center) {
            Text(storiesState.message, color = ErrorCrimson)
        }
        is UiState.Success -> {
            val stories = storiesState.data
            LazyColumn(
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(stories, key = { it.id }) { story ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(VoidSurface)
                            .border(1.dp, VoidBorder, RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StoryCoverImage(
                            coverImage = story.coverImage,
                            contentDescription = story.title,
                            modifier = Modifier
                                .size(64.dp, 84.dp)
                                .clip(RoundedCornerShape(10.dp)),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = story.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = story.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (story.isActive) "LIVE" else "HIDDEN",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (story.isActive) SuccessEmerald else TextMuted,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Switch(
                                checked = story.isActive,
                                onCheckedChange = { onToggleActive(story) },
                            )
                            Row {
                                IconButton(onClick = { onEdit(story) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit story", tint = ElectricCyan)
                                }
                                IconButton(onClick = { onDelete(story) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete story", tint = ErrorCrimson)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminUsersTab(
    usersState: UiState<List<UserProfile>>,
    chats: List<StoryChat>,
    onViewUserChats: (String) -> Unit,
) {
    when (usersState) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = ElectricCyan)
        }
        is UiState.Error -> Box(Modifier.fillMaxSize().padding(20.dp), Alignment.Center) {
            Text(usersState.message, color = ErrorCrimson)
        }
        is UiState.Success -> {
            val users = usersState.data
            if (users.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("No players registered yet.", color = TextSecondary)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(users, key = { it.userId }) { user ->
                        val userChatCount = chats.count { it.userId == user.userId }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(VoidSurface)
                                .border(1.dp, VoidBorder, RoundedCornerShape(16.dp))
                                .clickable { onViewUserChats(user.userId) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(user.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                if (!user.email.isNullOrBlank()) {
                                    Text(user.email, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                }
                                Text(
                                    text = "$userChatCount active stories",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan,
                                )
                            }
                            TextButton(onClick = { onViewUserChats(user.userId) }) {
                                Text("View Chats", color = ArcaneGold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminChatsTab(
    chatsState: UiState<List<StoryChat>>,
    users: List<UserProfile>,
    selectedUserId: String?,
    onClearFilter: () -> Unit,
    onInspectChat: (StoryChat) -> Unit,
) {
    when (chatsState) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = MysticViolet)
        }
        is UiState.Error -> Box(Modifier.fillMaxSize().padding(20.dp), Alignment.Center) {
            Text(chatsState.message, color = ErrorCrimson)
        }
        is UiState.Success -> {
            val filtered = chatsState.data.filter { selectedUserId == null || it.userId == selectedUserId }
            Column(modifier = Modifier.fillMaxSize()) {
                if (selectedUserId != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val userName = users.find { it.userId == selectedUserId }?.name ?: selectedUserId
                        Text("Filtered by player: $userName", color = ElectricCyan, style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = onClearFilter) {
                            Text("Show All", color = ArcaneGold)
                        }
                    }
                }
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filtered, key = { it.id }) { chat ->
                        val userName = users.find { it.userId == chat.userId }?.name ?: chat.userId.take(8)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(VoidSurface)
                                .border(1.dp, VoidBorder, RoundedCornerShape(16.dp))
                                .clickable { onInspectChat(chat) }
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(chat.storyTitle.ifBlank { chat.storyId }, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text("${chat.messageCount} msgs", style = MaterialTheme.typography.labelSmall, color = ArcaneGold)
                            }
                            Text("Player: $userName", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                            val lastPreview = chat.messages.lastOrNull()?.content.orEmpty()
                            if (lastPreview.isNotBlank()) {
                                Text(
                                    text = lastPreview,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryEditorDialog(
    existing: Story?,
    isBusy: Boolean,
    coverGenState: CoverGenState,
    onGenerateCover: (android.content.Context, String) -> Unit,
    onEditCover: (android.content.Context, String) -> Unit,
    onAutoFillPrompt: (title: String, genre: String, description: String) -> String,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, Boolean) -> Unit,
) {
    val context = LocalContext.current
    var title by remember(existing) { mutableStateOf(existing?.title.orEmpty()) }
    var genre by remember(existing) { mutableStateOf(existing?.genre ?: "Adventure") }
    var description by remember(existing) { mutableStateOf(existing?.description.orEmpty()) }
    var systemPrompt by remember(existing) {
        mutableStateOf(
            existing?.systemPrompt
                ?: "You are an interactive adventure storyteller. Pure adventure and mystery only, strictly NO romance. Always give 3 options A/B/C at the end. Call the player by their name and respond naturally to custom Hinglish/English messages."
        )
    }
    var coverImage by remember(existing) { mutableStateOf(existing?.coverImage ?: "preset:mystic_realm") }
    var openingLine by remember(existing) { mutableStateOf(existing?.openingLine.orEmpty()) }
    var isActive by remember(existing) { mutableStateOf(existing?.isActive ?: true) }
    var coverPrompt by remember { mutableStateOf("") }
    var coverEditInstruction by remember { mutableStateOf("") }

    LaunchedEffect(coverGenState) {
        if (coverGenState is CoverGenState.Ready) {
            coverImage = coverGenState.fileUri
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        containerColor = VoidSurfaceElevated,
        title = {
            Text(
                text = if (existing == null) "Add New Story" else "Edit Story",
                color = TextPrimary,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("admin_story_title_input"),
                )
                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text("Genre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Story Description") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().testTag("admin_story_description_input"),
                )
                TextButton(
                    onClick = {
                        systemPrompt = onAutoFillPrompt(title, genre, description)
                    },
                    enabled = description.isNotBlank(),
                    modifier = Modifier.testTag("admin_autofill_prompt_button"),
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Craft AI Prompt from Description", color = ElectricCyan)
                }
                OutlinedTextField(
                    value = openingLine,
                    onValueChange = { openingLine = it },
                    label = { Text("Opening Scene (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("System Prompt (AI Game Master Rules)") },
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Cover Image Preset / URL / AI Art", style = MaterialTheme.typography.labelLarge, color = ArcaneGold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val presets = listOf(
                        "preset:mystic_realm" to "Mystic",
                        "preset:zombie_city" to "Zombie",
                        "preset:space_mars" to "Mars",
                    )
                    items(presets) { (key, label) ->
                        FilterChip(
                            selected = coverImage == key,
                            onClick = { coverImage = key },
                            label = { Text(label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = coverImage,
                    onValueChange = { coverImage = it },
                    label = { Text("Cover Image URL or Preset") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // AI Cover Art Creator & Editor
                OutlinedTextField(
                    value = coverPrompt,
                    onValueChange = { coverPrompt = it },
                    label = { Text("AI Cover Prompt (Nano Banana 2.1)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = { onGenerateCover(context, coverPrompt.ifBlank { title }) },
                    enabled = coverGenState !is CoverGenState.Loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MysticViolet)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (coverGenState is CoverGenState.Loading) "Generating Cover..." else "Generate AI Cover",
                        color = MysticViolet,
                    )
                }

                if (coverGenState is CoverGenState.Ready) {
                    Image(
                        bitmap = coverGenState.bitmap.asImageBitmap(),
                        contentDescription = "Generated cover preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = coverEditInstruction,
                            onValueChange = { coverEditInstruction = it },
                            label = { Text("Edit cover") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                onEditCover(context, coverEditInstruction)
                                coverEditInstruction = ""
                            },
                            enabled = coverEditInstruction.isNotBlank(),
                        ) {
                            Text("Remix", color = ElectricCyan)
                        }
                    }
                } else if (coverGenState is CoverGenState.Error) {
                    Text(coverGenState.message, color = ErrorCrimson, style = MaterialTheme.typography.bodyMedium)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Visible to Players", color = TextPrimary)
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, description, systemPrompt, coverImage, genre, openingLine, isActive) },
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = ArcaneGold),
            ) {
                Text(if (isBusy) "Saving..." else "Save Story", color = Color(0xFF1A1100), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text("Cancel", color = TextSecondary)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminChatInspectorSheet(
    chat: StoryChat,
    userName: String,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = VoidSurfaceElevated,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "${chat.storyTitle.ifBlank { chat.storyId }} — $userName",
                style = MaterialTheme.typography.titleLarge,
                color = ArcaneGold,
            )
            if (chat.summary.isNotBlank()) {
                Surface(color = VoidSurface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Summary: ${chat.summary}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ElectricCyan,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(chat.messages, key = { it.id }) { msg ->
                    val isUser = msg.role == "user"
                    Surface(
                        color = if (isUser) Color(0xFF0E443B) else VoidSurface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = if (isUser) userName else "AI Storyteller",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isUser) SuccessEmerald else ArcaneGold,
                            )
                            Text(msg.content, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        }
                    }
                }
            }
        }
    }
}
