package com.cyxbs.functions.code.npm.bundle.manager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.functions.code.npm.NpmBundleManager
import com.cyxbs.functions.code.npm.model.NpmBundleInfo
import com.cyxbs.functions.code.npm.model.NpmBundleSnapshot
import com.cyxbs.functions.code.npm.model.NpmPackageId
import com.cyxbs.functions.code.npm.model.NpmPackageSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * npm Bundle 管理列表路由参数。
 *
 * 列表不携带业务参数；Bundle 详情使用独立的 [NpmBundleDetailNavArgument]，避免同一个
 * AppNavArgument 同时表达列表与详情两种目的地。
 */
@Serializable
data object NpmBundleManagerNavArgument : AppNavArgument

/**
 * 查看和维护默认 npm 包池的跨平台 Compose 页面。
 *
 * 页面按包名聚合不同版本：列表只展示最新版本，详情页再提供完整历史版本和依赖关系。页面只通过
 * [NpmBundleManager] 操作数据，不直接访问缓存目录，因此 Android、iOS 与 Desktop 使用相同语义。
 */
@AppNav(route = "code/npm-bundles")
class NpmBundleManagerNavEntry : AppNavEntry<NpmBundleManagerNavArgument>() {

  override fun isNeedLogin(argument: NpmBundleManagerNavArgument): Boolean = false

  /** 声明为列表 pane；详情 Entry 使用相同 sceneKey 后可在宽屏与其自动并排。 */
  @OptIn(ExperimentalMaterial3AdaptiveApi::class)
  override fun buildMetadata(argument: NpmBundleManagerNavArgument): Map<String, Any> {
    return ListDetailSceneStrategy.listPane(
      sceneKey = BUNDLE_LIST_DETAIL_SCENE_KEY,
      detailPlaceholder = { BundleDetailPlaceholder() },
    )
  }

  @OptIn(ExperimentalMaterial3AdaptiveApi::class)
  @Composable
  override fun Content(argument: NpmBundleManagerNavArgument) {
    val manager = remember { NpmBundleManager.Default }
    val coroutineScope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<NpmBundleSnapshot?>(null) }
    var showClearAllConfirmation by remember { mutableStateOf(false) }
    var isWorking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<BundlePageMessage?>(null) }

    /** 串行执行管理操作，完成后统一刷新快照。 */
    fun launchAction(block: suspend () -> String) {
      coroutineScope.launch {
        isWorking = true
        try {
          message = BundlePageMessage(block(), isError = false)
          snapshot = manager.getSnapshot()
        } catch (throwable: Throwable) {
          if (throwable is CancellationException) throw throwable
          message = BundlePageMessage(throwable.toDisplayMessage(), isError = true)
        } finally {
          isWorking = false
        }
      }
    }

    LaunchedEffect(Unit) {
      isWorking = true
      try {
        snapshot = manager.getSnapshot()
      } catch (throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        message = BundlePageMessage(throwable.toDisplayMessage(), isError = true)
      } finally {
        isWorking = false
      }
    }

    val packageGroups = remember(snapshot) { snapshot?.toPackageGroups().orEmpty() }
    val paneDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    val canShowMultiplePanes = paneDirective.maxHorizontalPartitions > 1 ||
      paneDirective.maxVerticalPartitions > 1
    // 每个 Scene Pane 都能读取到整屏 Insets。双栏时只消费自己贴近屏幕边缘的一侧，避免左栏
    // 错误叠加右侧导航栏宽度，同时让列表避开横屏设备左侧的摄像头与刘海区域。
    val paneSafeDrawingInsets = if (canShowMultiplePanes) {
      WindowInsets.safeDrawing.only(
        WindowInsetsSides.Start + WindowInsetsSides.Top + WindowInsetsSides.Bottom
      )
    } else {
      WindowInsets.safeDrawing
    }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colors.background)
        .windowInsetsPadding(paneSafeDrawingInsets),
    ) {
      BundleToolbar(
        title = "Bundle 管理",
        showBackButton = true,
        isWorking = isWorking,
        onBack = argument::popBackStack,
      )

      message?.let { BundleMessage(it) }

      Box(modifier = Modifier.fillMaxWidth().weight(1F)) {
        when {
          snapshot == null && isWorking -> CircularProgressIndicator(Modifier.align(Alignment.Center))
          else -> BundleList(
            snapshot = snapshot ?: NpmBundleSnapshot(emptyList(), 0, 0),
            groups = packageGroups,
            isWorking = isWorking,
            onSelectBundle = { NpmBundleDetailNavArgument.from(it.latest.id).navigate() },
            onClearAll = { showClearAllConfirmation = true },
          )
        }
      }
    }

    if (showClearAllConfirmation) {
      BundleConfirmationDialog(
        pending = BundleConfirmation.ClearAll,
        enabled = !isWorking,
        onDismiss = { showClearAllConfirmation = false },
        onConfirm = {
          showClearAllConfirmation = false
          launchAction {
            val result = manager.clearAll()
            "已清空 ${result.deletedBundleCount} 个 Bundle，失效 ${result.invalidatedEntryCount} 个入口。"
          }
        },
      )
    }
  }
}

