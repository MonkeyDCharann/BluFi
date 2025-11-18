package com.example.blufi

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import java.text.SimpleDateFormat
import java.util.*
import android.content.Context
import android.util.TypedValue
import kotlin.math.min

class ChatAdapter(
    // CHANGED: messages must be a mutable property (var)
    private var messages: List<ChatMessage>,
    private val connectedDeviceName: String,
    private val onFileClick: (Uri, String?) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // REINTRODUCED: Formatter for time and date
    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    companion object {
        private const val VIEW_TYPE_DATE = 0 // REINTRODUCED
        private const val VIEW_TYPE_SENT_TEXT = 1
        private const val VIEW_TYPE_RECEIVED_TEXT = 2
        private const val VIEW_TYPE_SYSTEM = 3
        private const val VIEW_TYPE_SENT_FILE = 4
        private const val VIEW_TYPE_RECEIVED_FILE = 5

        private const val MEDIA_THUMBNAIL_SIZE_DP = 200
        private const val DOCUMENT_THUMBNAIL_SIZE_DP = 48

        // REINTRODUCED: isSameDay logic
        private fun isSameDay(t1: Long, t2: Long): Boolean {
            val cal1 = Calendar.getInstance().apply { timeInMillis = t1 }
            val cal2 = Calendar.getInstance().apply { timeInMillis = t2 }
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                    cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
        }

        // MOVED: dpToPx helper to companion object
        private fun dpToPx(context: Context, dp: Int): Int {
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp.toFloat(),
                context.resources.displayMetrics
            ).toInt()
        }
    }

    // REINTRODUCED: List with headers logic
    private var listWithHeaders: List<ChatMessage?> = emptyList()

    init {
        // Initial setup calls the new public update function
        updateMessages(messages)
    }

    // NEW PUBLIC FUNCTION: Use this to update the adapter from the Activity
    fun updateMessages(newMessages: List<ChatMessage>) {
        this.messages = newMessages // Update the internal list reference
        this.listWithHeaders = calculateListWithHeaders(newMessages) // Recalculate headers
        notifyDataSetChanged() // Notify RecyclerView to refresh
    }


    private fun calculateListWithHeaders(originalMessages: List<ChatMessage>): List<ChatMessage?> {
        val result = mutableListOf<ChatMessage?>()
        if (originalMessages.isEmpty()) return result

        for (i in originalMessages.indices) {
            val currentMsg = originalMessages[i]
            val previousMsg = if (i > 0) originalMessages[i - 1] else null

            if (previousMsg == null || !isSameDay(currentMsg.timestamp, previousMsg.timestamp)) {
                result.add(null) // Date header placeholder
            }
            result.add(currentMsg) // Actual message
        }
        return result
    }

    private fun formatDateHeader(timestamp: Long): String {
        val messageCalendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val nowCalendar = Calendar.getInstance()

        return if (isSameDay(timestamp, System.currentTimeMillis())) {
            "Today"
        } else {
            dateFormatter.format(Date(timestamp))
        }
    }

    // REINTRODUCED: Logic for VIEW_TYPE_DATE and checking listWithHeaders
    override fun getItemViewType(position: Int): Int {
        val message = listWithHeaders[position]

        if (message == null) {
            return VIEW_TYPE_DATE
        }

        return when {
            message.isSystem -> VIEW_TYPE_SYSTEM
            message.fileUri != null -> {
                if (message.isUser) VIEW_TYPE_SENT_FILE else VIEW_TYPE_RECEIVED_FILE
            }
            else -> {
                if (message.isUser) VIEW_TYPE_SENT_TEXT else VIEW_TYPE_RECEIVED_TEXT
            }
        }
    }

    // REINTRODUCED: Correct message list count
    override fun getItemCount(): Int = listWithHeaders.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_DATE -> {
                val view = inflater.inflate(R.layout.list_item_message_system, parent, false)
                DateViewHolder(view)
            }
            VIEW_TYPE_SENT_TEXT -> {
                val view = inflater.inflate(R.layout.list_item_message_sent, parent, false)
                SentTextMessageViewHolder(view)
            }
            VIEW_TYPE_RECEIVED_TEXT -> {
                val view = inflater.inflate(R.layout.list_item_message_received, parent, false)
                ReceivedTextMessageViewHolder(view)
            }
            VIEW_TYPE_SENT_FILE -> {
                val view = inflater.inflate(R.layout.list_item_file_sent, parent, false)
                SentFileViewHolder(view)
            }
            VIEW_TYPE_RECEIVED_FILE -> {
                val view = inflater.inflate(R.layout.list_item_file_received, parent, false)
                ReceivedFileViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.list_item_message_system, parent, false)
                SystemMessageViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = listWithHeaders[position]

        // Handle Date Header
        if (holder.itemViewType == VIEW_TYPE_DATE) {
            val nextMessage = listWithHeaders[min(position + 1, listWithHeaders.lastIndex)]
            if (nextMessage != null) {
                (holder as DateViewHolder).bind(formatDateHeader(nextMessage.timestamp))
            }
            return
        }

        if (message == null) return

        val timestampString = timeFormatter.format(Date(message.timestamp))

        when (holder.itemViewType) {
            VIEW_TYPE_SENT_TEXT -> (holder as SentTextMessageViewHolder).bind(message, timestampString)
            VIEW_TYPE_RECEIVED_TEXT -> (holder as ReceivedTextMessageViewHolder).bind(message, timestampString, connectedDeviceName)
            VIEW_TYPE_SENT_FILE -> (holder as SentFileViewHolder).bind(message, timestampString, onFileClick)
            VIEW_TYPE_RECEIVED_FILE -> (holder as ReceivedFileViewHolder).bind(message, timestampString, connectedDeviceName, onFileClick)
            VIEW_TYPE_SYSTEM -> (holder as SystemMessageViewHolder).bind(message)
        }
    }

    // region ViewHolders

    class DateViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // UPDATED: Use systemText and systemDate for visibility switching
        private val systemText: TextView = itemView.findViewById(R.id.systemText)
        private val systemDate: TextView = itemView.findViewById(R.id.systemDate)
        fun bind(dateString: String) {
            systemText.visibility = View.GONE
            systemDate.visibility = View.VISIBLE
            systemDate.text = dateString
        }
    }

    class SentTextMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val messageTimestamp: TextView = itemView.findViewById(R.id.messageTimestamp)
        fun bind(message: ChatMessage, timestamp: String) {
            messageText.text = message.text
            messageTimestamp.text = timestamp
        }
    }

    class ReceivedTextMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val messageTimestamp: TextView = itemView.findViewById(R.id.messageTimestamp)
        private val avatarInitial: TextView = itemView.findViewById(R.id.avatarInitial)
        fun bind(message: ChatMessage, timestamp: String, deviceName: String) {
            messageText.text = message.text
            messageTimestamp.text = timestamp
            if (deviceName.isNotEmpty()) {
                avatarInitial.text = deviceName.first().toString().uppercase()
            }
        }
    }

    class SentFileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val fileThumbnail: ImageView = itemView.findViewById(R.id.fileThumbnail)
        private val fileNameText: TextView = itemView.findViewById(R.id.fileNameText)
        private val fileTimestamp: TextView = itemView.findViewById(R.id.fileTimestamp)

        fun bind(message: ChatMessage, timestamp: String, onFileClick: (Uri, String?) -> Unit) {
            fileNameText.text = message.fileName
            fileTimestamp.text = timestamp
            val fileUri = Uri.parse(message.fileUri)
            val mimeType = message.fileMimeType

            val isMedia = mimeType?.startsWith("image/") == true || mimeType?.startsWith("video/") == true

            val sizePx = if (isMedia) {
                dpToPx(itemView.context, MEDIA_THUMBNAIL_SIZE_DP)
            } else {
                dpToPx(itemView.context, DOCUMENT_THUMBNAIL_SIZE_DP)
            }

            // Set layout dimensions
            fileThumbnail.layoutParams.width = sizePx
            fileThumbnail.layoutParams.height = sizePx
            fileThumbnail.scaleType = if (isMedia) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER

            if (isMedia) {
                // Load actual thumbnail for media
                Glide.with(itemView.context)
                    .load(fileUri)
                    .centerCrop()
                    .override(sizePx)
                    .apply(RequestOptions.placeholderOf(R.drawable.ic_file_generic))
                    .into(fileThumbnail)
            } else {
                // Set a generic icon for documents
                fileThumbnail.setImageResource(R.drawable.ic_file_generic)
            }

            itemView.setOnClickListener {
                onFileClick(fileUri, message.fileMimeType)
            }
        }
    }

    class ReceivedFileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val fileThumbnail: ImageView = itemView.findViewById(R.id.fileThumbnail)
        private val fileNameText: TextView = itemView.findViewById(R.id.fileNameText)
        private val fileTimestamp: TextView = itemView.findViewById(R.id.fileTimestamp)
        private val avatarInitial: TextView = itemView.findViewById(R.id.avatarInitial)

        fun bind(message: ChatMessage, timestamp: String, deviceName: String, onFileClick: (Uri, String?) -> Unit) {
            fileNameText.text = message.fileName
            fileTimestamp.text = timestamp
            if (deviceName.isNotEmpty()) {
                avatarInitial.text = deviceName.first().toString().uppercase()
            }

            val fileUri = Uri.parse(message.fileUri)
            val mimeType = message.fileMimeType

            val isMedia = mimeType?.startsWith("image/") == true || mimeType?.startsWith("video/") == true

            val sizePx = if (isMedia) {
                dpToPx(itemView.context, MEDIA_THUMBNAIL_SIZE_DP)
            } else {
                dpToPx(itemView.context, DOCUMENT_THUMBNAIL_SIZE_DP)
            }

            // Set layout dimensions
            fileThumbnail.layoutParams.width = sizePx
            fileThumbnail.layoutParams.height = sizePx
            fileThumbnail.scaleType = if (isMedia) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER

            if (isMedia) {
                // Load actual thumbnail for media
                Glide.with(itemView.context)
                    .load(fileUri)
                    .centerCrop()
                    .override(sizePx)
                    .apply(RequestOptions.placeholderOf(R.drawable.ic_file_generic))
                    .into(fileThumbnail)
            } else {
                // Set a generic icon for documents
                fileThumbnail.setImageResource(R.drawable.ic_file_generic)
            }

            itemView.setOnClickListener {
                onFileClick(fileUri, message.fileMimeType)
            }
        }
    }

    class SystemMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // UPDATED: Find both views and switch visibility
        private val systemText: TextView = itemView.findViewById(R.id.systemText)
        private val systemDate: TextView = itemView.findViewById(R.id.systemDate)

        fun bind(message: ChatMessage) {
            systemDate.visibility = View.GONE
            systemText.visibility = View.VISIBLE
            systemText.text = message.text
        }
    }
    // endregion ViewHolders
}