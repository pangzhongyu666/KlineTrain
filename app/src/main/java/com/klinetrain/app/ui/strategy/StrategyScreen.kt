package com.klinetrain.app.ui.strategy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.klinetrain.app.data.db.StrategyEntity
import com.klinetrain.app.ui.theme.DownGreen
import com.klinetrain.app.ui.theme.UpRed
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrategyScreen(onBack: () -> Unit) {
    val vm: StrategyViewModel = viewModel()
    val strategies by vm.strategies.collectAsStateWithLifecycle()

    // null=不显示；id==0 的空实体=新建；其余=编辑
    var editing by remember { mutableStateOf<StrategyEntity?>(null) }
    var deleting by remember { mutableStateOf<StrategyEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("战法管理", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { editing = StrategyEntity(name = "") }) {
                        Icon(Icons.Filled.Add, contentDescription = "新建战法")
                    }
                }
            )
        }
    ) { padding ->
        if (strategies.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("创建你的第一个战法，让交易有计划", fontSize = 14.sp, color = Color.Gray)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { editing = StrategyEntity(name = "") }) {
                        Text("+ 新建战法")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                items(strategies, key = { it.strategy.id }) { item ->
                    StrategyCard(
                        item = item,
                        onEdit = { editing = item.strategy },
                        onDelete = { deleting = item.strategy }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    editing?.let { target ->
        StrategyEditDialog(
            initial = target,
            onDismiss = { editing = null },
            onConfirm = { name, desc ->
                if (target.id == 0L) vm.addStrategy(name, desc)
                else vm.updateStrategy(target, name, desc)
                editing = null
            }
        )
    }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除战法") },
            text = { Text("确定删除战法「${target.name}」吗？已归属该战法的记录不会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteStrategy(target)
                    deleting = null
                }) { Text("删除", color = UpRed) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun StrategyCard(
    item: StrategyWithStats,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.strategy.name, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.width(32.dp)) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多", tint = Color.Gray)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("编辑") },
                            onClick = {
                                menuOpen = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("删除", color = UpRed) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            }
                        )
                    }
                }
            }
            if (item.strategy.description.isNotBlank()) {
                Text(
                    item.strategy.description,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell("场次", "${item.sessionCount}", Modifier.weight(1f))
                StatCell(
                    "胜率",
                    String.format(Locale.CHINA, "%.2f", item.winRate) + "%",
                    Modifier.weight(1f)
                )
                StatCell(
                    "平均收益",
                    (if (item.avgReturnPct >= 0) "+" else "") +
                        String.format(Locale.CHINA, "%.2f", item.avgReturnPct) + "%",
                    Modifier.weight(1f),
                    valueColor = if (item.avgReturnPct >= 0) UpRed else DownGreen
                )
                StatCell(
                    "盈亏比",
                    String.format(Locale.CHINA, "%.2f", item.avgProfitLossRatio),
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Unspecified
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = valueColor, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, color = Color.Gray, maxLines = 1)
    }
}

@Composable
private fun StrategyEditDialog(
    initial: StrategyEntity,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String) -> Unit
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var desc by remember(initial.id) { mutableStateOf(initial.description) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "新建战法" else "编辑战法") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("战法名称") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("战法描述(选填)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, desc) },
                enabled = name.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
