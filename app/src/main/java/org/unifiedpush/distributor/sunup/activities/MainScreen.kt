/*
 * Adapted from UnifiedPush android-distributor 0.7.6 (Apache-2.0).
 * https://codeberg.org/UnifiedPush/android-distributor
 * Sunup modifications: per-application push arrival controls.
 */
package org.unifiedpush.distributor.sunup.activities

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.Flow
import org.unifiedpush.android.distributor.ui.R
import org.unifiedpush.android.distributor.ui.compose.CardDisableBatteryOptimisation
import org.unifiedpush.android.distributor.ui.compose.CardDisabledForMigration
import org.unifiedpush.android.distributor.ui.compose.DistribMigrationDialogs
import org.unifiedpush.android.distributor.ui.compose.PermissionsUi
import org.unifiedpush.android.distributor.ui.compose.RegistrationListHeading
import org.unifiedpush.android.distributor.ui.vm.DistribMigrationViewModel
import org.unifiedpush.android.distributor.ui.vm.MainViewModel

private const val TAG = "MainScreen"

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    migrationViewModel: DistribMigrationViewModel,
    uiFlow: Flow<String>?
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var firstResume by remember { mutableStateOf(true) }
    var showPermissionDialog by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        uiFlow?.collect {
            Log.d(TAG, "Refreshing registrations from uiFlow")
            viewModel.refreshRegistrations()
        }
    }
    LaunchedEffect(Unit) {
        migrationViewModel.refreshMigrationState()
        viewModel.refreshRegistrations()
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // Filter out resume after creation - it allows to run the refresh earlier
            if (firstResume) {
                firstResume = false
                return@repeatOnLifecycle
            }
            Log.d(TAG, "Refreshing registrations on resume")
            viewModel.refreshRegistrations()
        }
    }
    if (showPermissionDialog) {
        PermissionsUi {
            showPermissionDialog = false
        }
    }
    val arrivalSettings = rememberArrivalSettings(viewModel.registrationsViewModel.state.list)
    LazyColumn(
        modifier = Modifier
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            .fillMaxSize(),
        horizontalAlignment = Alignment.Start
    ) {
        if (migrationViewModel.state.migrated) {
            item {
                CardDisabledForMigration {
                    migrationViewModel.reactivateUnifiedPush()
                }
            }
            return@LazyColumn
        }

        item {
            CardDisableBatteryOptimisation(viewModel.batteryOptimisationViewModel)
        }

        viewModel.mainUiState.accountName?.let { accountName ->
            item {
                Text(
                    modifier = Modifier.padding(8.dp),
                    text = stringResource(R.string.main_account_desc, accountName),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            RegistrationListHeading(
                modifier = Modifier
                    .padding(8.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        viewModel.addDebugClick()
                    }
            )
        }
        registrationList(viewModel.registrationsViewModel, arrivalSettings)
    }
    viewModel.mainUiState.debugInfo?.let {
        DebugDialog(it) {
            viewModel.dismissDebugInfo()
        }
    }
    if (migrationViewModel.state.canMigrate) {
        DistribMigrationDialogs(migrationViewModel)
    }
}

@Composable
fun DebugDialog(content: String, onDismissRequest: () -> Unit) {
    AlertDialog(
        title = { Text("Debug") },
        text = {
            SelectionContainer {
                Text(content)
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
}
