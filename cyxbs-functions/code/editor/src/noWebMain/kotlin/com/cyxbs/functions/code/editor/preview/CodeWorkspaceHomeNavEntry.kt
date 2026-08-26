package com.cyxbs.functions.code.editor.preview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.functions.code.editor.project.CodeProjectTemplate
import com.cyxbs.functions.code.editor.project.CodeProjectRepository
import com.cyxbs.functions.code.editor.project.CodeProjectTemplates
import com.cyxbs.functions.code.editor.project.HistoricalCodeProject
import com.cyxbs.functions.code.editor.project.openProjectDirectory
import com.cyxbs.functions.code.editor.workbench.DynamicLanguageFileIconCache
import com.cyxbs.functions.code.editor.workbench.rememberDynamicLanguageFileIconCache
import com.cyxbs.functions.code.language.DynamicLanguageManager
import com.cyxbs.functions.code.tutorials.DynamicTutorialInfo
import com.cyxbs.functions.code.tutorials.DynamicTutorialManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** 代码工作区首页路由；保留原测试页 deeplink，入口与具体工作区分别占用一层返回栈。 */
@Serializable
data object CodeWorkspaceHomeNavArgument : AppNavArgument

/**
 * 无项目时提供创建项目和打开教程入口；存在历史记录时展示可快速恢复的完整历史项目列表。
 *
 * 首页先读取轻量 Settings 索引；语言 Catalog 只在后台恢复本地图标，不加载语言运行时，也不会
 * 阻塞历史项目列表。教程 Catalog 在用户主动打开选择框时才读取。
 */
@AppNav(route = "code/editor-test")
class CodeWorkspaceHomeNavEntry : AppNavEntry<CodeWorkspaceHomeNavArgument>() {

  override fun isNeedLogin(argument: CodeWorkspaceHomeNavArgument): Boolean = false

