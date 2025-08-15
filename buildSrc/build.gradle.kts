plugins { `kotlin-dsl` }

repositories { mavenCentral() }

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

libs.libraryAliases.forEach { alias ->
    val dependency = libs.findLibrary(alias).get().get()
    val dependencyNotation = "${dependency.module}:${dependency.versionConstraint.requiredVersion}"
    dependencies.add("implementation", dependencyNotation)
}
