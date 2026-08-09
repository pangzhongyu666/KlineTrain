package com.klinetrain.app.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klinetrain.app.data.model.Kline
import com.klinetrain.app.data.model.MainOverlay
import com.klinetrain.app.data.model.SubIndicator
import com.klinetrain.app.data.model.TimeFrame
import com.klinetrain.app.data.model.TradeMarker
import com.klinetrain.app.indicator.Indicators
import com.klinetrain.app.ui.theme.DownGreen
import com.klinetrain.app.ui.theme.GoldYellow
import com.klinetrain.app.ui.theme.UpRed
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ---------------- 配色常量 ----------------
private val MaLineColors = listOf(GoldYellow, Color(0xFF2196F3), Color(0xFFE040FB), Color(0xFF00BCD4))
private val FormulaColors = listOf(
    GoldYellow, Color(0xFF2196F3), Color(0xFFE040FB),
    Color(0xFF00BCD4), Color(0xFFFF7043), Color(0xFF9CCC65)
)
private val TextGray = Color(0xFF9E9EA7)
private val GridColor = Color(0x339E9EA7)
private val CrossColor = Color(0xCCAAAAB4)
private val MarkerBlue = Color(0xFF2E7CF6)
private val MinuteLineColor = Color(0xFF4FC3F7)
private val PanelBg = Color(0xE0262A33)

private const val MINUTE_DAY_BARS = 241f  // 分时全天bar数(9:30~15:00)

// ---------------- 指标缓存数据 ----------------
private class ChartData(
    val maLines: List<Pair<String, List<Double?>>>,
    val boll: Indicators.BollResult?,
    val volMa5: List<Double?>,
    val volMa10: List<Double?>,
    val macd: Indicators.MacdResult?,
    val kdj: Indicators.KdjResult?,
    val rsiLines: List<Pair<String, List<Double?>>>,
    val atr: List<Double?>?,
    val cci: List<Double?>?,
    val avgPrice: List<Double?>
)

private fun buildChartData(
    klines: List<Kline>,
    isMinute: Boolean,
    mainOverlay: MainOverlay,
    subs: List<SubIndicator>
): ChartData {
    val closes = klines.map { it.close }
    val vols = klines.map { it.volume }
    val maLines = if (isMinute) emptyList() else when (mainOverlay) {
        MainOverlay.MA -> listOf(5, 10, 20, 60).map { "MA$it" to Indicators.ma(closes, it) }
        MainOverlay.EMA -> listOf(5, 10, 20, 60).map { "EMA$it" to Indicators.ema(closes, it) }
        else -> emptyList()
    }
    val boll = if (!isMinute && mainOverlay == MainOverlay.BOLL && closes.isNotEmpty())
        Indicators.boll(closes) else null
    val needVol = isMinute || SubIndicator.VOL in subs
    val volMa5 = if (needVol && !isMinute) Indicators.ma(vols, 5) else emptyList()
    val volMa10 = if (needVol && !isMinute) Indicators.ma(vols, 10) else emptyList()
    val macd = if (!isMinute && SubIndicator.MACD in subs && closes.isNotEmpty()) Indicators.macd(closes) else null
    val kdj = if (!isMinute && SubIndicator.KDJ in subs && klines.isNotEmpty()) Indicators.kdj(klines) else null
    val rsiLines = if (!isMinute && SubIndicator.RSI in subs && closes.isNotEmpty())
        listOf(6, 12, 24).map { "RSI$it" to Indicators.rsi(closes, it) } else emptyList()
    val atr = if (!isMinute && SubIndicator.ATR in subs && klines.isNotEmpty()) Indicators.atr(klines) else null
    val cci = if (!isMinute && SubIndicator.CCI in subs && klines.isNotEmpty()) Indicators.cci(klines) else null
    val avgPrice: List<Double?> = if (isMinute) {
        var cumAmt = 0.0
        var cumVol = 0.0
        klines.map { k ->
            cumVol += k.volume
            cumAmt += if (k.amount > 0) k.amount else k.close * k.volume
            if (cumVol > 0) cumAmt / cumVol else null
        }
    } else emptyList()
    return ChartData(maLines, boll, volMa5, volMa10, macd, kdj, rsiLines, atr, cci, avgPrice)
}

