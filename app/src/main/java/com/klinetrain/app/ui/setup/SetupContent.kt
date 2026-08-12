@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class
)

package com.klinetrain.app.ui.setup

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klinetrain.app.KlineTrainApp
import com.klinetrain.app.data.model.TrainingMode
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

/** 截图风格的蓝色(开关/Chip高亮) */
private val SetupBlue = Color(0xFF2E7CF6)

/** 内置币种 */
private val BuiltinCoins = listOf("BTC", "ETH")

/** 训练时间段选项: 存储code -> 显示文案(3=自定义, 年数在 customYears) */
private val TimeRangeOptions = listOf(
    0 to "不限",
    1 to "最近1年",
    2 to "最近5年",
    3 to "自定义"
)

/** 每局K线数预设 */
private val SessionBarPresets = listOf(60, 120, 150, 240)

/**
 * 训练设置内容(可复用)。所有控件直接读写 SettingsStore，改动立即持久化。
 *
 * @param mode 训练模式；null 表示"我的-训练设置"通用页(显示除币种选择外的全部，含股票筛选与初始金额)。
 *             BLIND/LIMIT_UP 显示股票筛选(市场选择/去除ST)；CRYPTO 额外显示币种选择；
 *             非null(开局前设置页)不显示初始金额——初始金额在"我的-训练设置"中设定。
 * @param onSessionBarsChanged 每局K线数变化时回调(供外层描述文字联动刷新)。
 */