/** 页面顶栏；标题始终居中，返回图标在宽屏双栏详情中隐藏。 */
@Composable
internal fun BundleToolbar(
  title: String,
  showBackButton: Boolean,
  isWorking: Boolean,
  onBack: () -> Unit,
) {
  Box(
    modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
  ) {
    if (showBackButton) {
      IconButton(modifier = Modifier.align(Alignment.CenterStart), onClick = onBack) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "返回",
          tint = MaterialTheme.colors.onSurface,
        )
      }
    }
    Text(
      text = title,
      modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
      style = MaterialTheme.typography.h6,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    if (isWorking) {
      CircularProgressIndicator(
        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).size(18.dp),
        strokeWidth = 2.dp,
      )
    }
  }
  Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.08F))
}

/** 操作结果使用独立提示条展示，避免文本夹在工具栏和列表之间。 */
@Composable
internal fun BundleMessage(message: BundlePageMessage) {
  val color = if (message.isError) MaterialTheme.colors.error else MaterialTheme.colors.primary
  Surface(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    color = color.copy(alpha = 0.10F),
    shape = RoundedCornerShape(10.dp),
  ) {
    Text(
      text = message.text,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      color = color,
      style = MaterialTheme.typography.body2,
    )
  }
}

/** Bundle 列表按包名聚合，每个包只展示最新版本与历史版本数量。 */
@Composable
private fun BundleList(
  snapshot: NpmBundleSnapshot,
  groups: List<BundlePackageGroup>,
  isWorking: Boolean,
  onSelectBundle: (BundlePackageGroup) -> Unit,
  onClearAll: () -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      BundlePoolSummary(
        snapshot = snapshot,
        packageCount = groups.size,
        isWorking = isWorking,
        onClearAll = onClearAll,
      )
    }

    if (groups.isEmpty()) {
      item { EmptyBundlePool() }
    } else {
      items(groups, key = BundlePackageGroup::name) { group ->
        BundlePackageCard(group = group, onClick = { onSelectBundle(group) })
      }
    }
  }
}

/** 包池统计只保留需要快速判断的四项数据。 */
@Composable
private fun BundlePoolSummary(
  snapshot: NpmBundleSnapshot,
  packageCount: Int,
  isWorking: Boolean,
  onClearAll: () -> Unit,
) {
  // 汇总角色按包名而非包坐标去重，历史版本只进入历史版本与 Bundle 总计两项。
  val entryPackageNames = snapshot.bundles.asSequence()
    .filter(NpmBundleInfo::isEntryBundle)
    .map { it.id.name }
    .toSet()
  // 同一个包可能既是某个入口的根，又被另一个入口依赖；入口角色优先，避免两项重复统计。
  val dependencyPackageNames = snapshot.bundles.asSequence()
    .flatMap { it.dependencies.asSequence() }
    .map(NpmPackageId::name)
    .filterNot(entryPackageNames::contains)
    .toSet()
  val historyVersionCount = (snapshot.bundles.size - packageCount).coerceAtLeast(0)
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.10F),
    elevation = 0.dp,
  ) {
    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1F)) {
          Text("本地 Bundle 池", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
          Text(
            text = "$packageCount 个包名 · 共占用 ${snapshot.totalSizeBytes.toDisplaySize()}",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.68F),
          )
        }
        OutlinedButton(
          enabled = !isWorking && snapshot.bundles.isNotEmpty(),
          onClick = onClearAll,
          colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.error),
        ) {
          Text("清空")
        }
      }
      // 四项数据使用相同权重保持横向对齐，方便直接比较包池构成。
      Row(modifier = Modifier.fillMaxWidth()) {
        SummaryMetric("Bundle 总计", snapshot.bundles.size.toString(), Modifier.weight(1F))
        SummaryMetric("入口去重", entryPackageNames.size.toString(), Modifier.weight(1F))
        SummaryMetric(
          "依赖去重",
          dependencyPackageNames.size.toString(),
          Modifier.weight(1F),
        )
        SummaryMetric("历史版本", historyVersionCount.toString(), Modifier.weight(1F))
      }
    }
  }
}

