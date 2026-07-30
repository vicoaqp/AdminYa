package com.pasajesya.adminya

data class ChoferAdmin(

    val uid: String = "",

    val empresaId: String = "",
    val empresaNombre: String = "",

    val nombres: String = "",
    val apellidos: String = "",
    val nombreCompleto: String = "",

    val dni: String = "",
    val celular: String = "",
    val correo: String = "",

    val licencia: String = "",
    val categoriaLicencia: String = "",
    val vencimientoLicencia: String = "",

    val estado: String = "activo",

    val disponible: Boolean = false,
    val disponibilidad: String = "no_disponible",

    val vehiculoId: String = "",
    val vehiculoPlaca: String = "",
    val vehiculoDescripcion: String = "",

    val viajeActualId: String = ""
) {

    fun obtenerNombreCompleto(): String {

        return nombreCompleto.ifBlank {

            "$nombres $apellidos".trim()
        }
    }
}