package com.example.qmemo.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.example.qmemo.R

/**
 * WhatsApp-style overflow menu (three dots) for TopAppBars.
 * Centralises access to Help, Settings, and future context-aware actions.
 */
@Composable
fun TopBarOverflowMenu(
    onHelpClick: () -> Unit,
    onSettingsClick: () -> Unit,
    extraItems: (@Composable (dismissMenu: () -> Unit) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val dismissMenu = { expanded = false }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.cd_more_options),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = dismissMenu
        ) {
            extraItems?.invoke(dismissMenu)
            if (extraItems != null) {
                HorizontalDivider()
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.help_title)) },
                onClick = {
                    dismissMenu()
                    onHelpClick()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null
                    )
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_title)) },
                onClick = {
                    dismissMenu()
                    onSettingsClick()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null
                    )
                }
            )
        }
    }
}