/** 汇总卡片中的紧凑指标。 */
@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
  Column(modifier = modifier) {
    Text(value, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
    Text(
      label,
      style = MaterialTheme.typography.caption,
      color = MaterialTheme.colors.onSurface.copy(alpha = 0.58F),
    )
  }
}

/** 空包池状态。 */
@Composable
private fun EmptyBundlePool() {
  Column(
    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text("暂无 Bundle", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
    Text(
      "运行 npm 入口后，下载的包会显示在这里",
      style = MaterialTheme.typography.body2,
      color = MaterialTheme.colors.onSurface.copy(alpha = 0.58F),
    )
  }
}

/** 宽屏尚未选择 Bundle 时显示在右侧详情栏的占位内容。 */
@Composable
internal fun BundleDetailPlaceholder() {
  Box(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = "选择左侧 Bundle 查看详情",
      style = MaterialTheme.typography.body1,
      color = MaterialTheme.colors.onSurface.copy(alpha = 0.58F),
    )
  }
}

/** 详情参数指向的 Bundle 已被清理或不存在时展示稳定空状态，返回仍由 AppNav 处理。 */
@Composable
internal fun MissingBundleDetail(id: NpmPackageId?) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(
      text = id?.let { "未找到 ${it.toDisplayText()}" } ?: "Bundle 参数无效",
      color = MaterialTheme.colors.onSurface.copy(alpha = 0.62F),
      style = MaterialTheme.typography.body1,
    )
  }
}

/** 同名包的聚合卡片，只展示最新版本，历史版本统一收进详情页。 */
@Composable
private fun BundlePackageCard(group: BundlePackageGroup, onClick: () -> Unit) {
  val latest = group.latest
  val containsEntry = group.versions.any(NpmBundleInfo::isEntryBundle)
  Card(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    shape = RoundedCornerShape(16.dp),
    elevation = 1.dp,
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
          text = group.name,
          modifier = Modifier.weight(1F).padding(end = 8.dp),
          style = MaterialTheme.typography.subtitle1,
          fontWeight = FontWeight.SemiBold,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        if (containsEntry) StatusChip("入口", MaterialTheme.colors.primary)
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
      ) {
        Column(
          modifier = Modifier.weight(1F).padding(end = 12.dp),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          Text(
            text = "最新版本  ${latest.id.version}",
            style = MaterialTheme.typography.body2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            if (group.historyCount == 0) "没有历史版本" else "${group.historyCount} 个历史版本",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.58F),
          )
        }
        Text(
          latest.sizeBytes.toDisplaySize(),
          style = MaterialTheme.typography.body2,
          color = MaterialTheme.colors.onSurface.copy(alpha = 0.68F),
          maxLines = 1,
          softWrap = false,
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusChip(
          text = if (latest.isAvailable) "缓存正常" else "缓存异常",
          color = if (latest.isAvailable) MaterialTheme.colors.primary else MaterialTheme.colors.error,
        )
        if (latest.isInUse) StatusChip("使用中", MaterialTheme.colors.secondary)
      }
    }
  }
}

