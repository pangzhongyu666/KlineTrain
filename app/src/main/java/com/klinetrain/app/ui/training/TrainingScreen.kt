package com.klinetrain.app.ui.training

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.klinetrain.app.KlineTrainApp
import com.klinetrain.app.data.model.ChartStyle
import com.klinetrain.app.data.model.Direction
import com.klinetrain.app.data.model.SubIndicator
import com.klinetrain.app.data.model.TimeFrame
import com.klinetrain.app.data.model.TrainingMode
import com.klinetrain.app.ui.chart.KlineChartPanel
import com.klinetrain.app.ui.theme.DownGreen
import com.klinetrain.app.ui.theme.GoldYellow
import com.klinetrain.app.ui.theme.Purple
import com.klinetrain.app.ui.theme.PurpleDark
import com.klinetrain.app.ui.theme.UpRed
import kotlinx.coroutines.delay
import kotlin.math.abs

private val SellBlue = Color(0xFF2F6FED)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(mode: TrainingMode, onExit: () -> Unit) {
    val vm: TrainingViewModel = viewModel(
        key = "training_${mode.name}",
        factory = TrainingViewModel.factory(mode)
    )
    val state by vm.uiState.collectAsState()
    val saved by vm.saved.collectAsState()
    val context = LocalContext.current
    val settings = remember { KlineTrainApp.instance.settings }

    // 图表外观(K线设置), 设置sheet内即时修改
    var chartStyle by remember { mutableStateOf(settings.chartStyle) }

    // 横竖屏: 进入按设置初始化, 训练中可用旋转按钮切换, 退出恢复系统方向
    val activity = context as? Activity
    var forceLandscape by rememberSaveable { mutableStateOf(settings.landscapeMode) }
    LaunchedEffect(forceLandscape) {
        activity?.requestedOrientation = if (forceLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
    DisposableEffect(Unit) {
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    LaunchedEffect(Unit) {
        vm.toasts.collect { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
    }
    // 交易成功震动反馈(VM 内已按 vibrateMode 过滤)
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        vm.haptics.collect { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
    LaunchedEffect(saved) { if (saved) onExit() }
    BackHandler { vm.requestExit() }

    // 用时计秒
    var elapsedSec by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.startTimeMs) {
        if (state.startTimeMs > 0) {
            while (true) {
                elapsedSec = (System.currentTimeMillis() - state.startTimeMs) / 1000
                delay(1000)
            }
        }
    }

    var showSettings by remember { mutableStateOf(false) }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // 图表用movableContent包一层: 横竖屏两套布局间切换时保留内部平移/缩放/十字线状态
    val chartContent = remember {
        movableContentOf { modifier: Modifier ->
            ChartArea(state = state, vm = vm, chartStyle = chartStyle, modifier = modifier)
        }
    }

    if (isLandscape) {
        LandscapeLayout(
            state = state,
            vm = vm,
            chartContent = chartContent,
            onBack = { vm.requestExit() },
            onToggleOrientation = { forceLandscape = !forceLandscape },
            onOpenSettings = { showSettings = true }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            TrainingTopBar(
                state = state,
                elapsedSec = elapsedSec,
                onBack = { vm.requestExit() }
            )

            when {
                state.loading -> LoadingBox()
                state.error != null -> ErrorBox(state.error!!) { vm.retry() }
                else -> {
                    QuoteBar(state)
                    TimeframeTabs(
                        current = state.timeframe,
                        onSelect = { vm.setTimeframe(it) },
                        onToggleOrientation = { forceLandscape = !forceLandscape },
                        onOpenSettings = { showSettings = true }
                    )
                    chartContent(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                    AutoPlayRow(state, vm)
                    ActionButtons(state, vm)
                    StatsBar(state)
                }
            }
        }
    }

    if (state.showExitConfirm) {
        ExitConfirmDialog(
            onSettle = { vm.confirmExit() },
            onQuit = { vm.quitWithoutSaving() },
            onDismiss = { vm.dismissExitConfirm() }
        )
    }
    if (showSettings) {
        TrainingSettingsSheet(
            state = state,
            vm = vm,
            chartStyle = chartStyle,
            onChartStyleChange = {
                chartStyle = it
                settings.chartStyle = it
            },
            onDismiss = { showSettings = false }
        )
    }
    state.result?.let { result ->
        SessionResultDialog(
            state = state,
            result = result,
            onExitSave = { strategyId, note -> vm.saveResult(strategyId, note) },
            onNext = { strategyId, note -> vm.saveAndNext(strategyId, note) }
        )
    }
}

// ---------------- 加载/错误 ----------------

@Composable
private fun LoadingBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Purple)
            Spacer(Modifier.height(12.dp))
            Text("正在加载历史行情...", fontSize = 14.sp)
        }
    }
}

@Composable
private fun ErrorBox(error: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("加载失败: $error", fontSize = 14.sp, color = UpRed)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry) { Text("重试") }
        }
    }
}

