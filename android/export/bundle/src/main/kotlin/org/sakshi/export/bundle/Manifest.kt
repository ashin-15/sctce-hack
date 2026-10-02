package org.sakshi.export.bundle

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Versions of the software that produced the bundle contents. */
public data class GeneratorInfo(val appVersion: String, val ruleVersions: List<String>, val modelVersions: List<String>)

/** Counts of items left out of the bundle. Never hashes of the omitted items. */
public data class OmittedCounts(val evidenceCount: Int, val derivativeCount: Int, val eventCount: Int) {
    init {
        require(evidenceCount >= 0 && derivativeCount >= 0 && eventCount >= 0) { "Omitted counts must not be negative" }
    }
}

internal data class ManifestFile(val path: String, val role: String, val sha256: String, val size: Long)

internal data class Manifest(
    val snapshotId: String,
    val caseId: String,
    val createdAt: String,
    val generator: GeneratorInfo,
    val files: List<ManifestFile>,
    val merkleRoot: String,
    val auditChainHead: String,
    val omitted: OmittedCounts,
    val limits: List<String>,
) {
    fun toJson(): JsonObject = jsonObject(
        "format" to JsonPrimitive(BundleFormat.FORMAT),
        "snapshot_id" to JsonPrimitive(snapshotId),
        "case_id" to JsonPrimitive(caseId),
        "created_at" to JsonPrimitive(createdAt),
        "created_at_basis" to JsonPrimitive(BundleFormat.CLOCK_BASIS),
        "generator" to jsonObject(
            "app_version" to JsonPrimitive(generator.appVersion),
            "rule_versions" to jsonStrings(generator.ruleVersions),
            "model_versions" to jsonStrings(generator.modelVersions),
        ),
        "event_schema" to JsonPrimitive(BundleFormat.EVENT_SCHEMA),
        "files" to JsonArray(files.map(::fileJson)),
        "merkle_root" to JsonPrimitive(merkleRoot),
        "audit_chain_head" to JsonPrimitive(auditChainHead),
        "omitted" to jsonObject(
            "evidence_count" to JsonPrimitive(omitted.evidenceCount),
            "derivative_count" to JsonPrimitive(omitted.derivativeCount),
            "event_count" to JsonPrimitive(omitted.eventCount),
        ),
        "limits" to jsonStrings(limits),
    )

    companion object {
        /** @throws IllegalArgumentException if the shape is wrong. Values are not judged here. */
        fun parse(element: JsonElement): Manifest {
            val o = element as? JsonObject ?: throw IllegalArgumentException("manifest must be an object")
            require(o.str("format") == BundleFormat.FORMAT) { "unsupported format" }
            require(o.str("created_at_basis") == BundleFormat.CLOCK_BASIS) { "unsupported created_at_basis" }
            require(o.str("event_schema") == BundleFormat.EVENT_SCHEMA) { "unsupported event_schema" }
            val generator = o.obj("generator")
            val omitted = o.obj("omitted")
            return Manifest(
                snapshotId = o.str("snapshot_id"),
                caseId = o.str("case_id"),
                createdAt = o.str("created_at"),
                generator = GeneratorInfo(
                    generator.str("app_version"),
                    generator.strings("rule_versions"),
                    generator.strings("model_versions"),
                ),
                files = o.objects("files").map { ManifestFile(it.str("path"), it.str("role"), it.str("sha256"), it.long("size")) },
                merkleRoot = o.str("merkle_root"),
                auditChainHead = o.str("audit_chain_head"),
                omitted = OmittedCounts(omitted.int("evidence_count"), omitted.int("derivative_count"), omitted.int("event_count")),
                limits = o.strings("limits"),
            )
        }

        private fun fileJson(f: ManifestFile): JsonObject = jsonObject(
            "path" to JsonPrimitive(f.path),
            "role" to JsonPrimitive(f.role),
            "sha256" to JsonPrimitive(f.sha256),
            "size" to JsonPrimitive(f.size),
        )
    }
}
