package dev.turtur.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.turtur.ui.theme.Glacier
import dev.turtur.ui.theme.Line
import dev.turtur.ui.theme.Mist
import dev.turtur.ui.theme.Night
import dev.turtur.ui.theme.Rose
import dev.turtur.ui.theme.Slate
import dev.turtur.ui.theme.SlateHigh
import dev.turtur.ui.theme.Snow

private val PanelShape = RoundedCornerShape(12.dp)
private val ButtonShape = RoundedCornerShape(10.dp)

@Composable
internal fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
internal fun ScreenHeader(
    title: String,
    help: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Snow)
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .offset(x = if (onBack != null) (-8).dp else 0.dp),
            )
            actions()
        }
        help?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Mist) }
    }
}

@Composable
internal fun PrimaryButton(
    text: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    color: Color = Glacier,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Night,
            disabledContainerColor = SlateHigh,
            disabledContentColor = Mist,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) { ButtonLabel(text, icon) }
}

@Composable
internal fun SecondaryButton(
    text: String,
    icon: ImageVector? = null,
    color: Color = Glacier,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = ButtonShape,
        border = BorderStroke(1.dp, if (color == Glacier) Line else color.copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) { ButtonLabel(text, icon) }
}

@Composable
internal fun DangerButton(text: String, icon: ImageVector? = null, onClick: () -> Unit) =
    SecondaryButton(text, icon, color = Rose, onClick = onClick)

@Composable
internal fun LinkButton(text: String, color: Color = Glacier, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
        Text(text, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ButtonLabel(text: String, icon: ImageVector?) {
    if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
    }
    Text(text, style = MaterialTheme.typography.labelLarge)
}

/** Two tiles side by side with equal height. */
@Composable
internal fun TileRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
internal fun ActionTile(
    icon: ImageVector,
    label: String,
    hint: String,
    modifier: Modifier = Modifier,
    badge: Int? = null,
    accent: Color = Glacier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(PanelShape)
            .background(Slate)
            .border(1.dp, if (accent == Glacier) Line else accent.copy(alpha = 0.45f), PanelShape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(icon, accent)
            Spacer(Modifier.weight(1f))
            if (badge != null && badge > 0) CountBadge(badge, accent)
        }
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = Mist)
    }
}

@Composable
internal fun IconBubble(icon: ImageVector, accent: Color = Glacier, size: Dp = 32.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(accent.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(size * 0.56f))
    }
}

@Composable
private fun CountBadge(count: Int, color: Color) {
    Text(
        count.toString(),
        color = Night,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .background(color, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
internal fun Section(title: String, hint: String? = null) {
    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = Mist)
        hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Mist) }
    }
}

@Composable
internal fun Panel(
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(Slate)
            .border(1.dp, accent?.copy(alpha = 0.45f) ?: Line, PanelShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
internal fun InfoCard(icon: ImageVector, text: String, tint: Color = Glacier) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.09f), PanelShape)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Snow.copy(alpha = 0.85f))
    }
}

@Composable
internal fun InfoLine(icon: ImageVector, text: String, color: Color = Snow) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (color == Snow) Mist else color, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
internal fun EmptyState(icon: ImageVector, title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconBubble(icon, Mist, size = 48.dp)
        Text(title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Mist, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun StatusPill(text: String, color: Color) {
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(text, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

/** A tappable row: radio when [selected] is set, chevron otherwise. */
@Composable
internal fun ChoiceRow(
    title: String,
    hint: String,
    selected: Boolean? = null,
    icon: ImageVector? = null,
    accent: Color = Glacier,
    onClick: () -> Unit,
) {
    val border = if (selected == true) accent else Line
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(Slate)
            .border(1.dp, border, PanelShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            selected != null -> RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = accent, unselectedColor = Mist),
                modifier = Modifier.size(24.dp),
            )
            icon != null -> IconBubble(icon, accent)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = Mist)
        }
        if (selected == null) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Mist)
        }
    }
}

/** A list row for mail and sightings: icon, title, body, meta line, optional trailing action. */
@Composable
internal fun ListRow(
    icon: ImageVector,
    title: String,
    body: String? = null,
    meta: String? = null,
    accent: Color = Glacier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(Slate)
            .border(1.dp, Line, PanelShape)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(icon, accent)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = accent)
            body?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            meta?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Mist) }
        }
        trailing?.invoke()
    }
}

@Composable
internal fun QrPanel(bitmap: Bitmap, contentDescription: String, size: Dp = 200.dp) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = Modifier
                .clip(PanelShape)
                .background(Snow)
                .padding(10.dp)
                .size(size),
        )
    }
}

@Composable
internal fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    supporting: String? = null,
    maxLength: Int? = null,
    singleLine: Boolean = true,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
) {
    val showSupport = supporting != null || maxLength != null
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = keyboard,
        modifier = Modifier.fillMaxWidth(),
        shape = ButtonShape,
        supportingText = if (showSupport) {
            {
                Row {
                    Text(supporting.orEmpty(), modifier = Modifier.weight(1f))
                    maxLength?.let { Text("${value.length}/$it") }
                }
            }
        } else {
            null
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Glacier,
            unfocusedBorderColor = Line,
            focusedLabelColor = Glacier,
            unfocusedLabelColor = Mist,
            focusedTextColor = Snow,
            unfocusedTextColor = Snow,
            focusedContainerColor = Slate,
            unfocusedContainerColor = Slate,
            focusedSupportingTextColor = Mist,
            unfocusedSupportingTextColor = Mist,
            cursorColor = Glacier,
        ),
    )
}

@Composable
internal fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Glacier,
            selectedLabelColor = Night,
            containerColor = Slate,
            labelColor = Snow,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Line,
            selectedBorderColor = Glacier,
        ),
    )
}
