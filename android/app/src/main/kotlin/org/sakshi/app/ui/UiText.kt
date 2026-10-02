package org.sakshi.app.ui

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources

/**
 * Text chosen by a view model or a pure mapping function and turned into words only at the edge, so the mapping can be
 * unit-tested against the string resources without a composition. An argument is a string, a number or another
 * [UiText], which is resolved first.
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = emptyList()) : UiText

    /** Words that came from the evidence or from the person, shown exactly as they are. */
    data class Raw(val value: String) : UiText
}

fun res(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): UiText = UiText.Plural(id, count, args.toList())

fun UiText.resolve(resources: Resources): String = when (this) {
    is UiText.Res -> resources.getString(id, *resolvedArgs(args, resources))
    is UiText.Plural -> resources.getQuantityString(id, count, *resolvedArgs(args, resources))
    is UiText.Raw -> value
}

private fun resolvedArgs(args: List<Any>, resources: Resources): Array<Any> =
    Array(args.size) { index -> (args[index] as? UiText)?.resolve(resources) ?: args[index] }

@Composable
fun UiText.text(): String {
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    return remember(this, configuration) { resolve(resources) }
}
