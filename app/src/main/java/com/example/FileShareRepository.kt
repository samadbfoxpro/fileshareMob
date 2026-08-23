package com.example

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object CryptoHelper {
    fun encrypt(text: String): String {
        return text
    }

    fun decrypt(encryptedText: String): String {
        return encryptedText
    }
}

data class Message(
    val id: Long,
    val from: String,
    val text: String,
    val created: String,
    val edited: String? = null,
    val status: String? = "read", // "sent", "delivered", "read"
    val senderId: String? = null,
    val chatId: String? = null,
    val isEncrypted: Boolean = false,
    val replyToId: Long? = null,
    val replyToText: String? = null,
    val replyToUser: String? = null
) {
    fun getDecryptedText(): String {
        return text
    }

    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("from", from)
        obj.put("text", text)
        obj.put("created", created)
        obj.put("edited", edited ?: JSONObject.NULL)
        obj.put("status", status ?: "read")
        obj.put("senderId", senderId ?: JSONObject.NULL)
        obj.put("chatId", chatId ?: JSONObject.NULL)
        obj.put("isEncrypted", isEncrypted)
        obj.put("replyToId", replyToId ?: JSONObject.NULL)
        obj.put("replyToText", replyToText ?: JSONObject.NULL)
        obj.put("replyToUser", replyToUser ?: JSONObject.NULL)
        return obj
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): Message {
            return Message(
                id = obj.getLong("id"),
                from = obj.getString("from"),
                text = obj.getString("text"),
                created = obj.getString("created"),
                edited = if (obj.isNull("edited")) null else obj.getString("edited"),
                status = if (obj.has("status")) obj.getString("status") else "read",
                senderId = if (obj.has("senderId") && !obj.isNull("senderId")) obj.getString("senderId") else null,
                chatId = if (obj.has("chatId") && !obj.isNull("chatId")) obj.getString("chatId") else null,
                isEncrypted = if (obj.has("isEncrypted")) obj.getBoolean("isEncrypted") else false,
                replyToId = if (obj.has("replyToId") && !obj.isNull("replyToId")) obj.getLong("replyToId") else null,
                replyToText = if (obj.has("replyToText") && !obj.isNull("replyToText")) obj.getString("replyToText") else null,
                replyToUser = if (obj.has("replyToUser") && !obj.isNull("replyToUser")) obj.getString("replyToUser") else null
            )
        }
    }
}

data class FileItem(
    val name: String,
    val size: Long,
    val modified: String, // ISO 8601
    val source: String,   // "upload" or "shared"
    val canDelete: Boolean
)

class FileShareRepository(private val context: Context) {

    companion object {
        @Volatile
        var isChatScreenActive: Boolean = false
    }

