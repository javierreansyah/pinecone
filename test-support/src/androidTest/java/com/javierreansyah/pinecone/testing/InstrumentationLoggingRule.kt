package com.javierreansyah.pinecone.testing

import org.junit.rules.ExternalResource
import timber.log.Timber

/** Keep test diagnostics in Logcat without installing a logging tree in production. */
class InstrumentationLoggingRule : ExternalResource() {
    private val tree = Timber.DebugTree()

    override fun before() {
        Timber.plant(tree)
    }

    override fun after() {
        Timber.uproot(tree)
    }
}
