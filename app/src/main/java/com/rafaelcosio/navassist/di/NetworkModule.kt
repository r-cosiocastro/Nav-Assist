package com.rafaelcosio.navassist.di

import com.rafaelcosio.navassist.data.remote.NominatimApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    fun provideBaseUrl() = "https://nominatim.openstreetmap.org/"

    val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
                .newBuilder()
                .header("User-Agent", "AuxilioVisionIS/1.0 (r.cosio@uabcs.mx)")
                .build()
            chain.proceed(request)
        }
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(baseUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideNominatimApi(retrofit: Retrofit): NominatimApi =
        retrofit.create(NominatimApi::class.java)
}