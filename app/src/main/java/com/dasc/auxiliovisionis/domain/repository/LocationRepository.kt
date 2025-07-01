package com.dasc.auxiliovisionis.domain.repository

import com.dasc.auxiliovisionis.data.remote.model.Address

interface LocationRepository {
    suspend fun getAddressFromCoordinates(lat: Double, lon: Double): Address
}