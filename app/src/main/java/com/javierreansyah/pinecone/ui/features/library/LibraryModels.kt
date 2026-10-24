package com.javierreansyah.pinecone.ui.features.library

enum class LayoutMode { Grid, BigGrid, List, BigList }
enum class SortType { Title, Author, LastRead, Added, Progress, Custom }
enum class StatusFilter { NotStarted, Reading, Finished }
enum class ShelfFilter { Shelves, Unshelved }

enum class SearchCategory { All, Books, Authors, Shelves, Tags }

enum class LibraryView { Both, Books, Shelves }

enum class FileFilter { EPUB, PDF, Comics, Audio, Images, WebPublication, Other }
