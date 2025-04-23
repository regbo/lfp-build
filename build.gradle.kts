plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
}


gradlePlugin {
    plugins {
        register("BuildPlugin") {
            id = "com.github.regbo.lfp-build"
            implementationClass = "com.lfp.BuildPlugin"
        }
    }
}

repositories {
    gradlePluginPortal()
}