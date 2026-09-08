package io.github.immaghzbad.aetherst.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import io.github.immaghzbad.aetherst.shared.i18n.LocalAppStrings
import io.github.immaghzbad.aetherst.shared.i18n.StringsFa
import io.github.immaghzbad.aetherst.shared.ui.theme.AppPalette
import io.github.immaghzbad.aetherst.shared.ui.theme.appColors

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = appColors().surfaceRaised,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

private val IosCardBg = AppPalette.surfaceRaised
private val IosGroupBg = AppPalette.divider
private val IosSecondaryLabel = AppPalette.textSecondary
private val IosActiveBlue = AppPalette.accent
private val IosDividerColor = AppPalette.divider
private val IosActiveGreen = AppPalette.statusConnected
private val IosInactiveTrack = AppPalette.inactiveTrack

@Composable
fun AppDivider() = HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 50.dp))

@Composable
fun IosConfirmationDialog(title: String, message: String, confirmText: String, confirmColor: Color = Color.White, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val strings = LocalAppStrings.current
    val isRtl = strings is StringsFa
    CompositionLocalProvider(LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        AlertDialog(onDismissRequest = onDismiss, confirmButton = {
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss, modifier = Modifier.height(44.dp)) { Text(strings.CANCEL, color = IosSecondaryLabel, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                Button(onClick = onConfirm, modifier = Modifier.height(44.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = confirmColor, contentColor = Color.White)) { Text(confirmText, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
        }, dismissButton = {}, title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = if (isRtl) androidx.compose.ui.text.style.TextAlign.Right else androidx.compose.ui.text.style.TextAlign.Left, modifier = Modifier.fillMaxWidth()) }, text = { Text(message, color = IosSecondaryLabel, fontSize = 14.sp, lineHeight = 20.sp, textAlign = if (isRtl) androidx.compose.ui.text.style.TextAlign.Right else androidx.compose.ui.text.style.TextAlign.Left, modifier = Modifier.fillMaxWidth()) }, containerColor = AppPalette.surfaceRaised, shape = RoundedCornerShape(20.dp), modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp)))
    }
}

@Composable
fun IosPresetItem(icon: ImageVector, iconBg: Color, title: String, subtitle: String, isActive: Boolean, onClick: () -> Unit, scaleFactor: Float = 1f) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { IosIconBadge(icon = icon, backgroundColor = iconBg, scaleFactor = scaleFactor); Spacer(modifier = Modifier.width(12.dp)); Column { Text(title, fontWeight = FontWeight.Medium, color = Color.White, fontSize = 15.sp); Text(subtitle, color = IosSecondaryLabel, fontSize = 11.sp) } }; if (isActive) Text("Active", fontWeight = FontWeight.Bold, color = IosActiveGreen, fontSize = 11.sp) else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = IosSecondaryLabel, modifier = Modifier.size(18.dp)) }
}

@Composable
fun IosGroupCard(content: @Composable () -> Unit) { Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = IosCardBg), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) { content() } }

@Composable
fun IosIconBadge(icon: ImageVector, backgroundColor: Color, scaleFactor: Float = 1f) { Box(modifier = Modifier.size((30 * scaleFactor).dp).clip(RoundedCornerShape((8 * scaleFactor).dp)).background(backgroundColor), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White, modifier = Modifier.size((18 * scaleFactor).dp)) } }

@Composable
fun IosSwitchRow(icon: ImageVector, iconBg: Color, title: String, subtitle: String? = null, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit, testTag: String, scaleFactor: Float = 1f) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { IosIconBadge(icon = icon, backgroundColor = iconBg, scaleFactor = scaleFactor); Spacer(modifier = Modifier.width(12.dp)); Column { Text(title, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = if (enabled) 1f else 0.5f), fontSize = 15.sp); if (!subtitle.isNullOrEmpty()) Text(subtitle, color = IosSecondaryLabel.copy(alpha = if (enabled) 1f else 0.5f), fontSize = 11.sp) } }; Spacer(modifier = Modifier.width(8.dp)); Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled, modifier = Modifier.testTag(testTag).graphicsLayer { scaleX = scaleFactor * 0.9f; scaleY = scaleFactor * 0.9f }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = IosActiveGreen, checkedBorderColor = Color.Transparent, uncheckedThumbColor = Color.White, uncheckedTrackColor = IosInactiveTrack, uncheckedBorderColor = Color.Transparent, disabledCheckedTrackColor = IosActiveGreen.copy(alpha = 0.5f), disabledCheckedThumbColor = Color.White.copy(alpha = 0.8f))) }
}

