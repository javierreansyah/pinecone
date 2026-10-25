package com.javierreansyah.pinecone.ui.features.library

@kotlinx.serialization.Serializable
enum class LayoutMode { Grid, BigGrid, List, BigList }

@kotlinx.serialization.Serializable
enum class SortType { Title, Author, LastRead, Added, Progress, Custom }

@kotlinx.serialization.Serializable
enum class StatusFilter { NotStarted, Reading, Finished }

@kotlinx.serialization.Serializable
enum class ShelfFilter { Shelves, Unshelved }

@kotlinx.serialization.Serializable
enum class SearchCategory { All, Books, Authors, Shelves, Tags }

@kotlinx.serialization.Serializable
enum class LibraryView { Both, Books, Shelves }

@kotlinx.serialization.Serializable
enum class FileFilter { EPUB, PDF, Comics, Audio, Images, WebPublication, Other }
