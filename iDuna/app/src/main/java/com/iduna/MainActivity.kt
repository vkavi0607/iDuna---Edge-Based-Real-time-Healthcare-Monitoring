package com.iduna

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iduna.ui.navigation.IdunaNavGraph
import com.iduna.ui.theme.IdunaTheme

class MainActivity : ComponentActivity() {
    private val viewModel: IdunaViewModel by viewModels {
        IdunaViewModel.factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            IdunaTheme(darkTheme = settings.darkModeEnabled) {
                IdunaNavGraph(viewModel = viewModel)
            }
        }
    }
}
