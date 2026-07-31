package com.pasajesya.adminya

data class ReservaPagoAdmin(

    val reservaId: String = "",

    val empresaId: String = "",
    val viajeId: String = "",

    val usuarioId: String = "",
    val usuarioCorreo: String = "",

    val pasajeroNombre: String = "",
    val pasajeroDni: String = "",
    val pasajeroCelular: String = "",

    val asiento: String = "",
    val precio: Double = 0.0,

    val estadoReserva: String = "pendiente",
    val estadoPago: String = "pendiente",

    val estadoAbordaje: String = "pendiente",
    val boletoValidado: Boolean = false,

    val fechaOrden: Long = 0L
)