package dev.smto.driveassistant.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import dev.smto.driveassistant.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Speaks incoming *messaging* notifications aloud (Signal, WhatsApp, Telegram, SMS,
 * …) — detected by notification category / MessagingStyle, not a hardcoded package
 * list. Nothing is retained and nothing is exposed to the model. Binding this
 * service also unlocks active-media-session access for the media tools.
 */
class NotificationReaderService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!shouldConsider(sbn)) return
        if (!isMessaging(sbn) || isNoise(sbn)) return

        val (sender, message) = extractMessage(sbn)
        if (message.isBlank() && sender.isBlank()) return

        scope.launch {
            val cfg = App.get().settings.current()
            if (!cfg.notificationReadout) return@launch
            if (cfg.notificationReadoutOnlyInCar && !App.get().car.projecting) return@launch
            val allowed = cfg.allowedNotificationPackages
            if (allowed.isNotEmpty() && sbn.packageName !in allowed) return@launch

            val spoken = buildString {
                append(appLabel(sbn.packageName))
                if (sender.isNotBlank()) append(", ").append(clip(sender, MAX_SENDER_CHARS))
                if (message.isNotBlank()) append(": ").append(clip(message, MAX_MESSAGE_CHARS))
            }
            App.get().tts.speak(spoken, flush = false, languageTag = cfg.language)
        }
    }

    private fun shouldConsider(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName == packageName) return false
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        return true
    }

    /** Recognises person-to-person message notifications regardless of the app. */
    private fun isMessaging(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        if (n.category == Notification.CATEGORY_MESSAGE) return true
        val template = n.extras.getString(Notification.EXTRA_TEMPLATE)
        if (template == "android.app.Notification\$MessagingStyle") return true
        return n.extras.containsKey(Notification.EXTRA_MESSAGES)
    }

    private fun isNoise(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return true
        if (n.category in NOISE_CATEGORIES) return true
        return false
    }

    /** @return (sender, message); sender is blank when the notification has no person. */
    private fun extractMessage(sbn: StatusBarNotification): Pair<String, String> {
        val n = sbn.notification

        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        val lastWithText = style?.messages?.lastOrNull { !it.text.isNullOrBlank() }
        if (lastWithText != null) {
            val sender = lastWithText.person?.name?.toString()?.trim().orEmpty()
            return sender to lastWithText.text.toString().trim()
        }

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim().orEmpty()
        return title to text
    }

    private fun clip(s: String, max: Int): String {
        val flat = s.replace(Regex("\\s+"), " ").trim()
        if (flat.length <= max) return flat
        val cut = flat.take(max).substringBeforeLast(' ', flat.take(max)).trimEnd()
        return "$cut…"
    }

    private fun appLabel(pkg: String): String = runCatching {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private companion object {
        const val MAX_MESSAGE_CHARS = 220
        const val MAX_SENDER_CHARS = 40

        val NOISE_CATEGORIES = setOf(
            Notification.CATEGORY_TRANSPORT,
            Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_CALL,
        )
    }
}
