package com.cyxbs.functions.code.tutorials.java

import com.cyxbs.functions.code.language.java.JavaDynamicLanguageService
import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationRequest
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageWorkspace
import com.cyxbs.functions.code.language.js.bridge.DynamicProgramEntry
import com.cyxbs.functions.code.language.js.bridge.DynamicSourceFile
import com.cyxbs.functions.code.npm.js.bridge.NpmJsBridgeHostAbi
import com.cyxbs.functions.code.npm.js.bridge.NpmJsResult
import com.cyxbs.functions.code.npm.js.bridge.encodeNpmJsResult
import com.cyxbs.functions.code.npm.storage.NpmStorageHostAbi
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialCompletedStep
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialEvaluationRequest
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialProgress
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.promise
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.js.undefined
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 验证首个 Java 教程包的目录、正文和运行结果校验保持一致。 */
class JavaDynamicTutorialServiceTest {

  @Test
  fun exposesOrderedCoursePath() = runTest {
    val manifest = JavaDynamicTutorialService.manifest().getOrThrow()

    assertEquals("java", manifest.languageId)
    assertEquals(
      listOf(
        "java-getting-started",
        "java-control-flow",
        "java-object-oriented",
        "java-generics-collections",
      ),
      manifest.courses.map { it.courseId },
    )
    assertEquals(
      listOf("java-object-oriented"),
      manifest.courses.last().prerequisiteCourseIds,
    )
    manifest.courses.forEach { summary ->
      val course = assertNotNull(JavaDynamicTutorialService.course(summary.courseId).getOrThrow())
      assertEquals(course.lessons.map { it.lessonId }, summary.lessons.map { it.lessonId })
      assertEquals(
        course.lessons.map { lesson -> lesson.steps.map { it.stepId } },
        summary.lessons.map { it.stepIds },
      )
    }
  }

  @Test
  fun evaluatesHelloWorldOutput() = runTest {
    val course = assertNotNull(JavaDynamicTutorialService.course("java-getting-started").getOrThrow())
    val lesson = course.lessons.single()
    val request = DynamicTutorialEvaluationRequest(
      courseId = course.summary.courseId,
      lessonId = lesson.lessonId,
      stepId = "run-main",
      workspace = lesson.initialFiles,
      runExecuted = true,
      standardOutput = "Hello, Java!\n",
    )

    assertTrue(JavaDynamicTutorialService.evaluate(request).getOrThrow().completed)
    assertFalse(
      JavaDynamicTutorialService.evaluate(request.copy(standardOutput = "")).getOrThrow().completed,
    )
  }

  @Test
  fun exposesMultipleIndependentCollectionLessons() = runTest {
    val course = assertNotNull(
      JavaDynamicTutorialService.course("java-generics-collections").getOrThrow(),
    )

    assertEquals(listOf("typed-list", "score-map"), course.lessons.map { it.lessonId })
    assertEquals("src/CourseScores.java", course.lessons.last().activeFilePath)
  }

  @Test
  fun acceptsAnyAdditionalCourseName() = runTest {
    val course = assertNotNull(
      JavaDynamicTutorialService.course("java-generics-collections").getOrThrow(),
    )
    val lesson = course.lessons.first { it.lessonId == "typed-list" }
    val request = DynamicTutorialEvaluationRequest(
      courseId = course.summary.courseId,
      lessonId = lesson.lessonId,
      stepId = "add-course",
      workspace = lesson.initialFiles,
    )

    assertFalse(JavaDynamicTutorialService.evaluate(request).getOrThrow().completed)
    val customizedWorkspace = lesson.initialFiles.map { file ->
      if (file.path != lesson.activeFilePath) {
        file
      } else {
        file.copy(
          source = file.source.replace(
            "courses.add(\"集合\");",
            "courses.add(\"集合\");\n        courses.add(\"数据结构\");",
          ),
        )
      }
    }
    assertTrue(
      JavaDynamicTutorialService.evaluate(request.copy(workspace = customizedWorkspace))
        .getOrThrow()
        .completed,
    )
  }

