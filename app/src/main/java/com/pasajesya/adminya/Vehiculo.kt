package com.pasajesya.adminya

data class Vehiculo(

    val id: String = "",

    val empresaId: String = "",
    val empresaNombre: String = "",

    val placa: String = "",
    val tipo: String = "",
    val marca: String = "",
    val modelo: String = "",
    val color: String = "",

    val anio: Int = 0,
    val capacidad: Int = 4,

    val estado: String = "activo",

    val disponible: Boolean = true,
    val disponibilidad: String = "disponible",

    val viajeActualId: String = "",
    val choferActualUid: String = ""
)