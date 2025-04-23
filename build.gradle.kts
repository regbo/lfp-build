repositories {
    gradlePluginPortal()
    mavenCentral()
}

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
}

val pluginId = providers.provider {
    listOf("repository_group", "repository_owner", "repository_name").map {
        providers.gradleProperty(it).getOrElse("")
    }.filter { it.isNotEmpty() }.joinToString(".")
}
val pluginImplementationClassName = providers.gradleProperty("plugin_implementation_class_name")

gradlePlugin {
    plugins {
        register(pluginImplementationClassName.get().substringAfterLast('.')) {
            id = pluginId.get()
            implementationClass = "com.lfp.BuildPlugin"
        }
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.5"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}