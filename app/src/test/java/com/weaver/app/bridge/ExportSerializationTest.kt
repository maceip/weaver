package com.weaver.app.bridge

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bridge is a JSON wire protocol — if export messages stop round-tripping,
 * the WebView and native sides silently disagree. Locked down before the
 * on-device run.
 */
class ExportSerializationTest {

    // Mirrors Bridge.json so the test exercises the real wire format.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    @Test
    fun exportKind_hasTheEightStitchTargets() {
        assertEquals(
            listOf("Figma", "CopyCode", "Zip", "Firebase", "AiStudio", "Jules", "Lovable", "Bolt"),
            ExportKind.entries.map { it.name },
        )
    }

    @Test
    fun requestExport_roundTripsEveryExportKind() {
        for (kind in ExportKind.entries) {
            val original: Inbound = Inbound.RequestExport(kind = kind, id = "node-7")
            val encoded = json.encodeToString(Inbound.serializer(), original)
            val decoded = json.decodeFromString(Inbound.serializer(), encoded)
            assertEquals("round-trip failed for $kind", original, decoded)
        }
    }

    @Test
    fun requestExport_emitsTheDiscriminatorAndKindContentScriptExpects() {
        val encoded = json.encodeToString(
            Inbound.serializer(),
            Inbound.RequestExport(ExportKind.Firebase, id = null),
        )
        assertTrue(encoded, encoded.contains("\"type\":\"request_export\""))
        assertTrue(encoded, encoded.contains("\"kind\":\"Firebase\""))
    }

    @Test
    fun exportComplete_roundTrips() {
        val payload: JsonObject = buildJsonObject { put("url", "https://example.test/app.zip") }
        val original: Outbound = Outbound.ExportComplete(kind = ExportKind.Zip, payload = payload)
        val encoded = json.encodeToString(Outbound.serializer(), original)
        val decoded = json.decodeFromString(Outbound.serializer(), encoded)
        assertEquals(original, decoded)
    }
}
