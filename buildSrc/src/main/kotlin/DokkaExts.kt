/*
 * Copyright 2026, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import io.spine.internal.dependency.Dokka
import io.spine.internal.gradle.publish.getOrCreate
import java.time.LocalDate
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.configure
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import org.jetbrains.dokka.gradle.engine.plugins.DokkaHtmlPluginParameters
import org.jetbrains.dokka.gradle.tasks.DokkaGenerateTask

/**
 * Configures API visibility, generated documentation, and Chords' HTML assets.
 */
fun Project.configureDokka() {
    dependencies.add("dokkaPlugin", Dokka.SpineExtensions.lib)
    extensions.configure<DokkaExtension> {
        dokkaSourceSets.configureEach {
            jdkVersion.set(BuildSettings.javaVersion.asInt())
            skipEmptyPackages.set(true)
            documentedVisibilities.set(
                setOf(VisibilityModifier.Public, VisibilityModifier.Protected)
            )
        }
        dokkaPublications.named("html") {
            outputDirectory.set(layout.buildDirectory.dir("docs/dokkaKotlin"))
        }
        pluginsConfiguration.named("html", DokkaHtmlPluginParameters::class.java) {
            val assets = rootProject.file("buildSrc/src/main/resources/dokka")
            customStyleSheets.from(assets.resolve("styles/custom-styles.css"))
            customAssets.from(assets.resolve("assets/logo-icon.svg"))
            separateInheritedMembers.set(true)
            footerMessage.set("Copyright ${LocalDate.now().year}, TeamDev")
        }
    }
    tasks.withType(DokkaGenerateTask::class.java).configureEach {
        onlyIf { isInPublishingGraph() }
    }
}

/**
 * Finds the HTML publication task for documentation deployment.
 */
fun TaskContainer.dokkaHtmlTask(): Task? = findByName("dokkaGeneratePublicationHtml")

/**
 * Includes API documentation when publishing to a remote repository.
 * Maven-local publication remains part of routine verification.
 */
fun Task.isInPublishingGraph(): Boolean =
    project.gradle.taskGraph.allTasks.any {
        it.name.startsWith("publish") && !it.name.endsWith("ToMavenLocal")
    }

/**
 * Packages Kotlin API documentation with the existing publication classifier.
 */
fun Project.dokkaKotlinJar(): TaskProvider<Jar> = tasks.getOrCreate("dokkaKotlinJar") {
    archiveClassifier.set("dokka")
    from(layout.buildDirectory.dir("docs/dokkaKotlin"))
    dependsOn(tasks.named("dokkaGeneratePublicationHtml"))
}

/**
 * Packages Java API documentation with the existing publication classifier.
 */
fun Project.dokkaJavaJar(): TaskProvider<Jar> = tasks.getOrCreate("dokkaJavaJar") {
    archiveClassifier.set("dokka-java")
    from(layout.buildDirectory.dir("docs/dokkaJava"))
    dependsOn(tasks.named("dokkaGeneratePublicationJavadoc"))
}
