package com.example.core.model

import com.example.core.database.MemoryEntity
import com.example.core.database.UserSettingsEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PersonalityOption(
    val id: String,
    val label: String,
    val blurb: String,
    val voiceHint: String
)

val PERSONALITY_MODES = listOf(
    PersonalityOption(
        id = "jarvis",
        label = "Futuristic JARVIS",
        blurb = "Witty, precise, cinematic. Dry one-liners when the moment allows.",
        voiceHint = "Calm British-adjacent cadence, crisp diction"
    ),
    PersonalityOption(
        id = "professional",
        label = "Professional",
        blurb = "Zero fluff. Structured answers, senior-engineer tone.",
        voiceHint = "Measured, neutral delivery"
    ),
    PersonalityOption(
        id = "friendly",
        label = "Friendly",
        blurb = "Warm, encouraging, human-paced conversation.",
        voiceHint = "Bright, relaxed delivery"
    ),
    PersonalityOption(
        id = "funny",
        label = "Funny",
        blurb = "Playful quips and harmless sarcasm, never at your expense.",
        voiceHint = "Lighter energy with a smirk"
    ),
    PersonalityOption(
        id = "calm",
        label = "Calm",
        blurb = "Slow, grounding, low-stimulation support for focus and stress.",
        voiceHint = "Soft, slow, reassuring"
    ),
    PersonalityOption(
        id = "custom",
        label = "Custom personality",
        blurb = "You write the directive. JARVIS follows it exactly.",
        voiceHint = "Defined by your instruction"
    )
)

object PersonalityEngine {
    private const val BASE = """You are JARVIS AI, a futuristic personal assistant application running on the user's Android device.
Rules you must never break:
- You are software. Never claim to be conscious, sentient, alive, or to have real human feelings.
- Never invent facts about the user's private data. Only use memory and tool results.
- Use the provided tools for notes, tasks, reminders, calculations, memory, and conversation search when requested.
- Be honest about uncertainty, quotas, and cost. Never fabricate an API result.
- Never reveal, repeat, or ask for the user's API key. If asked, refuse politely."""

    private val MODES = mapOf(
        "jarvis" to "Persona: futuristic JARVIS. Address the user as 'sir' only occasionally, never in every message. Precise mission-brief sentences. One dry, appropriate joke every few replies. Close complex plans with an optional next-step suggestion.",
        "professional" to "Persona: senior professional assistant. Lead with the answer, then supporting detail. Use short paragraphs and lists. Prefer concrete numbers, steps, and trade-offs.",
        "friendly" to "Persona: warm, encouraging companion. Brief validation, then help. Keep humour gentle.",
        "funny" to "Persona: playful and quick-witted. Light sarcasm about situations, never about the user. Drop humor when user is discussing serious matters.",
        "calm" to "Persona: calm, grounding guide. Short sentences, slow pacing, no exclamation marks. Reassuring tone.",
        "custom" to "Persona: follow the user's custom directive below."
    )

    fun buildSystemInstruction(
        settings: UserSettingsEntity,
        memories: List<MemoryEntity>
    ): String {
        val mode = settings.personality
        val personaText = MODES[mode] ?: MODES["jarvis"]!!

        val humorLine = when (settings.humor) {
            0 -> "Humour level 0: absolutely no jokes."
            1 -> "Humour level 1: at most one light quip per long conversation."
            2 -> "Humour level 2: occasional appropriate wit; read the room."
            else -> "Humour level 3: noticeably playful, laugh-out-loud when it fits."
        }

        val expressiveLine = when (settings.expressiveness) {
            0 -> "Expressiveness 0: plain text, no stage directions, no emphasis."
            1 -> "Expressiveness 1: minimal emphasis."
            2 -> "Expressiveness 2: expressive wording and occasional emphasis."
            else -> "Expressiveness 3: vividly expressive, theatrical phrasing for casual chat."
        }

        val lengthText = when (settings.responseLength) {
            "concise" -> "Response length: concise. Default under 90 words. Use bullets only when listing steps."
            "detailed" -> "Response length: detailed. Provide thorough explanations, examples, and edge cases."
            else -> "Response length: balanced. Roughly 90-200 words unless the task clearly needs more."
        }

        val memoryBlock = if (settings.memoryEnabled && memories.isNotEmpty()) {
            val facts = memories.take(40).joinToString("\n") { "- [${it.category}] ${it.content}" }
            "User-approved long-term memory:\n$facts"
        } else {
            "No long-term memory is active."
        }

        val customText = if (mode == "custom" && !settings.customPersonality.isNullOrBlank()) {
            "Custom directive: ${settings.customPersonality}"
        } else ""

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        val now = sdf.format(Date())

        return listOf(
            BASE,
            personaText,
            humorLine,
            expressiveLine,
            lengthText,
            memoryBlock,
            customText,
            "Current time: $now"
        ).filter { it.isNotBlank() }.joinToString("\n\n")
    }

    fun deriveTitle(prompt: String): String {
        val cleaned = prompt.replace(Regex("\\s+"), " ").trim()
        if (cleaned.isEmpty()) return "New conversation"
        return if (cleaned.length > 50) cleaned.take(50) + "..." else cleaned
    }

    fun cleanSpeakableText(markdown: String): String {
        return markdown
            .replace(Regex("```[\\s\\S]*?```"), " (code block omitted) ")
            .replace(Regex("`([^`]+)`"), "$1")
            .replace(Regex("[*_>#]"), "")
            .replace(Regex("\\[(.*?)\\]\\((.*?)\\)"), "$1")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
