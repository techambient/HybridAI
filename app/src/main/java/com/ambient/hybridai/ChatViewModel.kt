package com.ambient.hybridai

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val STORAGE_FILE = "ambient_hybrid_chats.json"
    
    var sessions = mutableStateListOf<ChatSession>()
        private set
        
    var currentSession by mutableStateOf<ChatSession?>(null)
    
    var isTyping by mutableStateOf(false)
        private set

    init {
        loadSessions()
    }

    private fun loadSessions() {
        viewModelScope.launch {
            val content = readFromFile()
            if (content.isNotEmpty()) {
                try {
                    val array = JSONArray(content)
                    val loadedSessions = mutableListOf<ChatSession>()
                    for (i in 0 until array.length()) {
                        val sessionObj = array.getJSONObject(i)
                        val messagesArray = sessionObj.getJSONArray("messages")
                        val messages = androidx.compose.runtime.mutableStateListOf<ChatMessage>()
                        for (j in 0 until messagesArray.length()) {
                            val msgObj = messagesArray.getJSONObject(j)
                            messages.add(
                                ChatMessage(
                                    id = msgObj.getString("id"),
                                    text = msgObj.getString("text"),
                                    isUser = msgObj.getBoolean("isUser"),
                                    source = msgObj.optString("source", null),
                                    isError = msgObj.optBoolean("isError", false),
                                    timestamp = msgObj.getLong("timestamp")
                                )
                            )
                        }
                        loadedSessions.add(
                            ChatSession(
                                id = sessionObj.getString("id"),
                                title = sessionObj.getString("title"),
                                messages = messages
                            )
                        )
                    }
                    sessions.clear()
                    sessions.addAll(loadedSessions)
                    if (sessions.isNotEmpty()) {
                        currentSession = sessions.last()
                    } else {
                        startNewChat()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    startNewChat()
                }
            } else {
                startNewChat()
            }
        }
    }

    fun startNewChat() {
        val messages = androidx.compose.runtime.mutableStateListOf<ChatMessage>()
        messages.add(
            ChatMessage(
                text = "Hi, HybridAI user! How can I help you today?",
                isUser = false,
                source = "System Initialization"
            )
        )
        val newSession = ChatSession(
            title = "New Chat Thread",
            messages = messages
        )
        sessions.add(newSession)
        currentSession = newSession
        saveSessions()
    }

    fun selectSession(session: ChatSession) {
        currentSession = session
    }

    fun renameSession(sessionId: String, newTitle: String) {
        val session = sessions.find { it.id == sessionId }
        session?.let {
            it.title = newTitle
            saveSessions()
        }
    }

    fun deleteSession(sessionId: String) {
        val session = sessions.find { it.id == sessionId }
        session?.let {
            sessions.remove(it)
            if (currentSession?.id == sessionId) {
                currentSession = sessions.lastOrNull() ?: run {
                    startNewChat()
                    sessions.last()
                }
            }
            saveSessions()
        }
    }

    fun sendMessage(text: String) {
        val session = currentSession ?: return
        if (text.isBlank()) return

        if (session.title == "New Chat Thread") {
            session.title = if (text.length > 22) text.take(22) + "..." else text
        }

        session.messages.add(ChatMessage(text = text, isUser = true))
        saveSessions()
        
        isTyping = true
        
        viewModelScope.launch {
            try {
                val response = callAI(text)
                session.messages.add(
                    ChatMessage(
                        text = response.getString("answer"),
                        isUser = false,
                        source = "Source: " + response.getString("source").replace("_", " ")
                    )
                )
            } catch (e: Exception) {
                session.messages.add(
                    ChatMessage(
                        text = "Critical connection error. Ensure your Google Script is deployed properly to allow public access.",
                        isUser = false,
                        source = "Network Failure",
                        isError = true
                    )
                )
            } finally {
                isTyping = false
                saveSessions()
            }
        }
    }

    private suspend fun callAI(query: String): JSONObject = withContext(Dispatchers.IO) {
        val webAppUrl = "https://script.google.com/macros/s/AKfycbw81vKdCUxLnUM4MYbKDrCTaUqAcD3athMduBNshhaZEDdZgF2thbnTvX1a4YxPou50/exec"
        val url = URL("$webAppUrl?q=${java.net.URLEncoder.encode(query, "UTF-8")}")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = true
        
        val responseText = connection.inputStream.bufferedReader().use { it.readText() }
        JSONObject(responseText)
    }

    private fun saveSessions() {
        viewModelScope.launch {
            val array = JSONArray()
            sessions.forEach { session ->
                val sessionObj = JSONObject()
                sessionObj.put("id", session.id)
                sessionObj.put("title", session.title)
                val messagesArray = JSONArray()
                session.messages.forEach { msg ->
                    val msgObj = JSONObject()
                    msgObj.put("id", msg.id)
                    msgObj.put("text", msg.text)
                    msgObj.put("isUser", msg.isUser)
                    msgObj.put("source", msg.source)
                    msgObj.put("isError", msg.isError)
                    msgObj.put("timestamp", msg.timestamp)
                    messagesArray.put(msgObj)
                }
                sessionObj.put("messages", messagesArray)
                array.put(sessionObj)
            }
            writeToFile(array.toString())
        }
    }

    private suspend fun writeToFile(content: String) = withContext(Dispatchers.IO) {
        getApplication<Application>().openFileOutput(STORAGE_FILE, android.content.Context.MODE_PRIVATE).use {
            it.write(content.toByteArray())
        }
    }

    private suspend fun readFromFile(): String = withContext(Dispatchers.IO) {
        try {
            getApplication<Application>().openFileInput(STORAGE_FILE).use {
                it.bufferedReader().readText()
            }
        } catch (e: Exception) {
            ""
        }
    }
}
