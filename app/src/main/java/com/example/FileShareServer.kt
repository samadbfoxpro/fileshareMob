package com.example

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

class FileShareServer(
    val port: Int,
    private val repository: FileShareRepository,
    private val context: Context
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        val isPublicPath = uri == "/" || uri == "/index.html" || uri == "/icon.ico" || uri == "/api/network" || uri == "/api/register_peer" || uri == "/api/disconnect" || uri == "/api/web/session_status"
        if (!isPublicPath) {
            val webSessionId = session.headers["x-web-session-id"] ?: getParam(session, "sessionId") ?: ""
            if (webSessionId.isNotEmpty()) {
                val webSession = WebSessionApprovalManager.getSession(webSessionId)
                if (webSession == null || webSession.status != "approved") {
                    val res = JSONObject().apply {
                        put("status", webSession?.status ?: "unauthorized")
                        put("error", "منتظر تایید توسط مدیر شبکه این برنامه باشید")
                    }
                    return newFixedLengthResponse(Response.Status.FORBIDDEN, "application/json; charset=utf-8", res.toString())
                }
            } else {
                val allowedInOneWay = (uri == "/upload" && method == Method.POST) ||
                                      (uri == "/api/upload/resume" && method == Method.GET) ||
                                      (uri == "/api/upload/chunk" && method == Method.POST)
                if (!isAuthorized(session, allowedInOneWay)) {
                    return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain; charset=utf-8", "دسترسی محدود شده است (ارتباط یک‌طرفه فعال است)")
                }
            }
        }

        return try {
            when {
                // 1. GET / -> Serve Index HTML Panel
                (uri == "/" || uri == "/index.html") && method == Method.GET -> {
                    val htmlContent = context.assets.open("index.html").bufferedReader().use { it.readText() }
                    newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", htmlContent)
                }

                // 2. GET /icon.ico -> App Brand Logo
                uri == "/icon.ico" && method == Method.GET -> {
                    val resId = context.resources.getIdentifier("ic_app_logo", "drawable", context.packageName)
                    if (resId != 0) {
                        val inputStream = context.resources.openRawResource(resId)
                        newChunkedResponse(Response.Status.OK, "image/png", inputStream)
                    } else {
                        newFixedLengthResponse(Response.Status.OK, "image/x-icon", "")
                    }
                }

                // 2b. GET/POST /api/web/session_status -> Register or get status of a web client session
                uri == "/api/web/session_status" -> {
                    val sessionId = getParam(session, "sessionId") ?: ""
                    val nickname = getParam(session, "nickname") ?: "کاربر وب"
                    val clientIp = session.headers["remote-addr"] ?: session.remoteIpAddress ?: ""
                    
                    if (sessionId.isEmpty()) {
                        val errJson = JSONObject().apply { put("error", "sessionId is required") }
                        newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json; charset=utf-8", errJson.toString())
                    } else {
                        val webReq = WebSessionApprovalManager.requestSession(sessionId, clientIp, nickname)
                        val res = JSONObject().apply {
                            put("status", webReq.status)
                            put("sessionId", webReq.sessionId)
                            put("nickname", webReq.nickname)
                        }
                        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", res.toString())
                    }
                }

                // 3. GET /api/network -> Get active IP addresses
                uri == "/api/network" && method == Method.GET -> {
                    val ips = getLocalIpAddresses()
                    val json = JSONObject()
                    json.put("port", port)
                    json.put("local", "http://localhost:$port")
                    
                    val array = org.json.JSONArray()
                    for (ip in ips) {
                        array.put("http://$ip:$port")
                    }
                    json.put("addresses", array)

                    newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", json.toString())
                }

                // 3b. POST /api/register_peer -> Register peer IP with authorization
                uri == "/api/register_peer" && method == Method.POST -> {
                    val body = getBodyData(session)
                    if (body != null) {
                        val obj = JSONObject(body)
                        val peerIp = obj.getString("client_ip")
                        val peerNickname = obj.optString("client_nickname", "دستگاه ناشناس")
                        val peerUserId = obj.optString("client_user_id", "")
                        val peerAvatar = obj.optString("client_avatar", "🧑‍💻")
                        
                        // Check if peer is already trusted with matching IP & Nickname or matching peerUserId
                        val trusted = if (peerUserId.isNotEmpty()) {
                            repository.isPeerTrustedByUserId(peerUserId)
                        } else {
                            repository.isPeerTrusted(peerIp, peerNickname)
                        }
                        
                        if (trusted != null) {
                            repository.addTrustedPeer(peerIp, peerNickname, trusted.mode, peerUserId, peerAvatar)
                            repository.setActiveClientSession(peerIp, trusted.mode)
                            repository.setClientTargetIp(peerIp)
                            val res = JSONObject()
                            res.put("status", "success")
                            res.put("mode", trusted.mode)
                            res.put("host_user_id", repository.getClientUserId())
                            res.put("host_nickname", repository.getClientNickname())
                            res.put("host_avatar", repository.getClientAvatar())
                            newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", res.toString())
                        } else {
                            // Require security approval from local user on application screen & notification
                            val approvedMode = ConnectionApprovalManager.requestApprovalWithMode(peerIp, peerNickname, context)
                            if (approvedMode != null) {
                                repository.addTrustedPeer(peerIp, peerNickname, approvedMode, peerUserId, peerAvatar)
                                repository.setActiveClientSession(peerIp, approvedMode)
                                repository.setClientTargetIp(peerIp)
                                val res = JSONObject()
                                res.put("status", "success")
                                res.put("mode", approvedMode)
                                res.put("host_user_id", repository.getClientUserId())
                                res.put("host_nickname", repository.getClientNickname())
                                res.put("host_avatar", repository.getClientAvatar())
                                newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", res.toString())
                            } else {
                                val res = JSONObject()
                                res.put("status", "denied")
                                newFixedLengthResponse(Response.Status.FORBIDDEN, "application/json; charset=utf-8", res.toString())
                            }
                        }
                    } else {
                        newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain; charset=utf-8", "ورودی نامعتبر")
                    }
                }

                // 3c. POST /api/disconnect -> Client manually disconnected
                uri == "/api/disconnect" && method == Method.POST -> {
                    val body = getBodyData(session)
                    if (body != null) {
                        val obj = JSONObject(body)
                        val peerIp = obj.getString("client_ip")
                        val peerNickname = obj.optString("client_nickname", "")
                        repository.removeActiveClientSession(peerIp)
                        repository.setActiveClientSession(peerIp, "denied")
                        
                        // Clear client target IP if it matches so we don't auto-reconnect
                        if (repository.getClientTargetIp() == peerIp) {
                            repository.setClientTargetIp("")
                        }
                        
                        val res = JSONObject()
                        res.put("status", "success")
                        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", res.toString())
                    } else {
                        newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain; charset=utf-8", "ورودی نامعتبر")
                    }
                }

                // 4. GET /api/files -> List uploaded and shared files
                uri == "/api/files" && method == Method.GET -> {
                    val list = repository.getFilesList()
                    val array = org.json.JSONArray()
                    for (item in list) {
                        val obj = JSONObject()
                        obj.put("name", item.name)
                        obj.put("size", item.size)
                        obj.put("modified", item.modified)
                        obj.put("source", item.source)
                        obj.put("canDelete", item.canDelete)
                        array.put(obj)
                    }
                    newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", array.toString())
                }

                // 4.5 GET /api/upload/resume -> Get current upload size for resume support
                uri == "/api/upload/resume" && method == Method.GET -> {
                    val filename = getParam(session, "filename") ?: "file"
                    val file = File(repository.getUploadsDirectoryPath(), repository.sanitizeFilename(filename))
                    val size = if (file.exists()) file.length() else 0L
                    val obj = JSONObject()
                    obj.put("uploadedBytes", size)
                    newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", obj.toString())
                }

                // 4.6 POST /api/upload/chunk -> Write a chunk of raw bytes
                uri == "/api/upload/chunk" && method == Method.POST -> {
                    val filename = getParam(session, "filename") ?: "file"
                    val offsetStr = getParam(session, "offset") ?: "0"
                    val offset = offsetStr.toLongOrNull() ?: 0L
                    
                    val contentLengthStr = session.headers["content-length"]
                    val contentLength = contentLengthStr?.toIntOrNull() ?: 0
                    
                    val sanitized = repository.sanitizeFilename(filename)
                    val destFile = File(repository.getUploadsDirectoryPath(), sanitized)
                    destFile.parentFile?.mkdirs()
                    
                    try {
                        val inputStream = session.inputStream
                        val bytes = ByteArray(contentLength)
                        var totalRead = 0
                        while (totalRead < contentLength) {
                            val read = inputStream.read(bytes, totalRead, contentLength - totalRead)
                            if (read == -1) break
                            totalRead += read
                        }
                        
                        val raf = java.io.RandomAccessFile(destFile, "rw")
                        raf.seek(offset)
                        raf.write(bytes, 0, totalRead)
                        raf.close()
                        
                        val obj = JSONObject()
                        obj.put("status", "success")
                        obj.put("bytesWritten", totalRead)
                        obj.put("currentSize", destFile.length())
                        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", obj.toString())
                    } catch (e: Exception) {
                        e.printStackTrace()
                        newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain; charset=utf-8", e.message ?: "error")
                    }
                }

                // 5. POST /upload -> Upload form file
                uri == "/upload" && method == Method.POST -> {
                    val files = HashMap<String, String>()
                    session.parseBody(files)
                    
                    val tempPath = files["file"] ?: files["file-input"]
                    val originalName = getParam(session, "filename") ?: getParam(session, "file") ?: getParam(session, "file-input") ?: "uploaded_file"
                    
                    if (tempPath != null) {
                        val tempFile = File(tempPath)
                        val savedFile = repository.saveUpload(tempFile, originalName)
                        
                        val obj = JSONObject()
                        obj.put("status", "success")
                        obj.put("filename", savedFile.name)
                        newFixedLengthResponse(Response.Status.CREATED, "application/json; charset=utf-8", obj.toString())
                    } else {
                        newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain; charset=utf-8", "فایلی فرستاده نشده است")
                    }
                }

                // 6. GET /download/shared/{filename} -> Download shared file
                uri.startsWith("/download/shared/") && method == Method.GET -> {
                    val rawFilename = uri.substringBefore("?").substringAfter("/download/shared/")
                    val decoded = Uri.decode(rawFilename)
                    val mimeType = getMimeType(decoded)
                    val stream = repository.getSharedFileStream(decoded)
                    
                    if (stream != null) {
                        val length = repository.getSharedFileLength(decoded)
                        val response = if (length > 0) {
                            newFixedLengthResponse(Response.Status.OK, mimeType, stream, length)
                        } else {
                            newChunkedResponse(Response.Status.OK, mimeType, stream)
                        }
                        val encoded = Uri.encode(decoded)
                        val safeAscii = decoded.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                        response.addHeader("Content-Disposition", "attachment; filename=\"$safeAscii\"; filename*=UTF-8''$encoded")
                        response
                    } else {
                        newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain; charset=utf-8", "فایل پیدا نشد یا اجازه دسترسی وجود ندارد")
                    }
                }

                // 7. GET /download/{filename} -> Download upload file
                uri.startsWith("/download/") && method == Method.GET -> {
                    val rawFilename = uri.substringBefore("?").substringAfter("/download/")
                    val decoded = Uri.decode(rawFilename)
                    val mimeType = getMimeType(decoded)
                    val stream = repository.getUploadFileStream(decoded)
                    
                    if (stream != null) {
                        val file = File(repository.getUploadsDirectoryPath(), repository.sanitizeFilename(decoded))
                        val length = if (file.exists() && file.isFile) file.length() else -1L
                        val response = if (length > 0) {
                            newFixedLengthResponse(Response.Status.OK, mimeType, stream, length)
                        } else {
                            newChunkedResponse(Response.Status.OK, mimeType, stream)
                        }
                        val encoded = Uri.encode(decoded)
                        val safeAscii = decoded.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                        response.addHeader("Content-Disposition", "attachment; filename=\"$safeAscii\"; filename*=UTF-8''$encoded")
                        response
                    } else {
                        newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain; charset=utf-8", "فایل آپلود شده پیدا نشد")
                    }
                }

                // 8. DELETE /api/files/{filename} -> Delete uploaded file
                uri.startsWith("/api/files/") && method == Method.DELETE -> {
                    val filename = uri.substringAfter("/api/files/")
                    val decoded = Uri.decode(filename)
                    val deleted = repository.deleteUploadFile(decoded)
                    if (deleted) {
                        val obj = JSONObject()
                        obj.put("status", "success")
                        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", obj.toString())
                    } else {
                        newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain; charset=utf-8", "حذف غیرمجاز یا فایل یافت نشد")
                    }
                }

                // 9. GET /api/messages/download -> Download messages txt
                uri == "/api/messages/download" && method == Method.GET -> {
                    val txt = repository.getMessagesFormattedText()
                    val response = newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8", txt)
                    response.addHeader("Content-Disposition", "attachment; filename=\"fileshare-messages.txt\"")
                    response
                }

                // 10. GET /api/messages -> Get messages (supports ?username=... or ?chatId=...)
                uri == "/api/messages" && method == Method.GET -> {
                    val webSessionId = session.headers["x-web-session-id"] ?: getParam(session, "sessionId") ?: ""
                    val sessionNickname = if (webSessionId.isNotEmpty()) {
                        WebSessionApprovalManager.getSession(webSessionId)?.nickname
                    } else null

                    val usernameParam = getParam(session, "username") ?: getParam(session, "chatId")
                    val username = if (!usernameParam.isNullOrBlank()) usernameParam.trim() else sessionNickname?.trim()
                    val afterStr = getParam(session, "after")
                    val afterId = afterStr?.toLongOrNull() ?: 0L
                    
                    val list = if (!username.isNullOrBlank()) {
                        repository.markHostMessagesAsRead(username)
                        if (afterId > 0L) {
                            repository.getMessagesForChat(username).filter { it.id > afterId }
                        } else {
                            repository.getMessagesForChat(username)
                        }
                    } else {
                        repository.markHostMessagesAsRead()
                        if (afterId > 0L) {
                            repository.getMessagesAfter(afterId)
                        } else {
                            repository.getAllMessages()
                        }
                    }

                    val array = org.json.JSONArray()
                    for (msg in list) {
                        array.put(msg.toJsonObject())
                    }
                    newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", array.toString())
                }

                // 11. POST /api/messages -> Add Message
                uri == "/api/messages" && method == Method.POST -> {
                    val body = getBodyData(session)
                    if (body != null) {
                        val obj = JSONObject(body)
                        val webSessionId = session.headers["x-web-session-id"] ?: getParam(session, "sessionId") ?: ""
                        val sessionNickname = if (webSessionId.isNotEmpty()) {
                            WebSessionApprovalManager.getSession(webSessionId)?.nickname
                        } else null

                        var from = obj.optString("from", "").trim()
                        if (from.isEmpty()) {
                            from = sessionNickname?.trim() ?: "کاربر وب"
                        }
                        val text = obj.getString("text")
                        val senderId = obj.optString("senderId", "")
                        var chatId = obj.optString("chatId", "").trim()
                        if (chatId.isEmpty() && from.isNotBlank() && !from.contains("مدیر") && !from.contains("گوشی")) {
                            chatId = from
                        }
                        val isEncrypted = obj.optBoolean("isEncrypted", false)
                        val alreadyEncrypted = obj.optBoolean("alreadyEncrypted", false)
                        
                        val replyToId = if (obj.has("replyToId") && !obj.isNull("replyToId")) obj.getLong("replyToId") else null
                        val replyToText = if (obj.has("replyToText") && !obj.isNull("replyToText")) obj.getString("replyToText") else null
                        val replyToUser = if (obj.has("replyToUser") && !obj.isNull("replyToUser")) obj.getString("replyToUser") else null

                        val created = repository.addMessage(
                            from = from,
                            text = text,
                            senderId = if (senderId.isNotEmpty()) senderId else null,
                            chatId = if (chatId.isNotEmpty()) chatId else null,
                            isEncrypted = isEncrypted,
                            alreadyEncrypted = alreadyEncrypted,
                            replyToId = replyToId,
                            replyToText = replyToText,
                            replyToUser = replyToUser
                        )
                        newFixedLengthResponse(Response.Status.CREATED, "application/json; charset=utf-8", created.toJsonObject().toString())
                    } else {
                        newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain; charset=utf-8", "ورودی نامعتبر")
                    }
                }

                // 11c. DELETE /api/messages/room -> Delete specific user's chat room
                uri == "/api/messages/room" && method == Method.DELETE -> {
                    val username = getParam(session, "username") ?: getParam(session, "chatId")
                    if (username != null && username.isNotBlank()) {
                        val deleted = repository.deleteChatRoom(username)
                        val obj = JSONObject()
                        obj.put("status", "success")
                        obj.put("deleted", deleted)
                        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", obj.toString())
                    } else {
                        newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain; charset=utf-8", "نام کاربری نامعتبر است")
                    }
                }

                // 11b. POST /api/messages/sync -> Merge & Sync Messages (Restoration)
                uri == "/api/messages/sync" && method == Method.POST -> {
                    val body = getBodyData(session)
                    if (body != null) {
                        val array = org.json.JSONArray(body)
                        val list = mutableListOf<Message>()
                        for (i in 0 until array.length()) {
                            list.add(Message.fromJsonObject(array.getJSONObject(i)))
                        }
                        val merged = repository.syncAndMergeMessages(list)
                        val responseArray = org.json.JSONArray()
                        for (msg in merged) {
                            responseArray.put(msg.toJsonObject())
                        }
                        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", responseArray.toString())
                    } else {
                        newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain; charset=utf-8", "ورودی نامعتبر")
                    }
                }

                // 12. PUT /api/messages/{id} -> Edit Message
                uri.startsWith("/api/messages/") && method == Method.PUT -> {
                    val idStr = uri.substringAfter("/api/messages/")
                    val id = idStr.toLongOrNull() ?: 0L
                    val body = getBodyData(session)
                    if (body != null) {
                        val obj = JSONObject(body)
                        val from = obj.getString("from")
                        val text = obj.getString("text")
                        val updated = repository.editMessage(id, from, text)
                        if (updated != null) {
                            newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", updated.toJsonObject().toString())
                        } else {
                            newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain; charset=utf-8", "پیام یافت نشد")
                        }
                    } else {
                        newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain; charset=utf-8", "ورودی نامعتبر")
                    }
                }

                // 13. DELETE /api/messages/{id} -> Delete message
                uri.startsWith("/api/messages/") && method == Method.DELETE -> {
                    val idStr = uri.substringAfter("/api/messages/")
                    val id = idStr.toLongOrNull() ?: 0L
                    val deleted = repository.deleteMessage(id)
                    if (deleted) {
                        val obj = JSONObject()
                        obj.put("status", "success")
                        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", obj.toString())
                    } else {
                        newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain; charset=utf-8", "پیام یافت نشد")
                    }
                }

                // 14. DELETE /api/messages -> Delete all messages
                uri == "/api/messages" && method == Method.DELETE -> {
                    repository.deleteAllMessages()
                    val obj = JSONObject()
                    obj.put("status", "success")
                    newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", obj.toString())
                }

                // 15. POST /api/messages/delete -> Bulk delete messages
                uri == "/api/messages/delete" && method == Method.POST -> {
                    val body = getBodyData(session)
                    if (body != null) {
                        val obj = JSONObject(body)
                        val array = obj.getJSONArray("ids")
                        val ids = mutableListOf<Long>()
                        for (i in 0 until array.length()) {
                            ids.add(array.getLong(i))
                        }
                        
                        repository.deleteMessages(ids)
                        val res = JSONObject()
                        res.put("status", "success")
                        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", res.toString())
                    } else {
                        newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain; charset=utf-8", "ورودی نامعتبر")
                    }
                }

                else -> {
                    newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain; charset=utf-8", "مسیر مورد نظر یافت نشد")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain; charset=utf-8", "خطای سرور: ${e.message}")
        }
    }

    private fun decodeString(raw: String?): String {
        if (raw == null) return ""
        if (raw.any { it.code > 255 }) {
            return raw
        }
        return try {
            String(raw.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
        } catch (e: Exception) {
            raw
        }
    }

    private fun getParam(session: IHTTPSession, key: String): String? {
        val query = session.queryParameterString
        if (!query.isNullOrEmpty()) {
            val pairs = query.split("&")
            for (pair in pairs) {
                val idx = pair.indexOf("=")
                val k = if (idx > 0) pair.substring(0, idx) else pair
                if (k == key && idx > 0 && pair.length > idx + 1) {
                    val v = pair.substring(idx + 1)
                    try {
                        return java.net.URLDecoder.decode(v, "UTF-8")
                    } catch (e: Exception) {
                        // fallback
                    }
                }
            }
        }
        val raw = session.parms[key] ?: return null
        return decodeString(raw)
    }

    private fun getBodyData(session: IHTTPSession): String? {
        val contentType = session.headers["content-type"] ?: ""
        val contentLengthStr = session.headers["content-length"]
        val contentLength = contentLengthStr?.toIntOrNull() ?: 0
        
        if (contentType.contains("application/json", ignoreCase = true) && contentLength in 1..1048576) {
            try {
                val buf = ByteArray(contentLength)
                var totalRead = 0
                val input = session.inputStream
                while (totalRead < contentLength) {
                    val read = input.read(buf, totalRead, contentLength - totalRead)
                    if (read <= 0) break
                    totalRead += read
                }
                if (totalRead > 0) {
                    return String(buf, 0, totalRead, Charsets.UTF_8)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val files = HashMap<String, String>()
        try {
            session.parseBody(files)
            val raw = files["postData"] ?: return null
            return decodeString(raw)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun getMimeType(filename: String): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(filename)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    private fun getLocalIpAddresses(): List<String> {
        val wifiAddresses = mutableListOf<String>()
        val otherAddresses = mutableListOf<String>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (iface in interfaces) {
                val name = iface.name.lowercase()
                if (!iface.isUp) continue
                
                // Skip useless/non-local interfaces
                if (name.contains("dummy") || name.contains("lo") || name.contains("p2p")) {
                    continue
                }
                
                val addrs = Collections.list(iface.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        var sAddr = addr.hostAddress ?: ""
                        if (sAddr.contains("%")) {
                            sAddr = sAddr.split("%")[0]
                        }
                        
                        if (!sAddr.contains(":")) { // Only IPv4
                            val isWifiOrAp = name.contains("wlan") || 
                                             name.contains("ap") || 
                                             name.contains("wifi") || 
                                             name.contains("eth") || 
                                             name.contains("softap") ||
                                             name.contains("rndis")
                            
                            val isCellular = name.contains("rmnet") || 
                                             name.contains("pdp") || 
                                             name.contains("ccmni") || 
                                             (name.contains("usb") && !name.contains("rndis"))
                            
                            if (isWifiOrAp) {
                                wifiAddresses.add(sAddr)
                            } else if (!isCellular) {
                                otherAddresses.add(sAddr)
                            }
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        
        return if (wifiAddresses.isNotEmpty()) {
            wifiAddresses.distinct()
        } else {
            otherAddresses.distinct()
        }
    }

    private fun isAuthorized(session: IHTTPSession, allowedInOneWay: Boolean): Boolean {
        val clientIp = session.headers["remote-addr"] ?: session.remoteIpAddress ?: ""
        if (clientIp.isBlank()) return true
        
        if (clientIp == "127.0.0.1" || clientIp == "localhost" || clientIp == "0:0:0:0:0:0:0:1") {
            return true
        }

        val mode = repository.getActiveClientSessionMode(clientIp)
        if (mode == "one_way") {
            return allowedInOneWay
        }
        if (mode == "denied" || mode == "unauthorized") {
            return false
        }
        return true
    }
}