/** 包详情：先选择具体版本，再查看该版本的属性、依赖关系和维护操作。 */
@Composable
internal fun BundleDetail(
  group: BundlePackageGroup,
  selected: NpmBundleInfo,
  enabled: Boolean,
  onSelectVersion: (NpmPackageId) -> Unit,
  onSelectBundle: (NpmPackageId) -> Unit,
  onRedownload: () -> Unit,
  onDelete: () -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item { PackageOverview(group) }
    item { SectionTitle("版本", group.versions.size) }
    items(group.versions, key = { "version:${it.id.version}" }) { bundle ->
      BundleVersionCard(
        bundle = bundle,
        isLatest = bundle.id == group.latest.id,
        isSelected = bundle.id == selected.id,
        onClick = { onSelectVersion(bundle.id) },
      )
    }
    item {
      BundleVersionDetail(
        bundle = selected,
        enabled = enabled,
        onRedownload = onRedownload,
        onDelete = onDelete,
      )
    }
    item { SectionTitle("直接依赖", selected.dependencies.size) }
    if (selected.dependencies.isEmpty()) {
      item { EmptyRelation("该版本没有直接依赖") }
    } else {
      items(selected.dependencies, key = { "dependency:${it.name}@${it.version}" }) {
        BundleRelation(it, onSelectBundle)
      }
    }
    item { SectionTitle("被依赖", selected.dependents.size) }
    if (selected.dependents.isEmpty()) {
      item { EmptyRelation("没有其他 Bundle 直接依赖该版本") }
    } else {
      items(selected.dependents, key = { "dependent:${it.name}@${it.version}" }) {
        BundleRelation(it, onSelectBundle)
      }
    }
  }
}

/** 包级概览，大小为该包全部历史版本的合计。 */
@Composable
private fun PackageOverview(group: BundlePackageGroup) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.08F),
    elevation = 0.dp,
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text("包概览", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
      Text(
        text = group.name,
        style = MaterialTheme.typography.body2,
        fontWeight = FontWeight.Medium,
      )
      Text(
        "${group.versions.size} 个版本 · 共 ${group.totalSizeBytes.toDisplaySize()}",
        style = MaterialTheme.typography.body2,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.68F),
      )
    }
  }
}

/** 可切换的版本卡片，最新、入口、使用中状态均在同一行表达。 */
@Composable
private fun BundleVersionCard(
  bundle: NpmBundleInfo,
  isLatest: Boolean,
  isSelected: Boolean,
  onClick: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    shape = RoundedCornerShape(14.dp),
    backgroundColor = if (isSelected) {
      MaterialTheme.colors.primary.copy(alpha = 0.12F)
    } else {
      MaterialTheme.colors.surface
    },
    elevation = if (isSelected) 0.dp else 1.dp,
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
          bundle.id.version,
          modifier = Modifier.weight(1F),
          style = MaterialTheme.typography.subtitle1,
          fontWeight = FontWeight.SemiBold,
        )
        if (isLatest) StatusChip("最新", MaterialTheme.colors.primary)
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (bundle.isEntryBundle) StatusChip("入口", MaterialTheme.colors.secondary)
        if (bundle.isInUse) StatusChip("使用中", MaterialTheme.colors.secondary)
        if (!bundle.isAvailable) StatusChip("缓存异常", MaterialTheme.colors.error)
      }
      Text(
        "${bundle.sizeBytes.toDisplaySize()} · 下载于 ${bundle.downloadedAtEpochMillis.toDisplayDate()}",
        style = MaterialTheme.typography.caption,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.58F),
      )
    }
  }
}

