package it.ipzs.scan.drivelicense.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import coil.compose.AsyncImage
import it.ipzs.scan.R
import it.ipzs.scan.drivelicense.data.model.VerificationSpecs
import it.ipzs.theme.specs.ThemeSpecs
import it.ipzs.utils.roundToPx
import it.ipzs.utils.toDpSize
import it.ipzs.utils.withDensity

private val driveLicenseFontFamily = FontFamily(
    Font(R.font.readexpro_regular),
    Font(R.font.readexpro_semibold, FontWeight.SemiBold),
    Font(R.font.readexpro_bold, FontWeight.Bold),
    Font(R.font.readexpro_medium, FontWeight.Medium),
    Font(R.font.readexpro_light, FontWeight.Light),
    Font(R.font.readexpro_extralight, FontWeight.ExtraLight),
)

private val labelFontFamily = FontFamily(
    Font(R.font.titilliumweb_regular),
    Font(R.font.titilliumweb_semibold, FontWeight.SemiBold),
    Font(R.font.titilliumweb_bold, FontWeight.Bold),
    Font(R.font.titilliumweb_light, FontWeight.Light),
    Font(R.font.titilliumweb_extralight, FontWeight.ExtraLight),
    Font(R.font.titilliumweb_bolditalic, FontWeight.Bold, FontStyle.Italic),
    Font(R.font.titilliumweb_semibolditalic, FontWeight.SemiBold, FontStyle.Italic),
    Font(R.font.titilliumweb_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.titilliumweb_black, FontWeight.Black),
    Font(R.font.titilliumweb_extralightitalic, FontWeight.ExtraLight, FontStyle.Italic)
)

private val driveLicenseFontBlack = Color(0xff0E0F13)

private val cornerSize = 12.dp
private val shadow = 10.dp

