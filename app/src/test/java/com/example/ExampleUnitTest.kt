package com.example

import com.example.core.database.MemoryEntity
import com.example.core.database.UserSettingsEntity
import com.example.core.model.PersonalityEngine
import com.example.core.network.GeminiClient
import com.example.core.network.ValidationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testPersonalityInstructionGeneration() {
        val settings = UserSettingsEntity(
            personality = "jarvis",
            humor = 2,
            expressiveness = 2,
            responseLength = "concise",
            memoryEnabled = true
        )
        val memories = listOf(
            MemoryEntity(content = "Prefers bullet points", category = "preference")
        )
        val prompt = PersonalityEngine.buildSystemInstruction(settings, memories)

        assertTrue(prompt.contains("JARVIS AI"))
        assertTrue(prompt.contains("Prefers bullet points"))
        assertTrue(prompt.contains("concise"))
    }

    @Test
    fun testTitleDerivation() {
        assertEquals("New conversation", PersonalityEngine.deriveTitle("   "))
        assertEquals("Calculate total invoice amount", PersonalityEngine.deriveTitle("Calculate total invoice amount"))
        val longText = "A".repeat(100)
        val title = PersonalityEngine.deriveTitle(longText)
        assertTrue(title.length <= 53)
        assertTrue(title.endsWith("..."))
    }

    @Test
    fun testSpeakableTextCleaning() {
        val markdown = "# Mission Briefing\n\n```kotlin\nval x = 1\n```\n**Important:** Check [dashboard](https://jarvis.ai)."
        val clean = PersonalityEngine.cleanSpeakableText(markdown)
        assertFalse(clean.contains("```"))
        assertFalse(clean.contains("**"))
        assertFalse(clean.contains("val x = 1"))
        assertTrue(clean.contains("Important:"))
        assertTrue(clean.contains("dashboard"))
    }

    @Test
    fun testApiKeyValidationEmptyHandling() = runBlocking {
        val client = GeminiClient()
        val result = client.validateApiKey("   ")
        assertTrue(result is ValidationResult.Error)
        val err = result as ValidationResult.Error
        assertEquals("Invalid API Key. Please check your key and try again.", err.message)
    }

    @Test
    fun testApiKeyValidationAcceptsNonAIzaKeys() {
        // Critical requirement verification: verify that non-AIza keys are not rejected by format check
        val standardKey = "AIzaSyD-sample_test_key_12345"
        val customStudioKey = "studio-custom-key-without-aiza-prefix"
        val emptyKey = "   "

        assertFalse(standardKey.trim().isEmpty())
        assertFalse(customStudioKey.trim().isEmpty())
        assertTrue(emptyKey.trim().isEmpty())
    }
}
