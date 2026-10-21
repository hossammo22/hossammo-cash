package com.hossammo.cash.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hossammo.cash.BuildConfig
import com.hossammo.cash.R
import com.hossammo.cash.ui.components.FinanceCard
import com.hossammo.cash.ui.components.LabeledValue

@Composable
fun AboutScreen(onBack: () -> Unit) {
    SettingsPage(stringResource(R.string.about_title), onBack) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Savings, null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
        }
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.app_title_ar), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        FinanceCard(Modifier.fillMaxWidth()) {
            LabeledValue(stringResource(R.string.app_version), BuildConfig.VERSION_NAME)
            LabeledValue(stringResource(R.string.version_code), BuildConfig.VERSION_CODE.toString())
            LabeledValue(stringResource(R.string.package_name), BuildConfig.APPLICATION_ID)
        }
        FinanceCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.about_privacy), style = MaterialTheme.typography.bodyMedium)
        }
        FinanceCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.about_licenses), style = MaterialTheme.typography.bodySmall)
        }
    }
}
