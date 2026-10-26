package com.javierreansyah.pinecone.ui.features.library.components.book

import android.content.Context
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale
import coil3.size.SizeResolver
import com.javierreansyah.pinecone.data.local.preferences.CoverDisplayMode
import java.io.File

/** Share decode/cache/scale policy between visible covers and bounded prefetches. */
internal fun coverRequest(
    context: Context,
    path: String,
    size: SizeResolver,
    mode: CoverDisplayMode
): ImageRequest =
    ImageRequest.Builder(context).data(File(path)).size(size).precision(Precision.INEXACT)
        .scale(if (mode == CoverDisplayMode.Cover) Scale.FILL else Scale.FIT).memoryCacheKey(path)
        .build()
