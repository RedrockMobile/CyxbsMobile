package com.cyxbs.functions.code.tutorials.java

import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialAnchorIds
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialCompletionKind
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialCompletionRule
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialContentBlock
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialContentKind
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialCourse
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialCourseSummary
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialEvaluationRequest
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialEvaluationResult
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialGuideTarget
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialGuideTargetKind
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialLesson
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialManifest
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialService
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialSourceFile
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialStep
import com.cyxbs.functions.code.tutorials.js.bridge.withGeneratedLessonSummaries
import com.cyxbs.functions.code.npm.js.bridge.NpmJsResult
import com.cyxbs.functions.code.npm.js.bridge.npmJsCatching

/** Java 教程 npm 包入口；课程正文、示例源码与完成规则随 npm 包动态更新。 */
object JavaDynamicTutorialService : DynamicTutorialService {
  private val courses = listOf(
    helloCourse(),
    controlFlowCourse(),
    objectOrientedCourse(),
    collectionsCourse(),
  ).map(DynamicTutorialCourse::withGeneratedLessonSummaries)

  /** 返回 Java 课程路径，不传输课时正文与源码。 */
  override suspend fun manifest(): NpmJsResult<DynamicTutorialManifest> = npmJsCatching {
    DynamicTutorialManifest(
      languageId = "java",
      courses = courses.map(DynamicTutorialCourse::summary),
    )
  }

  /** 按稳定 ID 查找课程，未知 ID 保持为空以兼容 Catalog 与包更新的短暂差异。 */
  override suspend fun course(courseId: String): NpmJsResult<DynamicTutorialCourse?> = npmJsCatching {
    courses.firstOrNull { it.summary.courseId == courseId }
  }

  /** 使用步骤自身的声明式规则判断源码或运行结果，避免客户端复制语言相关字符串。 */
  override suspend fun evaluate(
    request: DynamicTutorialEvaluationRequest,
  ): NpmJsResult<DynamicTutorialEvaluationResult> = npmJsCatching {
    evaluateStep(request)
  }

  /** 执行教程包内声明式检查，协议层由 [evaluate] 统一捕获异常。 */
  private fun evaluateStep(
    request: DynamicTutorialEvaluationRequest,
  ): DynamicTutorialEvaluationResult {
    val step = courses.firstOrNull { it.summary.courseId == request.courseId }
      ?.lessons?.firstOrNull { it.lessonId == request.lessonId }
      ?.steps?.firstOrNull { it.stepId == request.stepId }
      ?: return DynamicTutorialEvaluationResult(false, "教程步骤已更新，请重新进入当前课程。")
    val completion = step.completion
    val completed = if (request.courseId == COLLECTIONS_COURSE_ID &&
      request.lessonId == TYPED_LIST_LESSON_ID &&
      request.stepId == ADD_COURSE_STEP_ID
    ) {
      hasAdditionalCourse(request)
    } else {
      when (completion.kind) {
        DynamicTutorialCompletionKind.MANUAL -> true
        DynamicTutorialCompletionKind.RUN_SUCCEEDED -> request.runExecuted
        DynamicTutorialCompletionKind.OUTPUT_CONTAINS ->
          request.runExecuted && completion.expected.orEmpty() in request.standardOutput
        DynamicTutorialCompletionKind.SOURCE_CONTAINS -> {
          val source = request.workspace.firstOrNull { it.path == completion.filePath }?.source
          source != null && completion.expected.orEmpty() in source
        }
      }
    }
    return DynamicTutorialEvaluationResult(
      completed = completed,
      feedback = if (completed) null else "还没有满足这一步的要求，可以根据提示继续修改或运行。",
    )
  }

  /**
   * 校验列表课时是否新增了第三次 `courses.add(...)` 调用。
   *
   * 这里只约束教学动作，不限定课程名称；用户可以填写任意字符串。轻量正则允许常见的空格和换行，
   * 避免教程包为了一个步骤重复引入 Java 语法分析器。
   */
  private fun hasAdditionalCourse(request: DynamicTutorialEvaluationRequest): Boolean {
    val source = request.workspace.firstOrNull { it.path == COURSE_LIST_FILE_PATH }?.source
      ?: return false
    return COURSE_ADD_PATTERN.findAll(source).count() >= INITIAL_COURSE_COUNT + 1
  }

