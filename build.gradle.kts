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

import org.gradle.internal.os.OperatingSystem


object Config {
    const val GROUP_ID = "com.caoccao.qjs4j"
    const val NAME = "qjs4j"
    const val VERSION = Versions.QJS4J
    const val URL = "https://github.com/caoccao/qjs4j"
    const val MAIN_CLASS = "com.caoccao.qjs4j.cli.QuickJSInterpreter"


    object Pom {
        const val ARTIFACT_ID = "javet"
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
            const val URL = "https://github.com/caoccao/Javet/blob/main/LICENSE"
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
        const val JAVET = "5.0.2"
        const val JMH = "1.37"
        const val JSON_UNIT_ASSERTJ = "5.1.0"
        const val JUNIT = "6.0.1"
        const val QJS4J = "0.1.0"
    }
}

plugins {
    java
    id("application")
    `maven-publish`
}

group = Config.GROUP_ID
version = Config.VERSION

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
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
}

// Create a separate task for performance tests
tasks.register<Test>("performanceTest") {
    useJUnitPlatform {
        includeTags("performance")
    }
    group = "verification"
    description = "Runs performance tests using JMH"
    shouldRunAfter(tasks.test)
}

// Create a task for running test262 conformance tests
tasks.register<JavaExec>("test262") {
    group = "verification"
    description = "Run Test262 ECMAScript conformance tests"
    
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.caoccao.qjs4j.test262.Test262Runner")
    
    // Pass test262 root path (default: ../test262)
    args = listOf("../test262")
    
    // Increase heap for large test suite
    jvmArgs = listOf("-Xmx2g")
}

// Quick test262 validation (limited tests)
tasks.register<JavaExec>("test262Quick") {
    group = "verification"
    description = "Run a quick subset of Test262 tests for validation"
    
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.caoccao.qjs4j.test262.Test262Runner")
    
    args = listOf("../test262", "--quick")
    
    jvmArgs = listOf("-Xmx1g")
}

// Long-running tests (e.g. decodeURI heavy loops)
tasks.register<JavaExec>("test262LongRunning") {
    group = "verification"
    description = "Run long-running Test262 tests"

    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.caoccao.qjs4j.test262.Test262Runner")

    args = listOf("../test262", "--long-running")

    jvmArgs = listOf("-Xmx1g")
}

// Language tests only
tasks.register<JavaExec>("test262Language") {
    group = "verification"
    description = "Run Test262 language tests only"
    
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.caoccao.qjs4j.test262.Test262Runner")
    
    args = listOf("../test262", "--language")
    
    jvmArgs = listOf("-Xmx2g")
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:deprecation")
    }
    withType<Javadoc> {
        options.encoding = "UTF-8"
    }
    withType<Test> {
        systemProperty("file.encoding", "UTF-8")
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
                    tag.set(Config.Versions.JAVET)
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
