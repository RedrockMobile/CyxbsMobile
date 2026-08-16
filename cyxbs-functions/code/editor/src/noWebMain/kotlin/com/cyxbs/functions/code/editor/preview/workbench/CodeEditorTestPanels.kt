package com.cyxbs.functions.code.editor.preview.workbench

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.School
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.functions.code.editor.workbench.CodeEditorSidePanel
import com.cyxbs.functions.code.editor.workbench.CodeEditorSidePanelGroup
import com.cyxbs.functions.code.editor.workbench.CodeEditorToolWindow
import com.cyxbs.functions.code.editor.workbench.EditorWorkbenchColors

/** 测试工作台文件面板的稳定标识。 */
internal const val FILES_PANEL_ID = "files"

/** 测试工作台运行窗口的稳定标识。 */
internal const val RUN_TOOL_WINDOW_ID = "run"

/**
 * 创建仅供编辑器手动测试页使用的侧边能力。
 *
 * [includeCourse] 用于验证教学场景的特殊入口；设为 false 后得到的就是普通代码编辑器侧栏，证明课程
 * 不属于通用工作台的必选依赖。[projectPath] 仅用于文件面板顶部展示，后续可传入真实工程路径；
 * [fileIcon] 与标签栏共享语言图标，避免文件树重新获取或解析动态资源。
 */
@Composable
internal fun rememberCodeEditorTestSidePanels(
  activeFilePath: String,
  sourceFiles: Map<String, String>,
  languageStatus: String,
  isLanguageReady: Boolean,
  isLoadingLanguage: Boolean,
  isAnalyzingSymbol: Boolean,
  highlightCacheCapacity: Int,
  includeCourse: Boolean,
  projectPath: String = TestProjectPath,
  fileIcon: (@Composable (filePath: String, modifier: Modifier) -> Unit)? = null,
  onOpenFile: (String) -> Unit,
  onCreateFile: (String) -> Boolean,
  onLoadLanguage: () -> Unit,
  onHighlightCacheCapacityChange: (Int) -> Unit,
  onFindDefinition: () -> Unit,
  onFindReferences: () -> Unit,
  onRename: (String) -> Unit,
): List<CodeEditorSidePanel> {
  val createdFolderPaths = remember { mutableStateListOf<String>() }
  val panels = buildList {
    if (includeCourse) {
      add(
        CodeEditorSidePanel(
          id = "course",
          title = "课程",
          icon = Icons.Default.School,
        ) {
          CoursePanelContent()
        },
      )
    }
    add(
      CodeEditorSidePanel(
        id = FILES_PANEL_ID,
        title = "文件",
        icon = Icons.Default.Folder,
      ) {
        FilePanelContent(
          projectPath = projectPath,
          activeFilePath = activeFilePath,
          filePaths = sourceFiles.keys.sorted(),
          folderPaths = createdFolderPaths,
          fileIcon = fileIcon,
          onOpenFile = {
            onOpenFile(it)
            if (layoutMode != com.cyxbs.functions.code.editor.workbench.CodeEditorWorkbenchLayoutMode.Expanded) {
              closePanel()
            }
          },
          onCreateFile = onCreateFile,
          onCreateFolder = { requestedPath ->
            createTestFolder(
              requestedPath = requestedPath,
              filePaths = sourceFiles.keys,
              folderPaths = createdFolderPaths,
            )
          },
        )
      },
    )
    add(
      CodeEditorSidePanel(
        id = "search",
        title = "工作区搜索",
        icon = Icons.Default.Search,
      ) {
        SearchPanelContent(sourceFiles = sourceFiles, onOpenFile = onOpenFile)
      },
    )
    add(
      CodeEditorSidePanel(
        id = "outline",
        title = "结构",
        icon = Icons.AutoMirrored.Filled.List,
      ) {
        OutlinePanelContent(source = sourceFiles[activeFilePath].orEmpty())
      },
    )
    add(
      CodeEditorSidePanel(
        id = "settings",
        title = "编辑器设置",
        icon = Icons.Default.Settings,
        group = CodeEditorSidePanelGroup.Bottom,
      ) {
        SettingsPanelContent(
          languageStatus = languageStatus,
          isLanguageReady = isLanguageReady,
          isLoadingLanguage = isLoadingLanguage,
          isAnalyzingSymbol = isAnalyzingSymbol,
          highlightCacheCapacity = highlightCacheCapacity,
          onLoadLanguage = onLoadLanguage,
          onHighlightCacheCapacityChange = onHighlightCacheCapacityChange,
          onFindDefinition = onFindDefinition,
          onFindReferences = onFindReferences,
          onRename = onRename,
        )
      },
    )
  }
  return panels
}