// ---------------- 坐标换算 ----------------
private class XMap(val w: Float, val slot: Float, val endIdxF: Float, val leftAligned: Boolean) {
    fun x(i: Int): Float =
        if (leftAligned) (i + 0.5f) * slot
        else w - slot / 2f - (endIdxF - i) * slot

    fun index(x: Float): Float =
        if (leftAligned) x / slot - 0.5f
        else endIdxF - (w - slot / 2f - x) / slot
}

// ---------------- 主组件 ----------------
@Composable
fun KlineChartPanel(
    klines: List<Kline>,
    timeframe: TimeFrame,
    mainOverlay: MainOverlay,
    subIndicators: List<SubIndicator>,
    formulaSeries: Map<String, List<Double?>> = emptyMap(),
    formulaOnMain: Boolean = false,
    markers: List<TradeMarker> = emptyList(),
    hideDates: Boolean = false,
    showChips: Boolean = false,
    prevClose: Double? = null,
    modifier: Modifier = Modifier
) {
    val isMinute = timeframe == TimeFrame.MIN_RT
    val subs = remember(isMinute, subIndicators) {
        if (isMinute) listOf(SubIndicator.VOL)
        else if (subIndicators.contains(SubIndicator.FORMULA))
            subIndicators.filter { it != SubIndicator.FORMULA }.take(2) + SubIndicator.FORMULA
        else subIndicators.take(3)
    }

    var barWidthDp by remember { mutableFloatStateOf(6f) }
    var offsetBars by remember { mutableFloatStateOf(0f) }   // 距最右bar的偏移(单位:bar)
    val cross = remember { mutableStateOf<Offset?>(null) }   // 十字线位置

    val barCount = rememberUpdatedState(klines.size)
    val minuteState = rememberUpdatedState(isMinute)

    // 回放跟随: 新bar到来自动回到最右
    LaunchedEffect(klines.size) { offsetBars = 0f }

    val data = remember(
        klines.size, klines.firstOrNull()?.time, klines.lastOrNull()?.time,
        klines.lastOrNull()?.close, klines.lastOrNull()?.high,
        klines.lastOrNull()?.low, klines.lastOrNull()?.volume,
        mainOverlay, subs, isMinute
    ) {
        buildChartData(klines, isMinute, mainOverlay, subs)
    }
    val tm = rememberTextMeasurer()

    BoxWithConstraints(modifier = modifier) {
        val axisDp = 16.dp
        val subDp = 80.dp
        val mainHeightDp = (maxHeight - axisDp - subDp * subs.size).coerceAtLeast(40.dp)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (cross.value != null || minuteState.value) return@detectTransformGestures
                        if (zoom != 1f) {
                            barWidthDp = (barWidthDp * zoom).coerceIn(2f, 20f)
                        }
                        val slotPx = barWidthDp.dp.toPx().coerceAtLeast(1f)
                        val maxOffset = (barCount.value - 2).coerceAtLeast(0).toFloat()
                        offsetBars = (offsetBars + pan.x / slotPx).coerceIn(0f, maxOffset)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { cross.value = it },
                        onDrag = { change, _ -> cross.value = change.position },
                        onDragEnd = { cross.value = null },
                        onDragCancel = { cross.value = null }
                    )
                }
        ) {
            val w = size.width
            val h = size.height
            if (klines.isEmpty() || w <= 0f || h <= 0f) {
                val layout = tm.measure(AnnotatedString("暂无数据"), TextStyle(color = TextGray, fontSize = 12.sp))
                drawText(layout, topLeft = Offset((w - layout.size.width) / 2f, (h - layout.size.height) / 2f))
                return@Canvas
            }

            val axisH = axisDp.toPx()
            val subH = subDp.toPx()
            val mainH = (h - axisH - subH * subs.size).coerceAtLeast(40f)
            val n = klines.size

            // 可见窗口
            val slot: Float
            val xm: XMap
            val iMin: Int
            val iMax: Int
            if (isMinute) {
                slot = w / max(MINUTE_DAY_BARS, n.toFloat())
                xm = XMap(w, slot, n - 1f, leftAligned = true)
                iMin = 0
                iMax = n - 1
            } else {
                slot = barWidthDp.dp.toPx().coerceAtLeast(1f)
                val offset = offsetBars.coerceIn(0f, (n - 2).coerceAtLeast(0).toFloat())
                val endIdxF = n - 1 - offset
                xm = XMap(w, slot, endIdxF, leftAligned = false)
                iMax = min(n - 1, ceil(endIdxF).toInt())
                iMin = max(0, floor(endIdxF - w / slot).toInt())
            }

            // 十字线对应bar下标(供图例数值联动)
            val crossPos = cross.value
            val crossIdx: Int? = crossPos?.let {
                xm.index(it.x).roundToInt().coerceIn(iMin, iMax)
            }
            val valueIdx = crossIdx ?: iMax

            // ---------------- 主图价格范围 ----------------
            var lo = Double.MAX_VALUE
            var hi = -Double.MAX_VALUE
            if (isMinute) {
                for (i in iMin..iMax) {
                    lo = min(lo, klines[i].close)
                    hi = max(hi, klines[i].close)
                    data.avgPrice.getOrNull(i)?.let { lo = min(lo, it); hi = max(hi, it) }
                }
                val base = prevClose ?: klines.first().open
                if (base > 0) {
                    val dev = max(max(hi - base, base - lo), base * 0.005)
                    lo = base - dev
                    hi = base + dev
                }
            } else {
                for (i in iMin..iMax) {
                    lo = min(lo, klines[i].low)
                    hi = max(hi, klines[i].high)
                }
                for ((_, s) in data.maLines) for (i in iMin..min(iMax, s.size - 1)) {
                    s[i]?.let { lo = min(lo, it); hi = max(hi, it) }
                }
                data.boll?.let { b ->
                    for (s in listOf(b.mid, b.upper, b.lower)) for (i in iMin..min(iMax, s.size - 1)) {
                        s[i]?.let { lo = min(lo, it); hi = max(hi, it) }
                    }
                }
                if (formulaOnMain) for ((_, s) in formulaSeries) for (i in iMin..min(iMax, s.size - 1)) {
                    s[i]?.let { lo = min(lo, it); hi = max(hi, it) }
                }
            }
            if (hi <= lo) { hi = lo + max(abs(lo) * 0.01, 0.01) }
            if (!isMinute) {
                val pad = (hi - lo) * 0.06
                lo -= pad; hi += pad
            }
            val range = hi - lo
            val yMain: (Double) -> Float = { p -> ((hi - p) / range * mainH).toFloat() }

            // 主图网格与价格刻度
            val axisStyle = TextStyle(color = TextGray, fontSize = 9.sp)
            for (j in 0..4) {
                val y = mainH * j / 4f
                drawLine(GridColor, Offset(0f, y), Offset(w, y), 1f)
                val price = hi - range * j / 4
                val label = tm.measure(AnnotatedString(String.format("%.2f", price)), axisStyle)
                val ty = (y + 2f).coerceAtMost(mainH - label.size.height - 1f)
                drawText(label, topLeft = Offset(2f, ty))
                if (isMinute) {
                    val base = prevClose ?: klines.first().open
                    if (base > 0) {
                        val pct = (price - base) / base * 100
                        val pctColor = if (pct >= 0) UpRed else DownGreen
                        val pl = tm.measure(
                            AnnotatedString(String.format("%.2f%%", pct)),
                            TextStyle(color = pctColor, fontSize = 9.sp)
                        )
                        drawText(pl, topLeft = Offset(w - pl.size.width - 2f, ty))
                    }
                }
            }

            // ---------------- 主图内容 ----------------
            if (isMinute) {
                drawMinuteMain(klines, data.avgPrice, iMin, iMax, xm, yMain, mainH, prevClose)
            } else {
                drawCandles(klines, iMin, iMax, xm, yMain, slot)
                for ((idx, pair) in data.maLines.withIndex()) {
                    drawSeriesLine(pair.second, iMin, iMax, xm, yMain, MaLineColors[idx % MaLineColors.size], 1.5f)
                }
                data.boll?.let { b ->
                    drawSeriesLine(b.mid, iMin, iMax, xm, yMain, GoldYellow, 1.5f)
                    drawSeriesLine(b.upper, iMin, iMax, xm, yMain, Color(0xFF2196F3), 1.5f)
                    drawSeriesLine(b.lower, iMin, iMax, xm, yMain, Color(0xFFE040FB), 1.5f)
                }
                if (formulaOnMain) {
                    var ci = 0
                    for ((_, s) in formulaSeries) {
                        drawSeriesLine(s, iMin, iMax, xm, yMain, FormulaColors[ci % FormulaColors.size], 1.5f)
                        ci++
                    }
                }
            }

            // 主图图例
            val legendItems = ArrayList<Pair<String, Color>>()
            if (isMinute) {
                legendItems.add("价:" + fmtPrice(klines.getOrNull(valueIdx)?.close) to MinuteLineColor)
                legendItems.add("均:" + fmtPrice(data.avgPrice.getOrNull(valueIdx)) to GoldYellow)
            } else {
                for ((idx, pair) in data.maLines.withIndex()) {
                    legendItems.add(
                        "${pair.first}:" + fmtPrice(pair.second.getOrNull(valueIdx))
                                to MaLineColors[idx % MaLineColors.size]
                    )
                }
                data.boll?.let { b ->
                    legendItems.add("BOLL:" + fmtPrice(b.mid.getOrNull(valueIdx)) to GoldYellow)
                    legendItems.add("UB:" + fmtPrice(b.upper.getOrNull(valueIdx)) to Color(0xFF2196F3))
                    legendItems.add("LB:" + fmtPrice(b.lower.getOrNull(valueIdx)) to Color(0xFFE040FB))
                }
                if (formulaOnMain) {
                    var ci = 0
                    for ((name, s) in formulaSeries) {
                        legendItems.add("$name:" + fmtPrice(s.getOrNull(valueIdx)) to FormulaColors[ci % FormulaColors.size])
                        ci++
                    }
                }
            }
            drawLegend(tm, 4f, 2f, legendItems)

            // ---------------- 副图 ----------------
            for ((k, sub) in subs.withIndex()) {
                drawSubChart(
                    type = sub, top = mainH + k * subH, height = subH,
                    iMin = iMin, iMax = iMax, xm = xm, slot = slot,
                    klines = klines, data = data, formulaSeries = formulaSeries,
                    tm = tm, valueIdx = valueIdx, isMinute = isMinute,
                    minuteBase = prevClose ?: klines.first().open
                )
            }

            // ---------------- x轴标签 ----------------
            val labelCount = max(2, (w / 90.dp.toPx()).toInt() + 1)
            val axisTop = h - axisH
            drawLine(GridColor, Offset(0f, axisTop), Offset(w, axisTop), 1f)
            if (iMax > iMin) {
                for (j in 0 until labelCount) {
                    val i = iMin + (iMax - iMin) * j / (labelCount - 1)
                    val text = if (hideDates && !isMinute) "编号$i" else klines[i].label
                    val layout = tm.measure(AnnotatedString(text), axisStyle)
                    val tx = (xm.x(i) - layout.size.width / 2f).coerceIn(0f, max(0f, w - layout.size.width))
                    drawText(layout, topLeft = Offset(tx, axisTop + 2f))
                }
            }

            // ---------------- 交易标记 B/S ----------------
            drawMarkers(markers, klines, iMin, iMax, xm, yMain, mainH, isMinute, tm)

            // ---------------- 十字线 ----------------
            if (crossPos != null && crossIdx != null) {
                drawCrosshair(
                    crossPos, crossIdx, klines, xm, hi, range, mainH, h, axisH,
                    hideDates, isMinute, prevClose, data.avgPrice, tm
                )
            }
        }

        if (showChips) {
            ChipPanel(
                klines = klines,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxWidth(0.35f)
                    .height(mainHeightDp)
            )
        }
    }
}

