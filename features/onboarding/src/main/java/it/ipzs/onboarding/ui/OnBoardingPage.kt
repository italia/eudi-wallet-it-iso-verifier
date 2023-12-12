package it.ipzs.onboarding.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import it.ipzs.onboarding.R
import it.ipzs.theme.AppTheme
import it.ipzs.theme.specs.ThemeSpecs
import it.ipzs.utils.resourceToAnnotatedString

data class OnboardingPageModel(
    @DrawableRes val img: Int,
    @StringRes val title: Int,
    @StringRes val text: Int,
    val withButton: Boolean
)

@Composable
internal fun OnBoardingPage(
    pageModel: OnboardingPageModel
){

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Image(
            modifier = Modifier.fillMaxWidth().weight(.65f),
            painter = painterResource(pageModel.img),
            contentDescription = null
        )

        Column(
            modifier = Modifier.fillMaxWidth().weight(.35f),
            horizontalAlignment = Alignment.CenterHorizontally
        ){

            Text(
                modifier = Modifier.padding(top = ThemeSpecs.Dimensions.u100)
                    .padding(horizontal = ThemeSpecs.Dimensions.u100),
                text = resourceToAnnotatedString(id = pageModel.title),
                style = MaterialTheme.typography.headlineSmall.copy(
                    textAlign = TextAlign.Center
                )
            )

            Text(
                modifier = Modifier.padding(top = ThemeSpecs.Dimensions.u100)
                    .padding(horizontal = ThemeSpecs.Dimensions.u100),
                text = resourceToAnnotatedString(id = pageModel.text),
                style = MaterialTheme.typography.bodyMedium.copy(
                    textAlign = TextAlign.Center
                )
            )

        }

    }

}

@Composable
@Preview
private fun OnBoardingPagePreview(){
    
    AppTheme {
        
        OnBoardingPage(pageModel = OnboardingPageModel(
            R.drawable.onboarding_1,
            R.string.onboarding_1_title,
            R.string.onboarding_1_text,
            false
        )
        )
        
    }
    
}