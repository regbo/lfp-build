package com.lfp

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.extra

class LombokPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extra["lombok"] = Utils.closure {
            project.logger.lifecycle("adding lombok")
            val dependencyNotation = VersionCatalogLibrary.find("lombok")!!.dependencyNotation(project)
            project.dependencies.add("annotationProcessor", dependencyNotation)
            project.dependencies.add("compileOnly", dependencyNotation)
            project.dependencies.add("testAnnotationProcessor", dependencyNotation)
        }
    }

}