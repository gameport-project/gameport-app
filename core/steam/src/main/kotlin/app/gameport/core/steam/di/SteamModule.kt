package app.gameport.core.steam.di

import app.gameport.core.steam.SteamAuthRepository
import app.gameport.core.steam.SteamLibraryRepository
import app.gameport.core.steam.JavaSteamAuthRepository
import app.gameport.core.steam.session.KeystoreTokenStore
import app.gameport.core.steam.session.TokenStore
import app.gameport.core.steam.GameDownloader
import app.gameport.core.steam.JavaSteamGameDownloader
import app.gameport.core.steam.JavaSteamLibraryRepository
import app.gameport.core.steam.cache.FileLibraryCacheStore
import app.gameport.core.steam.cache.LibraryCacheStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SteamModule {
    @Binds
    abstract fun bindAuthRepository(impl: JavaSteamAuthRepository): SteamAuthRepository

    @Binds
    abstract fun bindTokenStore(impl: KeystoreTokenStore): TokenStore

    @Binds
    abstract fun bindGameDownloader(impl: JavaSteamGameDownloader): GameDownloader

    @Binds
    abstract fun bindLibraryCache(impl: FileLibraryCacheStore): LibraryCacheStore

    @Binds
    abstract fun bindLibraryRepository(impl: JavaSteamLibraryRepository): SteamLibraryRepository
}