  @Composable
  override fun Content(argument: CodeWorkspaceHomeNavArgument) {
    val projectRepository = remember { CodeProjectRepository() }
    val dynamicLanguageManager = remember { DynamicLanguageManager() }
    val languageIconCache = rememberDynamicLanguageFileIconCache()
    val tutorialManager = remember { DynamicTutorialManager() }
    val coroutineScope = rememberCoroutineScope()
    var historicalProjects by remember { mutableStateOf<List<HistoricalCodeProject>?>(null) }
    var tutorialChoices by remember { mutableStateOf<List<DynamicTutorialInfo>?>(null) }
    var isLoadingTutorialChoices by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf<WorkspaceSelectionMode?>(null) }
    var projectTemplateForNaming by remember { mutableStateOf<CodeProjectTemplate?>(null) }
    var projectNameError by remember { mutableStateOf<String?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    /** 重新读取历史项目；创建、置顶和移除后复用该入口同步列表。 */
    fun refreshHistoricalProjects() {
      coroutineScope.launch {
        runCatching { projectRepository.historicalProjects() }
          .onSuccess { historicalProjects = it }
          .onFailure { errorMessage = it.message ?: "读取历史项目失败。" }
      }
    }

    LaunchedEffect(Unit) {
      refreshHistoricalProjects()
      // 入口页只恢复已经落盘的协议图标，不创建语言 Runtime；恢复失败时保留通用代码图标。
      try {
        languageIconCache.updateAll(dynamicLanguageManager.cachedIcons())
      } catch (exception: CancellationException) {
        throw exception
      } catch (_: Throwable) {
        // 图标是非关键派生数据，Catalog 或缓存不可用不能阻断本地项目入口。
      }
    }

    WorkspaceHomeScreen(
      historicalProjects = historicalProjects,
      languageIconCache = languageIconCache,
      isWorking = isWorking,
      errorMessage = errorMessage,
      onCreateProject = {
        errorMessage = null
        isWorking = true
        coroutineScope.launch {
          runCatching { projectRepository.prepareProjectRoot() }
            .onSuccess { isPrepared ->
              if (isPrepared) {
                historicalProjects = projectRepository.historicalProjects()
                selectionMode = WorkspaceSelectionMode.PROJECT
              }
            }
            .onFailure { errorMessage = it.message ?: "选择项目目录失败。" }
          isWorking = false
        }
      },
      onImportProject = {
        if (isWorking) return@WorkspaceHomeScreen
        errorMessage = null
        isWorking = true
        coroutineScope.launch {
          runCatching { projectRepository.importProject() }
            .onSuccess { workspace ->
              if (workspace != null) {
                historicalProjects = projectRepository.historicalProjects()
                CodeEditorTestNavArgument(projectId = workspace.project.projectId)
                  .navigateFromWorkspaceHome()
              }
            }
            .onFailure { errorMessage = it.message ?: "打开项目失败。" }
          isWorking = false
        }
      },
      onOpenTutorial = {
        errorMessage = null
        selectionMode = WorkspaceSelectionMode.TUTORIAL
        if (tutorialChoices == null && !isLoadingTutorialChoices) {
          isLoadingTutorialChoices = true
          coroutineScope.launch {
            runCatching { tutorialManager.supportedTutorials() }
              .onSuccess { tutorialChoices = it }
              .onFailure { errorMessage = it.message ?: "读取教程语言失败。" }
            isLoadingTutorialChoices = false
          }
        }
      },
      onOpenHistoricalProject = { historical ->
        if (!historical.isAvailable || isWorking) return@WorkspaceHomeScreen
        CodeEditorTestNavArgument(projectId = historical.project.projectId).navigateFromWorkspaceHome()
      },
      onOpenHistoricalProjectDirectory = { historical ->
        val directory = historical.directory ?: return@WorkspaceHomeScreen
        openProjectDirectory(directory).onFailure {
          errorMessage = it.message ?: "无法打开项目目录。"
        }
      },
      onToggleProjectPinned = { historical ->
        if (isWorking) return@WorkspaceHomeScreen
        isWorking = true
        coroutineScope.launch {
          runCatching {
            projectRepository.setProjectPinned(
              projectId = historical.project.projectId,
              isPinned = !historical.project.isPinned,
            )
          }.onSuccess {
            historicalProjects = projectRepository.historicalProjects()
          }.onFailure {
            errorMessage = it.message ?: "更新置顶状态失败。"
          }
          isWorking = false
        }
      },
      onRemoveHistoricalProject = { historical ->
        if (isWorking) return@WorkspaceHomeScreen
        isWorking = true
        coroutineScope.launch {
          runCatching { projectRepository.forgetProject(historical.project.projectId) }
            .onSuccess { historicalProjects = projectRepository.historicalProjects() }
            .onFailure { errorMessage = it.message ?: "移除历史项目失败。" }
          isWorking = false
        }
      },
    )

    when (selectionMode) {
      WorkspaceSelectionMode.PROJECT -> {
        WorkspaceLanguageDialog(
          title = "创建项目",
          subtitle = "选择项目使用的语言",
          languageIconCache = languageIconCache,
          choices = CodeProjectTemplates.all.map { template ->
            WorkspaceLanguageChoice(
              languageId = template.languageId,
              displayName = template.displayName,
              description = "创建 ${template.defaultProjectName}",
            )
          },
          isLoading = isWorking,
          onDismiss = { if (!isWorking) selectionMode = null },
          onSelect = { choice ->
            val template = CodeProjectTemplates.find(choice.languageId) ?: return@WorkspaceLanguageDialog
            selectionMode = null
            projectNameError = null
            projectTemplateForNaming = template
          },
        )
      }

      WorkspaceSelectionMode.TUTORIAL -> {
        WorkspaceLanguageDialog(
          title = "打开教程",
          subtitle = "选择要学习的编程语言",
          languageIconCache = languageIconCache,
          choices = tutorialChoices?.map(DynamicTutorialInfo::toWorkspaceChoice).orEmpty(),
          isLoading = isLoadingTutorialChoices,
          emptyMessage = errorMessage?.takeIf { tutorialChoices == null },
          onDismiss = { selectionMode = null },
          onSelect = { choice ->
            selectionMode = null
            CodeEditorTestNavArgument(tutorialLanguageId = choice.languageId).navigateFromWorkspaceHome()
          },
        )
      }

      null -> Unit
    }

    projectTemplateForNaming?.let { template ->
      WorkspaceProjectNameDialog(
        languageName = template.displayName,
        initialName = template.defaultProjectName,
        isWorking = isWorking,
        errorMessage = projectNameError,
        onDismiss = {
          if (!isWorking) {
            projectTemplateForNaming = null
            projectNameError = null
          }
        },
        onConfirm = { projectName ->
          if (isWorking) return@WorkspaceProjectNameDialog
          isWorking = true
          projectNameError = null
          coroutineScope.launch {
            runCatching { projectRepository.createProject(template, projectName) }
              .onSuccess { workspace ->
                historicalProjects = projectRepository.historicalProjects()
                projectTemplateForNaming = null
                CodeEditorTestNavArgument(projectId = workspace.project.projectId)
                  .navigateFromWorkspaceHome()
              }
              .onFailure { projectNameError = it.message ?: "创建项目失败。" }
            isWorking = false
          }
        },
      )
    }
  }
}

