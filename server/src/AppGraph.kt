package ru.haykor.sudv

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.server.config.*
import io.r2dbc.spi.ConnectionFactoryOptions
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase

@DependencyGraph(AppScope::class)
interface AppGraph {
    val database: R2dbcDatabase

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabase(config: ApplicationConfig): R2dbcDatabase {
        val db = config.config("database")
        return R2dbcDatabase.connect {
            setUrl(db.property("url").getString())
            connectionFactoryOptions {
                option(ConnectionFactoryOptions.USER, db.property("user").getString())
                option(ConnectionFactoryOptions.PASSWORD, db.property("password").getString())
            }
        }
    }

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides config: ApplicationConfig): AppGraph
    }
}
