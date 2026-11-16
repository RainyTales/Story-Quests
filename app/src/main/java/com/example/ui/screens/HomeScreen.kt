package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.Story
import com.example.data.model.StoryChat
import com.example.ui.components.StoryCoverImage
import com.example.ui.theme.ArcaneGold
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MysticViolet
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBackground
import com.example.ui.theme.VoidBorder
import com.example.ui.theme.VoidSurface
import com.example.ui.theme.VoidSurfaceElevated
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    defaultDisplayName: String?,
    onOpenStory: (storyId: String, playerName: String) -> Unit,
    onOpenAdmin: () -> Unit,
    onSignOut: () -> Unit,
) {
    val storiesState by viewModel.storiesState.collectAsStateWithLifecycle()
    val profileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val userChats by viewModel.userChatsState.collectAsStateWithLifecycle()
    val isSavingName by viewModel.isSavingName.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()

    val savedName = (profileState as? UiState.Success)?.data?.name.orEmpty()
    val profileLoaded = profileState is UiState.Success || profileState is UiState.Error
    val needsNamePrompt = profileLoaded && savedName.isBlank()
    var showEditNameDialog by remember { mutableStateOf(false) }

    val effectivePlayerName = savedName.ifBlank {
        defaultDisplayName?.substringBefore(" ")?.takeIf { it.isNotBlank() } ?: "Adventurer"
    }

    Scaffold(
        containerColor = VoidBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VoidBackground.copy(alpha = 0.96f),
                    titleContentColor = TextPrimary,
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_storyquest_logo),
                            contentDescription = "StoryQuest Logo",
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Column {
                            Text(
                                text = "STORYQUEST",
                                style = MaterialTheme.typography.titleLarge,
                                color = ArcaneGold,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Player: $effectivePlayerName",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan,
                                modifier = Modifier
                                    .clickable { showEditNameDialog = true }
                                    .testTag("edit_player_name_trigger"),
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showEditNameDialog = true },
                        modifier = Modifier.testTag("change_name_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Change player name",
                            tint = TextSecondary,
                        )
                    }
                    if (viewModel.isAdminUser) {
                        TextButton(
                            onClick = onOpenAdmin,
                            modifier = Modifier.testTag("admin_nav_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = "Open Admin Studio",
                                tint = MysticViolet,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Admin",
                                color = MysticViolet,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onOpenAdmin,
                            modifier = Modifier.testTag("admin_nav_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = "Open Admin Studio",
                                tint = TextSecondary,
                            )
                        }
                    }
                    IconButton(
                        onClick = onSignOut,
                        modifier = Modifier.testTag("sign_out_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sign out",
                            tint = TextSecondary,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val isWide = maxWidth >= 680.dp

            when (val state = storiesState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ArcaneGold)
                    }
                }
                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                is UiState.Success -> {
                    val stories = state.data
                    val featured = stories.firstOrNull()
                    val activeChats = stories.filter { userChats.containsKey(it.id) }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        if (featured != null) {
                            item(key = "hero_${featured.id}") {
                                FeaturedHeroStoryCard(
                                    story = featured,
                                    chat = userChats[featured.id],
                                    onPlay = { onOpenStory(featured.id, effectivePlayerName) },
                                )
                            }
                        }

                        if (activeChats.isNotEmpty()) {
                            item(key = "continue_section") {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    SectionHeader(
                                        title = "Continue Playing",
                                        subtitle = "Separate cloud saves per world",
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        items(activeChats, key = { "cont_${it.id}" }) { story ->
                                            NetflixStoryPosterCard(
                                                story = story,
                                                chat = userChats[story.id],
                                                onClick = { onOpenStory(story.id, effectivePlayerName) },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "all_stories_header") {
                            SectionHeader(
                                title = "Adventure Worlds",
                                subtitle = "Tap any story to begin or resume",
                            )
                        }

                        if (isWide) {
                            val rows = stories.chunked(2)
                            items(rows) { pair ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    pair.forEach { story ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            StoryCatalogCard(
                                                story = story,
                                                chat = userChats[story.id],
                                                onClick = { onOpenStory(story.id, effectivePlayerName) },
                                            )
                                        }
                                    }
                                    if (pair.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        } else {
                            items(stories, key = { it.id }) { story ->
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    StoryCatalogCard(
                                        story = story,
                                        chat = userChats[story.id],
                                        onClick = { onOpenStory(story.id, effectivePlayerName) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (needsNamePrompt || showEditNameDialog) {
        PlayerNameDialog(
            initialName = savedName.ifBlank {
                defaultDisplayName?.substringBefore(" ").orEmpty()
            },
            isSaving = isSavingName,
            errorMessage = actionError,
            canDismiss = !needsNamePrompt,
            onDismiss = {
                viewModel.clearError()
                showEditNameDialog = false
            },
            onSave = { chosenName ->
                viewModel.savePlayerName(chosenName) {
                    showEditNameDialog = false
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
    }
}

@Composable
private fun FeaturedHeroStoryCard(
    story: Story,
    chat: StoryChat?,
    onPlay: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(330.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, VoidBorder, RoundedCornerShape(24.dp))
            .clickable(onClick = onPlay)
            .testTag("featured_story_card_${story.id}"),
    ) {
        StoryCoverImage(
            coverImage = story.coverImage,
            contentDescription = story.title,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            VoidBackground.copy(alpha = 0.55f),
                            VoidBackground.copy(alpha = 0.96f),
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = ArcaneGold.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        text = story.genre.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = ArcaneGold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                if (chat != null) {
                    Surface(
                        color = SuccessEmerald.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            text = "${chat.messageCount} MSGS SAVED",
                            style = MaterialTheme.typography.labelSmall,
                            color = SuccessEmerald,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Text(
                text = story.title,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = story.description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Button(
                onClick = onPlay,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ArcaneGold,
                    contentColor = Color(0xFF1A1100),
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .padding(top = 4.dp)
                    .height(48.dp)
                    .testTag("featured_play_button"),
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (chat != null) "Resume Adventure" else "Start Quest",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun NetflixStoryPosterCard(
    story: Story,
    chat: StoryChat?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(156.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(VoidSurface)
            .border(1.dp, VoidBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("poster_story_card_${story.id}"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f),
        ) {
            StoryCoverImage(
                coverImage = story.coverImage,
                contentDescription = story.title,
                modifier = Modifier.fillMaxSize(),
            )
            if (chat != null) {
                Surface(
                    color = VoidBackground.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                ) {
                    Text(
                        text = "${chat.messageCount} msgs",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = story.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = story.genre,
                style = MaterialTheme.typography.labelSmall,
                color = ArcaneGold,
            )
        }
    }
}

@Composable
private fun StoryCatalogCard(
    story: Story,
    chat: StoryChat?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(VoidSurface)
            .border(1.dp, VoidBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
            .testTag("story_card_${story.id}"),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StoryCoverImage(
            coverImage = story.coverImage,
            contentDescription = story.title,
            modifier = Modifier
                .width(96.dp)
                .height(128.dp)
                .clip(RoundedCornerShape(14.dp)),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = story.genre.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan,
                )
                if (chat != null) {
                    Text(
                        text = "• ${chat.messageCount} msgs",
                        style = MaterialTheme.typography.labelSmall,
                        color = SuccessEmerald,
                    )
                }
            }

            Text(
                text = story.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = story.description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(ArcaneGold),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFF1A1100),
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = if (chat != null) "Continue Story" else "Play Story",
                    style = MaterialTheme.typography.labelLarge,
                    color = ArcaneGold,
                )
            }
        }
    }
}

@Composable
private fun PlayerNameDialog(
    initialName: String,
    isSaving: Boolean,
    errorMessage: String?,
    canDismiss: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var nameInput by remember(initialName) { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = { if (canDismiss && !isSaving) onDismiss() },
        containerColor = VoidSurfaceElevated,
        icon = {
            Icon(
                imageVector = Icons.Default.SportsEsports,
                contentDescription = null,
                tint = ArcaneGold,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                text = "Apna Player Name Likho",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "AI Storyteller har scene me aapko isi naam se bulayega (e.g. Vansh, Aryan).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { if (it.length <= 40) nameInput = it },
                    label = { Text("Player Name") },
                    placeholder = { Text("Vansh") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("player_name_input"),
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(nameInput) },
                enabled = !isSaving && nameInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ArcaneGold,
                    contentColor = Color(0xFF1A1100),
                ),
                modifier = Modifier.testTag("save_player_name_button"),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color(0xFF1A1100),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Save & Play", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = if (canDismiss) {
            {
                TextButton(onClick = onDismiss, enabled = !isSaving) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        } else null,
    )
}
