plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
libs.libraryAliases.forEach { alias ->
    val dep = libs.findLibrary(alias).get().get()
    val dependencyNotation = "${dep.module}:${dep.versionConstraint.requiredVersion}"
    println(dependencyNotation)
    dependencies.add("implementation", dependencyNotation)
}