@Composable
private fun WorkspaceHomeScreen(
  historicalProjects: List<HistoricalCodeProject>?,
  languageIconCache: DynamicLanguageFileIconCache,
  isWorking: Boolean,
  errorMessage: String?,
  onCreateProject: () -> Unit,
  onImportProject: () -> Unit,
  onOpenTutorial: () -> Unit,
  onOpenHistoricalProject: (HistoricalCodeProject) -> Unit,
  onOpenHistoricalProjectDirectory: (HistoricalCodeProject) -> Unit,
  onToggleProjectPinned: (HistoricalCodeProject) -> Unit,
  onRemoveHistoricalProject: (HistoricalCodeProject) -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxSize(),
    color = WorkspaceHomeColors.background,
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
      Text(
        text = "代码工作区",
        color = WorkspaceHomeColors.primaryText,
        fontSize = 21.sp,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text = "创建或打开本地项目，也可以从教程开始学习",
        modifier = Modifier.padding(top = 4.dp),
        color = WorkspaceHomeColors.secondaryText,
        fontSize = 12.sp,
      )

      WorkspaceActions(
        modifier = Modifier.padding(top = 24.dp),
        enabled = !isWorking,
        onCreateProject = onCreateProject,
        onImportProject = onImportProject,
        onOpenTutorial = onOpenTutorial,
      )
      Text(
        text = "历史项目",
        modifier = Modifier.padding(top = 26.dp, bottom = 10.dp),
        color = WorkspaceHomeColors.primaryText,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
      )
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
      ) {
        when {
          historicalProjects == null -> CircularProgressIndicator(
            modifier = Modifier
              .align(Alignment.Center)
              .size(18.dp),
            color = WorkspaceHomeColors.accent,
            strokeWidth = 2.dp,
          )

          historicalProjects.isEmpty() -> Text(
            text = "还没有历史项目，创建或打开项目后会显示在这里。",
            modifier = Modifier.align(Alignment.Center),
            color = WorkspaceHomeColors.secondaryText,
            fontSize = 11.sp,
          )

          else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(
              items = historicalProjects,
              key = { it.project.projectId },
            ) { historical ->
              HistoricalProjectRow(
                historical = historical,
                languageIconCache = languageIconCache,
                enabled = !isWorking,
                onClick = { onOpenHistoricalProject(historical) },
                onOpenDirectory = { onOpenHistoricalProjectDirectory(historical) },
                onTogglePinned = { onToggleProjectPinned(historical) },
                onRemove = { onRemoveHistoricalProject(historical) },
              )
            }
          }
        }
      }

      errorMessage?.let { message ->
        Text(
          text = message,
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
          color = WorkspaceHomeColors.error,
          fontSize = 11.sp,
        )
      }
    }
  }
}

