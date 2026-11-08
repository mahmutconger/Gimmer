package com.mcclabs.gimmer.backup

import android.accounts.Account
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.util.concurrent.TimeUnit

internal const val GOOGLE_ACCOUNT_TYPE = "com.google"
private const val WORK_NAME = "automatic-drive-backup"
private const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

internal fun driveAuthorizationRequest(account: String? = null): AuthorizationRequest =
    AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_SCOPE)))
        .apply { if (account != null) setAccount(Account(account, GOOGLE_ACCOUNT_TYPE)) }
        .build()

internal fun AuthorizationResult.driveAccessToken(): String? =
    accessToken.takeIf { !hasResolution() && DRIVE_SCOPE in grantedScopes.orEmpty() }

internal suspend fun scheduleDriveBackup(
    context: Context,
    interval: BackupInterval?,
    runSoon: Boolean = false,
) {
    val manager = WorkManager.getInstance(context)
    if (interval == null) {
        manager.cancelUniqueWork(WORK_NAME).await()
    } else {
        val work = PeriodicWorkRequestBuilder<AutomaticDriveBackupWorker>(interval.hours, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .apply { if (runSoon) setNextScheduleTimeOverride(System.currentTimeMillis()) }
            .build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, work).await()
    }
}

class AutomaticDriveBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val preferences = DriveBackupPreferences.get(applicationContext)
        val account = preferences.automaticBackup.value.account ?: return Result.success()
        val client = Identity.getAuthorizationClient(applicationContext)

        suspend fun authorizationRequired(): Result {
            preferences.report(account, BackupStatus.AUTH_REQUIRED)
            return Result.success()
        }
        suspend fun retry(): Result {
            preferences.report(account, BackupStatus.RETRYING)
            return Result.retry()
        }
        suspend fun failed(): Result {
            preferences.report(account, BackupStatus.FAILED)
            return Result.failure()
        }

        val token = try {
            val result = client.authorize(driveAuthorizationRequest(account)).await()
            result.driveAccessToken() ?: return authorizationRequired()
        } catch (error: CancellationException) {
            throw error
        } catch (error: ApiException) {
            return when (error.statusCode) {
                CommonStatusCodes.NETWORK_ERROR, CommonStatusCodes.TIMEOUT, CommonStatusCodes.INTERNAL_ERROR -> retry()
                else -> authorizationRequired()
            }
        } catch (_: Exception) {
            return failed()
        }

        return try {
            val outcome = DriveBackupCoordinator.backup(applicationContext, token) {
                if (preferences.automaticBackup.value.account != account) throw CancellationException()
            }
            preferences.report(account, outcome.status, outcome.backupTimestamp)
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: DriveHttpException) {
            when (error.status) {
                401 -> {
                    try {
                        client.clearToken(ClearTokenRequest.builder().setToken(token).build()).await()
                        if (runAttemptCount == 0) retry() else authorizationRequired()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        authorizationRequired()
                    }
                }
                408, 429, in 500..599 -> retry()
                else -> failed()
            }
        } catch (_: IOException) {
            retry()
        } catch (_: Exception) {
            failed()
        }
    }
}

internal val DriveBackupOutcome.status: BackupStatus
    get() = if (this is DriveBackupOutcome.Saved) BackupStatus.SAVED else BackupStatus.EMPTY

internal val DriveBackupOutcome.backupTimestamp: Long?
    get() = (this as? DriveBackupOutcome.Saved)?.file?.modifiedAt?.toEpochMilli()
