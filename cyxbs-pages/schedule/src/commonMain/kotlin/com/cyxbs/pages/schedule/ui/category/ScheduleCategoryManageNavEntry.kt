package com.cyxbs.pages.schedule.ui.category

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.res.ConfigRes
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.NAV_SCHEDULE_CATEGORY_MANAGE
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.schedule.domain.model.ScheduleCategory
import com.cyxbs.pages.schedule.domain.repository.ScheduleSyncResult
import com.cyxbs.pages.schedule.ui.dialog.ScheduleBottomSheet
import com.cyxbs.pages.schedule.ui.dialog.ScheduleConfirmDialog
import com.cyxbs.pages.schedule.ui.todo.ScheduleTodoAccentColor
import com.cyxbs.pages.schedule.ui.todo.ScheduleTodoAddIconColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.painterResource

/** 分组管理页的无参数导航身份。 */
@Serializable
object ScheduleCategoryManageNavArgument : AppNavArgument

/** 邮子清单分组管理入口；CRUD 与排序都通过共享 [ScheduleCategoryCatalog] 写入同一仓库。 */
@AppNav(route = NAV_SCHEDULE_CATEGORY_MANAGE)
class ScheduleCategoryManageNavEntry : AppNavEntry<ScheduleCategoryManageNavArgument>() {
  override fun isNeedLogin(argument: ScheduleCategoryManageNavArgument): Boolean = true

  override fun getContentKey(argument: ScheduleCategoryManageNavArgument): String =
    "schedule_category_manage_singleton"

  @Composable
  override fun Content(argument: ScheduleCategoryManageNavArgument) {
    ScheduleCategoryManagePage(onBack = argument::popBackStack)
  }
}

/**
 * 分组管理页。
 *
 * 页面用本地列表即时响应拖拽，松手后才把变化后的 sortOrder 写入仓库；新增、改名、配色和删除均由仓库
 * 快照反向刷新，不维护第二份长期业务状态。
 */
