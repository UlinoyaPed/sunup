package org.unifiedpush.distributor.sunup.activities

import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.unifiedpush.android.distributor.ui.state.RegistrationState
import org.unifiedpush.distributor.sunup.R
import org.unifiedpush.distributor.sunup.arrival.ArrivalStore
import org.unifiedpush.distributor.sunup.arrival.PushArrivalNotifier

class ArrivalSettingsState {
    val enabled = mutableStateMapOf<String, Boolean>()
    val saving = mutableStateMapOf<String, Boolean>()
    var update: (String, Boolean) -> Unit = { _, _ -> }
}

@Composable
fun rememberArrivalSettings(registrations: List<RegistrationState>): ArrivalSettingsState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { ArrivalSettingsState() }
    val packages = registrations.filter { !it.copyable }.map { it.app.packageName }.toSet()
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    LaunchedEffect(packages, refresh) {
        val settings = withContext(Dispatchers.IO) {
            runCatching { ArrivalStore(context).use { db -> packages.associateWith(db::isEnabled) } }.getOrNull()
        }
        settings?.forEach { (pkg, enabled) ->
            if (state.saving[pkg] != true) state.enabled[pkg] = enabled
        }
    }
    state.update = { packageName, enabled ->
        state.saving[packageName] = true
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    ArrivalStore(context).use { it.setEnabled(packageName, enabled) }
                    if (!enabled) PushArrivalNotifier.cancel(context, packageName)
                }.isSuccess
            }
            if (success) {
                state.enabled[packageName] = enabled
            } else {
                Toast.makeText(context, R.string.arrival_save_failed, Toast.LENGTH_LONG).show()
            }
            state.saving[packageName] = false
        }
    }
    return state
}

@Composable
fun ArrivalSwitch(packageName: String, state: ArrivalSettingsState) {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(NotificationManager::class.java) }
    fun blocked(): Boolean = !manager.areNotificationsEnabled() ||
        (
            Build.VERSION.SDK_INT >= 26 &&
                manager.getNotificationChannel(PushArrivalNotifier.CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE
            )
    var notificationsBlocked by remember { mutableStateOf(blocked()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { notificationsBlocked = blocked() }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsBlocked = blocked()
    }
    val enabled = state.enabled[packageName] == true
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.arrival_setting),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            Switch(
                checked = enabled,
                enabled = state.enabled.containsKey(packageName) && state.saving[packageName] != true,
                onCheckedChange = {
                    state.update(packageName, it)
                    if (it && Build.VERSION.SDK_INT >= 33 && !manager.areNotificationsEnabled()) {
                        requestPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )
        }
        if (enabled && notificationsBlocked) {
            Text(stringResource(R.string.arrival_permission_hint), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                val intent = if (Build.VERSION.SDK_INT >= 26) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
                }
                context.startActivity(intent)
            }) {
                Text(stringResource(R.string.arrival_open_settings))
            }
        }
    }
}
