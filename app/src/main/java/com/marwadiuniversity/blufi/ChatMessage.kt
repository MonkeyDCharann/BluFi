package com.example.blufi

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val remoteDeviceAddress: String,
    val text: String? = null,
    val isUser: Boolean,
    val timestamp: Long,
    val isSystem: Boolean = false,
    val fileUri: String? = null,
    val fileName: String? = null,
    val fileMimeType: String? = null
)