@Composable
fun DriveLicense(
    modifier: Modifier = Modifier,
    name: String,
    surname: String,
    types: String,
    expiration: String,
    picture: Bitmap?,
    verificationSpecs: VerificationSpecs
){

    val textLineHeight = MaterialTheme.typography.titleLarge.lineHeight

    val estimatedLabelHeight = (ThemeSpecs.Dimensions.u100 + shadow + withDensity{
        textLineHeight.toDp()
    }).roundToPx()

    var backgroundSize by remember {
        mutableStateOf(IntSize.Zero)
    }

    Column(modifier = modifier) {

        Box(modifier = Modifier.zIndex(1F)){

            Image(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cornerSize))
                    .onSizeChanged {
                        backgroundSize = it
                    },
                painter = painterResource(id = R.drawable.image_drive_license_background),
                contentDescription = null,
                contentScale = ContentScale.FillWidth
            )

            if(backgroundSize != IntSize.Zero){

                ConstraintLayout(
                    modifier = Modifier
                        .requiredSize(backgroundSize.toDpSize())
                ) {

                    val startGuideline = createGuidelineFromStart(0.40f)

                    val (
                        iconConstraints,
                        pictureConstraints,
                        titleConstraints,
                        nameConstraints,
                        typeConstraints,
                        dateConstraints
                    ) = createRefs()

                    Icon(
                        modifier = Modifier.constrainAs(iconConstraints){
                            top.linkTo(parent.top, ThemeSpecs.Dimensions.u100)
                            end.linkTo(parent.end, ThemeSpecs.Dimensions.u100)
                        },
                        painter = painterResource(id = R.drawable.icon_drive_license_flag),
                        contentDescription = null,
                        tint = Color.Unspecified
                    )

                    Text(
                        modifier = Modifier.constrainAs(titleConstraints){
                            start.linkTo(parent.start, ThemeSpecs.Dimensions.u100)
                            top.linkTo(iconConstraints.top)
                            bottom.linkTo(iconConstraints.bottom)
                        },
                        text = stringResource(id = R.string.drive_license_title),
                        style = TextStyle(
                            fontFamily = driveLicenseFontFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 24.sp,
                            color = driveLicenseFontBlack
                        )
                    )

                    if(picture != null && backgroundSize != IntSize.Zero){

                        AsyncImage(
                            modifier = Modifier.constrainAs(pictureConstraints){
                                start.linkTo(parent.start, ThemeSpecs.Dimensions.u100)
                                end.linkTo(startGuideline, ThemeSpecs.Dimensions.u100)
                                width = Dimension.fillToConstraints
                                bottom.linkTo(parent.bottom, ThemeSpecs.Dimensions.u100)
                                top.linkTo(titleConstraints.bottom, ThemeSpecs.Dimensions.u100)
                                height = Dimension.fillToConstraints
                            },
                            alignment = Alignment.BottomCenter,
                            model = picture,
                            contentDescription = null
                        )

                    }

                    Text(
                        modifier = Modifier.constrainAs(dateConstraints){
                            bottom.linkTo(parent.bottom, ThemeSpecs.Dimensions.u100)
                            start.linkTo(startGuideline)
                            end.linkTo(parent.end)
                            width = Dimension.fillToConstraints
                        },
                        text = stringResource(id = R.string.drive_license_exp, expiration),
                        style = TextStyle(
                            fontFamily = driveLicenseFontFamily,
                            fontSize = 14.sp,
                            color = driveLicenseFontBlack
                        )
                    )

                    Text(
                        modifier = Modifier.constrainAs(typeConstraints){
                            bottom.linkTo(dateConstraints.top)
                            start.linkTo(startGuideline)
                            end.linkTo(parent.end)
                            width = Dimension.fillToConstraints
                        },
                        text = types,
                        style = TextStyle(
                            fontFamily = driveLicenseFontFamily,
                            fontSize = 14.sp,
                            color = driveLicenseFontBlack
                        )
                    )

                    Text(
                        modifier = Modifier.constrainAs(nameConstraints){
                            bottom.linkTo(typeConstraints.top)
                            start.linkTo(startGuideline)
                            end.linkTo(parent.end)
                            width = Dimension.fillToConstraints
                        },
                        text = buildAnnotatedString {
                            append("$name ")
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.SemiBold
                                )
                            ){
                                append(surname)
                            }
                        },
                        style = TextStyle(
                            fontFamily = driveLicenseFontFamily,
                            fontSize = 14.sp,
                            color = driveLicenseFontBlack
                        )
                    )

                }
            }

        }

        Canvas(modifier = Modifier
            .fillMaxWidth()
            .height(shadow)){

            Path()
                .apply {

                    arcTo(
                        rect = Rect(
                            top = size.height - shadow.toPx() -  cornerSize.toPx(),
                            bottom = size.height,
                            left = shadow.toPx()/4,
                            right = 2* shadow.toPx()
                        ),
                        startAngleDegrees = -90F,
                        sweepAngleDegrees = -180F,
                        forceMoveTo = true
                    )

                    lineTo(center.x, size.height - shadow.toPx())

                    lineTo(size.width - 2* shadow.toPx(),  size.height)

                    arcTo(
                        rect = Rect(
                            top = size.height - shadow.toPx() -  cornerSize.toPx(),
                            bottom = size.height,
                            left = size.width - 2* shadow.toPx(),
                            right = size.width - shadow.toPx()/4
                        ),
                        startAngleDegrees = 90F,
                        sweepAngleDegrees = -180F,
                        forceMoveTo = false
                    )

                    drawPath(
                        path = this,
                        Brush.verticalGradient(
                            listOf(
                                verificationSpecs.color,
                                verificationSpecs.color.copy(alpha = 0f)
                            ),
                            startY = size.height - shadow.toPx() -  cornerSize.toPx()/2,
                            endY = size.height
                        )
                    )
                }

        }

        var slideState by remember {
            mutableStateOf(false)
        }

        LaunchedEffect(key1 = true){
            slideState = true
        }

        val slideAnimation by animateIntAsState(
            targetValue = if(slideState) -(shadow + cornerSize).roundToPx() else -estimatedLabelHeight,
            label = "",
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessVeryLow
            )
        )

        Row(
            modifier = Modifier
                .offset {
                    IntOffset(x = 0, y = slideAnimation)
                }
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomEnd = cornerSize, bottomStart = cornerSize))
                .background(verificationSpecs.color)
                .padding(vertical = ThemeSpecs.Dimensions.u50),
            horizontalArrangement = Arrangement.Center,
        ) {

            Icon(
                modifier = Modifier.padding(top = shadow).align(Alignment.CenterVertically),
                painter = painterResource(id = verificationSpecs.iconRes),
                contentDescription = null,
                tint = Color.Unspecified
            )
            
            Spacer(modifier = Modifier.width(ThemeSpecs.Dimensions.u100))

            Text(
                modifier = Modifier.padding(top = shadow),
                text = stringResource(id = verificationSpecs.textRes),
                style = TextStyle(
                    fontFamily = labelFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 24.sp,
                    lineHeight = 36.5.sp,
                    color = verificationSpecs.textColor
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

        }
    }

}