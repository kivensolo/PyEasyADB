package com.easyadb.core.database

import com.easyadb.core.config.AppPathsConfig
import com.easyadb.core.log.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager

/**
 * SQLite 数据库管理器。
 * 对应 Python 中的 DataBase.py（位于 src/）。
 * 表结构保持与 Python 一致：
 *   device(ip VARCHAR(20) PK, port VARCHAR(10) DEFAULT 0, alias VARCHAR(50), device_info VARCHAR(50), active INTEGER DEFAULT 0)
 *   package(name VARCHAR(50) PK)
 */
object DbManager {

    private val logger = AppLogger.getLogger(DbManager::class.java)
    private var connection: Connection? = null

    private val CREATE_DEVICE_TABLE = """
        CREATE TABLE IF NOT EXISTS device (
            ip VARCHAR(20) PRIMARY KEY,
            port VARCHAR(10) DEFAULT 0,
            alias VARCHAR(50),
            device_info VARCHAR(50),
            active INTEGER DEFAULT 0
        )
    """.trimIndent()

    private val CREATE_PACKAGE_TABLE = """
        CREATE TABLE IF NOT EXISTS package (
            name VARCHAR(50) PRIMARY KEY
        )
    """.trimIndent()

    suspend fun initialize(dbPath: String = AppPathsConfig.dbFile): DbResult<Unit> = withContext(Dispatchers.IO) {
        try {
            Class.forName("org.sqlite.JDBC")
            val conn = DriverManager.getConnection("jdbc:sqlite:$dbPath")
            connection = conn

            conn.createStatement().apply {
                execute(CREATE_DEVICE_TABLE)
                execute(CREATE_PACKAGE_TABLE)
                close()
            }

            logger.info { "Database initialized at $dbPath" }
            DbResult(success = true, data = Unit)
        } catch (e: Exception) {
            logger.error { "Database initialization failed: ${e.message}" }
            DbResult(success = false, error = e.message)
        }
    }

    private fun getConnection(): Connection =
        connection ?: throw IllegalStateException("Database not initialized")

    // ── 设备 CRUD ──

