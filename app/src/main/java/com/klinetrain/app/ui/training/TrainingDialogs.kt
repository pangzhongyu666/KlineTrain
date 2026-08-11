package com.klinetrain.app.ui.training

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.klinetrain.app.KlineTrainApp
import com.klinetrain.app.data.model.ChartStyle
import com.klinetrain.app.data.model.MainOverlay
import com.klinetrain.app.data.model.RankSystem
import com.klinetrain.app.data.model.SessionResult
import com.klinetrain.app.data.model.SubIndicator
import com.klinetrain.app.ui.settings.ChartStyleSection
import com.klinetrain.app.ui.theme.DownGreen
import com.klinetrain.app.ui.theme.GoldYellow
import com.klinetrain.app.ui.theme.Purple
import com.klinetrain.app.ui.theme.UpRed

// ---------------- 确认结束弹窗 ----------------

@Composable
internal fun ExitConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认结束训练？") },
        text = { Text("结束后将按当前价格强制平仓，并结算本局成绩。") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("结束并结算", color = UpRed) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("继续训练") }
        }
    )
}

// ---------------- 设置底部弹窗(图表指标 + K线设置) ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrainingSettingsSheet(
    state: TrainingUiState,
    vm: TrainingViewModel,
    chartStyle: ChartStyle,
    onChartStyleChange: (ChartStyle) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text("图表设置（即时生效）", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            // 主图指标
            Text("主图指标", fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MainOverlay.entries.forEach { o ->
                    FilterChip(
                        selected = state.mainOverlay == o,
                        onClick = { vm.setMainOverlay(o) },
                        label = { Text(o.label) }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))

            // 副图指标(最多3个)
            Text("副图指标（最多3个）", fontSize = 13.sp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(SubIndicator.VOL, SubIndicator.MACD, SubIndicator.KDJ).forEach { sub ->
                    FilterChip(
                        selected = state.subIndicators.contains(sub),
                        onClick = { vm.toggleSubIndicator(sub) },
                        label = { Text(sub.name) }
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(SubIndicator.RSI, SubIndicator.ATR, SubIndicator.CCI).forEach { sub ->
                    FilterChip(
                        selected = state.subIndicators.contains(sub),
                        onClick = { vm.toggleSubIndicator(sub) },
                        label = { Text(sub.name) }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))

            // 筹码分布
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("筹码分布", fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = state.showChips, onCheckedChange = { vm.setShowChips(it) })
            }

            // 自定义公式
            Text("自定义公式", fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            if (state.formulas.isEmpty()) {
                Text(
                    "暂无自定义公式，可在“我的-公式管理”中创建",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = state.selectedFormulaId == null,
                        onClick = { vm.selectFormula(null) },
                        label = { Text("无") }
                    )
                }
                state.formulas.chunked(2).forEach { rowItems ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowItems.forEach { f ->
                            FilterChip(
                                selected = state.selectedFormulaId == f.id,
                                onClick = { vm.selectFormula(f) },
                                label = { Text(f.name + if (f.onMainChart) "(主图)" else "(副图)") }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 10.dp))

            // K线设置(样式/坐标/开关, 即时生效)
            Text("K线设置", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            ChartStyleSection(style = chartStyle, onChange = onChartStyleChange)

            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Text(
                "杠杆、K线数、做空、止盈止损等训练参数请在开局前设置",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

// ---------------- 结算结果弹窗 ----------------

@Composable
internal fun SessionResultDialog(
    state: TrainingUiState,
    result: SessionResult,
    onSave: (strategyId: Long?, note: String) -> Unit,
    onAbandon: () -> Unit
) {
    var note by rememberSaveable { mutableStateOf("") }
    var strategyExpanded by remember { mutableStateOf(false) }
    var selectedStrategyId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedStrategyName = state.strategies.firstOrNull { it.id == selectedStrategyId }?.name ?: "不归属战法"

    Dialog(
        onDismissRequest = { /* 必须显式选择保存或放弃 */ },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .wrapContentHeight()
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                // 大字提示
                val (headline, headColor) = when {
                    result.bankrupt -> "破产离场" to DownGreen
                    result.richOnce -> "暴富一局!" to GoldYellow
                    result.returnPct >= 0 -> "训练结束" to UpRed
                    else -> "训练结束" to DownGreen
                }
                Text(
                    headline,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = headColor,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                // 破产/暴富(金钱口径)提示
                if (result.bankrupt) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "💥 破产！金钱重置为${String.format("%.0f", state.baseInitial)}，进入新赛季",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = DownGreen,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                if (result.richOnce) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "🎉 暴富！金钱达到初始金额100倍",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = GoldYellow,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                // 段位变化提示: 按当前段位星数用 RankSystem 预演真实结果(青铜0星保底不掉星)
                val rankSettings = remember { KlineTrainApp.instance.settings }
                val oldTier = remember { rankSettings.rankTier }
                val oldStars = remember { rankSettings.rankStars }
                val (newTier, newStars) = RankSystem.apply(oldTier, oldStars, result.returnPct)
                val (rankText, rankColor) = when {
                    newTier > oldTier -> "升段！${RankSystem.tierName(newTier)}" to GoldYellow
                    newTier < oldTier -> "降段至${RankSystem.tierName(newTier)}" to DownGreen
                    newStars > oldStars -> "段位 +1 星" to GoldYellow
                    newStars < oldStars -> "段位 -1 星" to DownGreen
                    else -> "段位星数不变" to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    rankText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = rankColor,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(4.dp))

                // 揭示标的与真实区间(合并为一行节省高度)
                val start = state.windowKlines.firstOrNull()?.label ?: "--"
                val end = state.currentBar?.label ?: "--"
                Text(
                    "${state.stockName} (${state.stockCode})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Text(
                    "$start ~ $end",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(6.dp))

                // 指标网格
                ResultMetricsGrid(result)
                Spacer(Modifier.height(6.dp))

                // 智能复盘
                Text("智能复盘", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Purple)
                state.reviewLines.forEach { line ->
                    Text("· $line", fontSize = 11.sp)
                }
                Spacer(Modifier.height(6.dp))

                // 战法归属
                Text("战法归属", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Box {
                    OutlinedButton(
                        onClick = { strategyExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(selectedStrategyName, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = strategyExpanded,
                        onDismissRequest = { strategyExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("不归属战法") },
                            onClick = {
                                selectedStrategyId = null
                                strategyExpanded = false
                            }
                        )
                        state.strategies.forEach { st ->
                            DropdownMenuItem(
                                text = { Text(st.name) },
                                onClick = {
                                    selectedStrategyId = st.id
                                    strategyExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))

                // 复盘笔记
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("复盘笔记（可选）") },
                    minLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onAbandon) {
                        Text("放弃不保存", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { onSave(selectedStrategyId, note) },
                        colors = ButtonDefaults.buttonColors(containerColor = Purple)
                    ) {
                        Text("保存并退出")
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultMetricsGrid(result: SessionResult) {
    fun signPct(x: Double) = "${if (x >= 0) "+" else ""}${String.format("%.2f", x)}%"
    fun upDown(x: Double) = if (x >= 0) UpRed else DownGreen

    data class Metric(val label: String, val value: String, val color: Color?)

    val metrics = listOf(
        Metric("本局收益", signPct(result.returnPct), upDown(result.returnPct)),
        Metric("区间涨跌幅", signPct(result.intervalChangePct), upDown(result.intervalChangePct)),
        Metric("跑赢区间", signPct(result.outperformPct), upDown(result.outperformPct)),
        Metric("开仓次数", "${result.openCount}次", null),
        Metric("开仓胜率", String.format("%.2f", result.openWinRate) + "%", null),
        Metric(
            "盈亏比",
            if (result.profitLossRatio >= 999.0) "∞" else String.format("%.2f", result.profitLossRatio),
            null
        ),
        Metric("最大回撤", String.format("%.2f", result.maxDrawdownPct) + "%", null),
        Metric("持仓率", String.format("%.2f", result.holdRatio) + "%", null),
        Metric("重仓率", String.format("%.2f", result.heavyRatio) + "%", null),
        Metric("持仓天数", "${result.holdBars}天", null)
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(vertical = 6.dp)
    ) {
        val cols = 4
        metrics.chunked(cols).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                row.forEach { m ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            m.value,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            color = m.color ?: MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            m.label,
                            fontSize = 9.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
                // 不满一行时占位
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
