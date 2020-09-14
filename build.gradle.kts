import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

val sonatypePassword: String by project

plugins {
    id("org.gradle.checkstyle")
    id("org.gradle.idea")
    id("org.gradle.jacoco")
    id("org.gradle.java")
    id("org.gradle.maven-publish")
    id("org.gradle.project-report")

    kotlin("jvm") version "1.4.0"

    id("com.github.spotbugs") version "4.5.0"
    id("io.gitlab.arturbosch.detekt") version "1.11.0"
    id("io.spring.dependency-management") version "1.0.10.RELEASE"
    id("me.champeau.gradle.jmh") version "0.5.0" apply false
    id("org.jlleitschuh.gradle.ktlint") version "9.4.0"
    id("org.springframework.boot") version "2.3.3.RELEASE" apply false
}

val VERSION = "1.0.0-SNAPSHOT"

object Versions {
    const val KOTLIN = "1.4.0"
    const val KOTLIN_COROUTINES = "1.3.9"
    const val JACKSON = "2.11.2"
}

allprojects {
    group = "la.serendipity.jackson-kotlin-inline-support"
    version = VERSION
    apply(plugin = "project-report")

    repositories {
        jcenter()
    }
}

fun Project.hasContents(): Boolean {
    return this.file("src").isDirectory || this.file("build.gradle.kts").isFile
}

allprojects {
    if (project != rootProject && !project.hasContents()) return@allprojects

    apply {
        plugin("org.jlleitschuh.gradle.ktlint")
    }

    ktlint {
        version.set("0.38.1")
        android.set(true)
        verbose.set(true)
        reporters {
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
        }
        disabledRules.set(
                setOf(
                        "import-ordering",
                        "indent",
                        "parameter-list-wrapping"
                )
        )
    }
}