/** 当前选中版本的完整状态和维护操作。 */
@Composable
private fun BundleVersionDetail(
  bundle: NpmBundleInfo,
  enabled: Boolean,
  onRedownload: () -> Unit,
  onDelete: () -> Unit,
) {
  Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), elevation = 1.dp) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text("版本信息", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
      DetailRow("版本", bundle.id.version)
      DetailRow("来源", bundle.source.toDisplayText())
      DetailRow("大小", bundle.sizeBytes.toDisplaySize())
      DetailRow("下载日期", bundle.downloadedAtEpochMillis.toDisplayDate())
      DetailRow("最近加载", bundle.lastLoadedAtEpochMillis.toDisplayDate())
      DetailRow("缓存状态", if (bundle.isAvailable) "正常" else "文件缺失或损坏")
      DetailRow("运行状态", if (bundle.isInUse) "正在使用" else "未使用")
      DetailRow("入口 Bundle", if (bundle.isEntryBundle) "是" else "否")
      if (bundle.entryNames.isNotEmpty()) {
        Text(
          text = "入口名称",
          style = MaterialTheme.typography.body2,
          color = MaterialTheme.colors.onSurface.copy(alpha = 0.58F),
        )
        bundle.entryNames.forEach { entryName ->
          Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.05F),
            shape = RoundedCornerShape(8.dp),
          ) {
            Text(
              text = entryName,
              modifier = Modifier.padding(10.dp),
              style = MaterialTheme.typography.caption,
            )
          }
        }
      }
      Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.08F))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = enabled, onClick = onRedownload) {
          Text("重新下载")
        }
        OutlinedButton(
          enabled = enabled && !bundle.isInUse,
          onClick = onDelete,
          colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.error),
        ) {
          Text("删除此版本")
        }
      }
      Text(
        "重新下载会原子替换本地文件，不影响正在运行的实例；下次创建 Runtime 时生效。",
        style = MaterialTheme.typography.caption,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.58F),
      )
    }
  }
}

/** 左右对齐的详情字段，减少连续长句造成的阅读负担。 */
@Composable
private fun DetailRow(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
    Text(
      text = label,
      modifier = Modifier.weight(0.35F),
      style = MaterialTheme.typography.body2,
      color = MaterialTheme.colors.onSurface.copy(alpha = 0.58F),
    )
    Text(
      text = value,
      modifier = Modifier.weight(0.65F),
      style = MaterialTheme.typography.body2,
    )
  }
}

/** 详情区分段标题。 */
@Composable
private fun SectionTitle(title: String, count: Int) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(title, modifier = Modifier.weight(1F), style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
    Text(
      count.toString(),
      style = MaterialTheme.typography.caption,
      color = MaterialTheme.colors.onSurface.copy(alpha = 0.58F),
    )
  }
}

/** 依赖为空时使用弱提示，避免误认为页面尚未加载完成。 */
@Composable
private fun EmptyRelation(text: String) {
  Text(
    text = text,
    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    style = MaterialTheme.typography.body2,
    color = MaterialTheme.colors.onSurface.copy(alpha = 0.58F),
  )
}

/** 可继续钻取的直接依赖或反向依赖行。 */
@Composable
private fun BundleRelation(id: NpmPackageId, onSelectBundle: (NpmPackageId) -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth().clickable { onSelectBundle(id) },
    shape = RoundedCornerShape(14.dp),
    elevation = 1.dp,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1F), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(id.name, style = MaterialTheme.typography.body2, fontWeight = FontWeight.Medium)
        Text(
          id.version,
          style = MaterialTheme.typography.caption,
          color = MaterialTheme.colors.onSurface.copy(alpha = 0.58F),
        )
      }
      Text("查看", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.primary)
    }
  }
}

/** 小型状态标签，不引入 Material3 Chip 依赖。 */
@Composable
private fun StatusChip(text: String, color: Color) {
  Surface(color = color.copy(alpha = 0.12F), shape = RoundedCornerShape(50)) {
    Text(
      text = text,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
      style = MaterialTheme.typography.caption,
      color = color,
      fontWeight = FontWeight.Medium,
    )
  }
}

/** 破坏性操作统一使用错误色确认按钮。 */
@Composable
internal fun BundleConfirmationDialog(
  pending: BundleConfirmation,
  enabled: Boolean,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = { if (enabled) onDismiss() },
    title = {
      Text(
        when (pending) {
          BundleConfirmation.ClearAll -> "清空全部 Bundle？"
          is BundleConfirmation.Delete -> "删除 ${pending.bundle.id.toDisplayText()}？"
        },
      )
    },
    text = {
      Text(
        when (pending) {
          BundleConfirmation.ClearAll ->
            "全部入口图和 Bundle 文件都会删除；仍有 Runtime 运行时操作会被拒绝。"
          is BundleConfirmation.Delete -> pending.bundle.deleteWarning()
        },
        style = MaterialTheme.typography.body2,
      )
    },
    confirmButton = {
      Button(
        enabled = enabled,
        onClick = onConfirm,
        colors = ButtonDefaults.buttonColors(
          backgroundColor = MaterialTheme.colors.error,
          contentColor = MaterialTheme.colors.onError,
        ),
      ) {
        Text("确认删除")
      }
    },
    dismissButton = {
      TextButton(enabled = enabled, onClick = onDismiss) {
        Text("取消")
      }
    },
  )
}

