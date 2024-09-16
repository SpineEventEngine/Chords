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

configurations {
    all {
        // JVM environment attribute configuration is necessary because Guava version >= 32.1.0
        // without it doesn't work on Gradle version < 7.
        //
        attributes {
            attribute(Attribute.of("org.gradle.jvm.environment", "".javaClass), "standard-jvm")
        }
        resolutionStrategy {
//        failOnVersionConflict()
            force(
                "com.google.guava:guava:32.1.3-jre"
            )
        }
    }

}
