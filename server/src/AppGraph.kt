package ru.haykor.sudv

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
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import ru.haykor.sudv.auth.IAuthService
import ru.haykor.sudv.auth.JwtService
import ru.haykor.sudv.config.AppConfig
import ru.haykor.sudv.config.Bootstrap
import ru.haykor.sudv.db.IEmployeeRepository
import ru.haykor.sudv.db.IEventLogRepository
import ru.haykor.sudv.db.IPenaltyRepository
import ru.haykor.sudv.db.IUserRepository
import ru.haykor.sudv.db.Transactor
import ru.haykor.sudv.expiry.ExpiryScheduler
import ru.haykor.sudv.expiry.IExpiryService
import ru.haykor.sudv.files.IFileStorage
import ru.haykor.sudv.records.IRecordService
import kotlin.time.Clock

@DependencyGraph(AppScope::class)
interface AppGraph {
    val appConfig: AppConfig
    val timeZone: TimeZone
    val transactor: Transactor
    val connectionPool: ConnectionPool
    val database: R2dbcDatabase
    val authService: IAuthService
    val jwtService: JwtService
    val userRepository: IUserRepository
    val employeeRepository: IEmployeeRepository
    val penaltyRepository: IPenaltyRepository
    val eventLogRepository: IEventLogRepository
    val recordService: IRecordService
    val expiryService: IExpiryService
    val fileStorage: IFileStorage
    val expiryScheduler: ExpiryScheduler
    val bootstrap: Bootstrap

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
