package app.yodo.messenger.features.update

import app.yodo.messenger.domain.model.AppUpdate

internal const val INSTANT_POLL_MS = 30_000L
internal const val INSTANT_WAIT_LIMIT_MS = 6 * 60 * 60 * 1000L
internal fun canStartInstantDownload(update: AppUpdate?, installedCode: Int): Boolean =
    update != null && update.versionCode > installedCode && update.apkUrl.isNullOrBlank()

// A newer release may supersede the one selected while its APK is being built.
internal fun instantApkReady(update: AppUpdate, selectedCode: Int, installedCode: Int): Boolean =
    update.versionCode >= selectedCode && update.versionCode > installedCode && !update.apkUrl.isNullOrBlank()

internal fun instantPollDelay(retryAfterMillis: Long?): Long =
    maxOf(INSTANT_POLL_MS, retryAfterMillis ?: 0L)
