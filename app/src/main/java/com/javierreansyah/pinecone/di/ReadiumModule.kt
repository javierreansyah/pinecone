package com.javierreansyah.pinecone.di

import android.content.Context
import com.javierreansyah.pinecone.reader.pdf.MuPdfDocumentFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReadiumModule {
    @Provides
    @Singleton
    fun httpClient(): DefaultHttpClient = DefaultHttpClient()

    @Provides
    @Singleton
    fun assetRetriever(
        @ApplicationContext context: Context,
        httpClient: DefaultHttpClient
    ): AssetRetriever =
        AssetRetriever(contentResolver = context.contentResolver, httpClient = httpClient)

    @Provides
    @Singleton
    fun publicationOpener(
        @ApplicationContext context: Context,
        httpClient: DefaultHttpClient,
        assetRetriever: AssetRetriever,
    ): PublicationOpener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            context = context,
            httpClient = httpClient,
            assetRetriever = assetRetriever,
            pdfFactory = MuPdfDocumentFactory(),
        ),
    )

}
