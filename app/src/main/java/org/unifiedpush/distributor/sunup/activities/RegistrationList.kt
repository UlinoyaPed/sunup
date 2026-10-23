/*
 * Adapted from UnifiedPush android-distributor 0.7.6 (Apache-2.0).
 * https://codeberg.org/UnifiedPush/android-distributor
 * Sunup modifications: per-application push arrival controls.
 */
package org.unifiedpush.distributor.sunup.activities

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.unifiedpush.android.distributor.ui.R
import org.unifiedpush.android.distributor.ui.compose.ApplicationRow
import org.unifiedpush.android.distributor.ui.compose.MaterialList
import org.unifiedpush.android.distributor.ui.vm.RegistrationsViewModel

/**
 * Column with the list of registrations
 *
 * @param viewModel [org.unifiedpush.android.distributor.ui.vm.RegistrationsViewModel]
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
fun LazyListScope.registrationList(viewModel: RegistrationsViewModel, arrivalSettings: ArrivalSettingsState) {
    item {
        Spacer(Modifier.padding(top = 8.dp))
    }
    MaterialList(
        viewModel.state.list.sortedBy { it.app.packageName },
        onClick = {
            if (viewModel.state.selectionCount > 0) {
                viewModel.toggleSelection(it.token)
            }
        },
        onLongClick = {
            viewModel.toggleSelection(it.token)
        },
        onLongClickLabel = {
            stringResource(
                R.string.list_registrations_alt_long_click_label
            )
        }
    ) {
        Column {
            Row {
                ApplicationRow(it.app, modifier = Modifier.weight(1f))
                if (it.copyable) {
                    Icon(
                        modifier = Modifier
                            .clickable {
                                viewModel.copyEndpoint(it.token)
                            }
                            .align(Alignment.CenterVertically),
                        painter = painterResource(R.drawable.baseline_content_copy_24),
                        contentDescription = stringResource(
                            R.string.button_copy_endpoint_description
                        )
                    )
                } else {
                    NotificationCount(it.msgCount)
                }
                Spacer(Modifier.width(16.dp))
            }
            if (!it.copyable) {
                ArrivalSwitch(it.app.packageName, arrivalSettings)
            }
        }
    }
    item {
        Spacer(Modifier.padding(vertical = 8.dp))
    }
}

@Composable
private fun NotificationCount(count: Int) {
    Row(modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.width(2.dp))
        Icon(
            Icons.Filled.Notifications,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(16.dp)
                .align(Alignment.CenterVertically)
        )
    }
}