    private val baseDir: File by lazy {
        // Try public Downloads directory first as requested by user
        val downloadsDir = try {
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        } catch (e: Exception) {
            null
        }
        val appDir = if (downloadsDir != null) File(downloadsDir, "FileShare") else null
        try {
            if (appDir != null) {
                if (!appDir.exists()) {
                    appDir.mkdirs()
                }
                if (appDir.exists() && appDir.canWrite()) {
                    return@lazy appDir
                }
            }
        } catch (e: Exception) {
            // ignore and try fallback
        }

        // Fallback to external files dir
        try {
            val extFilesDir = context.getExternalFilesDir(null)
            if (extFilesDir != null) {
                val fallbackDir = File(extFilesDir, "FileShare")
                if (!fallbackDir.exists()) fallbackDir.mkdirs()
                if (fallbackDir.exists() && fallbackDir.canWrite()) {
                    return@lazy fallbackDir
                }
            }
        } catch (e: Exception) {
            // ignore
        }

        // Ultimate bulletproof fallback: Internal filesDir (always available and writable!)
        val internalDir = File(context.filesDir, "FileShare")
        try {
            if (!internalDir.exists()) {
                internalDir.mkdirs()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        internalDir
    }

    private val uploadsDir: File
        get() = getCustomUploadsDir()

    private val messagesFile: File by lazy {
        File(baseDir, "messages.jsonl")
    }

    private val sharedFolderTxt: File by lazy {
        File(baseDir, "shared-folder.txt")
    }

    init {
        // Initialize folders and files option with safety try-catch block
        try {
            if (!messagesFile.exists()) {
                messagesFile.createNewFile()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (!sharedFolderTxt.exists()) {
                sharedFolderTxt.createNewFile()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- SHARED FOLDER MANAGEMENT ---

    fun getSharedFolderUri(): String? {
        val sp = context.getSharedPreferences("FileSharePrefs", Context.MODE_PRIVATE)
        val prefUri = sp.getString("shared_folder_uri", null)
        if (!prefUri.isNullOrBlank()) return prefUri

        return try {
            if (!sharedFolderTxt.exists()) return null
            val content = sharedFolderTxt.readText().trim()
            if (content.isEmpty()) null else content
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun setSharedFolderUri(uriString: String?) {
        val sp = context.getSharedPreferences("FileSharePrefs", Context.MODE_PRIVATE)
        sp.edit().putString("shared_folder_uri", uriString).apply()
        try {
            sharedFolderTxt.writeText(uriString ?: "")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getSharedFolderName(): String? {
        val uriStr = getSharedFolderUri() ?: return null
        return try {
            val treeUri = Uri.parse(uriStr)
            val docFile = DocumentFile.fromTreeUri(context, treeUri)
            docFile?.name ?: "پوشه اشتراکی"
        } catch (e: Throwable) {
            "خطا در خواندن نام پوشه"
        }
    }

    // --- FILE OPERATIONS ---

    fun getFilesList(): List<FileItem> {
        val list = mutableListOf<FileItem>()

        // 1. Add uploads
        val uploadFiles = try {
            uploadsDir.listFiles() ?: emptyArray()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyArray()
        }
        for (f in uploadFiles) {
            if (f.isFile) {
                list.add(
                    FileItem(
                        name = f.name,
                        size = f.length(),
                        modified = formatIso8601(f.lastModified()),
                        source = "upload",
                        canDelete = true
                    )
                )
            }
        }

        // 2. Add shared folders files (via reliable SAF DocumentFile and DocumentsContract API)
        val sharedUriStr = getSharedFolderUri()
        if (!sharedUriStr.isNullOrEmpty()) {
            try {
                val treeUri = Uri.parse(sharedUriStr)
                val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
                if (treeDoc != null && treeDoc.exists() && treeDoc.isDirectory) {
                    val files = treeDoc.listFiles()
                    for (doc in files) {
                        if (doc.isFile && !doc.name.isNullOrEmpty()) {
                            list.add(
                                FileItem(
                                    name = doc.name ?: "file",
                                    size = doc.length(),
                                    modified = formatIso8601(doc.lastModified()),
                                    source = "shared",
                                    canDelete = false
                                )
                            )
                        }
                    }
                } else {
                    // Fallback to DocumentsContract cursor query
                    val docId = try {
                        DocumentsContract.getTreeDocumentId(treeUri)
                    } catch (e: Exception) {
                        try {
                            DocumentsContract.getDocumentId(treeUri)
                        } catch (e2: Exception) {
                            null
                        }
                    }
                    if (docId != null) {
                        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
                        val projection = arrayOf(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_SIZE,
                            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                            DocumentsContract.Document.COLUMN_MIME_TYPE
                        )
                        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                            val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                            val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                            val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                            
                            while (cursor.moveToNext()) {
                                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                                val size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
                                val modified = if (modifiedIndex >= 0) cursor.getLong(modifiedIndex) else 0L
                                val mimeType = if (mimeIndex >= 0) cursor.getString(mimeIndex) else null
                                
                                val isDir = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
                                if (!isDir && !name.isNullOrEmpty() && list.none { it.name == name && it.source == "shared" }) {
                                    list.add(
                                        FileItem(
                                            name = name,
                                            size = size,
                                            modified = formatIso8601(modified),
                                            source = "shared",
                                            canDelete = false
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FileShare", "Failed to list shared files: ${e.message}")
                e.printStackTrace()
            }
        }

        // Sort by name
        list.sortBy { it.name.lowercase() }
        return list
    }

    fun sanitizeFilename(filename: String): String {
        // Strip out any path characters to prevent trajectory traversal
        val nameOnly = File(filename).name
        // Replace invalid filesystem characters (\ / : * ? " < > | \x00) with underscores
        return nameOnly.replace(Regex("[\\\\/:*?\"<>|\\x00]"), "_")
    }

    fun getUniqueUploadFile(originalFilename: String): File {
        val sanitized = sanitizeFilename(originalFilename)
        var file = File(uploadsDir, sanitized)
        if (!file.exists()) return file

        val nameWithoutExt: String
        val extension: String
        val dotIndex = sanitized.lastIndexOf('.')
        if (dotIndex > 0) {
            nameWithoutExt = sanitized.substring(0, dotIndex)
            extension = sanitized.substring(dotIndex)
        } else {
            nameWithoutExt = sanitized
            extension = ""
        }

        var counter = 1
        while (file.exists()) {
            val newName = "$nameWithoutExt-$counter$extension"
            file = File(uploadsDir, newName)
            counter++
        }
        return file
    }

    fun getUploadFileStream(filename: String): InputStream? {
        val sanitized = sanitizeFilename(filename)
        val file = File(uploadsDir, sanitized)
        return if (file.exists() && file.isFile) FileInputStream(file) else null
    }

    fun deleteUploadFile(filename: String): Boolean {
        val sanitized = sanitizeFilename(filename)
        val file = File(uploadsDir, sanitized)
        return if (file.exists() && file.isFile) {
            file.delete()
        } else {
            false
        }
    }

    fun renameUploadFile(oldName: String, newNameStr: String): Boolean {
        val sanitizedOld = sanitizeFilename(oldName)
        val sanitizedNew = sanitizeFilename(newNameStr)
        val oldFile = File(uploadsDir, sanitizedOld)
        val newFile = File(uploadsDir, sanitizedNew)
        if (oldFile.exists() && oldFile.isFile && !newFile.exists()) {
            return oldFile.renameTo(newFile)
        }
        return false
    }

    fun getSharedFileStream(filename: String): InputStream? {
        val sharedUriStr = getSharedFolderUri() ?: return null
        return try {
            val treeUri = Uri.parse(sharedUriStr)
            
            // 1. Try DocumentFile directly
            val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
            val fileDoc = treeDoc?.findFile(filename) 
                ?: treeDoc?.listFiles()?.firstOrNull { it.name == filename || it.name.equals(filename, ignoreCase = true) }
            
            if (fileDoc != null && fileDoc.exists() && fileDoc.isFile) {
                val stream = context.contentResolver.openInputStream(fileDoc.uri)
                if (stream != null) return stream
            }

            // 2. Fallback to DocumentsContract query
            val docId = try {
                DocumentsContract.getTreeDocumentId(treeUri)
            } catch (e: Exception) {
                try {
                    DocumentsContract.getDocumentId(treeUri)
                } catch (e2: Exception) {
                    null
                }
            } ?: return null
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )
            var fileUri: Uri? = null
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    if (name == filename || name.equals(filename, ignoreCase = true)) {
                        val fileId = if (idIndex >= 0) cursor.getString(idIndex) else null
                        if (fileId != null) {
                            fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, fileId)
                        }
                        break
                    }
                }
            }
            if (fileUri != null) {
                context.contentResolver.openInputStream(fileUri!!)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getSharedFileLength(filename: String): Long {
        val sharedUriStr = getSharedFolderUri() ?: return -1L
        return try {
            val treeUri = Uri.parse(sharedUriStr)
            
            // 1. Try DocumentFile length
            val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
            val fileDoc = treeDoc?.findFile(filename) 
                ?: treeDoc?.listFiles()?.firstOrNull { it.name == filename || it.name.equals(filename, ignoreCase = true) }
            
            if (fileDoc != null && fileDoc.exists() && fileDoc.isFile) {
                val len = fileDoc.length()
                if (len > 0) return len
            }

            // 2. Fallback to DocumentsContract query
            val docId = try {
                DocumentsContract.getTreeDocumentId(treeUri)
            } catch (e: Exception) {
                try {
                    DocumentsContract.getDocumentId(treeUri)
                } catch (e2: Exception) {
                    null
                }
            } ?: return -1L
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE
            )
            var length = -1L
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                while (cursor.moveToNext()) {
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    if (name == filename || name.equals(filename, ignoreCase = true)) {
                        length = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else -1L
                        break
                    }
                }
            }
            length
        } catch (e: Exception) {
            -1L
        }
    }

    fun saveUpload(tempFile: File, originalFilename: String): File {
        val destFile = getUniqueUploadFile(originalFilename)
        try {
            tempFile.inputStream().use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 128 * 1024) // 128KB buffer for maximum local network speed
                }
            }
            tempFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return destFile
    }

    // --- MESSAGING OPERATIONS ---

    @Synchronized
    fun getAllMessages(): List<Message> {
        val list = mutableListOf<Message>()
        if (!messagesFile.exists()) return list

        try {
            BufferedReader(InputStreamReader(FileInputStream(messagesFile), "UTF-8")).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line!!.trim()
                    if (trimmed.isEmpty()) continue
                    try {
                        val obj = JSONObject(trimmed)
                        list.add(Message.fromJsonObject(obj))
                    } catch (e: Exception) {
                        // Resilient check: Skip broken json lines
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    @Synchronized
    fun getMessagesAfter(id: Long): List<Message> {
        return getAllMessages().filter { it.id > id }
    }

    @Synchronized
    fun addMessage(
        from: String,
        text: String,
        senderId: String? = null,
        chatId: String? = null,
        isEncrypted: Boolean = false,
        alreadyEncrypted: Boolean = false,
        isPeerOnline: Boolean = true,
        replyToId: Long? = null,
        replyToText: String? = null,
        replyToUser: String? = null
    ): Message {
        val id = System.currentTimeMillis() + (0..999).random()
        val created = getCurrentIso8601()
        val isFromMe = from.contains("مدیر") || from.contains("گوشی") || (senderId != null && senderId == getClientUserId())
        
        // If client sends to host and host is opening the chat, marked as read, otherwise delivered.
        // If host sends, it starts as pending (if peer is offline) or delivered (if online), and will be read when client polls.
        val initStatus = if (isFromMe) {
            if (isPeerOnline) "delivered" else "pending"
        } else {
            if (isChatScreenActive) "read" else "delivered"
        }
        
        val finalTxt = if (isEncrypted && !alreadyEncrypted) CryptoHelper.encrypt(text) else text
        val message = Message(
            id = id,
            from = from,
            text = finalTxt,
            created = created,
            edited = null,
            status = initStatus,
            senderId = senderId,
            chatId = chatId,
            isEncrypted = isEncrypted,
            replyToId = replyToId,
            replyToText = replyToText,
            replyToUser = replyToUser
        )

        saveMessageLine(message)

        if (!isChatScreenActive && !isFromMe) {
            triggerMessageNotification(from, text)
        }

        return message
    }

    @Synchronized
    fun markAllMessagesAsRead() {
        val messages = getAllMessages()
        var changed = false
        val updated = messages.map {
            if (!it.from.contains("مدیر") && !it.from.contains("گوشی") && it.status == "delivered") {
                changed = true
                it.copy(status = "read")
            } else {
                it
            }
        }
        if (changed) {
            rewriteMessagesFile(updated)
        }
    }

    @Synchronized
    fun markHostMessagesAsRead() {
        val messages = getAllMessages()
        var changed = false
        val updated = messages.map {
            if ((it.from.contains("مدیر") || it.from.contains("گوشی") || it.senderId == "host_admin") && 
                (it.status == "delivered" || it.status == "pending")) {
                changed = true
                it.copy(status = "read")
            } else {
                it
            }
        }
        if (changed) {
            rewriteMessagesFile(updated)
        }
    }

    @Synchronized
    fun syncAndMergeMessages(clientMsgs: List<Message>): List<Message> {
        val localMsgs = getAllMessages().toMutableList()
        val localIds = localMsgs.map { it.id }.toSet()
        var changed = false
        
        for (m in clientMsgs) {
            if (!localIds.contains(m.id)) {
                val decrypted = m.getDecryptedText()
                if (!decrypted.startsWith("📎 فایل:") && !decrypted.contains("📎 فایل")) {
                    localMsgs.add(m)
                    changed = true
                }
            }
        }
        
        if (changed) {
            localMsgs.sortBy { it.id }
            rewriteMessagesFile(localMsgs)
        }
        return localMsgs
    }

    private fun triggerMessageNotification(sender: String, messageText: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channelId = "LocalChatChannel"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "پیام‌های چت محلی",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "اطلاع‌رسانی پیام‌های دریافتی جدید"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val activityIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("select_tab", "messages")
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                1102,
                activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, channelId)
                .setContentTitle("پیام جدید از: $sender")
                .setContentText(messageText)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(1105, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun editMessage(id: Long, from: String, text: String): Message? {
        val all = getAllMessages().toMutableList()
        val index = all.indexOfFirst { it.id == id }
        if (index == -1) return null

        val original = all[index]
        val updated = original.copy(
            from = from,
            text = text,
            edited = getCurrentIso8601()
        )
        all[index] = updated

        rewriteMessagesFile(all)
        return updated
    }

    @Synchronized
    fun deleteMessage(id: Long): Boolean {
        val all = getAllMessages().toMutableList()
        val removed = all.removeAll { it.id == id }
        if (removed) {
            rewriteMessagesFile(all)
        }
        return removed
    }

    @Synchronized
    fun deleteMessages(ids: List<Long>): Boolean {
        val all = getAllMessages().toMutableList()
        val removed = all.removeAll { it.id in ids }
        if (removed) {
            rewriteMessagesFile(all)
        }
        return removed
    }

    @Synchronized
    fun deleteAllMessages() {
        rewriteMessagesFile(emptyList())
    }

    @Synchronized
    fun getMessagesFormattedText(): String {
        val all = getAllMessages()
        val sb = java.lang.StringBuilder()
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        for (m in all) {
            val dateStr = try {
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                isoFormat.timeZone = TimeZone.getTimeZone("UTC")
                val parsedDate = isoFormat.parse(m.created)
                if (parsedDate != null) df.format(parsedDate) else m.created
            } catch (e: Exception) {
                m.created
            }
            sb.append("[").append(dateStr).append(" (").append(m.from).append(")")
            if (m.edited != null) {
                sb.append(" (ویرایش شده)")
            }
            sb.append("]\n").append(m.text).append("\n\n")
        }
        return sb.toString()
    }

    private fun saveMessageLine(message: Message) {
        try {
            FileOutputStream(messagesFile, true).use { out ->
                val line = message.toJsonObject().toString() + "\n"
                out.write(line.toByteArray(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun rewriteMessagesFile(messages: List<Message>) {
        try {
            FileOutputStream(messagesFile, false).use { out ->
                for (m in messages) {
                    val line = m.toJsonObject().toString() + "\n"
                    out.write(line.toByteArray(Charsets.UTF_8))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- HELPERS ---

    private fun formatIso8601(epochMs: Long): String {
        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        df.timeZone = TimeZone.getTimeZone("UTC")
        return df.format(Date(epochMs))
    }

    private fun getCurrentIso8601(): String {
        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        df.timeZone = TimeZone.getTimeZone("UTC")
        return df.format(Date())
    }

    fun getUploadsDirectoryPath(): String {
        return uploadsDir.absolutePath
    }

    fun getClientTargetIp(): String {
        val sp = context.getSharedPreferences("FileShareClientPrefs", Context.MODE_PRIVATE)
        return sp.getString("target_ip", "") ?: ""
    }

    fun setClientTargetIp(ip: String) {
        val sp = context.getSharedPreferences("FileShareClientPrefs", Context.MODE_PRIVATE)
        sp.edit().putString("target_ip", ip).apply()
    }

    fun getClientNickname(): String {
        val sp = context.getSharedPreferences("FileShareClientPrefs", Context.MODE_PRIVATE)
        if (isSetupCompleted()) {
            val name = sp.getString("username_id", "") ?: ""
            if (name.isNotEmpty()) {
                return name
            }
        }
        return sp.getString("nickname", android.os.Build.MODEL) ?: android.os.Build.MODEL
    }

    fun setClientNickname(nickname: String) {
        val sp = context.getSharedPreferences("FileShareClientPrefs", Context.MODE_PRIVATE)
        sp.edit()
            .putString("nickname", nickname.trim())
            .putString("username_id", nickname.trim())
            .apply()
    }

    // --- TRUSTED PEERS MANAGEMENT ---

    // --- CUSTOMIZABLE UPLOADS DIR ---
    fun getCustomUploadsDir(): File {
        val sp = context.getSharedPreferences("FileShareClientPrefs", Context.MODE_PRIVATE)
        val customPath = sp.getString("custom_uploads_path", "") ?: ""
        if (customPath.isNotEmpty()) {
            val dir = File(customPath)
            if (dir.exists() && dir.isDirectory) {
                return dir
            }
        }
        val dir = File(baseDir, "upload")
        try {
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        } catch (e: Exception) {
            e.printStackTrace()
            val fallback = File(context.filesDir, "upload")
            try {
                if (!fallback.exists()) fallback.mkdirs()
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            return fallback
        }
    }

    fun setCustomUploadsDir(path: String): Boolean {
        val dir = File(path)
        if (dir.exists() && dir.isDirectory) {
            val sp = context.getSharedPreferences("FileShareClientPrefs", Context.MODE_PRIVATE)
            sp.edit().putString("custom_uploads_path", path).apply()
            return true
        }
        return false
    }

    // --- CLIENT UNIQUE IDENTITY/PROFILE ---
    fun getClientUserId(): String {
        val sp = context.getSharedPreferences("FileShareClientPrefs", Context.MODE_PRIVATE)
        val existing = sp.getString("username_id", "") ?: ""
        if (existing.trim().isEmpty()) {
            val androidId = try {
                android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            } catch (e: Exception) {
                null
            } ?: java.util.UUID.randomUUID().toString()
            sp.edit().putString("username_id", androidId).apply()
            return androidId
        }
        return existing
    }

    fun setClientUserId(userId: String) {
        val sp = context.getSharedPreferences("FileShareClientPrefs", Context.MODE_PRIVATE)
        sp.edit()
            .putString("username_id", userId.trim())
            .putString("nickname", userId.trim())
            .apply()
    }

    fun isSetupCompleted(): Boolean {
        val sp = context.getSharedPreferences("FileShareClientPrefs", Context.MODE_PRIVATE)
        return sp.getBoolean("is_setup_completed", false)
    }

    fun setSetupCompleted(completed: Boolean) {
        val sp = context.getSharedPreferences("FileShareClientPrefs", Context.MODE_PRIVATE)
        sp.edit().putBoolean("is_setup_completed", completed).apply()
    }

    fun getClientAvatar(): String {
        val sp = context.getSharedPreferences("FileShareClientPrefs", Context.MODE_PRIVATE)
        return sp.getString("avatar", "🧑‍💻") ?: "🧑‍💻"
    }

    fun setClientAvatar(avatar: String) {
        val sp = context.getSharedPreferences("FileShareClientPrefs", Context.MODE_PRIVATE)
        sp.edit().putString("avatar", avatar).apply()
    }

    // --- TRUSTED PEERS MANAGEMENT ---

    fun getTrustedPeers(): List<TrustedPeer> {
        val sp = context.getSharedPreferences("FileShareClientPrefs", Context.MODE_PRIVATE)
        val jsonStr = sp.getString("trusted_peers_list", "[]") ?: "[]"
        val list = mutableListOf<TrustedPeer>()
        try {
            val array = org.json.JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    TrustedPeer(
                        ip = obj.getString("ip"),
                        nickname = obj.getString("nickname"),
                        mode = obj.optString("mode", "two_way"),
                        userId = if (obj.has("userId") && !obj.isNull("userId")) obj.getString("userId") else null,
                        avatar = if (obj.has("avatar") && !obj.isNull("avatar")) obj.getString("avatar") else null
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveTrustedPeers(list: List<TrustedPeer>) {
        val sp = context.getSharedPreferences("FileShareClientPrefs", Context.MODE_PRIVATE)
        val array = org.json.JSONArray()
        for (peer in list) {
            val obj = JSONObject()
            obj.put("ip", peer.ip)
            obj.put("nickname", peer.nickname)
            obj.put("mode", peer.mode)
            obj.put("userId", peer.userId ?: JSONObject.NULL)
            obj.put("avatar", peer.avatar ?: JSONObject.NULL)
            array.put(obj)
        }
        sp.edit().putString("trusted_peers_list", array.toString()).apply()
    }

    fun addTrustedPeer(ip: String, nickname: String, mode: String, userId: String? = null, avatar: String? = null) {
        val list = getTrustedPeers().toMutableList()
        if (userId != null && userId.isNotEmpty()) {
            list.removeAll { it.userId == userId }
        } else {
            list.removeAll { it.ip == ip && it.nickname == nickname }
        }
        list.add(TrustedPeer(ip, nickname, mode, userId, avatar))
        saveTrustedPeers(list)
    }

    fun removeTrustedPeer(ip: String, nickname: String) {
        val list = getTrustedPeers().toMutableList()
        list.removeAll { it.ip == ip && it.nickname == nickname }
        saveTrustedPeers(list)
        // Terminate the active session and block any further calls
        setActiveClientSession(ip, "denied")
        if (getClientTargetIp() == ip) {
            setClientTargetIp("")
        }
    }

    fun removeActiveClientSession(ip: String) {
        synchronized(activeSessions) {
            activeSessions.remove(ip)
        }
    }

    fun isPeerTrusted(ip: String, nickname: String): TrustedPeer? {
        val list = getTrustedPeers()
        return list.firstOrNull { it.ip == ip && it.nickname == nickname }
    }

    fun isPeerTrustedByUserId(userId: String): TrustedPeer? {
        val list = getTrustedPeers()
        return list.firstOrNull { it.userId == userId }
    }

    // --- ACTIVE CLIENT SESSIONS IN MEMORY ---
    private val activeSessions = mutableMapOf<String, String>()

    fun setActiveClientSession(ip: String, mode: String) {
        synchronized(activeSessions) {
            activeSessions[ip] = mode
        }
    }

    fun getActiveClientSessionMode(ip: String): String? {
        return synchronized(activeSessions) {
            activeSessions[ip]
        }
    }

    // --- CLIENT-SIDE MESSAGES CACHING & OFFLINE OUTBOX ---
    fun getClientCachedMessages(): List<Message> {
        val sp = context.getSharedPreferences("FileShareClientPrefs", Context.MODE_PRIVATE)
        val json = sp.getString("client_cached_messages", "[]") ?: "[]"
        return try {
            val array = org.json.JSONArray(json)
            val result = mutableListOf<Message>()
            for (i in 0 until array.length()) {
                result.add(Message.fromJsonObject(array.getJSONObject(i)))
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveClientCachedMessages(list: List<Message>) {
        val sp = context.getSharedPreferences("FileShareClientPrefs", Context.MODE_PRIVATE)
        try {
            val array = org.json.JSONArray()
            for (msg in list) {
                array.put(msg.toJsonObject())
            }
            sp.edit().putString("client_cached_messages", array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getClientOfflineOutbox(): List<Message> {
        val sp = context.getSharedPreferences("FileShareClientPrefs", Context.MODE_PRIVATE)
        val json = sp.getString("client_offline_outbox", "[]") ?: "[]"
        return try {
            val array = org.json.JSONArray(json)
            val result = mutableListOf<Message>()
            for (i in 0 until array.length()) {
                result.add(Message.fromJsonObject(array.getJSONObject(i)))
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveClientOfflineOutbox(list: List<Message>) {
        val sp = context.getSharedPreferences("FileShareClientPrefs", Context.MODE_PRIVATE)
        try {
            val array = org.json.JSONArray()
            for (msg in list) {
                array.put(msg.toJsonObject())
            }
            sp.edit().putString("client_offline_outbox", array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

data class TrustedPeer(
    val ip: String,
    val nickname: String,
    val mode: String, // "one_way" or "two_way"
    val userId: String? = null,
    val avatar: String? = null,
    val isOnline: Boolean = false
)

