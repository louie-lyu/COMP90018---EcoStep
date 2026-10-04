package com.ecostep.app.data.repository

import com.ecostep.app.data.model.PointTransaction
import com.ecostep.app.data.model.UserStats
import kotlinx.coroutines.flow.Flow

/**
 * Read-only view of the signed-in user's EcoPoints. Points are granted and spent only by the
 * trusted backend (mission awards, reward redemptions), never by the client.
 */
interface EcoPointsRepository {
    fun observeStats(): Flow<UserStats>

    suspend fun getStats(): UserStats

    fun observeTransactions(limit: Int = 50): Flow<List<PointTransaction>>
}
