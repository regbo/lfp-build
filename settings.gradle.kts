
pluginManagement {

buildscript{
    dependencies {

    }
}


// === Set the root project name from a Gradle property ===
rootProject.name = providers.gradleProperty("repository_name").get()


