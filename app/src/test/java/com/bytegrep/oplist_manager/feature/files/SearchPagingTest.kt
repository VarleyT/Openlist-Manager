package com.bytegrep.oplist_manager.feature.files

import com.bytegrep.oplist_manager.data.model.FsSearchItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchPagingTest {

    private fun items(range: IntRange, parent: String = "/a") = range.map { FsSearchItem(parent = parent, name = "f$it") }

    @Test
    fun `first page with more results reports hasMore`() {
        val state = SearchUiState("kw").appendPage(1, items(1..100), total = 250)

        assertEquals(100, state.results.size)
        assertEquals(1, state.page)
        assertFalse(state.isLoading)
        assertTrue(state.hasMore)
    }

    @Test
    fun `appending pages accumulates until total reached`() {
        val state = SearchUiState("kw")
            .appendPage(1, items(1..100), total = 150)
            .appendPage(2, items(101..150), total = 150)

        assertEquals(150, state.results.size)
        assertFalse(state.hasMore)
    }

    @Test
    fun `duplicate entries across pages are dropped`() {
        val state = SearchUiState("kw")
            .appendPage(1, items(1..3), total = 5)
            .appendPage(2, items(3..5), total = 5)

        assertEquals(listOf("f1", "f2", "f3", "f4", "f5"), state.results.map { it.name })
    }

    @Test
    fun `same name in different parents is kept`() {
        val state = SearchUiState("kw").appendPage(1, items(1..1, "/a") + items(1..1, "/b"), total = 2)

        assertEquals(2, state.results.size)
    }

    @Test
    fun `empty page stops paging even if server total is larger`() {
        val state = SearchUiState("kw")
            .appendPage(1, items(1..100), total = 500)
            .appendPage(2, emptyList(), total = 500)

        assertFalse(state.hasMore)
        assertEquals(100L, state.total)
    }

    @Test
    fun `appending clears load more error and loading flags`() {
        val state = SearchUiState("kw", isLoadingMore = true, loadMoreError = "x", page = 1, results = items(1..1), total = 2)
            .appendPage(2, items(2..2), total = 2)

        assertFalse(state.isLoadingMore)
        assertNull(state.loadMoreError)
    }

    @Test
    fun `not loaded state has no more`() {
        assertFalse(SearchUiState("kw", total = 10).hasMore)
    }
}