@Composable
fun SetupContent(
    mode: TrainingMode?,
    modifier: Modifier = Modifier,
    onSessionBarsChanged: ((Int) -> Unit)? = null
) {
    val settings = remember { KlineTrainApp.instance.settings }
    val focusManager = LocalFocusManager.current

    val showStockFilters = mode == null || mode == TrainingMode.BLIND || mode == TrainingMode.LIMIT_UP
    val showCoins = mode == TrainingMode.CRYPTO

    // ---------- 开关状态 ----------
    var vibrateMode by remember { mutableStateOf(settings.vibrateMode) }
    var landscapeMode by remember { mutableStateOf(settings.landscapeMode) }
    var splitMode by remember { mutableStateOf(settings.splitMode) }
    var allowShort by remember { mutableStateOf(settings.allowShort) }
    var excludeSt by remember { mutableStateOf(settings.excludeSt) }
    var useLeverage by remember { mutableStateOf(settings.useLeverage) }
    var stopEnabled by remember { mutableStateOf(settings.stopEnabled) }

    // ---------- 选择状态 ----------
    var totalSlots by remember { mutableIntStateOf(settings.totalSlots.coerceIn(1, 10)) }
    var slotsPerTrade by remember {
        val clamped = settings.slotsPerTrade.coerceIn(1, settings.totalSlots.coerceAtLeast(1))
        if (clamped != settings.slotsPerTrade) settings.slotsPerTrade = clamped
        mutableIntStateOf(clamped)
    }
    var marketFilter by remember { mutableIntStateOf(settings.marketFilter.coerceIn(0, 3)) }
    var timeRangeFilter by remember { mutableIntStateOf(settings.timeRangeFilter.coerceIn(0, 3)) }
    var sessionBars by remember { mutableIntStateOf(settings.sessionBars) }
    var leverage by remember { mutableFloatStateOf(settings.leverage.toFloat().coerceIn(1f, 10f)) }

    // ---------- 数字输入(失焦/确认时写回并钳制) ----------
    var initialCashText by remember { mutableStateOf(fmtAmount(settings.initialCash)) }
    var feeText by remember { mutableStateOf(String.format(Locale.US, "%.3f", settings.feeRate * 100)) }
    var stopProfitText by remember { mutableStateOf(fmtAmount(settings.stopProfitPct)) }
    var stopLossText by remember { mutableStateOf(fmtAmount(settings.stopLossPct)) }
    var customYearsText by remember { mutableStateOf(settings.customYears.toString()) }
    var sessionBarsText by remember { mutableStateOf(settings.sessionBars.toString()) }
    // 每局K线数是否处于自定义输入(当前值不在预设中则默认自定义)
    var sessionCustom by remember { mutableStateOf(settings.sessionBars !in SessionBarPresets) }

    // ---------- 金钱/币种 ----------
    var firecrackers by remember { mutableStateOf(settings.firecrackers) }
    var customCoins by remember { mutableStateOf(settings.customCoins) }
    var selectedCoin by remember { mutableStateOf(settings.selectedCoin) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showAddCoinDialog by remember { mutableStateOf(false) }

    fun commitInitialCash() {
        val v = initialCashText.toDoubleOrNull()
        if (v == null || v <= 0.0) {
            initialCashText = fmtAmount(settings.initialCash)
            return
        }
        val clamped = v.coerceIn(100.0, 1_000_000_000.0)
        settings.initialCash = clamped
        initialCashText = fmtAmount(clamped)
    }

    fun commitFee() {
        val v = feeText.toDoubleOrNull()
        val clamped = (v ?: settings.feeRate * 100).coerceIn(0.0, 5.0)
        settings.feeRate = clamped / 100.0
        feeText = String.format(Locale.US, "%.3f", clamped)
    }

    fun commitStopProfit() {
        val v = stopProfitText.toDoubleOrNull()
        val clamped = (v ?: settings.stopProfitPct).coerceIn(0.1, 1000.0)
        settings.stopProfitPct = clamped
        stopProfitText = fmtAmount(clamped)
    }

    fun commitStopLoss() {
        val v = stopLossText.toDoubleOrNull()
        val clamped = (v ?: settings.stopLossPct).coerceIn(0.1, 100.0)
        settings.stopLossPct = clamped
        stopLossText = fmtAmount(clamped)
    }

    fun commitCustomYears() {
        val v = customYearsText.toIntOrNull()
        val clamped = (v ?: settings.customYears).coerceIn(1, 50)
        settings.customYears = clamped
        customYearsText = clamped.toString()
    }

    fun commitSessionBars() {
        val v = sessionBarsText.toIntOrNull()
        // 上限500: 行情源单次最多稳定提供约640根日K, 需预留90根warmup
        val clamped = (v ?: settings.sessionBars).coerceIn(30, 500)
        sessionBars = clamped
        settings.sessionBars = clamped
        sessionBarsText = clamped.toString()
        onSessionBarsChanged?.invoke(clamped)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // ===== 开关对：震动 | 横屏 =====
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SwitchCard("震动模式", vibrateMode, Modifier.weight(1f)) {
                vibrateMode = it; settings.vibrateMode = it
            }
            SwitchCard("横屏模式", landscapeMode, Modifier.weight(1f)) {
                landscapeMode = it; settings.landscapeMode = it
            }
        }

        // ===== 开关对：分仓 | 做空 =====
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SwitchCard("分仓模式", splitMode, Modifier.weight(1f)) {
                splitMode = it; settings.splitMode = it
            }
            SwitchCard("允许做空", allowShort, Modifier.weight(1f)) {
                allowShort = it; settings.allowShort = it
            }
        }

        // ===== 分仓明细 =====
        if (splitMode) {
            SettingCard {
                LabeledChips(
                    label = "总仓数",
                    options = listOf(2, 3, 5, 10).map { "${it}仓" },
                    selectedIndex = listOf(2, 3, 5, 10).indexOf(totalSlots)
                ) { i ->
                    val v = listOf(2, 3, 5, 10).getOrElse(i) { 5 }
                    totalSlots = v
                    settings.totalSlots = v
                    if (slotsPerTrade > v) {
                        slotsPerTrade = v
                        settings.slotsPerTrade = v
                    }
                }
                Spacer(Modifier.height(10.dp))
                LabeledChips(
                    label = "每次仓位",
                    options = (1..totalSlots).map { "${it}仓" },
                    selectedIndex = slotsPerTrade - 1
                ) { i ->
                    val v = (i + 1).coerceIn(1, totalSlots)
                    slotsPerTrade = v
                    settings.slotsPerTrade = v
                }
            }
        }

        // ===== 市场选择(仅股票类) =====
        if (showStockFilters) {
            SettingCard {
                LabeledChips(
                    label = "市场选择",
                    options = listOf("不限", "主板", "创业板", "科创板"),
                    selectedIndex = marketFilter
                ) { i ->
                    marketFilter = i; settings.marketFilter = i
                }
            }
        }

        // ===== 训练时间段 =====
        SettingCard {
            LabeledChips(
                label = "训练时间段",
                options = TimeRangeOptions.map { it.second },
                selectedIndex = TimeRangeOptions.indexOfFirst { it.first == timeRangeFilter }
            ) { i ->
                val code = TimeRangeOptions.getOrElse(i) { TimeRangeOptions[0] }.first
                timeRangeFilter = code
                settings.timeRangeFilter = code
            }
            if (timeRangeFilter == 3) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "自定义: 最近N年内的行情",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        value = customYearsText,
                        onValueChange = { customYearsText = it },
                        onCommit = { commitCustomYears() },
                        modifier = Modifier.width(110.dp),
                        keyboardType = KeyboardType.Number,
                        suffix = "年"
                    )
                }
            }
        }

        // ===== 去除ST(仅股票类) =====
        if (showStockFilters) {
            SettingCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("去除ST", fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    BlueSwitch(excludeSt) { excludeSt = it; settings.excludeSt = it }
                }
            }
        }

        // ===== 初始金额 + 金钱(仅"我的-训练设置"页; 开局前不再设置, 重置金钱按此金额) =====
        if (mode == null) {
            SettingCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("初始金额", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "暴富=初始100倍 · 破产=初始5%",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    NumberField(
                        value = initialCashText,
                        onValueChange = { initialCashText = it },
                        onCommit = { commitInitialCash() },
                        modifier = Modifier.width(130.dp),
                        keyboardType = KeyboardType.Number
                    )
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "当前金钱  " + String.format(Locale.US, "%,.0f", firecrackers),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        // 输入框可能仍持有焦点未提交, 先提交再弹确认, 避免按旧金额重置
                        commitInitialCash()
                        showResetDialog = true
                    }) {
                        Text("重置金钱", color = SetupBlue, fontSize = 13.sp)
                    }
                }
            }
        }

        // ===== 手续费 =====
        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("手续费", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text("单边费率，精度0.001%", fontSize = 11.sp, color = Color.Gray)
                }
                NumberField(
                    value = feeText,
                    onValueChange = { feeText = it },
                    onCommit = { commitFee() },
                    modifier = Modifier.width(130.dp),
                    suffix = "%"
                )
            }
        }

        // ===== 使用杠杆 =====
        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("使用杠杆", fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                BlueSwitch(useLeverage) { useLeverage = it; settings.useLeverage = it }
            }
            if (useLeverage) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = leverage,
                        onValueChange = { leverage = it },
                        onValueChangeFinished = {
                            val l = leverage.roundToInt().coerceIn(1, 10)
                            leverage = l.toFloat()
                            settings.leverage = l.toDouble()
                        },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = SetupBlue,
                            activeTrackColor = SetupBlue
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${leverage.roundToInt().coerceIn(1, 10)}x",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = SetupBlue
                    )
                }
            }
        }

        // ===== 止盈止损 =====
        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("止盈止损", fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                BlueSwitch(stopEnabled) { stopEnabled = it; settings.stopEnabled = it }
            }
            if (stopEnabled) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(
                        value = stopProfitText,
                        onValueChange = { stopProfitText = it },
                        onCommit = { commitStopProfit() },
                        modifier = Modifier.weight(1f),
                        label = "止盈",
                        suffix = "%"
                    )
                    NumberField(
                        value = stopLossText,
                        onValueChange = { stopLossText = it },
                        onCommit = { commitStopLoss() },
                        modifier = Modifier.weight(1f),
                        label = "止损",
                        suffix = "%"
                    )
                }
            }
        }

        // ===== 每局K线数 =====
        SettingCard {
            LabeledChips(
                label = "每局K线数",
                options = SessionBarPresets.map { it.toString() } + "自定义",
                selectedIndex = if (sessionCustom) SessionBarPresets.size
                else SessionBarPresets.indexOf(sessionBars)
            ) { i ->
                if (i < SessionBarPresets.size) {
                    val v = SessionBarPresets[i]
                    sessionCustom = false
                    sessionBars = v
                    settings.sessionBars = v
                    sessionBarsText = v.toString()
                    onSessionBarsChanged?.invoke(v)
                } else {
                    sessionCustom = true
                }
            }
            if (sessionCustom) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "自定义: 30~500根",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        value = sessionBarsText,
                        onValueChange = { sessionBarsText = it },
                        onCommit = { commitSessionBars() },
                        modifier = Modifier.width(110.dp),
                        keyboardType = KeyboardType.Number,
                        suffix = "根"
                    )
                }
            }
        }

        // ===== 币种选择(仅币圈训练) =====
        if (showCoins) {
            val effectiveSelected =
                if (selectedCoin in BuiltinCoins || selectedCoin in customCoins) selectedCoin else "BTC"
            SettingCard {
                Text("币种选择", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BuiltinCoins.forEach { coin ->
                        SettingChip(
                            text = coin,
                            selected = effectiveSelected == coin,
                            onClick = { selectedCoin = coin; settings.selectedCoin = coin }
                        )
                    }
                    customCoins.forEach { coin ->
                        SettingChip(
                            text = coin,
                            selected = effectiveSelected == coin,
                            onClick = { selectedCoin = coin; settings.selectedCoin = coin },
                            onLongClick = {
                                val newList = customCoins.filterNot { it == coin }
                                customCoins = newList
                                settings.customCoins = newList
                                if (selectedCoin == coin) {
                                    selectedCoin = "BTC"
                                    settings.selectedCoin = "BTC"
                                }
                            }
                        )
                    }
                    SettingChip(
                        text = "+自定义",
                        selected = false,
                        accent = true,
                        onClick = { showAddCoinDialog = true }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text("长按自定义币种可删除", fontSize = 10.sp, color = Color.Gray)
            }
        }
    }

    // ===== 重置金钱确认弹窗 =====
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("重置金钱") },
            text = {
                Text(
                    "确认将金钱重置为初始金额 " + fmtAmount(settings.initialCash) +
                        "，并开启新赛季吗？当前赛季的训练记录仍会保留。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    settings.firecrackers = settings.initialCash
                    settings.seasonIndex = settings.seasonIndex + 1
                    firecrackers = settings.firecrackers
                    showResetDialog = false
                }) { Text("确认重置", color = SetupBlue) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("取消") }
            }
        )
    }

    // ===== 添加自定义币种弹窗 =====
    if (showAddCoinDialog) {
        var coinInput by remember { mutableStateOf("") }
        var coinError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showAddCoinDialog = false },
            title = { Text("添加自定义币种") },
            text = {
                Column {
                    OutlinedTextField(
                        value = coinInput,
                        onValueChange = { raw ->
                            coinInput = raw.uppercase(Locale.US).filter { it in 'A'..'Z' }.take(10)
                            coinError = null
                        },
                        singleLine = true,
                        label = { Text("币种符号，如 SOL") },
                        isError = coinError != null,
                        supportingText = coinError?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("输入2~10位字母的币种基础符号，按USDT计价", fontSize = 11.sp, color = Color.Gray)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val sym = coinInput.trim().uppercase(Locale.US)
                    when {
                        !Regex("^[A-Z]{2,10}$").matches(sym) ->
                            coinError = "格式不正确，需2~10位字母"
                        sym in BuiltinCoins || sym in customCoins ->
                            coinError = "该币种已存在"
                        else -> {
                            val newList = customCoins + sym
                            customCoins = newList
                            settings.customCoins = newList
                            selectedCoin = sym
                            settings.selectedCoin = sym
                            showAddCoinDialog = false
                        }
                    }
                }) { Text("添加", color = SetupBlue) }
            },
            dismissButton = {
                TextButton(onClick = { showAddCoinDialog = false }) { Text("取消") }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// 私有小部件
// ---------------------------------------------------------------------------

/** 圆角浅灰卡片 */
@Composable
private fun SettingCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            content = content
        )
    }
}

