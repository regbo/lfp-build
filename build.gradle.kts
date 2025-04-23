import java.io.Serializable

repositories {
    gradlePluginPortal()
    mavenCentral()
}


plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    id("com.github.gmazzo.buildconfig") version "5.6.2"
}

dependencies {
    implementation(libs.apache.commons.lang3)
    testImplementation(platform("org.junit:junit-bom:5.10.5"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

val pluginId = providers.provider {
    listOf("repository_group", "repository_owner", "repository_name").map {
        providers.gradleProperty(it).getOrElse("")
    }.filter { it.isNotEmpty() }.joinToString(".")
}
val pluginImplementationClassName = providers.gradleProperty("plugin_implementation_class_name")
val pluginName = pluginImplementationClassName.map { it.substringAfterLast('.') }

gradlePlugin {
    plugins {
        register(pluginName.get()) {
            id = pluginId.get()
            implementationClass = pluginImplementationClassName.get()
        }
    }
}

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
var versionCatalogLibraries = versionCatalog.libraryAliases.associateWith { alias ->
    val dep = versionCatalog.findLibrary(alias).get().get()
    "${dep.module}:${dep.version ?: ""}"
}

buildConfig {
    packageName(pluginImplementationClassName.get().substringBeforeLast("."))
    className(pluginName.get() + "Properties")
    properties.keys.forEach { key ->
        if (key.matches("^[a-zA-Z_\$][a-zA-Z0-9_\$]*\$".toRegex())) {
            val value = property(key)
            if (value is Number) {
                buildConfigField(key, value)
            } else if (value is String) {
                buildConfigField(key, value)
            }
        }
    }
    buildConfigField("versionCatalogLibraries", versionCatalogLibraries)


}

tasks.test {
    useJUnitPlatform()
}
