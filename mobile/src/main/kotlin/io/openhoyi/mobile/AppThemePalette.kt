package io.openhoyi.mobile

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Color
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes

/** Stable brand selection. Day/night is a separate input from the owning context. */
internal enum class AppThemePalette(
    val key: String,
    @StringRes val label: Int,
    @StyleRes val nativeOverlay: Int,
    @ColorRes val accent: Int,
    @ColorRes val primary: Int,
    @ColorRes val selected: Int,
    @ColorRes val switchOn: Int,
) {
    CLASSIC("classic", R.string.theme_palette_classic, R.style.MobilePaletteClassic,
        R.color.mobile_accent, R.color.mobile_primary_button, R.color.mobile_accent_soft, R.color.palette_classic_switch_on),
    SEA_SALT("sea-salt", R.string.theme_palette_sea_salt, R.style.MobilePaletteSeaSalt,
        R.color.palette_sea_salt_accent, R.color.palette_sea_salt_primary, R.color.palette_sea_salt_selected, R.color.palette_sea_salt_switch_on),
    COFFEE("coffee", R.string.theme_palette_coffee, R.style.MobilePaletteCoffee,
        R.color.palette_coffee_accent, R.color.palette_coffee_primary, R.color.palette_coffee_selected, R.color.palette_coffee_switch_on),
    GRAPHITE("graphite", R.string.theme_palette_graphite, R.style.MobilePaletteGraphite,
        R.color.palette_graphite_accent, R.color.palette_graphite_primary, R.color.palette_graphite_selected, R.color.palette_graphite_switch_on);

    companion object {
        fun restore(key: String?) = entries.firstOrNull { it.key == key } ?: CLASSIC
    }
}

/** Immutable selection of one window (or only one preview), not the latest global preference. */
internal class AppAppearanceContext(base: Context, val appearance: AppAppearance) : ContextWrapper(base)

internal fun Context.themePalette(): AppThemePalette {
    var current = this
    while (current is ContextWrapper) {
        if (current is AppAppearanceContext) return current.appearance.palette
        val base = current.baseContext ?: break
        if (base === current) break
        current = base
    }
    return AppThemePalette.CLASSIC
}

/** Legacy resource-based views share the palette, but only these three explicit brand roles.
 * Chart, warning, success and danger resources still resolve verbatim.
 */
internal fun Context.appColor(@ColorRes role: Int): Int = getColor(when (role) {
    R.color.mobile_accent -> themePalette().accent
    R.color.mobile_primary_button -> themePalette().primary
    R.color.mobile_accent_soft -> themePalette().selected
    else -> role
})

internal class AppPalette(private val context: Context, val choice: AppThemePalette = context.themePalette()) {
    private val classic = choice == AppThemePalette.CLASSIC
    private val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    private fun classicTone(@ColorRes classicColor: Int, other: Int) = if (classic) context.getColor(classicColor) else other

    val accent = context.getColor(choice.accent)
    val primary = context.getColor(choice.primary)
    val selected = context.getColor(choice.selected)
    val onPrimary = Color.WHITE
    val switchOn = context.getColor(choice.switchOn)
    val switchOff = if (dark) 0xff485354.toInt() else 0xffcdd3d1.toInt()

    // The original palette had separately approved product and home variants. The new palettes
    // share roles between screens and keep the neutral surfaces and semantic ink independent.
    val productAccent = classicTone(R.color.palette_classic_product_accent, accent)
    val productPrimary = classicTone(R.color.palette_classic_product_primary, primary)
    val productSelected = classicTone(R.color.palette_classic_product_selected, selected)
    val homeAccent = classicTone(R.color.palette_classic_home_accent, accent)
    val homePrimary = classicTone(R.color.palette_classic_home_primary, primary)
    val homeSelected = classicTone(R.color.palette_classic_home_selected, selected)
    val homePresetText = classicTone(R.color.palette_classic_home_preset, accent)
    val homeDialProgress = classicTone(R.color.palette_classic_home_dial, accent)
    val liveStart = classicTone(R.color.palette_classic_live_start, primary)
}