/** 创建测试页底部 Tool Window；内容不会反向持有编辑器或运行器。 */
internal fun codeEditorTestToolWindows(
  activeFilePath: String,
  output: String,
  performanceText: String,
): List<CodeEditorToolWindow> {
  return listOf(
    CodeEditorToolWindow(
      id = RUN_TOOL_WINDOW_ID,
      title = "Run · $activeFilePath",
      icon = Icons.Default.PlayArrow,
    ) {
      ToolWindowText(output)
    },
    CodeEditorToolWindow(
      id = "performance",
      title = "Performance",
      icon = Icons.Default.Code,
    ) {
      ToolWindowText(performanceText)
    },
  )
}

/** 教学场景的示例课程入口；正式课程模块后续可用相同模型替换。 */
@Composable
private fun CoursePanelContent() {
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("JavaScript 入门", color = EditorWorkbenchColors.PrimaryText, fontWeight = FontWeight.Bold)
    Text(
      "第 3 课 · 类与模块",
      color = EditorWorkbenchColors.SecondaryText,
      fontSize = 12.sp,
    )
    listOf(
      "1. 认识 class 与 constructor" to true,
      "2. 导出 Student 类" to true,
      "3. 在 main.js 中导入并运行" to false,
    ).forEach { (text, completed) ->
      Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (completed) Color(0x2239B982) else Color(0x226CB6FF),
        shape = RoundedCornerShape(10.dp),
      ) {
        Text(
          text = text,
          color = EditorWorkbenchColors.PrimaryText,
          fontSize = 12.sp,
          modifier = Modifier.padding(10.dp),
        )
      }
    }
    Text(
      "课程面板由教学业务注入；普通编辑器不会包含该入口。",
      color = EditorWorkbenchColors.SecondaryText,
      fontSize = 10.sp,
    )
  }
}

/** 工作区文件切换与创建。 */
@Composable
private fun FilePanelContent(
  projectPath: String,
  activeFilePath: String,
  filePaths: List<String>,
  folderPaths: List<String>,
  fileIcon: (@Composable (filePath: String, modifier: Modifier) -> Unit)?,
  onOpenFile: (String) -> Unit,
  onCreateFile: (String) -> Boolean,
  onCreateFolder: (String) -> Boolean,
) {
  var pendingCreation by remember { mutableStateOf<FileTreeCreation?>(null) }
  var creationName by remember { mutableStateOf("") }
  var creationError by remember { mutableStateOf<String?>(null) }
  val folderPathSnapshot = folderPaths.toList()
  val fileTree = remember(filePaths, folderPathSnapshot) {
    buildFileTree(filePaths = filePaths, folderPaths = folderPathSnapshot)
  }
  val expandedFolders = remember { mutableStateMapOf<String, Boolean>() }
  Column(Modifier.fillMaxSize()) {
    FilePanelToolbar(
      projectPath = projectPath,
      onRequestCreation = { creation ->
        pendingCreation = creation
        creationName = creation.defaultName
        creationError = null
      },
    )
    BoxWithConstraints(Modifier.weight(1F)) {
      val verticalScrollState = rememberScrollState()
      val horizontalScrollState = rememberScrollState()
      val minimumRowWidth = maxWidth
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(verticalScrollState)
          .horizontalScroll(horizontalScrollState),
      ) {
        FileTreeNodes(
          nodes = fileTree,
          activeFilePath = activeFilePath,
          expandedFolders = expandedFolders,
          minimumRowWidth = minimumRowWidth,
          fileIcon = fileIcon,
          onOpenFile = onOpenFile,
        )
      }
    }
  }
  pendingCreation?.let { creation ->
    FileTreeCreationDialog(
      projectPath = projectPath,
      creation = creation,
      name = creationName,
      error = creationError,
      onNameChange = {
        creationName = it
        creationError = null
      },
      onDismiss = { pendingCreation = null },
      onConfirm = {
        val created = when (creation) {
          FileTreeCreation.File -> onCreateFile(creationName)
          FileTreeCreation.Directory -> onCreateFolder(creationName)
        }
        if (created) {
          pendingCreation = null
        } else {
          creationError = "名称无效或已存在"
        }
      },
    )
  }
}