  /** 同一课程保存新快照时应覆盖旧值，同时不影响其他课程。 */
  @Test
  fun persistsProgressInsidePackageSettings() = runTest {
    withProgressStorage {
      val intro = progress("java-getting-started", "hello-world", "read-main")
      val collections = progress("java-generics-collections", "typed-list", "add-course")

      JavaDynamicTutorialService.saveProgress(intro).getOrThrow()
      JavaDynamicTutorialService.saveProgress(collections).getOrThrow()
      JavaDynamicTutorialService.saveProgress(intro.copy(stepId = "run-main")).getOrThrow()

      assertEquals(
        listOf("java-generics-collections", "java-getting-started"),
        JavaDynamicTutorialService.savedProgress().getOrThrow().map { it.courseId },
      )
      assertEquals(
        "run-main",
        JavaDynamicTutorialService.savedProgress().getOrThrow().last().stepId,
      )

      JavaDynamicTutorialService.clearCourseProgress("java-getting-started").getOrThrow()
      assertEquals(
        listOf("java-generics-collections"),
        JavaDynamicTutorialService.savedProgress().getOrThrow().map { it.courseId },
      )
      JavaDynamicTutorialService.clearProgress().getOrThrow()
      assertTrue(JavaDynamicTutorialService.savedProgress().getOrThrow().isEmpty())
    }
  }

  /** npm 包升级后应由包内迁移器修剪已删除步骤，并把当前位置移动到当前目录。 */
  @Test
  fun migratesRemovedLessonAndStepIdsBeforeReturningProgress() = runTest {
    withProgressStorage { storedSettings ->
      JavaDynamicTutorialService.saveProgress(
        progress("java-getting-started", "hello-world", "read-main").copy(
          completedSteps = listOf(
            DynamicTutorialCompletedStep("hello-world", "read-main"),
            DynamicTutorialCompletedStep("removed-lesson", "removed-step"),
          ),
        ),
      ).getOrThrow()
      val serialized = assertNotNull(storedSettings[PROGRESS_SETTINGS_KEY])
      storedSettings[PROGRESS_SETTINGS_KEY] = serialized
        .replaceFirst("\"lessonId\":\"hello-world\"", "\"lessonId\":\"removed-lesson\"")
        .replaceFirst("\"stepId\":\"read-main\"", "\"stepId\":\"removed-step\"")

      val migrated = JavaDynamicTutorialService.savedProgress().getOrThrow().single()

      assertEquals("hello-world", migrated.lessonId)
      assertEquals("run-main", migrated.stepId)
      assertEquals(
        listOf(DynamicTutorialCompletedStep("hello-world", "read-main")),
        migrated.completedSteps,
      )
      assertFalse(assertNotNull(storedSettings[PROGRESS_SETTINGS_KEY]).contains("removed-lesson"))
    }
  }

  @Test
  fun exposesMultiFileObjectAndInheritanceLessons() = runTest {
    val course = assertNotNull(JavaDynamicTutorialService.course("java-object-oriented").getOrThrow())

    assertEquals(
      listOf("student-object", "inheritance-override"),
      course.lessons.map { it.lessonId },
    )
    assertEquals(
      listOf("src/Animal.java", "src/Dog.java", "src/AnimalMain.java"),
      course.lessons.last().initialFiles.map { it.path },
    )
    assertTrue(
      JavaDynamicTutorialService.evaluate(
        DynamicTutorialEvaluationRequest(
          courseId = course.summary.courseId,
          lessonId = "inheritance-override",
          stepId = "run-override",
          workspace = course.lessons.last().initialFiles,
          runExecuted = true,
          standardOutput = "旺财：汪\n",
        ),
      ).getOrThrow().completed,
    )
  }

  @Test
  fun allTutorialExamplesCompileWithThePublishedJavaLanguageService() = runTest {
    JavaDynamicTutorialService.manifest().getOrThrow().courses.forEach { summary ->
      val course = assertNotNull(JavaDynamicTutorialService.course(summary.courseId).getOrThrow())
      course.lessons.forEach { lesson ->
        val activeSource = lesson.initialFiles.single { it.path == lesson.activeFilePath }.source
        val entryPosition = activeSource.indexOf("public static void main")
        assertTrue(entryPosition >= 0, "${lesson.lessonId} 缺少 public static void main 入口。")
        val result = JavaDynamicLanguageService.compile(
          DynamicCompilationRequest(
            workspace = DynamicLanguageWorkspace(
              lesson.initialFiles.map { sourceFile ->
                DynamicSourceFile(sourceFile.path, sourceFile.source)
              },
            ),
            entry = DynamicProgramEntry(
              filePath = lesson.activeFilePath,
              position = entryPosition,
            ),
          ),
        ).getOrThrow()

        assertNotNull(
          result.program,
          "${summary.courseId}/${lesson.lessonId} 编译失败：" +
            result.diagnostics.joinToString { it.message },
        )
      }
    }
  }

