package com.klinetrain.app.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klinetrain.app.KlineTrainApp
import com.klinetrain.app.data.model.TrainingMode
import com.klinetrain.app.ui.theme.Purple

/**
 * 训练初始设置页：开始某模式训练前的配置页。
 * 顶部模式标题 + 右上关闭，中部滚动设置项，底部"开始训练"。
 */
@Composable
fun TrainingSetupScreen(
    mode: TrainingMode,
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    val settings = remember { KlineTrainApp.instance.settings }
    var sessionBars by remember { mutableIntStateOf(settings.sessionBars) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 顶栏：居中标题 + 右上X
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                mode.label,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "关闭")
            }
        }

        // 中部滚动内容：模式说明 + 设置项
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                modeDescription(mode, sessionBars),
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier.padding(vertical = 8.dp)
            )
            SetupContent(
                mode = mode,
                onSessionBarsChanged = { sessionBars = it }
            )
            Spacer(Modifier.height(16.dp))
        }

        // 底部开始按钮
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .height(50.dp),
            shape = RoundedCornerShape(25.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Purple,
                contentColor = Color.White
            )
        ) {
            Text("开始训练", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** 各模式的说明文案(sessionBars 联动) */
private fun modeDescription(mode: TrainingMode, bars: Int): String = when (mode) {
    TrainingMode.BLIND ->
        "双盲训练完全随机，选取实盘股票走势。您将模拟交易未知时间、未知股票的${bars}根K线，" +
            "盈亏会影响您的爆竹数和段位，请认真对待。"
    TrainingMode.LIMIT_UP ->
        "涨停训练聚焦强势股：随机选取一只出现涨停的实盘股票，从涨停次日起模拟交易涨停后50个交易日的走势，" +
            "训练您对涨停板后续行情的把握能力，盈亏同样计入爆竹数和段位。"
    TrainingMode.INDEX ->
        "指数训练选取上证、深证、创业板等主要指数的真实历史走势。您将模拟交易未知时间段的${bars}根K线，" +
            "感受大盘节奏，练习趋势判断与仓位管理。"
    TrainingMode.ETF ->
        "ETF训练选取宽基与行业ETF的真实历史走势。您将模拟交易未知时间段的${bars}根K线，" +
            "适合练习板块轮动与中线趋势跟随。"
    TrainingMode.CRYPTO ->
        "币圈训练基于7×24真实币价走势，支持做多做空。您将模拟交易所选币种未知时间段的${bars}根K线，" +
            "币价波动剧烈，请注意控制风险。"
}
