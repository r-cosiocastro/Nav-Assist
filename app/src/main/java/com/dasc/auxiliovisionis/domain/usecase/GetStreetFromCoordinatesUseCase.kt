package com.dasc.auxiliovisionis.domain.usecase

import com.dasc.auxiliovisionis.data.remote.model.Address
import com.dasc.auxiliovisionis.domain.repository.LocationRepository
import javax.inject.Inject

class GetStreetFromCoordinatesUseCase @Inject constructor(
    private val repository: LocationRepository
) {
    suspend operator fun invoke(lat: Double, lon: Double): Address {
        return repository.getAddressFromCoordinates(lat, lon)
    }
}