// ---------------- 蜡烛图 ----------------
private fun DrawScope.drawCandles(
    klines: List<Kline>, iMin: Int, iMax: Int, xm: XMap, yOf: (Double) -> Float, slot: Float
) {
    val bodyW = max(1f, slot * 0.72f)
    val wickW = max(1f, slot * 0.1f)
    for (i in iMin..iMax) {
        val k = klines[i]
        val color = if (k.isUp) UpRed else DownGreen
        val x = xm.x(i)
        // 影线
        drawLine(color, Offset(x, yOf(k.high)), Offset(x, yOf(k.low)), wickW)
        // 实体
        val top = yOf(max(k.open, k.close))
        val bot = yOf(min(k.open, k.close))
        drawRect(color, Offset(x - bodyW / 2f, top), Size(bodyW, max(1.2f, bot - top)))
    }
}

// ---------------- 分时主图 ----------------
private fun DrawScope.drawMinuteMain(
    klines: List<Kline>, avgPrice: List<Double?>, iMin: Int, iMax: Int,
    xm: XMap, yOf: (Double) -> Float, mainH: Float, prevClose: Double?
) {
    // 昨收虚线基准
    prevClose?.let { base ->
        val y = yOf(base)
        if (y in 0f..mainH) {
            drawLine(
                TextGray, Offset(0f, y), Offset(size.width, y), 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            )
        }
    }
    // 价格线 + 面积填充
    val path = Path()
    val fill = Path()
    var started = false
    var lastX = 0f
    for (i in iMin..iMax) {
        val x = xm.x(i)
        val y = yOf(klines[i].close)
        if (!started) {
            path.moveTo(x, y)
            fill.moveTo(x, mainH)
            fill.lineTo(x, y)
            started = true
        } else {
            path.lineTo(x, y)
            fill.lineTo(x, y)
        }
        lastX = x
    }
    if (started) {
        fill.lineTo(lastX, mainH)
        fill.close()
        drawPath(fill, MinuteLineColor.copy(alpha = 0.12f))
        drawPath(path, MinuteLineColor, style = Stroke(width = 2f))
    }
    // 均价线(成交额加权)
    drawSeriesLine(avgPrice, iMin, iMax, xm, yOf, GoldYellow, 1.8f)
}

