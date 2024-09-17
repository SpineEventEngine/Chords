import org.gradle.kotlin.dsl.repositories

/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

/**
 * Configures repositories, adds dependencies and forces transitive dependencies.
 *
 * Dependencies are contained within dependency objects in the
 * [io.onedam.dependency] package. These objects allow configuration of
 * dependency properties (e.g. version).
 */

plugins {
    java
}

repositories {
    mavenLocal()
    gradlePluginPortal()
    mavenCentral()

    // TODO:2024-09-17:alex.tymchenko: Dmitry, please have a look. Do we need any of it?
//    maven {
//        url = uri("https://spine.mycloudrepo.io/public/repositories/releases")
//        mavenContent {
//            releasesOnly()
//        }
//    }
//    maven {
//        name = "GitHubPackages"
//        url = uri("https://maven.pkg.github.com/Projects-tm/Server")
//        credentials {
//            username = System.getenv("GITHUB_USERNAME")
//                ?: System.getenv("PROJECTSTM_PACKAGES_USER")
//            password = System.getenv("GITHUB_TOKEN")
//                ?: System.getenv("PROJECTSTM_PACKAGES_TOKEN")
//        }
//    }
//    spineSnapshots()
}

configurations.all {

    resolutionStrategy {
//        failOnVersionConflict()
        force(
            "com.google.guava:guava:32.1.3-jre"
        )
    }
}
