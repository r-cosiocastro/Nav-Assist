package com.rafaelcosio.navassist.domain.repository

import com.rafaelcosio.navassist.data.remote.model.Address

interface LocationRepository {
    suspend fun getAddressFromCoordinates(lat: Double, lon: Double): Address
}