package dev.smto.driveassistant.tools

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.view.KeyEvent
import dev.smto.driveassistant.service.NotificationReaderService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Media transport control via media-button key events (works with any player) plus
 * "now playing" info read from active [android.media.session.MediaSession]s, which
 * requires the notification-listener grant.
 */
class MediaControlTool(private val context: Context) : Tool {

    override val name = "control_media"
    override val description =
        "Control audio/music playback: play, pause, toggle, next track, previous track, " +
            "stop, volume up, volume down. Use for any 'play music', 'skip', 'pause' request."

    override val parameters: JsonObject = Json.parseToJsonElement(
        """
        {
          "type": "object",
          "properties": {
            "action": {
              "type": "string",
              "enum": ["play","pause","toggle","next","previous","stop","volume_up","volume_down"]
            }
          },
          "required": ["action"]
        }
        """.trimIndent(),
    ) as JsonObject

    private val audio get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override suspend fun run(args: JsonObject): String {
        return when (args.str("action")?.lowercase()) {
            "play" -> key(KeyEvent.KEYCODE_MEDIA_PLAY).let { "Playing." }
            "pause" -> key(KeyEvent.KEYCODE_MEDIA_PAUSE).let { "Paused." }
            "toggle", null -> key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE).let { "Toggled playback." }
            "next" -> key(KeyEvent.KEYCODE_MEDIA_NEXT).let { "Skipped to next track." }
            "previous", "prev" -> key(KeyEvent.KEYCODE_MEDIA_PREVIOUS).let { "Back to previous track." }
            "stop" -> key(KeyEvent.KEYCODE_MEDIA_STOP).let { "Stopped." }
            "volume_up" -> {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                "Volume up."
            }
            "volume_down" -> {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
                "Volume down."
            }
            else -> "Unknown media action."
        }
    }

    private fun key(code: Int) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }
}

class NowPlayingTool(private val context: Context) : Tool {

    override val name = "get_now_playing"
    override val description = "Report the currently playing track title and artist, if any."

    override val parameters: JsonObject = Json.parseToJsonElement(
        """{ "type": "object", "properties": {} }""",
    ) as JsonObject

    override suspend fun run(args: JsonObject): String =
        NowPlaying.describe(context) ?: "Nothing is playing."
}

/** Shared read of the active media session, used by the tool and by turn context. */
object NowPlaying {

    /** "Title by Artist" / "Title" / null when nothing is playing or access is missing. */
    fun describe(context: Context): String? {
        val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val listener = ComponentName(context, NotificationReaderService::class.java)
        val sessions = runCatching { msm.getActiveSessions(listener) }.getOrNull() ?: return null

        val active = sessions.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: return null

        val md = active.metadata ?: return null
        val title = md.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: return null
        val artist = md.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        return if (artist != null) "$title by $artist" else title
    }
}
