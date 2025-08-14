import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.initialization.Settings
import org.gradle.api.Action
import java.util.Objects
import java.util.stream.Stream

class GreetingPlugin : Plugin<Settings>, Action<Project> {
    override fun apply(settings: Settings) {
        settings.gradle.beforeProject(this)
        settings.gradle.allprojects(this)
    }

    override fun execute(project: Project) {
        Stream.of(project, project.rootProject).filter(Objects::nonNull).distinct().flatMap { p ->
            Stream.concat(Stream.of(p), p.subprojects.stream())
        }.distinct().forEach(::configureProject)
    }

    private fun configureProject(project: Project) {
        println("Configuring Greeting - ${project.name}")
        val taskName = "greeting"
        if (project.tasks.findByName(taskName) == null) {
            project.tasks.register(taskName, {
                doLast {
                    println("Hello from plugin 'com.lfp.build.greeting' in project ${project.name}")
                }
            })
        }
    }


}