// ---------------- 图表区(横竖屏共用) ----------------

@Composable
private fun ChartArea(
    state: TrainingUiState,
    vm: TrainingViewModel,
    chartStyle: ChartStyle,
    modifier: Modifier = Modifier
) {
    val subsForPanel =
        if (state.formulaSeries.isNotEmpty() && !state.formulaOnMain)
            state.subIndicators + SubIndicator.FORMULA
        else state.subIndicators
    KlineChartPanel(
        klines = state.displayKlines,
        timeframe = state.timeframe,
        mainOverlay = state.mainOverlay,
        subIndicators = subsForPanel,
        formulaSeries = state.formulaSeries,
        formulaOnMain = state.formulaOnMain,
        markers = if (state.timeframe == TimeFrame.DAY) state.displayMarkers else emptyList(),
        hideDates = state.isBlind,
        showChips = state.showChips,
        prevClose = state.prevCloseForPanel,
        chartStyle = chartStyle,
        costPrice = state.costPrice,
        // 涨跌停染色只对日K有意义(周/月/分钟线不适用日线阈值);
        // 阈值按板块区分(主板9.5%/创科19.5%/ST 4.5%), 指数/加密为null不染色
        limitPct = if (state.timeframe == TimeFrame.DAY) state.limitThreshold else null,
        // 左滑到最左端加载更早历史(组件内去抖): 日/周/月拉更早日K,
        // 分钟周期先扩大拼接天数再按需拉取; 分时/五日不触发
        onLoadMoreHistory = { vm.loadMoreHistory() },
        loadingMore = state.loadingMore,
        modifier = modifier
    )
}

// ---------------- 横屏布局 ----------------

/**
 * 横屏训练布局: 顶部单行(返回+最新价+周期+旋转+设置), 主体为图表+右侧操作列,
 * 让图表获得最大高度完整显示。
 */
