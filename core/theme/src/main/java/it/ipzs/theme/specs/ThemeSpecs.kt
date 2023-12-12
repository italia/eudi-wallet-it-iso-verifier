package it.ipzs.theme.specs

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object ThemeSpecs {

    object Colors{

        val mainColor = Color(0xff224A5D)
        val onMainColor = Color(0xffFFFFFF)
        val surfaceColor = Color(0xffFFFFFF)
        val onSurfaceColor = Color(0xff000000)
        val disableArrowColor = Color(0xffE9EDEF)
        val dividerColor = Color(0xffFCFCFC)
        val errorColor = Color(0xff93000A)
        val validColor = Color(0xffE1F4E1)
    }

    object Dimensions{

        /** 16dp */
        val u100 = 16.dp
        /** 8dp */
        val u50 = u100 /2
        /** 4dp */
        val u25 = u100 /4
        val u12 = u25 /2
        /** 12dp */
        val u75 = u50 + u25
        /** 24dp */
        val u150 = u50 + u100
        /** 32dp */
        val u200 = u100 *2
        /** 48dp */
        val u300 = u100 *3

    }

}