@Composable
private fun ScheduleCategoryManagePage(onBack: () -> Unit) {
  val colors = LocalAppColors.current
  val catalog = rememberScheduleCategoryCatalog()
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  var orderedCategories by remember { mutableStateOf(catalog.selectableCategories) }
  var showEditor by remember { mutableStateOf(false) }
  var editingCategory by remember { mutableStateOf<ScheduleCategory?>(null) }
  var deletingCategory by remember { mutableStateOf<ScheduleCategory?>(null) }
  var savingOrder by remember { mutableStateOf(false) }

  val dragDropState = rememberScheduleCategoryDragDropState(
    lazyListState = listState,
    onMove = { from, to ->
      if (from in orderedCategories.indices && to in orderedCategories.indices) {
        orderedCategories = orderedCategories.toMutableList().apply {
          add(to, removeAt(from))
        }
      }
    },
    onDragFinished = {
      val newOrder = orderedCategories
      // 先同步标记保存态，阻止 catalog 的旧快照在协程启动前把刚完成的拖拽顺序覆盖回去。
      savingOrder = true
      scope.launch {
        try {
          val result = catalog.reorder(newOrder)
          if (result is ScheduleSyncResult.Failure) {
            toast("顺序已保存到本地，将在网络恢复后同步")
          }
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (_: Throwable) {
          toast("分组顺序保存失败")
        } finally {
          savingOrder = false
        }
      }
    },
  )

  LaunchedEffect(catalog.selectableCategories, dragDropState.draggingItemIndex, savingOrder) {
    if (dragDropState.draggingItemIndex == null && !savingOrder) {
      orderedCategories = catalog.selectableCategories
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.bottomBg)
      .statusBarsPadding(),
  ) {
    Column(Modifier.fillMaxSize()) {
      ScheduleCategoryManageHeader(onBack)
      Text(
        text = "按住左侧点阵拖动排序，分组颜色会同时用于清单和事务的课表展示。",
        color = colors.tvLv2,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 10.dp),
      )
      LazyColumn(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        itemsIndexed(
          items = orderedCategories,
          key = { _, category -> category.id.value },
        ) { index, category ->
          ScheduleCategoryDraggableItem(dragDropState, index) { isDragging ->
            ScheduleCategoryManageRow(
              category = category,
              usageCount = catalog.usageCount(category.id),
              isDragging = isDragging,
              dragHandleModifier = Modifier.scheduleCategoryDragHandle(
                state = dragDropState,
                index = index,
                enabled = !savingOrder,
              ),
              onEdit = {
                editingCategory = category
                showEditor = true
              },
              onDelete = when {
                isFixedScheduleCategory(category) -> null
                catalog.usageCount(category.id) > 0 -> {
                  { toast("仍有 ${catalog.usageCount(category.id)} 项日程使用该分组") }
                }
                else -> ({ deletingCategory = category })
              },
            )
          }
        }
      }
    }
    FloatingActionButton(
      onClick = {
        editingCategory = null
        showEditor = true
      },
      backgroundColor = ScheduleTodoAccentColor,
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .navigationBarsPadding()
        .padding(end = 24.dp, bottom = 28.dp)
        .size(50.dp),
    ) {
      Icon(
        imageVector = Icons.Rounded.Add,
        contentDescription = "新建分组",
        tint = ScheduleTodoAddIconColor,
        modifier = Modifier.size(31.dp),
      )
    }
  }

  ScheduleCategoryEditorSheet(
    show = showEditor,
    category = editingCategory,
    allCategories = catalog.selectableCategories,
    onDismiss = { showEditor = false },
    onSave = { name, color ->
      val origin = editingCategory
      scope.launch {
        try {
          val result = if (origin == null) {
            catalog.create(name, color)
          } else {
            catalog.save(origin.copy(name = name, color = color))
          }
          showEditor = false
          when (result) {
            is ScheduleSyncResult.Failure -> toast("已保存到本地，将在网络恢复后同步")
            null -> toast("当前无法保存分组")
            else -> Unit
          }
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (_: Throwable) {
          toast("分组保存失败")
        }
      }
    },
  )

  val pendingDelete = deletingCategory
  ScheduleConfirmDialog(
    show = pendingDelete != null,
    title = "删除分组",
    message = pendingDelete?.let { "确定删除“${it.name}”吗？" }.orEmpty(),
    confirmText = "删除",
    onConfirm = {
      val category = pendingDelete ?: return@ScheduleConfirmDialog
      scope.launch {
        try {
          val result = catalog.delete(category.id)
          if (result is ScheduleSyncResult.Failure) {
            toast("删除已保存到本地，将在网络恢复后同步")
          }
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (_: Throwable) {
          toast("分组删除失败")
        }
      }
    },
    onDismiss = { deletingCategory = null },
  )
}

/** 分组管理页标题栏，返回图标沿用项目统一资源。 */
@Composable
private fun ScheduleCategoryManageHeader(onBack: () -> Unit) {
  val colors = LocalAppColors.current
  Column(Modifier.fillMaxWidth().padding(top = 13.dp)) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(37.dp)
        .padding(horizontal = 16.dp),
    ) {
      Box(
        modifier = Modifier
          .width(22.dp)
          .height(37.dp)
          .align(Alignment.CenterStart)
          .clickableNoIndicator(onClick = onBack),
        contentAlignment = Alignment.CenterStart,
      ) {
        Icon(
          painter = painterResource(ConfigRes.configIcBack()),
          contentDescription = "返回",
          tint = colors.tvLv1,
          modifier = Modifier.width(9.dp).height(19.dp),
        )
      }
      Text(
        text = "分组管理",
        color = colors.tvLv1,
        fontSize = 21.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.05.sp,
        modifier = Modifier.align(Alignment.Center),
      )
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.tvLv4.copy(alpha = 0.1f)))
  }
}

/** 单个分组行：点阵只负责拖动，名称和右侧按钮仍保持普通点击语义。 */
@Composable
private fun ScheduleCategoryManageRow(
  category: ScheduleCategory,
  usageCount: Int,
  isDragging: Boolean,
  dragHandleModifier: Modifier,
  onEdit: () -> Unit,
  onDelete: (() -> Unit)?,
) {
  val colors = LocalAppColors.current
  val colorValue = remember(category.color) {
    decodeScheduleCategoryColor(category.color) ?: ScheduleDefaultCategoryColorValue
  }
  val isLight = MaterialTheme.colors.isLight
  Surface(
    color = colors.topBg,
    shape = RoundedCornerShape(14.dp),
    elevation = if (isDragging) 6.dp else 1.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ScheduleCategoryDragHandle(dragHandleModifier)
      Surface(
        color = colorValue.backgroundColor(isLight),
        contentColor = colorValue.contentColor(isLight),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.size(40.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text("Aa", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
      }
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Text(
          text = category.name,
          color = colors.tvLv1,
          fontSize = 16.sp,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
          text = when {
            isFixedScheduleCategory(category) -> "内置分组 · $usageCount 项日程"
            else -> "$usageCount 项日程"
          },
          color = colors.tvLv2,
          fontSize = 12.sp,
        )
      }
      IconButton(onClick = onEdit) {
        Icon(Icons.Outlined.Edit, contentDescription = "编辑${category.name}", tint = colors.tvLv2)
      }
      if (onDelete != null) {
        IconButton(onClick = onDelete) {
          Icon(Icons.Outlined.Delete, contentDescription = "删除${category.name}", tint = MaterialTheme.colors.error)
        }
      } else {
        Spacer(Modifier.size(48.dp))
      }
    }
  }
}

