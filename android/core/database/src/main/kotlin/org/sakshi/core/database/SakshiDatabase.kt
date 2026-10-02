package org.sakshi.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/** The Sakshi vault database. Open it with [SakshiDatabaseFactory]. */
@Database(
    entities = [
        CaseEntity::class, ActorEntity::class, SourceScopeEntity::class, EvidenceEntity::class,
        EvidenceStateEntity::class, EvidenceBlobEntity::class, CaptureMetadataEntity::class,
        DerivativeEntity::class, RegionEntity::class, EventEntity::class, EventRevisionEntity::class,
        EvidenceAnchorEntity::class, EventLinkEntity::class, BoundaryEntity::class,
        CoverageGapEntity::class, FindingEntity::class, FindingAnchorEntity::class,
        ReviewDecisionEntity::class, PatternEntity::class, PatternSupportEntity::class,
        ReportEntity::class, ReportSnapshotEntity::class, AuditRecordEntity::class,
        ModelVersionEntity::class, ProcessingJobEntity::class, LabelMappingEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
public abstract class SakshiDatabase : RoomDatabase() {
    public abstract fun caseDao(): CaseDao
    public abstract fun evidenceDao(): EvidenceDao
    public abstract fun derivativeDao(): DerivativeDao
    public abstract fun eventDao(): EventDao
    public abstract fun findingDao(): FindingDao
    public abstract fun patternDao(): PatternDao
    public abstract fun reportDao(): ReportDao
    public abstract fun auditDao(): AuditDao
    public abstract fun jobDao(): JobDao
    public abstract fun modelVersionDao(): ModelVersionDao
    public abstract fun labelMappingDao(): LabelMappingDao
}
