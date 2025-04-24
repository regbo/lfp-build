package com.lfp.buildplugin

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project

class LombokPlugin : Plugin<Project> {
    override fun apply(project: Project) {
//        project.extensions.extraProperties["lombok"] = Utils.closure {
//            val dependencyNotation = VersionCatalog.instance.findLibrary("lombok")!!.dependencyNotation(project)
//            val configurationNames = mutableSetOf("annotationProcessor", "compileOnly", "testAnnotationProcessor")
//            configureProject(project,dependencyNotation, configurationNames)
//            project.afterEvaluate(object : Action<Project> {
//                override fun execute(project: Project) {
//                    configureProject(project, configurationNames)
//                }
//            })
//        }
    }

    private fun configureProject(project: Project, configurationNames: MutableSet<String>) {
        val configurationNameIterator = configurationNames.iterator();
        while (configurationNameIterator.hasNext()) {
            val configurationName = configurationNameIterator.next()
            val configuration = project.configurations.findByName(configurationName)
            if (configuration != null) {
                configurationNameIterator.remove()
            }

        }
        project.logger.lifecycle("adding lombok")
//        val dependencyNotation = VersionCatalog.instance.findLibrary("lombok")!!.dependencyNotation(project)
//        listOf("annotationProcessor", "compileOnly", "testAnnotationProcessor").forEach { configurationName ->
//            val configuration = project.configurations.findByName(configurationName)
//            if (configuration != null) {
//                project.dependencies.add(configuration.name, dependencyNotation)
//            }
//        }
    }

}