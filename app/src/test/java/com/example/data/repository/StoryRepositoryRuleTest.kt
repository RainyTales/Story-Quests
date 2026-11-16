package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.ChatMessage
import com.example.data.model.Story
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class StoryRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun saveUserProfile_authenticatedUser_createsAndObservesProfile() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = StoryRepository(firestore, auth)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveUserProfile(name = "Vansh", email = ALICE_EMAIL)
        }

        val profile = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeUserProfile(uid).first { it != null }
        }
        assertNotNull(profile)
        assertEquals("Vansh", profile?.name)
        assertEquals(uid, profile?.userId)
    }

    @Test
    fun saveChatState_isolatedPerUserAndStory_succeedsAndBlocksCrossUserRead() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = StoryRepository(firestore, auth)
        val storyId = "story_${UUID.randomUUID().toString().take(8)}"

        val initialChat = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeChat(aliceUid, storyId).first()
        }
        assertEquals(null, initialChat)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.saveChatState(
                storyId = storyId,
                storyTitle = "Mystery of Kaali Roshni",
                messages = listOf(
                    ChatMessage(
                        id = "m1",
                        role = "model",
                        content = "Welcome Vansh!",
                        timestampMs = 1000L,
                        options = listOf("Explore", "Stay", "Ask Zoya"),
                        imagePrompt = "Mystic forest under dark cosmic glow",
                        sceneImageUrl = "https://firebasestorage.googleapis.com/v0/b/demo/o/scene.jpg",
                    )
                ),
                summary = "",
                messageCount = 1L,
            )
        }

        val chat = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeChat(aliceUid, storyId).first { it != null }
        }
        assertEquals("Mystery of Kaali Roshni", chat?.storyTitle)
        assertEquals(1, chat?.messages?.size)
        assertEquals(
            "https://firebasestorage.googleapis.com/v0/b/demo/o/scene.jpg",
            chat?.messages?.firstOrNull()?.sceneImageUrl
        )

        // Switch to Bob and verify Bob cannot read Alice's chat
        signInTestUser(BOB_EMAIL)
        val bobRepo = StoryRepository(firestore, auth)
        var denied = false
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeChat(aliceUid, storyId).first()
            }
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            if (fsError?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                denied = true
            }
        }
        assertTrue("Cross-user chat read should fail with PERMISSION_DENIED", denied)
    }

    @Test
    fun createStory_nonAdminUser_failsWithPermissionDenied() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = StoryRepository(firestore, auth)

        var denied = false
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                repository.createStory(
                    Story(
                        title = "Unauthorized Story",
                        description = "Should fail",
                        systemPrompt = "Adventure only",
                        coverImage = "preset:mystic_realm",
                        isActive = true,
                    ),
                    explicitId = "unauth_${UUID.randomUUID().toString().take(8)}"
                )
            }
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                denied = true
            }
        }
        assertTrue("Non-admin story creation should fail with PERMISSION_DENIED", denied)
    }

    @Test
    fun observeActiveStories_unauthenticated_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        var threw = false
        try {
            val repository = StoryRepository(firestore, auth)
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeActiveStories().first()
            }
        } catch (_: IllegalStateException) {
            threw = true
        }
        assertTrue("Unauthenticated caller must be blocked", threw)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_story@test.com"
        const val BOB_EMAIL = "bob_story@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
