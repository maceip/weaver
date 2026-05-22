import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
  id("org.jetbrains.kotlin.jvm") version "2.2.0"
  application
  id("com.gradleup.shadow") version "8.3.6"
}

repositories {
  mavenCentral()
  google()
}

// src/main/kotlin/com/android/keyattestation/** is vendored verbatim from
// github.com/android/keyattestation @ a83ff03 (Apache-2.0, see LICENSE).
// This dependency set is that project's build.gradle.kts, copied verbatim.
dependencies {
  implementation("androidx.annotation:annotation:1.9.1")
  implementation("co.nstant.in:cbor:0.9")
  implementation("com.google.code.gson:gson:2.11.0")
  implementation("com.google.errorprone:error_prone_annotations:2.41.0")
  implementation("com.google.protobuf:protobuf-javalite:4.28.3")
  implementation("com.google.protobuf:protobuf-kotlin-lite:4.28.3")
  implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
  implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.2")
  implementation("com.google.guava:guava:33.5.0-jre")
}

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

application { mainClass = "com.weaver.attestation.SidecarKt" }

// roots.json -> generated GoogleTrustAnchors.kt (verbatim from keyattestation).
val generatedSourcesDir = layout.buildDirectory.dir("generated")

val googleTrustAnchors by
  tasks.registering {
    val jsonFile = file("roots.json")
    val json = jsonFile.readText()
    val generatedFile = generatedSourcesDir.get().file("main/kotlin/GoogleTrustAnchors.kt")
    inputs.files(jsonFile)
    outputs.file(generatedFile)
    doLast {
      generatedFile
        .getAsFile()
        .writeText(
          """
        package com.android.keyattestation.verifier

        import com.android.keyattestation.verifier.asX509Certificate

        import com.google.gson.Gson
        import java.security.cert.TrustAnchor

        object GoogleTrustAnchors : () -> Set<TrustAnchor> {
          const val JSON = ""${'"'}
            $json
            ""${'"'}

          override operator fun invoke(): Set<TrustAnchor> {
            return Gson()
              .fromJson(JSON, Array<String>::class.java)
              .map { TrustAnchor(it.asX509Certificate(), null) }
              .toSet()
          }
        }
        """
        )
    }
  }

val generateSources by
  tasks.registering {
    outputs.dir(generatedSourcesDir)
    dependsOn(googleTrustAnchors)
  }

sourceSets { main { kotlin.srcDir(generateSources) } }

tasks.named("compileKotlin").configure { dependsOn(generateSources) }

tasks.test {
  useJUnitPlatform()
  testLogging { exceptionFormat = TestExceptionFormat.FULL }
}

// Fat jar: BouncyCastle ships a signed jar — its signature files must be
// dropped or the JVM rejects the merged jar. JCA provider service files are
// merged so the registered providers still resolve.
tasks.shadowJar {
  archiveFileName = "weaver-attestation-verifier.jar"
  exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
  mergeServiceFiles()
}
