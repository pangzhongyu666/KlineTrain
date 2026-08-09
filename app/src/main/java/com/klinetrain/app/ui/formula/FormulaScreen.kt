package com.klinetrain.app.ui.formula

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.klinetrain.app.data.db.FormulaEntity
import com.klinetrain.app.formula.FormulaEngine
import com.klinetrain.app.ui.theme.DownGreen
import com.klinetrain.app.ui.theme.Purple
import com.klinetrain.app.ui.theme.UpRed

/**
 * 自定义公式指标管理页：列表 + 新建/编辑 + 校验 + 帮助速查。
 */
@Composable
fun FormulaScreen(onBack: () -> Unit) {
    val vm: FormulaViewModel = viewModel()
    val formulas by vm.formulas.collectAsState()

    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<FormulaEntity?>(null) }
    var deleting by remember { mutableStateOf<FormulaEntity?>(null) }

    if (showEditor) {
        FormulaEditorPage(
            original = editing,
            onClose = { showEditor = false },
            onSave = { entity -> vm.save(entity) { showEditor = false } }
        )
    } else {
        FormulaListPage(
            formulas = formulas,
            onBack = onBack,
            onCreate = { editing = null; showEditor = true },
            onEdit = { editing = it; showEditor = true },
            onLongPress = { deleting = it }
        )
    }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除公式") },
            text = { Text("确定删除公式 \"${target.name}\" 吗？删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = { vm.delete(target); deleting = null }) {
                    Text("删除", color = UpRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            }
        )
    }
}

// ==================== 列表页 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormulaListPage(
    formulas: List<FormulaEntity>,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (FormulaEntity) -> Unit,
    onLongPress: (FormulaEntity) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("自定义指标") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onCreate) {
                        Icon(Icons.Filled.Add, contentDescription = "新建公式")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (formulas.isEmpty()) {
                item {
                    Text(
                        "暂无自定义公式，点击右上角 \"+\" 新建",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
            items(formulas, key = { it.id }) { formula ->
                FormulaCard(
                    formula = formula,
                    onClick = { onEdit(formula) },
                    onLongClick = { onLongPress(formula) }
                )
            }
            item { HelpCard() }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FormulaCard(
    formula: FormulaEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Functions,
                    contentDescription = null,
                    tint = Purple
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formula.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ChartTag(onMain = formula.onMainChart)
            }
            Text(
                formula.source,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                "点击编辑 · 长按删除",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun ChartTag(onMain: Boolean) {
    val (text, color) = if (onMain) "主图" to UpRed else "副图" to Purple
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

// ==================== 编辑页 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormulaEditorPage(
    original: FormulaEntity?,
    onClose: () -> Unit,
    onSave: (FormulaEntity) -> Unit
) {
    var name by remember(original) { mutableStateOf(original?.name ?: "") }
    var source by remember(original) { mutableStateOf(original?.source ?: "") }
    var onMainChart by remember(original) { mutableStateOf(original?.onMainChart ?: false) }
    // null=未校验; ""=通过; 其他=错误信息
    var checkResult by remember(original) { mutableStateOf<String?>(null) }
    var nameError by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onClose)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (original == null) "新建公式" else "编辑公式") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = null },
                label = { Text("公式名称") },
                singleLine = true,
                isError = nameError != null,
                supportingText = { nameError?.let { Text(it, color = UpRed) } },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text("叠加主图", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (onMainChart) "输出线绘制在K线主图上" else "输出线绘制在独立副图中",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = onMainChart, onCheckedChange = { onMainChart = it })
            }

            OutlinedTextField(
                value = source,
                onValueChange = { source = it; checkResult = null },
                label = { Text("公式源码") },
                placeholder = { Text("MA5: MA(C,5);\nMA10: MA(C,10);") },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp)
            )

            // 校验结果
            when {
                checkResult == null -> {}
                checkResult!!.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = DownGreen)
                    Spacer(Modifier.width(6.dp))
                    Text("校验通过", color = DownGreen, style = MaterialTheme.typography.bodyMedium)
                }
                else -> Text(
                    checkResult!!,
                    color = UpRed,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { checkResult = FormulaEngine.validate(source) ?: "" },
                    modifier = Modifier.weight(1f)
                ) { Text("校验") }
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            nameError = "请输入公式名称"
                            return@Button
                        }
                        val err = FormulaEngine.validate(source)
                        checkResult = err ?: ""
                        if (err == null) {
                            onSave(
                                FormulaEntity(
                                    id = original?.id ?: 0,
                                    name = name.trim(),
                                    source = source,
                                    onMainChart = onMainChart,
                                    createdAt = original?.createdAt ?: System.currentTimeMillis()
                                )
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("保存") }
            }

            HelpCard()
        }
    }
}

// ==================== 帮助卡 ====================

@Composable
private fun HelpCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "公式语法速查",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Purple
            )
            HelpLine("变量", "OPEN/O 开盘  HIGH/H 最高  LOW/L 最低  CLOSE/C 收盘  VOL/V 成交量  AMOUNT 成交额")
            HelpLine(
                "函数",
                "MA(x,n) 均线  EMA(x,n) 指数均线  SMA(x,n,m) 移动平均  REF(x,n) 前n根  " +
                    "HHV(x,n) n根最高  LLV(x,n) n根最低  SUM(x,n) 求和  STD(x,n) 标准差  " +
                    "ABS(x) 绝对值  MAX/MIN(a,b)  CROSS(a,b) 上穿  IF(条件,a,b)  COUNT(条件,n)  AVEDEV(x,n)"
            )
            HelpLine("运算", "+ - * /  比较 > < >= <= == !=  逻辑 AND OR (或 && ||)  括号")
            HelpLine("语句", "每行一条 \"名称:表达式;\"，中间变量用 \"X := 表达式;\"(不输出)，支持 // 注释")
            Text(
                "示例",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ExampleLine("双均线", "MA5:MA(C,5); MA10:MA(C,10);")
            ExampleLine("多空线", "DIFF:EMA(C,12)-EMA(C,26);")
            ExampleLine("乖离率", "BIAS:(C-MA(C,12))/MA(C,12)*100;")
            ExampleLine("金叉计数", "N:COUNT(CROSS(MA(C,5),MA(C,10)),20);")
        }
    }
}

@Composable
private fun HelpLine(title: String, body: String) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExampleLine(label: String, code: String) {
    Row {
        Text(
            "$label  ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            code,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = Purple
            )
        )
    }
}
