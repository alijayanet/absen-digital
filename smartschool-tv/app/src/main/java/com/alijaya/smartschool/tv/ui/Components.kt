package com.alijaya.smartschool.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Teal = Color(0xFF14B8A6)
val Bg = Color(0xFF0B1220)
val Panel = Color(0xFF111A2E)
val PanelHi = Color(0xFF1C2944)
val Muted = Color(0xFF94A3B8)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Teal, secondary = Color(0xFF38BDF8), background = Bg, surface = Panel,
            onPrimary = Color.Black, onBackground = Color.White, onSurface = Color.White
        ),
        content = content
    )
}

/** Kartu yang bisa difokus D-pad dengan border highlight. */
@Composable
fun FocusCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .background(if (focused) PanelHi else if (selected) Teal.copy(alpha = .18f) else Panel, shape)
            .border(2.dp, if (focused) Teal else Color.Transparent, shape)
            .then(
                if (onClick != null) Modifier.clickable(interactionSource = src, indication = null, onClick = onClick)
                else Modifier.focusable(interactionSource = src)
            )
            .padding(14.dp)
    ) { content() }
}

@Composable
fun StatTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    FocusCard(modifier) {
        Column {
            Text(label, color = Muted, fontSize = 13.sp)
            Text(value, color = color, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun Field(
    label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth(),
    password: Boolean = false, number: Boolean = false
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, modifier = modifier,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else if (password) KeyboardType.Password else KeyboardType.Text)
    )
}

@Composable
fun <T> Picker(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: " + (options.firstOrNull { it.first == selected }?.second ?: "-"))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (v, t) -> DropdownMenuItem(text = { Text(t) }, onClick = { onSelect(v); open = false }) }
        }
    }
}

@Composable
fun ScreenHeader(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

@Composable
fun DangerButton(text: String, onClick: () -> Unit) =
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White)) { Text(text) }

@Composable
fun Cell(text: String, w: Int, color: Color = Color.White) =
    Text(text, color = color, modifier = Modifier.width(w.dp), maxLines = 1)

fun rupiah(v: Long): String = "Rp " + "%,d".format(v).replace(',', '.')
