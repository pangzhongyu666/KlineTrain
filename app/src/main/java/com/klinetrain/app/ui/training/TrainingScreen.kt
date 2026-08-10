package com.klinetrain.app.ui.training

import android.app.Activity
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.klinetrain.app.KlineTrainApp
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

    // 图表外观(K线设置), 设置sheet关闭时刷新
    var chartStyle by remember { mutableStateOf(settings.chartStyle) }

    // 横屏训练: 进入置横屏, 退出恢复
    val activity = context as? Activity
    DisposableEffect(Unit) {
        val landscape = settings.landscapeMode
        if (landscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        onDispose {
            if (landscape) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
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
            state.loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Purple)
                        Spacer(Modifier.height(12.dp))
                        Text("正在加载历史行情...", fontSize = 14.sp)
                    }
                }
            }
            state.error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("加载失败: ${state.error}", fontSize = 14.sp, color = UpRed)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { vm.retry() }) { Text("重试") }
                    }
                }
            }
            else -> {
                QuoteBar(state)
                TimeframeTabs(
                    current = state.timeframe,
                    onSelect = { vm.setTimeframe(it) },
                    onOpenSettings = { showSettings = true }
                )
                // 图表
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
                    // 涨跌停染色只对日K有意义(周/月/分钟线不适用9.5%日线阈值)
                    limitPct = if (state.timeframe == TimeFrame.DAY &&
                        mode != TrainingMode.CRYPTO && mode != TrainingMode.INDEX
                    ) 0.095 else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                AutoPlayRow(state, vm)
                ActionButtons(state, vm)
                StatsBar(state)
            }
        }
    }

    if (state.showExitConfirm) {
        ExitConfirmDialog(
            onConfirm = { vm.confirmExit() },
            onDismiss = { vm.dismissExitConfirm() }
        )
    }
    if (showSettings) {
        TrainingSettingsSheet(
            state = state,
            vm = vm,
            onDismiss = {
                showSettings = false
                chartStyle = settings.chartStyle
            }
        )
    }
    state.result?.let { result ->
        SessionResultDialog(
            state = state,
            result = result,
            onSave = { strategyId, note -> vm.saveResult(strategyId, note) },
            onAbandon = { vm.abandon() }
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

@Composable
private fun QuoteBar(state: TrainingUiState) {
    val bar = state.currentBar ?: return
    val prevClose = state.prevDayClose
    val change = bar.close - prevClose
    val changePct = if (prevClose > 0) change / prevClose * 100 else 0.0
    val color = if (change >= 0) UpRed else DownGreen
    val sign = if (change >= 0) "+" else ""

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
                    String.format("%.2f", bar.close),
                    color = color,
                    fontSize = 26.sp,
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
                "$sign${String.format("%.2f", change)}  $sign${String.format("%.2f", changePct)}%",
                color = color,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                QuoteItem("开", String.format("%.2f", bar.open), if (bar.open >= prevClose) UpRed else DownGreen)
                QuoteItem("高", String.format("%.2f", bar.high), if (bar.high >= prevClose) UpRed else DownGreen)
                QuoteItem("低", String.format("%.2f", bar.low), if (bar.low >= prevClose) UpRed else DownGreen)
            }
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                QuoteItem("收", String.format("%.2f", bar.close), color)
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
            fontWeight = FontWeight.Medium
        )
    }
}

// ---------------- 周期 Tab ----------------

@Composable
private fun TimeframeTabs(
    current: TimeFrame,
    onSelect: (TimeFrame) -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimeFrame.entries.forEach { tf ->
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

@Composable
private fun ActionButtons(state: TrainingUiState, vm: TrainingViewModel) {
    val enabled = !state.finished && state.result == null
    val total = state.totalSlots.coerceAtLeast(1)
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val buySub = if (state.direction == Direction.SHORT) "平空${state.usedSlots}仓"
        else "可买${state.freeSlots}/${total}仓"
        val sellSub = when {
            state.direction == Direction.LONG -> "可卖${state.usedSlots}/${total}仓"
            state.allowShort -> "可卖${state.freeSlots}/${total}仓"
            else -> "未开启做空"
        }
        BigActionButton("买入", buySub, UpRed, enabled, Modifier.weight(1f)) { vm.onBuy() }
        BigActionButton("卖出", sellSub, SellBlue, enabled, Modifier.weight(1f)) { vm.onSell() }
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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
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
        StatItem("爆竹数", String.format("%.0f", state.firecrackers), GoldYellow)
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
