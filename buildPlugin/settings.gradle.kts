dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files(rootDir.getParentFile().resolve("gradle/libs.versions.toml")))
        }
    }
}