package com.example.obsidiankeep

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.obsidiankeep.security.PinManager
import com.example.obsidiankeep.settings.LanguageHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: NoteViewModel,
    onBack: () -> Unit,
    onExportZip: () -> Unit,
    onImportZip: () -> Unit,
    onImportMarkdown: () -> Unit,
    onImportVault: () -> Unit,
    onToggleMirror: () -> Unit
) {
    val context = LocalContext.current
    val gridColumns by viewModel.gridColumns.collectAsState(initial = 2)
    val language by viewModel.language.collectAsState(initial = LanguageHelper.DEFAULT_LANG)
    var mirrorEnabled by remember { mutableStateOf(viewModel.isMirrorEnabled()) }

    // PIN-состояние
    val pinManager = remember { PinManager.get(context) }
    var pinEnabled by remember { mutableStateOf(pinManager.isPinEnabled()) }
    var hasDecoy by remember { mutableStateOf(pinManager.hasDecoy()) }
    var autoLockSeconds by remember { mutableStateOf(pinManager.getAutoLockSeconds()) }
    var showPinSetup by remember { mutableStateOf(false) }
    var showDecoySetup by remember { mutableStateOf(false) }

    fun applyLanguage(lang: String) {
        LanguageHelper.setLanguage(context, lang)
        viewModel.setLanguage(lang)
        // Полный перезапуск Activity для применения новой локали ко всем ресурсам
        // (включая виджеты, уведомления, и т.д.) без визуального "дёргания" recreate().
        val activity = context as? android.app.Activity
        activity?.let {
            val intent = it.packageManager.getLaunchIntentForPackage(it.packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            it.finish()
            it.startActivity(intent)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("←", color = Color.White, fontSize = 28.sp, modifier = Modifier.offset(y = (-7).dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSection(stringResource(R.string.settings_appearance)) {
                Text(stringResource(R.string.settings_grid_columns, gridColumns), color = Color.White, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    (1..4).forEach { cols ->
                        FilterChip(
                            selected = gridColumns == cols,
                            onClick = { viewModel.setGridColumns(cols) },
                            label = { Text("$cols", color = Color.White) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFBB86FC))
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.settings_language), color = Color.White, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = language == "ru",
                        onClick = { applyLanguage("ru") },
                        label = { Text(stringResource(R.string.settings_lang_ru), color = Color.White) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFBB86FC))
                    )
                    FilterChip(
                        selected = language == "en",
                        onClick = { applyLanguage("en") },
                        label = { Text(stringResource(R.string.settings_lang_en), color = Color.White) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFBB86FC))
                    )
                }
                Text(stringResource(R.string.settings_lang_restart), color = Color.Gray, fontSize = 11.sp)
            }

            // === PIN / Безопасность ===
            SettingsSection("🔐 " + stringResource(R.string.pin_settings_enable)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.pin_settings_enable), color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = pinEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                showPinSetup = true
                            } else {
                                pinManager.clear()
                                pinEnabled = false
                                hasDecoy = false
                                Toast.makeText(context, R.string.pin_cleared, Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFBB86FC))
                    )
                }
                if (pinEnabled) {
                    Divider(color = Color(0xFF333333))
                    SettingsRow(
                        title = stringResource(R.string.pin_settings_change),
                        subtitle = "",
                        onClick = { showPinSetup = true }
                    )
                    Divider(color = Color(0xFF333333))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.pin_settings_decoy), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(stringResource(R.string.pin_settings_decoy_hint), color = Color.Gray, fontSize = 11.sp)
                        }
                        Switch(
                            checked = hasDecoy,
                            onCheckedChange = { enabled ->
                                if (enabled) showDecoySetup = true
                                else { pinManager.setDecoyPin(null); hasDecoy = false }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFBB86FC))
                        )
                    }
                    Divider(color = Color(0xFF333333))
                    Text(stringResource(R.string.pin_settings_autolock), color = Color.White, fontSize = 14.sp)
                    // Off и 15s на первой строке, остальные на второй — чтобы не было вертикального сжатия
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0, 15, 30, 60).forEach { sec ->
                            FilterChip(
                                selected = autoLockSeconds == sec,
                                onClick = {
                                    autoLockSeconds = sec
                                    pinManager.setAutoLockSeconds(sec)
                                },
                                label = { Text(if (sec == 0) "Off" else "${sec}s", color = Color.White, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFBB86FC))
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(120, 300).forEach { sec ->
                            FilterChip(
                                selected = autoLockSeconds == sec,
                                onClick = {
                                    autoLockSeconds = sec
                                    pinManager.setAutoLockSeconds(sec)
                                },
                                label = { Text("${sec}s", color = Color.White, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFBB86FC))
                            )
                        }
                    }
                }
            }

            SettingsSection(stringResource(R.string.settings_sync)) {
                SettingsRow(
                    title = stringResource(R.string.settings_mirror),
                    subtitle = if (mirrorEnabled) stringResource(R.string.settings_mirror_on) else stringResource(R.string.settings_mirror_off),
                    onClick = {
                        onToggleMirror()
                        mirrorEnabled = viewModel.isMirrorEnabled()
                    }
                )
                Divider(color = Color(0xFF333333))
                SettingsRow(
                    title = stringResource(R.string.settings_webdav),
                    subtitle = stringResource(R.string.settings_webdav_desc),
                    onClick = { Toast.makeText(context, R.string.settings_webdav_click, Toast.LENGTH_LONG).show() }
                )
            }

            SettingsSection(stringResource(R.string.settings_backup)) {
                SettingsRow(title = stringResource(R.string.menu_export_zip), subtitle = stringResource(R.string.settings_export_zip), onClick = onExportZip)
                Divider(color = Color(0xFF333333))
                SettingsRow(title = stringResource(R.string.menu_import_zip), subtitle = stringResource(R.string.settings_import_zip), onClick = onImportZip)
                Divider(color = Color(0xFF333333))
                SettingsRow(title = stringResource(R.string.menu_import_md), subtitle = stringResource(R.string.settings_import_md), onClick = onImportMarkdown)
                Divider(color = Color(0xFF333333))
                SettingsRow(title = stringResource(R.string.menu_import_vault), subtitle = stringResource(R.string.settings_import_vault), onClick = onImportVault)
            }

            SettingsSection(stringResource(R.string.settings_about)) {
                Text("ObsidianKeep v5.0", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(R.string.settings_about_desc), color = Color.Gray, fontSize = 12.sp)
            }
        }
    }

    if (showPinSetup) {
        PinSetupDialog(
            onPinSet = { pin ->
                pinManager.setPin(pin)
                pinEnabled = true
                showPinSetup = false
                Toast.makeText(context, R.string.pin_set, Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showPinSetup = false }
        )
    }
    if (showDecoySetup) {
        PinSetupDialog(
            onPinSet = { pin ->
                pinManager.setDecoyPin(pin)
                hasDecoy = true
                showDecoySetup = false
            },
            onDismiss = { showDecoySetup = false }
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(title, color = Color(0xFFBB86FC), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}
