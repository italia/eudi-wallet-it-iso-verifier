package it.ipzs.onboarding.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import it.ipzs.theme.specs.ThemeSpecs

private const val dotMinAlpha = 0.4F
private val circleArrowSize = 34.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun OnBoardingFooter(
    modifier: Modifier = Modifier,
    pageCount: Int,
    currentPage: Int,
    onLeftArrowClicked: () -> Unit,
    onRightArrowClicked: () -> Unit,
    onDotClicked: (Int) -> Unit
){

    val leftArrowColor by animateColorAsState(
        targetValue = if(currentPage == 0) ThemeSpecs.Colors.disableArrowColor else
            MaterialTheme.colorScheme.primary,
        label = ""
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {

        CircleArrow(
            backgroundColor = leftArrowColor,
            onClick = onLeftArrowClicked
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(ThemeSpecs.Dimensions.u75)
        ) {

            repeat(pageCount){ index ->

                val alpha by animateFloatAsState(
                    targetValue = if(index == currentPage) 1F else dotMinAlpha,
                    label = ""
                )

                Spacer(
                    modifier = Modifier
                        .size(ThemeSpecs.Dimensions.u75)
                        .clip(CircleShape)
                        .graphicsLayer {
                            this.alpha = alpha
                        }
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            onDotClicked(index)
                        }
                )

            }

        }

        CircleArrow(
            modifier = Modifier
                .rotate(180F),
            onClick = onRightArrowClicked
        )

    }

}

@Composable
private fun CircleArrow(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
){

    Image(
        modifier = modifier
            .size(circleArrowSize)
            .clip(CircleShape)
            .background(
                color = backgroundColor
            )
            .clickable {
                onClick()
            }
            .padding(ThemeSpecs.Dimensions.u25),
        painter = painterResource(
            id = it.ipzs.theme.R.drawable.icon_arrow_left
        ), contentDescription = null
    )

}