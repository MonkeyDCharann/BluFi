package com.example.blufi

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ChatMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)

    @Query("SELECT * FROM chat_messages WHERE remoteDeviceAddress = :address ORDER BY timestamp ASC")
    suspend fun getMessagesForDevice(address: String): List<ChatMessage>

    @Query("DELETE FROM chat_messages WHERE remoteDeviceAddress = :address")
    suspend fun clearChatForDevice(address: String)
}