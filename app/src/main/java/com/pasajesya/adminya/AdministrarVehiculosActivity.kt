package com.pasajesya.adminya

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AlertDialog
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.pasajesya.adminya.databinding.ActivityAdministrarVehiculosBinding
import com.pasajesya.adminya.databinding.DialogVehiculoBinding
import java.util.Calendar
import java.util.Locale

class AdministrarVehiculosActivity : AppCompatActivity() {
    private lateinit var binding:
            ActivityAdministrarVehiculosBinding

    private lateinit var auth:
            FirebaseAuth

    private lateinit var firestore:
            FirebaseFirestore

    private lateinit var adaptador:
            VehiculoAdapter

    private var listenerPerfil:
            ListenerRegistration? = null

    private var listenerVehiculos:
            ListenerRegistration? = null

    private var empresaIdActual = ""
    private var empresaNombreActual = ""

    private val listaCompleta =
        mutableListOf<Vehiculo>()

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        binding =
            ActivityAdministrarVehiculosBinding.inflate(
                layoutInflater
            )

        setContentView(binding.root)

        auth =
            FirebaseAuth.getInstance()

        firestore =
            FirebaseFirestore.getInstance()

        configurarPantalla()
        configurarLista()
        configurarEventos()
    }

    override fun onStart() {
        super.onStart()

        empresaIdActual = ""
        escucharPerfilPropietario()
    }

    private fun configurarPantalla() {

        window.statusBarColor =
            getColor(
                R.color.adminya_primary_dark
            )

        window.navigationBarColor =
            getColor(
                R.color.adminya_background
            )
    }

    private fun configurarLista() {

        adaptador =
            VehiculoAdapter(

                alEditar = { vehiculo ->

                    mostrarFormularioVehiculo(
                        vehiculo
                    )
                },

                alCambiarEstado = { vehiculo ->

                    mostrarDialogoEstado(
                        vehiculo
                    )
                }
            )

        binding.recyclerVehiculos.layoutManager =
            LinearLayoutManager(this)

        binding.recyclerVehiculos.adapter =
            adaptador
    }

    private fun configurarEventos() {

        binding.btnVolver.setOnClickListener {
            finish()
        }

        binding.fabNuevoVehiculo.setOnClickListener {

            mostrarFormularioVehiculo(
                null
            )
        }

        binding.etBuscar.addTextChangedListener(

            object : TextWatcher {

                override fun beforeTextChanged(
                    texto: CharSequence?,
                    inicio: Int,
                    cantidad: Int,
                    despues: Int
                ) = Unit

                override fun onTextChanged(
                    texto: CharSequence?,
                    inicio: Int,
                    antes: Int,
                    cantidad: Int
                ) {

                    aplicarFiltro(
                        texto?.toString().orEmpty()
                    )
                }

                override fun afterTextChanged(
                    texto: Editable?
                ) = Unit
            }
        )
    }

    private fun escucharPerfilPropietario() {

        val usuario =
            auth.currentUser

        if (usuario == null) {
            finish()
            return
        }

        mostrarCargando(true)

        listenerPerfil?.remove()

        listenerPerfil = firestore
            .collection(
                "usuariosgestionpasajes"
            )
            .document(usuario.uid)
            .addSnapshotListener {
                    documento, error ->

                if (error != null) {

                    mostrarCargando(false)

                    Toast.makeText(
                        this,
                        "No se pudo cargar el propietario.",
                        Toast.LENGTH_LONG
                    ).show()

                    return@addSnapshotListener
                }

                if (
                    documento == null ||
                    !documento.exists()
                ) {

                    mostrarCargando(false)
                    finish()

                    return@addSnapshotListener
                }

                procesarPerfil(documento)
            }
    }

    private fun procesarPerfil(
        documento: DocumentSnapshot
    ) {

        val rol =
            textoDocumento(
                documento,
                "rol"
            ).lowercase(Locale.ROOT)

        val estado =
            textoDocumento(
                documento,
                "estado"
            ).lowercase(Locale.ROOT)

        val empresaId =
            textoDocumento(
                documento,
                "empresaId"
            )

        val empresaNombre =
            textoDocumento(
                documento,
                "empresaNombre"
            )

        when {

            rol != "propietario" -> {

                mostrarCargando(false)

                Toast.makeText(
                    this,
                    "Esta cuenta no pertenece a un propietario.",
                    Toast.LENGTH_LONG
                ).show()

                finish()
            }

            estado != "activo" -> {

                mostrarCargando(false)

                Toast.makeText(
                    this,
                    "La cuenta del propietario no está activa.",
                    Toast.LENGTH_LONG
                ).show()

                finish()
            }

            empresaId.isBlank() -> {

                mostrarCargando(false)

                Toast.makeText(
                    this,
                    "No tienes una empresa registrada.",
                    Toast.LENGTH_LONG
                ).show()

                finish()
            }

            empresaIdActual != empresaId -> {

                empresaIdActual =
                    empresaId

                empresaNombreActual =
                    empresaNombre

                binding.tvEmpresa.text =
                    empresaNombre.ifBlank {
                        empresaId
                    }

                escucharVehiculos(
                    empresaId
                )
            }
        }
    }

    private fun escucharVehiculos(
        empresaId: String
    ) {

        listenerVehiculos?.remove()

        listenerVehiculos = firestore
            .collection(
                "vehiculospasajes"
            )
            .whereEqualTo(
                "empresaId",
                empresaId
            )
            .addSnapshotListener {
                    resultado, error ->

                mostrarCargando(false)

                if (error != null) {

                    Toast.makeText(
                        this,
                        "No se pudieron cargar los vehículos: " +
                                (
                                        error.localizedMessage
                                            ?: "error desconocido"
                                        ),
                        Toast.LENGTH_LONG
                    ).show()

                    return@addSnapshotListener
                }

                listaCompleta.clear()

                resultado?.documents
                    ?.mapNotNull { documento ->

                        convertirVehiculo(
                            documento
                        )
                    }
                    ?.filter { vehiculo ->

                        vehiculo.estado !=
                                "eliminado"
                    }
                    ?.sortedBy { vehiculo ->

                        vehiculo.placa
                    }
                    ?.let { vehiculos ->

                        listaCompleta.addAll(
                            vehiculos
                        )
                    }

                actualizarContadores()

                aplicarFiltro(
                    binding.etBuscar.text
                        ?.toString()
                        .orEmpty()
                )
            }
    }

    private fun convertirVehiculo(
        documento: DocumentSnapshot
    ): Vehiculo {

        return Vehiculo(

            id =
                documento.id,

            empresaId =
                textoDocumento(
                    documento,
                    "empresaId"
                ),

            empresaNombre =
                textoDocumento(
                    documento,
                    "empresaNombre"
                ),

            placa =
                textoDocumento(
                    documento,
                    "placa"
                ),

            tipo =
                textoDocumento(
                    documento,
                    "tipo"
                ),

            marca =
                textoDocumento(
                    documento,
                    "marca"
                ),

            modelo =
                textoDocumento(
                    documento,
                    "modelo"
                ),

            color =
                textoDocumento(
                    documento,
                    "color"
                ),

            anio =
                enteroDocumento(
                    documento,
                    "anio",
                    0
                ),

            capacidad =
                enteroDocumento(
                    documento,
                    "capacidad",
                    4
                ),

            estado =
                textoDocumento(
                    documento,
                    "estado"
                ).ifBlank {
                    "activo"
                },

            disponible =
                booleanoDocumento(
                    documento,
                    "disponible",
                    false
                ),

            disponibilidad =
                textoDocumento(
                    documento,
                    "disponibilidad"
                ),

            viajeActualId =
                textoDocumento(
                    documento,
                    "viajeActualId"
                ),

            choferActualUid =
                textoDocumento(
                    documento,
                    "choferActualUid"
                )
        )
    }

    private fun actualizarContadores() {

        binding.tvTotal.text =
            listaCompleta.size.toString()

        binding.tvDisponibles.text =
            listaCompleta.count { vehiculo ->

                vehiculo.estado == "activo" &&
                        vehiculo.disponible
            }.toString()

        binding.tvMantenimiento.text =
            listaCompleta.count { vehiculo ->

                vehiculo.estado ==
                        "mantenimiento"
            }.toString()
    }

    private fun aplicarFiltro(
        busqueda: String
    ) {

        val consulta =
            busqueda
                .trim()
                .lowercase(Locale.ROOT)

        val filtrados =
            if (consulta.isBlank()) {

                listaCompleta.toList()

            } else {

                listaCompleta.filter { vehiculo ->

                    vehiculo.placa
                        .lowercase(Locale.ROOT)
                        .contains(consulta) ||

                            vehiculo.tipo
                                .lowercase(Locale.ROOT)
                                .contains(consulta) ||

                            vehiculo.marca
                                .lowercase(Locale.ROOT)
                                .contains(consulta) ||

                            vehiculo.modelo
                                .lowercase(Locale.ROOT)
                                .contains(consulta)
                }
            }

        adaptador.actualizarLista(
            filtrados
        )

        binding.contenedorVacio.visibility =
            if (filtrados.isEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.recyclerVehiculos.visibility =
            if (filtrados.isEmpty()) {
                View.GONE
            } else {
                View.VISIBLE
            }

        binding.tvMensajeVacio.text =
            if (
                consulta.isNotBlank() &&
                listaCompleta.isNotEmpty()
            ) {

                "No se encontraron vehículos."

            } else {

                "Todavía no tienes vehículos registrados."
            }
    }

    private fun mostrarFormularioVehiculo(
        vehiculo: Vehiculo?
    ) {

        val formulario =
            DialogVehiculoBinding.inflate(
                layoutInflater
            )

        val tipos =
            listOf(
                "Auto",
                "Minivan",
                "Van",
                "Combi",
                "Bus"
            )

        formulario.actTipo.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout
                    .simple_dropdown_item_1line,
                tipos
            )
        )

        if (vehiculo == null) {

            formulario.etAnio.setText(
                Calendar.getInstance()
                    .get(Calendar.YEAR)
                    .toString()
            )

            formulario.etCapacidad.setText(
                "4"
            )

            formulario.switchDisponible.isChecked =
                true

        } else {

            formulario.etPlaca.setText(
                vehiculo.placa
            )

            formulario.actTipo.setText(
                vehiculo.tipo,
                false
            )

            formulario.etMarca.setText(
                vehiculo.marca
            )

            formulario.etModelo.setText(
                vehiculo.modelo
            )

            formulario.etColor.setText(
                vehiculo.color
            )

            formulario.etAnio.setText(
                vehiculo.anio.toString()
            )

            formulario.etCapacidad.setText(
                vehiculo.capacidad.toString()
            )

            formulario.switchDisponible.isChecked =
                vehiculo.disponible

            formulario.switchDisponible.isEnabled =
                vehiculo.estado == "activo"
        }

        val constructor =
            MaterialAlertDialogBuilder(this)
                .setTitle(
                    if (vehiculo == null) {
                        "Registrar vehículo"
                    } else {
                        "Editar vehículo"
                    }
                )
                .setView(formulario.root)
                .setNegativeButton(
                    "CANCELAR",
                    null
                )
                .setPositiveButton(
                    "GUARDAR",
                    null
                )

        if (vehiculo != null) {

            constructor.setNeutralButton(
                "ELIMINAR",
                null
            )
        }

        val dialogo =
            constructor.create()

        dialogo.setOnShowListener {

            val botonGuardar =
                dialogo.getButton(
                    AlertDialog.BUTTON_POSITIVE
                )

            botonGuardar.setOnClickListener {

                validarYGuardarVehiculo(
                    formulario = formulario,
                    dialogo = dialogo,
                    vehiculoExistente = vehiculo
                )
            }

            if (vehiculo != null) {

                dialogo.getButton(
                    AlertDialog.BUTTON_NEUTRAL
                ).setOnClickListener {

                    confirmarEliminarVehiculo(
                        vehiculo = vehiculo,
                        dialogoFormulario = dialogo
                    )
                }
            }
        }

        dialogo.show()
    }

    private fun validarYGuardarVehiculo(
        formulario: DialogVehiculoBinding,
        dialogo: AlertDialog,
        vehiculoExistente: Vehiculo?
    ) {

        limpiarErrores(formulario)

        val placa =
            formulario.etPlaca.text
                ?.toString()
                ?.trim()
                ?.uppercase(Locale.ROOT)
                .orEmpty()

        val tipo =
            formulario.actTipo.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val marca =
            formulario.etMarca.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val modelo =
            formulario.etModelo.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val color =
            formulario.etColor.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val anio =
            formulario.etAnio.text
                ?.toString()
                ?.toIntOrNull()

        val capacidad =
            formulario.etCapacidad.text
                ?.toString()
                ?.toIntOrNull()

        val anioActual =
            Calendar.getInstance()
                .get(Calendar.YEAR)

        when {

            placa.length < 5 -> {

                formulario.tilPlaca.error =
                    "Ingresa una placa válida."
            }

            tipo.isBlank() -> {

                formulario.tilTipo.error =
                    "Selecciona el tipo de vehículo."
            }

            marca.length < 2 -> {

                formulario.tilMarca.error =
                    "Ingresa la marca."
            }

            modelo.isBlank() -> {

                formulario.tilModelo.error =
                    "Ingresa el modelo."
            }

            color.length < 3 -> {

                formulario.tilColor.error =
                    "Ingresa el color."
            }

            anio == null ||
                    anio < 1980 ||
                    anio > anioActual + 1 -> {

                formulario.tilAnio.error =
                    "Ingresa un año válido."
            }

            capacidad == null ||
                    capacidad < 1 ||
                    capacidad > 60 -> {

                formulario.tilCapacidad.error =
                    "La capacidad debe estar entre 1 y 60."
            }

            else -> {

                comprobarPlacaYGuardar(
                    formulario = formulario,
                    dialogo = dialogo,
                    vehiculoExistente =
                        vehiculoExistente,
                    placa = placa,
                    tipo = tipo,
                    marca = marca,
                    modelo = modelo,
                    color = color,
                    anio = anio,
                    capacidad = capacidad,
                    disponible =
                        formulario
                            .switchDisponible
                            .isChecked
                )
            }
        }
    }

    private fun comprobarPlacaYGuardar(
        formulario: DialogVehiculoBinding,
        dialogo: AlertDialog,
        vehiculoExistente: Vehiculo?,
        placa: String,
        tipo: String,
        marca: String,
        modelo: String,
        color: String,
        anio: Int,
        capacidad: Int,
        disponible: Boolean
    ) {

        mostrarCargando(true)

        firestore
            .collection(
                "vehiculospasajes"
            )
            .whereEqualTo(
                "empresaId",
                empresaIdActual
            )
            .get()
            .addOnSuccessListener { resultado ->

                val placaNormalizada =
                    normalizarPlaca(placa)

                val repetida =
                    resultado.documents.any {
                            documento ->

                        val mismaPlaca =
                            normalizarPlaca(
                                textoDocumento(
                                    documento,
                                    "placa"
                                )
                            ) == placaNormalizada

                        val otroDocumento =
                            documento.id !=
                                    vehiculoExistente?.id

                        val noEliminado =
                            textoDocumento(
                                documento,
                                "estado"
                            ) != "eliminado"

                        mismaPlaca &&
                                otroDocumento &&
                                noEliminado
                    }

                if (repetida) {

                    mostrarCargando(false)

                    formulario.tilPlaca.error =
                        "Esta placa ya está registrada."

                    return@addOnSuccessListener
                }

                guardarVehiculo(
                    dialogo = dialogo,
                    vehiculoExistente =
                        vehiculoExistente,
                    placa = placa,
                    tipo = tipo,
                    marca = marca,
                    modelo = modelo,
                    color = color,
                    anio = anio,
                    capacidad = capacidad,
                    disponible = disponible
                )
            }
            .addOnFailureListener { error ->

                mostrarCargando(false)

                Toast.makeText(
                    this,
                    "No se pudo comprobar la placa: " +
                            error.localizedMessage,
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun guardarVehiculo(
        dialogo: AlertDialog,
        vehiculoExistente: Vehiculo?,
        placa: String,
        tipo: String,
        marca: String,
        modelo: String,
        color: String,
        anio: Int,
        capacidad: Int,
        disponible: Boolean
    ) {

        val usuario =
            auth.currentUser
                ?: return

        val referencia =
            if (vehiculoExistente == null) {

                firestore
                    .collection(
                        "vehiculospasajes"
                    )
                    .document()

            } else {

                firestore
                    .collection(
                        "vehiculospasajes"
                    )
                    .document(
                        vehiculoExistente.id
                    )
            }

        val estado =
            vehiculoExistente
                ?.estado
                ?.ifBlank {
                    "activo"
                }
                ?: "activo"

        val disponibleFinal =
            estado == "activo" &&
                    disponible

        val datos =
            hashMapOf<String, Any>(

                "vehiculoId" to
                        referencia.id,

                "empresaId" to
                        empresaIdActual,

                "empresaNombre" to
                        empresaNombreActual,

                "placa" to
                        placa,

                "placaBusqueda" to
                        normalizarPlaca(
                            placa
                        ),

                "tipo" to
                        tipo,

                "marca" to
                        marca,

                "modelo" to
                        modelo,

                "color" to
                        color,

                "anio" to
                        anio,

                "capacidad" to
                        capacidad,

                "estado" to
                        estado,

                "disponible" to
                        disponibleFinal,

                "disponibilidad" to
                        if (disponibleFinal) {
                            "disponible"
                        } else {
                            "no_disponible"
                        },

                "fechaActualizacion" to
                        FieldValue.serverTimestamp()
            )

        if (vehiculoExistente == null) {

            datos["viajeActualId"] = ""
            datos["choferActualUid"] = ""

            datos["creadoPorUid"] =
                usuario.uid

            datos["fechaRegistro"] =
                FieldValue.serverTimestamp()
        }

        referencia
            .set(
                datos,
                SetOptions.merge()
            )
            .addOnSuccessListener {

                mostrarCargando(false)
                dialogo.dismiss()

                Toast.makeText(
                    this,
                    if (vehiculoExistente == null) {
                        "Vehículo registrado correctamente."
                    } else {
                        "Vehículo actualizado correctamente."
                    },
                    Toast.LENGTH_LONG
                ).show()
            }
            .addOnFailureListener { error ->

                mostrarCargando(false)

                Toast.makeText(
                    this,
                    "No se pudo guardar el vehículo: " +
                            (
                                    error.localizedMessage
                                        ?: "error desconocido"
                                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun mostrarDialogoEstado(
        vehiculo: Vehiculo
    ) {

        val opciones =
            arrayOf(
                "Activo y disponible",
                "Activo no disponible",
                "En mantenimiento",
                "Inactivo"
            )

        var seleccion =
            when {

                vehiculo.estado ==
                        "mantenimiento" -> 2

                vehiculo.estado ==
                        "inactivo" -> 3

                vehiculo.disponible -> 0

                else -> 1
            }

        MaterialAlertDialogBuilder(this)
            .setTitle(
                "Estado del vehículo ${vehiculo.placa}"
            )
            .setSingleChoiceItems(
                opciones,
                seleccion
            ) { _, posicion ->

                seleccion =
                    posicion
            }
            .setNegativeButton(
                "CANCELAR",
                null
            )
            .setPositiveButton(
                "GUARDAR"
            ) { _, _ ->

                if (
                    vehiculo.viajeActualId.isNotBlank() &&
                    seleccion >= 2
                ) {

                    Toast.makeText(
                        this,
                        "El vehículo tiene un viaje activo y no puede desactivarse.",
                        Toast.LENGTH_LONG
                    ).show()

                    return@setPositiveButton
                }

                actualizarEstadoVehiculo(
                    vehiculo,
                    seleccion
                )
            }
            .show()
    }

    private fun actualizarEstadoVehiculo(
        vehiculo: Vehiculo,
        seleccion: Int
    ) {

        val estado: String
        val disponible: Boolean

        when (seleccion) {

            0 -> {
                estado = "activo"
                disponible = true
            }

            1 -> {
                estado = "activo"
                disponible = false
            }

            2 -> {
                estado = "mantenimiento"
                disponible = false
            }

            else -> {
                estado = "inactivo"
                disponible = false
            }
        }

        mostrarCargando(true)

        firestore
            .collection(
                "vehiculospasajes"
            )
            .document(
                vehiculo.id
            )
            .update(
                mapOf(
                    "estado" to estado,

                    "disponible" to
                            disponible,

                    "disponibilidad" to
                            if (disponible) {
                                "disponible"
                            } else {
                                "no_disponible"
                            },

                    "fechaActualizacion" to
                            FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {

                mostrarCargando(false)

                Toast.makeText(
                    this,
                    "Estado actualizado.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { error ->

                mostrarCargando(false)

                Toast.makeText(
                    this,
                    "No se pudo actualizar: " +
                            error.localizedMessage,
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun confirmarEliminarVehiculo(
        vehiculo: Vehiculo,
        dialogoFormulario: AlertDialog
    ) {

        if (vehiculo.viajeActualId.isNotBlank()) {

            Toast.makeText(
                this,
                "El vehículo tiene un viaje activo y no puede eliminarse.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(
                "Eliminar vehículo"
            )
            .setMessage(
                "¿Deseas eliminar el vehículo ${vehiculo.placa}?"
            )
            .setNegativeButton(
                "CANCELAR",
                null
            )
            .setPositiveButton(
                "ELIMINAR"
            ) { _, _ ->

                eliminarVehiculo(
                    vehiculo,
                    dialogoFormulario
                )
            }
            .show()
    }

    private fun eliminarVehiculo(
        vehiculo: Vehiculo,
        dialogoFormulario: AlertDialog
    ) {

        mostrarCargando(true)

        firestore
            .collection(
                "vehiculospasajes"
            )
            .document(
                vehiculo.id
            )
            .update(
                mapOf(
                    "estado" to
                            "eliminado",

                    "disponible" to
                            false,

                    "disponibilidad" to
                            "no_disponible",

                    "fechaEliminacion" to
                            FieldValue.serverTimestamp(),

                    "fechaActualizacion" to
                            FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {

                mostrarCargando(false)
                dialogoFormulario.dismiss()

                Toast.makeText(
                    this,
                    "Vehículo eliminado.",
                    Toast.LENGTH_LONG
                ).show()
            }
            .addOnFailureListener { error ->

                mostrarCargando(false)

                Toast.makeText(
                    this,
                    "No se pudo eliminar: " +
                            error.localizedMessage,
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun limpiarErrores(
        formulario: DialogVehiculoBinding
    ) {

        formulario.tilPlaca.error = null
        formulario.tilTipo.error = null
        formulario.tilMarca.error = null
        formulario.tilModelo.error = null
        formulario.tilColor.error = null
        formulario.tilAnio.error = null
        formulario.tilCapacidad.error = null
    }

    private fun normalizarPlaca(
        placa: String
    ): String {

        return placa
            .replace(" ", "")
            .replace("-", "")
            .uppercase(Locale.ROOT)
    }

    private fun textoDocumento(
        documento: DocumentSnapshot,
        campo: String
    ): String {

        return documento
            .get(campo)
            ?.toString()
            ?.trim()
            .orEmpty()
    }

    private fun enteroDocumento(
        documento: DocumentSnapshot,
        campo: String,
        predeterminado: Int
    ): Int {

        return when (
            val valor = documento.get(campo)
        ) {

            is Number -> {
                valor.toInt()
            }

            is String -> {
                valor.toIntOrNull()
                    ?: predeterminado
            }

            else -> {
                predeterminado
            }
        }
    }

    private fun booleanoDocumento(
        documento: DocumentSnapshot,
        campo: String,
        predeterminado: Boolean
    ): Boolean {

        return when (
            val valor = documento.get(campo)
        ) {

            is Boolean -> {
                valor
            }

            is String -> {
                valor.equals(
                    "true",
                    ignoreCase = true
                )
            }

            is Number -> {
                valor.toInt() != 0
            }

            else -> {
                predeterminado
            }
        }
    }

    private fun mostrarCargando(
        mostrar: Boolean
    ) {

        binding.contenedorCargando.visibility =
            if (mostrar) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.fabNuevoVehiculo.isEnabled =
            !mostrar
    }

    override fun onStop() {

        listenerPerfil?.remove()
        listenerPerfil = null

        listenerVehiculos?.remove()
        listenerVehiculos = null

        super.onStop()
    }
}