package io.github.aedev.flow.core

import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

interface CoreExtractionService {
    val state: StateFlow<CoreState>
    suspend fun ensureRunning(timeoutMs: Long = 5000): Boolean
    suspend fun resolve(request: CoreResolveRequest): Result<CoreResolveResult>
    suspend fun search(request: CoreSearchRequest): Result<CoreSearchResponse>
}

@Singleton
class CoreExtractionServiceImpl @Inject constructor(
    private val manager: AdultHideoutCoreManager,
    private val installer: BinaryInstaller
) : CoreExtractionService {
    private val client = CoreHttpClient()

    override val state: StateFlow<CoreState> = manager.state

    override suspend fun ensureRunning(timeoutMs: Long): Boolean {
        if (!installer.isInstalled()) {
            if (!installer.install()) return false
        }
        return manager.ensureRunning(timeoutMs)
    }

    override suspend fun resolve(request: CoreResolveRequest): Result<CoreResolveResult> {
        if (!ensureRunning()) return Result.failure(CoreUnavailableException())
        return client.resolve(request)
    }

    override suspend fun search(request: CoreSearchRequest): Result<CoreSearchResponse> {
        if (!ensureRunning()) return Result.failure(CoreUnavailableException())
        return client.search(request)
    }
}

class CoreUnavailableException : Exception("AdultHideout Core is not available")
