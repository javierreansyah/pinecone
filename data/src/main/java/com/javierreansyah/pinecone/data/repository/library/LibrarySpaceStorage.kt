package com.javierreansyah.pinecone.data.repository.library

import android.content.Context
import com.javierreansyah.pinecone.data.R
import com.javierreansyah.pinecone.data.local.database.library.SpaceDao
import com.javierreansyah.pinecone.data.local.database.library.SpaceEntity
import com.javierreansyah.pinecone.data.model.library.ALL_SPACES_ID

/** Called within the caller's Room transaction. */
internal suspend fun SpaceDao.ensureAllSpace(context: Context) {
    if (getAllSpacesSync().none { it.id == ALL_SPACES_ID }) {
        insertSpace(SpaceEntity(ALL_SPACES_ID, context.getString(R.string.storage_all_space), 0L))
    }
}
