package com.example.ui.components

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.ImageRequest
import coil.request.Options
import com.example.R
import com.example.ui.theme.ArcaneGold
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricCyanContainer
import com.example.ui.theme.MysticViolet
import com.example.ui.theme.MysticVioletContainer
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBackground
import com.example.ui.theme.VoidBorder
import com.example.ui.theme.VoidSurface
import com.example.ui.theme.VoidSurfaceElevated
import com.google.firebase.Firebase
import com.google.firebase.storage.storage
import kotlinx.coroutines.tasks.await
import okio.Buffer
import java.io.File

/**
 * Custom Coil Fetcher that resolves `gs://` Firebase Storage URIs directly via Firebase Storage SDK
 * and feeds the image bytes into Coil's memory/disk cache.
 */
class FirebaseStorageUriFetcher(
    private val uri: Uri,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val storageRef = Firebase.storage.getReferenceFromUrl(uri.toString())
        val bytes = storageRef.getBytes(MAX_DOWNLOAD_BYTES).await()
        return SourceResult(
            source = ImageSource(
                source = Buffer().write(bytes),
                context = options.context,
            ),
            mimeType = "image/jpeg",
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            return if (data.scheme == "gs") {
                FirebaseStorageUriFetcher(data, options)
            } else {
                null
            }
        }
    }

    private companion object {
        const val MAX_DOWNLOAD_BYTES = 10L * 1024L * 1024L
    }
}

object StoryImageLoaderProvider {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        return instance ?: synchronized(this) {
            instance ?: ImageLoader.Builder(context.applicationContext)
                .components {
                    add(FirebaseStorageUriFetcher.Factory())
                }
                .crossfade(true)
                .build()
                .also { instance = it }
        }
    }
}

@Composable
fun rememberStoryCoilImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember(context) { StoryImageLoaderProvider.get(context) }
}

@Composable
fun StoryCoverImage(
    coverImage: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val imageLoader = rememberStoryCoilImageLoader()
    val normalized = coverImage.trim()
    val presetDrawable = when (normalized) {
        "preset:mystic_realm" -> R.drawable.img_cover_mystic_realm
        "preset:zombie_city" -> R.drawable.img_cover_zombie_city
        "preset:space_mars" -> R.drawable.img_cover_space_mars
        else -> null
    }

    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                listOf(VoidSurfaceElevated, MysticVioletContainer, ElectricCyanContainer)
            )
        )
    ) {
        when {
            presetDrawable != null -> {
                Image(
                    painter = painterResource(id = presetDrawable),
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                )
            }
            normalized.startsWith("http://") ||
                normalized.startsWith("https://") ||
                normalized.startsWith("gs://") -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(normalized)
                        .crossfade(true)
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                    error = painterResource(id = R.drawable.img_cover_mystic_realm),
                )
            }
            normalized.startsWith("file://") -> {
                val file = File(normalized.removePrefix("file://"))
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(file)
                        .crossfade(true)
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                    error = painterResource(id = R.drawable.img_cover_mystic_realm),
                )
            }
            else -> {
                Image(
                    painter = painterResource(id = R.drawable.img_cover_mystic_realm),
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                )
            }
        }
    }
}

/**
 * Dynamically loads an AI-generated story segment illustration from Firebase Storage
 * (`https://` download URL, `gs://` Storage URI, or local `file://` cache) via Coil.
 */
@Composable
fun StorySceneCoilBanner(
    sceneImageUrl: String,
    fallbackCoverImage: String,
    sceneCaption: String,
    isIllustrating: Boolean,
    onIllustrateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageLoader = rememberStoryCoilImageLoader()
    val cleanUrl = sceneImageUrl.trim()
    val isCloud = cleanUrl.startsWith("https://") || cleanUrl.startsWith("gs://")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(164.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(VoidSurface)
            .border(1.dp, VoidBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onIllustrateClick),
    ) {
        if (cleanUrl.isNotBlank()) {
            val requestData: Any = if (cleanUrl.startsWith("file://")) {
                File(cleanUrl.removePrefix("file://"))
            } else {
                cleanUrl
            }
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(requestData)
                    .crossfade(true)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = "Current story segment illustration",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(VoidSurfaceElevated),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                color = ArcaneGold,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                            )
                            Text(
                                text = "Loading scene via Coil...",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                        }
                    }
                },
                error = {
                    StoryCoverImage(
                        coverImage = fallbackCoverImage,
                        contentDescription = sceneCaption,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
            )
        } else {
            StoryCoverImage(
                coverImage = fallbackCoverImage,
                contentDescription = sceneCaption,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Cinematic bottom vignette overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            VoidBackground.copy(alpha = 0.20f),
                            Color.Transparent,
                            VoidBackground.copy(alpha = 0.88f),
                        )
                    )
                )
        )

        // Top-left status badge
        Surface(
            color = VoidBackground.copy(alpha = 0.78f),
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = when {
                        isIllustrating -> Icons.Default.AutoAwesome
                        isCloud -> Icons.Default.CloudDone
                        else -> Icons.Default.Image
                    },
                    contentDescription = null,
                    tint = when {
                        isIllustrating -> ArcaneGold
                        isCloud -> SuccessEmerald
                        else -> MysticViolet
                    },
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = when {
                        isIllustrating -> "Painting & Uploading Scene..."
                        isCloud -> "Firebase Storage • Live Scene"
                        cleanUrl.isNotBlank() -> "Local Scene Cache"
                        else -> "Tap to Illustrate Scene"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // Bottom caption
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = sceneCaption,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (isIllustrating) {
                CircularProgressIndicator(
                    color = ArcaneGold,
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

/**
 * Inline Coil image rendered inside an AI story chat bubble whenever a segment has an illustration.
 */
@Composable
fun InlineSegmentCoilImage(
    sceneImageUrl: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageLoader = rememberStoryCoilImageLoader()
    val cleanUrl = sceneImageUrl.trim()
    if (cleanUrl.isBlank()) return

    val requestData: Any = if (cleanUrl.startsWith("file://")) {
        File(cleanUrl.removePrefix("file://"))
    } else {
        cleanUrl
    }

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(requestData)
            .crossfade(true)
            .build(),
        imageLoader = imageLoader,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, VoidBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(VoidSurfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = MysticViolet,
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                )
            }
        },
        error = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(VoidSurfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Scene image unavailable",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan,
                )
            }
        },
    )
}
