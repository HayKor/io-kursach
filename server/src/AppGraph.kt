package ru.haykor.sudv.server

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.server.config.*
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions
import kotlinx.datetime.TimeZone
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import ru.haykor.sudv.server.core.config.AppConfig
import ru.haykor.sudv.server.core.database.Transactor
import ru.haykor.sudv.server.core.http.PluginInstaller
import ru.haykor.sudv.server.core.http.RouteRegistrar
import ru.haykor.sudv.server.expiry.domain.IExpiryService
import ru.haykor.sudv.server.records.domain.IPenaltyRepository
import ru.haykor.sudv.server.reports.domain.IReportRepository
import kotlin.time.Clock

/** Accessors only tests use (through [AppGraphKey]); production code gets dependencies by constructor injection. */
interface AppGraphTestAccessors {
    val appConfig: AppConfig
    val expiryService: IExpiryService
    val penaltyRepository: IPenaltyRepository
    val reportRepository: IReportRepository
}

@DependencyGraph(AppScope::class)
interface AppGraph : AppGraphTestAccessors {
    val connectionPool: ConnectionPool
    val database: R2dbcDatabase
    val bootstrap: Bootstrap
    val plugins: Set<PluginInstaller>
    val routes: Set<RouteRegistrar>
    val tables: Set<Table>

    // Built explicitly (rather than via the "r2dbc:pool:" URL scheme) so the pool can be disposed
    // on application shutdown; otherwise every module() start leaks one pool's worth of connections.
    @Provides
    @SingleIn(AppScope::class)
    fun provideConnectionPool(config: ApplicationConfig): ConnectionPool {
        val db = config.config("database")
        val options = ConnectionFactoryOptions.parse(db.property("url").getString())
            .mutate()
            .option(ConnectionFactoryOptions.USER, db.property("user").getString())
            .option(ConnectionFactoryOptions.PASSWORD, db.property("password").getString())
            .build()
        val factory = ConnectionFactories.get(options)
        val poolMaxSize = db.property("poolMaxSize").getString().toInt()
        val poolConfig = ConnectionPoolConfiguration.builder(factory)
            .maxSize(poolMaxSize)
            .build()
        return ConnectionPool(poolConfig)
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppConfig(config: ApplicationConfig): AppConfig = AppConfig.from(config)

    @Provides
    fun provideTimeZone(appConfig: AppConfig): TimeZone = appConfig.timeZone

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabase(connectionPool: ConnectionPool): R2dbcDatabase =
        R2dbcDatabase.connect(
            connectionFactory = connectionPool,
            databaseConfig = R2dbcDatabaseConfig.Builder().apply { explicitDialect = PostgreSQLDialect() },
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideTransactor(database: R2dbcDatabase): Transactor = Transactor(database)

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides config: ApplicationConfig, @Provides clock: Clock): AppGraph
    }
}
