package com.example.core.common

import java.time.ZoneId
import java.time.ZonedDateTime

data class ParsedVoiceReminder(
    val title: String,
    val suggestedTriggerMillis: Long,
    val isAmbiguousTime: Boolean,
    val detectedTimeDescription: String,
)

object VoiceReminderParser {

    /**
     * Parses spoken text like "Call Alex tomorrow at 4" or "Pay rent" into a proposed reminder,
     * explicitly flagging ambiguous or unspecified times so the UI can ask the user to confirm.
     */
    fun parse(spokenText: String, now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())): ParsedVoiceReminder {
        val raw = spokenText.trim()
        val lower = raw.lowercase()

        var target = now
        var hasExplicitDay = false
        var hasExplicitTime = false
        var isAmbiguous = false

        when {
            "tomorrow" in lower -> {
                target = target.plusDays(1)
                hasExplicitDay = true
            }
            "today" in lower -> {
                hasExplicitDay = true
            }
            "next week" in lower -> {
                target = target.plusWeeks(1)
                hasExplicitDay = true
                isAmbiguous = true
            }
        }

        // Check for "in X minutes/hours"
        val inRegex = Regex("""\bin\s+(\d+)\s+(minute|minutes|min|mins|hour|hours|hr|hrs)\b""")
        val inMatch = inRegex.find(lower)
        if (inMatch != null) {
            val amount = inMatch.groupValues[1].toLongOrNull() ?: 1L
            val unit = inMatch.groupValues[2]
            target = if (unit.startsWith("h")) now.plusHours(amount) else now.plusMinutes(amount)
            hasExplicitTime = true
            hasExplicitDay = true
        } else {
            // Check for "at H(:MM)? (am|pm)?"
            val atRegex = Regex("""\bat\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm|a\.m\.|p\.m\.)?\b""")
            val atMatch = atRegex.find(lower)
            if (atMatch != null) {
                val hourRaw = atMatch.groupValues[1].toIntOrNull() ?: 9
                val minute = atMatch.groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: 0
                val meridiem = atMatch.groupValues[3]

                val hour24 = when {
                    meridiem.startsWith("p") && hourRaw < 12 -> hourRaw + 12
                    meridiem.startsWith("a") && hourRaw == 12 -> 0
                    meridiem.isBlank() && hourRaw in 1..12 -> {
                        // Ambiguous AM vs PM!
                        isAmbiguous = true
                        if (hourRaw < now.hour && hourRaw <= 11) hourRaw + 12 else hourRaw
                    }
                    else -> hourRaw.coerceIn(0, 23)
                }
                target = target.withHour(hour24).withMinute(minute).withSecond(0).withNano(0)
                if (!target.isAfter(now) && !hasExplicitDay) {
                    target = target.plusDays(1)
                }
                hasExplicitTime = true
            } else {
                // No explicit time spoken -> ambiguous! Default proposal +1 hour
                target = now.plusHours(1).withSecond(0).withNano(0)
                isAmbiguous = true
            }
        }

        // Clean common filler phrases for title
        var title = raw
            .replace(Regex("""(?i)^(remind me to|remember to|create reminder to|set reminder to)\s+"""), "")
            .replace(inRegex, "")
            .replace(Regex("""(?i)\b(tomorrow|today|next week)\b"""), "")
            .replace(Regex("""(?i)\bat\s+\d{1,2}(?::\d{2})?\s*(?:am|pm|a\.m\.|p\.m\.)?\b"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (title.isBlank()) title = raw

        val desc = if (!hasExplicitTime) {
            "No specific time detected — suggested +1 hour. Please confirm."
        } else if (isAmbiguous) {
            "AM/PM or day may be ambiguous. Please confirm the scheduled time."
        } else {
            "Detected schedule time. Ready to save."
        }

        return ParsedVoiceReminder(
            title = title,
            suggestedTriggerMillis = target.toInstant().toEpochMilli(),
            isAmbiguousTime = isAmbiguous,
            detectedTimeDescription = desc,
        )
    }
}
