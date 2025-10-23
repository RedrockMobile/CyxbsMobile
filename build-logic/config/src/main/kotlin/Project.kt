import org.gradle.api.Project
import org.gradle.internal.extensions.core.extra
import java.util.Properties

/**
 * .
 *
 * @author 985892345
 * @date 2025/10/23
 */


val Project.localProperties: Properties
  get() {
    val key = "Project.localProperties"
    if (rootProject.extra.has(key)) {
      return rootProject.extra.get(key) as Properties
    } else {
      val properties = Properties()
      val localPropertiesFile = rootProject.rootDir.resolve("local.properties")
      if (localPropertiesFile.exists()) {
        properties.load(localPropertiesFile.inputStream())
      }
      rootProject.extra.set(key, properties)
      return properties
    }
  }