/**
 * 文件树顶部的虚拟项目路径栏。
 *
 * 三点菜单只负责发起创建意图；实际文件和目录的创建策略由上层回调决定，后续可直接替换为真实
 * 文件系统实现。
 */
@Composable
private fun FilePanelToolbar(
  projectPath: String,
  onRequestCreation: (FileTreeCreation) -> Unit,
) {
  var menuExpanded by remember { mutableStateOf(false) }
  Column {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(FilePanelToolbarHeight)
        .padding(start = 7.dp, end = 3.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = Icons.Default.Folder,
        contentDescription = null,
        tint = EditorWorkbenchColors.Accent,
        modifier = Modifier.size(16.dp),
      )
      Text(
        text = projectPath,
        color = EditorWorkbenchColors.PrimaryText,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1F).padding(start = 6.dp),
      )
      Box(
        modifier = Modifier.size(30.dp).clickable { menuExpanded = true },
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Default.MoreVert,
          contentDescription = "项目操作",
          tint = EditorWorkbenchColors.SecondaryText,
          modifier = Modifier.size(18.dp),
        )
        MaterialTheme(
          colors = MaterialTheme.colors.copy(
            surface = EditorWorkbenchColors.PanelBackground,
            onSurface = EditorWorkbenchColors.PrimaryText,
          ),
        ) {
          DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.removeDefaultDropdownMenuVerticalPadding(),
          ) {
            DropdownMenuItem(
              modifier = Modifier.height(CompactDropdownMenuItemHeight),
              contentPadding = PaddingValues(horizontal = 12.dp),
              onClick = {
                menuExpanded = false
                onRequestCreation(FileTreeCreation.Directory)
              },
            ) {
              Text("新建文件夹", color = EditorWorkbenchColors.PrimaryText, fontSize = 12.sp)
            }
            DropdownMenuItem(
              modifier = Modifier.height(CompactDropdownMenuItemHeight),
              contentPadding = PaddingValues(horizontal = 12.dp),
              onClick = {
                menuExpanded = false
                onRequestCreation(FileTreeCreation.File)
              },
            ) {
              Text("新建文件", color = EditorWorkbenchColors.PrimaryText, fontSize = 12.sp)
            }
          }
        }
      }
    }
    Divider(color = EditorWorkbenchColors.Divider)
  }
}

/**
 * 裁掉 Material 2 [DropdownMenu] 内部固定的上下留白。
 *
 * Compose 1.11.1 没有暴露该边距参数，因此这里只收缩并平移菜单内容布局；Popup 定位、圆角 Surface、
 * 动画及外部点击关闭仍由原组件提供。升级 Compose 后应重新核对默认边距值。
 */
internal fun Modifier.removeDefaultDropdownMenuVerticalPadding(): Modifier = layout {
    measurable,
    constraints,
  ->
  val verticalPaddingPx = DefaultDropdownMenuVerticalPadding.roundToPx()
  val placeable = measurable.measure(constraints)
  val compactHeight = (placeable.height - verticalPaddingPx * 2).coerceAtLeast(0)
  layout(placeable.width, compactHeight) {
    placeable.placeRelative(x = 0, y = -verticalPaddingPx)
  }
}

/** 输入新文件或文件夹名称的测试对话框。 */
@Composable
private fun FileTreeCreationDialog(
  projectPath: String,
  creation: FileTreeCreation,
  name: String,
  error: String?,
  onNameChange: (String) -> Unit,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    backgroundColor = EditorWorkbenchColors.PanelBackground,
    contentColor = EditorWorkbenchColors.PrimaryText,
    title = { Text(creation.title, fontSize = 16.sp) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "项目：$projectPath",
          color = EditorWorkbenchColors.SecondaryText,
          fontSize = 11.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        TextField(
          value = name,
          onValueChange = onNameChange,
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          label = { Text(creation.inputLabel) },
          colors = editorTextFieldColors(),
          shape = RoundedCornerShape(6.dp),
        )
        if (error != null) {
          Text(error, color = Color(0xFFFF6B6B), fontSize = 11.sp)
        }
      }
    },
    confirmButton = {
      TextButton(enabled = name.isNotBlank(), onClick = onConfirm) {
        Text("确认")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("取消")
      }
    },
  )
}

