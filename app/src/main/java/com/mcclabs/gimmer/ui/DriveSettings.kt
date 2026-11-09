package com.mcclabs.gimmer.ui

import android.accounts.AccountManager
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.mcclabs.gimmer.backup.BACKUPS_PER_DEVICE
import com.mcclabs.gimmer.backup.BackupInterval
import com.mcclabs.gimmer.backup.BackupStatus
import com.mcclabs.gimmer.backup.GOOGLE_ACCOUNT_TYPE
import com.mcclabs.gimmer.backup.driveAccessToken
import com.mcclabs.gimmer.backup.driveAuthorizationRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

private enum class DriveAction { BACKUP, RESTORE, ENABLE }

@Composable
internal fun DriveSettings(viewModel: WorkoutViewModel) {
    val context = LocalContext.current
    val settings by viewModel.automaticBackup.collectAsStateWithLifecycle()
    val busy by viewModel.cloudBusy.collectAsStateWithLifecycle()
    val restoreOptions by viewModel.restoreOptions.collectAsStateWithLifecycle()
    var action by rememberSaveable { mutableStateOf<DriveAction?>(null) }
    var account by rememberSaveable { mutableStateOf<String?>(null) }
    var waitingForConsent by rememberSaveable { mutableStateOf(false) }
    var selectingAccount by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf("") }
    val authClient = remember(context) { Identity.getAuthorizationClient(context) }

    fun resetAuthorization() {
        action = null
        account = null
        waitingForConsent = false
    }
    fun finishAuthorization(result: AuthorizationResult) {
        val completedAction = action
        val selectedAccount = account
        val token = result.driveAccessToken()
        resetAuthorization()
        if (token == null) {
            status = "Google hesabı yetkilendirilemedi."
            return
        }
        when (completedAction) {
            DriveAction.BACKUP -> viewModel.backupToDrive(token, selectedAccount)
            DriveAction.RESTORE -> viewModel.listDriveBackups(token)
            DriveAction.ENABLE -> selectedAccount?.let(viewModel::enableAutomaticBackup)
            null -> Unit
        }
    }
    fun authorize(nextAction: DriveAction, selectedAccount: String?) {
        status = ""
        account = selectedAccount
        action = nextAction
    }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val data = result.data
        if (action != null && result.resultCode == Activity.RESULT_OK && data != null) {
            try {
                finishAuthorization(authClient.getAuthorizationResultFromIntent(data))
            } catch (_: Exception) {
                resetAuthorization()
                status = "Google hesabı yetkilendirilemedi. Lütfen yeniden deneyin."
            }
        } else {
            resetAuthorization()
            status = "Google Drive erişimi iptal edildi."
        }
    }
    val accountPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        selectingAccount = false
        val picked = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        val type = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE)
        if (result.resultCode == Activity.RESULT_OK && !picked.isNullOrBlank() && type == GOOGLE_ACCOUNT_TYPE) {
            authorize(DriveAction.ENABLE, picked)
        }
    }
    LaunchedEffect(action, account, waitingForConsent) {
        if (action == null || waitingForConsent) return@LaunchedEffect
        try {
            val result = authClient.authorize(driveAuthorizationRequest(account)).await()
            val intent = result.pendingIntent
            if (result.hasResolution() && intent != null) {
                waitingForConsent = true
                consent.launch(IntentSenderRequest.Builder(intent.intentSender).build())
            } else {
                finishAuthorization(result)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            resetAuthorization()
            status = "Google Drive bağlantısı kurulamadı. Hesap izinlerini kontrol edin."
        }
    }

    val enabled = !busy && action == null && !selectingAccount
    val selectedAccount = settings.account
    SettingsCard("Bulut yedeği", "Antrenman, beslenme, ölçüm ve gelişim fotoğraflarını Google Drive'da sakla.") {
        Row(
            Modifier.fillMaxWidth().toggleable(
                value = selectedAccount != null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = { checked ->
                    status = ""
                    if (checked) {
                        try {
                            selectingAccount = true
                            accountPicker.launch(AccountManager.newChooseAccountIntent(
                                null, null, arrayOf(GOOGLE_ACCOUNT_TYPE), null, null, null, null,
                            ))
                        } catch (_: Exception) {
                            selectingAccount = false
                            status = "Google hesap seçicisi açılamadı."
                        }
                    } else {
                        viewModel.disableAutomaticBackup()
                    }
                },
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Otomatik Drive yedeklemesi", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(checked = selectedAccount != null, onCheckedChange = null, enabled = enabled)
        }

        Text("Yedekleme aralığı", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BackupInterval.entries.forEach { interval ->
                PillChip(interval.label, settings.interval == interval, enabled = enabled,
                    onClick = { viewModel.setBackupInterval(interval) })
            }
        }
        Text(
            "Android, internet bağlantısı uygun olduğunda yedekler. Bu kurulumun son $BACKUPS_PER_DEVICE sürümü saklanır.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (selectedAccount != null) {
            Text(selectedAccount, style = MaterialTheme.typography.labelMedium)
            settings.lastBackupAt?.let {
                Text("Son Drive yedeği · ${formatSessionDate(it)}", style = MaterialTheme.typography.bodySmall)
            }
            val message = when (settings.status) {
                BackupStatus.WAITING -> "Sıradaki yedekleme bekleniyor."
                BackupStatus.SAVED -> null
                BackupStatus.EMPTY -> "Yedeklenecek kayıt bulunamadı."
                BackupStatus.AUTH_REQUIRED -> "Otomatik yedekleme için Google Drive iznini yenile."
                BackupStatus.RETRYING -> "Yedekleme yeniden denenecek."
                BackupStatus.FAILED -> "Otomatik yedekleme tamamlanamadı."
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (settings.status == BackupStatus.AUTH_REQUIRED || settings.status == BackupStatus.FAILED) {
                OutlinedButton(onClick = { authorize(DriveAction.ENABLE, selectedAccount) }, enabled = enabled) {
                    Text(if (settings.status == BackupStatus.AUTH_REQUIRED) "İzni yenile" else "Yeniden dene")
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Button(onClick = { authorize(DriveAction.BACKUP, selectedAccount) }, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Drive'a yedekle") }
        OutlinedButton(onClick = { authorize(DriveAction.RESTORE, selectedAccount) }, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Drive'dan geri yükle") }
        if (!enabled) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(if (busy) "Drive işlemi sürüyor…" else "Google hesabı bekleniyor…")
        }
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
    }

    restoreOptions?.let {
        ChooseDriveBackup(it, onDismiss = viewModel::dismissRestoreOptions, onRestore = viewModel::restoreDriveBackup)
    }
}

@Composable
private fun ChooseDriveBackup(options: DriveRestoreOptions, onDismiss: () -> Unit, onRestore: (String) -> Unit) {
    var selected by rememberSaveable(options.files.first().id) { mutableStateOf(options.files.first().id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Drive yedeğini seç") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Seçilen yedekteki kayıtlar cihazındakilerle birleştirilir.")
                Column(
                    Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()).selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    options.files.forEach { file ->
                        val source = when {
                            file.legacy -> "Önceki Gimmer sürümü"
                            file.name == options.deviceFileName -> "Bu kurulum"
                            else -> "Başka bir kurulum"
                        }
                        Row(
                            Modifier.fillMaxWidth().selectable(selected == file.id, role = Role.RadioButton,
                                onClick = { selected = file.id }).padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected == file.id, onClick = null)
                            Column {
                                Text(formatSessionDate(file.modifiedAt.toEpochMilli()))
                                Text(source, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onRestore(selected) }) { Text("Geri yükle") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}
