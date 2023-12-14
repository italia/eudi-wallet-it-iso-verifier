package it.ipzs.onboarding.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import it.ipzs.onboarding.R
import it.ipzs.theme.AppTheme
import it.ipzs.theme.component.AppButton
import it.ipzs.theme.component.AppButtonDefaults
import it.ipzs.theme.component.AppTextButton
import it.ipzs.theme.specs.ThemeSpecs
import kotlinx.coroutines.launch


private val pages = listOf(
    OnboardingPageModel(
        R.drawable.onboarding_1,
        R.string.onboarding_1_title,
        R.string.onboarding_1_text,
        false
    ),
    OnboardingPageModel(
        R.drawable.onboarding_2,
        R.string.onboarding_2_title,
        R.string.onboarding_2_text,
        false
    ),
    OnboardingPageModel(
        R.drawable.onboarding_3,
        R.string.onboarding_3_title,
        R.string.onboarding_3_text,
        true
    ),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    onButtonClick: () -> Unit
){

    val pagerState = rememberPagerState {
        pages.size
    }

    val scope = rememberCoroutineScope()

    val upperRowArrangement = if(pagerState.currentPage < pagerState.pageCount -1)
        Arrangement.End
    else Arrangement.Start

    val upperButtonText = if(pagerState.currentPage < pagerState.pageCount -1)
        stringResource(id = R.string.onboarding_forward)
    else stringResource(id = R.string.onboarding_backward)

    val onClickAction = if(pagerState.currentPage < pagerState.pageCount -1) {
        {
            scope.launch {
                pagerState.animateScrollToPage(pages.size - 1)
            }
        }
    } else {
        {
            scope.launch {
                pagerState.animateScrollToPage(0)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ThemeSpecs.Dimensions.u100),
            horizontalArrangement = upperRowArrangement
        ){
            AppTextButton(
                text = upperButtonText,
                onClick = {
                    onClickAction()
                }
            )
        }

        HorizontalPager(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1F),
            state = pagerState
        ) {

            OnBoardingPage(
                pageModel = pages[it]
            )

        }

        Crossfade(
            targetState = pagerState.currentPage,
            label = ""
        ) {

            val footerModifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = ThemeSpecs.Dimensions.u100,
                    vertical = ThemeSpecs.Dimensions.u150
                )

            when{
                it < pagerState.pageCount - 1 -> OnBoardingFooter(
                    modifier = footerModifier.height(AppButtonDefaults.buttonHeight),
                    pageCount = pagerState.pageCount,
                    currentPage = pagerState.currentPage,
                    onLeftArrowClicked = {
                        scope.launch {
                            (pagerState.currentPage -1)
                                .takeIf { it >= 0 }?.let {
                                    pagerState.animateScrollToPage(it)
                                }
                        }
                    },
                    onRightArrowClicked = {
                        scope.launch {
                            (pagerState.currentPage + 1)
                                .takeIf { it < pagerState.pageCount }?.let {
                                    pagerState.animateScrollToPage(it)
                                }
                        }
                    },
                    onDotClicked = {
                        scope.launch {
                            pagerState.animateScrollToPage(it)
                        }
                    }
                )
                else -> AppButton(
                    modifier = footerModifier,
                    text = stringResource(id = R.string.onboarding_button),
                    onClick = onButtonClick
                )
            }


        }

    }

}

@Composable
@Preview
private fun OnboardingScreenPreview(){

    AppTheme {

        OnboardingScreen{

        }

    }

}