/**
 * 递归展示 IDEA 风格的紧凑文件树。
 *
 * 文件夹状态由 [expandedFolders] 按完整路径保存；未记录的文件夹默认展开，使首次进入面板时可以
 * 直接看到当前测试工程的文件。文件行保持整行矩形选中态，不使用卡片圆角。
 */
@Composable
private fun FileTreeNodes(
  nodes: List<FileTreeNode>,
  activeFilePath: String,
  expandedFolders: MutableMap<String, Boolean>,
  minimumRowWidth: Dp,
  fileIcon: (@Composable (filePath: String, modifier: Modifier) -> Unit)?,
  onOpenFile: (String) -> Unit,
  depth: Int = 0,
) {
  nodes.forEach { node ->
    when (node) {
      is FileTreeNode.Directory -> {
        val expanded = expandedFolders[node.path] ?: true
        FileTreeRow(
          name = node.name,
          depth = depth,
          icon = Icons.Default.Folder,
          expanded = expanded,
          selected = false,
          minimumWidth = minimumRowWidth,
          onClick = { expandedFolders[node.path] = !expanded },
        )
        if (expanded) {
          FileTreeNodes(
            nodes = node.children,
            activeFilePath = activeFilePath,
            expandedFolders = expandedFolders,
            minimumRowWidth = minimumRowWidth,
            fileIcon = fileIcon,
            onOpenFile = onOpenFile,
            depth = depth + 1,
          )
        }
      }
      is FileTreeNode.File -> FileTreeRow(
        name = node.name,
        depth = depth,
        icon = Icons.Default.Description,
        expanded = null,
        selected = node.path == activeFilePath,
        minimumWidth = minimumRowWidth,
        customIcon = fileIcon?.let { icon ->
          { modifier -> icon(node.path, modifier) }
        },
        onClick = { onOpenFile(node.path) },
      )
    }
  }
}

/**
 * 文件树中的单行节点。
 *
 * [expanded] 为 null 表示文件，否则表示文件夹的展开状态。固定紧凑高度和层级缩进用于接近桌面
 * IDE 的项目树密度，整行点击区域同时兼顾触摸与鼠标操作。[customIcon] 存在时优先绘制语言
 * 包提供的多色图标，否则使用 [icon] 并应用侧边栏选中颜色。
 */
@Composable
private fun FileTreeRow(
  name: String,
  depth: Int,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  expanded: Boolean?,
  selected: Boolean,
  minimumWidth: Dp,
  customIcon: (@Composable (modifier: Modifier) -> Unit)? = null,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .widthIn(min = minimumWidth)
      .height(FileTreeRowHeight)
      .background(if (selected) Color(0x386F5CFF) else Color.Transparent)
      .clickable(onClick = onClick)
      .padding(start = FileTreeHorizontalPadding + FileTreeIndent * depth, end = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (expanded != null) {
      Icon(
        imageVector = if (expanded) {
          Icons.Default.KeyboardArrowDown
        } else {
          Icons.AutoMirrored.Filled.KeyboardArrowRight
        },
        contentDescription = if (expanded) "收起文件夹" else "展开文件夹",
        tint = EditorWorkbenchColors.SecondaryText,
        modifier = Modifier.size(FileTreeDisclosureIconSize),
      )
    } else {
      androidx.compose.foundation.layout.Spacer(Modifier.width(FileTreeDisclosureIconSize))
    }
    if (customIcon == null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (selected) EditorWorkbenchColors.Accent else EditorWorkbenchColors.SecondaryText,
        modifier = Modifier.size(FileTreeNodeIconSize),
      )
    } else {
      customIcon(Modifier.size(FileTreeNodeIconSize))
    }
    Text(
      text = name,
      color = EditorWorkbenchColors.PrimaryText,
      fontFamily = FontFamily.Monospace,
      fontSize = 12.sp,
      maxLines = 1,
      softWrap = false,
      overflow = TextOverflow.Clip,
      modifier = Modifier.padding(start = 5.dp),
    )
  }
}

