package io.github.usmandurrany.tether.demo.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The few icons the demo needs, as outline paths on a 24 dp grid. Drawn here
 * instead of pulling in the large material-icons-extended library.
 */
object TetherIcons {
    val Back = icon("back", "M19 12H5M12 19l-7-7 7-7")
    val Settings = icon(
        "settings",
        "M4 6h9M17 6h3M4 12h3M11 12h9M4 18h11M19 18h1",
        circle(15f, 6f, 2f),
        circle(9f, 12f, 2f),
        circle(17f, 18f, 2f),
    )
    val Chats = icon("chats", "M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z")
    val Contacts = icon(
        "contacts",
        circle(9f, 8f, 3.5f),
        "M2.5 20c.6-3.6 3.3-5.5 6.5-5.5s5.9 1.9 6.5 5.5",
        "M16 4.6a3.5 3.5 0 0 1 0 6.8M18 14.8c2 .7 3.2 2.5 3.5 5.2",
    )
    val Person = icon("person", circle(12f, 8f, 4f), "M4 21c.8-4 4-6 8-6s7.2 2 8 6")
    val Log = icon("log", rect(3f, 4f, 18f, 16f, 2f), "M7 9l3 3-3 3M13 15h4")
    val Attach = icon("attach", "M21 11.5l-8.6 8.6a5 5 0 0 1-7.1-7.1l8.6-8.6a3.3 3.3 0 0 1 4.7 4.7l-8.6 8.6a1.7 1.7 0 0 1-2.4-2.4l7.9-7.9")
    val Send = icon("send", "M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z")
    val ArrowForward = icon("arrow-forward", "M5 12h14M13 5l7 7-7 7")
    val ExpandMore = icon("expand-more", "M6 9l6 6 6-6")
    val ExpandLess = icon("expand-less", "M6 15l6-6 6 6")
    val Check = icon("check", "M5 12.5l4.5 4.5L19 7.5")
    val DoubleCheck = icon("double-check", "M2 12.5l4.5 4.5L16 7.5M10.5 16l1 1L21 7.5")
    val Clock = icon("clock", circle(12f, 12f, 9f), "M12 7v5l3 2")
    val Copy = icon("copy", rect(9f, 9f, 12f, 12f, 2f), "M5 15H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v1")
    val Delete = icon("delete", "M3 6h18M8 6V4h8v2M6 6l1 14h10l1-14")
    val Photo = icon("photo", rect(3f, 3f, 18f, 18f, 2f), circle(9f, 9f, 2f), "M21 15l-5-5L5 21")
    val Video = icon("video", rect(2f, 6f, 14f, 12f, 2f), "M16 10l6-4v12l-6-4z")
    val Location = icon("location", "M12 22s7-6.2 7-12a7 7 0 0 0-14 0c0 5.8 7 12 7 12z", circle(12f, 10f, 2.5f))
    val Code = icon(
        "code",
        "M8 3H7a2 2 0 0 0-2 2v4a2 2 0 0 1-2 2 2 2 0 0 1 2 2v4a2 2 0 0 0 2 2h1" +
            "M16 3h1a2 2 0 0 1 2 2v4a2 2 0 0 0 2 2 2 2 0 0 0-2 2v4a2 2 0 0 1-2 2h-1",
    )
    val Server = icon("server", rect(3f, 3f, 18f, 7f, 2f), rect(3f, 14f, 18f, 7f, 2f), "M7 6.5h.01M7 17.5h.01")
    val Key = icon("key", circle(8f, 15f, 4f), "M11 12l9-9M17 6l3 3M14 9l2 2")
    val Moon = icon("moon", "M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z")
    val Bug = icon(
        "bug",
        rect(7f, 7f, 10f, 13f, 5f),
        "M12 11v9M3 13h4M17 13h4M4 7l3 2M20 7l-3 2M9 4l1.5 2M15 4l-1.5 2",
    )
    val PersonAdd = icon("person-add", circle(9f, 8f, 4f), "M2 21c.7-4 3.6-6 7-6s6.3 2 7 6M19 8v6M16 11h6")
    val Shield = icon("shield", "M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z", "M12 8v5M12 16.5h.01")
    val Camera = icon("camera", "M3 8a2 2 0 0 1 2-2h2l2-3h6l2 3h2a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", circle(12f, 13f, 3.5f))
    val Eye = icon("eye", "M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z", circle(12f, 12f, 3f))
    val EyeOff = icon("eye-off", "M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z", circle(12f, 12f, 3f), "M3 3l18 18")
    val Bell = icon("bell", "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10 21h4")
    val Wrench = icon("wrench", "M14.7 6.3a4 4 0 0 0-5.4 5.4L3 18l3 3 6.3-6.3a4 4 0 0 0 5.4-5.4l-2.5 2.5-2.5-.5-.5-2.5z")
    val Logout = icon("logout", "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9")
    val Presence = icon("presence", circle(12f, 12f, 9f), circle(12f, 12f, 3f))
    val Blocked = icon("blocked", circle(12f, 12f, 9f), "M5.6 5.6l12.8 12.8")
    val Follow = icon("follow", "M12 4v12M6 10l6 6 6-6M5 20h14")
    val Upload = icon("upload", "M12 16V4M6 10l6-6 6 6M5 20h14")
    val Add = icon("add", "M12 5v14M5 12h14")
    val Dash = icon("dash", "M7 12h10")
    val Play = filled("play", "M8 5.5v13l11-6.5z")
    val AppMark = icon("app-mark", "M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z", "M9 10.5h6M9 14h4")

    private fun icon(name: String, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        paths.forEach { path ->
            builder.addPath(
                pathData = addPathNodes(path),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }

    private fun filled(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
            .build()

    private fun circle(cx: Float, cy: Float, r: Float): String =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float): String =
        "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}h${-(w - 2 * r)}" +
            "a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"
}
