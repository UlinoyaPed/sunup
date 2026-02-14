package org.unifiedpush.distributor.sunup.activities

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.unifiedpush.android.distributor.ipc.InternalMessenger
import org.unifiedpush.android.distributor.ipc.subscribeUiActions
import org.unifiedpush.android.distributor.ui.screen.App
import org.unifiedpush.android.distributor.ui.vm.ThemeViewModel
import org.unifiedpush.android.distributor.ui.vm.ViewModelFactory
import org.unifiedpush.distributor.sunup.SunupAppConfig
import org.unifiedpush.distributor.sunup.activities.ui.theme.AppTheme
import org.unifiedpush.distributor.sunup.utils.TAG

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val messenger = InternalMessenger(this)
        val uiFlow = subscribeUiActions(this)
        val appConfig = SunupAppConfig()
        jobs.removeAll {
            Log.d(TAG, "Cancelling exitProcess job")
            it.cancel()
            true
        }
        enableEdgeToEdge()

        setContent {
            val factory = ViewModelFactory(
                context = this,
                appConfig = appConfig,
                messenger = messenger
            )
            val themeViewModel = viewModel<ThemeViewModel>(factory = factory)
            AppTheme(
                dynamicColor = themeViewModel.dynamicColors
            ) {
                App(
                    appConfig = appConfig,
                    factory = factory,
                    themeViewModel = themeViewModel,
                    uiFlow = uiFlow
                )
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Destroy")
        super.onDestroy()
        jobs += CoroutineScope(Dispatchers.Main + Job()).launch {
            delay(10_000)
            exitProcess(0)
        }
    }

    companion object {
        val jobs = emptyList<Job>().toMutableList()
    }
}
