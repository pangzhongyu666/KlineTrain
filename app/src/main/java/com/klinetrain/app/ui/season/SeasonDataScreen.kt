package com.klinetrain.app.ui.season

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.klinetrain.app.ui.home.TrainingRecordCard
import com.klinetrain.app.ui.theme.PurpleDark
import com.klinetrain.app.ui.theme.UpRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

private val ExportBlue = Color(0xFF3B6FE8)

/** 当前赛季详细数据页：爆竹曲线 + 训练数据汇总 + 交易历史 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonDataScreen(onBack: () -> Unit, onOpenRecord: (Long) -> Unit) {
    val vm: SeasonViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.nickname, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("当前赛季训练记录", fontSize = 11.sp, color = Color.Gray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "第${state.selectedSeason}赛季训练记录")
                            putExtra(Intent.EXTRA_TEXT, vm.buildCsv(state.recordsAsc))
                        }
                        runCatching { context.startActivity(Intent.createChooser(send, "数据导出")) }
                    }) {
                        Text("数据导出", color = ExportBlue, fontSize = 14.sp)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            item { SeasonTabRow(state, onSelect = vm::selectSeason) }
            item { SectionTitle("爆竹数量曲线") }
            item { CurveCard(state) }
            item { StatsSection(state) }
            item { SectionTitle("交易历史") }
            if (state.recordsAsc.isEmpty()) {
                item {
                    Text(
                        "本赛季暂无训练记录",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
                    )
                }
            } else {
                items(state.recordsAsc.asReversed(), key = { it.id }) { record ->
                    TrainingRecordCard(
                        record = record,
                        onClick = { onOpenRecord(record.id) }
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** 赛季 Tab：当前赛季"N.进行中"，历史赛季"N.破产" */
@Composable
private fun SeasonTabRow(state: SeasonUiState, onSelect: (Int) -> Unit) {
    val seasons = state.seasons
    if (seasons.isEmpty()) return
    val selIndex = seasons.indexOf(state.selectedSeason).coerceAtLeast(0)
    ScrollableTabRow(
        selectedTabIndex = selIndex,
        containerColor = MaterialTheme.colorScheme.surface,
        edgePadding = 16.dp
    ) {
        seasons.forEachIndexed { i, s ->
            Tab(
                selected = i == selIndex,
                onClick = { onSelect(s) },
                selectedContentColor = ExportBlue,
                unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                text = {
                    Text(
                        if (s == state.currentSeason) "$s.进行中" else "$s.破产",
                        fontSize = 15.sp,
                        fontWeight = if (i == selIndex) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

/** 爆竹数量曲线卡片：图例 + Canvas 折线 + 中央水印 */
@Composable
private fun CurveCard(state: SeasonUiState) {
    val latest = state.curve.lastOrNull() ?: state.firecrackers
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA) }
    val startLabel = state.recordsAsc.firstOrNull()?.let { sdf.format(Date(it.createdAt)) }
    val endLabel = if (state.recordsAsc.size > 1) {
        sdf.format(Date(state.recordsAsc.last().createdAt))
    } else null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(UpRed, CircleShape)
                )
                Spacer(Modifier.width(6.dp))
                Text("爆竹数量 ", fontSize = 13.sp)
                Text(thousands(latest), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = UpRed)
                Spacer(Modifier.weight(1f))
                Text("共${state.stats.sessions}场训练", fontSize = 12.sp, color = Color.Gray)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                FirecrackerCurve(
                    values = state.curve,
                    startLabel = startLabel,
                    endLabel = endLabel,
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    "K线训练助手",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray.copy(alpha = 0.18f),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

/** 红色爆竹折线：y轴左侧5档刻度，x轴下方首末时间；单点画水平线 */
@Composable
private fun FirecrackerCurve(
    values: List<Double>,
    startLabel: String?,
    endLabel: String?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas
        val labelPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 9.sp.toPx()
            color = 0xFF9099A8.toInt()
        }
        var maxV = values.max()
        var minV = values.min()
        if (maxV - minV < 1e-9) {
            val pad = max(abs(maxV) * 0.002, 1.0)
            maxV += pad
            minV -= pad
        } else {
            val pad = (maxV - minV) * 0.08
            maxV += pad
            minV -= pad
        }
        val range = (maxV - minV).let { if (it <= 0.0) 1.0 else it }
        val decimals = if (range < 20) 1 else 0
        val ticks = (0..4).map { maxV - range * it / 4.0 }
        val tickLabels = ticks.map { String.format(Locale.CHINA, "%,.${decimals}f", it) }
        val leftPad = (tickLabels.maxOfOrNull { labelPaint.measureText(it) } ?: 0f) + 6.dp.toPx()
        val hasXLabels = startLabel != null || endLabel != null
        val bottomPad = if (hasXLabels) 16.dp.toPx() else 4.dp.toPx()
        val topPad = 4.dp.toPx()
        val chartW = size.width - leftPad - 6.dp.toPx()
        val chartH = size.height - topPad - bottomPad
        if (chartW <= 0f || chartH <= 0f) return@Canvas
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
        val native = drawContext.canvas.nativeCanvas

        // 网格线 + y轴刻度
        ticks.forEachIndexed { i, _ ->
            val y = topPad + chartH * i / 4f
            drawLine(
                color = Color(0xFFE4E6EE),
                start = Offset(leftPad, y),
                end = Offset(leftPad + chartW, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dash
            )
            labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
            native.drawText(tickLabels[i], leftPad - 3.dp.toPx(), y + labelPaint.textSize * 0.35f, labelPaint)
        }

        fun yOf(v: Double): Float = topPad + chartH * (1f - ((v - minV) / range).toFloat())

        // 折线(单点则水平线)
        val lastX = leftPad + chartW
        val lastY = yOf(values.last())
        if (values.size == 1) {
            drawLine(UpRed, Offset(leftPad, lastY), Offset(lastX, lastY), 2.dp.toPx())
        } else {
            val path = Path()
            values.forEachIndexed { i, v ->
                val x = leftPad + chartW * i / (values.size - 1).toFloat()
                val y = yOf(v)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = UpRed,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        // 终点空心圆标记
        drawCircle(Color.White, 4.dp.toPx(), Offset(lastX, lastY))
        drawCircle(UpRed, 4.dp.toPx(), Offset(lastX, lastY), style = Stroke(width = 2.dp.toPx()))

        // x轴首末时间
        if (hasXLabels) {
            val baseline = size.height - 3.dp.toPx()
            startLabel?.let {
                labelPaint.textAlign = android.graphics.Paint.Align.LEFT
                native.drawText(it, leftPad, baseline, labelPaint)
            }
            endLabel?.let {
                labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
                native.drawText(it, leftPad + chartW, baseline, labelPaint)
            }
        }
    }
}

/** 训练数据标题行 + 深蓝汇总卡 */
@Composable
private fun StatsSection(state: SeasonUiState) {
    val stats = state.stats
    val ongoing = state.selectedSeason == state.currentSeason
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("训练数据", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("训练结果: ", fontSize = 14.sp, color = Color.Gray)
            Text(
                if (ongoing) "进行中" else "破产",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (ongoing) MaterialTheme.colorScheme.onBackground else UpRed
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = PurpleDark)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatRow("训练场次", "${stats.sessions}", "训练胜率", pctOrDash(stats.winRate))
                StatRow("跑赢区间率", pctOrDash(stats.outperformRate), "训练盈亏比", numOrDash(stats.plRatio))
                StatRow("持仓时间", "${stats.totalHoldBars}天", "持仓率", pctOrDash(stats.avgHoldRatio))
                StatRow("重仓率", pctOrDash(stats.avgHeavyRatio), "训练耗时", formatDurationCn(stats.totalDurationSec))
                StatRow("开仓次数", "${stats.totalOpenCount}", "开仓胜率", pctOrDash(stats.openWinRate))
                StatRow("最大盈利", pctOrDash(stats.maxProfitPct), "最大回撤", pctOrDash(stats.maxDrawdownPct))
            }
        }
    }
}

@Composable
private fun StatRow(label1: String, value1: String, label2: String, value2: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatCell(label1, value1, Modifier.weight(1f))
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.25f))
        )
        StatCell(label2, value2, Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$label:", fontSize = 13.sp, color = Color.White.copy(alpha = 0.92f), maxLines = 1)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
    }
}

// ---------------- 格式化工具(全部 "--" 兜底，不出现 NaN) ----------------

private fun thousands(v: Double): String =
    if (v.isNaN() || v.isInfinite()) "--" else String.format(Locale.CHINA, "%,.0f", v)

private fun pctOrDash(v: Double?): String =
    if (v == null || v.isNaN() || v.isInfinite()) "--"
    else String.format(Locale.CHINA, "%.2f%%", v)

private fun numOrDash(v: Double?): String =
    if (v == null || v.isNaN() || v.isInfinite()) "--"
    else String.format(Locale.CHINA, "%.2f", v)

/** 秒 -> "X分X秒"，超过1小时 -> "X小时X分" */
private fun formatDurationCn(sec: Long): String {
    val s = sec.coerceAtLeast(0)
    return if (s >= 3600) "${s / 3600}小时${s % 3600 / 60}分" else "${s / 60}分${s % 60}秒"
}