/** 读取真实项目目录期间的稳定占位，失败时允许重试或返回入口页。 */
@Composable
internal fun WorkspaceProjectLoading(
  errorMessage: String?,
  onRetry: () -> Unit,
  onBack: () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxSize(),
    color = WorkspaceHomeColors.background,
  ) {
    Box(contentAlignment = Alignment.Center) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (errorMessage == null) {
          CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            color = WorkspaceHomeColors.accent,
            strokeWidth = 2.dp,
          )
          Text(
            text = "正在打开本地项目…",
            modifier = Modifier.padding(top = 12.dp),
            color = WorkspaceHomeColors.secondaryText,
            fontSize = 11.sp,
          )
        } else {
          Text(
            text = errorMessage,
            color = WorkspaceHomeColors.error,
            fontSize = 12.sp,
          )
          Row(modifier = Modifier.padding(top = 14.dp)) {
            Text(
              text = "返回入口",
              modifier = Modifier
                .clickable(onClick = onBack)
                .padding(horizontal = 12.dp, vertical = 7.dp),
              color = WorkspaceHomeColors.secondaryText,
              fontSize = 11.sp,
            )
            Text(
              text = "重新读取",
              modifier = Modifier
                .clickable(onClick = onRetry)
                .padding(horizontal = 12.dp, vertical = 7.dp),
              color = WorkspaceHomeColors.accent,
              fontSize = 11.sp,
            )
          }
        }
      }
    }
  }
}

/**
 * 三个入口始终保持单行；手机端使用紧凑卡片，避免项目与教程入口因宽度不足换行。
 */
