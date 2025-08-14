dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files(rootDir.parentFile.path + "/gradle/libs.versions.toml"))
        }
    }
}
