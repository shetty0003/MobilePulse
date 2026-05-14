package com.mobilepulse.app.data.repository

import com.mobilepulse.app.data.db.dao.BootRestrictionDao
import com.mobilepulse.app.data.db.entity.BootRestrictionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BootRestrictionRepository @Inject constructor(
    private val dao: BootRestrictionDao
) {
    fun getAll(): Flow<List<BootRestrictionEntity>> = dao.getAll()

    suspend fun getAllList(): List<BootRestrictionEntity> = dao.getAllList()

    suspend fun add(entity: BootRestrictionEntity) = dao.insert(entity)

    suspend fun setRestrictBackground(pkg: String, value: Boolean) =
        dao.setRestrictBackground(pkg, value)

    suspend fun remove(pkg: String) = dao.deleteByPackage(pkg)
}
