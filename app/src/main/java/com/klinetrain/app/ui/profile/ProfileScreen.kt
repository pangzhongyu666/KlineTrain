package com.klinetrain.app.ui.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.klinetrain.app.KlineTrainApp
import com.klinetrain.app.data.model.RankSystem
import com.klinetrain.app.ui.theme.Purple
import com.klinetrain.app.ui.theme.PurpleDark
import com.klinetrain.app.ui.theme.UpRed
import java.util.Locale

@Composable
fun ProfileScreen(
    onOpenStrategies: () -> Unit,
    onOpenFormulas: () -> Unit,
    onOpenRecords: () -> Unit,
    onOpenTrainSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: ProfileViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val nickname by vm.nickname.collectAsStateWithLifecycle()

    var showNicknameDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        // 头部
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(PurpleDark, Purple)))
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    nickname.take(1),
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showNicknameDialog = true }
                ) {
                    Text(nickname, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "修改昵称",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                // 当前段位(王者荣耀式)
                val rankSettings = KlineTrainApp.instance.settings
                Text(
                    RankSystem.label(rankSettings.rankTier, rankSettings.rankStars),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp
                )
            }
        }

        // 训练数据卡
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("训练数据", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    DataCell("总场次", "${state.totalSessions}", Modifier.weight(1f))
                    DataCell("总胜率", String.format(Locale.CHINA, "%.2f", state.totalWinRate) + "%", Modifier.weight(1f))
                    DataCell("总暴富次数", "${state.totalRichCount}", Modifier.weight(1f))
                    DataCell("总破产次数", "${state.totalBankruptCount}", Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    DataCell("总持仓时间", ProfileViewModel.formatHoldDays(state.totalHoldBars), Modifier.weight(1f))
                    DataCell("总训练消耗", ProfileViewModel.formatDuration(state.totalDurationSec), Modifier.weight(1f))
                    DataCell("总开仓次数", "${state.totalOpenCount}", Modifier.weight(1f))
                    DataCell("总开仓胜率", String.format(Locale.CHINA, "%.2f", state.totalOpenWinRate) + "%", Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "当前第${state.seasonIndex}赛季 · 金钱${String.format(Locale.CHINA, "%,.2f", state.firecrackers)}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }

        // 成长曲线
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("成长曲线", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                GrowthCurve(
                    points = state.growthCurve,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 列表入口
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column {
                EntryRow(Icons.Filled.MilitaryTech, "战法管理", onOpenStrategies)
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                EntryRow(Icons.Filled.Functions, "公式指标", onOpenFormulas)
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                EntryRow(Icons.Filled.History, "全部训练记录", onOpenRecords)
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                EntryRow(Icons.Filled.Tune, "训练设置", onOpenTrainSettings)
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showNicknameDialog) {
        var input by remember { mutableStateOf(nickname) }
        AlertDialog(
            onDismissRequest = { showNicknameDialog = false },
            title = { Text("修改昵称") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    label = { Text("昵称") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateNickname(input)
                    showNicknameDialog = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showNicknameDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun DataCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // 长值(如"1年2个月13天")缩小字号, 保持单行不截断
        Text(
            value,
            fontSize = if (value.length >= 7) 11.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, color = Color.Gray, maxLines = 1)
    }
}

@Composable
private fun EntryRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, tint = Purple, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.Gray
        )
    }
}

/** 累计收益率折线图（红线） */
@Composable
private fun GrowthCurve(points: List<Double>, modifier: Modifier = Modifier) {
    if (points.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("训练数据不足，完成更多训练后展示成长曲线", fontSize = 12.sp, color = Color.Gray)
        }
        return
    }
    val gridColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val maxV = points.max()
        val minV = points.min()
        val range = (maxV - minV).let { if (it <= 0.0) 1.0 else it }
        // 网格线
        val gridLines = 4
        for (i in 0..gridLines) {
            val y = h * i / gridLines
            drawLine(
                color = gridColor,
                start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(w, y),
                strokeWidth = 1f
            )
        }
        // 折线
        val path = Path()
        points.forEachIndexed { i, v ->
            val x = w * i / (points.size - 1).toFloat()
            val y = h * (1f - ((v - minV) / range).toFloat()) * 0.9f + h * 0.05f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = UpRed, style = Stroke(width = 4f))
    }
}
