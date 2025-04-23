repositories {
    gradlePluginPortal()
    mavenCentral()
}

plugins {
    java
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
}


gradlePlugin {
    plugins {
        register("BuildPlugin") {
            id = providers.gradleProperty("group_id").get() + ".lfp-build"
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