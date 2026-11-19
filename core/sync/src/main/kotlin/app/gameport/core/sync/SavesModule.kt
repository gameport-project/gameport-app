package app.gameport.core.sync

import app.gameport.core.install.SavesBeforeRemoval
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SavesModule {
    @Binds
    abstract fun bindSavesBeforeRemoval(impl: SaveCatchUp): SavesBeforeRemoval
}