// ---------------- 副图 ----------------
private fun DrawScope.drawSubChart(
    type: SubIndicator, top: Float, height: Float,
    iMin: Int, iMax: Int, xm: XMap, slot: Float,
    klines: List<Kline>, data: ChartData, formulaSeries: Map<String, List<Double?>>,
    tm: TextMeasurer, valueIdx: Int, isMinute: Boolean, minuteBase: Double
) {
    drawLine(GridColor, Offset(0f, top), Offset(size.width, top), 1f)
    val headH = 13.sp.toPx() + 3f
    val chartTop = top + headH
    val chartH = (height - headH - 3f).coerceAtLeast(10f)
    val bodyW = max(1f, slot * 0.72f)

    fun yMapper(lo: Double, hi: Double): (Double) -> Float {
        val r = if (hi > lo) hi - lo else 1.0
        return { v -> (chartTop + (hi - v) / r * chartH).toFloat() }
    }

    when (type) {
        SubIndicator.VOL -> {
            var maxVol = 0.0
            for (i in iMin..iMax) maxVol = max(maxVol, klines[i].volume)
            for (s in listOf(data.volMa5, data.volMa10)) for (i in iMin..min(iMax, s.size - 1)) {
                s[i]?.let { maxVol = max(maxVol, it) }
            }
            if (maxVol <= 0.0) maxVol = 1.0
            for (i in iMin..iMax) {
                val k = klines[i]
                val up = if (isMinute) {
                    val prev = if (i > 0) klines[i - 1].close else minuteBase
                    k.close >= prev
                } else k.isUp
                val color = if (up) UpRed else DownGreen
                val barH = (k.volume / maxVol * chartH).toFloat().coerceAtLeast(0f)
                drawRect(color, Offset(xm.x(i) - bodyW / 2f, chartTop + chartH - barH), Size(bodyW, max(1f, barH)))
            }
            val yOf = yMapper(0.0, maxVol)
            if (!isMinute) {
                drawSeriesLine(data.volMa5, iMin, iMax, xm, yOf, GoldYellow, 1.3f)
                drawSeriesLine(data.volMa10, iMin, iMax, xm, yOf, Color(0xFF2196F3), 1.3f)
            }
            val items = ArrayList<Pair<String, Color>>()
            items.add("VOL:" + fmtVol(klines.getOrNull(valueIdx)?.volume ?: 0.0) to TextGray)
            if (!isMinute) {
                items.add("MA5:" + (data.volMa5.getOrNull(valueIdx)?.let { fmtVol(it) } ?: "--") to GoldYellow)
                items.add("MA10:" + (data.volMa10.getOrNull(valueIdx)?.let { fmtVol(it) } ?: "--") to Color(0xFF2196F3))
            }
            drawLegend(tm, 4f, top + 2f, items)
        }

        SubIndicator.MACD -> {
            val m = data.macd ?: return
            val r = visRange(iMin, iMax, listOf(m.dif, m.dea, m.hist)) ?: return
            val lo = min(r.first, 0.0)
            val hi = max(r.second, 0.0)
            val yOf = yMapper(lo, hi)
            val zeroY = yOf(0.0)
            drawLine(GridColor, Offset(0f, zeroY), Offset(size.width, zeroY), 1f)
            val histW = max(1f, slot * 0.4f)
            for (i in iMin..min(iMax, m.hist.size - 1)) {
                val v = m.hist[i] ?: continue
                val y = yOf(v)
                val color = if (v >= 0) UpRed else DownGreen
                drawRect(color, Offset(xm.x(i) - histW / 2f, min(y, zeroY)), Size(histW, max(1f, abs(y - zeroY))))
            }
            drawSeriesLine(m.dif, iMin, iMax, xm, yOf, GoldYellow, 1.3f)
            drawSeriesLine(m.dea, iMin, iMax, xm, yOf, Color(0xFF2196F3), 1.3f)
            drawLegend(
                tm, 4f, top + 2f, listOf(
                    "MACD" to TextGray,
                    "DIF:" + fmtPrice(m.dif.getOrNull(valueIdx)) to GoldYellow,
                    "DEA:" + fmtPrice(m.dea.getOrNull(valueIdx)) to Color(0xFF2196F3),
                    "M:" + fmtPrice(m.hist.getOrNull(valueIdx)) to Color(0xFFE040FB)
                )
            )
        }

        SubIndicator.KDJ -> {
            val d = data.kdj ?: return
            val r = visRange(iMin, iMax, listOf(d.k, d.d, d.j)) ?: return
            val yOf = yMapper(r.first, r.second)
            drawSeriesLine(d.k, iMin, iMax, xm, yOf, GoldYellow, 1.3f)
            drawSeriesLine(d.d, iMin, iMax, xm, yOf, Color(0xFF2196F3), 1.3f)
            drawSeriesLine(d.j, iMin, iMax, xm, yOf, Color(0xFFE040FB), 1.3f)
            drawLegend(
                tm, 4f, top + 2f, listOf(
                    "K:" + fmtPrice(d.k.getOrNull(valueIdx)) to GoldYellow,
                    "D:" + fmtPrice(d.d.getOrNull(valueIdx)) to Color(0xFF2196F3),
                    "J:" + fmtPrice(d.j.getOrNull(valueIdx)) to Color(0xFFE040FB)
                )
            )
        }

        SubIndicator.RSI -> {
            if (data.rsiLines.isEmpty()) return
            val r = visRange(iMin, iMax, data.rsiLines.map { it.second }) ?: return
            val yOf = yMapper(r.first, r.second)
            val items = ArrayList<Pair<String, Color>>()
            for ((idx, pair) in data.rsiLines.withIndex()) {
                val c = MaLineColors[idx % MaLineColors.size]
                drawSeriesLine(pair.second, iMin, iMax, xm, yOf, c, 1.3f)
                items.add("${pair.first}:" + fmtPrice(pair.second.getOrNull(valueIdx)) to c)
            }
            drawLegend(tm, 4f, top + 2f, items)
        }

        SubIndicator.ATR -> {
            val s = data.atr ?: return
            val r = visRange(iMin, iMax, listOf(s)) ?: return
            val yOf = yMapper(r.first, r.second)
            drawSeriesLine(s, iMin, iMax, xm, yOf, GoldYellow, 1.3f)
            drawLegend(tm, 4f, top + 2f, listOf("ATR:" + fmtPrice(s.getOrNull(valueIdx)) to GoldYellow))
        }

        SubIndicator.CCI -> {
            val s = data.cci ?: return
            val r = visRange(iMin, iMax, listOf(s)) ?: return
            val lo = min(r.first, -100.0)
            val hi = max(r.second, 100.0)
            val yOf = yMapper(lo, hi)
            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            for (ref in listOf(100.0, 0.0, -100.0)) {
                val y = yOf(ref)
                drawLine(GridColor, Offset(0f, y), Offset(size.width, y), 1f, pathEffect = dash)
            }
            drawSeriesLine(s, iMin, iMax, xm, yOf, GoldYellow, 1.3f)
            drawLegend(tm, 4f, top + 2f, listOf("CCI:" + fmtPrice(s.getOrNull(valueIdx)) to GoldYellow))
        }

        SubIndicator.FORMULA -> {
            if (formulaSeries.isEmpty()) {
                drawLegend(tm, 4f, top + 2f, listOf("自定义公式: 无数据" to TextGray))
                return
            }
            val r = visRange(iMin, iMax, formulaSeries.values.toList()) ?: return
            val yOf = yMapper(r.first, r.second)
            val items = ArrayList<Pair<String, Color>>()
            var ci = 0
            for ((name, s) in formulaSeries) {
                val c = FormulaColors[ci % FormulaColors.size]
                drawSeriesLine(s, iMin, iMax, xm, yOf, c, 1.3f)
                items.add("$name:" + fmtPrice(s.getOrNull(valueIdx)) to c)
                ci++
            }
            drawLegend(tm, 4f, top + 2f, items)
        }
    }
}