    suspend fun insertDevice(device: DeviceRecord): DbResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val sql = "INSERT OR REPLACE INTO device (ip, port, alias, device_info, active) VALUES (?, ?, ?, ?, ?)"
            getConnection().prepareStatement(sql).use { stmt ->
                stmt.setString(1, device.ip)
                stmt.setString(2, device.port)
                stmt.setString(3, device.alias)
                stmt.setString(4, device.deviceInfo)
                stmt.setInt(5, device.active)
                stmt.executeUpdate()
            }
            DbResult(success = true)
        } catch (e: Exception) {
            logger.error { "Insert device failed: ${e.message}" }
            DbResult(success = false, error = e.message)
        }
    }

    suspend fun getAllDevices(): DbResult<List<DeviceRecord>> = withContext(Dispatchers.IO) {
        try {
            val sql = "SELECT ip, port, alias, device_info, active FROM device"
            val stmt = getConnection().createStatement()
            val rs = stmt.executeQuery(sql)
            val devices = mutableListOf<DeviceRecord>()
            while (rs.next()) {
                devices.add(
                    DeviceRecord(
                        ip = rs.getString("ip"),
                        port = rs.getString("port"),
                        alias = rs.getString("alias") ?: "",
                        deviceInfo = rs.getString("device_info") ?: "",
                        active = rs.getInt("active")
                    )
                )
            }
            rs.close()
            stmt.close()
            DbResult(success = true, data = devices)
        } catch (e: Exception) {
            logger.error { "Get all devices failed: ${e.message}" }
            DbResult(success = false, error = e.message)
        }
    }

    suspend fun getDevice(ip: String): DbResult<DeviceRecord?> = withContext(Dispatchers.IO) {
        try {
            val sql = "SELECT ip, port, alias, device_info, active FROM device WHERE ip = ?"
            getConnection().prepareStatement(sql).use { stmt ->
                stmt.setString(1, ip)
                val rs = stmt.executeQuery()
                val device = if (rs.next()) {
                    DeviceRecord(
                        ip = rs.getString("ip"),
                        port = rs.getString("port"),
                        alias = rs.getString("alias") ?: "",
                        deviceInfo = rs.getString("device_info") ?: "",
                        active = rs.getInt("active")
                    )
                } else null
                rs.close()
                DbResult(success = true, data = device)
            }
        } catch (e: Exception) {
            logger.error { "Get device failed: ${e.message}" }
            DbResult(success = false, error = e.message)
        }
    }

    suspend fun updateDeviceAlias(ip: String, alias: String): DbResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val sql = "UPDATE device SET alias = ? WHERE ip = ?"
            getConnection().prepareStatement(sql).use { stmt ->
                stmt.setString(1, alias)
                stmt.setString(2, ip)
                stmt.executeUpdate()
            }
            DbResult(success = true)
        } catch (e: Exception) {
            logger.error { "Update device alias failed: ${e.message}" }
            DbResult(success = false, error = e.message)
        }
    }

    suspend fun setDeviceActive(ip: String, active: Int): DbResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val sql = "UPDATE device SET active = ? WHERE ip = ?"
            getConnection().prepareStatement(sql).use { stmt ->
                stmt.setInt(1, active)
                stmt.setString(2, ip)
                stmt.executeUpdate()
            }
            DbResult(success = true)
        } catch (e: Exception) {
            logger.error { "Set device active failed: ${e.message}" }
            DbResult(success = false, error = e.message)
        }
    }

    suspend fun deleteDevice(ip: String): DbResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val sql = "DELETE FROM device WHERE ip = ?"
            getConnection().prepareStatement(sql).use { stmt ->
                stmt.setString(1, ip)
                stmt.executeUpdate()
            }
            DbResult(success = true)
        } catch (e: Exception) {
            logger.error { "Delete device failed: ${e.message}" }
            DbResult(success = false, error = e.message)
        }
    }

    suspend fun clearDevices(): DbResult<Unit> = withContext(Dispatchers.IO) {
        try {
            getConnection().createStatement().use { stmt ->
                stmt.execute("DELETE FROM device")
            }
            DbResult(success = true)
        } catch (e: Exception) {
            logger.error { "Clear devices failed: ${e.message}" }
            DbResult(success = false, error = e.message)
        }
    }

    // ── 包名 CRUD ──

    suspend fun insertPackage(name: String): DbResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val sql = "INSERT OR IGNORE INTO package (name) VALUES (?)"
            getConnection().prepareStatement(sql).use { stmt ->
                stmt.setString(1, name)
                stmt.executeUpdate()
            }
            DbResult(success = true)
        } catch (e: Exception) {
            logger.error { "Insert package failed: ${e.message}" }
            DbResult(success = false, error = e.message)
        }
    }

    suspend fun getAllPackages(): DbResult<List<String>> = withContext(Dispatchers.IO) {
        try {
            val sql = "SELECT name FROM package"
            val stmt = getConnection().createStatement()
            val rs = stmt.executeQuery(sql)
            val packages = mutableListOf<String>()
            while (rs.next()) {
                packages.add(rs.getString("name"))
            }
            rs.close()
            stmt.close()
            DbResult(success = true, data = packages)
        } catch (e: Exception) {
            logger.error { "Get all packages failed: ${e.message}" }
            DbResult(success = false, error = e.message)
        }
    }

    suspend fun deletePackage(name: String): DbResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val sql = "DELETE FROM package WHERE name = ?"
            getConnection().prepareStatement(sql).use { stmt ->
                stmt.setString(1, name)
                stmt.executeUpdate()
            }
            DbResult(success = true)
        } catch (e: Exception) {
            logger.error { "Delete package failed: ${e.message}" }
            DbResult(success = false, error = e.message)
        }
    }

    fun close() {
        connection?.close()
        connection = null
        logger.info { "Database connection closed" }
    }
}
