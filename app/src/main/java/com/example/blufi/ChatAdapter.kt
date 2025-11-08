package com.example.blufi

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import java.text.SimpleDateFormat
import java.util.*
import android.content.Context // Added import
import android.util.TypedValue // Added import

class ChatAdapter(
    private val messages: List<ChatMessage>,
    private val connectedDeviceName: String,
    private val onFileClick: (Uri, String?) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val dateFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    companion object {
        private const val VIEW_TYPE_SENT_TEXT = 1
        private const val VIEW_TYPE_RECEIVED_TEXT = 2
        private const val VIEW_TYPE_SYSTEM = 3
        private const val VIEW_TYPE_SENT_FILE = 4
        private const val VIEW_TYPE_RECEIVED_FILE = 5

        // Define size constants for file thumbnails
        private const val MEDIA_THUMBNAIL_SIZE_DP = 200 // Large size for photos/videos
        private const val DOCUMENT_THUMBNAIL_SIZE_DP = 48 // Small size for documents (PDF, DOCX)
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
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
        val message = messages[position]
        val timestamp = dateFormatter.format(Date(message.timestamp))

        when (holder.itemViewType) {
            VIEW_TYPE_SENT_TEXT -> (holder as SentTextMessageViewHolder).bind(message, timestamp)
            VIEW_TYPE_RECEIVED_TEXT -> (holder as ReceivedTextMessageViewHolder).bind(message, timestamp, connectedDeviceName)
            VIEW_TYPE_SENT_FILE -> (holder as SentFileViewHolder).bind(message, timestamp, onFileClick)
            VIEW_TYPE_RECEIVED_FILE -> (holder as ReceivedFileViewHolder).bind(message, timestamp, connectedDeviceName, onFileClick)
            VIEW_TYPE_SYSTEM -> (holder as SystemMessageViewHolder).bind(message)
        }
    }

    override fun getItemCount(): Int = messages.size

    // region ViewHolders
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

            // Helper function to convert dp to px
            fun dpToPx(context: Context, dp: Int): Int {
                return TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    dp.toFloat(),
                    context.resources.displayMetrics
                ).toInt()
            }

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

            // Helper function to convert dp to px
            fun dpToPx(context: Context, dp: Int): Int {
                return TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    dp.toFloat(),
                    context.resources.displayMetrics
                ).toInt()
            }

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
        private val systemText: TextView = itemView.findViewById(R.id.systemText)
        fun bind(message: ChatMessage) {
            systemText.text = message.text
        }
    }
    // endregion ViewHolders
}
