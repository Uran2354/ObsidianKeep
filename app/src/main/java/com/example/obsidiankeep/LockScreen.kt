package com.example.obsidiankeep

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.obsidiankeep.security.PinCheckResult
import com.example.obsidiankeep.security.PinManager

/**
 * Экран блокировки. Показывается поверх MainActivity, пока PIN не введён.
 * Поддерживает биометрию (кнопка внизу) — вызывает onSuccess.
 */
@Composable
fun LockScreen(
    pinManager: PinManager,
    onUnlocked: (PinCheckResult) -> Unit,
    onUseBiometric: () -> Unit,
    biometricAvailable: Boolean
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    // Все строковые ресурсы берём ВНУТРИ @Composable функции,
    // не внутри onClick-ламбд (иначе будет "Composable invocations can only happen from context of @Composable")
    val titleText = stringResource(R.string.pin_enter_title)
    val saveText = stringResource(R.string.btn_save)
    val useBiometricText = stringResource(R.string.pin_use_biometric)
    val wrongPinText = stringResource(R.string.pin_wrong)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text("🔒", fontSize = 48.sp)
            Text(titleText, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 16 && it.all { c -> c.isDigit() }) { pin = it; error = null } },
                modifier = Modifier.width(220.dp),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                visualTransformation = PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFBB86FC),
                    unfocusedBorderColor = Color(0xFF444444)
                )
            )

            if (error != null) {
                Text(error!!, color = Color(0xFFE53935), fontSize = 13.sp)
            }

            Button(
                onClick = {
                    val result = pinManager.checkPin(pin)
                    when (result) {
                        PinCheckResult.MAIN -> {
                            pinManager.setDecoySession(false)
                            pinManager.setLastUnlockTime(System.currentTimeMillis())
                            onUnlocked(result)
                        }
                        PinCheckResult.DECOY -> {
                            pinManager.setDecoySession(true)
                            pinManager.setLastUnlockTime(System.currentTimeMillis())
                            onUnlocked(result)
                        }
                        PinCheckResult.WRONG -> {
                            error = "❌ $wrongPinText"
                            pin = ""
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBB86FC)),
                modifier = Modifier.width(220.dp)
            ) { Text(saveText, color = Color.Black, fontWeight = FontWeight.Bold) }

            if (biometricAvailable) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    useBiometricText,
                    color = Color(0xFFBB86FC),
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { onUseBiometric() }
                )
            }
        }
    }
}

/**
 * Диалог ввода PIN для доступа к защищённой папке.
 *
 * Логика:
 *  - Если введён основной PIN → вызывается onMainPin (папка разблокируется)
 *  - Если введён decoy PIN → вызывается onDecoyPin (папка открывается, но заметки
 *    остаются зашифрованными — пользователь видит пустую папку)
 *  - Если неверный PIN → показывается ошибка, остаёмся в диалоге
 */
@Composable
fun FolderPinDialog(
    pinManager: PinManager,
    folderName: String,
    onMainPin: () -> Unit,
    onDecoyPin: () -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val titleText = stringResource(R.string.pin_enter_title)
    val wrongPinText = stringResource(R.string.pin_wrong)
    val unlockText = "🔓"
    val cancelText = stringResource(R.string.btn_cancel)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$unlockText $titleText · $folderName", color = Color.White) },
        containerColor = Color(0xFF1E1E1E),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 16 && it.all { c -> c.isDigit() }) { pin = it; error = null } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    textStyle = LocalTextStyle.current.copy(color = Color.White, textAlign = TextAlign.Center),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFBB86FC),
                        unfocusedBorderColor = Color(0xFF444444)
                    )
                )
                if (error != null) {
                    Text(error!!, color = Color(0xFFE53935), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val result = pinManager.checkPin(pin)
                when (result) {
                    PinCheckResult.MAIN -> { pinManager.setDecoySession(false); onMainPin() }
                    PinCheckResult.DECOY -> { pinManager.setDecoySession(true); onDecoyPin() }
                    PinCheckResult.WRONG -> {
                        error = "❌ $wrongPinText"
                        pin = ""
                    }
                }
            }) { Text(stringResource(R.string.btn_save), color = Color(0xFFBB86FC)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelText, color = Color.White)
            }
        }
    )
}

/**
 * Диалог установки PIN.
 * Сначала просит ввести PIN, потом повторить. При совпадении — вызывает onSuccess.
 */
@Composable
fun PinSetupDialog(
    onPinSet: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var stage by remember { mutableStateOf(0) } // 0 = ввод, 1 = подтверждение
    var firstPin by remember { mutableStateOf("") }
    var secondPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    // Все строковые ресурсы берём ВНУТРИ @Composable
    val setupTitle = stringResource(R.string.pin_setup_title)
    val confirmTitle = stringResource(R.string.pin_confirm_title)
    val saveText = stringResource(R.string.btn_save)
    val cancelText = stringResource(R.string.btn_cancel)
    val emptyPinText = stringResource(R.string.pin_empty)
    val mismatchText = stringResource(R.string.pin_mismatch)

    val title = if (stage == 0) setupTitle else confirmTitle

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.White) },
        containerColor = Color(0xFF1E1E1E),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val currentPin = if (stage == 0) firstPin else secondPin
                OutlinedTextField(
                    value = currentPin,
                    onValueChange = {
                        if (it.length <= 16 && it.all { c -> c.isDigit() }) {
                            if (stage == 0) firstPin = it else secondPin = it
                            error = null
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    textStyle = LocalTextStyle.current.copy(color = Color.White, textAlign = TextAlign.Center),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFBB86FC),
                        unfocusedBorderColor = Color(0xFF444444)
                    )
                )
                if (error != null) {
                    Text(error!!, color = Color(0xFFE53935), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val currentPin = if (stage == 0) firstPin else secondPin
                if (currentPin.length < 4) {
                    error = emptyPinText
                    return@TextButton
                }
                if (stage == 0) {
                    stage = 1
                    secondPin = ""
                } else {
                    if (firstPin == secondPin) {
                        onPinSet(firstPin)
                    } else {
                        error = mismatchText
                        secondPin = ""
                    }
                }
            }) { Text(saveText, color = Color(0xFFBB86FC)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelText, color = Color.White)
            }
        }
    )
}
