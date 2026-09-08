package io.github.immaghzbad.aetherst.shared.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Picture
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caverock.androidsvg.SVG
import java.io.ByteArrayOutputStream

@Composable
actual fun CountryFlag(
    countryCode: String,
    size: Dp,
    modifier: Modifier
) {
    val context = LocalContext.current
    val state = produceState<Painter?>(initialValue = null, countryCode) {
        value = loadSvgFlagPainter(context, countryCode)
    }

    val painter = state.value
    if (painter != null) {
        Image(
            painter = painter,
            contentDescription = countryCode.uppercase(),
            modifier = modifier
                .size(size)
                .graphicsLayer { clip = true }
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(size * 0.2f))
        )
    } else {
        val emoji = if (countryCode.length == 2) {
            val first = countryCode[0].uppercaseChar().code - 'A'.code + 0x1F1E6
            val second = countryCode[1].uppercaseChar().code - 'A'.code + 0x1F1E6
            codePointToString(first) + codePointToString(second)
        } else {
            "\uD83C\uDF10"
        }
        Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
            Text(
                text = emoji,
                fontSize = (size.value * 0.75f).sp
            )
        }
    }
}

private fun loadSvgFlagPainter(
    context: android.content.Context,
    countryCode: String
): Painter? {
    if (countryCode.length != 2) return null
    return try {
        val svgBytes = readSvgBytesFromAssets(context, countryCode)
        if (svgBytes != null) {
            val svg = SVG.getFromInputStream(svgBytes.inputStream())
            val picture: Picture = svg.renderToPicture()
            val w = picture.width.coerceAtLeast(1)
            val h = picture.height.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawPicture(picture)
            BitmapPainter(bitmap.asImageBitmap())
        } else null
    } catch (_: Exception) {
        null
    }
}

private fun readSvgBytesFromAssets(
    context: android.content.Context,
    countryCode: String
): ByteArray? {
    return try {
        val assetPath = "composeResources/aetherst_tunnel.composeapp.generated.resources/drawable/${countryCode.lowercase()}.svg"
        context.assets.open(assetPath).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    } catch (_: Exception) {
        null
    }
}

private fun codePointToString(codePoint: Int): String {
    return if (codePoint <= 0xFFFF) {
        codePoint.toChar().toString()
    } else {
        val high = ((codePoint - 0x10000) shr 10) + 0xD800
        val low = ((codePoint - 0x10000) and 0x3FF) + 0xDC00
        high.toChar().toString() + low.toChar().toString()
    }
}
