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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
          .filter(path -> Files.isDirectory(path) && Files.isRegularFile(path.resolve("case.properties")))
          .sorted(Comparator.comparing(path -> path.getFileName().toString()))
          .collect(Collectors.toList());
    }
    if (caseDirectories.isEmpty()) {
      throw new IllegalStateException("No javac differential cases found in " + casesDirectory);
    }

    Map<String, String> categories = readCoverageCategories(casesDirectory);
    List<Fixture> fixtures = new ArrayList<>();
    for (Path caseDirectory : caseDirectories) {
      fixtures.addAll(createFixtures(caseDirectory, categories));
    }
    Files.createDirectories(outputFile.getParent());
    Files.writeString(outputFile, renderKotlin(fixtures), StandardCharsets.UTF_8);
  }

  /**
   * 编译并按需运行一项普通语料或一组共享源码的矩阵语料。
   *
   * <p>普通目录继续读取 {@code entryMethod}；若存在 {@code entries.tsv}，则每行声明一个独立
   * fixture，多个入口只复用 javac 编译结果，不复用 ClassLoader、静态字段或标准输入输出状态。
   */
  private static List<Fixture> createFixtures(
      Path caseDirectory,
      Map<String, String> categories
  ) throws Exception {
    Properties properties = new Properties();
    Path propertiesFile = caseDirectory.resolve("case.properties");
    try (var reader = Files.newBufferedReader(propertiesFile, StandardCharsets.UTF_8)) {
      properties.load(reader);
    }
    String entryClass = required(properties, "entryClass", propertiesFile);
    List<Entry> entries = readEntries(caseDirectory, properties, propertiesFile, categories);

    Path sourcesDirectory = caseDirectory.resolve("src");
    List<Path> sourceFiles;
    try (var paths = Files.walk(sourcesDirectory)) {
      sourceFiles = paths
          .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".java"))
          .sorted()
          .collect(Collectors.toList());
    }
    if (sourceFiles.isEmpty()) {
      throw new IllegalStateException("Case directory '" + caseDirectory + "' has no Java sources.");
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

    List<Source> sources = sourceFiles.stream()
        .map(path -> new Source(
            sourcesDirectory.relativize(path).toString().replace('\\', '/'),
            readUtf8(path)
        ))
        .collect(Collectors.toList());
    List<Fixture> fixtures = new ArrayList<>();
    try {
      for (Entry entry : entries) {
        Execution execution = compiled
            ? execute(classesDirectory, entryClass, entry.method, entry.standardInput)
            : Execution.EMPTY;
        fixtures.add(new Fixture(
            entry.id,
            entry.category,
            entryClass,
            entry.method,
            entry.descriptor,
            entry.standardInput,
            sources,
            compiled,
            execution.stdout,
            execution.stderr,
            execution.throwableSimpleName,
            List.copyOf(diagnosticCategories)
        ));
      }
      return fixtures;
    } finally {
      deleteRecursively(classesDirectory);
    }
  }

  /** 读取普通单入口或 entries.tsv 矩阵入口，并确保每项都有唯一覆盖分类。 */
  private static List<Entry> readEntries(
      Path caseDirectory,
      Properties properties,
      Path propertiesFile,
      Map<String, String> categories
  ) throws IOException {
    Path matrixFile = caseDirectory.resolve("entries.tsv");
    if (!Files.isRegularFile(matrixFile)) {
      String directoryName = caseDirectory.getFileName().toString();
      String category = properties.getProperty("category", categories.get(directoryName));
      if (category == null || category.isBlank()) {
        throw new IllegalArgumentException("Missing coverage category for " + directoryName);
      }
      return List.of(new Entry(
          required(properties, "id", propertiesFile),
          category.trim(),
          required(properties, "entryMethod", propertiesFile),
          required(properties, "descriptor", propertiesFile),
          decodeInput(properties.getProperty("standardInputBase64", ""), propertiesFile)
      ));
    }

    List<Entry> entries = new ArrayList<>();
    int lineNumber = 0;
    for (String line : Files.readAllLines(matrixFile, StandardCharsets.UTF_8)) {
      lineNumber++;
      if (line.isBlank() || line.startsWith("#")) {
        continue;
      }
      String[] columns = line.split("\\t", -1);
      if (columns.length != 4 && columns.length != 5) {
        throw new IllegalArgumentException(
            matrixFile + ":" + lineNumber + " must have 4 or 5 tab-separated columns."
        );
      }
      entries.add(new Entry(
          requiredColumn(columns[0], "id", matrixFile, lineNumber),
          requiredColumn(columns[1], "category", matrixFile, lineNumber),
          requiredColumn(columns[2], "entryMethod", matrixFile, lineNumber),
          requiredColumn(columns[3], "descriptor", matrixFile, lineNumber),
          decodeInput(columns.length == 5 ? columns[4] : "", matrixFile)
      ));
    }
    if (entries.isEmpty()) {
      throw new IllegalArgumentException("No matrix entries found in " + matrixFile);
    }
    return entries;
  }

  /** coverage.properties 以“分类=目录列表”集中维护旧式 case 的分类，避免复制元数据。 */
  private static Map<String, String> readCoverageCategories(Path casesDirectory) throws IOException {
    Path coverageFile = casesDirectory.resolve("coverage.properties");
    Properties properties = new Properties();
    try (var reader = Files.newBufferedReader(coverageFile, StandardCharsets.UTF_8)) {
      properties.load(reader);
    }
    Map<String, String> categories = new LinkedHashMap<>();
    for (String category : properties.stringPropertyNames()) {
      for (String directory : properties.getProperty(category).split(",")) {
        String normalized = directory.trim();
        if (normalized.isEmpty()) {
          continue;
        }
        String previous = categories.put(normalized, category.trim());
        if (previous != null) {
          throw new IllegalArgumentException(
              "Case directory '" + normalized + "' belongs to both " + previous + " and " + category
          );
        }
      }
    }
    return categories;
  }

  /** 解码可选标准输入；畸形 Base64 在生成阶段直接失败而不进入 Node 测试。 */
  private static String decodeInput(String base64, Path source) {
    try {
      return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Invalid standardInputBase64 in " + source, exception);
    }
  }

  private static String requiredColumn(String value, String name, Path file, int line) {
    if (value.isBlank()) {
      throw new IllegalArgumentException("Missing '" + name + "' in " + file + ":" + line);
    }
    return value.trim();
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
    if (code.contains("cant.assign.val.to.final.var")
        || code.contains("cant.assign.val.to.var")
        || code.contains("final.parameter.may.not.be.assigned")) {
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
      output.append("    category = ").append(kotlinString(fixture.category)).append(",\n");
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

  private record Entry(
      String id,
      String category,
      String method,
      String descriptor,
      String standardInput
  ) {
  }

  private record Fixture(
      String id,
      String category,
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
