package com.klinetrain.app.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.klinetrain.app.data.model.Kline
import com.klinetrain.app.indicator.Indicators
import com.klinetrain.app.ui.theme.GoldYellow
import com.klinetrain.app.ui.theme.UpRed
import kotlin.math.max

private val TrapBlue = Color(0xFF2E7CF6)   // 套牢盘(当前价上方)
private val PanelGray = Color(0xFF9E9EA7)

/**
 * 筹码分布面板：价格为y轴、筹码量为x轴的横向柱状图。
 * 当前价上方为套牢盘(蓝色)，下方为获利盘(红色)。
 * 顶部显示获利比例与平均成本。
 */
@Composable
fun ChipPanel(klines: List<Kline>, modifier: Modifier = Modifier) {
    val chip = remember(klines.size, klines.lastOrNull()?.time) {
        if (klines.isEmpty()) null
        else Indicators.chipDistribution(klines, klines.size - 1)
    }
    val bg = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
    val fg = MaterialTheme.colorScheme.onSurface
    val tm = rememberTextMeasurer()

    Canvas(modifier = modifier) {
        drawRect(bg)
        val w = size.width
        val h = size.height
        if (chip == null || chip.prices.isEmpty() || chip.weights.isEmpty() || klines.isEmpty()) {
            val layout = tm.measure(AnnotatedString("暂无筹码数据"), TextStyle(color = PanelGray, fontSize = 9.sp))
            drawText(layout, topLeft = Offset((w - layout.size.width) / 2f, (h - layout.size.height) / 2f))
            return@Canvas
        }

        // 顶部信息：获利比例 / 平均成本
        val style = TextStyle(fontSize = 9.sp)
        val l1 = tm.measure(
            AnnotatedString("获利 " + String.format("%.2f", chip.profitRatio * 100) + "%"),
            style.copy(color = UpRed)
        )
        val l2 = tm.measure(
            AnnotatedString("成本 " + String.format("%.2f", chip.avgCost)),
            style.copy(color = GoldYellow)
        )
        drawText(l1, topLeft = Offset(4f, 2f))
        drawText(l2, topLeft = Offset(4f, 2f + l1.size.height + 2f))
        val top = 2f + l1.size.height + l2.size.height + 8f
        val chartH = h - top - 4f
        if (chartH <= 10f) return@Canvas

        val lo = chip.prices.first()
        val hi = chip.prices.last()
        if (hi <= lo) return@Canvas
        val maxW = chip.weights.max()
        if (maxW <= 0.0) return@Canvas
        val cur = klines.last().close

        fun yOf(price: Double): Float = (top + (1.0 - (price - lo) / (hi - lo)) * chartH).toFloat()

        // 横向筹码柱
        val count = minOf(chip.prices.size, chip.weights.size)
        val barH = max(1f, chartH / count * 0.85f)
        for (b in 0 until count) {
            val wgt = chip.weights[b]
            if (wgt <= 0.0) continue
            val price = chip.prices[b]
            val len = (wgt / maxW * (w - 6f)).toFloat().coerceAtLeast(1f)
            val color = if (price <= cur) UpRed else TrapBlue
            drawRect(
                color = color.copy(alpha = 0.85f),
                topLeft = Offset(0f, yOf(price) - barH / 2f),
                size = Size(len, barH)
            )
        }

        // 当前价线 / 平均成本线
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))
        val curY = yOf(cur.coerceIn(lo, hi))
        drawLine(fg.copy(alpha = 0.7f), Offset(0f, curY), Offset(w, curY), 1.2f, pathEffect = dash)
        if (chip.avgCost in lo..hi) {
            val costY = yOf(chip.avgCost)
            drawLine(GoldYellow, Offset(0f, costY), Offset(w, costY), 1.2f, pathEffect = dash)
        }
    }
}
