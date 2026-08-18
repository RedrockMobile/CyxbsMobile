import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * 使用当前 JDK 的 javac/java 为共享语料生成 Kotlin/JS 差分测试基准。
 *
 * <p>生成器只在测试编译前运行，不进入 npm 产物。语料固定使用 {@code --release 8}，
 * 从而避免开发机 JDK 版本改变被测 Java 方言；运行阶段通过隔离 ClassLoader 和受控标准流
 * 记录 stdout、stderr 与未捕获异常类型。
 */
public final class JavacDifferentialFixtureGenerator {

  private JavacDifferentialFixtureGenerator() {
  }

  /**
   * 读取 case 目录并生成可直接参与 jsTest 编译的 Kotlin 源文件。
   *
   * @param args 依次为 case 根目录和目标 Kotlin 文件。
   */
  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      throw new IllegalArgumentException("Expected <cases-directory> <generated-kotlin-file>.");
    }
    Path casesDirectory = Path.of(args[0]).toAbsolutePath().normalize();
    Path outputFile = Path.of(args[1]).toAbsolutePath().normalize();
    List<Path> caseDirectories;
    try (var children = Files.list(casesDirectory)) {
      caseDirectories = children
          .filter(Files::isDirectory)
          .sorted(Comparator.comparing(path -> path.getFileName().toString()))
          .collect(Collectors.toList());
    }
    if (caseDirectories.isEmpty()) {
      throw new IllegalStateException("No javac differential cases found in " + casesDirectory);
    }

    List<Fixture> fixtures = new ArrayList<>();
    for (Path caseDirectory : caseDirectories) {
      fixtures.add(createFixture(caseDirectory));
    }
    Files.createDirectories(outputFile.getParent());
    Files.writeString(outputFile, renderKotlin(fixtures), StandardCharsets.UTF_8);
  }

  /** 编译并按需运行一项语料，得到当前 JDK 的 Java 8 基准。 */
  private static Fixture createFixture(Path caseDirectory) throws Exception {
    Properties properties = new Properties();
    Path propertiesFile = caseDirectory.resolve("case.properties");
    try (var reader = Files.newBufferedReader(propertiesFile, StandardCharsets.UTF_8)) {
      properties.load(reader);
    }
    String id = required(properties, "id", propertiesFile);
    String entryClass = required(properties, "entryClass", propertiesFile);
    String entryMethod = required(properties, "entryMethod", propertiesFile);
    String descriptor = required(properties, "descriptor", propertiesFile);
    String standardInput = new String(
        Base64.getDecoder().decode(properties.getProperty("standardInputBase64", "")),
        StandardCharsets.UTF_8
    );

    Path sourcesDirectory = caseDirectory.resolve("src");
    List<Path> sourceFiles;
    try (var paths = Files.walk(sourcesDirectory)) {
      sourceFiles = paths
          .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".java"))
          .sorted()
          .collect(Collectors.toList());
    }
    if (sourceFiles.isEmpty()) {
      throw new IllegalStateException("Case '" + id + "' does not contain Java sources.");
    }

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("A full JDK with javac is required for differential fixtures.");
    }
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    Path classesDirectory = Files.createTempDirectory("cyxbs-javac-differential-");
    boolean compiled;
    try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
        diagnostics,
        null,
        StandardCharsets.UTF_8
    )) {
      Iterable<? extends JavaFileObject> compilationUnits =
          fileManager.getJavaFileObjectsFromPaths(sourceFiles);
      List<String> options = List.of(
          "--release", "8",
          "-proc:none",
          "-Xlint:none",
          "-d", classesDirectory.toString()
      );
      compiled = Boolean.TRUE.equals(
          compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits).call()
      );
    }

    Set<String> diagnosticCategories = diagnostics.getDiagnostics().stream()
        .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
        .map(Diagnostic::getCode)
        .map(JavacDifferentialFixtureGenerator::diagnosticCategory)
        .collect(Collectors.toCollection(LinkedHashSet::new));

    Execution execution = compiled
        ? execute(classesDirectory, entryClass, entryMethod, standardInput)
        : Execution.EMPTY;
    List<Source> sources = sourceFiles.stream()
        .map(path -> new Source(
            sourcesDirectory.relativize(path).toString().replace('\\', '/'),
            readUtf8(path)
        ))
        .collect(Collectors.toList());
    deleteRecursively(classesDirectory);
    return new Fixture(
        id,
        entryClass,
        entryMethod,
        descriptor,
        standardInput,
        sources,
        compiled,
        execution.stdout,
        execution.stderr,
        execution.throwableSimpleName,
        List.copyOf(diagnosticCategories)
    );
  }

  /**
   * 在独立 ClassLoader 中执行无参 static 入口。
   *
   * <p>这里只运行受仓库控制的测试语料；禁止在语料中调用 System.exit 或创建后台线程，
   * 否则会破坏 Gradle 生成进程。
   */
  private static Execution execute(
      Path classesDirectory,
      String entryClass,
      String entryMethod,
      String standardInput
  ) throws Exception {
    var stdoutBytes = new ByteArrayOutputStream();
    var stderrBytes = new ByteArrayOutputStream();
    var previousInput = System.in;
    var previousOutput = System.out;
    var previousError = System.err;
    String throwableSimpleName = null;
    try (
        URLClassLoader loader = new URLClassLoader(
            new URL[]{classesDirectory.toUri().toURL()},
            ClassLoader.getPlatformClassLoader()
        );
        PrintStream stdout = new PrintStream(stdoutBytes, true, StandardCharsets.UTF_8);
        PrintStream stderr = new PrintStream(stderrBytes, true, StandardCharsets.UTF_8)
    ) {
      System.setIn(new ByteArrayInputStream(standardInput.getBytes(StandardCharsets.UTF_8)));
      System.setOut(stdout);
      System.setErr(stderr);
      Class<?> entryType = Class.forName(entryClass, true, loader);
      Method method = entryType.getDeclaredMethod(entryMethod);
      method.setAccessible(true);
      try {
        method.invoke(null);
      } catch (InvocationTargetException exception) {
        Throwable target = exception.getTargetException();
        throwableSimpleName = target.getClass().getSimpleName();
      }
    } finally {
      System.setIn(previousInput);
      System.setOut(previousOutput);
      System.setErr(previousError);
    }
    return new Execution(
        stdoutBytes.toString(StandardCharsets.UTF_8),
        stderrBytes.toString(StandardCharsets.UTF_8),
        throwableSimpleName
    );
  }

  /** 把 javac 的稳定诊断 key 归一为编译器间可比较的语义类别。 */
  private static String diagnosticCategory(String code) {
    if (code.contains("cant.resolve")) {
      return "UNRESOLVED_SYMBOL";
    }
    if (code.contains("prob.found.req") || code.contains("incompatible.types")) {
      return "TYPE_MISMATCH";
    }
    if (code.contains("ref.ambiguous")) {
      return "AMBIGUOUS_CALL";
    }
    if (code.contains("cant.assign.val.to.final.var") || code.contains("cant.assign.val.to.var")) {
      return "FINAL_ASSIGNMENT";
    }
    if (code.contains("missing.ret.stmt")) {
      return "MISSING_RETURN";
    }
    return "JAVAC:" + code;
  }

  /** 将基准和原始源码渲染为测试源码，Node 测试无需再访问文件系统。 */
  private static String renderKotlin(List<Fixture> fixtures) {
    StringBuilder output = new StringBuilder();
    output.append("package com.cyxbs.functions.code.language.java.compiler.differential\n\n");
    output.append("/** 由 generateJavacDifferentialFixtures 生成，请修改 src/javacDifferentialTest 下的语料。 */\n");
    output.append("internal val generatedJavacDifferentialFixtures = listOf(\n");
    for (Fixture fixture : fixtures) {
      output.append("  JavacDifferentialFixture(\n");
      output.append("    id = ").append(kotlinString(fixture.id)).append(",\n");
      output.append("    entryClass = ").append(kotlinString(fixture.entryClass)).append(",\n");
      output.append("    entryMethod = ").append(kotlinString(fixture.entryMethod)).append(",\n");
      output.append("    descriptor = ").append(kotlinString(fixture.descriptor)).append(",\n");
      output.append("    standardInput = ").append(kotlinString(fixture.standardInput)).append(",\n");
      output.append("    sources = listOf(\n");
      for (Source source : fixture.sources) {
        output.append("      ")
            .append(kotlinString(source.path))
            .append(" to ")
            .append(kotlinString(source.content))
            .append(",\n");
      }
      output.append("    ),\n");
      output.append("    javacCompiled = ").append(fixture.compiled).append(",\n");
      output.append("    expectedStandardOutput = ")
          .append(kotlinString(fixture.stdout))
          .append(",\n");
      output.append("    expectedStandardError = ")
          .append(kotlinString(fixture.stderr))
          .append(",\n");
      output.append("    expectedThrowableSimpleName = ")
          .append(fixture.throwableSimpleName == null
              ? "null"
              : kotlinString(fixture.throwableSimpleName))
          .append(",\n");
      output.append("    expectedDiagnosticCategories = setOf(");
      for (int index = 0; index < fixture.diagnosticCategories.size(); index++) {
        if (index > 0) {
          output.append(", ");
        }
        output.append(kotlinString(fixture.diagnosticCategories.get(index)));
      }
      output.append("),\n");
      output.append("  ),\n");
    }
    output.append(")\n");
    return output.toString();
  }

  /** 生成普通 Kotlin 字符串字面量，避免源码中的美元符号触发模板插值。 */
  private static String kotlinString(String value) {
    StringBuilder escaped = new StringBuilder(value.length() + 16);
    escaped.append('"');
    for (int index = 0; index < value.length(); index++) {
      char current = value.charAt(index);
      switch (current) {
        case '\\' -> escaped.append("\\\\");
        case '"' -> escaped.append("\\\"");
        case '\n' -> escaped.append("\\n");
        case '\r' -> escaped.append("\\r");
        case '\t' -> escaped.append("\\t");
        case '$' -> escaped.append("\\$");
        default -> {
          if (current < 0x20) {
            escaped.append(String.format("\\u%04x", (int) current));
          } else {
            escaped.append(current);
          }
        }
      }
    }
    return escaped.append('"').toString();
  }

  private static String required(Properties properties, String key, Path file) {
    String value = properties.getProperty(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing '" + key + "' in " + file);
    }
    return value.trim();
  }

  private static String readUtf8(Path path) {
    try {
      return Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to read " + path, exception);
    }
  }

  /** 删除单个 case 的临时 class 输出，不触碰仓库或 Gradle build 目录。 */
  private static void deleteRecursively(Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }
    try (var paths = Files.walk(root)) {
      List<Path> entries = paths.sorted(Comparator.reverseOrder()).collect(Collectors.toList());
      for (Path entry : entries) {
        Files.deleteIfExists(entry);
      }
    }
  }

  private record Source(String path, String content) {
  }

  private record Execution(String stdout, String stderr, String throwableSimpleName) {
    private static final Execution EMPTY = new Execution("", "", null);
  }

  private record Fixture(
      String id,
      String entryClass,
      String entryMethod,
      String descriptor,
      String standardInput,
      List<Source> sources,
      boolean compiled,
      String stdout,
      String stderr,
      String throwableSimpleName,
      List<String> diagnosticCategories
  ) {
  }
}
