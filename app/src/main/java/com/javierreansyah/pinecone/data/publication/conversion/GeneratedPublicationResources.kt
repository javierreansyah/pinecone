package com.javierreansyah.pinecone.data.publication.conversion

import android.content.res.Resources
import com.javierreansyah.pinecone.R

fun generatedPublicationStrings(resources: Resources): GeneratedPublicationStrings =
    GeneratedPublicationStrings(
        contents = resources.getString(R.string.reader_toc_title),
        scannedPageNotice = resources.getString(R.string.reader_scanned_page_notice),
        originalPageNotice = resources.getString(R.string.reader_original_page_notice),
        pageLabel = { number -> resources.getString(R.string.reader_page_label, number) },
    )
