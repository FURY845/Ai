package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY addedAt DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE subject = :subject ORDER BY addedAt DESC")
    fun getDocumentsBySubject(subject: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE subject = :subject ORDER BY addedAt DESC")
    suspend fun getDocumentsBySubjectSync(subject: String): List<DocumentEntity>

    @Query("SELECT * FROM documents ORDER BY addedAt DESC")
    suspend fun getAllDocumentsSync(): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE subject = :subject AND docType = :docType LIMIT 1")
    suspend fun getDocumentBySubjectAndType(subject: String, docType: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE subject = :subject AND docType = :docType")
    fun getDocumentsBySubjectAndTypeFlow(subject: String, docType: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: Long): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: Long)

    @Query("DELETE FROM documents WHERE subject = :subject AND docType = :docType")
    suspend fun deleteDocumentBySubjectAndType(subject: String, docType: String)

    @Query("SELECT COUNT(*) FROM documents")
    fun getDocumentCount(): Flow<Int>

    @Query("SELECT DISTINCT subject FROM documents ORDER BY subject ASC")
    fun getDistinctSubjects(): Flow<List<String>>

    @Query("SELECT DISTINCT subject FROM documents ORDER BY subject ASC")
    suspend fun getDistinctSubjectsSync(): List<String>
}

@Dao
interface PageDao {
    @Query("SELECT * FROM document_pages WHERE documentId = :documentId ORDER BY pageNumber ASC")
    fun getPagesForDocument(documentId: Long): Flow<List<PageEntity>>

    @Query("SELECT * FROM document_pages WHERE documentId = :documentId ORDER BY pageNumber ASC")
    suspend fun getPagesForDocumentSync(documentId: Long): List<PageEntity>

    @Query("SELECT * FROM document_pages WHERE documentId = :documentId AND pageNumber = :pageNumber LIMIT 1")
    suspend fun getPage(documentId: Long, pageNumber: Int): PageEntity?

    @Query("SELECT * FROM document_pages WHERE id = :pageId")
    suspend fun getPageById(pageId: Long): PageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<PageEntity>)

    @Query("DELETE FROM document_pages WHERE documentId = :documentId")
    suspend fun deletePagesForDocument(documentId: Long)

    @Query("SELECT * FROM document_pages WHERE normalizedText LIKE '%' || :query || '%' LIMIT 30")
    suspend fun searchPagesByText(query: String): List<PageEntity>

    @Query("SELECT * FROM document_pages WHERE documentId IN (SELECT id FROM documents WHERE subject = :subject AND docType = 'EXPLANATION') AND normalizedText LIKE '%' || :query || '%' LIMIT 50")
    suspend fun searchExplanationPagesBySubject(subject: String, query: String): List<PageEntity>

    @Query("SELECT * FROM document_pages WHERE documentId IN (SELECT id FROM documents WHERE subject = :subject) AND normalizedText LIKE '%' || :query || '%' LIMIT 50")
    suspend fun searchPagesBySubjectAndText(subject: String, query: String): List<PageEntity>

    @Query("SELECT * FROM document_pages WHERE documentId IN (SELECT id FROM documents WHERE subject = :subject)")
    suspend fun getAllPagesBySubject(subject: String): List<PageEntity>

    @Query("SELECT * FROM document_pages")
    suspend fun getAllPages(): List<PageEntity>

    @Query("SELECT COUNT(*) FROM document_pages")
    fun getTotalPagesCount(): Flow<Int>
}

@Dao
interface SearchTokenDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTokens(tokens: List<SearchTokenEntity>)

    @Query("DELETE FROM search_tokens WHERE documentId = :documentId")
    suspend fun deleteTokensForDocument(documentId: Long)

    @Query("SELECT pageId, SUM(frequency) as totalFreq FROM search_tokens WHERE token IN (:tokens) GROUP BY pageId ORDER BY totalFreq DESC LIMIT 20")
    suspend fun findMatchingPageIds(tokens: List<String>): List<PageMatchResult>
}

data class PageMatchResult(
    val pageId: Long,
    val totalFreq: Int
)

@Dao
interface QuizDao {
    @Query("SELECT * FROM quizzes ORDER BY createdAt DESC")
    fun getAllQuizzes(): Flow<List<QuizEntity>>

    @Query("SELECT * FROM quizzes WHERE isCompleted = 1 ORDER BY createdAt DESC")
    fun getCompletedQuizzes(): Flow<List<QuizEntity>>

