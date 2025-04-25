@file:Suppress("ObjectLiteralToLambda")

import org.gradle.internal.extensions.core.extra


// === Set the root project name from a Gradle property ===
rootProject.name = providers.gradleProperty("repository_name").get()

