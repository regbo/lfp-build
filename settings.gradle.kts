import com.lfp.buildplugin.shared.TestUtils

pluginManagement {
    includeBuild("buildPlugin")
}

buildscript{
    dependencies {

    }
}

plugins {
    id("greeting")
}

// === Set the root project name from a Gradle property ===
rootProject.name = providers.gradleProperty("repository_name").get()


TestUtils.sayHello()
