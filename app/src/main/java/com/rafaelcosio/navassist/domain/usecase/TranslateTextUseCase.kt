package com.rafaelcosio.navassist.domain.usecase

import com.google.mlkit.nl.translate.Translator
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class TranslateTextUseCase @Inject constructor(
    private val translator: Translator
) {
    suspend operator fun invoke(text: String): String {
        return try {
            translator.translate(text).await()
        } catch (e: Exception) {
            e.printStackTrace()
            text
        }
    }
}