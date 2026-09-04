package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.data.local.PreferencesManager
import com.example.data.provider.ProviderRegistry
import com.example.ui.components.MarkdownBlock
import com.example.ui.components.parseMarkdownBlocks
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NovaAiUnitTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var prefs: PreferencesManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = PreferencesManager(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testMarkdownCodeBlockParsing() {
        val raw = """
            Here is a Python function:
            ```python
            def greet(name):
                return f"Hello {name}"
            ```
            That is all!
        """.trimIndent()

        val blocks = parseMarkdownBlocks(raw)
        assertTrue(blocks.any { it is MarkdownBlock.Code && it.language.equals("python", ignoreCase = true) })
        val codeBlock = blocks.filterIsInstance<MarkdownBlock.Code>().first()
        assertTrue(codeBlock.code.contains("def greet"))
    }

    @Test
    fun testMarkdownHeadersAndBullets() {
        val raw = """
            # Header One
            ## Header Two
            - Point 1
            - Point 2
        """.trimIndent()

        val blocks = parseMarkdownBlocks(raw)
        assertTrue(blocks.any { it is MarkdownBlock.Header && it.level == 1 && it.text == "Header One" })
        assertTrue(blocks.any { it is MarkdownBlock.Header && it.level == 2 && it.text == "Header Two" })
        assertEquals(2, blocks.count { it is MarkdownBlock.Bullet })
    }

    @Test
    fun testPreferencesMaskApiKey() {
        val key = "AIzaSyABC1234567890XYZ"
        prefs.setApiKey(PreferencesManager.PROVIDER_GEMINI, key)
        val retrieved = prefs.getApiKey(PreferencesManager.PROVIDER_GEMINI)
        assertEquals(key, retrieved)

        val masked = prefs.maskApiKey(key)
        assertTrue(masked.contains("••••"))
        assertFalse(masked.contains("123456"))
    }

    @Test
    fun testProviderRegistry() {
        val gemini = ProviderRegistry.getProvider(PreferencesManager.PROVIDER_GEMINI)
        assertNotNull(gemini)
        assertEquals("gemini", gemini.id)

        val openai = ProviderRegistry.getProvider(PreferencesManager.PROVIDER_OPENAI)
        assertNotNull(openai)
        assertEquals("openai", openai.id)
    }

    @Test
    fun testRoomConversationAndMessagePersistence() = runBlocking {
        val dao = db.chatDao()

        val conv = ConversationEntity(
            id = "conv-1",
            title = "Quantum Physics Chat",
            providerId = "gemini",
            modelId = "gemini-2.5-flash"
        )
        dao.insertConversation(conv)

        val convs = dao.getAllConversations().first()
        assertEquals(1, convs.size)
        assertEquals("Quantum Physics Chat", convs.first().title)

        val msg = MessageEntity(
            id = "msg-1",
            conversationId = "conv-1",
            role = "user",
            content = "Explain Schrödinger's cat"
        )
        dao.insertMessage(msg)

        val msgs = dao.getMessagesForConversation("conv-1").first()
        assertEquals(1, msgs.size)
        assertEquals("Explain Schrödinger's cat", msgs.first().content)
    }
}
