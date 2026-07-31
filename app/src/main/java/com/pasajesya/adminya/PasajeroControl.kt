package com.pasajesya.adminya

data class PasajeroControl(

    val reservaId: String = "",

    val empresaId: String = "",
    val viajeId: String = "",

    val pasajeroNombre: String = "",
    val pasajeroDni: String = "",
    val pasajeroCelular: String = "",

    val numeroAsiento: String = "",
    val cantidadPasajeros: Int = 1,

    val estadoReserva: String = "confirmada",
    val embarcado: Boolean = false
)