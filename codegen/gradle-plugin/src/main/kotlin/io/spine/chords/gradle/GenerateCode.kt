package io.spine.chords.gradle

import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.os.OperatingSystem

/**
 * A Gradle task which runs `codegen-plugins` in a separate Gradle process.
 *
 * Launches Gradle wrapper under a [workspaceDir] with the
 * specified [taskNames] and [dependencies].
 *
 * The [sourceModuleDir] and [pluginsVersion] properties may be specified
 * if the default values are unsuitable.
 *
 * The `clean` task is also run if current build includes a `clean` task.
 *
 * The build writes verbose log into `$workspaceDir/_out/debug-out.txt`.
 * The error output is written into `$workspaceDir/_out/error-out.txt`.
 */
@Suppress("unused")
public open class GenerateCode : DefaultTask() {

    public companion object {
        /**
         * Default Gradle build timeout.
         */
        private const val BUILD_TIMEOUT_MINUTES: Long = 10
    }

    /**
     * Path to the `codegen-workspace` module where the code generation is actually performed.
     *
     * The default value is `${project.rootDir}/codegen/workspace`.
     */
    @Internal
    public var workspaceDir: String = "${project.rootDir}/codegen/workspace"

    /**
     * The version of `spine-chords-codegen-plugins` artifact to be used for code generation.
     *
     * The artifact with this version should be located in `mavenLocal` or `spineSnapshots` repo.
     *
     * The default value is `project.version.toString()`.
     */
    @Internal
    public var pluginsVersion: String = project.version.toString()

    /**
     * Path to the module to generate the code for.
     *
     * The default value is `project.projectDir.path`.
     */
    @Internal
    public var sourceModuleDir: String = project.projectDir.path

    /**
     * Dependencies that should be added to classpath of codegen module
     * to read the necessary Proto files.
     */
    private var dependencies: MutableSet<String> = mutableSetOf()

    /**
     * The names of the tasks to be passed to the Gradle Wrapper script.
     *
     * The `build` task will be executed by default.
     */
    private var taskNames: MutableList<String> = mutableListOf("build")

    /**
     * For how many minutes to wait for the Gradle build to complete.
     */
    @Internal
    public var maxDurationMins: Long = BUILD_TIMEOUT_MINUTES

    /**
     * Names of Gradle properties to copy into the launched build.
     *
     * The properties are looked up in the root project. If a property is not found, it is ignored.
     *
     * See [Gradle doc](https://docs.gradle.org/current/userguide/build_environment.html#sec:gradle_configuration_properties)
     * for more info about Gradle properties.
     */
    @Internal
    public var includeGradleProperties: MutableSet<String> = mutableSetOf()

    /**
     * Specifies task names to be passed to the Gradle Wrapper script.
     */
    public fun task(vararg tasks: String) {
        taskNames.clear()
        taskNames.addAll(tasks)
    }

    /**
     * Configures dependencies that should be added to the codegen module's classpath.
     */
    public fun dependencies(vararg dependencies: String) {
        this.dependencies.clear()
        this.dependencies.addAll(dependencies)
    }

    /**
     * Configures dependencies that should be added to the codegen module's classpath.
     */
    public fun dependencies(dependencies: Set<String>) {
        this.dependencies.clear()
        this.dependencies.addAll(dependencies)
    }

    /**
     * Sets the maximum time to wait until the build completion in minutes
     * and specifies task names to be passed to the Gradle Wrapper script.
     */
    public fun task(maxDurationMins: Long, vararg tasks: String) {
        taskNames.clear()
        taskNames.addAll(tasks)
        this.maxDurationMins = maxDurationMins
    }

    @TaskAction
    private fun execute() {
        // Since we're executing this task in another process, we redirect error output to
        // the file under the `_out` directory. Using the `build` directory for this purpose
        // proved to cause problems under Windows when executing the `clean` command, which
        // fails because another process holds files.
        val buildDir = File(workspaceDir, "_out")
        if (!buildDir.exists()) {
            buildDir.mkdir()
        }
        val errorOut = File(buildDir, "error-out.txt")
        errorOut.truncate()
        val debugOut = File(buildDir, "debug-out.txt")
        debugOut.truncate()

        val command = buildCommand()
        val process = startProcess(command, errorOut, debugOut)

        /*  The timeout is set because of Gradle process execution under Windows.
            See the following locations for details:
              https://github.com/gradle/gradle/pull/8467#issuecomment-498374289
              https://github.com/gradle/gradle/issues/3987
              https://discuss.gradle.org/t/weirdness-in-gradle-exec-on-windows/13660/6
         */
        val completed = process.waitFor(maxDurationMins, TimeUnit.MINUTES)
        val exitCode = process.exitValue()
        if (!completed || exitCode != 0) {
            val errorOutExists = errorOut.exists()
            if (errorOutExists) {
                logger.error(errorOut.readText())
            }
            throw GradleException(
                "Child build process FAILED." +
                        " Exit code: $exitCode." +
                        if (errorOutExists) " See $errorOut for details."
                        else " $errorOut file was not created."
            )
        }
    }

    private fun buildCommand(): List<String> {
        val script = buildScript()
        val command = mutableListOf<String>()
        command.add(script)
        val shouldClean = project.gradle
            .taskGraph
            .hasTask(":clean")
        if (shouldClean) {
            command.add("clean")
        }
        command.addAll(taskNames)
        command.add("--console=plain")
        command.add("--stacktrace")
        command.add("--no-daemon")
        addProperties(command)
        return command
    }

    private fun addProperties(command: MutableList<String>) {
        val rootProject = project.rootProject
        includeGradleProperties
            .filter { rootProject.hasProperty(it) }
            .map { name -> name to rootProject.property(name).toString() }
            .forEach { (name, value) -> command.add("-P$name=$value") }
        command.add("-PsourceModuleDir=$sourceModuleDir")
        if (dependencies.isNotEmpty()) {
            val classPath = dependencies.joinToString(";")
            command.add("-PdependencyItems=$classPath")
        }
        command.add("-PcodegenPluginsVersion=$pluginsVersion")
    }

    private fun buildScript(): String {
        val runsOnWindows = OperatingSystem.current().isWindows
        return if (runsOnWindows) "$workspaceDir/gradlew.bat" else "$workspaceDir/gradlew"
    }

    private fun startProcess(command: List<String>, errorOut: File, debugOut: File) =
        ProcessBuilder()
            .command(command)
            .directory(project.file(workspaceDir))
            .redirectError(errorOut)
            .redirectOutput(debugOut)
            .start()
}

private fun File.truncate() {
    val stream = FileOutputStream(this)
    stream.use {
        it.channel.truncate(0)
    }
}
