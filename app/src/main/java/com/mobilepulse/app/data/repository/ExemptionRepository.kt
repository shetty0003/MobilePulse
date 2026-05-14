package com.mobilepulse.app.data.repository

import com.mobilepulse.app.data.db.dao.ExemptionDao
import com.mobilepulse.app.data.db.entity.IgnoreListEntity
import com.mobilepulse.app.data.db.entity.WhitelistEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExemptionRepository @Inject constructor(
    private val dao: ExemptionDao
) {
    fun getWhitelist(): Flow<List<WhitelistEntity>> = dao.getWhitelist()

    fun getIgnoreList(): Flow<List<IgnoreListEntity>> = dao.getIgnoreList()

    suspend fun isExempt(pkg: String): Boolean {
        return dao.isWhitelisted(pkg) > 0 || dao.isIgnored(pkg) > 0
    }

    suspend fun getAllExemptPackages(): Set<String> {
        return (dao.getAllWhitelistedPackages() +
                dao.getAllIgnoredPackages()).toSet()
    }

    suspend fun addToWhitelist(pkg: String, name: String) {
        dao.removeFromIgnoreList(pkg)
        dao.addToWhitelist(WhitelistEntity(pkg, name))
    }

    suspend fun removeFromWhitelist(pkg: String) =
        dao.removeFromWhitelist(pkg)

    suspend fun addToIgnoreList(pkg: String, name: String) {
        dao.removeFromWhitelist(pkg)
        dao.addToIgnoreList(IgnoreListEntity(pkg, name))
    }

    suspend fun removeFromIgnoreList(pkg: String) =
        dao.removeFromIgnoreList(pkg)
}