/** 将平铺快照按包名分组；管理器已保证同名版本按 npm 语义版本从新到旧排列。 */
internal fun NpmBundleSnapshot.toPackageGroups(): List<BundlePackageGroup> {
  return bundles.groupBy { it.id.name }
    .map { (name, versions) -> BundlePackageGroup(name, versions) }
    .sortedWith(
      compareByDescending<BundlePackageGroup> { group -> group.versions.any(NpmBundleInfo::isEntryBundle) }
        .thenBy(BundlePackageGroup::name),
    )
}

/** 同名 npm 包的所有本地版本，首项固定为语义版本最新项。 */
internal data class BundlePackageGroup(
  val name: String,
  val versions: List<NpmBundleInfo>,
) {
  val latest: NpmBundleInfo get() = versions.first()
  val historyCount: Int get() = versions.size - 1
  val totalSizeBytes: Long get() = versions.sumOf(NpmBundleInfo::sizeBytes)
}

/** 删除确认文案明确提示会失效的入口数量和后续 GC。 */
private fun NpmBundleInfo.deleteWarning(): String {
  val entryWarning = if (isEntryBundle || dependents.isNotEmpty()) {
    "引用该 Bundle 的入口图会失效，相关孤立依赖会同时被 GC；下次使用入口时重新下载。"
  } else {
    "该 Bundle 当前没有入口或反向依赖，删除后会从本地包池移除。"
  }
  return "$entryWarning 当前依赖 ${dependencies.size} 个 Bundle，" +
    "被 ${dependents.size} 个 Bundle 直接依赖。"
}

/** 管理操作失败时展示稳定异常类型和消息。 */
internal fun Throwable.toDisplayMessage(): String {
  return "${this::class.simpleName ?: "Error"}：${message ?: "未知错误"}"
}

/** 包坐标的紧凑展示。 */
internal fun NpmPackageId.toDisplayText(): String = "$name@$version"

/** 使用 IEC 单位展示逻辑文件大小，避免依赖平台格式化 API。 */
private fun Long.toDisplaySize(): String {
  if (this < KIBIBYTE) return "$this B"
  val unit = if (this >= MEBIBYTE) MEBIBYTE else KIBIBYTE
  val suffix = if (unit == MEBIBYTE) "MiB" else "KiB"
  val tenths = this * 10 / unit
  return "${tenths / 10}.${tenths % 10} $suffix"
}

/** 时间戳转换为设备当前时区并精确到分钟；文件系统不提供时间时显示未知。 */
private fun Long?.toDisplayDate(): String {
  if (this == null) return "未知"
  val date = Instant.fromEpochMilliseconds(this)
    .toLocalDateTime(TimeZone.currentSystemDefault())
  return buildString {
    append(date.year)
    append('-').append(date.month.number.toTwoDigits())
    append('-').append(date.day.toTwoDigits())
    append(' ').append(date.hour.toTwoDigits())
    append(':').append(date.minute.toTwoDigits())
  }
}

/** 日期字段统一补齐两位，避免依赖平台格式化 API。 */
private fun Int.toTwoDigits(): String = toString().padStart(2, '0')

/** Bundle 来源的页面文案。 */
private fun NpmPackageSource.toDisplayText(): String = when (this) {
  NpmPackageSource.REGISTRY -> "npm Registry"
  NpmPackageSource.LOCAL_DEBUG -> "本地 Debug 注入"
}

/** 需要二次确认的破坏性管理操作。 */
internal sealed interface BundleConfirmation {
  data class Delete(val bundle: NpmBundleInfo) : BundleConfirmation
  data object ClearAll : BundleConfirmation
}

/** 页面操作结果，区分成功提示与错误提示的颜色。 */
internal data class BundlePageMessage(
  val text: String,
  val isError: Boolean,
)

private const val KIBIBYTE = 1024L
private const val MEBIBYTE = 1024L * KIBIBYTE
internal const val BUNDLE_LIST_DETAIL_SCENE_KEY = "npm-bundle-manager"
