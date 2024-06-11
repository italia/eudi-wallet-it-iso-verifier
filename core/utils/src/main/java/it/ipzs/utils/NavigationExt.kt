package it.ipzs.utils

import androidx.navigation.NavHostController
import androidx.navigation.NavOptions

fun NavHostController.navigateAndPop(
    to: String,
    popTo: String,
    inclusive: Boolean
){
    navigate(
        route = to,
        navOptions = NavOptions
            .Builder()
            .setPopUpTo(
                route = popTo,
                inclusive = inclusive
            )
            .build()
    )
}
