package com.javierreansyah.pinecone.ui.features.reader.components.contents

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun annotationTimestamp(context: Context, createdAt: Long): String {
    val date = Date(createdAt)
    val locale = Locale.getDefault()
    val dateStr = if (locale.language == "en") SimpleDateFormat("MM/dd/yyyy", locale).format(date)
    else android.text.format.DateFormat.getDateFormat(context).format(date)
    val timeStr = android.text.format.DateFormat.getTimeFormat(context).format(date)
    return "$dateStr $timeStr"
}
