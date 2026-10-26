package com.javierreansyah.pinecone.data.repository.library

/** Platform scheduling stays in the application composition layer. Only IDs cross this boundary. */
interface OperationScheduler {
    suspend fun enqueue(operationId: String)
    suspend fun cancel(operationId: String)
}
