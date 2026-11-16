package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.ChatMessage
import com.example.data.model.Story
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class StoryTurnResult(
    val narrative: String,
    val options: List<String>,
    val imagePrompt: String,
)

class StoryAiService {

    private val storyResponseSchema = Schema.obj(
        properties = mapOf(
            "narrative" to Schema.string(
                description = "The next vivid adventure scene reacting directly to the player's action or custom message. Address the player by their name. Keep it dramatic, WhatsApp-friendly (concise paragraphs), and strictly adventure/mystery (zero romance)."
            ),
            "options" to Schema.array(
                items = Schema.string(),
                description = "Exactly 3 distinct adventure action suggestions (corresponding to A, B, C) without 'A)'/'B)'/'C)' prefixes."
            ),
            "imagePrompt" to Schema.string(
                description = "A concise 1-sentence cinematic visual art prompt describing the current scene for illustration, with no text in the image."
            ),
        )
    )

    private val storyModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.9f
                responseMimeType = "application/json"
                responseSchema = storyResponseSchema
            }
        )
    }

    private val summaryModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.3f
            }
        )
    }

    private val imageModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    suspend fun generateNextTurn(
        story: Story,
        playerName: String,
        summary: String,
        allMessages: List<ChatMessage>,
        playerMessage: String,
    ): StoryTurnResult {
        // Memory Formula: Send ONLY Story System Prompt + User Name + Story Summary + Last 10 messages
        val last10 = allMessages.takeLast(10)
        val recentTranscript = if (last10.isEmpty()) {
            "(Story just started)"
        } else {
            last10.joinToString("\n") { msg ->
                val speaker = if (msg.role == "user") playerName else "Storyteller"
                "$speaker: ${msg.content}"
            }
        }

        val prompt = buildString {
            appendLine("=== STORY SYSTEM PROMPT ===")
            appendLine(story.systemPrompt)
            appendLine("Strict Content Rule: Pure adventure, mystery, survival, and questing ONLY. Strictly NO romance.")
            appendLine("Language Rule: Match the player's language/style naturally (Hinglish, Hindi, or English).")
            appendLine("Custom Input Rule: The player can write ANY custom action or dialogue, not just A/B/C options. Always react directly to their exact message and evolve the plot accordingly.")
            appendLine()
            appendLine("=== PLAYER PROFILE ===")
            appendLine("Player Name: $playerName")
            appendLine()
            appendLine("=== STORY SUMMARY SO FAR ===")
            appendLine(summary.ifBlank { "No prior summary yet. Adventure is in its opening chapter." })
            appendLine()
            appendLine("=== LAST 10 MESSAGES ===")
            appendLine(recentTranscript)
            appendLine()
            appendLine("=== LATEST PLAYER ACTION ===")
            appendLine("$playerName: $playerMessage")
            appendLine()
            appendLine("Continue the story in character, react to '$playerMessage', and provide 3 fresh adventure options.")
        }

        try {
            val response = withTimeout(60_000L) {
                storyModel.generateContent(prompt)
            }
            val rawJson = response.text ?: throw IllegalStateException("Story model returned an empty response.")
            val json = JSONObject(rawJson)
            val narrative = json.getString("narrative").trim()
            val optionsArray = json.getJSONArray("options")
            val options = (0 until optionsArray.length())
                .map { optionsArray.getString(it).trim() }
                .filter { it.isNotBlank() }
                .take(3)
            val imagePrompt = json.optString("imagePrompt", "").trim()

            return StoryTurnResult(
                narrative = narrative,
                options = options.ifEmpty {
                    listOf(
                        "Aage badho aur aas-paas check karo",
                        "Apne saathi se madad mango",
                        "Koi naya raasta dhoondo"
                    )
                },
                imagePrompt = imagePrompt.ifBlank {
                    "Atmospheric dark adventure scene for ${story.title}, cinematic lighting, no text"
                }
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Story generation timed out", e)
            throw IllegalStateException("Story generation timed out after 60 seconds. Please try again.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Story generation failed", e)
            throw e
        }
    }

    suspend fun generateRollingSummary(
        storyTitle: String,
        playerName: String,
        previousSummary: String,
        recentMessages: List<ChatMessage>,
    ): String {
        val window = recentMessages.takeLast(20).joinToString("\n") { msg ->
            val speaker = if (msg.role == "user") playerName else "Storyteller"
            "$speaker: ${msg.content}"
        }
        val prompt = """
            Summarize the adventure story progress concisely in under 150 words so the AI Game Master remembers all key facts.
            Story Title: $storyTitle
            Player Name: $playerName
            Previous Summary: ${previousSummary.ifBlank { "None" }}
            Recent 20 Messages:
            $window

            Include: current location, key NPCs met, alliances/conflicts, inventory/injuries, and unresolved mysteries. Plain text only.
        """.trimIndent()

        return try {
            val response = withTimeout(45_000L) {
                summaryModel.generateContent(prompt)
            }
            response.text?.trim().orEmpty().ifBlank { previousSummary }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Summary generation failed", e)
            previousSummary
        }
    }

    suspend fun generateSceneImage(promptText: String): Bitmap {
        return try {
            val response = withTimeout(60_000L) {
                imageModel.generateContent(
                    "Cinematic dark fantasy/sci-fi game scene artwork, atmospheric volumetric lighting, no text or watermarks: $promptText"
                )
            }
            val imagePart = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()
            imagePart?.image ?: throw IllegalStateException("No image was returned by gemini-nano-banana-2.1.")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Image generation timed out", e)
            throw IllegalStateException("Image generation timed out after 60 seconds.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image generation failed", e)
            throw e
        }
    }

    suspend fun editSceneImage(inputBitmap: Bitmap, editInstruction: String): Bitmap {
        return try {
            val response = withTimeout(60_000L) {
                imageModel.generateContent(
                    content {
                        image(inputBitmap)
                        text("Edit this adventure artwork according to the instruction, keeping cohesive cinematic lighting and no text: $editInstruction")
                    }
                )
            }
            val imagePart = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()
            imagePart?.image ?: throw IllegalStateException("No edited image was returned by gemini-nano-banana-2.1.")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Image editing timed out", e)
            throw IllegalStateException("Image editing timed out after 60 seconds.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image editing failed", e)
            throw e
        }
    }

    private companion object {
        const val TAG = "StoryAiService"
    }
}
