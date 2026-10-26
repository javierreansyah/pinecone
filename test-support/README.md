# Instrumentation fixtures

These test-only sources are compiled into the app, data, and publication instrumentation APKs.
Keeping WebView and logging fixtures here lets each module test its own internals without adding
production visibility or a dependency on application code. This directory is never part of a
production source set.
