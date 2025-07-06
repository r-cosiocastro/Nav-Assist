package com.rafaelcosio.navassist.data.remote

import com.rafaelcosio.navassist.data.remote.model.ReverseGeocodingResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface NominatimApi {
    @GET("reverse")
    suspend fun getReverseGeocoding(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("format") format: String = "json"
    ): ReverseGeocodingResponse
}