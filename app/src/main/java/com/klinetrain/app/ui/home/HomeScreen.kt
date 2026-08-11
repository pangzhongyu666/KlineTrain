package com.klinetrain.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.klinetrain.app.KlineTrainApp
import com.klinetrain.app.data.db.TrainingRecordEntity
import com.klinetrain.app.data.model.RankSystem
import com.klinetrain.app.data.model.TrainingMode
import com.klinetrain.app.ui.theme.DownGreen
import com.klinetrain.app.ui.theme.GoldYellow
import com.klinetrain.app.ui.theme.Purple
import com.klinetrain.app.ui.theme.PurpleDark
import com.klinetrain.app.ui.theme.UpRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 各训练模式的标签色（供首页与记录页共用） */
fun modeTagColor(modeName: String): Color = when (modeName) {
    TrainingMode.BLIND.name -> Color(0xFF3B6FE8)
    TrainingMode.LIMIT_UP.name -> Color(0xFFF5A623)
    TrainingMode.INDEX.name -> Color(0xFFE85B8A)
    TrainingMode.ETF.name -> Color(0xFF0FA97D)
    TrainingMode.CRYPTO.name -> Color(0xFFF7931A)
    else -> Purple
}

private fun modeLabel(modeName: String): String =
    TrainingMode.entries.firstOrNull { it.name == modeName }?.label ?: modeName

/** 涨红跌绿 */
fun pnlColor(v: Double): Color = if (v >= 0) UpRed else DownGreen

fun signedPct(v: Double): String =
    (if (v >= 0) "+" else "") + String.format(Locale.CHINA, "%.2f", v) + "%"

@Composable
fun HomeScreen(
    onStartTraining: (TrainingMode) -> Unit,
    onOpenRecords: () -> Unit,
    onOpenRecordDetail: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: HomeViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        item { HomeHeader(state) }
        item {
            Text(
                "训练模式",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
            )
        }
        item { ModeCardRow(onStartTraining) }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("最近训练", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    "更多 >",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onOpenRecords() }
                )
            }
        }
        if (state.recentRecords.isEmpty()) {
            item {
                Text(
                    "暂无训练记录，快去开始第一局训练吧",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
                )
            }
        } else {
            items(state.recentRecords, key = { it.id }) { record ->
                TrainingRecordCard(
                    record = record,
                    onClick = { onOpenRecordDetail(record.id) }
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun HomeHeader(state: HomeUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(PurpleDark, Purple))
            )
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("模拟训练", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                "爆竹: " + String.format(Locale.CHINA, "%,.2f", state.firecrackers),
                color = GoldYellow,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(20.dp))
        // 段位徽章(王者荣耀式，段位数据直接读全局设置，与爆竹一致在状态刷新时重读)
        val rankSettings = KlineTrainApp.instance.settings
        RankBadge(
            tier = rankSettings.rankTier.coerceIn(0, RankSystem.tiers.lastIndex),
            stars = rankSettings.rankStars.coerceAtLeast(0)
        )
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                "训练场次 ${state.sessionCount}",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            HeaderStat("持仓天数", HomeViewModel.formatHoldDays(state.totalHoldBars), Modifier.weight(1f))
            HeaderStat("总胜率", String.format(Locale.CHINA, "%.2f", state.totalWinRate) + "%", Modifier.weight(1f))
            HeaderStat("暴富次数", "${state.richCount}", Modifier.weight(1f))
            HeaderStat("破产次数", "${state.bankruptCount}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeaderStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}

/** 各段位徽章底色 */
private fun rankTierColor(tier: Int): Color = when (tier) {
    0 -> Color(0xFFB87333)   // 青铜
    1 -> Color(0xFFB8C4CE)   // 白银
    2 -> Color(0xFFF5C518)   // 黄金
    3 -> Color(0xFF3EB489)   // 铂金
    4 -> Color(0xFF5AC8FA)   // 钻石
    5 -> Color(0xFFB57EDC)   // 大师
    else -> Color(0xFFFF4D4F) // 王者
}

/** 王者荣耀式段位徽章：圆形底色+盾牌/皇冠+段位名+星槽 */
@Composable
private fun RankBadge(tier: Int, stars: Int, modifier: Modifier = Modifier) {
    val isKing = tier == RankSystem.tiers.lastIndex
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(rankTierColor(tier), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (isKing) Icons.Filled.EmojiEvents else Icons.Filled.Shield,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    RankSystem.tierName(tier),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (isKing) {
            Text("⭐×$stars", color = GoldYellow, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        } else {
            Row {
                val slots = RankSystem.starsNeeded(tier).coerceIn(1, 5)
                repeat(slots) { i ->
                    Icon(
                        imageVector = if (i < stars) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = null,
                        tint = if (i < stars) GoldYellow else Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "盈利≥5%升1星 · 亏损≥5%降1星",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 10.sp
        )
    }
}

private data class ModeCardSpec(
    val mode: TrainingMode,
    val color: Color,
    val icon: ImageVector
)

@Composable
private fun ModeCardRow(onStartTraining: (TrainingMode) -> Unit) {
    val specs = listOf(
        ModeCardSpec(TrainingMode.BLIND, Color(0xFF3B6FE8), Icons.Filled.VisibilityOff),
        ModeCardSpec(TrainingMode.LIMIT_UP, Color(0xFFF5A623), Icons.Filled.TrendingUp),
        ModeCardSpec(TrainingMode.INDEX, Color(0xFFE85B8A), Icons.Filled.ShowChart),
        ModeCardSpec(TrainingMode.ETF, Color(0xFF0FA97D), Icons.Filled.PieChart),
        ModeCardSpec(TrainingMode.CRYPTO, Color(0xFFF7931A), Icons.Filled.CurrencyBitcoin)
    )
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(specs) { spec ->
            Card(
                modifier = Modifier
                    .width(150.dp)
                    .height(96.dp)
                    .clickable { onStartTraining(spec.mode) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = spec.color)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            spec.icon,
                            contentDescription = spec.mode.label,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            spec.mode.label,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        spec.mode.desc,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/** 记录卡片（首页与记录列表页共用） */
@Composable
fun TrainingRecordCard(
    record: TrainingRecordEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateText = rememberDateText(record.createdAt)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(modeTagColor(record.mode), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(modeLabel(record.mode), color = Color.White, fontSize = 10.sp)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "${record.stockName} ${record.stockCode}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text(dateText, fontSize = 11.sp, color = Color.Gray)
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                RecordStat("持仓率", String.format(Locale.CHINA, "%.2f", record.holdRatio) + "%", Modifier.weight(1f))
                RecordStat("重仓率", String.format(Locale.CHINA, "%.2f", record.heavyRatio) + "%", Modifier.weight(1f))
                RecordStat("开仓胜率", String.format(Locale.CHINA, "%.2f", record.openWinRate) + "%", Modifier.weight(1f))
                RecordStat(
                    "区间涨跌幅", signedPct(record.intervalChangePct),
                    Modifier.weight(1f), valueColor = pnlColor(record.intervalChangePct)
                )
                RecordStat(
                    "盈亏", signedPct(record.returnPct),
                    Modifier.weight(1f), valueColor = pnlColor(record.returnPct)
                )
            }
        }
    }
}

@Composable
private fun RecordStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Unspecified
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = valueColor, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, color = Color.Gray, maxLines = 1)
    }
}

@Composable
private fun rememberDateText(millis: Long): String {
    return androidx.compose.runtime.remember(millis) {
        SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(millis))
    }
}
