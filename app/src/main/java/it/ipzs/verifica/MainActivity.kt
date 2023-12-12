package it.ipzs.verifica

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import dagger.hilt.android.AndroidEntryPoint
import it.ipzs.verifica.ui.App

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContent {
            App{
                if(it) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        if(
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            savedInstanceState == null
        ){

            val time = System.currentTimeMillis()

            val content: View = findViewById(android.R.id.content)
            content.viewTreeObserver.addOnPreDrawListener(
                object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {

                        val currentTime = System.currentTimeMillis()

                        return if (currentTime - time >= 500) {
                            content.viewTreeObserver.removeOnPreDrawListener(this)
                            true
                        } else false
                    }
                }
            )

        }

    }
}