/**
 * 在测试工作区创建空目录。
 *
 * 当前只更新内存目录池；真实文件系统接入后可直接替换该回调。路径必须位于项目内，且不能与已有
 * 文件、显式目录或文件路径隐含的目录重名。
 */
private fun createTestFolder(
  requestedPath: String,
  filePaths: Collection<String>,
  folderPaths: MutableCollection<String>,
): Boolean {
  val normalizedPath = requestedPath.trim().replace('\\', '/')
  val segments = normalizedPath.split('/')
  val conflictsWithFile = segments.indices.any { index ->
    segments.take(index + 1).joinToString("/") in filePaths
  }
  val alreadyExists = normalizedPath in folderPaths || filePaths.any { it.startsWith("$normalizedPath/") }
  if (
    normalizedPath.isEmpty() ||
    normalizedPath.startsWith('/') ||
    segments.any { it.isEmpty() || it == "." || it == ".." } ||
    conflictsWithFile ||
    alreadyExists
  ) {
    return false
  }
  folderPaths += normalizedPath
  return true
}

/** 将显式目录和相对文件路径构造成文件夹优先、同类型按名称排序的展示树。 */
private fun buildFileTree(
  filePaths: List<String>,
  folderPaths: List<String>,
): List<FileTreeNode> {
  val root = MutableFileTreeDirectory(name = "", path = "")
  folderPaths.distinct().forEach { folderPath ->
    root.findOrCreateDirectory(folderPath.split('/').filter(String::isNotBlank))
  }
  filePaths.distinct().forEach { filePath ->
    val segments = filePath.split('/').filter(String::isNotBlank)
    if (segments.isEmpty()) return@forEach
    val directory = root.findOrCreateDirectory(segments.dropLast(1))
    directory.files[segments.last()] = filePath
  }
  return root.toNodes()
}

/** 构建阶段使用的可变目录，完成后转换为只读展示节点。 */
private class MutableFileTreeDirectory(
  val name: String,
  val path: String,
) {
  val directories = linkedMapOf<String, MutableFileTreeDirectory>()
  val files = linkedMapOf<String, String>()

  /** 沿相对路径查找目录，不存在的节点会在构建阶段补齐。 */
  fun findOrCreateDirectory(segments: List<String>): MutableFileTreeDirectory {
    var directory = this
    segments.forEach { segment ->
      val childPath = listOf(directory.path, segment).filter(String::isNotEmpty).joinToString("/")
      directory = directory.directories.getOrPut(segment) {
        MutableFileTreeDirectory(name = segment, path = childPath)
      }
    }
    return directory
  }

  /** 固化当前目录，并保证文件夹始终排在文件之前。 */
  fun toNodes(): List<FileTreeNode> {
    return directories.values.sortedBy { it.name }.map { directory ->
      FileTreeNode.Directory(
        name = directory.name,
        path = directory.path,
        children = directory.toNodes(),
      )
    } + files.entries.sortedBy { it.key }.map { (name, path) ->
      FileTreeNode.File(name = name, path = path)
    }
  }
}

/** 文件面板仅用于展示的树节点。 */
private sealed interface FileTreeNode {
  val name: String
  val path: String

  /** 可展开的目录节点。 */
  data class Directory(
    override val name: String,
    override val path: String,
    val children: List<FileTreeNode>,
  ) : FileTreeNode

  /** 可打开的源码文件节点。 */
  data class File(
    override val name: String,
    override val path: String,
  ) : FileTreeNode
}

/** 测试文件树支持的创建类型及其输入框文案。 */
private enum class FileTreeCreation(
  val title: String,
  val inputLabel: String,
  val defaultName: String,
) {
  Directory(title = "新建文件夹", inputLabel = "文件夹名称", defaultName = "new-folder"),
  File(title = "新建文件", inputLabel = "文件名称", defaultName = "untitled.js"),
}

