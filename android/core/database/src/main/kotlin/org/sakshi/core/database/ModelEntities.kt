package org.sakshi.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A model artefact that produced derivatives or findings. Insert-only, never deleted. */
@Entity(tableName = SakshiSchema.MODEL_VERSION)
public data class ModelVersionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val role: String,
    @ColumnInfo(name = "artefact_sha256") val artefactSha256: String,
    @ColumnInfo(name = "tokenizer_sha256") val tokenizerSha256: String?,
    val runtime: String,
    @ColumnInfo(name = "runtime_version") val runtimeVersion: String,
    val quantisation: String?,
    val licence: String,
    @ColumnInfo(name = "calibration_id") val calibrationId: String?,
    @ColumnInfo(name = "supported_languages_json") val supportedLanguagesJson: String,
)

/** Maps a producer's own label to the event schema label. Insert-only, never deleted. */
@Entity(
    tableName = SakshiSchema.LABEL_MAPPING,
    primaryKeys = ["producer", "producer_label", "mapping_version"],
)
public data class LabelMappingEntity(
    val producer: String,
    @ColumnInfo(name = "producer_label") val producerLabel: String,
    @ColumnInfo(name = "schema_label") val schemaLabel: String,
    @ColumnInfo(name = "mapping_version") val mappingVersion: Int,
)