@Composable
private fun WorkspaceActions(
  enabled: Boolean,
  onCreateProject: () -> Unit,
  onImportProject: () -> Unit,
  onOpenTutorial: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.fillMaxWidth(),
    contentAlignment = Alignment.Center,
  ) {
    Row(
      modifier = Modifier
        .widthIn(max = 560.dp)
        .fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      WorkspaceActionCard(
        title = "创建项目",
        icon = { Icon(Icons.Default.Add, contentDescription = null) },
        enabled = enabled,
        onClick = onCreateProject,
        modifier = Modifier.weight(1f),
      )
      WorkspaceActionCard(
        title = "打开项目",
        icon = { Icon(Icons.Default.Folder, contentDescription = null) },
        enabled = enabled,
        onClick = onImportProject,
        modifier = Modifier.weight(1f),
      )
      WorkspaceActionCard(
        title = "打开教程",
        icon = { Icon(Icons.Default.School, contentDescription = null) },
        enabled = enabled,
        onClick = onOpenTutorial,
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun WorkspaceActionCard(
  title: String,
  icon: @Composable () -> Unit,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier
      .height(64.dp)
      .clickable(enabled = enabled, onClick = onClick),
    shape = RoundedCornerShape(12.dp),
    color = WorkspaceHomeColors.panel,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 9.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        modifier = Modifier.size(28.dp),
        shape = RoundedCornerShape(8.dp),
        color = WorkspaceHomeColors.accent.copy(alpha = 0.16f),
        contentColor = WorkspaceHomeColors.accent,
      ) {
        Box(contentAlignment = Alignment.Center) {
          icon()
        }
      }
      Spacer(Modifier.width(6.dp))
      Text(
        text = title,
        color = WorkspaceHomeColors.primaryText,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun HistoricalProjectRow(
  historical: HistoricalCodeProject,
  languageIconCache: DynamicLanguageFileIconCache,
  enabled: Boolean,
  onClick: () -> Unit,
  onOpenDirectory: () -> Unit,
  onTogglePinned: () -> Unit,
  onRemove: () -> Unit,
) {
  val contentAlpha = if (historical.isAvailable) 1f else 0.45f
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .defaultMinSize(minHeight = 82.dp)
      .clickable(enabled = enabled && historical.isAvailable, onClick = onClick),
    shape = RoundedCornerShape(10.dp),
    color = WorkspaceHomeColors.panel,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val languageIcon = languageIconCache[historical.project.languageId]
      Icon(
        imageVector = languageIcon ?: Icons.Default.Code,
        contentDescription = null,
        modifier = Modifier.size(28.dp),
        tint = if (languageIcon == null) {
          WorkspaceHomeColors.accent.copy(alpha = contentAlpha)
        } else {
          Color.Unspecified
        },
      )
      Spacer(Modifier.width(12.dp))
      Column(
        modifier = Modifier
          .weight(1f)
          .padding(end = 8.dp),
      ) {
        Text(
          text = historical.project.name,
          color = WorkspaceHomeColors.primaryText.copy(alpha = contentAlpha),
          fontSize = 14.sp,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = historical.project.languageId.uppercase(),
          modifier = Modifier.padding(top = 3.dp),
          color = WorkspaceHomeColors.accent.copy(alpha = contentAlpha),
          fontSize = 10.sp,
        )
        Text(
          text = if (historical.isAvailable) {
            historical.directoryDisplayPath
          } else {
            "项目目录不可访问"
          },
          modifier = Modifier
            .padding(top = 3.dp)
            .clickable(enabled = enabled && historical.isAvailable, onClick = onOpenDirectory),
          color = WorkspaceHomeColors.secondaryText.copy(alpha = contentAlpha),
          fontSize = 9.sp,
        )
      }
      IconButton(
        modifier = Modifier.size(34.dp),
        enabled = enabled && historical.isAvailable,
        onClick = onTogglePinned,
      ) {
        Icon(
          imageVector = if (historical.project.isPinned) {
            Icons.Default.Star
          } else {
            Icons.Default.StarBorder
          },
          contentDescription = if (historical.project.isPinned) "取消置顶" else "置顶项目",
          modifier = Modifier.size(18.dp),
          tint = if (historical.project.isPinned) {
            WorkspaceHomeColors.accent
          } else {
            WorkspaceHomeColors.secondaryText
          },
        )
      }
      IconButton(
        modifier = Modifier.size(34.dp),
        enabled = enabled,
        onClick = onRemove,
      ) {
        Icon(
          imageVector = Icons.Default.Delete,
          contentDescription = "从历史项目中移除",
          modifier = Modifier.size(18.dp),
          tint = WorkspaceHomeColors.secondaryText,
        )
      }
    }
  }
}

/**
 * 语言选择后的项目命名步骤。
 *
 * 名称只作为项目展示信息，不直接拼入目录；确认后仍由仓库执行重复名称和控制字符校验。
 */
@Composable
private fun WorkspaceProjectNameDialog(
  languageName: String,
  initialName: String,
  isWorking: Boolean,
  errorMessage: String?,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var projectName by remember(initialName) { mutableStateOf(initialName) }
  val canConfirm = projectName.isNotBlank() && !isWorking

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(14.dp),
      color = WorkspaceHomeColors.panel,
    ) {
      Column(modifier = Modifier.padding(18.dp)) {
        Text(
          text = "创建 $languageName 项目",
          color = WorkspaceHomeColors.primaryText,
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = "输入项目名称",
          modifier = Modifier.padding(top = 4.dp),
          color = WorkspaceHomeColors.secondaryText,
          fontSize = 10.sp,
        )
        BasicTextField(
          value = projectName,
          onValueChange = { value ->
            projectName = value.take(MAX_PROJECT_NAME_INPUT_LENGTH)
          },
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
          enabled = !isWorking,
          singleLine = true,
          textStyle = TextStyle(
            color = WorkspaceHomeColors.primaryText,
            fontSize = 13.sp,
          ),
          cursorBrush = SolidColor(WorkspaceHomeColors.accent),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
          keyboardActions = KeyboardActions(
            onDone = {
              if (canConfirm) onConfirm(projectName)
            },
          ),
          decorationBox = { innerTextField ->
            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 42.dp),
              shape = RoundedCornerShape(8.dp),
              color = WorkspaceHomeColors.input,
            ) {
              Box(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                contentAlignment = Alignment.CenterStart,
              ) {
                if (projectName.isEmpty()) {
                  Text(
                    text = "项目名称",
                    color = WorkspaceHomeColors.secondaryText,
                    fontSize = 13.sp,
                  )
                }
                innerTextField()
              }
            }
          },
        )
        Text(
          text = "${projectName.length}/$MAX_PROJECT_NAME_INPUT_LENGTH",
          modifier = Modifier
            .align(Alignment.End)
            .padding(top = 4.dp),
          color = WorkspaceHomeColors.secondaryText,
          fontSize = 9.sp,
        )
        errorMessage?.let { message ->
          Text(
            text = message,
            modifier = Modifier.padding(top = 8.dp),
            color = WorkspaceHomeColors.error,
            fontSize = 10.sp,
          )
        }
        Row(
          modifier = Modifier
            .align(Alignment.End)
            .padding(top = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "取消",
            modifier = Modifier
              .clickable(enabled = !isWorking, onClick = onDismiss)
              .padding(horizontal = 12.dp, vertical = 8.dp),
            color = WorkspaceHomeColors.secondaryText,
            fontSize = 11.sp,
          )
          Surface(
            modifier = Modifier
              .padding(start = 4.dp)
              .clickable(enabled = canConfirm) { onConfirm(projectName) },
            shape = RoundedCornerShape(7.dp),
            color = WorkspaceHomeColors.accent.copy(alpha = if (canConfirm) 1f else 0.4f),
          ) {
            Text(
              text = if (isWorking) "创建中…" else "创建",
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
              color = WorkspaceHomeColors.primaryText,
              fontSize = 11.sp,
              fontWeight = FontWeight.Medium,
            )
          }
        }
      }
    }
  }
}