/** 对当前内存工作区执行简单文本搜索，用于验证多文件结果列表的交互。 */
@Composable
private fun SearchPanelContent(
  sourceFiles: Map<String, String>,
  onOpenFile: (String) -> Unit,
) {
  var query by remember { mutableStateOf("") }
  val results = if (query.isBlank()) {
    emptyList()
  } else {
    sourceFiles.flatMap { (path, source) ->
      source.lineSequence().mapIndexedNotNull { index, line ->
        line.takeIf { it.contains(query, ignoreCase = true) }?.let {
          SearchResult(
            filePath = path,
            lineNumber = index + 1,
            context = searchResultContext(line = line, query = query),
          )
        }
      }.toList()
    }
  }
  Column(Modifier.fillMaxSize()) {
    SearchInput(
      value = query,
      onValueChange = { query = it },
      modifier = Modifier.fillMaxWidth().padding(8.dp),
    )
    Column(modifier = Modifier.weight(1F).verticalScroll(rememberScrollState())) {
      results.forEach { result ->
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenFile(result.filePath) }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
          Text(
            text = "${result.filePath}:${result.lineNumber}",
            color = EditorWorkbenchColors.SecondaryText,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = highlightedSearchContext(result.context, query),
            color = EditorWorkbenchColors.PrimaryText,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

/**
 * 工作区搜索使用的紧凑输入框。
 *
 * 自绘装饰层避免 Material 默认输入框的高占位和强调下划线，并保留单行输入、光标及无障碍描述。
 */
@Composable
private fun SearchInput(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  BasicTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier,
    singleLine = true,
    textStyle = TextStyle(
      color = EditorWorkbenchColors.PrimaryText,
      fontFamily = FontFamily.Monospace,
      fontSize = 12.sp,
    ),
    cursorBrush = SolidColor(EditorWorkbenchColors.Accent),
    decorationBox = { innerTextField ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(SearchInputHeight)
          .background(EditorWorkbenchColors.EditorBackground, RoundedCornerShape(5.dp))
          .border(1.dp, EditorWorkbenchColors.Divider, RoundedCornerShape(5.dp))
          .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = Icons.Default.Search,
          contentDescription = null,
          tint = EditorWorkbenchColors.SecondaryText,
          modifier = Modifier.size(16.dp),
        )
        Box(
          modifier = Modifier.fillMaxHeight().weight(1F).padding(start = 6.dp),
          contentAlignment = Alignment.CenterStart,
        ) {
          if (value.isEmpty()) {
            Text(
              text = "搜索工作区",
              color = EditorWorkbenchColors.SecondaryText,
              fontSize = 12.sp,
              maxLines = 1,
            )
          }
          innerTextField()
        }
      }
    },
  )
}

/**
 * 截取首个命中位置附近的一行上下文。
 *
 * 结果最多保留命中点前后各 [SearchContextRadius] 个字符，并通过省略号标记被裁剪的内容；仅裁剪
 * 展示文本，不改变文件路径和源码行号。
 */
private fun searchResultContext(line: String, query: String): String {
  val normalizedLine = line.trim()
  val matchStart = normalizedLine.indexOf(query, ignoreCase = true)
  if (matchStart < 0) return normalizedLine
  val contextStart = (matchStart - SearchContextRadius).coerceAtLeast(0)
  val contextEnd = (matchStart + query.length + SearchContextRadius).coerceAtMost(normalizedLine.length)
  return buildString {
    if (contextStart > 0) append('…')
    append(normalizedLine.substring(contextStart, contextEnd))
    if (contextEnd < normalizedLine.length) append('…')
  }
}

/** 将上下文中的全部查询词标记为高亮文本，匹配过程不区分大小写。 */
private fun highlightedSearchContext(context: String, query: String) = buildAnnotatedString {
  if (query.isEmpty()) {
    append(context)
    return@buildAnnotatedString
  }
  var cursor = 0
  while (cursor < context.length) {
    val matchStart = context.indexOf(query, startIndex = cursor, ignoreCase = true)
    if (matchStart < 0) break
    append(context.substring(cursor, matchStart))
    withStyle(
      SpanStyle(
        color = EditorWorkbenchColors.AccentLight,
        background = EditorWorkbenchColors.Accent.copy(alpha = 0.28F),
        fontWeight = FontWeight.SemiBold,
      ),
    ) {
      append(context.substring(matchStart, matchStart + query.length))
    }
    cursor = matchStart + query.length
  }
  if (cursor < context.length) append(context.substring(cursor))
}

/** 从当前文件中提取课堂示例常见的 class/function/method 结构。 */
@Composable
private fun OutlinePanelContent(source: String) {
  val symbols = source.lineSequence().mapIndexedNotNull { index, line ->
    val trimmed = line.trim()
    val name = when {
      trimmed.startsWith("class ") || trimmed.startsWith("export class ") ->
        trimmed.substringAfter("class ").substringBefore(' ').substringBefore('{')
      trimmed.startsWith("function ") || trimmed.startsWith("export function ") ->
        trimmed.substringAfter("function ").substringBefore('(')
      trimmed.endsWith("{") && '(' in trimmed && !trimmed.startsWith("if") -> trimmed.substringBefore('(')
      else -> null
    }
    name?.takeIf(String::isNotBlank)?.let { OutlineSymbol(it, index + 1) }
  }.toList()
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
    if (symbols.isEmpty()) {
      Text(
        "当前文件没有可展示的结构",
        color = EditorWorkbenchColors.SecondaryText,
        fontSize = 12.sp,
        modifier = Modifier.padding(14.dp),
      )
    }
    symbols.forEach { symbol ->
      Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp)) {
        Icon(Icons.Default.Code, contentDescription = null, tint = EditorWorkbenchColors.SecondaryText)
        Text(
          symbol.name,
          color = EditorWorkbenchColors.PrimaryText,
          fontFamily = FontFamily.Monospace,
          fontSize = 12.sp,
          modifier = Modifier.weight(1F).padding(start = 8.dp),
        )
        Text("L${symbol.line}", color = EditorWorkbenchColors.SecondaryText, fontSize = 10.sp)
      }
    }
  }
}

