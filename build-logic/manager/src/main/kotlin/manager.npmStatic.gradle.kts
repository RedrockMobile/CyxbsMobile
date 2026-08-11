import npm.configureNpmStaticPackaging
import npm.configureNpmStaticIdeaModel
import npm.createNpmStaticPackageExtension

plugins {
  base
  idea
}

createNpmStaticPackageExtension()

/**
 * 只发布 JSON、配置或其他资源文件的静态 npm 包约定。
 *
 * 插件不会启用 Kotlin/JS、KSP 或 npm Service，也不会加入共享 Runtime distribution。源文件放在
 * `src/resources`，该目录会注册为 IDEA Resources Root；package.json 由插件根据稳定包名和
 * project.version 生成。对外提供：
 *
 * - `prepareNpmPackage`：生成发布目录；
 * - `packNpmPackage`：生成 tgz；
 * - `publishNpmPackage`：比较远端精确版本 integrity，缺失才发布。
 */
afterEvaluate {
  configureNpmStaticPackaging()
  configureNpmStaticIdeaModel()
}
