package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.Story
import com.example.data.model.StoryChat
import com.example.data.model.UserProfile
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class StoryRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth,
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeActiveStories(): Flow<List<Story>> = flow {
        requireUserId()
        val path = STORIES_COLLECTION
        emitAll(
            db.collection(STORIES_COLLECTION)
                .whereEqualTo("isActive", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Story::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeAllStoriesAdmin(): Flow<List<Story>> = flow {
        requireUserId()
        val path = STORIES_COLLECTION
        emitAll(
            db.collection(STORIES_COLLECTION)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Story::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun getStory(storyId: String): Story? {
        requireUserId()
        val docRef = db.collection(STORIES_COLLECTION).document(storyId)
        return try {
            docRef.get().await().toObject(
                Story::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun seedDefaultStoriesIfEmpty() {
        requireUserId()
        val path = STORIES_COLLECTION
        try {
            val existing = db.collection(STORIES_COLLECTION).limit(1).get().await()
            if (!existing.isEmpty) return
            for (story in DEFAULT_STARTER_STORIES) {
                createStory(story, explicitId = story.id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            throw e
        }
    }

    suspend fun createStory(story: Story, explicitId: String? = null): String {
        requireUserId()
        val docRef = if (!explicitId.isNullOrBlank()) {
            db.collection(STORIES_COLLECTION).document(explicitId)
        } else {
            db.collection(STORIES_COLLECTION).document()
        }
        val payload = mapOf(
            "title" to story.title.trim(),
            "description" to story.description.trim(),
            "systemPrompt" to story.systemPrompt.trim(),
            "coverImage" to story.coverImage.trim().ifEmpty { "preset:mystic_realm" },
            "genre" to story.genre.trim().ifEmpty { "Adventure" },
            "openingLine" to story.openingLine.trim(),
            "isActive" to story.isActive,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateStory(story: Story) {
        requireUserId()
        require(story.id.isNotBlank()) { "Story ID must not be blank for update" }
        val docRef = db.collection(STORIES_COLLECTION).document(story.id)
        val updates = mapOf(
            "title" to story.title.trim(),
            "description" to story.description.trim(),
            "systemPrompt" to story.systemPrompt.trim(),
            "coverImage" to story.coverImage.trim().ifEmpty { "preset:mystic_realm" },
            "genre" to story.genre.trim().ifEmpty { "Adventure" },
            "openingLine" to story.openingLine.trim(),
            "isActive" to story.isActive,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteStory(storyId: String) {
        requireUserId()
        val docRef = db.collection(STORIES_COLLECTION).document(storyId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        requireUserId()
        val docRef = db.collection(USERS_COLLECTION).document(userId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    logStreamError(error, OperationType.GET, docRef.path)
                    throw error
                }
        )
    }

    suspend fun saveUserProfile(name: String, email: String? = null) {
        val uid = requireUserId()
        val docRef = db.collection(USERS_COLLECTION).document(uid)
        try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                val updates = buildMap<String, Any> {
                    put("name", name.trim())
                    if (!email.isNullOrBlank()) put("email", email.trim())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(updates).await()
            } else {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("name", name.trim())
                    if (!email.isNullOrBlank()) put("email", email.trim())
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    fun observeAllUsersAdmin(): Flow<List<UserProfile>> = flow {
        requireUserId()
        val path = USERS_COLLECTION
        emitAll(
            db.collection(USERS_COLLECTION)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeUserChats(userId: String): Flow<List<StoryChat>> = flow {
        requireUserId()
        val path = CHATS_COLLECTION
        emitAll(
            db.collection(CHATS_COLLECTION)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StoryChat::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeAllChatsAdmin(): Flow<List<StoryChat>> = flow {
        requireUserId()
        val path = CHATS_COLLECTION
        emitAll(
            db.collection(CHATS_COLLECTION)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StoryChat::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeChat(userId: String, storyId: String): Flow<StoryChat?> = flow {
        requireUserId()
        val chatId = chatDocId(userId, storyId)
        val docRef = db.collection(CHATS_COLLECTION).document(chatId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            StoryChat::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    logStreamError(error, OperationType.GET, docRef.path)
                    throw error
                }
        )
    }

    suspend fun saveChatState(
        storyId: String,
        storyTitle: String,
        messages: List<ChatMessage>,
        summary: String,
        messageCount: Long,
    ) {
        val uid = requireUserId()
        val chatId = chatDocId(uid, storyId)
        val docRef = db.collection(CHATS_COLLECTION).document(chatId)
        // Enforce bounded array size <= 180 (latest window) while summary holds older arcs
        val boundedMessages = if (messages.size > 180) messages.takeLast(180) else messages
        val serializedMessages = boundedMessages.map { it.toMap() }
        try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                val updates = mapOf(
                    "storyTitle" to storyTitle.take(120),
                    "messages" to serializedMessages,
                    "summary" to summary.take(4000),
                    "messageCount" to messageCount,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.update(updates).await()
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "storyId" to storyId,
                    "storyTitle" to storyTitle.take(120),
                    "messages" to serializedMessages,
                    "summary" to summary.take(4000),
                    "messageCount" to messageCount,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.set(payload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteChat(storyId: String) {
        val uid = requireUserId()
        val chatId = chatDocId(uid, storyId)
        val docRef = db.collection(CHATS_COLLECTION).document(chatId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    private fun logStreamError(error: Throwable, operationType: OperationType, path: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: error
        if (auth.currentUser != null) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }

    companion object {
        const val STORIES_COLLECTION = "stories"
        const val USERS_COLLECTION = "users"
        const val CHATS_COLLECTION = "chats"
        const val ADMIN_EMAIL = "rainytalesstudio@gmail.com"

        fun chatDocId(userId: String, storyId: String): String = "${userId}_${storyId}"

        val DEFAULT_STARTER_STORIES = listOf(
            Story(
                id = "one_boy_mystic_land",
                title = "Mystery of Kaali Roshni",
                description = "You wake up injured in an ancient hidden kingdom ruled by Rani Meera. Dark cosmic light is rising over the forest — uncover the secret with Zoya and Kaiya.",
                systemPrompt = "You are an immersive interactive storyteller running an adventure mystery set in an ancient hidden realm. Key characters: Zoya (healer & ally), Kaiya (royal scout), Rani Meera (warrior ruler). A mysterious Kaali Roshni (dark light) has appeared in the sky after 500 years. STRICT RULES: Pure adventure, mystery, survival, and questing ONLY — strictly NO romance. Write vivid, cinematic scenes in natural Hinglish/English matching the player's language. Always address the player by their name. End with 3 clear suggested action choices A, B, C, while welcoming any custom response.",
                coverImage = "preset:mystic_realm",
                genre = "Mystery Quest",
                openingLine = "*ZOYA:* (dheere se) Hosh aa gaya? Sar par gehri chot hai... Tum achanak jungle ke paas behosh mile the. Bahar aasman me wahi *Kaali Roshni* phir se dikh rahi hai jo 500 saal pehle aayi thi!\n\nAb tum kya karoge?",
                isActive = true,
            ),
            Story(
                id = "zombie_city_signal",
                title = "Zombie City: Last Signal",
                description = "The city fell in 48 hours. From a rain-soaked neon rooftop, intercept the final evacuation signal before the night-runners breach the tower.",
                systemPrompt = "You are the Game Master of a high-stakes post-apocalyptic survival adventure in a flooded neon metropolis overrun by fast night-runners. Key elements: scavenged gear, tactical stealth, radio signals, rooftop ziplines, and survivor factions. STRICT RULES: Pure survival & adventure ONLY — NO romance. Adapt naturally to Hinglish or English based on the player's message. Call the player by name. Always offer 3 distinct tactical options A, B, C at the end.",
                coverImage = "preset:zombie_city",
                genre = "Survival Thriller",
                openingLine = "Barish tez ho rahi hai aur rooftop ke neeche galiyon se *night-runners* ki cheekhein aa rahi hain. Tumhari walkie-talkie par ek kamzor signal aata hai: *'Chopper Sector-7 roof par 15 minute me utrega...'*\n\nTumhare paas ek flare gun aur ek crowbar hai. Pehla kadam kya hoga?",
                isActive = true,
            ),
            Story(
                id = "mars_red_monolith",
                title = "Mars Horizon: Red Monolith",
                description = "Cut off from Ares Base during a dust storm, you discover a humming obsidian monolith beneath the crimson dunes. Oxygen is at 38%.",
                systemPrompt = "You are an atmospheric hard-sci-fi adventure Game Master on Mars. The player is an astronaut exploring an ancient alien monolith inside a crimson canyon while managing suit power, oxygen, and rover systems. STRICT RULES: Pure sci-fi exploration & mystery ONLY — NO romance. Respond in vivid Hinglish/English matching the player. Address the player by name. Always provide 3 options A, B, C.",
                coverImage = "preset:space_mars",
                genre = "Sci-Fi Odyssey",
                openingLine = "*SUIT AI:* Warning — Oxygen 38%, Ares Base se signal cut ho chuka hai. Saamne dhundh ke beech ek 40-meter lamba *kala monolith* chamak raha hai, aur uske base par ek khula hua darwaza dikh raha hai.\n\nRover battery low hai. Tum kya decide karte ho?",
                isActive = true,
            ),
        )
    }
}
