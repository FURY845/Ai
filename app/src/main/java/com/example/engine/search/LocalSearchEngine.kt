package com.example.engine.search

import com.example.data.local.AppDatabase
import com.example.data.local.DocumentEntity
import com.example.data.local.PageEntity
import com.example.data.local.SearchTokenEntity
import com.example.engine.arabic.ArabicTextProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SearchResultPassage(
    val documentId: Long,
    val documentTitle: String,
    val subject: String,
    val pageNumber: Int,
    val matchedPassage: String,
    val fullPageText: String,
    val confidenceScore: Float,
    val matchedKeywords: List<String>
)

class LocalSearchEngine(private val db: AppDatabase) {

    /**
     * Builds inverted search index for a document and its pages
     */
    suspend fun indexDocument(document: DocumentEntity, pages: List<PageEntity>) = withContext(Dispatchers.IO) {
        val searchTokenDao = db.searchTokenDao()
        searchTokenDao.deleteTokensForDocument(document.id)

        val tokensToInsert = mutableListOf<SearchTokenEntity>()

        for (page in pages) {
            val tokens = ArabicTextProcessor.tokenize(page.rawText, stem = true)
            val freqMap = mutableMapOf<String, Int>()
            for (t in tokens) {
                freqMap[t] = (freqMap[t] ?: 0) + 1
            }

            for ((token, freq) in freqMap) {
                tokensToInsert.add(
                    SearchTokenEntity(
                        pageId = page.id,
                        documentId = document.id,
                        token = token,
                        frequency = freq
                    )
                )
            }
        }

        if (tokensToInsert.isNotEmpty()) {
            tokensToInsert.chunked(500).forEach { chunk ->
                searchTokenDao.insertTokens(chunk)
            }
        }

        db.documentDao().updateDocument(document.copy(isIndexed = true))
    }

    /**
     * Searches curriculum documents for passages relevant to the student's question across assigned documents
     */
    suspend fun searchCurriculum(
        question: String,
        subjectFilter: String? = null,
        documentIds: List<Long>? = null,
        docTypeFilter: String? = null, // "EXPLANATION" or "QUESTIONS" or null for all
        topK: Int = 3
    ): List<SearchResultPassage> = withContext(Dispatchers.IO) {
        val normalizedQuestion = ArabicTextProcessor.normalize(question)
        if (normalizedQuestion.isBlank()) return@withContext emptyList()

        val queryTokens = ArabicTextProcessor.tokenize(question, stem = true)
        val rawTokens = ArabicTextProcessor.tokenize(question, stem = false)

        val pageDao = db.pageDao()
        val documentDao = db.documentDao()
        val searchTokenDao = db.searchTokenDao()

        // 1. Inverted index match
        val matchedPageIds = if (queryTokens.isNotEmpty()) {
            searchTokenDao.findMatchingPageIds(queryTokens).map { it.pageId }
        } else {
            emptyList()
        }

        val allCandidatePages = mutableMapOf<Long, PageEntity>()

        for (pageId in matchedPageIds) {
            pageDao.getPageById(pageId)?.let { allCandidatePages[it.id] = it }
        }

        // 2. Full text scan for raw tokens
        for (word in rawTokens) {
            if (word.length >= 3) {
                val wordMatches = if (subjectFilter != null && subjectFilter.isNotBlank() && subjectFilter != "الكل") {
                    pageDao.searchPagesBySubjectAndText(subjectFilter, word)
                } else {
                    pageDao.searchPagesByText(word)
                }
                for (p in wordMatches) {
                    allCandidatePages[p.id] = p
                }
            }
        }

        // 3. Fallback: only if query was broad and inverted index returned empty results
        if (allCandidatePages.isEmpty() && queryTokens.isEmpty()) {
            val subjectDocs = if (subjectFilter != null && subjectFilter.isNotBlank() && subjectFilter != "الكل") {
                documentDao.getDocumentsBySubjectSync(subjectFilter)
            } else {
                documentDao.getAllDocumentsSync()
            }
            for (d in subjectDocs) {
                val pages = pageDao.getPagesForDocumentSync(d.id)
                for (p in pages) {
                    allCandidatePages[p.id] = p
                }
            }
        }

        val scoredResults = mutableListOf<SearchResultPassage>()

        for ((_, page) in allCandidatePages) {
            if (documentIds != null && documentIds.isNotEmpty() && !documentIds.contains(page.documentId)) {
                continue
            }

            val doc = documentDao.getDocumentById(page.documentId) ?: continue
            if (subjectFilter != null && subjectFilter.isNotBlank() && subjectFilter != "الكل" && doc.subject != subjectFilter) {
                continue
            }
            if (docTypeFilter != null && doc.docType != docTypeFilter) {
                continue
            }

            val passages = ArabicTextProcessor.splitIntoPassages(page.rawText)
            for (passage in passages) {
                val score = calculateRelevanceScore(question, queryTokens, rawTokens, passage)
                if (score > 0.10f) {
                    val matchedKeys = queryTokens.filter {
                        ArabicTextProcessor.normalize(passage).contains(it) ||
                                ArabicTextProcessor.lightStem(it).let { st -> ArabicTextProcessor.normalize(passage).contains(st) }
                    }

                    // Boost score if document matches requested docType
                    val typeBonus = if (docTypeFilter != null && doc.docType == docTypeFilter) 0.05f else 0.0f
                    val finalScore = (score + typeBonus).coerceAtMost(1.0f)

                    scoredResults.add(
                        SearchResultPassage(
                            documentId = doc.id,
                            documentTitle = doc.title,
                            subject = doc.subject,
                            pageNumber = page.pageNumber,
                            matchedPassage = passage,
                            fullPageText = page.rawText,
                            confidenceScore = finalScore,
                            matchedKeywords = matchedKeys
                        )
                    )
                }
            }
        }

        scoredResults.sortedByDescending { it.confidenceScore }.take(topK)
    }

    private fun calculateRelevanceScore(
        question: String,
        stemmedTokens: List<String>,
        rawTokens: List<String>,
        passage: String
    ): Float {
        val normalizedPassage = ArabicTextProcessor.normalize(passage)
        val normalizedQuestion = ArabicTextProcessor.normalize(question)

        if (normalizedPassage.contains(normalizedQuestion)) {
            return 0.95f
        }

        var matchScore = 0f
        val passageTokens = ArabicTextProcessor.tokenize(passage, stem = true).toSet()
        val passageRawWords = ArabicTextProcessor.tokenize(passage, stem = false)

        var hitCount = 0
        for (qToken in stemmedTokens) {
            if (passageTokens.contains(qToken)) {
                hitCount++
            } else {
                for (pWord in passageRawWords) {
                    if (ArabicTextProcessor.fuzzySimilarity(qToken, pWord) >= 0.80f) {
                        hitCount++
                        break
                    }
                }
            }
        }

        if (stemmedTokens.isNotEmpty()) {
            matchScore += (hitCount.toFloat() / stemmedTokens.size.toFloat()) * 0.70f
        }

        if (rawTokens.size >= 2) {
            for (i in 0 until rawTokens.size - 1) {
                val bigram = "${rawTokens[i]} ${rawTokens[i + 1]}"
                if (normalizedPassage.contains(bigram)) {
                    matchScore += 0.25f
                }
            }
        }

        return matchScore.coerceIn(0.0f, 1.0f)
    }
}
