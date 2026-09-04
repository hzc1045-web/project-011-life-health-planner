package com.project011.lifehealthplanner

import android.os.Bundle
import android.content.Intent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.project011.lifehealthplanner.pairing.PairingLinkParser
import com.project011.lifehealthplanner.pairing.PairingLinkResult
import com.project011.lifehealthplanner.pairing.PairingRequest
import com.project011.lifehealthplanner.ui.AppViewModel
import com.project011.lifehealthplanner.ui.LifeHealthApp
import com.project011.lifehealthplanner.ui.screens.OnboardingScreen
import com.project011.lifehealthplanner.ui.theme.LifeHealthTheme

class MainActivity : FragmentActivity() {
    private var pendingPairing by mutableStateOf<PairingRequest?>(null)
    private var pairingLinkError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handlePairingIntent(intent)
        setContent {
            LifeHealthTheme {
                val viewModel: AppViewModel = viewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()
                val content: @Composable () -> Unit = {
                    if (state.profile?.onboardingComplete != true) {
                        OnboardingScreen(
                            loading = state.loading,
                            message = state.message,
                            onSave = viewModel::saveOnboarding,
                        )
                    } else {
                        LifeHealthApp(viewModel, state)
                    }
                    PairingLinkDialogs(
                        request = pendingPairing,
                        error = pairingLinkError,
                        currentlyPaired = state.paired,
                        currentServer = state.companionServer,
                        onConfirm = { request ->
                            pendingPairing = null
                            viewModel.pair(request.serverUrl, request.code)
                        },
                        onDismissRequest = { pendingPairing = null },
                        onDismissError = { pairingLinkError = null },
                    )
                }
                if (
                    state.profile?.onboardingComplete == true &&
                    state.profile?.appLockEnabled == true
                ) {
                    BiometricGate(activity = this, content = content)
                } else {
                    content()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePairingIntent(intent)
    }

    private fun handlePairingIntent(intent: Intent?) {
        val rawLink = intent?.dataString ?: return
        intent.data = null
        when (val result = PairingLinkParser.parse(rawLink)) {
            is PairingLinkResult.Valid -> {
                pendingPairing = result.request
                pairingLinkError = null
            }
            is PairingLinkResult.Invalid -> {
                pendingPairing = null
                pairingLinkError = result.message
            }
        }
    }
}

@Composable
private fun PairingLinkDialogs(
    request: PairingRequest?,
    error: String?,
    currentlyPaired: Boolean,
    currentServer: String,
    onConfirm: (PairingRequest) -> Unit,
    onDismissRequest: () -> Unit,
    onDismissError: () -> Unit,
) {
    if (request != null) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text("确认电脑配对") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("待配对电脑地址")
                    Text(
                        request.serverUrl,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (currentlyPaired) {
                        Text(
                            "当前已配对 ${currentServer.ifBlank { "另一台电脑" }}。确认后将替换现有配对。",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text("请只确认你刚刚在可信电脑上生成的配对二维码。")
                }
            },
            confirmButton = {
                Button(onClick = { onConfirm(request) }) { Text("确认配对") }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) { Text("取消") }
            },
        )
    }
    if (error != null) {
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text("无法使用配对链接") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = onDismissError) { Text("知道了") }
            },
        )
    }
}

@Composable
private fun BiometricGate(activity: FragmentActivity, content: @Composable () -> Unit) {
    var unlocked by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun authenticate() {
        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val manager = BiometricManager.from(activity)
        if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            error = "设备未设置可用的指纹、面容或锁屏凭据，请先在系统设置中启用。"
            return
        }
        val prompt = BiometricPrompt(
            activity,
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlocked = true
                    error = null
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    error = errString.toString()
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("解锁人生健康规划助手")
                .setSubtitle("验证身份后查看个人健康与生活数据")
                .setAllowedAuthenticators(authenticators)
                .build(),
        )
    }

    LaunchedEffect(Unit) { authenticate() }
    if (unlocked) {
        content()
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("个人数据已锁定", style = MaterialTheme.typography.headlineSmall)
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Button(onClick = ::authenticate, modifier = Modifier.padding(top = 20.dp)) {
                Text("重新验证")
            }
        }
    }
}
