package com.archiekuo.localmusic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val NameW = 88.dp
private val CountW = 52.dp

@Composable
fun ManageScreen(vm: LibraryViewModel) {
    val p = LocalPal.current
    var editing by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var checked by remember { mutableStateOf(setOf<String>()) }
    var confirm by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newErr by remember { mutableStateOf<String?>(null) }

    fun commitNew() {
        if (newName.isBlank()) return
        val e = vm.addCategory(newName)
        if (e == null) {
            newName = ""
            newErr = null
        } else {
            newErr = e
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("管理分類", style = SerifBold, color = p.text, fontSize = 24.sp, modifier = Modifier.weight(1f))
            val can = checked.isNotEmpty()
            Row(
                Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(enabled = can) { confirm = true }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Ico(IcTrash, if (can) p.red else p.muted.copy(alpha = 0.5f), 18.dp)
                Text("刪除", color = if (can) p.red else p.muted.copy(alpha = 0.5f), fontSize = 14.sp)
            }
        }
        if (confirm) {
            ConfirmBar(
                message = "刪除 ${checked.size} 個分類？裡面的歌不會被刪掉",
                okText = "確定刪除",
                onCancel = { confirm = false },
                onOk = {
                    vm.deleteCategories(checked)
                    checked = emptySet()
                    confirm = false
                    editing = null
                },
            )
        }
        Text(
            "先勾選再按「刪除」。平常是純文字，點一格才能改。",
            color = p.muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 10.dp),
        )

        Row(
            Modifier.padding(horizontal = 16.dp).height(34.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp))
            Text("名稱", color = p.muted, fontSize = 12.sp, modifier = Modifier.width(NameW))
            Text("自動規則", color = p.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("首數", color = p.muted, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.width(CountW))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))

        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(vm.cats, key = { it.id }) { c ->
                CategoryRow(
                    vm = vm,
                    c = c,
                    count = vm.byCat[c.id]?.size ?: 0,
                    editing = editing,
                    onEdit = { editing = it },
                    checked = c.id in checked,
                    onCheck = { checked = if (c.id in checked) checked - c.id else checked + c.id },
                )
            }
            item(key = "new") {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(horizontal = 16.dp).heightIn(min = 52.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Ico(IcPlus, p.muted, 18.dp) }
                        Box(Modifier.width(NameW).padding(end = 6.dp)) {
                            BasicTextField(
                                value = newName,
                                onValueChange = {
                                    newName = it
                                    newErr = null
                                },
                                singleLine = true,
                                textStyle = TextStyle(color = if (newErr != null) p.red else p.text, fontSize = 15.sp),
                                cursorBrush = SolidColor(p.accent),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { commitNew() }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (newErr != null) p.red.copy(alpha = 0.08f) else Color.Transparent)
                                    .border(1.5.dp, if (newErr != null) p.red else p.border, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp)
                                    .onFocusChanged { if (!it.isFocused && newName.isNotBlank()) commitNew() },
                                decorationBox = { inner ->
                                    Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                                        if (newName.isEmpty()) Text("新分類", color = p.muted, fontSize = 15.sp)
                                        inner()
                                    }
                                },
                            )
                        }
                        Text("填完自動存", color = p.muted.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                    if (newErr != null) {
                        Text(newErr ?: "", color = p.red, fontSize = 12.sp, modifier = Modifier.padding(start = 60.dp, bottom = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    vm: LibraryViewModel,
    c: Category,
    count: Int,
    editing: Pair<String, Int>?,
    onEdit: (Pair<String, Int>?) -> Unit,
    checked: Boolean,
    onCheck: () -> Unit,
) {
    val p = LocalPal.current
    val editName = editing == (c.id to 0)
    val editRule = editing == (c.id to 1)
    var nameErr by remember(c.id) { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .background(if (editName || editRule) p.selected.copy(alpha = 0.6f) else Color.Transparent),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp).heightIn(min = 52.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckBox(checked = checked, enabled = !c.isSystem, onToggle = onCheck)

            // 名稱格
            Box(Modifier.width(NameW).padding(end = 6.dp)) {
                if (editName && !c.isSystem) {
                    var text by remember(c.id, c.name) { mutableStateOf(c.name) }
                    val fr = remember { FocusRequester() }
                    LaunchedEffect(Unit) { fr.requestFocus() }
                    BasicTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            nameErr = null
                        },
                        singleLine = true,
                        textStyle = TextStyle(color = if (nameErr != null) p.red else p.text, fontSize = 15.sp),
                        cursorBrush = SolidColor(p.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            val e = vm.renameCategory(c.id, text)
                            if (e == null) onEdit(null) else nameErr = e
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(p.surface)
                            .border(1.5.dp, if (nameErr != null) p.red else p.accent, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp)
                            .focusRequester(fr),
                        decorationBox = { inner ->
                            Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) { inner() }
                        },
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clickable(enabled = !c.isSystem) {
                                nameErr = null
                                onEdit(c.id to 0)
                            },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            c.name,
                            color = if (c.isSystem) p.amber else p.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // 規則格
            Box(Modifier.weight(1f).padding(end = 6.dp)) {
                if (editRule) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(p.surface)
                            .border(1.5.dp, p.accent, RoundedCornerShape(8.dp))
                            .clickable(enabled = !c.isSystem) { menu = !menu }
                            .padding(start = 10.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            c.ruleText(),
                            color = p.text,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (!c.isSystem) Ico(IcChevDown, p.accent, 16.dp)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        RuleType.values().filter { it != RuleType.FREQUENT }.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.label) },
                                onClick = {
                                    vm.setRule(c.id, t, if (t == RuleType.KEYWORDS) c.param else "")
                                    menu = false
                                },
                            )
                        }
                    }
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clickable {
                                menu = false
                                onEdit(c.id to 1)
                            },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            c.ruleText(),
                            color = if (c.rule == RuleType.MANUAL) p.muted else p.sub,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Text(
                fmtCount(count),
                color = p.text,
                fontSize = 15.sp,
                style = NumStyle,
                textAlign = TextAlign.End,
                modifier = Modifier.width(CountW),
            )
        }

        val ne = nameErr
        if (ne != null) {
            Text(ne, color = p.red, fontSize = 12.sp, modifier = Modifier.padding(start = 60.dp, bottom = 8.dp))
        }

        if (editRule && (c.rule == RuleType.KEYWORDS || c.rule == RuleType.FREQUENT)) {
            var ptext by remember(c.id, c.rule, c.param) { mutableStateOf(c.param) }
            val fr = remember { FocusRequester() }
            LaunchedEffect(c.id, c.rule) { fr.requestFocus() }
            Column(Modifier.padding(start = 60.dp, end = 16.dp, bottom = 10.dp)) {
                Text(
                    if (c.rule == RuleType.KEYWORDS) "資料夾名稱或關鍵字，用逗號分開" else "播放幾次以上算常聽",
                    color = p.muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                BasicTextField(
                    value = ptext,
                    onValueChange = { ptext = it },
                    singleLine = c.rule == RuleType.FREQUENT,
                    textStyle = TextStyle(color = p.text, fontSize = 14.sp),
                    cursorBrush = SolidColor(p.accent),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        keyboardType = if (c.rule == RuleType.FREQUENT) KeyboardType.Number else KeyboardType.Text,
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        vm.setRule(c.id, c.rule, ptext)
                        onEdit(null)
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(p.surface)
                        .border(1.5.dp, p.accent, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                        .focusRequester(fr),
                )
            }
        }
    }
}