// ---------------- 交易标记 ----------------
private fun DrawScope.drawMarkers(
    markers: List<TradeMarker>, klines: List<Kline>,
    iMin: Int, iMax: Int, xm: XMap, yOf: (Double) -> Float,
    mainH: Float, isMinute: Boolean, tm: TextMeasurer
) {
    if (markers.isEmpty()) return
    val r = 5.dp.toPx()
    val style = TextStyle(color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    for (mk in markers) {
        val i = mk.barIndex
        if (i < iMin || i > iMax || i < 0 || i >= klines.size) continue
        val k = klines[i]
        val x = xm.x(i)
        val cy: Float
        val color: Color
        val text: String
        if (mk.isOpen) {
            // 开仓 B: bar下方, 红底
            val anchor = if (isMinute) yOf(k.close) else yOf(k.low)
            cy = (anchor + r * 2 + 3f).coerceIn(0f, max(0f, mainH - r))
            color = UpRed
            text = "B"
        } else {
            // 平仓 S: bar上方, 蓝底
            val anchor = if (isMinute) yOf(k.close) else yOf(k.high)
            cy = (anchor - r * 2 - 3f).coerceIn(0f, max(0f, mainH - r))
            color = MarkerBlue
            text = "S"
        }
        drawCircle(color, r, Offset(x, cy))
        val layout = tm.measure(AnnotatedString(text), style)
        drawText(layout, topLeft = Offset(x - layout.size.width / 2f, cy - layout.size.height / 2f))
    }
}

// ---------------- 十字线 ----------------
private fun DrawScope.drawCrosshair(
    pos: Offset, ci: Int, klines: List<Kline>, xm: XMap,
    hi: Double, range: Double, mainH: Float, h: Float, axisH: Float,
    hideDates: Boolean, isMinute: Boolean, prevClose: Double?,
    avgPrice: List<Double?>, tm: TextMeasurer
) {
    val w = size.width
    val x = xm.x(ci)
    val chartBottom = h - axisH
    // 竖线
    drawLine(CrossColor, Offset(x, 0f), Offset(x, chartBottom), 1.5f)
    // 横线(仅主图区域) + 价格标签
    if (pos.y in 0f..mainH) {
        drawLine(CrossColor, Offset(0f, pos.y), Offset(w, pos.y), 1.5f)
        val price = hi - pos.y / mainH * range
        val pl = tm.measure(
            AnnotatedString(String.format("%.2f", price)),
            TextStyle(color = Color.White, fontSize = 9.sp)
        )
        val py = (pos.y - pl.size.height / 2f).coerceIn(0f, max(0f, mainH - pl.size.height))
        drawRoundRect(PanelBg, Offset(0f, py - 1f), Size(pl.size.width + 8f, pl.size.height + 2f), CornerRadius(3f))
        drawText(pl, topLeft = Offset(4f, py))
    }
    // x轴时间标签
    val k = klines[ci]
    val xLabel = if (hideDates && !isMinute) "编号$ci" else k.label
    val xl = tm.measure(AnnotatedString(xLabel), TextStyle(color = Color.White, fontSize = 9.sp))
    val tx = (x - xl.size.width / 2f).coerceIn(0f, max(0f, w - xl.size.width - 8f))
    drawRoundRect(PanelBg, Offset(tx - 4f, chartBottom), Size(xl.size.width + 8f, axisH), CornerRadius(3f))
    drawText(xl, topLeft = Offset(tx, chartBottom + (axisH - xl.size.height) / 2f))

    // 信息浮层
    val lines = ArrayList<Pair<String, Color>>()
    if (isMinute) {
        val base = prevClose ?: klines.first().open
        val chg = if (base > 0) (k.close - base) / base * 100 else 0.0
        val cc = if (chg >= 0) UpRed else DownGreen
        lines.add("时间 " + k.label to Color.White)
        lines.add("价格 " + String.format("%.2f", k.close) to cc)
        lines.add("均价 " + fmtPrice(avgPrice.getOrNull(ci)) to GoldYellow)
        lines.add("涨跌 " + String.format("%.2f%%", chg) to cc)
        lines.add("成交量 " + fmtVol(k.volume) to Color.White)
    } else {
        val pc = if (ci > 0) klines[ci - 1].close else k.open
        fun colorOf(v: Double) = if (pc > 0 && v >= pc) UpRed else DownGreen
        val chg = if (pc > 0) (k.close - pc) / pc * 100 else 0.0
        lines.add((if (hideDates) "编号$ci" else k.label) to Color.White)
        lines.add("开盘 " + String.format("%.2f", k.open) to colorOf(k.open))
        lines.add("最高 " + String.format("%.2f", k.high) to colorOf(k.high))
        lines.add("最低 " + String.format("%.2f", k.low) to colorOf(k.low))
        lines.add("收盘 " + String.format("%.2f", k.close) to colorOf(k.close))
        lines.add("涨跌幅 " + String.format("%.2f%%", chg) to (if (chg >= 0) UpRed else DownGreen))
        lines.add("成交量 " + fmtVol(k.volume) to Color.White)
    }
    val style = TextStyle(fontSize = 9.sp)
    val layouts = lines.map { (s, c) -> tm.measure(AnnotatedString(s), style.copy(color = c)) }
    val lineH = (layouts.maxOfOrNull { it.size.height } ?: 12).toFloat() + 3f
    val panelW = (layouts.maxOfOrNull { it.size.width } ?: 60).toFloat() + 16f
    val panelH = lineH * layouts.size + 10f
    val px = if (pos.x < w / 2f) w - panelW - 8f else 8f
    val py = 6f
    drawRoundRect(PanelBg, Offset(px, py), Size(panelW, panelH), CornerRadius(8f))
    var ty = py + 5f
    for (layout in layouts) {
        drawText(layout, topLeft = Offset(px + 8f, ty))
        ty += lineH
    }
}

// ---------------- 通用绘制/工具 ----------------
private fun DrawScope.drawSeriesLine(
    series: List<Double?>, iMin: Int, iMax: Int, xm: XMap,
    yOf: (Double) -> Float, color: Color, width: Float
) {
    if (series.isEmpty()) return
    val path = Path()
    var started = false
    for (i in max(0, iMin)..min(iMax, series.size - 1)) {
        val v = series[i]
        if (v == null || v.isNaN() || v.isInfinite()) {
            started = false
            continue
        }
        val x = xm.x(i)
        val y = yOf(v)
        if (!started) {
            path.moveTo(x, y)
            started = true
        } else {
            path.lineTo(x, y)
        }
    }
    drawPath(path, color, style = Stroke(width = width))
}

private fun DrawScope.drawLegend(tm: TextMeasurer, x0: Float, y: Float, items: List<Pair<String, Color>>) {
    var x = x0
    for ((text, color) in items) {
        val layout = tm.measure(AnnotatedString(text), TextStyle(color = color, fontSize = 9.sp))
        if (x + layout.size.width > size.width) break
        drawText(layout, topLeft = Offset(x, y))
        x += layout.size.width + 8f
    }
}

private fun visRange(iMin: Int, iMax: Int, seriesList: List<List<Double?>>): Pair<Double, Double>? {
    var lo = Double.MAX_VALUE
    var hi = -Double.MAX_VALUE
    for (s in seriesList) {
        for (i in max(0, iMin)..min(iMax, s.size - 1)) {
            val v = s[i] ?: continue
            if (v.isNaN() || v.isInfinite()) continue
            lo = min(lo, v)
            hi = max(hi, v)
        }
    }
    if (hi < lo) return null
    if (hi == lo) {
        val pad = max(abs(hi) * 0.05, 0.5)
        return (lo - pad) to (hi + pad)
    }
    val pad = (hi - lo) * 0.08
    return (lo - pad) to (hi + pad)
}

internal fun fmtPrice(v: Double?): String = if (v == null) "--" else String.format("%.2f", v)

internal fun fmtVol(v: Double): String = when {
    v >= 1e8 -> String.format("%.2f亿", v / 1e8)
    v >= 1e4 -> String.format("%.2f万", v / 1e4)
    else -> String.format("%.0f", v)
}
