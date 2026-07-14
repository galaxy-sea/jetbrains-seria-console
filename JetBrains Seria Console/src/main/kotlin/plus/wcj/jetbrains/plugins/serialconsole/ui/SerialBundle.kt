package plus.wcj.jetbrains.plugins.serialconsole.ui

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey
import java.text.MessageFormat
import java.util.Locale
import java.util.ResourceBundle

private const val BUNDLE = "messages.SerialConsoleBundle"

enum class SerialLanguage(val displayName: String, val locale: Locale) {
    English("English", Locale.ROOT),
    Chinese("中文", Locale.SIMPLIFIED_CHINESE),
}

object SerialBundle : DynamicBundle(BUNDLE) {
    private val bundleControl = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES)

    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
        return getMessage(key, *params)
    }

    fun message(language: SerialLanguage, @PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
        val bundle = ResourceBundle.getBundle(BUNDLE, language.locale, SerialBundle::class.java.classLoader, bundleControl)
        val pattern = bundle.getString(key)
        return MessageFormat(pattern, language.locale).format(params)
    }
}
