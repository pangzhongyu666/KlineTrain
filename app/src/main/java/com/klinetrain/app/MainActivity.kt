package com.klinetrain.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.klinetrain.app.ui.AppNav
import com.klinetrain.app.ui.theme.KlineTrainTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KlineTrainTheme {
                AppNav()
            }
        }
    }
}
