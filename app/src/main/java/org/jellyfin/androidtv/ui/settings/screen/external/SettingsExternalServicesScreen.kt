package org.jellyfin.androidtv.ui.settings.screen.external

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.ExternalServicesPreferences
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.LocalTextStyle
import org.jellyfin.androidtv.ui.base.ProvideTextStyle
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.settings.compat.rememberPreference
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.jellyfin.androidtv.ui.base.dialog.DialogBase
import org.koin.compose.koinInject

@Composable
fun SettingsExternalServicesScreen() {
    val context = LocalContext.current
    val externalPreferences = koinInject<ExternalServicesPreferences>()
    var jellyseerrUrl by rememberPreference(externalPreferences, ExternalServicesPreferences.jellyseerrUrl)
    var jellyseerrApiKey by rememberPreference(externalPreferences, ExternalServicesPreferences.jellyseerrApiKey)
    var radarrUrl by rememberPreference(externalPreferences, ExternalServicesPreferences.radarrUrl)
    var radarrApiKey by rememberPreference(externalPreferences, ExternalServicesPreferences.radarrApiKey)
    var sonarrUrl by rememberPreference(externalPreferences, ExternalServicesPreferences.sonarrUrl)
    var sonarrApiKey by rememberPreference(externalPreferences, ExternalServicesPreferences.sonarrApiKey)

    var dialogState by remember { mutableStateOf<DialogState?>(null) }
    var dialogValue by remember { mutableStateOf("") }

    SettingsColumn {
        item {
            ListSection(
                overlineContent = { Text(stringResource(R.string.pref_external_services).uppercase()) },
                headingContent = { Text(stringResource(R.string.pref_external_services)) },
                captionContent = { Text(stringResource(R.string.pref_external_services_description)) },
            )
        }

        item { ListSection(headingContent = { Text(stringResource(R.string.pref_jellyseerr)) }) }
        item {
            ListButton(
                headingContent = { Text(stringResource(R.string.pref_server_url)) },
                captionContent = { Text(displayValue(jellyseerrUrl)) },
                onClick = {
                    dialogValue = jellyseerrUrl
                    dialogState = DialogState(
                        title = context.getString(R.string.pref_jellyseerr),
                        onSave = { value -> jellyseerrUrl = value },
                    )
                }
            )
        }
        item {
            ListButton(
                headingContent = { Text(stringResource(R.string.pref_api_key)) },
                captionContent = { Text(maskSecret(jellyseerrApiKey)) },
                onClick = {
                    dialogValue = jellyseerrApiKey
                    dialogState = DialogState(
                        title = context.getString(R.string.pref_api_key),
                        onSave = { value -> jellyseerrApiKey = value },
                    )
                }
            )
        }

        item { ListSection(headingContent = { Text(stringResource(R.string.pref_radarr)) }) }
        item {
            ListButton(
                headingContent = { Text(stringResource(R.string.pref_server_url)) },
                captionContent = { Text(displayValue(radarrUrl)) },
                onClick = {
                    dialogValue = radarrUrl
                    dialogState = DialogState(
                        title = context.getString(R.string.pref_radarr),
                        onSave = { value -> radarrUrl = value },
                    )
                }
            )
        }
        item {
            ListButton(
                headingContent = { Text(stringResource(R.string.pref_api_key)) },
                captionContent = { Text(maskSecret(radarrApiKey)) },
                onClick = {
                    dialogValue = radarrApiKey
                    dialogState = DialogState(
                        title = context.getString(R.string.pref_api_key),
                        onSave = { value -> radarrApiKey = value },
                    )
                }
            )
        }

        item { ListSection(headingContent = { Text(stringResource(R.string.pref_sonarr)) }) }
        item {
            ListButton(
                headingContent = { Text(stringResource(R.string.pref_server_url)) },
                captionContent = { Text(displayValue(sonarrUrl)) },
                onClick = {
                    dialogValue = sonarrUrl
                    dialogState = DialogState(
                        title = context.getString(R.string.pref_sonarr),
                        onSave = { value -> sonarrUrl = value },
                    )
                }
            )
        }
        item {
            ListButton(
                headingContent = { Text(stringResource(R.string.pref_api_key)) },
                captionContent = { Text(maskSecret(sonarrApiKey)) },
                onClick = {
                    dialogValue = sonarrApiKey
                    dialogState = DialogState(
                        title = context.getString(R.string.pref_api_key),
                        onSave = { value -> sonarrApiKey = value },
                    )
                }
            )
        }
    }

    SettingsTextInputDialog(
        visible = dialogState != null,
        title = dialogState?.title.orEmpty(),
        value = dialogValue,
        onValueChange = { dialogValue = it },
        onSave = {
            dialogState?.onSave?.invoke(dialogValue.trim())
            dialogState = null
        },
        onDismiss = { dialogState = null },
    )
}

private data class DialogState(
    val title: String,
    val onSave: (String) -> Unit,
)

@Composable
private fun SettingsTextInputDialog(
    visible: Boolean,
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    DialogBase(
        visible = visible,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .width(640.dp)
                .background(JellyfinTheme.colorScheme.surface, JellyfinTheme.shapes.medium)
                .padding(24.dp)
        ) {
            Text(title)
            Spacer(Modifier.height(12.dp))

            ProvideTextStyle(LocalTextStyle.current.copy(fontSize = 16.sp)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                        showKeyboardOnFocus = true,
                    ),
                    cursorBrush = SolidColor(JellyfinTheme.colorScheme.onInputFocused),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(2.dp, JellyfinTheme.colorScheme.inputFocused, JellyfinTheme.shapes.small)
                                .padding(12.dp)
                        ) {
                            if (value.isBlank()) {
                                Text(stringResource(R.string.pref_not_set), color = JellyfinTheme.colorScheme.onInput)
                            }
                            innerTextField()
                        }
                    }
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onSave) { Text(stringResource(R.string.lbl_ok)) }
                Button(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
            }
        }
    }
}

@Composable
private fun displayValue(value: String): String {
    return if (value.isBlank()) stringResource(R.string.pref_not_set) else value
}

@Composable
private fun maskSecret(value: String): String {
    if (value.isBlank()) return stringResource(R.string.pref_not_set)
    if (value.length <= 4) return "****"
    val suffix = value.takeLast(4)
    return "****" + suffix
}
