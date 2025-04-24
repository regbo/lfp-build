package com.lfp.buildplugin

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project

class LombokPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.extraProperties["lombok"] = Utils.closure {
            project.afterEvaluate(object : Action<Project> {
                override fun execute(project: Project) {
                    configureProjectAfterEvaluate(project)
                }
            })
        }
    }

    private fun configureProjectAfterEvaluate(project: Project) {
        project.logger.lifecycle("adding lombok")
        val dependencyNotation = VersionCatalog.instance.findLibrary("lombok")!!.dependencyNotation(project)
        listOf("annotationProcessor", "compileOnly", "testAnnotationProcessor").forEach { configurationName ->
            val configuration = project.configurations.findByName(configurationName)
            if (configuration != null) {
                project.dependencies.add(configuration.name, dependencyNotation)
            }
        }
    }

}