/** 2×3 点阵拖动手柄；只有该区域长按才会启动列表排序。 */
@Composable
private fun ScheduleCategoryDragHandle(modifier: Modifier) {
  val color = LocalAppColors.current.tvLv3
  Box(
    modifier = modifier
      .size(48.dp)
      .semantics { contentDescription = "按住拖动调整顺序" },
    contentAlignment = Alignment.Center,
  ) {
    Canvas(Modifier.size(width = 16.dp, height = 24.dp)) {
      val radius = 1.6.dp.toPx()
      val xPositions = listOf(size.width * 0.32f, size.width * 0.68f)
      val yPositions = listOf(size.height * 0.24f, size.height * 0.5f, size.height * 0.76f)
      xPositions.forEach { x ->
        yPositions.forEach { y -> drawCircle(color = color, radius = radius, center = Offset(x, y)) }
      }
    }
  }
}

/** 新建与编辑共用的底部表单；名称即时校验，颜色只允许选择预置的完整 JSON 颜色组。 */
@Composable
private fun ScheduleCategoryEditorSheet(
  show: Boolean,
  category: ScheduleCategory?,
  allCategories: List<ScheduleCategory>,
  onDismiss: () -> Unit,
  onSave: (name: String, color: String?) -> Unit,
) {
  if (!show) return
  var name by remember(category?.id) { mutableStateOf(category?.name.orEmpty()) }
  var selectedColor by remember(category?.id) {
    mutableStateOf(
      decodeScheduleCategoryColor(category?.color) ?: ScheduleCategoryColorPresets.first().value,
    )
  }
  val normalizedName = name.trim()
  val duplicate = normalizedName.isNotEmpty() && allCategories.any { other ->
    other.id != category?.id && other.name.trim().equals(normalizedName, ignoreCase = true)
  }
  val canSave = normalizedName.isNotEmpty() && !duplicate
  val colors = LocalAppColors.current

  ScheduleBottomSheet(show = true, onDismiss = onDismiss) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
      Text(
        text = if (category == null) "新建分组" else "编辑分组",
        color = colors.tvLv1,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
      )
      Spacer(Modifier.height(16.dp))
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("分组名称") },
        singleLine = true,
        isError = duplicate,
        colors = TextFieldDefaults.outlinedTextFieldColors(
          textColor = colors.tvLv2,
          cursorColor = colors.positive,
        ),
        modifier = Modifier.fillMaxWidth(),
      )
      if (duplicate) {
        Text(
          text = "已存在同名分组",
          color = MaterialTheme.colors.error,
          fontSize = 12.sp,
          modifier = Modifier.padding(start = 12.dp, top = 4.dp),
        )
      }
      Spacer(Modifier.height(18.dp))
      Text("课表配色", color = colors.tvLv1, fontSize = 15.sp, fontWeight = FontWeight.Medium)
      Spacer(Modifier.height(10.dp))
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        ScheduleCategoryColorPresets.forEach { preset ->
          ScheduleCategoryColorOption(
            label = preset.label,
            value = preset.value,
            selected = selectedColor == preset.value,
            onClick = { selectedColor = preset.value },
          )
        }
      }
      Spacer(Modifier.height(22.dp))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onDismiss) { Text("取消") }
        Spacer(Modifier.width(8.dp))
        Button(
          onClick = {
            onSave(normalizedName, selectedColor.encodeScheduleCategoryColor())
          },
          enabled = canSave,
          colors = ButtonDefaults.buttonColors(
            backgroundColor = colors.positive,
            contentColor = Color.White,
            disabledBackgroundColor = colors.negative,
            disabledContentColor = colors.tvLv2.copy(alpha = 0.45F),
          ),
        ) {
          Text("保存")
        }
      }
    }
  }
}

/** 用 Aa 同时预览背景色和字体色，选中时在右下显示小勾。 */
@Composable
private fun ScheduleCategoryColorOption(
  label: String,
  value: ScheduleCategoryColorValue,
  selected: Boolean,
  onClick: () -> Unit,
) {
  val colors = LocalAppColors.current
  val isLight = MaterialTheme.colors.isLight
  Surface(
    color = value.backgroundColor(isLight),
    contentColor = value.contentColor(isLight),
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(
      width = if (selected) 2.dp else 1.dp,
      color = if (selected) MaterialTheme.colors.primary else colors.tvLv4.copy(alpha = 0.18f),
    ),
    modifier = Modifier
      .size(48.dp)
      .semantics { contentDescription = "$label 配色" }
      .clickableNoIndicator(onClick = onClick),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text("Aa", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
      if (selected) {
        Icon(
          imageVector = Icons.Rounded.Check,
          contentDescription = null,
          tint = value.contentColor(isLight),
          modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp).size(13.dp),
        )
      }
    }
  }
}
