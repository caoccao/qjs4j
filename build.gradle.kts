/*
 * Copyright (c) 2025-2026. caoccao.com Sam Cao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.internal.os.OperatingSystem


object Config {
    const val GROUP_ID = "com.caoccao.qjs4j"
    const val NAME = "qjs4j"
    const val VERSION = Versions.QJS4J
    const val URL = "https://github.com/caoccao/qjs4j"
    const val MAIN_CLASS = "com.caoccao.qjs4j.cli.QuickJSInterpreter"


    object Pom {
        const val ARTIFACT_ID = "qjs4j"
        const val DESCRIPTION = "qjs4j is a native Java implementation of QuickJS."

        object Developer {
            const val ID = "caoccao"
            const val EMAIL = "sjtucaocao@gmail.com"
            const val NAME = "Sam Cao"
            const val ORGANIZATION = "caoccao.com"
            const val ORGANIZATION_URL = "https://www.caoccao.com"
        }

        object License {
            const val NAME = "APACHE LICENSE, VERSION 2.0"
            const val URL = "https://github.com/caoccao/qjs4j/blob/main/LICENSE"
        }

        object Scm {
            const val CONNECTION = "scm:git:git://github.com/caoccao/qjs4j.git"
            const val DEVELOPER_CONNECTION = "scm:git:ssh://github.com/caoccao/qjs4j.git"
        }
    }

    object Projects {
        // https://mvnrepository.com/artifact/org.assertj/assertj-core
        const val ASSERTJ_CORE = "org.assertj:assertj-core:${Versions.ASSERTJ_CORE}"

        // https://mvnrepository.com/artifact/commons-io/commons-io
        const val COMMONS_IO = "commons-io:commons-io:${Versions.COMMONS_IO}"

        // https://mvnrepository.com/artifact/net.javacrumbs.json-unit/json-unit-assertj
        const val JSON_UNIT_ASSERTJ = "net.javacrumbs.json-unit:json-unit-assertj:${Versions.JSON_UNIT_ASSERTJ}"

        const val JUNIT_BOM = "org.junit:junit-bom:${Versions.JUNIT}"
        // https://mvnrepository.com/artifact/org.junit.jupiter/junit-jupiter
        const val JUNIT_JUPITER = "org.junit.jupiter:junit-jupiter"
        const val JUNIT_JUPITER_LAUNCHER = "org.junit.platform:junit-platform-launcher"

        // https://mvnrepository.com/artifact/org.openjdk.jmh/jmh-core
        const val JMH_CORE = "org.openjdk.jmh:jmh-core:${Versions.JMH}"
        const val JMH_GENERATOR_ANNPROCESS = "org.openjdk.jmh:jmh-generator-annprocess:${Versions.JMH}"

        const val JAVET = "com.caoccao.javet:javet:${Versions.JAVET}"
    }

    object Versions {
        const val ASSERTJ_CORE = "3.27.6"
        const val COMMONS_IO = "2.18.0"
        const val JAVA_VERSION = "17"
        const val JAVET = "5.0.5"
        const val JMH = "1.37"
        const val JSON_UNIT_ASSERTJ = "5.1.0"
        const val JUNIT = "6.0.1"
        const val QJS4J = "0.2.0"
    }
}

plugins {
    java
    id("application")
    jacoco
    `maven-publish`
    signing
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}

group = Config.GROUP_ID
version = Config.VERSION

repositories {
    mavenCentral()
}

// The JDK that runs the tests. Defaults to the compilation toolchain; the CI matrix overrides it
// with -PtestJavaVersion so each cell actually exercises the engine on that JDK. Pinning both to 17
// meant the "JDK 21" matrix cell only proved that Gradle could be launched by 21 — the engine still
// compiled and ran on 17 in both cells.
val testJavaVersion = (findProperty("testJavaVersion") as String? ?: Config.Versions.JAVA_VERSION).toInt()

java {
    // A toolchain, not sourceCompatibility/targetCompatibility. Those only set javac flags —
    // Gradle still ran on whatever JDK was on PATH, and the compiled bytecode silently followed the
    // launcher. The toolchain fixes compilation at JDK 17 wherever the build runs from.
    //
    // It does not, however, make every launcher work: Gradle's Kotlin DSL compiles the build script
    // before any toolchain is resolved, so a launcher its embedded Kotlin compiler does not know
    // aborts first, with a bare version number. That is a property of the Gradle version, not of
    // this project — hence the wrapper at 9.4.1, which accepts launchers through JDK 25. Launching
    // on a JDK newer than the wrapper supports still fails early; upgrade the wrapper for that.
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(Config.Versions.JAVA_VERSION.toInt()))
    }
    withJavadocJar()
    withSourcesJar()
}

val testJavaLauncher = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(testJavaVersion))
}

application {
    mainClass.set(Config.MAIN_CLASS)
}

val os = OperatingSystem.current()
val arch = System.getProperty("os.arch")
val osType = if (os.isWindows) "windows" else
    if (os.isMacOsX) "macos" else
        if (os.isLinux) "linux" else ""
val archType = if (arch == "aarch64" || arch == "arm64") "arm64" else "x86_64"

dependencies {
    // https://mvnrepository.com/artifact/org.assertj/assertj-core
    testImplementation(Config.Projects.ASSERTJ_CORE)
    testImplementation(Config.Projects.COMMONS_IO)
    testImplementation(Config.Projects.JSON_UNIT_ASSERTJ)

    // https://mvnrepository.com/artifact/org.junit.jupiter/junit-jupiter
    testImplementation(platform(Config.Projects.JUNIT_BOM))
    testImplementation(Config.Projects.JUNIT_JUPITER)
    testRuntimeOnly(Config.Projects.JUNIT_JUPITER_LAUNCHER)

    // https://mvnrepository.com/artifact/org.openjdk.jmh/jmh-core
    testImplementation(Config.Projects.JMH_CORE)
    testAnnotationProcessor(Config.Projects.JMH_GENERATOR_ANNPROCESS)

    testImplementation(Config.Projects.JAVET)
    testImplementation("com.caoccao.javet:javet-v8-$osType-$archType-i18n:${Config.Versions.JAVET}")
}

tasks.test {
    useJUnitPlatform {
        excludeTags("performance")
    }
    // The heap is set once, for every Test task, in the `withType<Test>` block below. Setting it
    // here as well only looked like it worked: the later block is configured second and wins, so
    // this task ran on a heap the comment here disagreed with, and the memory-accounting tests
    // that were said to depend on it were passing for a different reason than the one documented.
}

// Create a separate task for performance tests
tasks.register<Test>("performanceTest") {
    // The built-in `test` task is given these by the java plugin; a Test task registered here is
    // not, and one with no test classes and no runtime classpath is not an empty run — it is
    // `NO-SOURCE`, which Gradle reports as a successful build having executed nothing. The one
    // advertised way to check a performance claim was therefore a guaranteed green with zero
    // measurements, and stayed that way through every change to the hot paths it names.
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("performance")
    }
    // The JMH annotation processor generates a shadow class per benchmark, each a subclass of the
    // class it was generated from — so each one inherits that class's @Test methods and JUnit runs
    // the whole wrapper again, four extra times, once the task can see them at all.
    exclude("**/jmh_generated/**")
    // One fork. JMH takes a process-global lock, so wrappers running side by side do not measure
    // anything twice — all but the first fail outright with "Another JMH instance might be
    // running". Benchmarks that did share a machine would be measuring the contention rather than
    // the engine, so this is what the task wants regardless.
    maxParallelForks = 1
    group = "verification"
    description = "Runs performance tests using JMH"
    shouldRunAfter(tasks.test)
}

// The zone every conformance run reads dates in.
//
// The suite reads the host's zone, so without pinning one the result depends on what the machine
// happens to be set to — which is how the same code and the same suite gave opposite answers
// depending on whether the task was invoked by a CI wrapper that set TZ or by Gradle as documented.
// Pinned here rather than there, so the task itself owns the guarantee and every caller gets it.
//
// UTC rather than the runner's own Etc/UTC: those are the same instant but not the same identifier,
// and the pinned harness's isCanonicalizedStructurallyValidTimeZoneName still applies the
// pre-canonical-tz rule that rejects "Etc/UTC" — while canonicalize-utc-timezone.js, in the same
// suite, asserts the engine must preserve it. The engine follows the newer rule and is right; only
// the harness is behind. Both spellings are set because the JVM reads user.timezone on every
// platform and TZ only on some.
val test262TimeZone = "UTC"

// The revision the suite has to be at, from the one file that holds it, so the Gradle tasks and the
// runner cannot drift apart from each other or from what this repository records. The runner
// refuses a checkout at any other revision; see test262-revision.txt and Test262Environment.
val test262Revision = providers.fileContents(layout.projectDirectory.file("test262-revision.txt"))
    .asText
    .map { contents ->
        contents.lineSequence()
            .map(String::trim)
            .firstOrNull { line -> line.isNotEmpty() && !line.startsWith("#") }
            .orEmpty()
    }
    .getOrElse("")

val test262AllowAnyRevision = providers.gradleProperty("test262AllowAnyRevision").getOrElse("false")

// Register one Test262 selection.
//
// Every selection gets the same heap, the same pinned zone and the same pinned revision. These were
// four copies of one block, which is how the zone and the revision came to be guaranteed by a CI
// workflow rather than by the tasks contributors are told to run.
fun registerTest262Task(name: String, taskDescription: String, vararg modeArguments: String) =
    tasks.register<JavaExec>(name) {
        group = "verification"
        description = taskDescription

        classpath = sourceSets["test"].runtimeClasspath
        mainClass.set("com.caoccao.qjs4j.test262.Test262Runner")

        // Pass test262 root path (default: ../test262)
        args = listOf("../test262", *modeArguments)

        // Increase heap for large test suite
        jvmArgs("-Xmx2g", "-Duser.timezone=$test262TimeZone")
        environment("TZ", test262TimeZone)
        systemProperty("qjs4j.test262.revision", test262Revision)
        systemProperty("qjs4j.test262.allowAnyRevision", test262AllowAnyRevision)
    }

registerTest262Task("test262", "Run Test262 ECMAScript conformance tests")
registerTest262Task("test262Quick", "Run a quick subset of Test262 tests for validation", "--quick")
registerTest262Task("test262LongRunning", "Run long-running Test262 tests", "--long-running")
registerTest262Task("test262Language", "Run Test262 language tests only", "--language")

// Coverage measurement. There was none, so nothing said which of the ~450 main source files the
// suite exercised at all.
jacoco {
    // 0.8.12 cannot read class-file major version 69, so a test run launched on JDK 25 died while
    // instrumenting the JDK's own classes — which is how the review's JDK 25 matrix cell failed
    // before it reached a single assertion.
    toolVersion = "0.8.14"
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// A report is observational: it cannot fail a build, so nothing stopped coverage from sliding.
// This is the ratchet. The floors sit just under the measured figures for the whole tree, which is
// ~450 files of ported engine written long before any suite existed — a 100% whole-tree rule would
// be a fiction that had to be disabled to commit anything. Raise the floors as coverage rises;
// never lower them to make a build pass.
// Measured on this tree: 61.03% line, 46.80% branch.
val minimumLineCoverage = "0.60".toBigDecimal()
val minimumBranchCoverage = "0.46".toBigDecimal()

// A single whole-tree rule is not a gate on any particular subsystem: a change can leave an entire
// critical package untested while unrelated covered code holds the aggregate above the floor. The
// per-package and per-class floors below are what make a regression in one area fail on its own,
// and each sits just under what that area measures today.
//
// Format: package path to (line floor, branch floor).
val packageCoverageFloors = mapOf(
    "com/caoccao/qjs4j/exceptions" to ("0.95" to "0.93"),
    "com/caoccao/qjs4j/compilation/lexer" to ("0.79" to "0.66"),
    "com/caoccao/qjs4j/cli" to ("0.75" to "0.80"),
    "com/caoccao/qjs4j/compilation/compiler" to ("0.68" to "0.62"),
    "com/caoccao/qjs4j/regexp" to ("0.67" to "0.53"),
    "com/caoccao/qjs4j/compilation/parser" to ("0.67" to "0.56"),
    "com/caoccao/qjs4j/vm" to ("0.65" to "0.50"),
    "com/caoccao/qjs4j/unicode" to ("0.64" to "0.53"),
    // Calibrated to the *lowest* figure across supported JDKs, not to the toolchain's: on JDK 25
    // the lock-free half of ByteArrayAtomics is unreachable by construction, and on 17 and 21 the
    // locked half is only reached through its package-private entry points. Neither run covers
    // both; the union across the matrix does.
    "com/caoccao/qjs4j/utils" to ("0.60" to "0.41"),
    "com/caoccao/qjs4j/core" to ("0.58" to "0.42"),
    "com/caoccao/qjs4j/builtins" to ("0.56" to "0.48"),
)

// The classes an embedder's safety actually rests on: lifecycle, resource limits, the diagnostic
// path, the weak collections, the concurrency primitives and the CLI entry points. Each must stay
// individually covered, so none can quietly become dead-but-shipped code again.
val criticalClassLineFloors = mapOf(
    "com/caoccao/qjs4j/core/JSRuntime" to "0.95",
    // 0.95, above the 0.90 this used to be. It was briefly lowered to 0.85 for margin, on a guess
    // that a run reporting 39 of 44 lines where every other run reported 41 had lost exec data —
    // which lowering an assertion does nothing about, while it does let three covered lines quietly
    // stop being covered. The three that were missing were the rollback around registering a
    // reservation, unreachable from the public API because nothing in the class can throw between
    // the charge and the handle; they now have a test that injects a registry that can, so the
    // class measures 44 of 44 and this floor demands 42. Stricter than it was, with more room.
    "com/caoccao/qjs4j/core/JSMemoryAccounting" to "0.95",
    "com/caoccao/qjs4j/core/JSWeakEntryTable" to "0.85",
    "com/caoccao/qjs4j/exceptions/JSException" to "0.95",
    "com/caoccao/qjs4j/exceptions/JSVirtualMachineException" to "0.80",
    "com/caoccao/qjs4j/cli/REPL" to "0.80",
    "com/caoccao/qjs4j/cli/QuickJSInterpreter" to "0.85",
    "com/caoccao/qjs4j/core/JSWeakSet" to "0.75",
    "com/caoccao/qjs4j/core/JSObject" to "0.75",
    "com/caoccao/qjs4j/core/JSWeakMap" to "0.70",
    "com/caoccao/qjs4j/core/JSArrayBuffer" to "0.70",
    "com/caoccao/qjs4j/utils/ByteArrayAtomics" to "0.65",
    "com/caoccao/qjs4j/utils/DynamicBuffer" to "0.70",
    // The realm collaborators JSContext was decomposed into. A package floor cannot protect any of
    // these: `core` is large and well covered in aggregate, so the whole of ModuleLoader could stop
    // being exercised without the package rule noticing. Each sits just under what it measures.
    "com/caoccao/qjs4j/core/EvalOverlayManager" to "0.95",
    "com/caoccao/qjs4j/core/RegExpLegacyStatics" to "0.95",
    "com/caoccao/qjs4j/core/ModuleSourceTransformer" to "0.78",
    // Lower than its neighbours because it is: the loader's TLA and deferred-evaluation ordering is
    // reached only by evaluating real module graphs, and much of it is still only covered that way.
    // It is a floor to raise, not a figure to be satisfied with.
    "com/caoccao/qjs4j/core/ModuleLoader" to "0.50",
)

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = minimumLineCoverage
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = minimumBranchCoverage
            }
        }
        packageCoverageFloors.forEach { (packageName, floors) ->
            rule {
                element = "PACKAGE"
                includes = listOf(packageName.replace('/', '.'))
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = floors.first.toBigDecimal()
                }
                limit {
                    counter = "BRANCH"
                    value = "COVEREDRATIO"
                    minimum = floors.second.toBigDecimal()
                }
            }
        }
        criticalClassLineFloors.forEach { (className, floor) ->
            rule {
                element = "CLASS"
                includes = listOf(className.replace('/', '.'))
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = floor.toBigDecimal()
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}

tasks.register("sourceJar") {
    group = "build"
    description = "Alias task for sourcesJar."
    dependsOn(tasks.named("sourcesJar"))
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        // -Xlint:all rather than deprecation alone: the disabled categories surface the unchecked
        // casts, fall-throughs and this-escapes that hid real defects. `serial` is off because no
        // class here is meant to be Java-serialized, and `processing` because no annotation
        // processors are configured.
        // -Werror makes the zero-warning claim enforceable. -Xlint:all only prints; the tree was
        // reported as warning-free while a trailing-space text block warned on every build.
        // The toolchain pins javac to one release, so the warning set does not drift with the
        // launching JDK.
        options.compilerArgs.addAll(
            listOf("-Xlint:all", "-Xlint:-serial", "-Xlint:-processing", "-Werror")
        )
    }
    withType<Javadoc> {
        options.encoding = "UTF-8"
        // Now that the tree is clean under the categories below, a new Javadoc defect fails the
        // build instead of scrolling past.
        isFailOnError = true
        // Xdoclint:none suppressed the diagnostics that would have caught a Javadoc block naming
        // the wrong type, a stray asterisk mid-sentence, and one comment swallowed into another.
        // syntax+reference are the categories that find those; the noisier `missing` stays off.
        (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:syntax,reference", "-quiet")
    }
    withType<Test> {
        javaLauncher.set(testJavaLauncher)
        systemProperty("file.encoding", "UTF-8")
        // The one authoritative test heap. Gradle's default of 512 MB is below what the engine's
        // own resource limits need to be reachable: a string-length or array-join limit only fires
        // after the builder has grown past it, so a smaller heap runs out first and the JVM dies
        // instead of the test observing a RangeError. Matches the heap the test262 tasks use.
        //
        // No test asserts anything about this number. The memory-accounting rollback tests drive
        // their failure through an injected allocator, and the two that go through the JVM's own
        // allocation limit state their premise as an assumption, so they skip rather than quietly
        // stop testing anything if a future heap could satisfy the request.
        maxHeapSize = "2g"
        // The interpreter recurses through Java frames for structures a script nests — a chain of a
        // thousand proxies, for one — and the engine's own call-depth budget is meant to be what
        // bounds that, with a catchable RangeError at a documented, configurable limit. On the
        // default thread stack it is not: whether a thousand layers fit depends on how much the JIT
        // has compiled, so the same deep-chain test passed alone and overflowed the JVM stack in a
        // busy suite, reporting the same RangeError for a quite different reason. A larger stack
        // makes the engine's limit the one being observed.
        jvmArgs("-Xss8m")
        val cpuCount = Runtime.getRuntime().availableProcessors()
        maxParallelForks = maxOf(
            1,
            if (os.isMacOsX) cpuCount * 3 / 4 else cpuCount / 2
        )
    }
}

publishing {
    publications {
        create<MavenPublication>("generatePom") {
            from(components["java"])
            pom {
                artifactId = Config.Pom.ARTIFACT_ID
                description.set(Config.Pom.DESCRIPTION)
                groupId = Config.GROUP_ID
                name.set(Config.NAME)
                url.set(Config.URL)
                version = Config.VERSION
                licenses {
                    license {
                        name.set(Config.Pom.License.NAME)
                        url.set(Config.Pom.License.URL)
                    }
                }
                developers {
                    developer {
                        id.set(Config.Pom.Developer.ID)
                        email.set(Config.Pom.Developer.EMAIL)
                        name.set(Config.Pom.Developer.NAME)
                        organization.set(Config.Pom.Developer.ORGANIZATION)
                        organizationUrl.set(Config.Pom.Developer.ORGANIZATION_URL)
                    }
                }
                scm {
                    connection.set(Config.Pom.Scm.CONNECTION)
                    developerConnection.set(Config.Pom.Scm.DEVELOPER_CONNECTION)
                    tag.set("v${Config.VERSION}")
                    url.set(Config.URL)
                }
                properties.set(
                    mapOf(
                        "maven.compiler.source" to Config.Versions.JAVA_VERSION,
                        "maven.compiler.target" to Config.Versions.JAVA_VERSION,
                    )
                )
            }
        }
    }
}

signing {
    val signingKey = providers.gradleProperty("signingKey")
        .orElse(providers.environmentVariable("MAVEN_GPG_PRIVATE_KEY"))
        .orNull
    val signingPassword = providers.gradleProperty("signingPassword")
        .orElse(providers.environmentVariable("MAVEN_GPG_PASSPHRASE"))
        .orNull
    if (!signingKey.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}

nexusPublishing {
    repositoryDescription.set("qjs4j v${Config.VERSION}")
    repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
            username.set(
                providers.gradleProperty("sonatypeUsername")
                    .orElse(providers.environmentVariable("SONATYPE_USERNAME"))
            )
            password.set(
                providers.gradleProperty("sonatypePassword")
                    .orElse(providers.environmentVariable("SONATYPE_PASSWORD"))
            )
        }
    }
}
