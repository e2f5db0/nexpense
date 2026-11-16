// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.size
import app.nexpense.R

@Composable
fun NostrichLoading(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val context = LocalContext.current
    val drawableRes = if (LocalArcadeFont.current) R.drawable.running_nostrich_loading_pixelated
    else R.drawable.running_nostrich_loading_unpixelated
    AndroidView(modifier = modifier.size(size), factory = { viewContext ->
        ImageView(viewContext).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Loading"
            setImageDrawable(gifDrawable(context, drawableRes))
            tag = drawableRes
        }
    }, update = { imageView ->
        if ((imageView.tag as? Int) != drawableRes) {
            imageView.setImageDrawable(gifDrawable(context, drawableRes))
            imageView.tag = drawableRes
        }
    })
}

private fun gifDrawable(context: Context, @DrawableRes drawableRes: Int): Drawable =
    if (Build.VERSION.SDK_INT >= 28) animatedGif(context, drawableRes)
    else BitmapDrawable(context.resources, BitmapFactory.decodeResource(context.resources, drawableRes))

@RequiresApi(28)
private fun animatedGif(context: Context, @DrawableRes drawableRes: Int): Drawable {
    val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.resources, drawableRes))
    if (drawable is AnimatedImageDrawable) {
        drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        drawable.start()
    }
    return drawable
}
