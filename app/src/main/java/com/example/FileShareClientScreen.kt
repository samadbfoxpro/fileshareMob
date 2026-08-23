package com.example

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import okio.BufferedSink
import okio.ForwardingSink
import okio.Okio
import okio.Buffer
import okio.buffer

class ProgressRequestBody(
    private val delegate: okhttp3.RequestBody,
    private val isCanceled: () -> Boolean = { false },
    private val onProgressUpdate: (Int) -> Unit
) : okhttp3.RequestBody() {
    override fun contentType() = delegate.contentType()
    override fun contentLength() = delegate.contentLength()

    override fun writeTo(sink: okio.BufferedSink) {
        val totalBytes = contentLength()
        var bytesWritten = 0L

        val countingSink = object : okio.ForwardingSink(sink) {
            override fun write(source: okio.Buffer, byteCount: Long) {
                if (isCanceled()) {
                    throw java.io.IOException("Canceled")
                }
                super.write(source, byteCount)
                bytesWritten += byteCount
                if (totalBytes > 0) {
                    val percent = ((bytesWritten * 100) / totalBytes).toInt()
                    onProgressUpdate(percent.coerceIn(0, 100))
                }
            }
        }
        val bufferedSink = countingSink.buffer()
        delegate.writeTo(bufferedSink)
        bufferedSink.flush()
    }
}

object ClientNetworkManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun getLocalWifiIp(): String {
        try {
            val interfaces = java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())
            for (iface in interfaces) {
                val addrs = java.util.Collections.list(iface.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress ?: ""
                        if (!sAddr.contains(":")) { // Only IPv4
                            return sAddr
                        }
                    }
                }
            }
        } catch (ex: java.lang.Exception) {
            ex.printStackTrace()
        }
        return ""
    }

    suspend fun registerPeer(
        hostIp: String,
        clientIp: String,
        clientNickname: String,
        clientUserId: String,
        clientAvatar: String,
        onHostInfoReceived: ((hostUserId: String, hostNickname: String, hostAvatar: String) -> Unit)? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val cleanIp = formatHostUrl(hostIp)
            val json = JSONObject()
            json.put("client_ip", clientIp)
            json.put("client_nickname", clientNickname)
            json.put("client_user_id", clientUserId)
            json.put("client_avatar", clientAvatar)

            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("$cleanIp/api/register_peer")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val resText = response.body?.string() ?: ""
                    if (resText.isNotEmpty()) {
                        val obj = JSONObject(resText)
                        val mode = obj.optString("mode", "two_way")
                        val hostUserId = obj.optString("host_user_id", "")
                        val hostNickname = obj.optString("host_nickname", "مدیر")
                        val hostAvatar = obj.optString("host_avatar", "⚙️")
                        if (hostUserId.isNotEmpty()) {
                            onHostInfoReceived?.invoke(hostUserId, hostNickname, hostAvatar)
                        }
                        mode
                    } else {
                        "two_way"
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun pingHost(hostIp: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val cleanIp = formatHostUrl(hostIp)
            val request = Request.Builder()
                .url("$cleanIp/api/network")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun fetchFiles(hostIp: String): List<FileItem> = withContext(Dispatchers.IO) {
        try {
            val cleanIp = formatHostUrl(hostIp)
            val request = Request.Builder()
                .url("$cleanIp/api/files")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: "[]"
                    val array = JSONArray(bodyStr)
                    val result = mutableListOf<FileItem>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        result.add(
                            FileItem(
                                name = obj.getString("name"),
                                size = obj.getLong("size"),
                                modified = obj.optString("modified", ""),
                                source = obj.optString("source", "upload"),
                                canDelete = obj.optBoolean("canDelete", false)
                            )
                        )
                    }
                    result
                } else emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun fetchMessages(hostIp: String): List<Message> = withContext(Dispatchers.IO) {
        try {
            val cleanIp = formatHostUrl(hostIp)
            val request = Request.Builder()
                .url("$cleanIp/api/messages")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: "[]"
                    val array = JSONArray(bodyStr)
                    val result = mutableListOf<Message>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        result.add(Message.fromJsonObject(obj))
                    }
                    result
                } else emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun sendMessage(
        hostIp: String,
        nickname: String,
        text: String,
        senderId: String,
        chatId: String,
        isEncrypted: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val cleanIp = formatHostUrl(hostIp)
            val finalTxt = if (isEncrypted) CryptoHelper.encrypt(text) else text
            
            val json = JSONObject()
            json.put("from", nickname)
            json.put("text", finalTxt)
            json.put("senderId", senderId)
            json.put("chatId", chatId)
            json.put("isEncrypted", isEncrypted)
            json.put("alreadyEncrypted", isEncrypted)

            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("$cleanIp/api/messages")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun syncMessages(
        hostIp: String,
        localCached: List<Message>,
        repository: FileShareRepository
    ): List<Message>? = withContext(Dispatchers.IO) {
        try {
            val cleanIp = formatHostUrl(hostIp)
            val textOnlyLocal = localCached.filter {
                val decrypted = it.getDecryptedText()
                !decrypted.startsWith("📎 فایل:") && !decrypted.contains("📎 فایل")
            }
            val array = JSONArray()
            for (msg in textOnlyLocal) {
                array.put(msg.toJsonObject())
            }
            val body = array.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("$cleanIp/api/messages/sync")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val resBody = response.body?.string() ?: "[]"
                    val resArray = JSONArray(resBody)
                    val result = mutableListOf<Message>()
                    for (i in 0 until resArray.length()) {
                        val obj = resArray.getJSONObject(i)
                        val msg = Message.fromJsonObject(obj)
                        val decrypted = msg.getDecryptedText()
                        val isMedia = decrypted.startsWith("📎 فایل:") || decrypted.contains("📎 فایل")
                        val idExistsLocally = localCached.any { it.id == msg.id }
                        if (!isMedia || idExistsLocally) {
                            result.add(msg)
                        }
                    }
                    result
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun deleteFile(hostIp: String, filename: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val cleanIp = formatHostUrl(hostIp)
            val request = Request.Builder()
                .url("$cleanIp/api/files/${Uri.encode(filename)}")
                .delete()
                .build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun uploadFile(hostIp: String, file: File, originalName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val cleanIp = formatHostUrl(hostIp)
            val fileBody = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", originalName, fileBody)
                .build()

            val request = Request.Builder()
                .url("$cleanIp/upload")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun uploadFileWithProgress(
        hostIp: String,
        file: File,
        originalName: String,
        isCanceled: () -> Boolean = { false },
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val cleanIp = formatHostUrl(hostIp)
            val fileBody = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", originalName, fileBody)
                .build()

            val progressBody = ProgressRequestBody(multipartBody, isCanceled, onProgress)

            val request = Request.Builder()
                .url("$cleanIp/upload")
                .post(progressBody)
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getRemoteUploadedSize(hostIp: String, filename: String): Long = withContext(Dispatchers.IO) {
        try {
            val cleanIp = formatHostUrl(hostIp)
            val request = Request.Builder()
                .url("$cleanIp/api/upload/resume?filename=${android.net.Uri.encode(filename)}")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val obj = JSONObject(bodyStr)
                    obj.optLong("uploadedBytes", 0L)
                } else {
                    0L
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0L
        }
    }

    suspend fun uploadFileInChunks(
        hostIp: String,
        file: File,
        filename: String,
        forceRestart: Boolean,
        isCanceled: () -> Boolean = { false },
        onProgress: (Long, Long, String) -> Unit // uploaded, total, state
    ): String = withContext(Dispatchers.IO) { // returns "success", "failed", "canceled"
        try {
            val cleanIp = formatHostUrl(hostIp)
            val totalSize = file.length()
            if (totalSize == 0L) {
                return@withContext "failed"
            }
            
            val rawOffset = if (forceRestart) 0L else getRemoteUploadedSize(hostIp, filename)
            val offset = if (rawOffset >= totalSize) 0L else rawOffset
            
            val chunkSize = 1024 * 1024 * 1L // 1MB chunk size
            val buffer = ByteArray(chunkSize.toInt())
            
            java.io.RandomAccessFile(file, "r").use { raf ->
                raf.seek(offset)
                var currentOffset = offset
                
                while (currentOffset < totalSize) {
                    if (isCanceled()) {
                        return@withContext "canceled"
                    }
                    
                    val remaining = totalSize - currentOffset
                    val toRead = if (remaining < chunkSize) remaining.toInt() else chunkSize.toInt()
                    
                    raf.readFully(buffer, 0, toRead)
                    
                    val requestBody = buffer.toRequestBody("application/octet-stream".toMediaTypeOrNull(), 0, toRead)
                    
                    val url = "$cleanIp/api/upload/chunk?filename=${android.net.Uri.encode(filename)}&offset=$currentOffset"
                    val request = Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .build()
                        
                    var tryCount = 0
                    var success = false
                    while (tryCount < 3 && !success && !isCanceled()) {
                        try {
                            client.newCall(request).execute().use { response ->
                                if (response.isSuccessful) {
                                    val bodyStr = response.body?.string() ?: ""
                                    val obj = JSONObject(bodyStr)
                                    val written = obj.optInt("bytesWritten", 0)
                                    if (written == toRead) {
                                        currentOffset += toRead
                                        success = true
                                        onProgress(currentOffset, totalSize, "Uploading")
                                    } else {
                                        tryCount++
                                        delay(1000)
                                    }
                                } else {
                                    tryCount++
                                    delay(1000)
                                }
                            }
                        } catch (e: Exception) {
                            tryCount++
                            delay(1000)
                        }
                    }
                    
                    if (!success) {
                        return@withContext "failed"
                    }
                }
            }
            
            return@withContext "success"
        } catch (e: Exception) {
            e.printStackTrace()
            "failed"
        }
    }

    suspend fun deleteMessage(hostIp: String, messageId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val cleanIp = formatHostUrl(hostIp)
            val request = Request.Builder()
                .url("$cleanIp/api/messages/$messageId")
                .delete()
                .build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun downloadFile(hostIp: String, fileItem: FileItem, localDir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val cleanIp = formatHostUrl(hostIp)
            val url = if (fileItem.source == "shared") {
                "$cleanIp/download/shared/${Uri.encode(fileItem.name)}"
            } else {
                "$cleanIp/download/${Uri.encode(fileItem.name)}"
            }

            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body ?: return@withContext false
                    if (!localDir.exists()) localDir.mkdirs()
                    val targetFile = File(localDir, fileItem.name)
                    FileOutputStream(targetFile).use { output ->
                        body.byteStream().copyTo(output)
                    }
                    true
                } else false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun notifyDisconnect(hostIp: String, clientIp: String, clientNickname: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val cleanIp = formatHostUrl(hostIp)
            val json = JSONObject()
            json.put("client_ip", clientIp)
            json.put("client_nickname", clientNickname)

            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("$cleanIp/api/disconnect")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun formatHostUrl(input: String): String {
        var clean = input.trim()
        if (clean.isBlank()) return ""
        // Ensure protocol exists
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "http://$clean"
        }
        // Ensure port exists
        val lastColon = clean.lastIndexOf(':')
        val hasPort = lastColon > 5 // Greater than 'http:' colon index
        if (!hasPort) {
            clean = "$clean:8886"
        }
        return clean
    }

    fun cleanIpInput(input: String): String {
        var clean = input.trim()
        if (clean.startsWith("http://")) {
            clean = clean.substring(7)
        }
        if (clean.startsWith("https://")) {
            clean = clean.substring(8)
        }
        if (clean.endsWith("/")) {
            clean = clean.dropLast(1)
        }
        return clean
    }
}

fun copyUriToTempFile(context: Context, uri: Uri): File? {
    return try {
        val contentResolver = context.contentResolver
        val cursor = contentResolver.query(uri, null, null, null, null)
        val name = if (cursor != null && cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1) cursor.getString(nameIndex) else "upload_temp"
        } else {
            "upload_temp"
        }
        cursor?.close()

        val tempFile = File(context.cacheDir, name)
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        tempFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun FileShareClient(
    repository: FileShareRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Preferences & Settings States
    var targetIpInput by remember { mutableStateOf(repository.getClientTargetIp()) }
    var nicknameInput by remember(repository.getClientNickname()) { mutableStateOf(repository.getClientNickname()) }
    var trustedPeers by remember { mutableStateOf(repository.getTrustedPeers()) }
    LaunchedEffect(Unit) {
        trustedPeers = repository.getTrustedPeers()
    }

    var isConnected by remember { mutableStateOf(false) }
    var connectionMode by remember { mutableStateOf("two_way") } // "two_way" or "one_way"
    var isCheckingConnection by remember { mutableStateOf(false) }
    var userWantsConnection by remember { mutableStateOf(false) }

    // Tab control inside client: "messages" or "files"
    var clientSubTab by remember { mutableStateOf("messages") }

    // Data lists
    var hostMessages by remember { mutableStateOf<List<Message>>(repository.getClientCachedMessages()) }
    var hostFiles by remember { mutableStateOf<List<FileItem>>(emptyList()) }

    // Message input
    var messageText by remember { mutableStateOf("") }
    var isSendingMsg by remember { mutableStateOf(false) }

    // Chat file uploading states
    var isChatUploading by remember { mutableStateOf(false) }
    var chatUploadProgress by remember { mutableStateOf<Int?>(null) }
    var chatUploadFileName by remember { mutableStateOf("") }
    var chatUploadCanceled by remember { mutableStateOf(false) }

    val chatFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                isChatUploading = true
                chatUploadProgress = 0
                val tempFile = copyUriToTempFile(context, uri)
                if (tempFile != null && tempFile.exists()) {
                    chatUploadFileName = tempFile.name
                    val success = ClientNetworkManager.uploadFileWithProgress(
                        targetIpInput,
                        tempFile,
                        tempFile.name,
                        isCanceled = { chatUploadCanceled }
                    ) { progress ->
                        chatUploadProgress = progress
                    }
                    if (success) {
                        Toast.makeText(context, "فایل با موفقیت ارسال شد.", Toast.LENGTH_SHORT).show()
                        val author = nicknameInput.trim().ifEmpty { android.os.Build.MODEL }
                        val sizeStr = run {
                            val mb = tempFile.length().toDouble() / (1024.0 * 1024.0)
                            val kb = tempFile.length().toDouble() / 1024.0
                            if (mb >= 1.0) String.format("%.1f MB", mb) else String.format("%.1f KB", kb)
                        }
                        ClientNetworkManager.sendMessage(targetIpInput, author, "📎 فایل: ${tempFile.name} ($sizeStr)", repository.getClientUserId(), "host_admin")
                        hostMessages = ClientNetworkManager.fetchMessages(targetIpInput)
                    } else {
                        if (chatUploadCanceled) {
                            Toast.makeText(context, "ارسال فایل لغو شد.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "ارسال فایل ناموفق بود.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    tempFile.delete()
                } else {
                    Toast.makeText(context, "خطا در پردازش فایل.", Toast.LENGTH_SHORT).show()
                }
                isChatUploading = false
                chatUploadProgress = null
                chatUploadCanceled = false
            }
        }
    }

    // File Upload loading and resumable chunk tracking states
    var isUploadingFile by remember { mutableStateOf(false) }
    var chunkUploadProgress by remember { mutableStateOf(0f) }
    var chunkUploadFileName by remember { mutableStateOf("") }
    var chunkUploadStateText by remember { mutableStateOf("") } // "Uploading", "Success", "Failed", "Canceled"
    var chunkUploadCanceled by remember { mutableStateOf(false) }
    var chunkUploadedBytes by remember { mutableStateOf(0L) }
    var chunkTotalBytes by remember { mutableStateOf(0L) }
    var chunkUploadJob by remember { mutableStateOf<Job?>(null) }
    
    // Duplicate handler states inside Client
    var showDuplicateDialogClient by remember { mutableStateOf(false) }
    var duplicateFileClient by remember { mutableStateOf<File?>(null) }

    // P2P states
    var p2pProgress by remember { mutableStateOf<Int?>(null) }
    var p2pFileName by remember { mutableStateOf("") }
    var p2pFlashText by remember { mutableStateOf("") }
    var isP2pSending by remember { mutableStateOf(false) }

    // Dedicated P2P file transmitter launcher
    val p2pFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                isP2pSending = true
                p2pProgress = 0
                val tempFile = copyUriToTempFile(context, uri)
                if (tempFile != null && tempFile.exists()) {
                    p2pFileName = tempFile.name
                    val success = ClientNetworkManager.uploadFileWithProgress(
                        targetIpInput,
                        tempFile,
                        tempFile.name
                    ) { progress ->
                        p2pProgress = progress
                    }
                    if (success) {
                        Toast.makeText(context, "فایل با موفقیت به دستگاه مقصد ارسال شد!", Toast.LENGTH_SHORT).show()
                        hostFiles = ClientNetworkManager.fetchFiles(targetIpInput)
                    } else {
                        Toast.makeText(context, "ارسال فایل ناموفق بود.", Toast.LENGTH_SHORT).show()
                    }
                    tempFile.delete()
                } else {
                    Toast.makeText(context, "خطا در پردازش فایل انتخابی.", Toast.LENGTH_SHORT).show()
                }
                delay(1200)
                p2pProgress = null
                p2pFileName = ""
                isP2pSending = false
            }
        }
    }

    // QR Scanner Dialog
    var showQRScanner by remember { mutableStateOf(false) }

    // Helper function to initiate client-side chunked/resumable upload
    fun performChunkedUpload(file: File, filename: String, forceRestart: Boolean) {
        chunkUploadJob?.cancel()
        chunkUploadCanceled = false
        isUploadingFile = true
        chunkUploadFileName = filename
        chunkUploadStateText = "Uploading"
        chunkUploadedBytes = 0L
        chunkTotalBytes = file.length()
        chunkUploadProgress = 0f
        
        chunkUploadJob = coroutineScope.launch {
            val status = ClientNetworkManager.uploadFileInChunks(
                hostIp = targetIpInput,
                file = file,
                filename = filename,
                forceRestart = forceRestart,
                isCanceled = { chunkUploadCanceled },
                onProgress = { uploaded, total, state ->
                    chunkUploadedBytes = uploaded
                    chunkTotalBytes = total
                    chunkUploadProgress = if (total > 0) uploaded.toFloat() / total.toFloat() else 0f
                    chunkUploadStateText = state
                }
            )
            
            if (status == "success") {
                chunkUploadStateText = "Success"
                Toast.makeText(context, "فایل \"$filename\" با موفقیت آپلود شد.", Toast.LENGTH_SHORT).show()
                hostFiles = ClientNetworkManager.fetchFiles(targetIpInput)
                file.delete()
            } else if (status == "canceled") {
                chunkUploadStateText = "Canceled"
                Toast.makeText(context, "آپلود فایل لغو شد.", Toast.LENGTH_SHORT).show()
            } else {
                chunkUploadStateText = "Failed"
                Toast.makeText(context, "آپلود فایل ناموفق بود. اتصال شبکه را بررسی کنید.", Toast.LENGTH_SHORT).show()
            }
            // Keep status visible for a short time, then clear isUploadingFile
            delay(3000)
            if (chunkUploadStateText != "Uploading") {
                isUploadingFile = false
            }
        }
    }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val tempFile = copyUriToTempFile(context, uri)
            if (tempFile != null && tempFile.exists()) {
                val filename = tempFile.name
                val alreadyExists = hostFiles.any { it.name == filename }
                if (alreadyExists) {
                    duplicateFileClient = tempFile
                    showDuplicateDialogClient = true
                } else {
                    performChunkedUpload(tempFile, filename, forceRestart = false)
                }
            } else {
                Toast.makeText(context, "خطا در پردازش فایل انتخابی.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Ping host & verify state
    val onCheckConnection = {
        val ip = targetIpInput.trim()
        val cleaned = ClientNetworkManager.cleanIpInput(ip)
        if (cleaned.isBlank()) {
            Toast.makeText(context, "لطفا آی‌پی یا آدرس اتصال را وارد کنید.", Toast.LENGTH_SHORT).show()
        } else {
            isCheckingConnection = true
            coroutineScope.launch {
                val pingOk = ClientNetworkManager.pingHost(cleaned)
                if (pingOk) {
                    // Wait for remote safety approval!
                    Toast.makeText(context, "در حال ارسال درخواست اتصال و دریافت تاییدیه امنیت...", Toast.LENGTH_LONG).show()
                    
                    val myIp = ClientNetworkManager.getLocalWifiIp()
                    val myIpToSend = myIp.ifEmpty { "127.0.0.1" }
                    val finalNickname = nicknameInput.trim().ifEmpty { android.os.Build.MODEL }
                    val approvedMode = ClientNetworkManager.registerPeer(
                        hostIp = cleaned,
                        clientIp = myIpToSend,
                        clientNickname = finalNickname,
                        clientUserId = repository.getClientUserId(),
                        clientAvatar = repository.getClientAvatar(),
                        onHostInfoReceived = { hostUserId, hostNickname, hostAvatar ->
                            repository.addTrustedPeer(
                                ip = cleaned,
                                nickname = hostNickname,
                                mode = "two_way",
                                userId = hostUserId,
                                avatar = hostAvatar
                            )
                        }
                    )

                    if (approvedMode != null) {
                        connectionMode = approvedMode
                        userWantsConnection = true
                        isConnected = true
                        repository.setClientTargetIp(cleaned)
                        repository.setClientNickname(nicknameInput)
                        
                        if (approvedMode == "one_way") {
                            clientSubTab = "files" // Force files tab
                            Toast.makeText(context, "اتصال یک‌طرفه با موفقیت قرار شد (فقط ارسال فایل مجاز است)", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "اتصال امن دوطرفه با موفقیت برقرار شد!", Toast.LENGTH_SHORT).show()
                            // Initial load for two_way
                            hostMessages = ClientNetworkManager.fetchMessages(cleaned)
                            hostFiles = ClientNetworkManager.fetchFiles(cleaned)
                        }
                    } else {
                        userWantsConnection = false
                        isConnected = false
                        Toast.makeText(context, "درخواست اتصال توسط دستگاه مقابل رد شد یا زمان آن به پایان رسید.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    userWantsConnection = false
                    isConnected = false
                    Toast.makeText(context, "اتصال برقرار نشد. مطمئن شوید سرور در گوشی اول روشن است و به یک مودم متصل هستید.", Toast.LENGTH_LONG).show()
                }
                isCheckingConnection = false
            }
        }
    }

    // Initialize targetIp from shared prefs
    LaunchedEffect(Unit) {
        val savedIp = repository.getClientTargetIp()
        if (savedIp.isNotEmpty()) {
            targetIpInput = savedIp
            userWantsConnection = true
        }
    }

    // 1. Continuous SharedPreferences Observer Loop to implement "کافیه یکی از این دو گوشی اتصال رو برقرار کنه"
    LaunchedEffect(Unit) {
        while (true) {
            val savedIp = repository.getClientTargetIp()
            if (savedIp != targetIpInput && savedIp.isNotEmpty()) {
                targetIpInput = savedIp
                userWantsConnection = true
            }
            delay(1500)
        }
    }

    // 2. Active connection health check and data polling loop
    LaunchedEffect(targetIpInput, userWantsConnection) {
        var consecutiveFailures = 0
        while (true) {
            if (!userWantsConnection) {
                isConnected = false
                delay(2000)
                continue
            }
            val ip = targetIpInput.trim()
            if (ip.isNotEmpty()) {
                val pingOk = ClientNetworkManager.pingHost(ip)
                if (pingOk) {
                    consecutiveFailures = 0
                    if (!isConnected) {
                        isConnected = true
                    }
                    try {
                        if (connectionMode != "one_way") {
                            val cachedLocal = repository.getClientCachedMessages()
                            val mergedMsgs = ClientNetworkManager.syncMessages(ip, cachedLocal, repository)
                            val freshMsgs = if (mergedMsgs != null) {
                                mergedMsgs
                            } else {
                                ClientNetworkManager.fetchMessages(ip)
                            }
                            hostMessages = freshMsgs
                            repository.saveClientCachedMessages(freshMsgs)
                            hostFiles = ClientNetworkManager.fetchFiles(ip)

                            // AUTO-SYNC OFFLINE OUTBOX QUEUE!
                            val outbox = repository.getClientOfflineOutbox().toMutableList()
                            if (outbox.isNotEmpty()) {
                                val remaining = mutableListOf<Message>()
                                for (msg in outbox) {
                                    val sent = ClientNetworkManager.sendMessage(
                                        hostIp = ip,
                                        nickname = msg.from,
                                        text = msg.getDecryptedText(), // Decrypt because sendMessage will re-encrypt it!
                                        senderId = msg.senderId ?: repository.getClientUserId(),
                                        chatId = msg.chatId ?: "host_admin",
                                        isEncrypted = msg.isEncrypted
                                    )
                                    if (!sent) {
                                        remaining.add(msg)
                                    }
                                }
                                repository.saveClientOfflineOutbox(remaining)
                                // Fetch updated messages after sync
                                val syncedMsgs = ClientNetworkManager.fetchMessages(ip)
                                hostMessages = syncedMsgs
                                repository.saveClientCachedMessages(syncedMsgs)
                            }
                        } else {
                            hostMessages = emptyList()
                            hostFiles = emptyList()
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                } else {
                    consecutiveFailures++
                    if (consecutiveFailures >= 3) {
                        if (isConnected) {
                            isConnected = false
                        }
                    }
                }
            } else {
                isConnected = false
            }
            delay(3000)
        }
    }

    if (!isConnected) {
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Main Screen Header info
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2C34)),
                border = BorderStroke(1.dp, Color(0xFF2A3942))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(
                                text = "اتصال مستقیم دو گوشی",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "با وارد کردن آی‌پی گوشی اول، فایل و متن تبادل کنید.",
                                fontSize = 10.sp,
                                color = Color(0xFF8696A0)
                            )
                        }
                    }
                }
            }

            // Connection Setup Widget Panel
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2C34)),
                border = BorderStroke(1.dp, Color(0xFF2A3942))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "تنظیمات اتصال کلاینت",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00A884)
                    )

                    // Nickname Field
                    OutlinedTextField(
                        value = nicknameInput,
                        onValueChange = { nicknameInput = it },
                        label = { Text("نام کاربری شما") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00A884),
                            unfocusedBorderColor = Color(0xFF2A3942),
                            focusedLabelColor = Color(0xFF00A884),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            unfocusedLabelColor = Color(0xFF8696A0)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Target IP with scanning option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = targetIpInput,
                            onValueChange = { targetIpInput = it },
                            label = { Text("آی‌پی یا آدرس گوشی اول") },
                            placeholder = { Text("مثلا: 192.168.1.100") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00A884),
                                unfocusedBorderColor = Color(0xFF2A3942),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLabelColor = Color(0xFF00A884),
                                unfocusedLabelColor = Color(0xFF8696A0),
                                focusedPlaceholderColor = Color(0xFF8696A0),
                                unfocusedPlaceholderColor = Color(0xFF8696A0)
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        // Button to open scan dialog camera
                        IconButton(
                            onClick = { showQRScanner = true },
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF005C4B))
                        ) {
                            QrCodeIcon(
                                modifier = Modifier.size(24.dp),
                                color = Color(0xFF00A884)
                            )
                        }
                    }

                    // Connect/Disconnect Button Trigger
                    Button(
                        onClick = {
                            onCheckConnection()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00A884),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isCheckingConnection
                    ) {
                        if (isCheckingConnection) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        } else {
                            Text(
                                text = "بررسی و اتصال",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Status banner inline
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Color(0x33F87171),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFFF87171), shape = CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "قطع ارتباط با گوشی میزبان",
                            fontSize = 11.sp,
                            color = Color(0xFFF87171),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Trusted Peers List Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                border = BorderStroke(1.dp, Color(0xFF49454F))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "دستگاه‌های اعتماد شده (اتصال مستقیم بدون تایید)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD0BCFF)
                        )
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFFD0BCFF),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (trustedPeers.isEmpty()) {
                        Text(
                            text = "هنوز هیچ دستگاهی به لیست اعتماد اضافه نشده است. بعد از اولین اتصال موفق، دستگاه متصل شده به صورت خودکار در این لیست ذخیره می‌شود.",
                            fontSize = 10.sp,
                            color = Color(0xFF938F99),
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (peer in trustedPeers) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF1C1B1F), shape = RoundedCornerShape(12.dp))
                                        .border(BorderStroke(1.dp, Color(0xFF49454F)), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "📱 ${peer.nickname}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "آی‌پی: ${peer.ip}",
                                                fontSize = 9.sp,
                                                color = Color(0xFFCAC4D0)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        if (peer.mode == "two_way") Color(0x33B2F2BB) else Color(0x33FFB74D),
                                                        shape = RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (peer.mode == "two_way") "دو طرفه" else "یک طرفه (محدود)",
                                                    fontSize = 8.sp,
                                                    color = if (peer.mode == "two_way") Color(0xFFB2F2BB) else Color(0xFFFFB74D),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            if (targetIpInput == peer.ip) {
                                                targetIpInput = ""
                                                userWantsConnection = false
                                                isConnected = false
                                                hostMessages = emptyList()
                                                hostFiles = emptyList()
                                            }
                                            repository.removeTrustedPeer(peer.ip, peer.nickname)
                                            trustedPeers = repository.getTrustedPeers()
                                            Toast.makeText(context, "دستگاه از لیست دستگاه‌های مورد اعتماد حذف شد و اتصال با آن منقضی گردید.", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "حذف اعتماد",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Connected View!
        val isChatActive = (clientSubTab == "messages")
        Column(
            modifier = modifier
                .imePadding()
                .then(if (isChatActive) Modifier else Modifier.verticalScroll(rememberScrollState()))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // New Active-Connection Top Bar/Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2C34)),
                border = BorderStroke(1.dp, Color(0xFF2A3942))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color(0xFFB2F2BB), shape = CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (connectionMode == "one_way") "اتصال موفق یک‌طرفه 🔒" else "اتصال موفق دوطرفه 🤝",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (connectionMode == "one_way") Color(0xFFFFB74D) else Color(0xFF4CAF50)
                            )
                            Text(
                                text = "آی‌پی سرور: $targetIpInput",
                                fontSize = 10.sp,
                                color = Color(0xFF8696A0)
                            )
                        }
                    }
                    
                    // Small red disconnect button
                    Button(
                        onClick = {
                            val ipToNotify = targetIpInput
                            coroutineScope.launch {
                                try {
                                    val myIp = ClientNetworkManager.getLocalWifiIp()
                                    val myIpToSend = myIp.ifEmpty { "127.0.0.1" }
                                    val finalNickname = nicknameInput.trim().ifEmpty { android.os.Build.MODEL }
                                    ClientNetworkManager.notifyDisconnect(ipToNotify, myIpToSend, finalNickname)
                                } catch (e: Exception) {
                                    // ignore
                                }
                            }
                            repository.setClientTargetIp("")
                            targetIpInput = ""
                            userWantsConnection = false
                            isConnected = false
                            hostMessages = emptyList()
                            hostFiles = emptyList()
                            Toast.makeText(context, "اتصال قطع شد.", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "قطع اتصال",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "قطع اتصال",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Set subtab defaults adaptively based on connection mode
            if (connectionMode == "one_way") {
                LaunchedEffect(Unit) {
                    clientSubTab = "files"
                }
            } else {
                LaunchedEffect(Unit) {
                    clientSubTab = "messages"
                }
            }

            if (connectionMode == "one_way") {
                // Tabs indicator inside active screen: "چت" or "ارسال فایل"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1F2C34))
                        .border(BorderStroke(1.dp, Color(0xFF2A3942)), RoundedCornerShape(16.dp)),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val availableTabs = listOf(Pair("files", "📂 ارسال فایل"))
                    availableTabs.forEach { (tabId, label) ->
                        val clicked = clientSubTab == tabId
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (clicked) Color(0xFF00A884).copy(alpha = 0.15f) else Color.Transparent)
                                .clickable { clientSubTab = tabId }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (clicked) Color(0xFF00A884) else Color(0xFF8696A0),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Show appropriate content based on tab
            if (clientSubTab == "messages") {
                // Chats portal card
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2C34)),
                    border = BorderStroke(1.dp, Color(0xFF2A3942))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "صندوق پیام‌های مشترک (${hostMessages.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00A884)
                        )

                        // Floating Upload Progress Bar inside Client Chat
                        if (isChatUploading) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2C34)),
                                border = BorderStroke(1.dp, Color(0xFF2A3942))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text("⏳", fontSize = 18.sp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "در حال ارسال: $chatUploadFileName",
                                            fontSize = 12.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        if (chatUploadProgress != null) {
                                            LinearProgressIndicator(
                                                progress = { chatUploadProgress!!.toFloat() / 100f },
                                                modifier = Modifier.fillMaxWidth(),
                                                color = Color(0xFF00A884),
                                                trackColor = Color(0xFF2A3942)
                                            )
                                        } else {
                                            LinearProgressIndicator(
                                                modifier = Modifier.fillMaxWidth(),
                                                color = Color(0xFF00A884),
                                                trackColor = Color(0xFF2A3942)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${chatUploadProgress ?: 0}%",
                                        fontSize = 12.sp,
                                        color = Color(0xFF00A884),
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = {
                                            chatUploadCanceled = true
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Text("❌", fontSize = 14.sp)
                                    }
                                }
                            }
                        }

                        // Message sending field input
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📎",
                                fontSize = 20.sp,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .clickable {
                                        if (!isConnected) {
                                            Toast.makeText(context, "ارسال فایل و مدیا فقط زمانی مجاز است که هر دو کاربر آنلاین باشند.", Toast.LENGTH_LONG).show()
                                        } else {
                                            chatFilePickerLauncher.launch("*/*")
                                        }
                                    }
                            )

                            OutlinedTextField(
                                value = messageText,
                                onValueChange = { messageText = it },
                                placeholder = { Text("چیزی بنویسید...") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF00A884),
                                    unfocusedBorderColor = Color(0xFF2A3942),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedPlaceholderColor = Color(0xFF8696A0),
                                    unfocusedPlaceholderColor = Color(0xFF8696A0)
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(
                                onClick = {
                                    val text = messageText.trim()
                                    if (text.isNotBlank()) {
                                        isSendingMsg = true
                                        val author = nicknameInput.trim().ifEmpty { "ناشناس" }
                                        val myId = repository.getClientUserId()
                                        
                                        coroutineScope.launch {
                                            if (isConnected) {
                                                val sentOk = ClientNetworkManager.sendMessage(targetIpInput, author, text, myId, "host_admin")
                                                if (sentOk) {
                                                    messageText = ""
                                                    val freshMsgs = ClientNetworkManager.fetchMessages(targetIpInput)
                                                    hostMessages = freshMsgs
                                                    repository.saveClientCachedMessages(freshMsgs)
                                                } else {
                                                    // Send failed -> Offline fallback
                                                    val localMsgId = System.currentTimeMillis() + (0..999).random()
                                                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                                                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                                    val created = sdf.format(java.util.Date())
                                                    val encryptedText = CryptoHelper.encrypt(text)
                                                    val pendingMsg = Message(
                                                        id = localMsgId,
                                                        from = author,
                                                        text = encryptedText,
                                                        created = created,
                                                        edited = null,
                                                        status = "sending",
                                                        senderId = myId,
                                                        chatId = "host_admin",
                                                        isEncrypted = true
                                                    )
                                                    val currentOutbox = repository.getClientOfflineOutbox().toMutableList()
                                                    currentOutbox.add(pendingMsg)
                                                    repository.saveClientOfflineOutbox(currentOutbox)
                                                    
                                                    val currentCache = repository.getClientCachedMessages().toMutableList()
                                                    currentCache.add(pendingMsg)
                                                    repository.saveClientCachedMessages(currentCache)
                                                    hostMessages = currentCache
                                                    
                                                    messageText = ""
                                                    Toast.makeText(context, "عدم اتصال: پیام در صف خروجی ذخیره شد تا پس از اتصال ارسال شود.", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                // Completely offline case
                                                val localMsgId = System.currentTimeMillis() + (0..999).random()
                                                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                                                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                                val created = sdf.format(java.util.Date())
                                                val encryptedText = CryptoHelper.encrypt(text)
                                                val pendingMsg = Message(
                                                    id = localMsgId,
                                                    from = author,
                                                    text = encryptedText,
                                                    created = created,
                                                    edited = null,
                                                    status = "sending",
                                                    senderId = myId,
                                                    chatId = "host_admin",
                                                    isEncrypted = true
                                                )
                                                val currentOutbox = repository.getClientOfflineOutbox().toMutableList()
                                                currentOutbox.add(pendingMsg)
                                                repository.saveClientOfflineOutbox(currentOutbox)
                                                
                                                val currentCache = repository.getClientCachedMessages().toMutableList()
                                                currentCache.add(pendingMsg)
                                                repository.saveClientCachedMessages(currentCache)
                                                hostMessages = currentCache
                                                
                                                messageText = ""
                                                Toast.makeText(context, "آفلاین: پیام ذخیره شد و پس از بازگشت به شبکه هدایت می‌شود.", Toast.LENGTH_SHORT).show()
                                            }
                                            isSendingMsg = false
                                        }
                                    }
                                },
                                enabled = !isSendingMsg,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00A884))
                            ) {
                                if (isSendingMsg) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                                } else {
                                    Icon(Icons.Default.Send, contentDescription = "ارسال", tint = Color.White)
                                }
                            }
                        }

                        val messageScrollState = rememberScrollState()
                        LaunchedEffect(hostMessages.size) {
                            if (hostMessages.isNotEmpty()) {
                                messageScrollState.animateScrollTo(messageScrollState.maxValue)
                            }
                        }

                        // Message log list
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(messageScrollState)
                        ) {
                            if (hostMessages.isEmpty()) {
                                Text(
                                    text = "هیچ پیامی هنوز ثبت نشده است.",
                                    color = Color(0xFF938F99),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            } else {
                                 for (msg in hostMessages.reversed()) {
                                    val isMe = msg.from == nicknameInput
                                    
                                    // Outgoing (isMe) aligns to the right (End), incoming aligns to the left (Start)
                                    val align = if (isMe) Arrangement.End else Arrangement.Start
                                    val bubbleColor = if (isMe) Color(0xFF005C4B) else Color(0xFF202C33)
                                    val textColor = Color.White
                                    val senderColor = if (isMe) Color(0xFF53BDEB) else Color(0xFFE2C974)

                                    // Parse time
                                    val timeStr = try {
                                        if (msg.created.contains("T")) {
                                            val parts = msg.created.split("T")
                                            if (parts.size > 1) {
                                                val timePart = parts[1]
                                                val timeParts = timePart.split(":")
                                                if (timeParts.size >= 2) {
                                                    "${timeParts[0]}:${timeParts[1]}"
                                                } else {
                                                    timePart.take(5)
                                                }
                                            } else {
                                                msg.created.take(5)
                                            }
                                        } else {
                                            msg.created.take(5)
                                        }
                                    } catch (e: Exception) {
                                        "00:00"
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = align
                                    ) {
                                        Card(
                                            shape = RoundedCornerShape(
                                                topStart = 16.dp,
                                                topEnd = 16.dp,
                                                bottomStart = if (isMe) 16.dp else 2.dp,
                                                bottomEnd = if (isMe) 2.dp else 16.dp
                                            ),
                                            colors = CardDefaults.cardColors(containerColor = bubbleColor),
                                            border = BorderStroke(1.dp, if (isMe) Color(0xFF027A64) else Color(0xFF2C3943)),
                                            modifier = Modifier
                                                .widthIn(max = 280.dp)
                                                .clickable {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                    val clip = android.content.ClipData.newPlainText("Copied Message", msg.getDecryptedText())
                                                    clipboard.setPrimaryClip(clip)
                                                    Toast.makeText(context, "کپی شد!", Toast.LENGTH_SHORT).show()
                                                }
                                        ) {
                                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                                // Header
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = if (isMe) "شما" else msg.from,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = senderColor
                                                    )
                                                    
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        // Quick Copy action label
                                                        Text(
                                                            text = "کپی",
                                                            fontSize = 9.sp,
                                                            color = Color(0xFFBAC4D0),
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier
                                                                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                                                .clickable {
                                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                                    val clip = android.content.ClipData.newPlainText("Copied Message", msg.getDecryptedText())
                                                                    clipboard.setPrimaryClip(clip)
                                                                    Toast.makeText(context, "کپی شد!", Toast.LENGTH_SHORT).show()
                                                                }
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )

                                                        // Quick Delete action label
                                                        Text(
                                                            text = "حذف",
                                                            fontSize = 9.sp,
                                                            color = Color(0xFFF87171).copy(alpha = 0.9f),
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier
                                                                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                                                .clickable {
                                                                    coroutineScope.launch {
                                                                        val deletedOk = ClientNetworkManager.deleteMessage(targetIpInput, msg.id)
                                                                        if (deletedOk) {
                                                                            Toast.makeText(context, "پیام حذف شد.", Toast.LENGTH_SHORT).show()
                                                                            hostMessages = ClientNetworkManager.fetchMessages(targetIpInput)
                                                                        } else {
                                                                            Toast.makeText(context, "حذف پیام ناموفق بود.", Toast.LENGTH_SHORT).show()
                                                                        }
                                                                    }
                                                                }
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                
                                                Spacer(modifier = Modifier.height(4.dp))
                                                
                                                // Message Text or File Attachment
                                                val decryptedText = msg.getDecryptedText()
                                                val hasFile = decryptedText.startsWith("📎 فایل:")
                                                if (hasFile) {
                                                    val fileName = run {
                                                        val prefix = "📎 فایل:"
                                                        val cleanText = decryptedText.substringAfter(prefix).trim()
                                                        if (cleanText.contains(" (") && cleanText.endsWith(")")) {
                                                            cleanText.substringBeforeLast(" (").trim()
                                                        } else {
                                                            cleanText
                                                        }
                                                    }
                                                    val sizeString = if (decryptedText.contains("(")) {
                                                        decryptedText.substringAfter("(").substringBefore(")")
                                                     } else {
                                                        "فایل چت"
                                                     }
                                                     
                                                    Card(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 4.dp),
                                                        shape = RoundedCornerShape(12.dp),
                                                        colors = CardDefaults.cardColors(containerColor = if (isMe) Color(0xFF025142) else Color(0xFF182229)),
                                                        border = BorderStroke(1.dp, if (isMe) Color(0xFF014135) else Color(0xFF27343E))
                                                    ) {
                                                        Column(modifier = Modifier.padding(10.dp)) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(42.dp)
                                                                        .background(Color(0xFF00A884).copy(alpha = 0.2f), shape = CircleShape),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Text("📁", fontSize = 20.sp)
                                                                }
                                                                Column(modifier = Modifier.weight(1f)) {
                                                                    Text(
                                                                        text = fileName,
                                                                        fontSize = 13.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = Color.White,
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis
                                                                    )
                                                                    Text(
                                                                        text = sizeString,
                                                                        fontSize = 11.sp,
                                                                        color = Color(0xFF8696A0)
                                                                    )
                                                                }
                                                                
                                                                var isChatFileDownloading by remember { mutableStateOf(false) }
                                                                IconButton(
                                                                    onClick = {
                                                                        isChatFileDownloading = true
                                                                        coroutineScope.launch {
                                                                            val localDir = File(repository.getUploadsDirectoryPath())
                                                                            val fakeFileItem = FileItem(name = fileName, size = 0L, modified = "", source = "upload", canDelete = false)
                                                                            val success = ClientNetworkManager.downloadFile(
                                                                                targetIpInput,
                                                                                fakeFileItem,
                                                                                localDir
                                                                            )
                                                                            isChatFileDownloading = false
                                                                            if (success) {
                                                                                Toast.makeText(context, "فایل با موفقیت در بخش فایل‌های دریافتی ذخیره شد.", Toast.LENGTH_SHORT).show()
                                                                            } else {
                                                                                Toast.makeText(context, "دانلود فایل ناموفق بود.", Toast.LENGTH_SHORT).show()
                                                                            }
                                                                        }
                                                                    },
                                                                    modifier = Modifier.size(36.dp),
                                                                    enabled = !isChatFileDownloading
                                                                ) {
                                                                    if (isChatFileDownloading) {
                                                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF00A884))
                                                                    } else {
                                                                        Text("📥", fontSize = 16.sp)
                                                                    }
                                                                }
                                                            }

                                                            // Beautiful inline WhatsApp media visualizer
                                                            val fileType = getFileType(fileName)
                                                            val localFile = java.io.File(repository.getUploadsDirectoryPath(), fileName)
                                                            val cleanHost = targetIpInput.trim()
                                                            val sourceUrl = if (localFile.exists()) {
                                                                localFile.absolutePath
                                                            } else {
                                                                "http://$cleanHost:8082/download/$fileName"
                                                            }

                                                            Spacer(modifier = Modifier.height(6.dp))
                                                            when (fileType) {
                                                                "image" -> ImagePreviewWidget(sourceUrl, Modifier.fillMaxWidth())
                                                                "audio" -> AudioPlayerWidget(sourceUrl, Modifier.fillMaxWidth())
                                                                "video" -> VideoPreviewWidget(sourceUrl, Modifier.fillMaxWidth())
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    Text(
                                                        text = decryptedText,
                                                        fontSize = 13.sp,
                                                        color = textColor,
                                                        lineHeight = 18.sp,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                                
                                                Spacer(modifier = Modifier.height(6.dp))
                                                
                                                // Status bar
                                                Row(
                                                    modifier = Modifier.align(Alignment.End),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = timeStr,
                                                        fontSize = 9.sp,
                                                        color = Color.White.copy(alpha = 0.5f)
                                                    )
                                                    
                                                    if (isMe) {
                                                        val (tickText, tickColor) = when (msg.status) {
                                                            "sent" -> Pair("✓", Color(0xFF8696A0))
                                                            "delivered" -> Pair("✓✓", Color(0xFF8696A0))
                                                            else -> Pair("✓✓", Color(0xFF53BDEB))
                                                        }
                                                        Text(
                                                            text = tickText,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = tickColor
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // File Transfer tab content
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Files shared list card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2C34)),
                        border = BorderStroke(1.dp, Color(0xFF2A3942))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "فایل‌های موجود در سرور (${hostFiles.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00A884)
                                )

                                Button(
                                    onClick = { filePickerLauncher.launch("*/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF005C4B)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    enabled = !isUploadingFile
                                ) {
                                    if (isUploadingFile && chunkUploadStateText == "Uploading") {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White)
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("ارسال فایل", fontSize = 10.sp)
                                        }
                                    }
                                }
                            }

                            // Real-time Client upload progress bar and cancel Action
                            if (isUploadingFile) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF121B22)),
                                    border = BorderStroke(1.dp, Color(0xFF2A3942))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("⏳", fontSize = 16.sp)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (chunkUploadStateText == "Uploading") "در حال ارسال: $chunkUploadFileName" else "ارسال $chunkUploadFileName: ${if (chunkUploadStateText == "Success") "موفق" else if (chunkUploadStateText == "Canceled") "لغو شد" else "ناموفق"}",
                                                fontSize = 11.sp,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            
                                            LinearProgressIndicator(
                                                progress = { chunkUploadProgress },
                                                modifier = Modifier.fillMaxWidth(),
                                                color = if (chunkUploadStateText == "Success") Color(0xFF4CAF50) else if (chunkUploadStateText == "Failed") Color(0xFFEF4444) else Color(0xFF00A884),
                                                trackColor = Color(0xFF2A3942)
                                            )
                                            
                                            Text(
                                                text = "${formatBytes(chunkUploadedBytes)} از ${formatBytes(chunkTotalBytes)}",
                                                fontSize = 9.sp,
                                                color = Color(0xFF8696A0),
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                        
                                        Text(
                                            text = "${(chunkUploadProgress * 100).toInt()}%",
                                            fontSize = 11.sp,
                                            color = Color(0xFF00A884),
                                            fontWeight = FontWeight.Bold
                                        )
                                        
                                        if (chunkUploadStateText == "Uploading") {
                                            IconButton(
                                                onClick = {
                                                    chunkUploadCanceled = true
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Text("❌", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }

                            if (connectionMode == "one_way") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0x22FFB74D), shape = RoundedCornerShape(12.dp))
                                        .border(BorderStroke(1.dp, Color(0x66FFB74D)), RoundedCornerShape(12.dp))
                                        .padding(14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("🔒 دسترسی یک‌طرفه امن", color = Color(0xFFFFB74D), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "میزبان اجازه دسترسی به فایل‌های موجود در سرور را محدود کرده است. شما همچنان می‌توانید با استفاده از دکمه بالا برای سرور فایل ارسال کنید.",
                                            color = Color(0xFF8696A0),
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Center,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 240.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    if (hostFiles.isEmpty()) {
                                        Text(
                                            text = "هیچ فایلی روی سرور میزبان تعریف نشده است.",
                                            color = Color(0xFF938F99),
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    } else {
                                        for (file in hostFiles) {
                                            ClientFileRowItem(
                                                fileItem = file,
                                                onDownload = {
                                                    coroutineScope.launch {
                                                        val localDir = File(repository.getUploadsDirectoryPath())
                                                        val success = ClientNetworkManager.downloadFile(
                                                            targetIpInput,
                                                            file,
                                                            localDir
                                                        )
                                                        if (success) {
                                                            Toast.makeText(context, "فایل \"${file.name}\" با موفقیت دانلود و در پوشه محلی ذخیره شد.", Toast.LENGTH_LONG).show()
                                                        } else {
                                                            Toast.makeText(context, "دانلود فایل ناموفق بود.", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                },
                                                onDelete = if (file.canDelete) {
                                                    {
                                                        coroutineScope.launch {
                                                            val success = ClientNetworkManager.deleteFile(targetIpInput, file.name)
                                                            if (success) {
                                                                Toast.makeText(context, "فایل با موفقیت حذف شد.", Toast.LENGTH_SHORT).show()
                                                                hostFiles = ClientNetworkManager.fetchFiles(targetIpInput)
                                                            } else {
                                                                Toast.makeText(context, "عملیات حذف ناموفق بود.", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                } else null
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // P2P Panel is displayed directly under "ارسال فایل" tab
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
                        border = BorderStroke(2.dp, Color(0xFF6750A4))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFFB74D)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("⚡", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "پنل انتقال مستقیم فوق‌سریع (P2P)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "انتقال دوطرفه کاملا مستقیم به دستگاه مقصد",
                                        fontSize = 9.sp,
                                        color = Color(0xFFCAC4D0)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF2E3B2E))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "فعال",
                                        fontSize = 9.sp,
                                        color = Color(0xFFB2F2BB),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            HorizontalDivider(color = Color(0xFF49454F), thickness = 0.5.dp)

                            if (p2pProgress != null) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0x1A6750A4), shape = RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "در حال ارسال: ${p2pFileName.take(24)}${if (p2pFileName.length > 24) "..." else ""}",
                                            fontSize = 10.sp,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "$p2pProgress%",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFD0BCFF)
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { p2pProgress!!.toFloat() / 100f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = Color(0xFFD0BCFF),
                                        trackColor = Color(0xFF49454F)
                                    )
                                }
                            }

                            Button(
                                onClick = { p2pFilePickerLauncher.launch("*/*") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFD0BCFF),
                                    contentColor = Color(0xFF381E72)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                enabled = !isP2pSending
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = if (isP2pSending) "در حال مخابره فایل..." else "⚡ انتخاب و ارسال فوری فایل (P2P)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = p2pFlashText,
                                    onValueChange = { p2pFlashText = it },
                                    placeholder = { Text("ارسال سریع یادداشت، شماره یا لینک...") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFFFFB74D),
                                        unfocusedBorderColor = Color(0xFF49454F),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedPlaceholderColor = Color(0xFF938F99),
                                        unfocusedPlaceholderColor = Color(0xFF938F99)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(
                                    onClick = {
                                        val txt = p2pFlashText.trim()
                                        if (txt.isNotBlank()) {
                                            coroutineScope.launch {
                                                val author = nicknameInput.trim().ifEmpty { android.os.Build.MODEL }
                                                val success = ClientNetworkManager.sendMessage(targetIpInput, author, txt, repository.getClientUserId(), "host_admin")
                                                if (success) {
                                                    p2pFlashText = ""
                                                    Toast.makeText(context, "یادداشت فورا فرستاده شد!", Toast.LENGTH_SHORT).show()
                                                    hostMessages = ClientNetworkManager.fetchMessages(targetIpInput)
                                                } else {
                                                    Toast.makeText(context, "ارسال یادداشت موفق نبود.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF381E72))
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = "ارسال سریع یادداشت", tint = Color.White)
                                }
                            }

                            Text(
                                text = "💡 راهنما: برای ارسال دوطرفه، کافیست برنامه در هر دو گوشی باز باشد و یکی از گوشی‌ها دکمه اتصال را فشرده باشد. بخش فست تبادل مستقیما با مقصد همگام خواهد بود.",
                                fontSize = 8.sp,
                                color = Color(0xFF938F99),
                                lineHeight = 12.sp,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }

    // Duplicate handle dialog for Client side
    if (showDuplicateDialogClient && duplicateFileClient != null) {
        val dupFile = duplicateFileClient!!
        AlertDialog(
            onDismissRequest = {
                showDuplicateDialogClient = false
                dupFile.delete()
                duplicateFileClient = null
            },
            title = { Text("فایل تکراری یافت شد ⚠️", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "فایلی با نام \"${dupFile.name}\" در سرور موجود است. چه عملیاتی می‌خواهید انجام دهید؟",
                    color = Color.White,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            showDuplicateDialogClient = false
                            performChunkedUpload(dupFile, dupFile.name, forceRestart = false) // Resume!
                            duplicateFileClient = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A884)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ادامه آپلود فایل ناقص (Resume)", color = Color.White, fontSize = 11.sp)
                    }
                    
                    Button(
                        onClick = {
                            showDuplicateDialogClient = false
                            performChunkedUpload(dupFile, dupFile.name, forceRestart = true) // Overwrite!
                            duplicateFileClient = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF381E72)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("جایگزینی مجدد فایل کاملاً جدید (Overwrite)", color = Color.White, fontSize = 11.sp)
                    }
                    
                    Button(
                        onClick = {
                            showDuplicateDialogClient = false
                            val ext = dupFile.name.substringAfterLast(".", "")
                            val base = dupFile.name.substringBeforeLast(".", dupFile.name)
                            val newName = "${base}_${System.currentTimeMillis().toString().takeLast(4)}.${ext}"
                            val renamedFile = File(dupFile.parentFile, newName)
                            if (dupFile.renameTo(renamedFile)) {
                                performChunkedUpload(renamedFile, newName, forceRestart = true)
                            } else {
                                performChunkedUpload(dupFile, newName, forceRestart = true)
                            }
                            duplicateFileClient = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF005C4B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("آپلود با نام متمایز و کپی جدید", color = Color.White, fontSize = 11.sp)
                    }
                    
                    TextButton(
                        onClick = {
                            showDuplicateDialogClient = false
                            dupFile.delete()
                            duplicateFileClient = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("انصراف", color = Color(0xFF938F99), fontSize = 12.sp)
                    }
                }
            },
            containerColor = Color(0xFF1F2C34)
        )
    }

    // QR Code Dialog Camera scanner
    if (showQRScanner) {
        QRCodeScannerDialog(
            onScanSuccess = { result ->
                targetIpInput = ClientNetworkManager.cleanIpInput(result)
                showQRScanner = false
                // Trigger connection automatically
                onCheckConnection()
            },
            onDismiss = { showQRScanner = false }
        )
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    val kb = bytes.toDouble() / 1024.0
    return if (mb >= 1.0) String.format("%.1f MB", mb) else String.format("%.1f KB", kb)
}

@Composable
fun ClientFileRowItem(
    fileItem: FileItem,
    onDownload: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val sizeStr = remember(fileItem.size) {
        val kb = fileItem.size / 1024.0
        val mb = kb / 1024.0
        if (mb >= 1.0) String.format("%.1f MB", mb) else String.format("%.1f KB", kb)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E1E24))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(
                    text = if (fileItem.source == "upload") "📥" else "📂",
                    fontSize = 18.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileItem.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (fileItem.source == "upload") "مخزن آپلود" else "پوشه اشتراکی",
                            fontSize = 8.sp,
                            color = if (fileItem.source == "upload") Color(0xFFB2F2BB) else Color(0xFFD0BCFF),
                            modifier = Modifier
                                .background(
                                    color = if (fileItem.source == "upload") Color(0xFF2E3B2E) else Color(0xFF381E72),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                        Text(
                            text = sizeStr,
                            fontSize = 9.sp,
                            color = Color(0xFF938F99)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Action Download Button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF381E72))
                    .clickable { onDownload() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "دانلود و ذخیره",
                    tint = Color(0xFFD0BCFF),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "دانلود کردن",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD0BCFF)
                )
            }

            // Optional Delete Button
            if (onDelete != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x22F87171))
                        .clickable { onDelete() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف فایل",
                        tint = Color(0xFFF87171),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "حذف",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF87171)
                    )
                }
            }
        }
    }
}
