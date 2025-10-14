package fr.neamar.kiss.searcher

import fr.neamar.kiss.pojo.Pojo

/**
 * Common interface for search result receiver
 * Allows Providers to work with both Searcher and SearcherCoroutine
 * 
 * Part of AsyncTask → Coroutines migration
 */
interface ISearchResultReceiver {
    /**
     * Add single result to the search
     */
    fun addResult(pojo: Pojo): Boolean
    
    /**
     * Add multiple results to the search
     */
    fun addResults(pojos: List<Pojo>): Boolean
    
    /**
     * Check if search is cancelled
     */
    fun isCancelled(): Boolean
}
