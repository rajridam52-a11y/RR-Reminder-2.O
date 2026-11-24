package com.example.core.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

sealed interface AssistantUiState {
    data object Idle : AssistantUiState
    data object Loading : AssistantUiState
    data class Streaming(val text: String) : AssistantUiState
    data class Success(val text: String) : AssistantUiState
    data class Error(val message: String) : AssistantUiState
}

class AiAssistantRepository {
    private val model by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    fun streamProductivityAdvice(
        userPrompt: String,
        contextSummary: String,
    ): Flow<AssistantUiState> = flow {
        emit(AssistantUiState.Loading)
        val fullPrompt = buildString {
            appendLine("You are the optional RR Reminder Productivity Assistant inside the RR Reminder Android app.")
            appendLine("Be concise, practical, and structured with clear bullet points or a time-blocked schedule.")
            appendLine("User's real local productivity context:")
            appendLine(contextSummary)
            appendLine()
            appendLine("User request: $userPrompt")
        }
        val accumulated = StringBuilder()
        try {
            withTimeout(60_000L) {
                model.generateContentStream(fullPrompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(AssistantUiState.Streaming(accumulated.toString()))
                    }
                }
            }
            if (accumulated.isNotBlank()) {
                emit(AssistantUiState.Success(accumulated.toString()))
            } else {
                emit(AssistantUiState.Error("Assistant returned an empty response."))
            }
        } catch (e: TimeoutCancellationException) {
            emit(AssistantUiState.Error("Assistant request timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(AssistantUiState.Error(e.localizedMessage ?: "Assistant failed to respond."))
        }
    }
}
