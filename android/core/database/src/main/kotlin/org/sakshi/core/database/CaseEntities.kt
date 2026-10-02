package org.sakshi.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A case. The table is `case_file` because `case` is an SQL keyword. */
@Entity(tableName = SakshiSchema.CASE_FILE)
public data class CaseEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    /** One of [CaseStatus]. */
    val status: String,
    @ColumnInfo(name = "owner_note") val ownerNote: String? = null,
)

/** A case-scoped actor. Mutable; label edits are expected to be audited by the caller. */
@Entity(
    tableName = SakshiSchema.ACTOR,
    foreignKeys = [
        ForeignKey(CaseEntity::class, ["id"], ["case_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("case_id")],
)
public data class ActorEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "case_id") val caseId: String,
    @ColumnInfo(name = "display_label") val displayLabel: String,
    @ColumnInfo(name = "identity_basis") val identityBasis: String,
    @ColumnInfo(name = "association_review") val associationReview: String,
)

/** The app, profile and conversation an event came from. Insert-only. */
@Entity(
    tableName = SakshiSchema.SOURCE_SCOPE,
    foreignKeys = [
        ForeignKey(CaseEntity::class, ["id"], ["case_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("case_id")],
)
public data class SourceScopeEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "case_id") val caseId: String,
    @ColumnInfo(name = "source_app_claim") val sourceAppClaim: String?,
    @ColumnInfo(name = "profile_scope") val profileScope: String?,
    @ColumnInfo(name = "conversation_scope") val conversationScope: String?,
)
