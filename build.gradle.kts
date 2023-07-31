import org.gradle.api.publish.maven.MavenPom
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Date
import java.io.FileInputStream
import java.util.Properties
import java.util.concurrent.atomic.AtomicReference
import java.util.Scanner

object Versions {
    val java = JavaVersion.VERSION_17 // can't go higher than 17 because of shadowJar

    const val kotlin = "1.9.0"

    const val nettyBuffer = "4.1.95.Final" // https://mvnrepository.com/artifact/io.netty/netty-buffer
    const val reactor = "3.5.8" // https://mvnrepository.com/artifact/io.projectreactor/reactor-core
    const val reactorNetty = "1.1.9" // https://mvnrepository.com/artifact/io.projectreactor.netty/reactor-netty
    const val moshi = "1.15.0" // https://mvnrepository.com/artifact/com.squareup.moshi/moshi
    const val moshix = "0.24.0" // https://mvnrepository.com/artifact/dev.zacsweers.moshix/dev.zacsweers.moshix.gradle.plugin
    const val classgraph = "4.8.161" // https://mvnrepository.com/artifact/io.github.classgraph/classgraph

    const val log4j = "2.20.0" // https://mvnrepository.com/artifact/org.apache.logging.log4j/log4j-core

    const val jdbi3 = "3.39.1" // https://mvnrepository.com/artifact/org.jdbi/jdbi3-core
    const val postgresql = "42.6.0" // https://mvnrepository.com/artifact/org.postgresql/postgresql
    const val jackson = "2.14.2" // https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-core
    const val h2 = "2.1.214" // https://mvnrepository.com/artifact/com.h2database/h2
    const val walkmindSerializers = "1.7"
    const val hikariCP = "5.0.1" // https://mvnrepository.com/artifact/com.zaxxer/HikariCP
    const val reactorKotlinExt = "1.2.2" // https://mvnrepository.com/artifact/io.projectreactor.kotlin/reactor-kotlin-extensions
    const val commonsCsv = "1.10.0" // https://mvnrepository.com/artifact/org.apache.commons/commons-csv/
    const val telegramBots = "6.5.0"

    val commitHash by lazy {
        val scanner = Scanner(Runtime.getRuntime().exec("git rev-parse HEAD").inputStream)
        if (scanner.hasNext())
            scanner.next()
        else
            ""
    }
}

plugins {
    kotlin("jvm") version "1.9.0"
    id("dev.zacsweers.moshix") version "0.24.0"
    idea
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("maven-publish")
//    `java-library`
}

kotlin {
    //    experimental.coroutines = Coroutines.ENABLE
}

project.group = "com.walkmind.extensions"
project.version = "1.0"

val publicationName = "DefaultPublication"
val artifactID = "netty-routing"
val licenseName = "Apache-2.0"
val licenseUrl = "http://opensource.org/licenses/apache-2.0"
val repoHttpsUrl = "https://github.com/unoexperto/extensions-netty-routing.git"
val repoSshUri = "git@github.com:unoexperto/extensions-netty-routing.git"

val awsCreds = File(System.getProperty("user.home") + "/.aws/credentials")
    .let {

        if (it.exists())
            it.readLines()
                .map {
                    val commentPos = it.indexOf('#')
                    if (commentPos >= 0) {
                        it.substring(0, commentPos).trim()
                    } else
                        it.trim()
                }
                .filter { it.isNotEmpty() }
                .fold(mutableMapOf<String, MutableMap<String, String>>() to AtomicReference<String>()) { (acc, cur), s ->

                    if (s.startsWith("[") && s.endsWith("]")) {
                        cur.set(s.substring(1, s.length - 1))
                    } else
                        if (s.contains("=")) {
                            check(cur.get() != null)
                            val items = acc.computeIfAbsent(cur.get()) { mutableMapOf<String, String>() }
                            val (k, v) = s.split("=").map { it.trim() }
                            items[k.toLowerCase()] = v
                        }

                    acc to cur
                }
                .first
        else
            emptyMap()
    }
    .get("bp")!!