    @Query("SELECT * FROM quizzes WHERE id = :id")
    suspend fun getQuizById(id: Long): QuizEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuiz(quiz: QuizEntity): Long

    @Update
    suspend fun updateQuiz(quiz: QuizEntity)

    @Query("DELETE FROM quizzes WHERE id = :id")
    suspend fun deleteQuiz(id: Long)

    @Query("SELECT * FROM quiz_questions WHERE quizId = :quizId")
    fun getQuestionsForQuiz(quizId: Long): Flow<List<QuizQuestionEntity>>

    @Query("SELECT * FROM quiz_questions WHERE quizId = :quizId")
    suspend fun getQuestionsForQuizSync(quizId: Long): List<QuizQuestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<QuizQuestionEntity>)

    @Update
    suspend fun updateQuestion(question: QuizQuestionEntity)

    @Query("SELECT COUNT(*) FROM quizzes WHERE isCompleted = 1")
    fun getCompletedQuizCount(): Flow<Int>

    @Query("SELECT AVG(CAST(score AS FLOAT) / CAST(totalQuestions AS FLOAT) * 100) FROM quizzes WHERE isCompleted = 1")
    fun getAverageQuizScore(): Flow<Float?>

    @Query("SELECT SUM(timeSpentSeconds) FROM quizzes WHERE isCompleted = 1")
    fun getTotalTestTimeSeconds(): Flow<Int?>
}

@Dao
interface MistakeDao {
    @Query("SELECT * FROM mistakes WHERE isResolved = 0 ORDER BY mistakeCount DESC, lastMistakeAt DESC")
    fun getUnresolvedMistakes(): Flow<List<MistakeEntity>>

    @Query("SELECT * FROM mistakes WHERE isResolved = 0 ORDER BY mistakeCount DESC, lastMistakeAt DESC")
    suspend fun getUnresolvedMistakesSync(): List<MistakeEntity>

    @Query("SELECT * FROM mistakes WHERE subject = :subject AND isResolved = 0 ORDER BY mistakeCount DESC")
    fun getMistakesBySubject(subject: String): Flow<List<MistakeEntity>>

    @Query("SELECT * FROM mistakes WHERE questionText = :questionText LIMIT 1")
    suspend fun findMistakeByQuestion(questionText: String): MistakeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMistake(mistake: MistakeEntity): Long

    @Update
    suspend fun updateMistake(mistake: MistakeEntity)

    @Query("UPDATE mistakes SET isResolved = 1 WHERE id = :id")
    suspend fun resolveMistake(id: Long)

    @Query("DELETE FROM mistakes WHERE id = :id")
    suspend fun deleteMistake(id: Long)

    @Query("SELECT COUNT(*) FROM mistakes WHERE isResolved = 0")
    fun getUnresolvedMistakeCount(): Flow<Int>
}

@Dao
interface ModelFileDao {
    @Query("SELECT * FROM model_files ORDER BY addedAt DESC")
    fun getAllModelFiles(): Flow<List<ModelFileEntity>>

    @Query("SELECT * FROM model_files WHERE modelType = :type")
    fun getModelsByType(type: String): Flow<List<ModelFileEntity>>

    @Query("SELECT * FROM model_files WHERE modelType = :type AND isActive = 1 LIMIT 1")
    suspend fun getActiveModel(type: String): ModelFileEntity?

    @Query("SELECT * FROM model_files WHERE id = :id")
    suspend fun getModelById(id: Long): ModelFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: ModelFileEntity): Long

    @Update
    suspend fun updateModel(model: ModelFileEntity)

    @Query("UPDATE model_files SET isActive = 0 WHERE modelType = :type")
    suspend fun deactivateAllModelsOfType(type: String)

    @Query("UPDATE model_files SET isActive = 1 WHERE id = :id")
    suspend fun activateModel(id: Long)

    @Query("DELETE FROM model_files WHERE id = :id")
    suspend fun deleteModel(id: Long)
}

@Dao
interface StudyActivityDao {
    @Query("SELECT * FROM study_activities ORDER BY timestamp DESC LIMIT 50")
    fun getRecentActivities(): Flow<List<StudyActivityEntity>>

    @Insert
    suspend fun logActivity(activity: StudyActivityEntity)

    @Query("SELECT SUM(durationMinutes) FROM study_activities")
    fun getTotalStudyMinutes(): Flow<Int?>
}
