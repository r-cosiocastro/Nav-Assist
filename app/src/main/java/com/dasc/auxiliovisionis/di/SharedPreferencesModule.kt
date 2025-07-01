package com.dasc.auxiliovisionis.di

import android.content.Context
import android.content.SharedPreferences
import com.dasc.auxiliovisionis.utils.AppPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SharedPreferencesModule {

    @Singleton
    @Provides
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences(AppPreferences.PREFS_NAME, Context.MODE_PRIVATE)
    }
}