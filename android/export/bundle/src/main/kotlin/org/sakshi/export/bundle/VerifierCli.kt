package org.sakshi.export.bundle

import java.io.PrintStream
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.system.exitProcess

/** Command line front end: `verify <directory>`. */
public object VerifierCli {
    private const val USAGE: String = "usage: verify <bundle-directory>"

    /** Prints the report to [out]. Returns 0 for consistent, 1 for inconsistent, 2 for unreadable or bad usage. */
    public fun run(args: Array<String>, out: PrintStream): Int {
        if (args.size != 2 || args[0] != "verify") {
            out.println(USAGE)
            return 2
        }
        val directory = try {
            Path.of(args[1])
        } catch (e: InvalidPathException) {
            out.println(USAGE)
            return 2
        }
        val report = BundleVerifier.verify(directory)
        out.print(format(args[1], report))
        return when (report.verdict) {
            Verdict.CONSISTENT -> 0
            Verdict.INCONSISTENT -> 1
            Verdict.UNREADABLE -> 2
        }
    }

    @JvmStatic
    public fun main(args: Array<String>) {
        exitProcess(run(args, System.out))
    }

    private fun format(directory: String, report: VerificationReport): String = buildString {
        appendLine("Sakshi bundle verification")
        appendLine("Directory: ${safe(directory)}")
        appendLine("Result: ${report.verdict} (${describe(report.verdict)})")
        appendLine("Signing key id: ${report.signerKeyId ?: "not determined"}")
        appendLine()
        appendLine("Checks:")
        report.checks.forEach { appendLine("  [${it.status}] ${it.name}: ${it.detail}") }
        appendLine()
        appendLine("Left out of this bundle (counts only):")
        report.omitted.forEach { (name, count) -> appendLine("  $name: $count") }
        appendLine()
        if (report.unverifiable.isNotEmpty()) {
            appendLine("Cannot be checked from this bundle:")
            report.unverifiable.forEach { appendLine("  $it") }
            appendLine()
        }
        appendLine("Limits:")
        report.limits.forEach { appendLine("  $it") }
        appendLine()
        appendLine(BundleFormat.CLOSING_SENTENCE)
    }

    private fun describe(verdict: Verdict): String = when (verdict) {
        Verdict.CONSISTENT -> "no check failed"
        Verdict.INCONSISTENT -> "at least one check failed"
        Verdict.UNREADABLE -> "the manifest or signer file could not be read"
    }
}