  /** 构造第一张课程卡片，重点验证 Tutorial 工具窗口与运行按钮引导。 */
  private fun helloCourse(): DynamicTutorialCourse {
    val source = """
      public class Main {
          public static void main(String[] args) {
              System.out.println("Hello, Java!");
          }
      }
    """.trimIndent()
    val mainFrom = source.indexOf("public static void main")
    val summary = DynamicTutorialCourseSummary(
      courseId = "java-getting-started",
      title = "Java 起步",
      description = "认识 main 方法，并使用 System.out 输出第一行内容。",
      order = 10,
      estimatedMinutes = 8,
    )
    return DynamicTutorialCourse(
      summary = summary,
      lessons = listOf(
        DynamicTutorialLesson(
          lessonId = "hello-world",
          title = "Hello, Java",
          description = "运行第一个 Java 程序。",
          initialFiles = listOf(
            DynamicTutorialSourceFile(
              path = "src/Main.java",
              source = source,
            ),
          ),
          activeFilePath = "src/Main.java",
          steps = listOf(
            DynamicTutorialStep(
              stepId = "read-main",
              title = "找到程序入口",
              content = listOf(
                DynamicTutorialContentBlock(
                  kind = DynamicTutorialContentKind.PARAGRAPH,
                  text = "main 是 Java 程序的入口。先观察它，再运行当前程序。",
                ),
              ),
              guideTarget = DynamicTutorialGuideTarget(
                kind = DynamicTutorialGuideTargetKind.EDITOR_RANGE,
                filePath = "src/Main.java",
                from = mainFrom,
                to = mainFrom + "public static void main".length,
              ),
            ),
            DynamicTutorialStep(
              stepId = "run-main",
              title = "运行程序",
              content = listOf(
                DynamicTutorialContentBlock(
                  kind = DynamicTutorialContentKind.TIP,
                  text = "点击顶部运行按钮，并在 Run 工具窗口中查看输出。",
                ),
              ),
              guideTarget = DynamicTutorialGuideTarget(
                kind = DynamicTutorialGuideTargetKind.LAYOUT_ANCHOR,
                anchorId = DynamicTutorialAnchorIds.RUN_BUTTON,
              ),
              completion = DynamicTutorialCompletionRule(
                kind = DynamicTutorialCompletionKind.OUTPUT_CONTAINS,
                expected = "Hello, Java!",
              ),
            ),
          ),
        ),
      ),
    )
  }

  /** 构造流程控制课程，示例保持在当前 Java 运行子集内。 */
  private fun controlFlowCourse(): DynamicTutorialCourse {
    val summary = DynamicTutorialCourseSummary(
      courseId = "java-control-flow",
      title = "流程控制",
      description = "使用循环、条件和变量完成一个求和程序。",
      order = 20,
      estimatedMinutes = 12,
      prerequisiteCourseIds = listOf("java-getting-started"),
    )
    return DynamicTutorialCourse(
      summary = summary,
      lessons = listOf(
        DynamicTutorialLesson(
          lessonId = "loop-sum",
          title = "循环求和",
          description = "修改循环范围并观察输出。",
          initialFiles = listOf(
            DynamicTutorialSourceFile(
              path = "src/LoopSum.java",
              source = """
                public class LoopSum {
                    public static void main(String[] args) {
                        int sum = 0;
                        for (int value = 1; value <= 5; value++) {
                            sum += value;
                        }
                        System.out.println(sum);
                    }
                }
              """.trimIndent(),
            ),
          ),
          activeFilePath = "src/LoopSum.java",
          steps = listOf(
            DynamicTutorialStep(
              stepId = "run-loop",
              title = "验证循环结果",
              content = listOf(
                DynamicTutorialContentBlock(
                  kind = DynamicTutorialContentKind.PARAGRAPH,
                  text = "运行程序，确认 1 到 5 的累加结果为 15。",
                ),
              ),
              completion = DynamicTutorialCompletionRule(
                kind = DynamicTutorialCompletionKind.OUTPUT_CONTAINS,
                expected = "15",
              ),
            ),
          ),
        ),
      ),
    )
  }