/** 动态语言和符号测试设置。 */
@Composable
private fun SettingsPanelContent(
  languageStatus: String,
  isLanguageReady: Boolean,
  isLoadingLanguage: Boolean,
  isAnalyzingSymbol: Boolean,
  highlightCacheCapacity: Int,
  onLoadLanguage: () -> Unit,
  onHighlightCacheCapacityChange: (Int) -> Unit,
  onFindDefinition: () -> Unit,
  onFindReferences: () -> Unit,
  onRename: (String) -> Unit,
) {
  var renameTarget by remember { mutableStateOf("learner") }
  var highlightCacheCapacityInput by remember {
    mutableStateOf(highlightCacheCapacity.toString())
  }
  val highlightCacheInputWidth = (
    highlightCacheCapacityInput.length.coerceIn(1, 8) * 7 + 8
  ).dp
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
    SettingsSectionTitle("动态语言服务")
    Text(
      text = languageStatus,
      color = EditorWorkbenchColors.SecondaryText,
      fontSize = 11.sp,
      modifier = Modifier.padding(horizontal = 14.dp),
    )
    TextButton(enabled = !isLoadingLanguage, onClick = onLoadLanguage) {
      Text(if (isLoadingLanguage) "加载中…" else "加载 / 重新加载")
    }
    SettingsSectionTitle("文件会话缓存")
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "文件上限",
        color = EditorWorkbenchColors.PrimaryText,
        fontSize = 12.sp,
        modifier = Modifier.weight(1F),
      )
      Column(horizontalAlignment = Alignment.End) {
        BasicTextField(
          value = highlightCacheCapacityInput,
          onValueChange = { input ->
            if (input.isEmpty() || input.all(Char::isDigit)) {
              highlightCacheCapacityInput = input
              input.toIntOrNull()?.let(onHighlightCacheCapacityChange)
            }
          },
          modifier = Modifier.width(highlightCacheInputWidth),
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          textStyle = TextStyle(
            color = EditorWorkbenchColors.PrimaryText,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
          ),
          cursorBrush = SolidColor(EditorWorkbenchColors.Accent),
        )
        Divider(
          color = EditorWorkbenchColors.SecondaryText,
          modifier = Modifier.width(highlightCacheInputWidth),
        )
      }
      Text(
        text = " 个文件",
        color = EditorWorkbenchColors.SecondaryText,
        fontSize = 11.sp,
      )
    }
    Text(
      text = if (highlightCacheCapacity == 0) {
        "跨文件缓存已关闭；当前文件会话仍会保留。"
      } else {
        "保留最近 $highlightCacheCapacity 个文件的高亮、光标和撤销栈；输入 0 只保留当前文件。"
      },
      color = EditorWorkbenchColors.SecondaryText,
      fontSize = 10.sp,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
    )
    SettingsSectionTitle("符号工具")
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      TextButton(
        enabled = isLanguageReady && !isAnalyzingSymbol,
        onClick = onFindDefinition,
        modifier = Modifier.weight(1F),
      ) { Text("定义") }
      TextButton(
        enabled = isLanguageReady && !isAnalyzingSymbol,
        onClick = onFindReferences,
        modifier = Modifier.weight(1F),
      ) { Text("引用") }
    }
    TextField(
      value = renameTarget,
      onValueChange = { renameTarget = it },
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
      enabled = isLanguageReady && !isAnalyzingSymbol,
      singleLine = true,
      label = { Text("重命名为") },
      colors = editorTextFieldColors(),
      shape = RoundedCornerShape(8.dp),
    )
    Button(
      enabled = isLanguageReady && !isAnalyzingSymbol && renameTarget.isNotBlank(),
      onClick = { onRename(renameTarget) },
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
      Text("安全重命名")
    }
  }
}

