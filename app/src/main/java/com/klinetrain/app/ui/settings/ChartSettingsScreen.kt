package com.klinetrain.app.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klinetrain.app.data.model.AxisType
import com.klinetrain.app.data.model.CandleStyle
import com.klinetrain.app.data.model.ChartStyle
import kotlin.math.roundToInt

/**
 * K线设置控件组: K线样式 / 坐标类型 / 初始数量 / 各显示开关。
 * 嵌入训练页设置弹层使用, 所有改动通过 onChange 即时回调(由调用方持久化)。
 */
@Composable
fun ChartStyleSection(
    style: ChartStyle,
    onChange: (ChartStyle) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // ---------------- K线样式 4宫格 ----------------
        Text("K线样式", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CandleStyle.entries.forEach { cs ->
                CandleStyleItem(
                    style = cs,
                    selected = style.candleStyle == cs,
                    modifier = Modifier.weight(1f),
                    onClick = { onChange(style.copy(candleStyle = cs)) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // ---------------- 坐标类型 ----------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("坐标类型", fontSize = 15.sp, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AxisType.entries.forEach { at ->
                    FilterChip(
                        selected = style.axisType == at,
                        onClick = { onChange(style.copy(axisType = at)) },
                        label = { Text(at.label, fontSize = 13.sp) }
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // ---------------- K线初始数量 ----------------
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("K线初始数量", fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text(
                    "${style.initialBars}根",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = style.initialBars.coerceIn(60, 200).toFloat(),
                onValueChange = { v ->
                    onChange(style.copy(initialBars = v.roundToInt().coerceIn(60, 200)))
                },
                valueRange = 60f..200f,
                steps = 13
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // ---------------- 开关列表 ----------------
        SwitchRow("K线右边距", "最右K线与右轴间留白", style.rightPadding) {
            onChange(style.copy(rightPadding = it))
        }
        SwitchRow("品牌水印", "主图中央显示半透明水印", style.watermark) {
            onChange(style.copy(watermark = it))
        }
        SwitchRow("成本线", "持仓时显示金色成本虚线", style.costLineEnabled) {
            onChange(style.copy(costLineEnabled = it))
        }
        SwitchRow("最新价线", "最新收盘价水平虚线", style.lastPriceLine) {
            onChange(style.copy(lastPriceLine = it))
        }
        SwitchRow("涨停黄色跌停蓝色", "涨跌停K线特殊染色", style.limitColors) {
            onChange(style.copy(limitColors = it))
        }
        SwitchRow("绿涨红跌", "反转全图涨跌配色", style.greenUp) {
            onChange(style.copy(greenUp = it))
        }
    }
}

// ---------------- K线样式选项(小图标 + 文字) ----------------
@Composable
private fun CandleStyleItem(
    style: CandleStyle,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val iconColor = if (selected) primary else Color(0xFF8A8A93)
    val bg = if (selected) primary.copy(alpha = 0.12f)
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(Modifier.size(30.dp)) {
            val w = size.width
            val h = size.height
            val stroke = 2f
            when (style) {
                // 空心阳: 左空心蜡烛 + 右实心蜡烛
                CandleStyle.HOLLOW -> {
                    drawLine(iconColor, Offset(w * 0.3f, h * 0.08f), Offset(w * 0.3f, h * 0.92f), stroke)
                    drawRect(
                        iconColor, Offset(w * 0.16f, h * 0.3f), Size(w * 0.28f, h * 0.38f),
                        style = Stroke(width = stroke)
                    )
                    drawLine(iconColor, Offset(w * 0.72f, h * 0.15f), Offset(w * 0.72f, h * 0.85f), stroke)
                    drawRect(iconColor, Offset(w * 0.58f, h * 0.35f), Size(w * 0.28f, h * 0.32f))
                }
                // 实心阳: 两根实心蜡烛
                CandleStyle.SOLID -> {
                    drawLine(iconColor, Offset(w * 0.3f, h * 0.08f), Offset(w * 0.3f, h * 0.92f), stroke)
                    drawRect(iconColor, Offset(w * 0.16f, h * 0.3f), Size(w * 0.28f, h * 0.38f))
                    drawLine(iconColor, Offset(w * 0.72f, h * 0.15f), Offset(w * 0.72f, h * 0.85f), stroke)
                    drawRect(iconColor, Offset(w * 0.58f, h * 0.35f), Size(w * 0.28f, h * 0.32f))
                }
                // 折线图
                CandleStyle.LINE -> {
                    val p = Path()
                    p.moveTo(w * 0.05f, h * 0.78f)
                    p.lineTo(w * 0.35f, h * 0.45f)
                    p.lineTo(w * 0.58f, h * 0.62f)
                    p.lineTo(w * 0.95f, h * 0.18f)
                    drawPath(p, iconColor, style = Stroke(width = stroke))
                }
                // 竹节图(OHLC bar)
                CandleStyle.BAR -> {
                    drawLine(iconColor, Offset(w * 0.32f, h * 0.12f), Offset(w * 0.32f, h * 0.88f), stroke)
                    drawLine(iconColor, Offset(w * 0.1f, h * 0.62f), Offset(w * 0.32f, h * 0.62f), stroke)
                    drawLine(iconColor, Offset(w * 0.32f, h * 0.3f), Offset(w * 0.54f, h * 0.3f), stroke)
                    drawLine(iconColor, Offset(w * 0.74f, h * 0.05f), Offset(w * 0.74f, h * 0.8f), stroke)
                    drawLine(iconColor, Offset(w * 0.52f, h * 0.52f), Offset(w * 0.74f, h * 0.52f), stroke)
                    drawLine(iconColor, Offset(w * 0.74f, h * 0.2f), Offset(w * 0.96f, h * 0.2f), stroke)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            style.label,
            fontSize = 12.sp,
            color = if (selected) primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
        )
    }
}

// ---------------- 开关行 ----------------
@Composable
private fun SwitchRow(
    title: String,
    desc: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            if (!desc.isNullOrEmpty()) {
                Text(
                    desc,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
