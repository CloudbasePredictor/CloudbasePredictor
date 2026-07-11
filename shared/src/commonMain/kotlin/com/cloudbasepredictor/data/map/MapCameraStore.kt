package com.cloudbasepredictor.data.map

data class MapCameraPosition(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
)

interface MapCameraStore {
    fun read(): MapCameraPosition?

    fun write(position: MapCameraPosition)

    fun clear()
}
