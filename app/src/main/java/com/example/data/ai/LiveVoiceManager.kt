package com.example.data.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.model.ChatMessage
import com.example.data.model.Story
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException

sealed interface LiveVoiceState {
    data object Idle : LiveVoiceState
    data object Connecting : LiveVoiceState
    data object Active : LiveVoiceState
    data class Error(val message: String) : LiveVoiceState
}

@OptIn(PublicPreviewAPI::class)
class LiveVoiceManager {
    private var activeSession: LiveSession? = null

    suspend fun startVoiceAdventure(
        context: Context,
        story: Story,
        playerName: String,
        summary: String,
        recentMessages: List<ChatMessage>,
    ) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            throw SecurityException("Microphone permission is required for live voice mode.")
        }

        stopVoiceAdventure()

        val recentContext = recentMessages.takeLast(6).joinToString("\n") { msg ->
            val who = if (msg.role == "user") playerName else "Storyteller"
            "$who: ${msg.content}"
        }

        val instructionText = buildString {
            appendLine("You are the live voice Game Master for '${story.title}'.")
            appendLine(story.systemPrompt)
            appendLine("Player Name: $playerName. Speak naturally in Hinglish or English matching the player.")
            appendLine("Adventure & mystery only, strictly NO romance.")
            if (summary.isNotBlank()) {
                appendLine("Story Summary: $summary")
            }
            if (recentContext.isNotBlank()) {
                appendLine("Recent Context:\n$recentContext")
            }
            appendLine("Keep spoken turns engaging, dramatic, and concise (2-4 sentences) and prompt $playerName for what they do next.")
        }

        val liveModel = Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
            modelName = "gemini-3.8-live",
            generationConfig = liveGenerationConfig {
                responseModality = ResponseModality.AUDIO
                speechConfig = SpeechConfig(voice = Voice("FENRIR"))
            },
            systemInstruction = content { text(instructionText) }
        )

        val session = liveModel.connect()
        activeSession = session
        session.startAudioConversation()
    }

    suspend fun stopVoiceAdventure() {
        val session = activeSession ?: return
        activeSession = null
        try {
            session.stopAudioConversation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping audio conversation: ${e.message}")
        }
        try {
            session.close()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Error closing live session: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "LiveVoiceManager"
    }
}