subprojects {
    if (!project.hasContents()) return@subprojects

    apply {
        plugin<JavaLibraryPlugin>()
        plugin("maven-publish")
        plugin("com.github.spotbugs")
        plugin("io.gitlab.arturbosch.detekt")
        plugin("io.spring.dependency-management")
        plugin("jacoco")
        plugin("org.gradle.checkstyle")
    }

    detekt {
        reports {
            xml.enabled = false
            html.enabled = true
            txt.enabled = true
        }
        parallel = true
        ignoreFailures = true
    }

    tasks.test {
        maxHeapSize = "1G"
        testLogging.showStackTraces = true
        testLogging.exceptionFormat = TestExceptionFormat.FULL
    }

    tasks.withType(JavaCompile::class.java) {
        sourceCompatibility = JavaVersion.VERSION_11.toString()
        targetCompatibility = JavaVersion.VERSION_11.toString()

        options.encoding = "UTF-8"
        options.compilerArgs.addAll(
                listOf(
                        "-Xlint:all", "-Xlint:-processing", "-Xlint:-classfile",
                        "-Xlint:-serial", "-Xdiags:verbose",
                        "-parameters"
                )
        )

        if (properties["skip_error"] != "true") {
            // To skip -Werror, invoke gradle with -Pskip_werror=true
            options.compilerArgs.add("-Werror")
        }

        sourceSets {
            main { java { setSrcDirs(srcDirs + file("src/main/kotlin/")) } }
            test { java { setSrcDirs(srcDirs + file("src/test/kotlin/")) } }
        }
    }

    val checkstyleConfigDir = file("$rootDir/config/checkstyle")

    checkstyle {
        toolVersion = "8.35"
        sourceSets = setOf(project.sourceSets["main"])
    }

    tasks.withType<com.github.spotbugs.snom.SpotBugsTask> {
        reports {
            create("html")
        }
    }

    tasks {
        spotbugsTest {
            enabled = false // ignore test codes
        }
    }

    jacoco {
        toolVersion = "0.8.5"
        reportsDir = file("$buildDir/customJacocoReportDir")
    }

    tasks.withType<Test> { // https://github.com/junit-team/junit5-samples/blob/r5.7.0/junit5-jupiter-starter-gradle/build.gradle
        useJUnitPlatform()
        testLogging {
            events.add(TestLogEvent.PASSED)
            events.add(TestLogEvent.FAILED)
            events.add(TestLogEvent.SKIPPED)
        }
    }

    dependencyManagement {
        imports {
            // https://docs.spring.io/spring-boot/docs/current/reference/html/appendix-dependency-versions.html#dependency-versions-properties
            mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES) {
                extra["jackson-bom.version"] = Versions.JACKSON
                extra["kotlin-coroutines.version"] = Versions.KOTLIN_COROUTINES
                extra["kotlin.version"] = Versions.KOTLIN
            }
        }

        dependencies {
            dependency("io.github.microutils:kotlin-logging:1.7.8")
        }
    }

    configurations.all {
        exclude(group = "log4j") // = Log4j implementation (Old). Replaced by log4j-over-slf4j.
        exclude(module = "slf4j-log4j12") // = SLF4J > Log4J Implementation by SLF4J.
        exclude(module = "slf4j-jdk14") // = SLF4J > JDK14 Binding.
        exclude(module = "slf4j-jcl") // = SLF4J > Commons Logging.
        exclude(module = "commons-logging") // Because bridged by jcl-over-slf4j.
        exclude(module = "commons-logging-api") // Replaced by jcl-over-slf4j.
        exclude(module = "servlet-api") // Old pre-3.0 servlet API artifact

        resolutionStrategy {
            // Disable Gradle caches the contents and artifacts of changing modules.
            // By default, these cached values are kept for 24 hours, after which the cached entry is expired and the module is resolved again.
            cacheChangingModulesFor(0, "seconds")
        }
    }

    configurations.compileClasspath {
        // Libraries we don't use our codebase (suppress autocomplete) but needed runtime.
        exclude(group = "commons-lang", module = "commons-lang")
        exclude(group = "org.codehaus.jackson")
        exclude(group = "org.apache.logging.log4j")
        exclude(group = "javax.inject", module = "javax.inject")
    }

    dependencies {
        testImplementation("io.github.microutils:kotlin-logging")
        testImplementation("org.junit.jupiter:junit-jupiter-api")
        testRuntimeOnly("org.springframework.boot:spring-boot-starter-logging")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    }

    plugins.withType<org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper> {
        tasks {
            listOf(compileKotlin, compileTestKotlin).forEach { kotlinCompile ->
                kotlinCompile {
                    kotlinOptions {
                        jvmTarget = "11"
                        allWarningsAsErrors = true
                        javaParameters = true
                        freeCompilerArgs = listOf(
                                "-Xjsr305=strict",
                                "-Xemit-jvm-type-annotations",
                                "-Xopt-in=kotlin.ExperimentalStdlibApi",
                                "-Xopt-in=kotlin.RequiresOptIn",
                                "-Xinline-classes"
                        )
                    }
                }
            }
        }
    }

    plugins.withType<me.champeau.gradle.JMHPlugin> {
        dependencies {
            add("jmhCompileOnly", "org.openjdk.jmh:jmh-generator-annprocess:1.22")
            add("jmhAnnotationProcessor", "org.openjdk.jmh:jmh-generator-annprocess:1.22")
        }

        configure<me.champeau.gradle.JMHPluginExtension> {
            // Default value for casual testing.
            fork = 1
            warmupIterations = 3
            isZip64 = true
            duplicateClassesStrategy = DuplicatesStrategy.WARN
        }
    }
}

configure(
        listOf(project(":jackson-kotlin-inline-class-module"))
) {
    plugins.withType<PublishingPlugin> {
        extensions.findByType(org.gradle.api.publish.PublishingExtension::class.java)!!.apply {
            repositories {
                maven("https://oss.sonatype.org/content/repositories/snapshots/") {
                    credentials {
                        username = "kazuki-ma"
                        password = sonatypePassword
                    }
                }
            }
            publications {
                create<MavenPublication>("mavenJava") {
                    from(components["java"])
                    versionMapping {
                        usage("java-api") {
                            fromResolutionOf("runtimeClasspath")
                        }
                        usage("java-runtime") {
                            fromResolutionResult()
                        }
                    }
                }
            }
        }
    }
}

tasks.wrapper {
    distributionType = Wrapper.DistributionType.ALL
}
