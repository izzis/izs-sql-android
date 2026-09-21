package id.web.izs.sqlclient.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import id.web.izs.sqlclient.data.remote.ColumnMetadata
import java.io.File
import java.io.FileWriter

object ExportUtil {

    fun exportToCsv(
        context: Context,
        columns: List<ColumnMetadata>,
        rows: List<List<Any?>>,
        fileName: String
    ): Uri? {
        return try {
            val file = File(context.cacheDir, "$fileName.csv")
            FileWriter(file).use { writer ->
                writer.appendLine(columns.joinToString(",") { it.name })

                rows.forEach { row ->
                    writer.appendLine(row.joinToString(",") { cell ->
                        val value = cell?.toString() ?: ""
                        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
                            "\"${value.replace("\"", "\"\"")}\""
                        } else {
                            value
                        }
                    })
                }
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            null
        }
    }

    fun exportToJson(
        context: Context,
        columns: List<ColumnMetadata>,
        rows: List<List<Any?>>,
        fileName: String
    ): Uri? {
        return try {
            val file = File(context.cacheDir, "$fileName.json")
            FileWriter(file).use { writer ->
                writer.append("[\n")

                rows.forEachIndexed { rowIndex, row ->
                    writer.append("  {\n")
                    columns.forEachIndexed { colIndex, col ->
                        val value = row[colIndex]
                        val jsonValue = when (value) {
                            null -> "null"
                            is Number -> value.toString()
                            is Boolean -> value.toString()
                            else -> "\"${value.toString().replace("\\", "\\\\").replace("\"", "\\\"")}\""
                        }
                        writer.append("    \"${col.name}\": $jsonValue")
                        if (colIndex < columns.lastIndex) writer.append(",")
                        writer.append("\n")
                    }
                    writer.append("  }")
                    if (rowIndex < rows.lastIndex) writer.append(",")
                    writer.append("\n")
                }

                writer.append("]\n")
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            null
        }
    }

    fun exportToSqlInsert(
        context: Context,
        tableName: String,
        columns: List<ColumnMetadata>,
        rows: List<List<Any?>>,
        fileName: String
    ): Uri? {
        return try {
            val file = File(context.cacheDir, "$fileName.sql")
            FileWriter(file).use { writer ->
                val colNames = columns.joinToString(",") { "`${it.name}`" }

                rows.forEach { row ->
                    val values = row.joinToString(",") { cell ->
                        when (cell) {
                            null -> "NULL"
                            is Number -> cell.toString()
                            is Boolean -> if (cell) "1" else "0"
                            else -> "'${cell.toString().replace("'", "''")}'"
                        }
                    }
                    writer.appendLine("INSERT INTO `$tableName` ($colNames) VALUES ($values);")
                }
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            null
        }
    }

    fun shareFile(context: Context, uri: Uri, mimeType: String = "text/plain") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share via"))
    }
}
