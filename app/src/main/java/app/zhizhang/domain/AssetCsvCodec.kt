package app.zhizhang.domain

import app.zhizhang.data.AssetCategories
import app.zhizhang.data.AssetEntity
import app.zhizhang.data.Formats
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object AssetCsvCodec {
    fun encode(assets: List<AssetEntity>, zone: ZoneId = ZoneId.systemDefault()): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val header = "id,name,category,currentValue,acquiredAt,note,source"
        return buildString {
            appendLine(header)
            assets.sortedBy { it.acquiredAt }.forEach { asset ->
                val acquired = Instant.ofEpochMilli(asset.acquiredAt)
                    .atZone(zone)
                    .format(formatter)
                appendLine(
                    listOf(
                        asset.id.toString(),
                        csv(asset.name),
                        csv(AssetCategories.byId(asset.categoryId).name),
                        Formats.centsToInput(asset.valueCents),
                        csv(acquired),
                        csv(asset.note),
                        asset.source,
                    ).joinToString(","),
                )
            }
        }
    }

    private fun csv(value: String): String {
        if (value.none { it == ',' || it == '"' || it == '\n' || it == '\r' }) return value
        return '"' + value.replace("\"", "\"\"") + '"'
    }
}
