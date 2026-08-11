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
import com.klinetrain.app.data.model.MainOverlay
import com.klinetrain.app.data.model.SessionResult
import com.klinetrain.app.data.model.SubIndicator
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

// ---------------- 设置底部弹窗(仅图表相关) ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrainingSettingsSheet(
    state: TrainingUiState,
    vm: TrainingViewModel,
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
                .padding(horizontal = 20.dp, vertical = 32.dp)
                .wrapContentHeight()
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
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
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = headColor,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                // 破产/暴富(爆竹口径)提示
                if (result.bankrupt) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "💥 破产！爆竹重置为${String.format("%.0f", state.baseInitial)}，进入新赛季",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = DownGreen,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                if (result.richOnce) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "🎉 暴富！爆竹达到初始金额100倍",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = GoldYellow,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                // 段位变化提示(与 RankSystem 的 ±5% 加减星规则一致)
                val (rankText, rankColor) = when {
                    result.returnPct >= 5.0 -> "段位 +1 星" to GoldYellow
                    result.returnPct <= -5.0 -> "段位 -1 星" to DownGreen
                    else -> "段位星数不变" to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    rankText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = rankColor,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(6.dp))

                // 揭示标的与真实区间
                Text(
                    "${state.stockName} (${state.stockCode})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                val start = state.windowKlines.firstOrNull()?.label ?: "--"
                val end = state.currentBar?.label ?: "--"
                Text(
                    "$start ~ $end",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(12.dp))

                // 指标网格
                ResultMetricsGrid(result)
                Spacer(Modifier.height(12.dp))

                // 智能复盘
                Text("智能复盘", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Purple)
                Spacer(Modifier.height(4.dp))
                state.reviewLines.forEach { line ->
                    Text("· $line", fontSize = 12.sp, modifier = Modifier.padding(vertical = 1.dp))
                }
                Spacer(Modifier.height(12.dp))

                // 战法归属
                Text("战法归属", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
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
                Spacer(Modifier.height(8.dp))

                // 复盘笔记
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("复盘笔记（可选）") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))

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
            .padding(vertical = 8.dp)
    ) {
        metrics.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                row.forEach { m ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            m.value,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = m.color ?: MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            m.label,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
                // 不满3列时占位
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
