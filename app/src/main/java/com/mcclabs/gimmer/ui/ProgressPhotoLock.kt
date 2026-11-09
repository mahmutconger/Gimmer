package com.mcclabs.gimmer.ui

import android.app.KeyguardManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

@Composable
internal fun ProgressPhotoLock(onBack: () -> Unit, content: @Composable () -> Unit) {
    val activity = checkNotNull(LocalActivity.current)
    val keyguard = activity.getSystemService(KeyguardManager::class.java)
    var unlocked by remember { mutableStateOf(false) }
    var authenticating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cancellation by remember { mutableStateOf<CancellationSignal?>(null) }
    val credential = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        authenticating = false
        unlocked = result.resultCode == android.app.Activity.RESULT_OK
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        unlocked = false
        cancellation?.cancel()
    }
    DisposableEffect(activity) {
        val secure = WindowManager.LayoutParams.FLAG_SECURE
        val wasSecure = activity.window.attributes.flags and secure != 0
        activity.window.addFlags(secure)
        onDispose {
            cancellation?.cancel()
            if (!wasSecure) activity.window.clearFlags(secure)
        }
    }

    @Suppress("DEPRECATION")
    fun confirmCredential() {
        cancellation = null
        val intent = keyguard.createConfirmDeviceCredentialIntent("Görsel gelişim", "Fotoğraflarını açmak için doğrula.")
        if (intent != null) {
            authenticating = true
            credential.launch(intent)
        } else {
            authenticating = false
            error = "Önce cihaz ayarlarından bir ekran kilidi oluştur."
        }
    }

    if (unlocked) {
        content()
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        PhotoHeading("Görsel gelişim", "Kişisel fotoğraf günlüğün", onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Rounded.Fingerprint, null, Modifier.padding(20.dp).size(42.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text("Fotoğrafların kilitli", style = MaterialTheme.typography.headlineSmall)
            Text("Parmak izinle veya cihaz ekran kilidinle doğrulayarak günlüğünü aç.",
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            Button(enabled = !authenticating, onClick = {
                error = null
                if (!keyguard.isDeviceSecure) {
                    error = "Önce cihaz ayarlarından bir ekran kilidi oluştur."
                    return@Button
                }
                authenticating = true
                val signal = CancellationSignal()
                cancellation = signal
                val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
                val builder = BiometricPrompt.Builder(activity).setTitle("Görsel gelişim")
                    .setSubtitle("Fotoğraf günlüğünü açmak için doğrula.")
                if (Build.VERSION.SDK_INT >= 30) {
                    builder.setAllowedAuthenticators(
                        android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                    )
                } else {
                    builder.setNegativeButton("Ekran kilidini kullan", executor) { _, _ -> confirmCredential() }
                }
                builder.build().authenticate(signal, executor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        authenticating = false
                        cancellation = null
                        unlocked = !signal.isCanceled
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        authenticating = false
                        cancellation = null
                        if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED &&
                            errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED) {
                            error = if (Build.VERSION.SDK_INT < 30) {
                                "Biyometrik doğrulama tamamlanamadı. Ekran kilidini kullanabilirsin."
                            } else {
                                "Doğrulama tamamlanamadı. Yeniden dene."
                            }
                        }
                    }
                })
            }) { Text("Kilidi aç") }
            if (Build.VERSION.SDK_INT < 30) {
                TextButton(enabled = !authenticating, onClick = ::confirmCredential) { Text("Ekran kilidini kullan") }
            }
        }
    }
}
