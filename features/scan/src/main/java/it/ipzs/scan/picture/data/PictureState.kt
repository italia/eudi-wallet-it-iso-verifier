package it.ipzs.scan.picture.data

import android.graphics.Bitmap
import it.ipzs.architecture.viewmodel.BaseState

data class PictureState(
    val fullName: String,
    val picture: Bitmap
): BaseState