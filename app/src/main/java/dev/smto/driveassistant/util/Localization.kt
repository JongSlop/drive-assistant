package dev.smto.driveassistant.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The in-app language setting is independent of the system locale, so string
 * resources must be resolved against an explicitly-configured context rather than
 * the default one.
 */
fun Context.forLanguage(tag: String?): Context {
    if (tag.isNullOrBlank()) return this
    val locale = Locale.forLanguageTag(tag)
    val config = Configuration(resources.configuration).apply { setLocale(locale) }
    return createConfigurationContext(config)
}
