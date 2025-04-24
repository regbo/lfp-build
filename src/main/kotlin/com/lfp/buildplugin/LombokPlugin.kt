package com.lfp.buildplugin

import org.gradle.api.Plugin
import org.gradle.api.Project

class LombokPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.extraProperties["lombok"] = Utils.closure {
            project.logger.lifecycle("adding lombok")
            val dependencyNotation = VersionCatalog.instance.findLibrary("lombok")!!.dependencyNotation(project)
            project.dependencies.add("annotationProcessor", dependencyNotation)
            project.dependencies.add("compileOnly", dependencyNotation)
            project.dependencies.add("testAnnotationProcessor", dependencyNotation)
        }
    }

}