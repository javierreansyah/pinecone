package com.javierreansyah.pinecone.data.publication.conversion

import android.content.res.Resources
import com.javierreansyah.pinecone.publication.R

fun generatedPublicationStrings(resources: Resources): GeneratedPublicationStrings =
    GeneratedPublicationStrings(
        contents = resources.getString(R.string.generated_contents),
        scannedPageNotice = resources.getString(R.string.generated_scanned_page_notice),
        pageLabel = { number -> resources.getString(R.string.generated_page_label, number) },
    )
