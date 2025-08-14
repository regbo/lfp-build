repositories {
    mavenCentral()
}

plugins {
    `kotlin-dsl`
}

//
//
//kotlin {
//    sourceSets {
//        getByName("main") {
//            kotlin.srcDir(
//                File(
//                    rootDir.getParentFile(),
//                    "buildSrc/src/main/kotlin"
//                )
//            ) // Adds an extra Kotlin source directory
//        }
//    }
//}

gradlePlugin {
    plugins {
        register("GreetingPlugin") {
            id = "greeting"
            implementationClass = "GreetingPlugin"
        }
    }
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
libs.libraryAliases.forEach { alias ->
    val dependency = libs.findLibrary(alias).get().get()
    val version = dependency.versionConstraint.requiredVersion
    val notation = "${dependency.module}:$version"
    dependencies.add("implementation", notation)
}
