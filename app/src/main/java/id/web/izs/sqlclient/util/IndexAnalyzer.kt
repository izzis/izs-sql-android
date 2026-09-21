package id.web.izs.sqlclient.util

import id.web.izs.sqlclient.data.remote.model.IndexInfo

/**
 * Pure client-side index health analysis over [IndexInfo] lists.
 *
 * No Android dependencies — fully JVM-unit-testable. Rules are heuristics over
 * SHOW INDEX output, not a substitute for EXPLAIN / performance_schema:
 *
 * - DUPLICATE: two indexes covering the exact same column list. The second one only
 *   costs write overhead + space. PRIMARY is never suggested for drop.
 * - REDUNDANT: a non-unique index whose columns are a strict leftmost prefix of another
 *   index (e.g. KEY(a) under KEY(a,b)) — the wider index serves the same lookups.
 *   Unique indexes are excluded: UNIQUE(a) still enforces a stricter constraint than
 *   any wider index, so it is NOT redundant.
 * - LOW_SELECTIVITY: single-column non-unique index with tiny estimated cardinality —
 *   such indexes rarely help the optimizer. Informational only.
 */
object IndexAnalyzer {

    enum class Severity { ERROR, WARNING, INFO }

    data class IndexIssue(
        val severity: Severity,
        val title: String,
        val detail: String,
        val indexName: String,
        val suggestedSql: String
    )

    /** Full cardinality number for card layouts (no words, just digits): 1500 -> "1500". */
    fun formatCardinality(value: Long): String = value.toString()

    fun analyze(database: String, table: String, indexes: List<IndexInfo>): List<IndexIssue> {
        val issues = mutableListOf<IndexIssue>()
        val duplicates = findDuplicates(database, table, indexes)
        issues += duplicates
        // Don't double-report an index already flagged as duplicate.
        val reported = duplicates.map { it.indexName }.toSet()
        issues += findRedundant(database, table, indexes, reported)
        issues += findLowSelectivity(database, table, indexes)
        return issues
    }

    private fun dropSql(database: String, table: String, index: String) =
        "DROP INDEX `$index` ON `$database`.`$table`"

    private fun findDuplicates(database: String, table: String, indexes: List<IndexInfo>): List<IndexIssue> {
        // Group by normalized column list; first occurrence is kept, rest are duplicates.
        return indexes.groupBy { it.columns.map { c -> c.lowercase() } }
            .filter { (_, group) -> group.size > 1 }
            .flatMap { (_, group) ->
                // PRIMARY is always the keeper — it must never be suggested for drop.
                val keeper = group.firstOrNull { it.name.equals("PRIMARY", true) } ?: group.first()
                group.filter { it.name != keeper.name }.map { dup ->
                    val primaryNote = if (keeper.name.equals("PRIMARY", true))
                        " PRIMARY key is kept — only this non-PRIMARY copy is suggested for drop."
                    else ""
                    IndexIssue(
                        severity = Severity.ERROR,
                        title = "Duplicate index `${dup.name}`",
                        detail = "Covers the same column(s) (${dup.columns.joinToString(", ")}) as `${keeper.name}`." +
                            " It only adds write overhead and disk usage.$primaryNote",
                        indexName = dup.name,
                        suggestedSql = dropSql(database, table, dup.name)
                    )
                }
            }
    }

    private fun findRedundant(
        database: String,
        table: String,
        indexes: List<IndexInfo>,
        skip: Set<String> = emptySet()
    ): List<IndexIssue> {
        val issues = mutableListOf<IndexIssue>()
        val alreadyFlagged = mutableSetOf<String>()
        for (narrow in indexes) {
            // UNIQUE indexes enforce a stricter constraint — never redundant.
            // PRIMARY is the clustered key — never redundant.
            if (narrow.isUnique || narrow.name.equals("PRIMARY", true)) continue
            if (narrow.name in alreadyFlagged || narrow.name in skip) continue
            val wider = indexes.firstOrNull { other ->
                other.name != narrow.name &&
                    other.columns.size > narrow.columns.size &&
                    other.columns.take(narrow.columns.size).map { it.lowercase() } ==
                        narrow.columns.map { it.lowercase() }
            } ?: continue
            alreadyFlagged.add(narrow.name)
            issues.add(
                IndexIssue(
                    severity = Severity.WARNING,
                    title = "Redundant index `${narrow.name}`",
                    detail = "Leftmost prefix of `${wider.name}` (${wider.columns.joinToString(", ")})." +
                        " The wider index serves the same lookups; this one only costs writes.",
                    indexName = narrow.name,
                    suggestedSql = dropSql(database, table, narrow.name)
                )
            )
        }
        return issues
    }

    private fun findLowSelectivity(database: String, table: String, indexes: List<IndexInfo>): List<IndexIssue> {
        return indexes
            .filter { idx ->
                !idx.isUnique &&
                    !idx.name.equals("PRIMARY", true) &&
                    idx.columns.size == 1 &&
                    idx.cardinality != null && idx.cardinality <= 2
            }
            .map { idx ->
                IndexIssue(
                    severity = Severity.INFO,
                    title = "Low selectivity `${idx.name}`",
                    detail = "Single-column index on `${idx.columns.first()}` with ~${idx.cardinality} distinct value(s)." +
                        " The optimizer will likely ignore it — consider dropping unless queries filter heavily on this column.",
                    indexName = idx.name,
                    suggestedSql = dropSql(database, table, idx.name)
                )
            }
    }
}
