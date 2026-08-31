package io.github.devildare687.stealthmesh.stealthmesh

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.devildare687.stealthmesh.util.AppConstants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StealthMeshSettingsSheet(
    currentDisplayName: String,
    onDismiss: () -> Unit,
    onSaveDisplayName: (String) -> Result<String>
) {
    var showDisplayNameEditor by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Settings",
                    fontFamily = StealthMeshChatDisplayFont,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Tune how StealthMesh shows up on your nearby mesh.",
                    fontFamily = StealthMeshChatDisplayFont,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                StealthMeshSectionLabel("Identity")
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDisplayNameEditor = true }
                        .testTag("display_name_setting")
                ) {
                    Column {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = StealthMeshChatAccent.copy(alpha = 0.16f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = null,
                                    tint = StealthMeshChatAccent,
                                    modifier = Modifier.padding(9.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "Display name",
                                    fontFamily = StealthMeshChatDisplayFont,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = currentDisplayName,
                                    fontFamily = StealthMeshChatDisplayFont,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("current_display_name")
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Edit display name",
                                tint = StealthMeshChatAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                        Text(
                            text = "Nearby people see this name. It isn’t an account or a globally unique username.",
                            fontFamily = StealthMeshChatDisplayFont,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }

    if (showDisplayNameEditor) {
        DisplayNameEditorDialog(
            currentDisplayName = currentDisplayName,
            onCancel = { showDisplayNameEditor = false },
            onSaveDisplayName = { candidate ->
                onSaveDisplayName(candidate).onSuccess {
                    showDisplayNameEditor = false
                }
            }
        )
    }
}

@Composable
private fun DisplayNameEditorDialog(
    currentDisplayName: String,
    onCancel: () -> Unit,
    onSaveDisplayName: (String) -> Result<String>
) {
    var draft by rememberSaveable(currentDisplayName) { mutableStateOf(currentDisplayName) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    fun save() {
        onSaveDisplayName(draft)
            .onFailure { error ->
                errorMessage = error.message ?: "Display name could not be saved"
            }
    }

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Display name",
                fontFamily = StealthMeshChatDisplayFont,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Pick the name nearby people will see on the mesh.",
                    fontFamily = StealthMeshChatDisplayFont,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = {
                        draft = it
                        errorMessage = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("display_name_input"),
                    singleLine = true,
                    label = { Text("Display name") },
                    isError = errorMessage != null,
                    supportingText = {
                        Text(
                            text = errorMessage
                                ?: "${draft.trim().length}/${AppConstants.UI.MAX_NICKNAME_LENGTH} characters"
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = StealthMeshChatAccent,
                        focusedLabelColor = StealthMeshChatAccent,
                        cursorColor = StealthMeshChatAccent
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = ::save,
                enabled = draft.trim().isNotEmpty(),
                modifier = Modifier.testTag("save_display_name")
            ) {
                Text("Save", color = StealthMeshChatAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
