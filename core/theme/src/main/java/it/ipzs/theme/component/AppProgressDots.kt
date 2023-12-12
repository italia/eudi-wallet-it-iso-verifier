package it.ipzs.theme.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import it.ipzs.theme.AppTheme
import it.ipzs.theme.specs.ThemeSpecs
import it.ipzs.utils.toPx

private val dotSize = 15.dp
private const val DOT_COUNT = 3
private const val DELAY = 150
private const val DURATION = 500

@Composable
fun AppProgressDots(
    modifier: Modifier = Modifier
){

    val dotSizePixels = dotSize.toPx()
    val targetAnimationValue = dotSizePixels * 2

    val infiniteTransition = rememberInfiniteTransition(label = "")

    val transitionValue0 by infiniteTransition.getTransition(
        targetAnimationValue = targetAnimationValue,
        delay = 0
    )

    val transitionValue1 by infiniteTransition.getTransition(
        targetAnimationValue = targetAnimationValue,
        delay = DELAY
    )

    val transitionValue2 by infiniteTransition.getTransition(
        targetAnimationValue = targetAnimationValue,
        delay = DELAY * 2
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(dotSize * 3),
        horizontalArrangement = Arrangement.spacedBy(
            ThemeSpecs.Dimensions.u50, Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.Bottom
    ) {

        repeat(DOT_COUNT){

            Spacer(
                modifier = Modifier
                    .graphicsLayer {

                        translationY = when (it) {
                            0 -> -transitionValue0
                            1 -> -transitionValue1
                            else -> -transitionValue2
                        }
                    }
                    .size(
                        dotSize
                    )
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(
                            alpha = when (it) {
                                0 -> 0.3f
                                1 -> 0.7f
                                else -> 1f
                            }
                        )
                    )
            )

        }

    }

}

@Composable
private fun InfiniteTransition.getTransition(
    targetAnimationValue: Float,
    delay: Int
) = animateFloat(
    initialValue = 0f,
    targetValue = targetAnimationValue,
    animationSpec = InfiniteRepeatableSpec(
        animation = tween(
            durationMillis = DURATION,
            easing = FastOutSlowInEasing
        ),
        repeatMode = RepeatMode.Reverse,
        initialStartOffset = StartOffset(
            delay
        )
    ), label = ""
)

@Composable
@Preview
private fun AppProgressDotsPreview(){

    AppTheme {

        Box(
            modifier = Modifier
                .padding(ThemeSpecs.Dimensions.u100)
        ) {

            AppProgressDots()

        }

    }

}