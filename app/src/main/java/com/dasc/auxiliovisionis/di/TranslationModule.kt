package com.dasc.auxiliovisionis.di

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TranslationModule {

    @Provides
    @Singleton
    fun provideMLKitTranslator(
        @ApplicationContext context: Context
    ): Translator {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.SPANISH)
            .build()

        val translator = Translation.getClient(options)

        // Descarga automática del modelo
        val conditions = DownloadConditions.Builder()
            //.requireWifi() // Opcional: puedes quitarlo si aceptas datos móviles
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                translator.downloadModelIfNeeded(conditions).await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return translator
    }
}