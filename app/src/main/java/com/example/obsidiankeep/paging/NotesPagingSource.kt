package com.example.obsidiankeep.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.obsidiankeep.data.Note
import com.example.obsidiankeep.data.NoteDao

/**
 * PagingSource с настоящей SQL-пагинацией (LIMIT/OFFSET).
 *
 * Раньше загружал ВСЕ заметки через getAllNotesOnce() на каждой странице —
 * это убивало смысл пагинации. Теперь запрашивает только нужную страницу.
 *
 * Использование:
 *   val pager = Pager(PagingConfig(pageSize = 30)) {
 *       NotesPagingSource(dao, folderId = null, query = "")
 *   }.flow
 */
class NotesPagingSource(
    private val dao: NoteDao,
    private val folderId: String?,
    private val query: String
) : PagingSource<Int, Note>() {

    override fun getRefreshKey(state: PagingState<Int, Note>): Int? {
        return state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Note> {
        return try {
            val page = params.key ?: 0
            val pageSize = params.loadSize.coerceAtMost(MAX_PAGE_SIZE)
            val offset = page * pageSize

            val items = when {
                query.isNotBlank() -> dao.searchNotesPage(query, pageSize, offset)
                folderId != null -> dao.getNotesPageInFolder(folderId, pageSize, offset)
                else -> dao.getNotesPage(pageSize, offset)
            }

            val isLastPage = items.size < pageSize

            LoadResult.Page(
                data = items,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (isLastPage || items.isEmpty()) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    companion object {
        const val MAX_PAGE_SIZE = 50
    }
}
