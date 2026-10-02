package org.sakshi.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A parsed, OCR, transcript or edited text revision derived from evidence. Insert-only. */
@Entity(
    tableName = SakshiSchema.DERIVATIVE,
    foreignKeys = [
        ForeignKey(EvidenceEntity::class, ["id"], ["evidence_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(
            DerivativeEntity::class, ["id"], ["parent_derivative_id"], onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(ModelVersionEntity::class, ["id"], ["model_version_id"]),
    ],
    indices = [
        Index("evidence_id", "kind", "revision"),
        Index("parent_derivative_id"),
        Index("model_version_id"),
    ],
)
public data class DerivativeEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "evidence_id") val evidenceId: String,
    @ColumnInfo(name = "parent_derivative_id") val parentDerivativeId: String?,
    val revision: Int,
    /** One of [DerivativeKind]. */
    val kind: String,
    val text: String,
    @ColumnInfo(name = "source_map_json") val sourceMapJson: String?,
    @ColumnInfo(name = "quality_json") val qualityJson: String?,
    @ColumnInfo(name = "tool_id") val toolId: String,
    @ColumnInfo(name = "tool_version") val toolVersion: String,
    @ColumnInfo(name = "model_version_id") val modelVersionId: String?,
    @ColumnInfo(name = "created_at") val createdAt: String,
)

/** An image or page region of a derivative. Insert-only. */
@Entity(
    tableName = SakshiSchema.REGION,
    foreignKeys = [
        ForeignKey(DerivativeEntity::class, ["id"], ["derivative_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("derivative_id")],
)
public data class RegionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "derivative_id") val derivativeId: String,
    @ColumnInfo(name = "page_index") val pageIndex: Int,
    @ColumnInfo(name = "polygon_json") val polygonJson: String,
    @ColumnInfo(name = "transform_json") val transformJson: String?,
)
