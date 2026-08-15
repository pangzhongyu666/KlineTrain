package com.klinetrain.app.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import com.klinetrain.app.data.model.AxisType
import com.klinetrain.app.data.model.CandleStyle
import com.klinetrain.app.data.model.ChartStyle
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
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
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
private val LimitDownBlue = Color(0xFF3B6FE8)   // 跌停蓝
private val TrapBlue = Color(0xFF2E7CF6)        // 筹码套牢盘
private val WatermarkColor = Color(0x2E9E9EA7)  // 品牌水印

private const val MINUTE_DAY_BARS = 241f  // 分时全天bar数(9:30~15:00)
private const val CHIP_AREA_FRAC = 0.35f  // 筹码区占宽比例
private const val RIGHT_PAD_FRAC = 0.15f  // K线右边距占宽比例
private const val EDGE_PAD_DP = 6f        // 最右K线固定内边距(dp), 防贴边裁剪

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
        // 五日模式标签带日期前缀("MM-dd HH:mm"), 换日时重置累计——均价线是日内口径;
        // 单日分时标签无空格, 前缀恒为空串不触发重置
        var lastDay: String? = null
        klines.map { k ->
            val day = k.label.substringBefore(' ', "")
            if (day != lastDay) {
                if (lastDay != null) {
                    cumAmt = 0.0
                    cumVol = 0.0
                }
                lastDay = day
            }
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
    chartStyle: ChartStyle = ChartStyle(),
    costPrice: Double? = null,
    limitPct: Double? = 0.095,
    onLoadMoreHistory: (() -> Unit)? = null,  // 用户平移到最左端时触发(组件内去抖); 分时不触发
    loadingMore: Boolean = false,             // true时图表左上角显示"加载更早K线..."小字
    modifier: Modifier = Modifier
) {
    val isMinute = timeframe == TimeFrame.MIN_RT || timeframe == TimeFrame.DAY5
    val chipsOn = showChips && !isMinute && klines.isNotEmpty()
    val subs = remember(isMinute, subIndicators) {
        if (isMinute) listOf(SubIndicator.VOL)
        else if (subIndicators.contains(SubIndicator.FORMULA))
            subIndicators.filter { it != SubIndicator.FORMULA }.take(2) + SubIndicator.FORMULA
        else subIndicators.take(3)
    }

    var offsetBars by remember { mutableFloatStateOf(0f) }   // 距最右bar的偏移(单位:bar)
    val cross = remember { mutableStateOf<Offset?>(null) }   // 十字线位置

    val barCount = rememberUpdatedState(klines.size)
    val minuteState = rememberUpdatedState(isMinute)
    val loadingMoreState = rememberUpdatedState(loadingMore)
    val loadMoreCallback = rememberUpdatedState(onLoadMoreHistory)
    // 手势内读取的最新值(pointerInput(Unit) 不随设置变化重启, 避免捕获过期值)
    val rightPaddingState = rememberUpdatedState(chartStyle.rightPadding)
    val chipsOnState = rememberUpdatedState(chipsOn)
    // 左滑加载去抖标志: 同一"到达最左"状态只触发一次
    val loadMoreFired = remember { mutableStateOf(false) }

    // 回放跟随: 仅"尾部追加新bar"(最后一根时间变化)时回到最右。
    // 加载更早历史 prepend 时最后一根时间不变、offsetBars(距最右偏移)不变、maxOffset 增大,
    // 视图天然停在原位, 不会被强行拉回最右。
    LaunchedEffect(klines.lastOrNull()?.time) { offsetBars = 0f }
    // klines 数量变化(prepend/append)后才允许再次触发加载更早历史
    LaunchedEffect(klines.size) { loadMoreFired.value = false }

    // 指标缓存key包含最后一根close/high/low/volume: 周K/月K回放时最后一根原地更新也能刷新
    val data = remember(
        klines.size, klines.firstOrNull()?.time, klines.lastOrNull()?.time,
        klines.lastOrNull()?.close, klines.lastOrNull()?.high,
        klines.lastOrNull()?.low, klines.lastOrNull()?.volume,
        mainOverlay, subs, isMinute
    ) {
        buildChartData(klines, isMinute, mainOverlay, subs)
    }
    // 筹码分布(基于全部klines到最后一根)
    val chipData = remember(
        chipsOn, klines.size, klines.lastOrNull()?.time,
        klines.lastOrNull()?.close, klines.lastOrNull()?.volume
    ) {
        if (chipsOn) Indicators.chipDistribution(klines, klines.size - 1) else null
    }
    val tm = rememberTextMeasurer()
    val chipBg = MaterialTheme.colorScheme.surface
    val chipFg = MaterialTheme.colorScheme.onSurface

    // 涨跌配色(绿涨红跌反转)
    val upColor = if (chartStyle.greenUp) DownGreen else UpRed
    val downColor = if (chartStyle.greenUp) UpRed else DownGreen

    BoxWithConstraints(modifier = modifier) {
        val axisDp = 16.dp
        val subDp = 80.dp

        // K线初始数量: 初始bar宽 = 绘图区宽 / initialBars, 仍可手势缩放
        val initBars = chartStyle.initialBars.coerceIn(20, 500)
        var barWidthDp by remember { mutableFloatStateOf(6f) }
        var appliedInitBars by remember { mutableIntStateOf(Int.MIN_VALUE) }
        if (appliedInitBars != initBars) {
            appliedInitBars = initBars
            val widthDp = if (maxWidth.value > 0f) maxWidth.value else 360f
            val frac = (if (chipsOn) 1f - CHIP_AREA_FRAC else 1f) *
                    (if (chartStyle.rightPadding && !isMinute) 1f - RIGHT_PAD_FRAC else 1f)
            // 扣除最右6dp固定内边距后再按initialBars均分
            val plotDp = (widthDp * frac - (if (isMinute) 0f else EDGE_PAD_DP)).coerceAtLeast(1f)
            barWidthDp = (plotDp / initBars).coerceIn(2f, 20f)
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (cross.value != null || minuteState.value) return@detectTransformGestures
                        if (zoom != 1f) {
                            barWidthDp = (barWidthDp * zoom).coerceIn(2f, 20f)
                        }
                        val slotPx = barWidthDp.dp.toPx().coerceAtLeast(1f)
                        // 绘图区宽度(与Canvas内主图口径一致: 筹码收窄+右侧留白+固定内边距)
                        val padFrac = if (rightPaddingState.value) 1f - RIGHT_PAD_FRAC else 1f
                        val mainPlotW = if (chipsOnState.value) size.width * (1f - CHIP_AREA_FRAC)
                        else size.width.toFloat()
                        val mapW = (mainPlotW * padFrac - EDGE_PAD_DP.dp.toPx()).coerceAtLeast(1f)
                        val visBars = mapW / slotPx
                        // 平移上界: 最老K线到达绘图区左缘为止, 不再留出整幅空白
                        val maxOffset = (barCount.value - 1 - visBars).coerceAtLeast(0f)
                        offsetBars = (offsetBars + pan.x / slotPx).coerceIn(0f, maxOffset)
                        // 平移到最左端(最老一根bar已进入可见区)时触发加载更早历史;
                        // 去抖: 触发一次后置标志位, 等 klines.size 变化才允许再次触发; 加载中不触发
                        if (pan.x > 0f && !loadingMoreState.value && !loadMoreFired.value) {
                            val leftReach = barCount.value - 1 - visBars
                            if (offsetBars >= leftReach - 0.5f) {
                                loadMoreCallback.value?.let { cb ->
                                    loadMoreFired.value = true
                                    cb()
                                }
                            }
                        }
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
                drawText(layout, topLeft = Offset(max(0f, (w - layout.size.width) / 2f), max(0f, (h - layout.size.height) / 2f)))
                return@Canvas
            }

            val axisH = axisDp.toPx()
            // 副图高度自适应: 常规为80dp, 图表整体高度不足(横屏/小窗)时副图最多共占50%高度,
            // 保证主图始终至少占剩余高度的一半, 不再溢出画布
            val availH = (h - axisH).coerceAtLeast(1f)
            val subH = if (subs.isEmpty()) 0f else min(subDp.toPx(), availH * 0.5f / subs.size)
            val mainH = (availH - subH * subs.size).coerceAtLeast(1f)
            val n = klines.size

            // 主图K线绘制区: 开启筹码时收窄到65%, 右侧35%为整列筹码栏; 副图与x轴同步收窄对齐
            val mainPlotW = if (chipsOn) w * (1f - CHIP_AREA_FRAC) else w
            val padFrac = if (chartStyle.rightPadding && !isMinute) 1f - RIGHT_PAD_FRAC else 1f
            // 最后一根K线右侧固定预留6dp内边距(独立于15%大留白, 两者叠加),
            // 保证最右蜡烛的右半边与影线完整可见不贴边; 十字线反算共用同一XMap保持一致
            val edgePad = if (isMinute) 0f else EDGE_PAD_DP.dp.toPx()
            val mainMapW = (mainPlotW * padFrac - edgePad).coerceAtLeast(1f)

            // 可见窗口: 主图与副图共用同一x映射, 开启筹码时一起收窄, 保证K线与副图bar严格对齐
            val slot: Float
            val xmMain: XMap
            val iMinMain: Int
            val iMaxMain: Int
            if (isMinute) {
                slot = w / max(MINUTE_DAY_BARS, n.toFloat())
                xmMain = XMap(w, slot, n - 1f, leftAligned = true)
                iMinMain = 0; iMaxMain = n - 1
            } else {
                slot = barWidthDp.dp.toPx().coerceAtLeast(1f)
                // 与手势内同口径: 最老K线最多平移到绘图区左缘
                val offset = offsetBars.coerceIn(0f, (n - 1 - mainMapW / slot).coerceAtLeast(0f))
                val endIdxF = n - 1 - offset
                xmMain = XMap(mainMapW, slot, endIdxF, leftAligned = false)
                iMaxMain = min(n - 1, ceil(endIdxF).toInt()).coerceAtLeast(0)
                iMinMain = max(0, floor(endIdxF - mainMapW / slot).toInt()).coerceAtMost(iMaxMain)
            }

            // 十字线对应bar下标(供图例数值联动)
            val crossPos = cross.value
            val crossIdx: Int? = crossPos?.let {
                xmMain.index(it.x).roundToInt().coerceIn(iMinMain, iMaxMain)
            }
            val valueIdx = crossIdx ?: iMaxMain

            // ---------------- 主图价格范围 ----------------
            var lo = Double.MAX_VALUE
            var hi = -Double.MAX_VALUE
            if (isMinute) {
                for (i in iMinMain..iMaxMain) {
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
                for (i in iMinMain..iMaxMain) {
                    lo = min(lo, klines[i].low)
                    hi = max(hi, klines[i].high)
                }
                for ((_, s) in data.maLines) for (i in iMinMain..min(iMaxMain, s.size - 1)) {
                    s[i]?.let { lo = min(lo, it); hi = max(hi, it) }
                }
                data.boll?.let { b ->
                    for (s in listOf(b.mid, b.upper, b.lower)) for (i in iMinMain..min(iMaxMain, s.size - 1)) {
                        s[i]?.let { lo = min(lo, it); hi = max(hi, it) }
                    }
                }
                if (formulaOnMain) for ((_, s) in formulaSeries) for (i in iMinMain..min(iMaxMain, s.size - 1)) {
                    s[i]?.let { lo = min(lo, it); hi = max(hi, it) }
                }
            }
            if (hi <= lo) { hi = lo + max(abs(lo) * 0.01, 0.01) }
            if (!isMinute) {
                val pad = (hi - lo) * 0.06
                lo -= pad; hi += pad
            }
            val range = hi - lo

            // 坐标类型: 普通/对数(主图y用log10映射, 刻度仍显示原价)
            val useLog = !isMinute && chartStyle.axisType == AxisType.LOG && lo > 0
            val yMain: (Double) -> Float
            val priceOfY: (Float) -> Double
            if (useLog) {
                val logLo = log10(lo)
                val logHi = log10(hi)
                val logRange = max(logHi - logLo, 1e-9)
                yMain = { p -> ((logHi - log10(max(p, 1e-9))) / logRange * mainH).toFloat() }
                priceOfY = { y -> 10.0.pow(logHi - y / mainH * logRange) }
            } else {
                yMain = { p -> ((hi - p) / range * mainH).toFloat() }
                priceOfY = { y -> hi - y / mainH * range }
            }

            // 主图网格与价格刻度(百分比坐标: 右轴显示相对可见区间第一根收盘价的涨跌%)
            val axisStyle = TextStyle(color = TextGray, fontSize = 9.sp)
            val percentBase: Double? =
                if (!isMinute && chartStyle.axisType == AxisType.PERCENT)
                    klines[iMinMain.coerceIn(0, n - 1)].close.takeIf { it > 0 }
                else null
            for (j in 0..4) {
                val y = mainH * j / 4f
                drawLine(GridColor, Offset(0f, y), Offset(w, y), 1f)
                val price = priceOfY(y)
                val label = tm.measure(AnnotatedString(String.format("%.2f", price)), axisStyle)
                val ty = (y + 2f).coerceAtMost(max(0f, mainH - label.size.height - 1f))
                drawText(label, topLeft = Offset(2f, ty))
                if (isMinute) {
                    val base = prevClose ?: klines.first().open
                    if (base > 0) {
                        val pct = (price - base) / base * 100
                        val pctColor = if (pct >= 0) upColor else downColor
                        val pl = tm.measure(
                            AnnotatedString(String.format("%.2f%%", pct)),
                            TextStyle(color = pctColor, fontSize = 9.sp)
                        )
                        drawText(pl, topLeft = Offset(max(0f, w - pl.size.width - 2f), ty))
                    }
                } else if (percentBase != null) {
                    val pct = (price - percentBase) / percentBase * 100
                    val pctColor = if (pct >= 0) upColor else downColor
                    val pl = tm.measure(
                        AnnotatedString(String.format("%+.2f%%", pct)),
                        TextStyle(color = pctColor, fontSize = 9.sp)
                    )
                    drawText(pl, topLeft = Offset(max(0f, mainPlotW - pl.size.width - 2f), ty))
                }
            }

            // 品牌水印(主图中央, 半透明)
            if (chartStyle.watermark) {
                val wm = tm.measure(
                    AnnotatedString("K线训练助手"),
                    TextStyle(color = WatermarkColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                )
                drawText(wm, topLeft = Offset(max(0f, (mainPlotW - wm.size.width) / 2f), max(0f, (mainH - wm.size.height) / 2f)))
            }

            // ---------------- 主图内容 ----------------
            if (isMinute) {
                drawMinuteMain(klines, data.avgPrice, iMinMain, iMaxMain, xmMain, yMain, mainH, prevClose)
            } else {
                drawCandles(
                    klines, iMinMain, iMaxMain, xmMain, yMain, slot, mainH,
                    chartStyle.candleStyle, upColor, downColor,
                    chartStyle.limitColors, limitPct, prevClose
                )
                for ((idx, pair) in data.maLines.withIndex()) {
                    drawSeriesLine(pair.second, iMinMain, iMaxMain, xmMain, yMain, MaLineColors[idx % MaLineColors.size], 1.5f)
                }
                data.boll?.let { b ->
                    drawSeriesLine(b.mid, iMinMain, iMaxMain, xmMain, yMain, GoldYellow, 1.5f)
                    drawSeriesLine(b.upper, iMinMain, iMaxMain, xmMain, yMain, Color(0xFF2196F3), 1.5f)
                    drawSeriesLine(b.lower, iMinMain, iMaxMain, xmMain, yMain, Color(0xFFE040FB), 1.5f)
                }
                if (formulaOnMain) {
                    var ci = 0
                    for ((_, s) in formulaSeries) {
                        drawSeriesLine(s, iMinMain, iMaxMain, xmMain, yMain, FormulaColors[ci % FormulaColors.size], 1.5f)
                        ci++
                    }
                }
            }

            // ---------------- 最新价线 / 成本线 ----------------
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            // 左侧标签下限: 避开左上角图例行(标签背景不透明, 会盖住图例)
            val minTagY = 12.dp.toPx()
            if (!isMinute && chartStyle.lastPriceLine) {
                val lastC = klines[n - 1].close
                val ly = yMain(lastC)
                if (ly in 0f..mainH) {
                    val prev = if (n >= 2) klines[n - 2].close else klines[n - 1].open
                    val lc = if (lastC >= prev) upColor else downColor
                    drawLine(lc.copy(alpha = 0.85f), Offset(0f, ly), Offset(mainPlotW, ly), 1.2f, pathEffect = dashEffect)
                    val pl = tm.measure(AnnotatedString(String.format("%.2f", lastC)), TextStyle(color = Color.White, fontSize = 8.sp))
                    val py = (ly - pl.size.height / 2f).coerceIn(minTagY, max(minTagY, mainH - pl.size.height))
                    // 价格标签放左侧, 避免遮挡右侧最新K线
                    val px = 2f
                    drawRoundRect(lc, Offset(px, py - 1f), Size(pl.size.width + 8f, pl.size.height + 2f), CornerRadius(3f))
                    drawText(pl, topLeft = Offset(px + 4f, py))
                }
            }
            if (!isMinute && chartStyle.costLineEnabled && costPrice != null && costPrice > 0) {
                val cy = yMain(costPrice)
                if (cy in 0f..mainH) {
                    drawLine(GoldYellow, Offset(0f, cy), Offset(mainPlotW, cy), 1.2f, pathEffect = dashEffect)
                    val pl = tm.measure(
                        AnnotatedString("成本" + String.format("%.2f", costPrice)),
                        TextStyle(color = Color.White, fontSize = 8.sp)
                    )
                    val py = (cy - pl.size.height / 2f).coerceIn(minTagY, max(minTagY, mainH - pl.size.height))
                    // 成本标签同样放左侧
                    val px = 2f
                    drawRoundRect(GoldYellow, Offset(px, py - 1f), Size(pl.size.width + 8f, pl.size.height + 2f), CornerRadius(3f))
                    drawText(pl, topLeft = Offset(px + 4f, py))
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
            drawLegend(tm, 4f, 2f, legendItems, maxX = mainPlotW)

            // 加载更早历史提示(左上角, 图例下一行, 避免重叠)
            if (loadingMore) {
                val tip = tm.measure(
                    AnnotatedString("加载更早K线..."),
                    TextStyle(color = TextGray, fontSize = 12.sp)
                )
                drawText(tip, topLeft = Offset(4f, 2f + 13.sp.toPx()))
            }

            // ---------------- 副图(与主图同宽, 开启筹码时随主图收窄) ----------------
            for ((k2, sub) in subs.withIndex()) {
                drawSubChart(
                    type = sub, top = mainH + k2 * subH, height = subH, plotW = mainPlotW,
                    iMin = iMinMain, iMax = iMaxMain, xm = xmMain, slot = slot,
                    klines = klines, data = data, formulaSeries = formulaSeries,
                    tm = tm, valueIdx = valueIdx, isMinute = isMinute,
                    minuteBase = prevClose ?: klines.first().open,
                    upColor = upColor, downColor = downColor
                )
            }

            // ---------------- x轴标签(与主图同宽) ----------------
            val labelCount = max(2, (mainPlotW / 90.dp.toPx()).toInt() + 1)
            val axisTop = h - axisH
            drawLine(GridColor, Offset(0f, axisTop), Offset(mainPlotW, axisTop), 1f)
            if (iMaxMain > iMinMain) {
                for (j in 0 until labelCount) {
                    val i = iMinMain + (iMaxMain - iMinMain) * j / (labelCount - 1)
                    val text = if (hideDates && !isMinute) "编号$i" else klines[i].label
                    val layout = tm.measure(AnnotatedString(text), axisStyle)
                    val tx = (xmMain.x(i) - layout.size.width / 2f).coerceIn(0f, max(0f, mainPlotW - layout.size.width))
                    drawText(layout, topLeft = Offset(tx, axisTop + 2f))
                }
            }

            // ---------------- 交易标记 B/S ----------------
            drawMarkers(markers, klines, iMinMain, iMaxMain, xmMain, yMain, mainH, isMinute, tm)

            // ---------------- 筹码栏(右侧整列: 上半分布图 + 下半统计) ----------------
            // 放在副图/标记之后绘制: 不透明背景可盖住平移中溢出主图区右缘的半根bar与标记
            if (chipsOn) {
                drawChipArea(chipData, klines, mainPlotW, w, mainH, h, yMain, priceOfY, tm, chipBg, chipFg, dashEffect)
            }

            // ---------------- 十字线 ----------------
            if (crossPos != null && crossIdx != null) {
                drawCrosshair(
                    pos = crossPos, ci = crossIdx, klines = klines,
                    xPx = xmMain.x(crossIdx), mainPlotW = mainPlotW,
                    priceOfY = priceOfY, mainH = mainH, h = h, axisH = axisH,
                    hideDates = hideDates, isMinute = isMinute, prevClose = prevClose,
                    avgPrice = data.avgPrice, tm = tm, upColor = upColor, downColor = downColor
                )
            }
        }
    }
}

// ---------------- 蜡烛/折线/竹节主图 ----------------
private fun DrawScope.drawCandles(
    klines: List<Kline>, iMin: Int, iMax: Int, xm: XMap,
    yOf: (Double) -> Float, slot: Float, mainH: Float,
    candleStyle: CandleStyle, upColor: Color, downColor: Color,
    limitColors: Boolean, limitPct: Double?, prevClose: Double?
) {
    if (klines.isEmpty() || iMax < iMin) return

    // 涨跌停染色: 相对前一根收盘涨幅>=limitPct染黄, 跌幅<=-limitPct染蓝
    fun colorOf(i: Int): Color {
        val k = klines[i]
        if (limitColors && limitPct != null && limitPct > 0) {
            val base = if (i > 0) klines[i - 1].close else (prevClose ?: k.open)
            if (base > 0) {
                val chg = (k.close - base) / base
                if (chg >= limitPct - 1e-9) return GoldYellow
                if (chg <= -limitPct + 1e-9) return LimitDownBlue
            }
        }
        return if (k.isUp) upColor else downColor
    }

    // 折线图: 只画收盘价折线+浅色面积填充
    if (candleStyle == CandleStyle.LINE) {
        val path = Path()
        val fill = Path()
        var started = false
        var firstX = 0f
        var lastX = 0f
        for (i in iMin..iMax) {
            val x = xm.x(i)
            val y = yOf(klines[i].close)
            if (!started) {
                path.moveTo(x, y)
                fill.moveTo(x, y)
                firstX = x
                started = true
            } else {
                path.lineTo(x, y)
                fill.lineTo(x, y)
            }
            lastX = x
        }
        if (started) {
            fill.lineTo(lastX, mainH)
            fill.lineTo(firstX, mainH)
            fill.close()
            drawPath(fill, MinuteLineColor.copy(alpha = 0.12f))
            drawPath(path, MinuteLineColor, style = Stroke(width = 2f))
        }
        return
    }

    val bodyW = max(1f, slot * 0.72f)
    val wickW = max(1f, slot * 0.1f)
    for (i in iMin..iMax) {
        val k = klines[i]
        val color = colorOf(i)
        val x = xm.x(i)
        when (candleStyle) {
            // 竹节图(OHLC bar): 竖线 + 左开口横线 + 右收口横线
            CandleStyle.BAR -> {
                val lw = max(1f, slot * 0.12f)
                drawLine(color, Offset(x, yOf(k.high)), Offset(x, yOf(k.low)), lw)
                drawLine(color, Offset(x - bodyW / 2f, yOf(k.open)), Offset(x, yOf(k.open)), lw)
                drawLine(color, Offset(x, yOf(k.close)), Offset(x + bodyW / 2f, yOf(k.close)), lw)
            }
            // 空心阳: 涨=描边空心, 跌=实心
            CandleStyle.HOLLOW -> {
                drawLine(color, Offset(x, yOf(k.high)), Offset(x, yOf(k.low)), wickW)
                val top = yOf(max(k.open, k.close))
                val bot = yOf(min(k.open, k.close))
                val bodyH = max(1.2f, bot - top)
                if (k.isUp && bodyW > 2.5f) {
                    drawRect(
                        color, Offset(x - bodyW / 2f, top), Size(bodyW, bodyH),
                        style = Stroke(width = max(1f, min(2f, slot * 0.12f)))
                    )
                } else {
                    drawRect(color, Offset(x - bodyW / 2f, top), Size(bodyW, bodyH))
                }
            }
            // 实心蜡烛
            else -> {
                drawLine(color, Offset(x, yOf(k.high)), Offset(x, yOf(k.low)), wickW)
                val top = yOf(max(k.open, k.close))
                val bot = yOf(min(k.open, k.close))
                drawRect(color, Offset(x - bodyW / 2f, top), Size(bodyW, max(1.2f, bot - top)))
            }
        }
    }
}

// ---------------- 筹码栏(右侧整列: 上半与主图共享y轴的分布图, 下半统计信息) ----------------
private fun DrawScope.drawChipArea(
    chip: Indicators.ChipDistribution?, klines: List<Kline>,
    chipLeft: Float, right: Float, mainH: Float, bottom: Float,
    yOf: (Double) -> Float, priceOfY: (Float) -> Double,
    tm: TextMeasurer, bg: Color, fg: Color, dashEffect: PathEffect
) {
    val chipW = (right - chipLeft).coerceAtLeast(1f)
    // 整列不透明背景 + 左侧分隔线(贯穿主图与副图, 形成独立右栏)
    drawRect(bg, Offset(chipLeft, 0f), Size(chipW, bottom))
    drawLine(GridColor, Offset(chipLeft, 0f), Offset(chipLeft, bottom), 1f)

    if (chip == null || chip.prices.isEmpty() || chip.weights.isEmpty() || klines.isEmpty()) {
        val empty = tm.measure(AnnotatedString("暂无筹码"), TextStyle(color = TextGray, fontSize = 9.sp))
        drawText(empty, topLeft = Offset(chipLeft + max(0f, (chipW - empty.size.width) / 2f), max(0f, (mainH - empty.size.height) / 2f)))
        return
    }

    val cur = klines.last().close
    val maxWgt = chip.weights.max()
    val count = min(chip.prices.size, chip.weights.size)
    if (maxWgt > 0 && count > 0) {
        val binH = if (count >= 2) max(1f, abs(yOf(chip.prices[1]) - yOf(chip.prices[0])) * 0.8f) else 2f
        for (b in 0 until count) {
            val wgt = chip.weights[b]
            if (wgt <= 0) continue
            val price = chip.prices[b]
            val y = yOf(price)
            if (y < 0f || y > mainH) continue   // 与主图同一y轴, 超出可见价格区间不画
            val len = (wgt / maxWgt * (chipW - 12f)).toFloat().coerceAtLeast(1f)
            // 当前价下方=获利盘红, 上方=套牢盘蓝
            val c = if (price <= cur) UpRed else TrapBlue
            drawRect(c.copy(alpha = 0.88f), Offset(chipLeft + 2f, y - binH / 2f), Size(len, max(1f, binH)))
        }
    }

    // 当前价虚线 / 平均成本虚线
    val curY = yOf(cur)
    if (curY in 0f..mainH) {
        drawLine(fg.copy(alpha = 0.55f), Offset(chipLeft, curY), Offset(right, curY), 1f, pathEffect = dashEffect)
    }
    val costY = yOf(chip.avgCost)
    if (costY in 0f..mainH) {
        drawLine(GoldYellow, Offset(chipLeft, costY), Offset(right, costY), 1f, pathEffect = dashEffect)
    }

    // 右缘价格刻度(与主图网格同行): 最后绘制, 避免被高权重筹码柱/虚线盖住
    val axisStyle = TextStyle(color = TextGray, fontSize = 9.sp)
    var tickH = 0
    for (j in 0..4) {
        val y = mainH * j / 4f
        val label = tm.measure(AnnotatedString(String.format("%.2f", priceOfY(y))), axisStyle)
        if (j == 0) tickH = label.size.height
        val ty = (y + 2f).coerceAtMost(max(0f, mainH - label.size.height - 1f))
        drawText(label, topLeft = Offset(right - label.size.width - 2f, ty))
    }

    // 90%筹码区间与集中度: 按累计权重掐头去尾各5%
    var low90 = chip.prices.first()
    var high90 = chip.prices.last()
    var cum = 0.0
    var lowSet = false
    for (b in 0 until count) {
        cum += chip.weights[b]
        if (!lowSet && cum >= 0.05) { low90 = chip.prices[b]; lowSet = true }
        if (cum >= 0.95) { high90 = chip.prices[b]; break }
    }
    val concentration = if (high90 + low90 > 0) (high90 - low90) / (high90 + low90) * 100 else 0.0

    // 下半统计栏(副图高度区域): 标签靠左灰字, 数值靠右着色
    val labelStyle = TextStyle(color = TextGray, fontSize = 9.sp)
    val rows = listOf(
        Triple("收盘获利", String.format("%.2f%%", chip.profitRatio * 100), UpRed),
        Triple("平均成本", String.format("%.2f", chip.avgCost), GoldYellow),
        Triple("90%成本", String.format("%.2f-%.2f", low90, high90), fg),
        Triple("集中度", String.format("%.2f%%", concentration), fg)
    )
    val rowH = tm.measure(AnnotatedString("获"), labelStyle).size.height + 6f
    val statsTop = mainH + 5f
    // 至少能放下两行才画统计栏, 否则(无副图等)退回顶部小字, 避免单行挤进x轴带
    if (bottom - statsTop >= rowH * 2) {
        drawLine(GridColor, Offset(chipLeft, mainH), Offset(right, mainH), 1f)
        var y = statsTop
        for ((label, value, vc) in rows) {
            if (y + rowH > bottom) break
            val ll = tm.measure(AnnotatedString(label), labelStyle)
            val vl = tm.measure(AnnotatedString(value), TextStyle(color = vc, fontSize = 9.sp))
            val vx = max(chipLeft + 6f, right - vl.size.width - 6f)
            // 数值优先右对齐; 标签放不下(会与数值重叠)时只画数值
            if (chipLeft + 6f + ll.size.width + 8f <= vx) {
                drawText(ll, topLeft = Offset(chipLeft + 6f, y))
            }
            drawText(vl, topLeft = Offset(vx, y))
            y += rowH
        }
    } else {
        // 高度不足场景: 退回分布图顶部两行小字, 竖排且下移避开右缘第一行价格刻度
        val fy = 2f + tickH + 2f
        val t1 = tm.measure(
            AnnotatedString("获利" + String.format("%.1f%%", chip.profitRatio * 100)),
            TextStyle(color = UpRed, fontSize = 8.sp)
        )
        val t2 = tm.measure(
            AnnotatedString("成本" + String.format("%.2f", chip.avgCost)),
            TextStyle(color = GoldYellow, fontSize = 8.sp)
        )
        drawText(t1, topLeft = Offset(chipLeft + 4f, fy))
        drawText(t2, topLeft = Offset(chipLeft + 4f, fy + t1.size.height + 2f))
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
    type: SubIndicator, top: Float, height: Float, plotW: Float,
    iMin: Int, iMax: Int, xm: XMap, slot: Float,
    klines: List<Kline>, data: ChartData, formulaSeries: Map<String, List<Double?>>,
    tm: TextMeasurer, valueIdx: Int, isMinute: Boolean, minuteBase: Double,
    upColor: Color, downColor: Color
) {
    drawLine(GridColor, Offset(0f, top), Offset(plotW, top), 1f)
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
                val color = if (up) upColor else downColor
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
            drawLegend(tm, 4f, top + 2f, items, maxX = plotW)
        }

        SubIndicator.MACD -> {
            val m = data.macd ?: return
            val r = visRange(iMin, iMax, listOf(m.dif, m.dea, m.hist)) ?: return
            val lo = min(r.first, 0.0)
            val hi = max(r.second, 0.0)
            val yOf = yMapper(lo, hi)
            val zeroY = yOf(0.0)
            drawLine(GridColor, Offset(0f, zeroY), Offset(plotW, zeroY), 1f)
            val histW = max(1f, slot * 0.4f)
            for (i in iMin..min(iMax, m.hist.size - 1)) {
                val v = m.hist[i] ?: continue
                val y = yOf(v)
                val color = if (v >= 0) upColor else downColor
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
                ), maxX = plotW
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
                ), maxX = plotW
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
            drawLegend(tm, 4f, top + 2f, items, maxX = plotW)
        }

        SubIndicator.ATR -> {
            val s = data.atr ?: return
            val r = visRange(iMin, iMax, listOf(s)) ?: return
            val yOf = yMapper(r.first, r.second)
            drawSeriesLine(s, iMin, iMax, xm, yOf, GoldYellow, 1.3f)
            drawLegend(tm, 4f, top + 2f, listOf("ATR:" + fmtPrice(s.getOrNull(valueIdx)) to GoldYellow), maxX = plotW)
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
                drawLine(GridColor, Offset(0f, y), Offset(plotW, y), 1f, pathEffect = dash)
            }
            drawSeriesLine(s, iMin, iMax, xm, yOf, GoldYellow, 1.3f)
            drawLegend(tm, 4f, top + 2f, listOf("CCI:" + fmtPrice(s.getOrNull(valueIdx)) to GoldYellow), maxX = plotW)
        }

        SubIndicator.FORMULA -> {
            if (formulaSeries.isEmpty()) {
                drawLegend(tm, 4f, top + 2f, listOf("自定义公式: 无数据" to TextGray), maxX = plotW)
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
            drawLegend(tm, 4f, top + 2f, items, maxX = plotW)
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
    pos: Offset, ci: Int, klines: List<Kline>,
    xPx: Float, mainPlotW: Float,
    priceOfY: (Float) -> Double, mainH: Float, h: Float, axisH: Float,
    hideDates: Boolean, isMinute: Boolean, prevClose: Double?,
    avgPrice: List<Double?>, tm: TextMeasurer, upColor: Color, downColor: Color
) {
    if (ci < 0 || ci >= klines.size) return
    val chartBottom = h - axisH
    // 竖线贯穿主图与副图(两者共用同一x映射, 开启筹码时都在收窄后的左栏内)
    if (xPx in 0f..mainPlotW) {
        drawLine(CrossColor, Offset(xPx, 0f), Offset(xPx, chartBottom), 1.5f)
    }
    // 横线(仅主图区域) + 价格标签
    if (pos.y in 0f..mainH) {
        drawLine(CrossColor, Offset(0f, pos.y), Offset(mainPlotW, pos.y), 1.5f)
        val price = priceOfY(pos.y)
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
    val tx = (xPx - xl.size.width / 2f).coerceIn(0f, max(0f, mainPlotW - xl.size.width - 8f))
    drawRoundRect(PanelBg, Offset(tx - 4f, chartBottom), Size(xl.size.width + 8f, axisH), CornerRadius(3f))
    drawText(xl, topLeft = Offset(tx, chartBottom + max(0f, (axisH - xl.size.height) / 2f)))

    // 信息浮层
    val lines = ArrayList<Pair<String, Color>>()
    if (isMinute) {
        val base = prevClose ?: klines.first().open
        val chg = if (base > 0) (k.close - base) / base * 100 else 0.0
        val cc = if (chg >= 0) upColor else downColor
        lines.add("时间 " + k.label to Color.White)
        lines.add("价格 " + String.format("%.2f", k.close) to cc)
        lines.add("均价 " + fmtPrice(avgPrice.getOrNull(ci)) to GoldYellow)
        lines.add("涨跌 " + String.format("%.2f%%", chg) to cc)
        lines.add("成交量 " + fmtVol(k.volume) to Color.White)
    } else {
        val pc = if (ci > 0) klines[ci - 1].close else k.open
        fun colorOf(v: Double) = if (pc > 0 && v >= pc) upColor else downColor
        val chg = if (pc > 0) (k.close - pc) / pc * 100 else 0.0
        lines.add((if (hideDates) "编号$ci" else k.label) to Color.White)
        lines.add("开盘 " + String.format("%.2f", k.open) to colorOf(k.open))
        lines.add("最高 " + String.format("%.2f", k.high) to colorOf(k.high))
        lines.add("最低 " + String.format("%.2f", k.low) to colorOf(k.low))
        lines.add("收盘 " + String.format("%.2f", k.close) to colorOf(k.close))
        lines.add("涨跌幅 " + String.format("%.2f%%", chg) to (if (chg >= 0) upColor else downColor))
        lines.add("成交量 " + fmtVol(k.volume) to Color.White)
    }
    val style = TextStyle(fontSize = 9.sp)
    val layouts = lines.map { (s, c) -> tm.measure(AnnotatedString(s), style.copy(color = c)) }
    val lineH = (layouts.maxOfOrNull { it.size.height } ?: 12).toFloat() + 3f
    val panelW = (layouts.maxOfOrNull { it.size.width } ?: 60).toFloat() + 16f
    val panelH = lineH * layouts.size + 10f
    val px = if (pos.x < mainPlotW / 2f) max(0f, mainPlotW - panelW - 8f) else 8f
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

private fun DrawScope.drawLegend(
    tm: TextMeasurer, x0: Float, y: Float,
    items: List<Pair<String, Color>>, maxX: Float = -1f
) {
    val limit = if (maxX > 0f) maxX else size.width
    var x = x0
    for ((text, color) in items) {
        val layout = tm.measure(AnnotatedString(text), TextStyle(color = color, fontSize = 9.sp))
        if (x + layout.size.width > limit) break
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