  /** 构造面向对象课程，以多文件工作区演示对象状态、构造器、继承与重写。 */
  private fun objectOrientedCourse(): DynamicTutorialCourse {
    val summary = DynamicTutorialCourseSummary(
      courseId = "java-object-oriented",
      title = "类、对象与继承",
      description = "在多文件项目中创建对象，并通过继承和方法重写复用行为。",
      order = 30,
      estimatedMinutes = 18,
      prerequisiteCourseIds = listOf("java-control-flow"),
    )
    return DynamicTutorialCourse(
      summary = summary,
      lessons = listOf(
        DynamicTutorialLesson(
          lessonId = "student-object",
          title = "创建带状态的对象",
          description = "使用字段、构造器和实例方法描述一名学生。",
          initialFiles = listOf(
            DynamicTutorialSourceFile(
              path = "src/Student.java",
              source = """
                public class Student {
                    private String name;
                    private int score;

                    public Student(String name, int score) {
                        this.name = name;
                        this.score = score;
                    }

                    public String report() {
                        return name + " 的分数：" + score;
                    }
                }
              """.trimIndent(),
            ),
            DynamicTutorialSourceFile(
              path = "src/StudentMain.java",
              source = """
                public class StudentMain {
                    public static void main(String[] args) {
                        Student student = new Student("小邮", 95);
                        System.out.println(student.report());
                    }
                }
              """.trimIndent(),
            ),
          ),
          activeFilePath = "src/StudentMain.java",
          steps = listOf(
            DynamicTutorialStep(
              stepId = "run-student",
              title = "观察对象状态",
              content = listOf(
                DynamicTutorialContentBlock(
                  kind = DynamicTutorialContentKind.PARAGRAPH,
                  text = "Student 的构造器把姓名和分数保存到字段。运行 StudentMain，观察实例方法如何读取这些状态。",
                ),
              ),
              completion = DynamicTutorialCompletionRule(
                kind = DynamicTutorialCompletionKind.OUTPUT_CONTAINS,
                expected = "小邮 的分数：95",
              ),
            ),
          ),
        ),
        DynamicTutorialLesson(
          lessonId = "inheritance-override",
          title = "继承并重写行为",
          description = "父类引用仍会调用子类重写后的实例方法。",
          initialFiles = listOf(
            DynamicTutorialSourceFile(
              path = "src/Animal.java",
              source = """
                public class Animal {
                    private String name;

                    public Animal(String name) {
                        this.name = name;
                    }

                    public String getName() {
                        return name;
                    }

                    public String sound() {
                        return "未知声音";
                    }
                }
              """.trimIndent(),
            ),
            DynamicTutorialSourceFile(
              path = "src/Dog.java",
              source = """
                public class Dog extends Animal {
                    public Dog(String name) {
                        super(name);
                    }

                    @Override
                    public String sound() {
                        return "汪";
                    }
                }
              """.trimIndent(),
            ),
            DynamicTutorialSourceFile(
              path = "src/AnimalMain.java",
              source = """
                public class AnimalMain {
                    public static void main(String[] args) {
                        Animal animal = new Dog("旺财");
                        System.out.println(animal.getName() + "：" + animal.sound());
                    }
                }
              """.trimIndent(),
            ),
          ),
          activeFilePath = "src/AnimalMain.java",
          steps = listOf(
            DynamicTutorialStep(
              stepId = "run-override",
              title = "验证动态分派",
              content = listOf(
                DynamicTutorialContentBlock(
                  kind = DynamicTutorialContentKind.PARAGRAPH,
                  text = "变量类型是 Animal，实际对象是 Dog。运行程序，确认 sound 调用了 Dog 中的重写实现。",
                ),
                DynamicTutorialContentBlock(
                  kind = DynamicTutorialContentKind.TIP,
                  text = "可以把 new Dog 改成其他子类，继续观察同一个父类引用呈现的不同行为。",
                ),
              ),
              completion = DynamicTutorialCompletionRule(
                kind = DynamicTutorialCompletionKind.OUTPUT_CONTAINS,
                expected = "旺财：汪",
              ),
            ),
          ),
        ),
      ),
    )
  }