@Composable
private fun LandscapeLayout(
    state: TrainingUiState,
    vm: TrainingViewModel,
    chartContent: @Composable (Modifier) -> Unit,
    onBack: () -> Unit,
    onToggleOrientation: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // safeDrawing含刘海/挖孔区: 横屏时挖孔多在左缘, 避免遮住返回键与左侧价格标签
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            val bar = state.effectiveBar
            if (bar != null) {
                val prevClose = state.prevDayClose
                val change = bar.close - prevClose
                val pct = if (prevClose > 0) change / prevClose * 100 else 0.0
                val color = if (change >= 0) UpRed else DownGreen
                Text(fmtQuote(bar.close), color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text(
                    "${if (change >= 0) "+" else ""}${String.format("%.2f", pct)}%",
                    color = color,
                    fontSize = 11.sp
                )
            }
            TimeframeChipRow(
                current = state.timeframe,
                onSelect = { vm.setTimeframe(it) },
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onToggleOrientation) {
                Icon(
                    Icons.Filled.ScreenRotation,
                    contentDescription = "横竖屏切换",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "设置",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        when {
            state.loading -> LoadingBox()
            state.error != null -> ErrorBox(state.error!!) { vm.retry() }
            else -> {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    chartContent(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    LandscapeSidePanel(
                        state = state,
                        vm = vm,
                        modifier = Modifier
                            .width(104.dp)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

/** 横屏右侧操作列: 买/卖/观望 + 自动播放 + 关键数据(可滚动, 防小屏截断) */
@Composable
private fun LandscapeSidePanel(
    state: TrainingUiState,
    vm: TrainingViewModel,
    modifier: Modifier = Modifier
) {
    val enabled = !state.finished && state.result == null
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BigActionButton("买入", buySubtitle(state), UpRed, enabled, Modifier.fillMaxWidth()) { vm.onBuy() }
        BigActionButton("卖出", sellSubtitle(state), SellBlue, enabled, Modifier.fillMaxWidth()) { vm.onSell() }
        BigActionButton("观望", "下一根", Purple, enabled, Modifier.fillMaxWidth()) { vm.advance() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("自动", fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            Switch(
                checked = state.autoPlay,
                onCheckedChange = { vm.setAutoPlay(it) },
                modifier = Modifier.height(24.dp)
            )
        }
        SideStat("仓位", String.format("%.0f%%", state.positionPct), null)
        SideStat(
            "本局收益",
            "${if (state.sessionReturnPct >= 0) "+" else ""}${String.format("%.2f", state.sessionReturnPct)}%",
            if (state.sessionReturnPct >= 0) UpRed else DownGreen
        )
        SideStat("金钱", String.format("%.0f", state.firecrackers), GoldYellow)
    }
}

@Composable
private fun SideStat(label: String, value: String, valueColor: Color?) {
    Column {
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        Text(
            value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

// ---------------- 顶栏 ----------------

@Composable
private fun TrainingTopBar(state: TrainingUiState, elapsedSec: Long, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(PurpleDark)
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text("K线训练进行中", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                // 双盲隐藏标的; 币圈/指数/ETF/涨停显示真名
                val name = if (state.isBlind) "神秘股票" else state.stockName.ifEmpty { "--" }
                Text(name, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("用时: ${elapsedSec}秒", color = Color.White, fontSize = 12.sp)
                Text("剩余${state.remainingBars}根日线", color = GoldYellow, fontSize = 12.sp)
            }
        }
    }
}

// ---------------- 行情条 ----------------

/** 大数字自适应: 万元级取整、千元级一位小数、1元以下按量级放大精度(小币价), 其余两位(A股不变) */
private fun fmtQuote(v: Double): String {
    val a = abs(v)
    return when {
        a >= 10000 -> String.format("%.0f", v)
        a >= 1000 -> String.format("%.1f", v)
        a >= 1 -> String.format("%.2f", v)
        a >= 0.1 -> String.format("%.3f", v)
        a >= 0.0001 -> String.format("%.5f", v)
        a > 0 -> String.format("%.8f", v)
        else -> String.format("%.2f", v)
    }
}

@Composable
private fun QuoteBar(state: TrainingUiState) {
    // 分钟周期部分揭示时为已揭示区间聚合bar, 避免泄露当日收盘
    val bar = state.effectiveBar ?: return
    val prevClose = state.prevDayClose
    val change = bar.close - prevClose
    val changePct = if (prevClose > 0) change / prevClose * 100 else 0.0
    val color = if (change >= 0) UpRed else DownGreen
    val sign = if (change >= 0) "+" else ""
    val closeText = fmtQuote(bar.close)

    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    closeText,
                    color = color,
                    fontSize = if (closeText.length >= 7) 21.sp else 26.sp,
                    fontWeight = FontWeight.Bold
                )
                if (state.leverage > 1.0) {
                    Spacer(Modifier.width(6.dp))
                    Surface(color = GoldYellow, shape = RoundedCornerShape(4.dp)) {
                        Text(
                            "杠杆x${String.format("%.0f", state.leverage)}",
                            color = Color.White,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Text(
                "$sign${fmtQuote(change)}  $sign${String.format("%.2f", changePct)}%",
                color = color,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                QuoteItem("开", fmtQuote(bar.open), if (bar.open >= prevClose) UpRed else DownGreen)
                QuoteItem("高", fmtQuote(bar.high), if (bar.high >= prevClose) UpRed else DownGreen)
                QuoteItem("低", fmtQuote(bar.low), if (bar.low >= prevClose) UpRed else DownGreen)
            }
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                QuoteItem("收", fmtQuote(bar.close), color)
                QuoteItem("量", String.format("%.2f万手", bar.volume / 1_000_000.0), null)
                QuoteItem(
                    "换手",
                    state.turnoverPct?.let { String.format("%.2f%%", it) } ?: "--",
                    null
                )
            }
        }
    }
}

@Composable
private fun QuoteItem(label: String, value: String, color: Color?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        Spacer(Modifier.width(3.dp))
        Text(
            value,
            fontSize = 11.sp,
            color = color ?: MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

// ---------------- 周期 Tab ----------------

@Composable
private fun TimeframeChipRow(
    current: TimeFrame,
    onSelect: (TimeFrame) -> Unit,
    modifier: Modifier = Modifier
) {
    var moreExpanded by remember { mutableStateOf(false) }
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimeFrame.primaryTabs.forEach { tf ->
            val selected = tf == current
            Text(
                tf.label,
                color = if (selected) Purple else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clickable { onSelect(tf) }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
        // "更多": 1/5/15/30/60分钟周期收进下拉; 选中分钟周期时直接显示其标签
        val minuteSelected = current in TimeFrame.minuteSteps
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { moreExpanded = true }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    if (minuteSelected) current.label else "更多",
                    color = if (minuteSelected) Purple else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    fontWeight = if (minuteSelected) FontWeight.Bold else FontWeight.Normal
                )
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = "更多周期",
                    tint = if (minuteSelected) Purple else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
            DropdownMenu(expanded = moreExpanded, onDismissRequest = { moreExpanded = false }) {
                TimeFrame.minuteSteps.forEach { tf ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                tf.label,
                                color = if (tf == current) Purple else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (tf == current) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            moreExpanded = false
                            onSelect(tf)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeframeTabs(
    current: TimeFrame,
    onSelect: (TimeFrame) -> Unit,
    onToggleOrientation: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimeframeChipRow(current = current, onSelect = onSelect, modifier = Modifier.weight(1f))
        IconButton(onClick = onToggleOrientation) {
            Icon(
                Icons.Filled.ScreenRotation,
                contentDescription = "横竖屏切换",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = "设置",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

// ---------------- 自动播放 ----------------

@Composable
private fun AutoPlayRow(state: TrainingUiState, vm: TrainingViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("自动播放", fontSize = 12.sp)
        Spacer(Modifier.width(6.dp))
        Switch(
            checked = state.autoPlay,
            onCheckedChange = { vm.setAutoPlay(it) },
            modifier = Modifier.height(32.dp)
        )
        Spacer(Modifier.weight(1f))
        PlaySpeed.entries.forEach { sp ->
            FilterChip(
                selected = state.speed == sp,
                onClick = { vm.setSpeed(sp) },
                label = { Text(sp.label, fontSize = 11.sp) },
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

// ---------------- 操作按钮 ----------------

private fun buySubtitle(state: TrainingUiState): String {
    val total = state.totalSlots.coerceAtLeast(1)
    // 未开分仓: 一次操作即全仓, 显示可用仓数没有意义
    if (state.slotsPerTrade >= total) return "全仓"
    // 持空单时买入=平空, 与其余状态统一用"已用/总仓"格式
    return if (state.direction == Direction.SHORT) "平空${state.usedSlots}/${total}仓"
    else "可买${state.freeSlots}/${total}仓"
}

private fun sellSubtitle(state: TrainingUiState): String {
    val total = state.totalSlots.coerceAtLeast(1)
    if (state.direction != Direction.LONG && !state.allowShort) return "未开启做空"
    if (state.slotsPerTrade >= total) return "全仓"
    // T+1下今日买入不可卖, 显示实际可卖仓数
    return if (state.direction == Direction.LONG) "可卖${state.sellableSlots}/${total}仓"
    else "可卖${state.freeSlots}/${total}仓"
}

@Composable
private fun ActionButtons(state: TrainingUiState, vm: TrainingViewModel) {
    val enabled = !state.finished && state.result == null
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BigActionButton("买入", buySubtitle(state), UpRed, enabled, Modifier.weight(1f)) { vm.onBuy() }
        BigActionButton("卖出", sellSubtitle(state), SellBlue, enabled, Modifier.weight(1f)) { vm.onSell() }
        BigActionButton("观望", "下一根", Purple, enabled, Modifier.weight(1f)) { vm.advance() }
    }
}

@Composable
private fun BigActionButton(
    title: String,
    subtitle: String,
    color: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = modifier
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 10.sp, color = Color.White.copy(alpha = 0.85f))
        }
    }
}

// ---------------- 底部统计条 ----------------

@Composable
private fun StatsBar(state: TrainingUiState) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(PurpleDark)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem("金钱", String.format("%.0f", state.firecrackers), GoldYellow)
        StatItem("仓位", String.format("%.0f%%", state.positionPct), Color.White)
        StatItem("最大回撤", String.format("%.2f%%", state.maxDrawdownPct), Color.White)
        StatItem("盈亏比", if (state.plRatio >= 999.0) "∞" else String.format("%.2f", state.plRatio), Color.White)
        StatItem(
            "本局收益",
            "${if (state.sessionReturnPct >= 0) "+" else ""}${String.format("%.2f", state.sessionReturnPct)}%",
            if (state.sessionReturnPct >= 0) UpRed else DownGreen
        )
        StatItem(
            "开仓收益",
            "${if (state.floatingPnl >= 0) "+" else ""}${String.format("%.2f", state.floatingPnl)}",
            if (state.floatingPnl >= 0) UpRed else DownGreen
        )
    }
}

@Composable
private fun StatItem(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
        Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
