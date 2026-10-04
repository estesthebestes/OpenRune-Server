package org.rsmod.api.hotreload

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.db.gateway.GameDbManager
import org.rsmod.api.db.gateway.model.GameDbResult
import org.rsmod.api.db.gateway.model.fold

public class ReloadContext(
    public val requesters: Set<ReloadRequester>,
    public val options: Set<String>,
    private val db: ReloadDatabase,
) {
    public fun <T> awaitDb(timeout: Duration = 30.seconds, query: (DatabaseConnection) -> T): T =
        db.query(timeout, query)

    public fun withOptions(extra: Set<String>): ReloadContext =
        ReloadContext(requesters, options + extra, db)
}

public interface ReloadDatabase {
    public fun <T> query(timeout: Duration, block: (DatabaseConnection) -> T): T
}

internal class GatewayReloadDatabase(private val db: GameDbManager) : ReloadDatabase {
    override fun <T> query(timeout: Duration, block: (DatabaseConnection) -> T): T {
        val future = CompletableFuture<T>()
        db.request(
            request = { connection -> GameDbResult.Ok(block(connection)) },
            response = { result ->
                result.fold(
                    onOk = { future.complete(it) },
                    onErr = { future.completeExceptionally(ReloadException("Database error: $it")) },
                )
            },
        )
        return try {
            future.get(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            throw ReloadException("Database query timed out after $timeout", e)
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }
}

internal object NoReloadDatabase : ReloadDatabase {
    override fun <T> query(timeout: Duration, block: (DatabaseConnection) -> T): T =
        throw ReloadException("Database access is unavailable.")
}