  /** 构造泛型集合课程，作为后续扩充课程正文和自动校验的基准。 */
  private fun collectionsCourse(): DynamicTutorialCourse {
    val summary = DynamicTutorialCourseSummary(
      courseId = COLLECTIONS_COURSE_ID,
      title = "泛型与集合",
      description = "使用 List<String> 和 Map<String, Integer> 组织类型安全的数据。",
      order = 40,
      estimatedMinutes = 15,
      prerequisiteCourseIds = listOf("java-object-oriented"),
    )
    return DynamicTutorialCourse(
      summary = summary,
      lessons = listOf(
        DynamicTutorialLesson(
          lessonId = TYPED_LIST_LESSON_ID,
          title = "类型安全的列表",
          description = "理解泛型如何约束集合元素。",
          initialFiles = listOf(
            DynamicTutorialSourceFile(
              path = COURSE_LIST_FILE_PATH,
              source = """
                import java.util.ArrayList;
                import java.util.List;

                public class CourseList {
                    public static void main(String[] args) {
                        List<String> courses = new ArrayList<>();
                        courses.add("Java 基础");
                        courses.add("集合");
                        for (String course : courses) {
                            System.out.println(course);
                        }
                    }
                }
              """.trimIndent(),
            ),
          ),
          activeFilePath = COURSE_LIST_FILE_PATH,
          steps = listOf(
            DynamicTutorialStep(
              stepId = ADD_COURSE_STEP_ID,
              title = "添加一个课程",
              content = listOf(
                DynamicTutorialContentBlock(
                  kind = DynamicTutorialContentKind.PARAGRAPH,
                  text = "再调用一次 courses.add，课程名称可以自行选择，然后点击检查。",
                ),
              ),
              completion = DynamicTutorialCompletionRule(
                kind = DynamicTutorialCompletionKind.SOURCE_CONTAINS,
                filePath = COURSE_LIST_FILE_PATH,
                expected = "courses.add(",
              ),
            ),
          ),
        ),
        DynamicTutorialLesson(
          lessonId = "score-map",
          title = "名称到分数的映射",
          description = "使用 Map 按名称保存并查询课程分数。",
          initialFiles = listOf(
            DynamicTutorialSourceFile(
              path = "src/CourseScores.java",
              source = """
                import java.util.HashMap;
                import java.util.Map;

                public class CourseScores {
                    public static void main(String[] args) {
                        Map<String, Integer> scores = new HashMap<>();
                        scores.put("Java", 95);
                        scores.put("算法", 92);

                        System.out.println("Java 分数：" + scores.get("Java"));
                        System.out.println("课程数量：" + scores.size());
                    }
                }
              """.trimIndent(),
            ),
          ),
          activeFilePath = "src/CourseScores.java",
          steps = listOf(
            DynamicTutorialStep(
              stepId = "run-score-map",
              title = "查询课程分数",
              content = listOf(
                DynamicTutorialContentBlock(
                  kind = DynamicTutorialContentKind.PARAGRAPH,
                  text = "运行程序，观察 Map 如何通过课程名称查询对应分数。",
                ),
              ),
              completion = DynamicTutorialCompletionRule(
                kind = DynamicTutorialCompletionKind.OUTPUT_CONTAINS,
                expected = "Java 分数：95",
              ),
            ),
          ),
        ),
      ),
    )
  }

  private const val COLLECTIONS_COURSE_ID = "java-generics-collections"
  private const val TYPED_LIST_LESSON_ID = "typed-list"
  private const val ADD_COURSE_STEP_ID = "add-course"
  private const val COURSE_LIST_FILE_PATH = "src/CourseList.java"
  private const val INITIAL_COURSE_COUNT = 2
  private val COURSE_ADD_PATTERN = Regex("""\bcourses\s*\.\s*add\s*\(""")

  /** 当前实现不持有独立资源；Runtime 仍由宿主代理在本方法返回后统一释放。 */
  override suspend fun close(): NpmJsResult<Unit> = NpmJsResult.success(Unit)
}
