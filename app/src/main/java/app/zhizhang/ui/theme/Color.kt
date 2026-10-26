package app.zhizhang.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// 有数 · 米白、雾蓝、宝石蓝与暖金。
// 大面积保持温暖中性，交互由宝石蓝承担，玻璃负责层次。
val Cobalt = Color(0xFF2451D8)
val CobaltDeep = Color(0xFF15339B)
val IceBlue = Color(0xFFE5EBFF)
val Jade = Color(0xFF131A2A) // 兼容旧命名：深海军蓝文字
val JadeDeep = Color(0xFF0B101E)
val JadeMist = Color(0xFFE9EDF7)
val Coral = Color(0xFFC05243)
val Amber = Color(0xFFB58B4C)
val Sky = Color(0xFF6A89B8)
val Lilac = Color(0xFF8C8398)

val CanvasTop = Color(0xFFF8F3E9)
val CanvasBottom = Color(0xFFE8EEF8)
val Ink = Color(0xFF131A2A)
val Muted = Color(0xFF687284)

val NightCanvasTop = Color(0xFF080B12)
val NightCanvasBottom = Color(0xFF101725)
val NightInk = Color(0xFFF2F5FA)
val NightMuted = Color(0xFF9BA5B6)

val HeroTop = Color(0xFF131A2A)
val HeroBottom = Color(0xFF1B2942)
val HeroText = Color(0xFFFBFAF5)
val HeroMuted = Color(0xFFC9D1E0)

@Immutable
data class ZhExtraColors(
    val expense: Color,
    val income: Color,
    val gold: Color,
    val hairline: Color,
    val chip: Color,
    val heroTop: Color,
    val heroBottom: Color,
    val heroText: Color,
    val heroMuted: Color,
    val autoBadge: Color,
    val autoBadgeText: Color,
    val canvasBottom: Color,
    val glassTop: Color,
    val glassBottom: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val glassShadow: Color,
    val auraMint: Color,
    val auraPeach: Color,
    val auraLilac: Color,
    val auraBlue: Color,
)

val LightZhColors = ZhExtraColors(
    expense = Coral,
    income = Color(0xFF0D7A64),
    gold = Amber,
    hairline = Color(0x2E1B2438),
    chip = Color(0x66FFFFFF),
    heroTop = HeroTop,
    heroBottom = HeroBottom,
    heroText = HeroText,
    heroMuted = HeroMuted,
    autoBadge = Color(0xBFE2E9FF),
    autoBadgeText = Color(0xFF2244B8),
    canvasBottom = CanvasBottom,
    glassTop = Color(0xB8FFFFFF),
    glassBottom = Color(0x4DFFFFFF),
    glassBorder = Color(0xE6FFFFFF),
    glassHighlight = Color(0xB3FFFFFF),
    glassShadow = Color(0x24122132),
    auraMint = Color(0xFFBFD7F5),
    auraPeach = Color(0xFFF0D4A8),
    auraLilac = Color(0xFFD6D3DC),
    auraBlue = Color(0xFF4667D8),
)

val DarkZhColors = ZhExtraColors(
    expense = Color(0xFFFFA595),
    income = Color(0xFF76D8BD),
    gold = Color(0xFFE0BE7C),
    hairline = Color(0x30FFFFFF),
    chip = Color(0x1FFFFFFF),
    heroTop = Color(0xFF16203A),
    heroBottom = Color(0xFF090D17),
    heroText = Color(0xFFF9FBFF),
    heroMuted = Color(0xFFBBC5D5),
    autoBadge = Color(0xBF1A2A52),
    autoBadgeText = Color(0xFFBCCBFF),
    canvasBottom = NightCanvasBottom,
    glassTop = Color(0xA61A2438),
    glassBottom = Color(0x4D0C1220),
    glassBorder = Color(0x2EFFFFFF),
    glassHighlight = Color(0x1AFFFFFF),
    glassShadow = Color(0x80000000),
    auraMint = Color(0xFF3A5C87),
    auraPeach = Color(0xFF7A5A36),
    auraLilac = Color(0xFF545462),
    auraBlue = Color(0xFF2949A8),
)
