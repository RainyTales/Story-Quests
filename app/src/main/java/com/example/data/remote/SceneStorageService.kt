package com.example.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.storage.storage
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class UploadedIllustration(
    val url: String,
    val isFirebaseStorage: Boolean,
    val note: String? = null,
)

class SceneStorageService {

    suspend fun uploadSceneIllustration(
        context: Context,
        userId: String,
        storyId: String,
        messageId: String,
        bitmap: Bitmap,
    ): UploadedIllustration {
        val jpegBytes = compressBitmapToJpeg(bitmap)
        val safeStoryId = sanitizePathSegment(storyId)
        val safeMessageId = sanitizePathSegment(messageId)
        val storagePath = "scene_illustrations/$userId/$safeStoryId/$safeMessageId.jpg"

        return uploadOrFallbackLocal(
            context = context,
            storagePath = storagePath,
            localSubDir = "scene_cache",
            localFileName = "${safeStoryId}_${safeMessageId}_${System.currentTimeMillis()}.jpg",
            jpegBytes = jpegBytes,
        )
    }

    suspend fun uploadStoryCover(
        context: Context,
        storyKey: String,
        bitmap: Bitmap,
    ): UploadedIllustration {
        val jpegBytes = compressBitmapToJpeg(bitmap)
        val safeKey = sanitizePathSegment(storyKey.ifBlank { "story" })
        val storagePath = "story_covers/${safeKey}_${UUID.randomUUID()}.jpg"

        return uploadOrFallbackLocal(
            context = context,
            storagePath = storagePath,
            localSubDir = "covers",
            localFileName = "cover_${safeKey}_${UUID.randomUUID()}.jpg",
            jpegBytes = jpegBytes,
        )
    }

    private suspend fun uploadOrFallbackLocal(
        context: Context,
        storagePath: String,
        localSubDir: String,
        localFileName: String,
        jpegBytes: ByteArray,
    ): UploadedIllustration {
        return try {
            val storageRef = Firebase.storage.reference.child(storagePath)
            val metadata = storageMetadata {
                contentType = "image/jpeg"
                setCustomMetadata("app", "StoryQuest")
            }
            storageRef.putBytes(jpegBytes, metadata).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()
            UploadedIllustration(
                url = downloadUrl,
                isFirebaseStorage = true,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Firebase Storage upload failed for $storagePath, caching locally: ${e.message}")
            val localUri = saveBytesLocally(context, localSubDir, localFileName, jpegBytes)
            UploadedIllustration(
                url = localUri,
                isFirebaseStorage = false,
                note = "Saved locally (enable Firebase Storage bucket & rules in Firebase Console for cloud sync).",
            )
        }
    }

    private suspend fun compressBitmapToJpeg(bitmap: Bitmap): ByteArray = withContext(Dispatchers.Default) {
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            out.toByteArray()
        }
    }

    private suspend fun saveBytesLocally(
        context: Context,
        subDir: String,
        fileName: String,
        bytes: ByteArray,
    ): String = withContext(Dispatchers.IO) {
        val dir = File(context.applicationContext.filesDir, subDir).apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            out.write(bytes)
        }
        "file://${file.absolutePath}"
    }

    private fun sanitizePathSegment(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(80).ifBlank { "item" }
    }

    private companion object {
        const val TAG = "SceneStorageService"
    }
}