@Composable
fun IosPickerRow(icon: ImageVector, iconBg: Color, title: String, value: String, options: List<String>, onOptionSelected: (Int) -> Unit, scaleFactor: Float = 1f, onClickOverride: (() -> Unit)? = null, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    Box { Row(modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { if (onClickOverride != null) onClickOverride() else expanded = true }.padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { IosIconBadge(icon = icon, backgroundColor = iconBg, scaleFactor = scaleFactor); Spacer(modifier = Modifier.width(12.dp)); Text(title, fontWeight = FontWeight.Medium, color = if (enabled) Color.White else Color.White.copy(alpha = 0.35f), fontSize = 15.sp) }; Row(verticalAlignment = Alignment.CenterVertically) { Text(value, color = if (enabled) IosSecondaryLabel else IosSecondaryLabel.copy(alpha = 0.35f), maxLines = 1, fontSize = 13.sp); Spacer(modifier = Modifier.width(4.dp)); Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = if (enabled) IosSecondaryLabel else IosSecondaryLabel.copy(alpha = 0.35f), modifier = Modifier.size(18.dp)) } }; DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(IosGroupBg)) { options.forEachIndexed { index, option -> DropdownMenuItem(text = { Text(option, color = Color.White, fontSize = 14.sp) }, onClick = { onOptionSelected(index); expanded = false }) } } }
}

@Composable
fun IosActionRow(icon: ImageVector? = null, iconBg: Color = Color.Transparent, title: String, subtitle: String? = null, onClick: () -> Unit, scaleFactor: Float = 1f, titleColor: Color = Color.White) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { if (icon != null) { IosIconBadge(icon = icon, backgroundColor = iconBg, scaleFactor = scaleFactor); Spacer(modifier = Modifier.width(12.dp)) }; Column { Text(title, fontWeight = FontWeight.Medium, color = titleColor, fontSize = 15.sp); if (subtitle != null) Text(subtitle, color = IosSecondaryLabel, fontSize = 11.sp) } }; Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = IosSecondaryLabel, modifier = Modifier.size(18.dp)) }
}

@Composable
fun IosInputField(label: String, value: String, onValueChange: (String) -> Unit, testTag: String, modifier: Modifier = Modifier, placeholder: String = "", keyboardType: KeyboardType = KeyboardType.Text) {
    val focusManager = LocalFocusManager.current; var isFocused by remember { mutableStateOf(false) }
    Column(modifier = modifier) { Text(label, color = IosSecondaryLabel, fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp)); BasicTextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth().height(46.dp).background(IosGroupBg, RoundedCornerShape(10.dp)).border(1.dp, if (isFocused) IosActiveBlue else Color.Transparent, RoundedCornerShape(10.dp)).onFocusChanged { isFocused = it.isFocused }.testTag(testTag), textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontSize = 14.sp), keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }), singleLine = true, cursorBrush = SolidColor(IosActiveBlue), decorationBox = { innerTextField -> Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) { if (value.isEmpty()) Text(placeholder, color = IosSecondaryLabel, fontSize = 13.sp); innerTextField() } }) }
}

@Composable
fun IosInputFieldRow(icon: ImageVector, iconBg: Color, label: String, value: String, onValueChange: (String) -> Unit, placeholder: String = "", keyboardType: KeyboardType = KeyboardType.Text, testTag: String, scaleFactor: Float = 1f) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { IosIconBadge(icon = icon, backgroundColor = iconBg, scaleFactor = scaleFactor); Spacer(modifier = Modifier.width(12.dp)); IosInputField(label = label, value = value, onValueChange = onValueChange, modifier = Modifier.weight(1f), placeholder = placeholder, keyboardType = keyboardType, testTag = testTag) }
}