/** 半宽开关卡片(标签+蓝色开关) */
@Composable
private fun SwitchCard(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Boolean) -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            BlueSwitch(checked, onChange)
        }
    }
}

@Composable
private fun BlueSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = SetupBlue,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Color(0xFFDDE0E6),
            uncheckedBorderColor = Color.Transparent
        )
    )
}

/** 标签 + 一组单选Chip(单行, 放不下时横向滚动) */
@Composable
private fun LabeledChips(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.width(10.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEachIndexed { i, opt ->
                SettingChip(text = opt, selected = i == selectedIndex, onClick = { onSelect(i) })
            }
        }
    }
}

/** 圆角胶囊Chip；选中=浅蓝底蓝框蓝字。支持长按(自定义币删除) */
@Composable
private fun SettingChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    accent: Boolean = false
) {
    val shape = RoundedCornerShape(50)
    val bg = when {
        selected -> SetupBlue.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surface
    }
    val textColor = when {
        selected || accent -> SetupBlue
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    }
    Box(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, if (selected) SetupBlue else Color.Transparent, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        Text(text, fontSize = 13.sp, color = textColor, maxLines = 1)
    }
}

/** 数字输入框：失焦或键盘确认时通过 onCommit 写回并钳制 */
@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    label: String? = null,
    suffix: String? = null
) {
    val focusManager = LocalFocusManager.current
    var hadFocus by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            onValueChange(raw.filter { it.isDigit() || it == '.' }.take(12))
        },
        modifier = modifier.onFocusChanged { st ->
            if (st.isFocused) {
                hadFocus = true
            } else if (hadFocus) {
                hadFocus = false
                onCommit()
            }
        },
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
        label = label?.let { { Text(it, fontSize = 12.sp) } },
        suffix = suffix?.let { { Text(it, fontSize = 13.sp) } },
        shape = RoundedCornerShape(10.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
    )
}

/** 整数不带小数位，否则保留两位 */
private fun fmtAmount(v: Double): String =
    if (v.isFinite() && v == floor(v)) String.format(Locale.US, "%.0f", v)
    else String.format(Locale.US, "%.2f", v)
