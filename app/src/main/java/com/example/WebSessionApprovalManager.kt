package com.example

import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentHashMap

data class WebSessionRequest(
    val sessionId: String,
    val ip: String,
    val nickname: String,
    val timestamp: Long,
    var status: String // "pending", "approved", "denied"
)

object WebSessionApprovalManager {
    val pendingRequests = MutableStateFlow<List<WebSessionRequest>>(emptyList())
    private val sessions = ConcurrentHashMap<String, WebSessionRequest>()

    fun requestSession(sessionId: String, ip: String, nickname: String): WebSessionRequest {
        val existing = sessions[sessionId]
        if (existing != null) {
            // Update timestamp to keep it active
            return existing
        }
        val req = WebSessionRequest(sessionId, ip, nickname, System.currentTimeMillis(), "pending")
        sessions[sessionId] = req
        updatePending()
        return req
    }

    fun getSession(sessionId: String): WebSessionRequest? {
        return sessions[sessionId]
    }

    fun approveSession(sessionId: String) {
        sessions[sessionId]?.let {
            it.status = "approved"
            updatePending()
        }
    }

    fun denySession(sessionId: String) {
        sessions[sessionId]?.let {
            it.status = "denied"
            updatePending()
        }
    }

    fun clearSession(sessionId: String) {
        sessions.remove(sessionId)
        updatePending()
    }

    fun getAllSessions(): List<WebSessionRequest> {
        return sessions.values.toList()
    }

    private fun updatePending() {
        pendingRequests.value = sessions.values.filter { it.status == "pending" }.toList()
    }
}