  /** 构造与当前 Java 教程包身份匹配的最小进度快照。 */
  private fun progress(
    courseId: String,
    lessonId: String,
    stepId: String,
  ): DynamicTutorialProgress = DynamicTutorialProgress(
    languageId = "java",
    npmPackageName = "@cyxbs-mobile/tutorial-java",
    npmPackageVersion = "0.1.0-test",
    courseId = courseId,
    lessonId = lessonId,
    stepId = stepId,
  )

  /**
   * 安装仅覆盖包级 Settings 的临时宿主网关。
   *
   * 测试结束后必须移除全局函数，避免 Kotlin/JS 测试之间共享桥状态。
   */
  private suspend fun withProgressStorage(
    block: suspend (MutableMap<String, String>) -> Unit,
  ) {
    val storedSettings = mutableMapOf<String, String>()
    val global: dynamic = js("globalThis")
    val scope = MainScope()
    global[NpmJsBridgeHostAbi.GATEWAY] = {
        operation: String,
        bridgeId: String,
        methodName: String?,
        argumentsJson: String?,
      ->
      scope.promise {
        require(bridgeId == STORAGE_BRIDGE_ID)
        when (operation) {
          NpmJsBridgeHostAbi.DESCRIBE -> """{"ok":true,"methods":["invoke"]}"""
          NpmJsBridgeHostAbi.INVOKE -> {
            require(methodName == "invoke")
            val requestJson = Json.parseToJsonElement(requireNotNull(argumentsJson))
              .jsonArray.single().jsonPrimitive.content
            val request = Json.parseToJsonElement(requestJson).jsonObject
            buildJsonObject {
              put("ok", true)
              put(
                "result",
                encodeNpmJsResult(
                  NpmJsResult.success(handleStorageRequest(request, storedSettings)),
                  ::JsonPrimitive,
                ),
              )
            }.toString()
          }
          else -> error("Unknown test bridge operation: $operation")
        }
      }
    }
    try {
      block(storedSettings)
    } finally {
      global[NpmJsBridgeHostAbi.GATEWAY] = undefined
      scope.cancel()
    }
  }

  /** 执行 Java 教程进度所需的最小 Settings 协议，并把状态保留在测试内存中。 */
  private fun handleStorageRequest(
    request: JsonObject,
    storedSettings: MutableMap<String, String>,
  ): String {
    require(request.getValue("scope").jsonPrimitive.content == NpmStorageHostAbi.SCOPE_PACKAGE)
    val key = request.getValue("key").jsonPrimitive.content
    return when (request.getValue("operation").jsonPrimitive.content) {
      NpmStorageHostAbi.SETTINGS_GET_STRING -> storageResponse(
        storedSettings[key]?.let(::JsonPrimitive) ?: JsonNull,
      )
      NpmStorageHostAbi.SETTINGS_PUT_STRING -> {
        storedSettings[key] = request.getValue("stringValue").jsonPrimitive.content
        storageResponse()
      }
      NpmStorageHostAbi.SETTINGS_REMOVE -> {
        storedSettings.remove(key)
        storageResponse()
      }
      else -> error("Unexpected tutorial storage operation: $request")
    }
  }

  /** 构造 Storage Bridge 约定的成功 JSON。 */
  private fun storageResponse(value: kotlinx.serialization.json.JsonElement? = null): String =
    buildJsonObject {
      put("ok", true)
      value?.let { put("value", it) }
    }.toString()

  private companion object {
    const val STORAGE_BRIDGE_ID = "com.cyxbs.functions.code.npm.storage.NpmStorageBridge"
    const val PROGRESS_SETTINGS_KEY = "tutorial.progress"
  }
}
