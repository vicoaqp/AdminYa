package com.pasajesya.adminya

data class VendedorAdmin(

    val uid: String = "",

    val empresaId: String = "",
    val empresaNombre: String = "",

    val nombres: String = "",
    val apellidos: String = "",
    val nombreCompleto: String = "",

    val dni: String = "",
    val celular: String = "",
    val correo: String = "",

    val rol: String = "vendedor",
    val estado: String = "activo",

    val registroCompleto: Boolean = true,
    val debeCambiarPassword: Boolean = true

) {

    fun obtenerNombreCompleto(): String {

        return nombreCompleto.ifBlank {

            "$nombres $apellidos".trim()
        }
    }
}