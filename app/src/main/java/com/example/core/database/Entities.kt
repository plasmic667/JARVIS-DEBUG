package com.example.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val lastMessagePreview: String = "",
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val role: String, // "user" or "assistant"
    val content: String,
    val status: String = "complete", // "complete", "streaming", "error", "cancelled"
    val toolsJson: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val content: String,
    val category: String = "preference", // preference, project, person, routine, general
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val dueAt: Long? = null,
    val priority: String = "normal", // "low", "normal", "high"
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val remindAt: Long,
    val status: String = "scheduled", // "scheduled", "fired", "done"
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: String = "default_user",
    val operatorName: String = "Operator",
    val personality: String = "jarvis",
    val customPersonality: String? = null,
    val humor: Int = 2,
    val expressiveness: Int = 2,
    val responseLength: String = "balanced", // concise, balanced, detailed
    val autoSpeak: Boolean = true,
    val animations: Boolean = true,
    val memoryEnabled: Boolean = true,
    val voiceRate: Float = 1.0f,
    val voicePitch: Float = 0.95f,
    val voiceName: String? = null,
    val model: String = "gemini-2.5-flash",
    val temperature: Float = 0.8f,
    val email: String = "operator@jarvis.ai",
    val emailVerified: Boolean = true
)