val sourcesJar by tasks.creating(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

val javadocJar by tasks.creating(Jar::class) {
    archiveClassifier.set("javadoc")
    from("$buildDir/javadoc")
}

val jar = tasks["jar"] as org.gradle.jvm.tasks.Jar

fun MavenPom.addDependencies() = withXml {
    asNode().appendNode("dependencies").let { depNode ->
        configurations.implementation.get().allDependencies.forEach {
            depNode.appendNode("dependency").apply {
                appendNode("groupId", it.group)
                appendNode("artifactId", it.name)
                appendNode("version", it.version)
            }
        }
    }
}

publishing {
    repositories {
        maven {
//            name = "GitHubPackages"
            url = uri("s3://${awsCreds["maven_bucket"]!!}/")
//            url = uri("https://maven.pkg.github.com/unoexperto/maven/")
            credentials(AwsCredentials::class) {

                accessKey = awsCreds["aws_access_key_id"]
                secretKey = awsCreds["aws_secret_access_key"]
//                sessionToken = "someSTSToken" // optional
            }
//            credentials {
//                username = "unoexperto"
//                password = "xxxx"
//            }
        }
    }

    publications {

        create(publicationName, MavenPublication::class) {
            artifactId = artifactID
            groupId = project.group.toString()
            version = project.version.toString()
            description = project.description

            artifact(jar)
            artifact(sourcesJar) {
                classifier = "sources"
            }
            artifact(javadocJar) {
                classifier = "javadoc"
            }
            pom.addDependencies()
            pom {
                packaging = "jar"
                developers {
                    developer {
                        email.set("unoexperto.support@mailnull.com")
                        id.set("unoexperto")
                        name.set("ruslan")
                    }
                }
                licenses {
                    license {
                        name.set(licenseName)
                        url.set(licenseUrl)
                        distribution.set("repo")
                    }
                }
                scm {
                    connection.set("scm:$repoSshUri")
                    developerConnection.set("scm:$repoSshUri")
                    url.set(repoHttpsUrl)
                }
            }
        }
    }
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

dependencies {
    implementation(kotlin("stdlib", Versions.kotlin))

    compileOnly("io.projectreactor.netty:reactor-netty-http:${Versions.reactorNetty}") {
        exclude("io.netty", "netty-resolver-dns-native-macos")
    }
    compileOnly("com.squareup.moshi:moshi:${Versions.moshi}")

    testImplementation(kotlin("test-junit5", Versions.kotlin))
}

repositories {
    mavenCentral()
    maven ("https://repo.spring.io/snapshot")
    maven ("https://repo.spring.io/release")
}

configurations {
    implementation {
        resolutionStrategy.failOnVersionConflict()
    }
}

//sourceSets {
//    main {
//        java.srcDir("src/core/java")
//    }
//}

//java {
//    sourceCompatibility = JavaVersion.VERSION_1_8
//    targetCompatibility = JavaVersion.VERSION_1_8
//}

tasks {

    withType<JavaCompile> {
        sourceCompatibility  = Versions.java.majorVersion
        targetCompatibility = Versions.java.majorVersion

//        options.compilerArgs.add("--enable-preview")
//        inputs.property("moduleName", moduleName)
//        doFirst {
//            options.compilerArgs = listOf(
//                "--module-path", classpath.asPath,
//                "--patch-module", "$moduleName=${sourceSets["main"].output.asPath}"
//            )
//            classpath = files()
//        }
    }

    withType<KotlinCompile> {
        // https://github.com/JetBrains/kotlin/blob/master/compiler/util/src/org/jetbrains/kotlin/config/LanguageVersionSettings.kt
        kotlinOptions.freeCompilerArgs = listOf(
            "-Xextended-compiler-checks",
            "-Xjsr305=strict",
            "-Xjvm-default=all",
            "-Xinline-classes",
            "-opt-in=kotlin.ExperimentalStdlibApi",
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-opt-in=kotlin.io.path.ExperimentalPathApi",
            "-opt-in=kotlin.js.ExperimentalJsExport",
            "-opt-in=kotlin.time.ExperimentalTime",
            "-opt-in=kotlin.ExperimentalUnsignedTypes",
            "-Xskip-prerelease-check",
        )
        kotlinOptions.apiVersion = "1.9"
        kotlinOptions.languageVersion = "1.9"
        kotlinOptions.jvmTarget = Versions.java.majorVersion
    }

    withType<Test>().all {
//        jvmArgs = listOf("--enable-preview")
        testLogging.showStandardStreams = true
        testLogging.showExceptions = true
        useJUnitPlatform {
        }
    }

    withType<JavaExec>().all {
//        jvmArgs = listOf("--enable-preview")
    }

    withType<Wrapper>().all {
        gradleVersion = "8.0"
        distributionType = Wrapper.DistributionType.BIN
    }

    withType<JavaCompile>().all {
//        options.compilerArgs.addAll(listOf("--enable-preview", "-Xlint:preview"))
    }
}
