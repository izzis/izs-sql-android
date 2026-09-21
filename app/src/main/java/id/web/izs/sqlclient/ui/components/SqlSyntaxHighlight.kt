package id.web.izs.sqlclient.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

private val SQL_KEYWORDS: Set<String> = setOf(
    "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES",
    "UPDATE", "SET", "DELETE", "CREATE", "ALTER", "DROP",
    "TABLE", "INDEX", "DATABASE", "JOIN", "LEFT", "RIGHT",
    "INNER", "OUTER", "ON", "AND", "OR", "NOT", "NULL",
    "IS", "IN", "LIKE", "BETWEEN", "ORDER", "BY", "GROUP",
    "HAVING", "LIMIT", "OFFSET", "AS", "DISTINCT", "COUNT",
    "SUM", "AVG", "MIN", "MAX", "IF", "EXISTS", "PRIMARY",
    "KEY", "FOREIGN", "REFERENCES", "CONSTRAINT", "UNIQUE",
    "DEFAULT", "ENGINE", "CHARSET", "DESC", "ASC", "UNION",
    "ALL", "EXPLAIN", "SHOW", "DESCRIBE", "TRUNCATE", "RENAME",
    "GRANT", "REVOKE", "COMMIT", "ROLLBACK", "BEGIN", "TRANSACTION",
    "CASE", "WHEN", "THEN", "ELSE", "END", "ANY", "SOME",
    "TOP", "FULL", "CROSS", "NATURAL", "USING"
)

private enum class SqlTokenType {
    KEYWORD, STRING, NUMBER, COMMENT, OPERATOR, IDENTIFIER, DEFAULT
}

private data class SqlToken(
    val type: SqlTokenType,
    val start: Int,
    val end: Int
)

private fun tokenizeSql(text: String): List<SqlToken> {
    val tokens = mutableListOf<SqlToken>()
    var i = 0
    val len = text.length

    while (i < len) {
        val c = text[i]

        // Comments: -- line comment
        if (c == '-' && i + 1 < len && text[i + 1] == '-') {
            val start = i
            i += 2
            while (i < len && text[i] != '\n') i++
            tokens.add(SqlToken(SqlTokenType.COMMENT, start, i))
            continue
        }

        // Comments: /* block comment */
        if (c == '/' && i + 1 < len && text[i + 1] == '*') {
            val start = i
            i += 2
            while (i < len - 1 && !(text[i] == '*' && text[i + 1] == '/')) i++
            i = (i + 2).coerceAtMost(len)
            tokens.add(SqlToken(SqlTokenType.COMMENT, start, i))
            continue
        }

        // Strings: single quote
        if (c == '\'') {
            val start = i
            i++
            while (i < len) {
                if (text[i] == '\'' && i + 1 < len && text[i + 1] == '\'') {
                    i += 2
                } else if (text[i] == '\'') {
                    i++
                    break
                } else {
                    i++
                }
            }
            tokens.add(SqlToken(SqlTokenType.STRING, start, i))
            continue
        }

        // Strings: double quote
        if (c == '"') {
            val start = i
            i++
            while (i < len && text[i] != '"') i++
            i = (i + 1).coerceAtMost(len)
            tokens.add(SqlToken(SqlTokenType.STRING, start, i))
            continue
        }

        // Identifiers: backtick
        if (c == '`') {
            val start = i
            i++
            while (i < len && text[i] != '`') i++
            i = (i + 1).coerceAtMost(len)
            tokens.add(SqlToken(SqlTokenType.IDENTIFIER, start, i))
            continue
        }

        // Numbers
        if (c.isDigit()) {
            val start = i
            while (i < len && (text[i].isDigit() || text[i] == '.')) i++
            tokens.add(SqlToken(SqlTokenType.NUMBER, start, i))
            continue
        }

        // Operators
        if (c == '=' || c == '>' || c == '<' || c == '+' || c == '-' || c == '*' ||
            c == '/' || c == '%'
        ) {
            val start = i
            i++
            if (i < len && (text[i] == '=' || text[i] == '>')) i++
            tokens.add(SqlToken(SqlTokenType.OPERATOR, start, i))
            continue
        }

        // Words (keywords or identifiers)
        if (c.isLetter() || c == '_') {
            val start = i
            while (i < len && (text[i].isLetterOrDigit() || text[i] == '_')) i++
            val word = text.substring(start, i)
            val type = if (word.uppercase() in SQL_KEYWORDS) SqlTokenType.KEYWORD else SqlTokenType.IDENTIFIER
            tokens.add(SqlToken(type, start, i))
            continue
        }

        // Everything else
        i++
    }

    return tokens
}

fun sqlHighlightTransformation(): VisualTransformation {
    val keywordColor = Color(0xFF6750A4)
    val stringColor = Color(0xFF2E7D32)
    val numberColor = Color(0xFFE65100)
    val commentColor = Color(0xFF9E9E9E)
    val operatorColor = Color(0xFF7C4DFF)

    return VisualTransformation { text ->
        val tokens = tokenizeSql(text.text)
        val annotatedString = buildAnnotatedString {
            append(text.text)
            for (token in tokens) {
                val style = when (token.type) {
                    SqlTokenType.KEYWORD -> SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold)
                    SqlTokenType.STRING -> SpanStyle(color = stringColor)
                    SqlTokenType.NUMBER -> SpanStyle(color = numberColor)
                    SqlTokenType.COMMENT -> SpanStyle(color = commentColor, fontStyle = FontStyle.Italic)
                    SqlTokenType.OPERATOR -> SpanStyle(color = operatorColor)
                    else -> SpanStyle()
                }
                if (token.type != SqlTokenType.IDENTIFIER && token.type != SqlTokenType.DEFAULT) {
                    addStyle(style, token.start, token.end)
                }
            }
        }
        TransformedText(annotatedString, OffsetMapping.Identity)
    }
}
