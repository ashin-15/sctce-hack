package org.sakshi.export.bundle

import java.io.InputStream
import org.sakshi.core.model.Event

/** A file to include. [open] is called each time the bytes are needed and must return a fresh stream. */
public class BundleFile(public val opaqueId: String, public val length: Long, public val open: () -> InputStream)

/** Everything one export snapshot contains. The writer refuses content that breaks the bundle invariants. */
public class BundleContent(
    public val snapshotId: String,
    public val caseId: String,
    /** RFC 3339 timestamp read from the device clock. */
    public val createdAt: String,
    public val generator: GeneratorInfo,
    /** Hex SHA-256 head of the audit chain at snapshot time. */
    public val auditChainHead: String,
    public val omitted: OmittedCounts,
    public val events: List<Event>,
    public val findings: List<Finding>,
    public val corrections: List<Correction>,
    public val patterns: List<Pattern>,
    public val provenance: Provenance,
    public val originals: List<BundleFile>,
    public val derivatives: List<BundleFile>,
    public val reportPdf: BundleFile?,
)

/** What the writer produced. */
public data class BundleSummary(
    val snapshotId: String,
    val signerKeyId: String,
    val merkleRoot: String,
    val fileCount: Int,
)
