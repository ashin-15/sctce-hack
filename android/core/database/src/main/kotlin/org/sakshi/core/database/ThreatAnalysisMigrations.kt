package org.sakshi.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive only: old evidence and review history stays intact and has no fabricated analysis-run row. */
public object ThreatAnalysisMigrations {
    public val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `threat_analysis_run` (" +
                    "`id` TEXT NOT NULL, `case_id` TEXT NOT NULL, `event_id` TEXT NOT NULL, " +
                    "`event_revision` INTEGER NOT NULL, `derivative_id` TEXT, `request_id` TEXT NOT NULL, " +
                    "`status` TEXT NOT NULL, `reason_code` TEXT, `model_preset` TEXT, `weight_sha256` TEXT, " +
                    "`runtime_commit` TEXT, `runtime_version` TEXT, `task_version` TEXT NOT NULL, " +
                    "`created_at` TEXT NOT NULL, `finding_id` TEXT, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`case_id`) REFERENCES `case_file`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(`event_id`, `event_revision`) REFERENCES `event_revision`(`event_id`, `revision`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(`derivative_id`) REFERENCES `derivative`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(`finding_id`) REFERENCES `finding`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_threat_analysis_run_case_id` ON `threat_analysis_run` (`case_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_threat_analysis_run_event_id_event_revision` ON `threat_analysis_run` (`event_id`, `event_revision`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_threat_analysis_run_derivative_id` ON `threat_analysis_run` (`derivative_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_threat_analysis_run_finding_id` ON `threat_analysis_run` (`finding_id`)")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_threat_analysis_run_event_id_event_revision_request_id` " +
                    "ON `threat_analysis_run` (`event_id`, `event_revision`, `request_id`)",
            )
        }
    }
}
