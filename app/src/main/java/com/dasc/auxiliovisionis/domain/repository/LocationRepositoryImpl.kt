package com.dasc.auxiliovisionis.domain.repository

import com.dasc.auxiliovisionis.data.remote.NominatimApi
import com.dasc.auxiliovisionis.data.remote.model.Address
import javax.inject.Inject

class LocationRepositoryImpl @Inject constructor(
    private val api: NominatimApi
) : LocationRepository {
    override suspend fun getAddressFromCoordinates(lat: Double, lon: Double): Address {
        return api.getReverseGeocoding(lat, lon).address
    }
}