/** 设置面板分区标题。 */
@Composable
private fun SettingsSectionTitle(text: String) {
  Text(
    text = text,
    color = EditorWorkbenchColors.Accent,
    fontSize = 11.sp,
    fontWeight = FontWeight.SemiBold,
    modifier = Modifier.padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 6.dp),
  )
}

/** Tool Window 中可滚动、可选中复制的等宽文本。 */
@Composable
private fun ToolWindowText(text: String) {
  SelectionContainer {
    Text(
      text = text,
      color = EditorWorkbenchColors.PrimaryText,
      fontFamily = FontFamily.Monospace,
      fontSize = 11.sp,
      lineHeight = 16.sp,
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
    )
  }
}

/** 暗色编辑器面板输入框颜色。 */
@Composable
private fun editorTextFieldColors() = TextFieldDefaults.textFieldColors(
  textColor = EditorWorkbenchColors.PrimaryText,
  backgroundColor = Color(0xFF121720),
  cursorColor = EditorWorkbenchColors.Accent,
  focusedLabelColor = EditorWorkbenchColors.Accent,
  unfocusedLabelColor = EditorWorkbenchColors.SecondaryText,
  focusedIndicatorColor = EditorWorkbenchColors.Accent,
  unfocusedIndicatorColor = EditorWorkbenchColors.Divider,
  trailingIconColor = EditorWorkbenchColors.SecondaryText,
)

/** 文本搜索结果，仅保存路径、行号和裁剪后的单行上下文。 */
private data class SearchResult(
  val filePath: String,
  val lineNumber: Int,
  val context: String,
)

/** 结构面板中的轻量符号。 */
private data class OutlineSymbol(val name: String, val line: Int)

/** IDEA 风格文件树的紧凑单行高度。 */
private val FileTreeRowHeight = 26.dp

/** 文件树根节点与面板左边缘的间距。 */
private val FileTreeHorizontalPadding = 6.dp

/** 文件树每深入一级增加的水平缩进。 */
private val FileTreeIndent = 14.dp

/** 文件夹展开箭头及文件占位区域的宽高。 */
private val FileTreeDisclosureIconSize = 14.dp

/** 文件与文件夹图标的统一尺寸。 */
private val FileTreeNodeIconSize = 16.dp

/** 文件面板项目路径栏的紧凑高度。 */
private val FilePanelToolbarHeight = 34.dp

/** 新建菜单单项的紧凑高度。 */
internal val CompactDropdownMenuItemHeight = 34.dp

/** Compose Material 2 当前固定在菜单内容上下的默认留白。 */
private val DefaultDropdownMenuVerticalPadding = 8.dp

/** 测试页使用的虚拟项目路径，不对应设备真实文件系统。 */
private const val TestProjectPath = "/JavaScriptCourse"

/** 紧凑搜索框高度。 */
private val SearchInputHeight = 32.dp

/** 搜索结果在首个命中点前后保留的最大字符数。 */
private const val SearchContextRadius = 36
