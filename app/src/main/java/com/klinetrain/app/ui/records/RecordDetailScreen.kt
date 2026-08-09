package com.klinetrain.app.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.klinetrain.app.data.db.TradeEntity
import com.klinetrain.app.data.db.TrainingRecordEntity
import com.klinetrain.app.data.model.Direction
import com.klinetrain.app.data.model.TrainingMode
import com.klinetrain.app.ui.home.modeTagColor
import com.klinetrain.app.ui.home.pnlColor
import com.klinetrain.app.ui.home.signedPct
import com.klinetrain.app.ui.theme.DownGreen
import com.klinetrain.app.ui.theme.UpRed
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(
    recordId: Long,
    onBack: () -> Unit
) {
    val vm: RecordsViewModel = viewModel()
    LaunchedEffect(recordId) { vm.loadDetail(recordId) }

    val record by vm.detailRecord.collectAsStateWithLifecycle()
    val trades by vm.detailTrades.collectAsStateWithLifecycle()
    val strategies by vm.strategies.collectAsStateWithLifecycle()

    var showDeleteDialog by remember { mutableStateOf(false) }

    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("训练详情", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除记录", tint = UpRed)
                    }
                }
            )
        }
    ) { padding ->
        val r = record
        if (r == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("记录不存在或已删除", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
            ) {
                StatsCard(r)
                TradesCard(trades)
                ReviewCard(r)
                NoteCard(note = r.note, onSave = { vm.saveNote(it) })
                StrategyCard(
                    currentStrategyId = r.strategyId,
                    strategies = strategies,
                    onSelect = { vm.setStrategy(it) }
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除记录") },
            text = { Text("确定删除这条训练记录吗？删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    vm.deleteRecord(onDeleted = onBack)
                }) { Text("删除", color = UpRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun StatsCard(r: TrainingRecordEntity) {
    val modeLabel = TrainingMode.entries.firstOrNull { it.name == r.mode }?.label ?: r.mode
    val items: List<Triple<String, String, Color>> = listOf(
        Triple("模式", modeLabel, Color.Unspecified),
        Triple("标的", "${r.stockName} ${r.stockCode}", Color.Unspecified),
        Triple("区间", "${r.startLabel} ~ ${r.endLabel}", Color.Unspecified),
        Triple("本局收益", signedPct(r.returnPct), pnlColor(r.returnPct)),
        Triple("区间涨跌幅", signedPct(r.intervalChangePct), pnlColor(r.intervalChangePct)),
        Triple("跑赢区间", signedPct(r.outperformPct), pnlColor(r.outperformPct)),
        Triple("开仓次数", "${r.openCount}", Color.Unspecified),
        Triple("开仓胜率", String.format(Locale.CHINA, "%.2f", r.openWinRate) + "%", Color.Unspecified),
        Triple("盈亏比", String.format(Locale.CHINA, "%.2f", r.profitLossRatio), Color.Unspecified),
        Triple("最大回撤", String.format(Locale.CHINA, "%.2f", r.maxDrawdownPct) + "%", DownGreen),
        Triple("持仓率", String.format(Locale.CHINA, "%.2f", r.holdRatio) + "%", Color.Unspecified),
        Triple("重仓率", String.format(Locale.CHINA, "%.2f", r.heavyRatio) + "%", Color.Unspecified),
        Triple("持仓天数", "${r.holdBars}天", Color.Unspecified),
        Triple("杠杆", String.format(Locale.CHINA, "%.2f", r.leverage) + "倍", Color.Unspecified),
        Triple("耗时", formatDuration(r.durationSec), Color.Unspecified),
        Triple("暴富", if (r.richOnce) "是" else "否", if (r.richOnce) UpRed else Color.Unspecified),
        Triple("破产", if (r.bankrupt) "是" else "否", if (r.bankrupt) DownGreen else Color.Unspecified)
    )
    SectionCard(title = "本局统计") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .background(modeTagColor(r.mode), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(modeLabel, color = Color.White, fontSize = 11.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text("${r.stockName} ${r.stockCode}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        // 两列网格
        items.chunked(2).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { (label, value, color) ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 5.dp)
                    ) {
                        Text(label, fontSize = 12.sp, color = Color.Gray)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            value,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = color,
                            maxLines = 1
                        )
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TradesCard(trades: List<TradeEntity>) {
    SectionCard(title = "交易明细") {
        if (trades.isEmpty()) {
            Text("本局没有交易记录", fontSize = 12.sp, color = Color.Gray)
        } else {
            Row(modifier = Modifier.fillMaxWidth()) {
                TradeHeaderCell("时间", 1.4f)
                TradeHeaderCell("方向", 0.8f)
                TradeHeaderCell("开/平", 0.8f)
                TradeHeaderCell("价格", 1f)
                TradeHeaderCell("盈亏", 1f)
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )
            trades.forEach { t ->
                val dirLabel = if (t.direction == Direction.SHORT.name) "做空" else "做多"
                val dirColor = if (t.direction == Direction.SHORT.name) DownGreen else UpRed
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    TradeCell(t.barLabel, 1.4f)
                    TradeCell(dirLabel, 0.8f, dirColor)
                    TradeCell(if (t.isOpen) "开仓" else "平仓", 0.8f)
                    TradeCell(String.format(Locale.CHINA, "%.2f", t.price), 1f)
                    val pnl = t.pnl
                    if (pnl == null) {
                        TradeCell("-", 1f, Color.Gray)
                    } else {
                        TradeCell(
                            (if (pnl >= 0) "+" else "") + String.format(Locale.CHINA, "%.2f", pnl),
                            1f,
                            pnlColor(pnl)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.TradeHeaderCell(text: String, weight: Float) {
    Text(
        text,
        fontSize = 11.sp,
        color = Color.Gray,
        maxLines = 1,
        modifier = Modifier.weight(weight)
    )
}

@Composable
private fun RowScope.TradeCell(
    text: String,
    weight: Float,
    color: Color = Color.Unspecified
) {
    Text(
        text,
        fontSize = 12.sp,
        color = color,
        maxLines = 1,
        modifier = Modifier.weight(weight)
    )
}

@Composable
private fun ReviewCard(r: TrainingRecordEntity) {
    val comments = remember(r.id, r.returnPct) { RecordsViewModel.buildReviewComments(r) }
    SectionCard(title = "智能复盘") {
        comments.forEachIndexed { i, c ->
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                Text("${i + 1}.", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(c, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun NoteCard(note: String, onSave: (String) -> Unit) {
    var text by remember(note) { mutableStateOf(note) }
    SectionCard(title = "复盘笔记") {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            placeholder = { Text("写下本局的复盘心得…", fontSize = 12.sp) }
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            Button(onClick = { onSave(text) }, enabled = text != note) {
                Text("保存笔记")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StrategyCard(
    currentStrategyId: Long?,
    strategies: List<com.klinetrain.app.data.db.StrategyEntity>,
    onSelect: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentName = strategies.firstOrNull { it.id == currentStrategyId }?.name ?: "未归属"
    SectionCard(title = "战法归属") {
        if (strategies.isEmpty()) {
            Text("还没有创建战法，可在「我的-战法管理」中新建", fontSize = 12.sp, color = Color.Gray)
        } else {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                TextField(
                    value = currentName,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.textFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("未归属") },
                        onClick = {
                            expanded = false
                            onSelect(null)
                        }
                    )
                    strategies.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s.name) },
                            onClick = {
                                expanded = false
                                onSelect(s.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(sec: Long): String {
    val s = sec.coerceAtLeast(0)
    val m = s / 60
    val r = s % 60
    return if (m > 0) "${m}分${r}秒" else "${r}秒"
}
