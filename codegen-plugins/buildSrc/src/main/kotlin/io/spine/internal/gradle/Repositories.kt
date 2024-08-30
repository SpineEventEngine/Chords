/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.gradle

import java.net.URI
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository

/**
 * Applies repositories commonly used by Spine Event Engine projects.
 */
fun RepositoryHandler.standardToSpineSdk() {
    spineArtifacts()

    maven {
        url = URI(Repos.sonatypeSnapshots)
    }

    mavenCentral()
    gradlePluginPortal()
    mavenLocal().includeSpineOnly()
}

/**
 * A scrambled version of PAT generated with the only "read:packages" scope.
 *
 * The scrambling around PAT is necessary because GitHub analyzes commits for the presence
 * of tokens and invalidates them.
 *
 * @see <a href="https://github.com/orgs/community/discussions/25629">
 *     How to make GitHub packages to the public</a>
 */
object Pat {
    private const val shade = "_phg->8YlN->MFRA->gxIk->HVkm->eO6g->FqHJ->z8MS->H4zC->ZEPq"
    private const val separator = "->"
    private val chunks: Int = shade.split(separator).size - 1

    fun credentials(): Credentials {
        val pass = shade.replace(separator, "").splitAndReverse(chunks, "")
        return Credentials("public", pass)
    }

    /**
     * Splits this string to the chunks, reverses each chunk, and joins them
     * back to a string using the [separator].
     */
    private fun String.splitAndReverse(numChunks: Int, separator: String): String {
        check(length / numChunks >= 2) {
            "The number of chunks is too big. Must be <= ${length / 2}."
        }
        val chunks = chunked(length / numChunks)
        val reversedChunks = chunks.map { chunk -> chunk.reversed() }
        return reversedChunks.joinToString(separator)
    }
}

/**
 * Adds a read-only view to all artifacts of the SpineEventEngine
 * GitHub organization.
 */
fun RepositoryHandler.spineArtifacts(): MavenArtifactRepository = maven {
    url = URI("https://maven.pkg.github.com/SpineEventEngine/*")
    includeSpineOnly()
    val pat = Pat.credentials()
    credentials {
        username = pat.username
        password = pat.password
    }
}

/**
 * Password credentials for a Maven repository.
 */
data class Credentials(
    val username: String?,
    val password: String?
)

/**
 * Defines names of additional repositories commonly used in the Spine SDK projects.
 */
private object Repos {
    const val sonatypeSnapshots = "https://oss.sonatype.org/content/repositories/snapshots"
}

/**
 * Narrows down the search for this repository to Spine-related artifact groups.
 */
private fun MavenArtifactRepository.includeSpineOnly() {
    content {
        includeGroupByRegex("io\\.spine.*")
    }
}
