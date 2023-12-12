package it.ipzs.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

@Composable
fun <R>withDensity(block: Density.() -> R) = with(LocalDensity.current, block)

@Composable
fun Dp.toPx() = withDensity{
    this@toPx.toPx()
}

@Composable
fun Dp.roundToPx() = withDensity{
    this@roundToPx.roundToPx()
}

@Composable
fun Int.toDp() = withDensity{
    this@toDp.toDp()
}

@Composable
fun IntSize.toDpSize(
    additionalWidth: Dp = 0.dp,
    additionalHeight: Dp = 0.dp
) = withDensity{
    val widthDp = width.toDp()
    val heightDp = height.toDp()
    DpSize(widthDp + additionalWidth, heightDp + additionalHeight)
}