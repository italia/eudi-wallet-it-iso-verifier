package it.ipzs.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import it.ipzs.settings.R

enum class SettingSpecs(
    @DrawableRes val iconRes: Int,
    @StringRes val stringRes: Int
){

    Privacy(
        it.ipzs.theme.R.drawable.icon_settings_privacy,
        R.string.settings_privacy
    ),
    Security(
        it.ipzs.theme.R.drawable.icon_settings_security,
        R.string.settings_security
    ),
    Info(
        it.ipzs.theme.R.drawable.icon_settings_info,
        R.string.settings_info
    ),
    Tutorial(
        it.ipzs.theme.R.drawable.icon_settings_tutorial,
        R.string.settings_tutorial
    ),
    Debug(
        it.ipzs.theme.R.drawable.icon_settings_debug,
        R.string.settings_debug
    )

}