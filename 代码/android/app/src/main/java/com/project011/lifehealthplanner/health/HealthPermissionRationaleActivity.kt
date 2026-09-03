package com.project011.lifehealthplanner.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.project011.lifehealthplanner.R
import com.project011.lifehealthplanner.ui.theme.LifeHealthTheme

class HealthPermissionRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LifeHealthTheme { Rationale() } }
    }
}

@Composable
private fun Rationale() {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("健康数据权限", style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.health_permission_rationale),
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
