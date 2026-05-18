package com.weaver.app.bridge

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class StitchNode(
    val id: String,
    val type: NodeType = NodeType.Screen,
    val label: String? = null,
    val x: Float = 0f,
    val y: Float = 0f,
    val w: Float = 0f,
    val h: Float = 0f,
    val thumb: String? = null,
    val selected: Boolean = false,
)

enum class NodeType { Screen, Asset, DesignSystem, Unknown }

enum class GenerationState { Pending, Streaming, Complete, Failed }

enum class AssetKind { Thumbnail, FullRender }

enum class ExportKind { Figma, Zip, AiStudio, RawCode }

enum class SlashCommand { Image, Logo, Diagram, Animate }

enum class AttachmentKind { UploadFile, WebsiteUrl, Variations }

enum class CanvasTool {
    Cursor, Marquee, Edit, Hand, InsertImage, Palette, Favorite
}

enum class CanvasAction {
    // Generate menu
    InstantPrototype, Variations, Regenerate, PredictiveHeatmap,
    MobileAppVersion, MissingStates, Animate,
    // Modify menu
    Edit, Annotate, OpenDesignDoc,
    // Preview menu
    PreviewNewTab, PreviewQrCode, PreviewMobile, PreviewTablet, PreviewDesktop,
    // Multi-select extras
    AlignLeft, DistributeHorizontal,
    // Generic
    More
}

@Serializable
data class Preset(
    val id: String,
    val name: String,
    val palette: List<String> = emptyList(),
    val isBuiltin: Boolean = false,
)

@Serializable
sealed interface Outbound {
    @Serializable
    @SerialName("nodes_updated")
    data class NodesUpdated(val nodes: List<StitchNode>) : Outbound

    @Serializable
    @SerialName("selection_changed")
    data class SelectionChanged(val ids: List<String> = emptyList()) : Outbound

    @Serializable
    @SerialName("generation_progress")
    data class GenerationProgress(
        val id: String,
        val state: GenerationState,
        val partialRender: String? = null,
    ) : Outbound

    @Serializable
    @SerialName("asset_ready")
    data class AssetReady(
        val id: String,
        val kind: AssetKind,
        val data: String,
        val revision: Long = 0,
    ) : Outbound

    @Serializable
    @SerialName("export_complete")
    data class ExportComplete(
        val kind: ExportKind,
        val payload: JsonElement,
    ) : Outbound

    @Serializable
    @SerialName("agent_log_updated")
    data class AgentLogUpdated(val entries: List<AgentLogEntry>) : Outbound

    @Serializable
    @SerialName("error")
    data class Error(val code: String, val message: String) : Outbound
}

enum class AgentRole { User, Agent, System }

@Serializable
data class AgentLogEntry(
    val id: String,
    val role: AgentRole = AgentRole.Agent,
    val text: String,
    val timestamp: Long = 0,
)

@Serializable
sealed interface Inbound {
    @Serializable
    @SerialName("submit_prompt")
    data class SubmitPrompt(
        val text: String,
        val scopeId: String? = null,
        val slash: SlashCommand? = null,
        val presetId: String? = null,
        val modelId: String? = null,
    ) : Inbound

    @Serializable
    @SerialName("attach")
    data class Attach(
        val kind: AttachmentKind,
        val payload: String? = null,
    ) : Inbound

    @Serializable
    @SerialName("select_preset")
    data class SelectPreset(val presetId: String) : Inbound

    @Serializable
    @SerialName("select_model")
    data class SelectModel(val modelId: String) : Inbound

    @Serializable
    @SerialName("voice_input")
    data class VoiceInput(val text: String) : Inbound

    @Serializable
    @SerialName("select_node")
    data class SelectNode(val id: String) : Inbound

    @Serializable
    @SerialName("clear_selection")
    data object ClearSelection : Inbound

    @Serializable
    @SerialName("synthesize_input")
    data class SynthesizeInput(val event: JsonElement) : Inbound

    @Serializable
    @SerialName("request_export")
    data class RequestExport(val kind: ExportKind, val id: String? = null) : Inbound

    @Serializable
    @SerialName("viewport_changed")
    data class ViewportChanged(val w: Int, val h: Int) : Inbound

    @Serializable
    @SerialName("canvas_action")
    data class Canvas(
        val action: CanvasAction,
        val ids: List<String> = emptyList(),
    ) : Inbound

    @Serializable
    @SerialName("select_tool")
    data class SelectTool(val tool: CanvasTool) : Inbound
}
