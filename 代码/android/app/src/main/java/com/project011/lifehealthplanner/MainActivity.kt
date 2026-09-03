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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.project011.lifehealthplanner.ui.AppViewModel
import com.project011.lifehealthplanner.ui.LifeHealthApp
import com.project011.lifehealthplanner.ui.screens.OnboardingScreen
import com.project011.lifehealthplanner.ui.theme.LifeHealthTheme

class MainActivity : FragmentActivity() {
    private var pairingUri by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pairingUri = intent?.dataString
        setContent {
            LifeHealthTheme {
                val viewModel: AppViewModel = viewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(pairingUri) {
                    pairingUri?.let { uriText ->
                        val uri = android.net.Uri.parse(uriText)
                        val server = uri.getQueryParameter("server")
                        val code = uri.getQueryParameter("code")
                        if (uri.scheme == "lifehealth" && uri.host == "pair" && server != null && code != null) {
                            viewModel.pair(server, code)
                            pairingUri = null
                        }
                    }
                }
                when {
                    state.profile?.onboardingComplete != true -> OnboardingScreen(
                        loading = state.loading,
                        message = state.message,
                        onSave = viewModel::saveOnboarding,
                    )
                    state.profile?.appLockEnabled == true -> BiometricGate(
                        activity = this,
                        content = { LifeHealthApp(viewModel, state) },
                    )
                    else -> LifeHealthApp(viewModel, state)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pairingUri = intent.dataString
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
