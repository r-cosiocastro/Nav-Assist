package com.dasc.auxiliovisionis.data.remote.model

import com.google.gson.annotations.SerializedName

data class ReverseGeocodingResponse(
    @SerializedName("address") val address: Address
)

data class Address(
    val road: String?,
    val neighbourhood: String?,
    val city: String?,
    val state: String?,
    val postcode: String?,
    val country: String?
)