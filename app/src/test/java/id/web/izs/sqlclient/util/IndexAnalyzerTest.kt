package id.web.izs.sqlclient.util

import id.web.izs.sqlclient.data.remote.model.IndexInfo
import org.junit.Assert.*
import org.junit.Test

class IndexAnalyzerTest {

    private fun idx(name: String, vararg cols: String, unique: Boolean = false, cardinality: Long? = null) =
        IndexInfo(name = name, columns = cols.toList(), isUnique = unique, type = "BTREE", cardinality = cardinality)

    // Healthy table -> no issues
    @Test fun healthy_noIssues() {
        val indexes = listOf(
            idx("PRIMARY", "id", unique = true),
            idx("idx_email", "email", unique = true, cardinality = 1000),
            idx("idx_name", "name", cardinality = 500)
        )
        assertTrue(IndexAnalyzer.analyze("db", "t", indexes).isEmpty())
    }

    // Exact duplicate column list
    @Test fun duplicate_sameColumns() {
        val indexes = listOf(
            idx("PRIMARY", "id", unique = true),
            idx("idx_email", "email", unique = true, cardinality = 100),
            idx("email_2", "email", unique = true, cardinality = 100)
        )
        val issues = IndexAnalyzer.analyze("db", "t", indexes)
        assertEquals(1, issues.size)
        val issue = issues[0]
        assertEquals(IndexAnalyzer.Severity.ERROR, issue.severity)
        assertEquals("email_2", issue.indexName)
        assertEquals("DROP INDEX `email_2` ON `db`.`t`", issue.suggestedSql)
    }

    // PRIMARY must never be suggested for drop, even if listed second
    @Test fun duplicate_primaryAlwaysKept() {
        val indexes = listOf(
            idx("idx_id", "id", cardinality = 10),
            idx("PRIMARY", "id", unique = true)
        )
        val issues = IndexAnalyzer.analyze("db", "t", indexes)
        assertEquals(1, issues.size)
        assertEquals("idx_id", issues[0].indexName)
        assertTrue(issues.none { it.indexName == "PRIMARY" })
    }

    // Non-unique leftmost prefix is redundant
    @Test fun redundant_prefixIndex() {
        val indexes = listOf(
            idx("PRIMARY", "id", unique = true),
            idx("idx_a", "a", cardinality = 50),
            idx("idx_a_b", "a", "b", cardinality = 200)
        )
        val issues = IndexAnalyzer.analyze("db", "t", indexes)
        assertEquals(1, issues.size)
        assertEquals(IndexAnalyzer.Severity.WARNING, issues[0].severity)
        assertEquals("idx_a", issues[0].indexName)
        assertEquals("DROP INDEX `idx_a` ON `db`.`t`", issues[0].suggestedSql)
    }

    // UNIQUE prefix is NOT redundant (enforces stricter constraint)
    @Test fun redundant_uniquePrefixKept() {
        val indexes = listOf(
            idx("PRIMARY", "id", unique = true),
            idx("uq_a", "a", unique = true, cardinality = 50),
            idx("idx_a_b", "a", "b", cardinality = 200)
        )
        assertTrue(IndexAnalyzer.analyze("db", "t", indexes).isEmpty())
    }

    // Non-prefix overlap is not redundancy
    @Test fun redundant_nonPrefixNotFlagged() {
        val indexes = listOf(
            idx("PRIMARY", "id", unique = true),
            idx("idx_b_a", "b", "a", cardinality = 200),
            idx("idx_a_b", "a", "b", cardinality = 200)
        )
        assertTrue(IndexAnalyzer.analyze("db", "t", indexes).isEmpty())
    }

    // Duplicate reported once — not also as redundant
    @Test fun duplicate_notDoubleReported() {
        val indexes = listOf(
            idx("PRIMARY", "id", unique = true),
            idx("idx_a", "a", cardinality = 50),
            idx("idx_a_copy", "a", cardinality = 50),
            idx("idx_a_b", "a", "b", cardinality = 200)
        )
        val issues = IndexAnalyzer.analyze("db", "t", indexes)
        // idx_a_copy = duplicate(ERROR); idx_a = redundant(WARNING) under idx_a_b. No double count.
        assertEquals(2, issues.size)
        assertEquals(1, issues.count { it.indexName == "idx_a_copy" })
        assertEquals(1, issues.count { it.indexName == "idx_a" })
    }

    // Tiny single-column cardinality -> info
    @Test fun lowSelectivity_flagged() {
        val indexes = listOf(
            idx("PRIMARY", "id", unique = true),
            idx("idx_flag", "is_active", cardinality = 2)
        )
        val issues = IndexAnalyzer.analyze("db", "t", indexes)
        assertEquals(1, issues.size)
        assertEquals(IndexAnalyzer.Severity.INFO, issues[0].severity)
        assertEquals("idx_flag", issues[0].indexName)
    }

    // Unknown cardinality -> no low-selectivity noise
    @Test fun lowSelectivity_unknownCardinalitySilent() {
        val indexes = listOf(
            idx("PRIMARY", "id", unique = true),
            idx("idx_flag", "is_active")
        )
        assertTrue(IndexAnalyzer.analyze("db", "t", indexes).isEmpty())
    }

    // Multi-column low cardinality -> not flagged (rule is single-column only)
    @Test fun lowSelectivity_multiColumnSilent() {
        val indexes = listOf(
            idx("PRIMARY", "id", unique = true),
            idx("idx_ab", "a", "b", cardinality = 2)
        )
        assertTrue(IndexAnalyzer.analyze("db", "t", indexes).isEmpty())
    }

    // Full cardinality numbers (no compacting, no words)
    @Test fun formatCardinality_full() {
        assertEquals("0", IndexAnalyzer.formatCardinality(0))
        assertEquals("999", IndexAnalyzer.formatCardinality(999))
        assertEquals("1500", IndexAnalyzer.formatCardinality(1500))
        assertEquals("2000000", IndexAnalyzer.formatCardinality(2_000_000))
    }
}