/** 项目与教程共用的小型语言选择框，后续增加语言只需要更新动态目录或模板表。 */
@Composable
internal fun WorkspaceLanguageDialog(
  title: String,
  subtitle: String,
  choices: List<WorkspaceLanguageChoice>,
  isLoading: Boolean,
  languageIconCache: DynamicLanguageFileIconCache? = null,
  emptyMessage: String? = null,
  onDismiss: () -> Unit,
  onSelect: (WorkspaceLanguageChoice) -> Unit,
) {
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(14.dp),
      color = WorkspaceHomeColors.panel,
    ) {
      Column(modifier = Modifier.padding(vertical = 14.dp)) {
        Text(
          text = title,
          modifier = Modifier.padding(horizontal = 18.dp),
          color = WorkspaceHomeColors.primaryText,
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = subtitle,
          modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
          color = WorkspaceHomeColors.secondaryText,
          fontSize = 10.sp,
        )
        Spacer(Modifier.height(6.dp))
        when {
          isLoading -> {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(86.dp),
              contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = WorkspaceHomeColors.accent,
                strokeWidth = 2.dp,
              )
            }
          }

          choices.isEmpty() -> {
            Text(
              text = emptyMessage ?: "暂时没有可用语言。",
              modifier = Modifier.padding(horizontal = 18.dp, vertical = 24.dp),
              color = WorkspaceHomeColors.secondaryText,
              fontSize = 11.sp,
            )
          }

          else -> choices.forEach { choice ->
            val languageIcon = languageIconCache?.get(choice.languageId)
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(choice) }
                .padding(horizontal = 18.dp, vertical = 11.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = languageIcon ?: Icons.Default.Code,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (languageIcon == null) WorkspaceHomeColors.accent else Color.Unspecified,
              )
              Spacer(Modifier.width(12.dp))
              Column {
                Text(
                  text = choice.displayName,
                  color = WorkspaceHomeColors.primaryText,
                  fontSize = 13.sp,
                )
                Text(
                  text = choice.description,
                  modifier = Modifier.padding(top = 2.dp),
                  color = WorkspaceHomeColors.secondaryText,
                  fontSize = 9.sp,
                )
              }
            }
          }
        }
      }
    }
  }
}

private fun DynamicTutorialInfo.toWorkspaceChoice(): WorkspaceLanguageChoice =
  WorkspaceLanguageChoice(
    languageId = languageId,
    displayName = displayName,
    description = "打开 $displayName 学习路径",
  )

/**
 * 从入口页进入具体工作区，并同步移除入口页自身。
 *
 * 先压入目标再移除入口，可以保证任何时刻栈中都至少保留目标页面；工作区返回时会直接回到
 * 打开代码入口前的上一级页面，而不会再次经过入口页。
 */
private fun CodeEditorTestNavArgument.navigateFromWorkspaceHome() {
  navigate()
  CodeWorkspaceHomeNavArgument.popBackStack()
}

private enum class WorkspaceSelectionMode {
  PROJECT,
  TUTORIAL,
}

internal data class WorkspaceLanguageChoice(
  val languageId: String,
  val displayName: String,
  val description: String,
)

private object WorkspaceHomeColors {
  val background = Color(0xFF0D121C)
  val panel = Color(0xFF192231)
  val input = Color(0xFF111925)
  val primaryText = Color(0xFFF0F3FA)
  val secondaryText = Color(0xFF98A4B7)
  val accent = Color(0xFF8A74FF)
  val error = Color(0xFFFF8E98)
}

private const val MAX_PROJECT_NAME_INPUT_LENGTH = 64
