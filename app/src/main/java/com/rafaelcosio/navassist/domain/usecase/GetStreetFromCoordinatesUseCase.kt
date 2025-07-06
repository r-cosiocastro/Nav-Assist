package com.rafaelcosio.navassist.domain.usecase

import com.rafaelcosio.navassist.data.remote.model.Address
import com.rafaelcosio.navassist.domain.repository.LocationRepository
import javax.inject.Inject

class GetStreetFromCoordinatesUseCase @Inject constructor(
    private val repository: LocationRepository
) {
    suspend operator fun invoke(lat: Double, lon: Double): Address {
        return repository.getAddressFromCoordinates(lat, lon)
    }
}