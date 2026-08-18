import npm.npmJsLanguage
import org.gradle.api.tasks.Exec

plugins {
  id("manager.npmJs")
}

version = "0.2.0"

val javacDifferentialGenerator = layout.projectDirectory.file(
  "tools/JavacDifferentialFixtureGenerator.java"
)
val javacDifferentialCases = layout.projectDirectory.dir("src/javacDifferentialTest/cases")
val generatedJavacDifferentialRoot = layout.buildDirectory.dir(
  "generated/javacDifferentialTest/kotlin"
)
val generatedJavacDifferentialFile = generatedJavacDifferentialRoot.map { directory ->
  directory.file(
    "com/cyxbs/functions/code/language/java/compiler/differential/GeneratedJavacDifferentialFixtures.kt"
  )
}

/**
 * 使用 Gradle 当前 JDK 的 javac/java 生成 Java 8 差分测试基准。
 *
 * 生成源码只接入 jsTest，不会进入 npm 发布包；Java 版本作为任务输入，切换 JDK 后会自动重新生成，
 * 避免复用其他工具链留下的基准。
 */
val generateJavacDifferentialFixtures by tasks.registering(Exec::class) {
  group = "verification"
  description = "Generate Java 8 javac/java reference fixtures for Kotlin/JS differential tests."
  inputs.file(javacDifferentialGenerator)
  inputs.dir(javacDifferentialCases)
  inputs.property("javaVersion", System.getProperty("java.version"))
  inputs.property("javaVendor", System.getProperty("java.vendor"))
  outputs.file(generatedJavacDifferentialFile)
  commandLine(
    file("${System.getProperty("java.home")}/bin/java").absolutePath,
    javacDifferentialGenerator.asFile.absolutePath,
    javacDifferentialCases.asFile.absolutePath,
    generatedJavacDifferentialFile.get().asFile.absolutePath,
  )
}

npmJsPackage {
  packageName.set("@cyxbs-mobile/language-java")
}

npmJsLanguage {
  languageId.set("java")
  displayName.set("Java")
  fileExtensions.add("java")
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(projects.cyxbsFunctions.code.language.jsBridge)
      implementation(projects.cyxbsFunctions.code.language.lezer)
    }
    jsMain.dependencies {
      implementation(npm("@lezer/java", "1.1.3"))
    }
    jsTest.dependencies {
      implementation(libs.kotlinx.coroutines.test)
    }
    named("jsTest") {
      kotlin.srcDir(generatedJavacDifferentialRoot)
    }
  }
}

tasks.named("compileTestKotlinJs") {
  dependsOn(generateJavacDifferentialFixtures)
}
