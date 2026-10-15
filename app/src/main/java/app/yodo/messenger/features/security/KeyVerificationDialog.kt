package app.yodo.messenger.features.security

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.yodo.messenger.R
import app.yodo.messenger.core.crypto.ConversationSecurity
import app.yodo.messenger.core.crypto.formattedFingerprint

@Composable
fun KeyVerificationDialog(
    state: ConversationSecurity?, loading: Boolean, error: String?, onDismiss: () -> Unit,
    onRefresh: () -> Unit, onVerifyPeer: (String) -> Unit, onActivateOwn: (String?) -> Unit
) {
    var compared by remember(state?.peerFingerprint) { mutableStateOf(false) }
    var ownReplacementAccepted by remember(state?.ownFingerprint, state?.publishedOwnFingerprint) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.security_keys_title)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.security_scope_notice))
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state?.let { info ->
                    Text(stringResource(R.string.security_own_key), style = MaterialTheme.typography.titleSmall)
                    SelectionContainer { Text(formattedFingerprint(info.ownFingerprint), style = MaterialTheme.typography.bodySmall) }
                    Text(stringResource(R.string.security_peer_key), style = MaterialTheme.typography.titleSmall)
                    SelectionContainer { Text(formattedFingerprint(info.peerFingerprint), style = MaterialTheme.typography.bodySmall) }
                    if (info.peerKeyChanged) {
                        Text(stringResource(R.string.security_key_changed), color = MaterialTheme.colorScheme.error)
                        Text(stringResource(R.string.security_old_peer_key), style = MaterialTheme.typography.labelMedium)
                        info.previousPeerFingerprint?.let { old ->
                            SelectionContainer { Text(formattedFingerprint(old), style = MaterialTheme.typography.bodySmall) }
                        }
                    } else Text(stringResource(if (info.peerVerified) R.string.security_verified_status else R.string.security_tofu_status))
                    Text(stringResource(R.string.security_compare_instruction))
                    Row {
                        Checkbox(checked = compared, onCheckedChange = { compared = it }, enabled = !loading)
                        Text(stringResource(R.string.security_compared_ack), Modifier.weight(1f).padding(top = 10.dp))
                    }
                    TextButton(enabled = compared && !loading && error == null, onClick = { onVerifyPeer(info.peerFingerprint); compared = false }) {
                        Text(stringResource(R.string.security_verify_peer))
                    }
                    if (!info.ownKeyActive) {
                        Text(stringResource(R.string.security_own_key_warning), color = MaterialTheme.colorScheme.error)
                        Row {
                            Checkbox(checked = ownReplacementAccepted, onCheckedChange = { ownReplacementAccepted = it }, enabled = !loading)
                            Text(stringResource(R.string.security_own_replace_ack), Modifier.weight(1f).padding(top = 10.dp))
                        }
                        TextButton(enabled = ownReplacementAccepted && !loading && error == null,
                            onClick = { onActivateOwn(info.publishedOwnFingerprint); ownReplacementAccepted = false }) {
                            Text(stringResource(R.string.security_activate_own))
                        }
                    }
                }
                TextButton(enabled = !loading, onClick = onRefresh) { Text(stringResource(R.string.security_refresh)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_close)) } }
    )
}
