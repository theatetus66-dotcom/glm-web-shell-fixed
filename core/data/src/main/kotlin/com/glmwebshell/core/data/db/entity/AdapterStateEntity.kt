package com.glmwebshell.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * The health record of a single capability, persisted across launches so the
 * app can auto-disable a capability that has failed CAPABILITY_FAIL_THRESHOLD
 * times in a row (TZ §5.3).
 */
@Entity(tableName = "adapter_state")
@Serializable
data class AdapterStateEntity(
    @PrimaryKey val capabilityId: String,
    val status: String,
    val lastCheckedAt: Long,
    val failCount: Int,
    val activeStrategy: String?,
    val adapterVersion: Int,
)

object AdapterStatus {
    const val OK = "OK"
    const val DEGRADED = "DEGRADED"
    const val FAILED = "FAILED"
    const val UNKNOWN = "UNKNOWN"
}
