package com.ultrascaler.app.di

import android.content.Context
import com.ultrascaler.app.ml.UpscaleEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideUpscaleEngine(
        @ApplicationContext context: Context
    ): UpscaleEngine {
        return UpscaleEngine(context)
    }
}
