package com.pasajesya.adminya

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.pasajesya.adminya.databinding.ActivityAdministrarChoferesBinding
import com.pasajesya.adminya.databinding.DialogEditarChoferBinding
import java.util.Calendar
import java.util.Locale
import com.pasajesya.adminya.databinding.DialogSeleccionarVehiculoBinding

class AdministrarChoferesActivity : AppCompatActivity() {
    private lateinit var binding:
            ActivityAdministrarChoferesBinding

    private lateinit var auth:
            FirebaseAuth

    private lateinit var firestore:
            FirebaseFirestore

    private lateinit var adaptador:
            ChoferAdminAdapter

    private var listenerPerfil:
            ListenerRegistration? = null

    private var listenerChoferes:
            ListenerRegistration? = null

    private var empresaIdActual = ""
    private var empresaNombreActual = ""

    private val listaCompleta =
        mutableListOf<ChoferAdmin>()

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        binding =
            ActivityAdministrarChoferesBinding.inflate(
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

        /*
         * Reiniciamos el ID para volver a colocar
         * los listeners cuando regresemos desde
         * CrearChoferActivity.
         */
        empresaIdActual = ""

        removerListeners()
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
            ChoferAdminAdapter(

                alEditar = { chofer ->

                    mostrarFormularioEditar(
                        chofer
                    )
                },

                alAsignarVehiculo = { chofer ->

                    cargarVehiculosParaAsignar(
                        chofer
                    )
                },

                alCambiarEstado = { chofer ->

                    mostrarDialogoEstado(
                        chofer
                    )
                }
            )

        binding.recyclerChoferes.layoutManager =
            LinearLayoutManager(this)

        binding.recyclerChoferes.adapter =
            adaptador
    }

    private fun configurarEventos() {

        binding.btnVolver.setOnClickListener {
            finish()
        }

        binding.fabNuevoChofer
            .setOnClickListener {

                val intent = Intent(
                    this,
                    CrearChoferActivity::class.java
                )

                startActivity(intent)
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
                        texto
                            ?.toString()
                            .orEmpty()
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

            abrirLogin()
            return
        }

        mostrarCargando(true)

        listenerPerfil = firestore
            .collection(
                COLECCION_USUARIOS_GESTION
            )
            .document(
                usuario.uid
            )
            .addSnapshotListener {
                    documento, error ->

                if (error != null) {

                    mostrarCargando(false)

                    Toast.makeText(
                        this,
                        "No se pudo cargar el propietario: " +
                                (
                                        error.localizedMessage
                                            ?: "error desconocido"
                                        ),
                        Toast.LENGTH_LONG
                    ).show()

                    return@addSnapshotListener
                }

                if (
                    documento == null ||
                    !documento.exists()
                ) {

                    mostrarCargando(false)

                    Toast.makeText(
                        this,
                        "No se encontró el perfil del propietario.",
                        Toast.LENGTH_LONG
                    ).show()

                    finish()

                    return@addSnapshotListener
                }

                procesarPerfilPropietario(
                    documento
                )
            }
    }

    private fun procesarPerfilPropietario(
        documento: DocumentSnapshot
    ) {

        val rol =
            textoDocumento(
                documento,
                CAMPO_ROL
            ).lowercase(Locale.ROOT)

        val estado =
            textoDocumento(
                documento,
                CAMPO_ESTADO
            ).lowercase(Locale.ROOT)

        val empresaId =
            textoDocumento(
                documento,
                CAMPO_EMPRESA_ID
            )

        val empresaNombre =
            textoDocumento(
                documento,
                CAMPO_EMPRESA_NOMBRE
            )

        when {

            rol != ROL_PROPIETARIO -> {

                mostrarCargando(false)

                Toast.makeText(
                    this,
                    "Esta cuenta no pertenece a un propietario.",
                    Toast.LENGTH_LONG
                ).show()

                finish()
            }

            estado != ESTADO_ACTIVO -> {

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

                escucharChoferes(
                    empresaId
                )
            }
        }
    }

    private fun escucharChoferes(
        empresaId: String
    ) {

        listenerChoferes?.remove()
        listenerChoferes = null

        listenerChoferes = firestore
            .collection(
                COLECCION_CHOFERES
            )
            .whereEqualTo(
                CAMPO_EMPRESA_ID,
                empresaId
            )
            .addSnapshotListener {
                    resultado, error ->

                mostrarCargando(false)

                if (error != null) {

                    Toast.makeText(
                        this,
                        "No se pudieron cargar los choferes: " +
                                (
                                        error.localizedMessage
                                            ?: "error desconocido"
                                        ),
                        Toast.LENGTH_LONG
                    ).show()

                    return@addSnapshotListener
                }

                listaCompleta.clear()

                resultado
                    ?.documents
                    ?.map { documento ->

                        convertirChofer(
                            documento
                        )
                    }
                    ?.filter { chofer ->

                        chofer.estado !=
                                ESTADO_ELIMINADO
                    }
                    ?.sortedBy { chofer ->

                        chofer
                            .obtenerNombreCompleto()
                            .lowercase(Locale.ROOT)
                    }
                    ?.let { choferes ->

                        listaCompleta.addAll(
                            choferes
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

    private fun convertirChofer(
        documento: DocumentSnapshot
    ): ChoferAdmin {

        return ChoferAdmin(

            uid =
                documento.id,

            empresaId =
                textoDocumento(
                    documento,
                    CAMPO_EMPRESA_ID
                ),

            empresaNombre =
                textoDocumento(
                    documento,
                    CAMPO_EMPRESA_NOMBRE
                ),

            nombres =
                textoDocumento(
                    documento,
                    CAMPO_NOMBRES
                ),

            apellidos =
                textoDocumento(
                    documento,
                    CAMPO_APELLIDOS
                ),

            nombreCompleto =
                textoDocumento(
                    documento,
                    CAMPO_NOMBRE_COMPLETO
                ),

            dni =
                textoDocumento(
                    documento,
                    CAMPO_DNI
                ),

            celular =
                textoDocumento(
                    documento,
                    CAMPO_CELULAR
                ),

            correo =
                textoDocumento(
                    documento,
                    CAMPO_CORREO
                ),

            licencia =
                textoDocumento(
                    documento,
                    CAMPO_LICENCIA
                ),

            categoriaLicencia =
                textoDocumento(
                    documento,
                    CAMPO_CATEGORIA_LICENCIA
                ),

            vencimientoLicencia =
                textoDocumento(
                    documento,
                    CAMPO_VENCIMIENTO_LICENCIA
                ),

            estado =
                textoDocumento(
                    documento,
                    CAMPO_ESTADO
                ).ifBlank {
                    ESTADO_ACTIVO
                },

            disponible =
                booleanoDocumento(
                    documento,
                    CAMPO_DISPONIBLE,
                    false
                ),

            disponibilidad =
                textoDocumento(
                    documento,
                    CAMPO_DISPONIBILIDAD
                ),

            vehiculoId =
                textoDocumento(
                    documento,
                    CAMPO_VEHICULO_ID
                ),

            vehiculoPlaca =
                textoDocumento(
                    documento,
                    CAMPO_VEHICULO_PLACA
                ),

            vehiculoDescripcion =
                textoDocumento(
                    documento,
                    CAMPO_VEHICULO_DESCRIPCION
                ),

            viajeActualId =
                textoDocumento(
                    documento,
                    CAMPO_VIAJE_ACTUAL_ID
                )
        )
    }

    private fun actualizarContadores() {

        binding.tvTotal.text =
            listaCompleta.size.toString()

        binding.tvActivos.text =
            listaCompleta.count { chofer ->

                chofer.estado ==
                        ESTADO_ACTIVO
            }.toString()

        binding.tvDisponibles.text =
            listaCompleta.count { chofer ->

                chofer.estado ==
                        ESTADO_ACTIVO &&
                        chofer.disponible &&
                        chofer.viajeActualId.isBlank()
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

                listaCompleta.filter { chofer ->

                    chofer.obtenerNombreCompleto()
                        .lowercase(Locale.ROOT)
                        .contains(consulta) ||

                            chofer.dni
                                .lowercase(Locale.ROOT)
                                .contains(consulta) ||

                            chofer.celular
                                .lowercase(Locale.ROOT)
                                .contains(consulta) ||

                            chofer.correo
                                .lowercase(Locale.ROOT)
                                .contains(consulta) ||

                            chofer.licencia
                                .lowercase(Locale.ROOT)
                                .contains(consulta) ||

                            chofer.vehiculoPlaca
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

        binding.recyclerChoferes.visibility =
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

                "No se encontraron choferes."

            } else {

                "Todavía no tienes choferes registrados."
            }
    }

    // -------------------------------------------------
    // EDITAR CHOFER
    // -------------------------------------------------

    private fun mostrarFormularioEditar(
        chofer: ChoferAdmin
    ) {

        val formulario =
            DialogEditarChoferBinding.inflate(
                layoutInflater
            )

        val categorias =
            listOf(
                "A-I",
                "A-IIA",
                "A-IIB",
                "A-IIIA",
                "A-IIIB",
                "A-IIIC"
            )

        formulario.actCategoriaLicencia
            .setAdapter(
                ArrayAdapter(
                    this,
                    android.R.layout
                        .simple_dropdown_item_1line,
                    categorias
                )
            )

        formulario.tvCorreoCuenta.text =
            "Cuenta: ${chofer.correo}"

        formulario.etNombres.setText(
            chofer.nombres
        )

        formulario.etApellidos.setText(
            chofer.apellidos
        )

        formulario.etDni.setText(
            chofer.dni
        )

        formulario.etCelular.setText(
            chofer.celular
        )

        formulario.etLicencia.setText(
            chofer.licencia
        )

        formulario.actCategoriaLicencia
            .setText(
                chofer.categoriaLicencia,
                false
            )

        formulario.etVencimientoLicencia
            .setText(
                chofer.vencimientoLicencia
            )

        formulario.etVencimientoLicencia
            .setOnClickListener {

                mostrarSelectorFecha(
                    formulario
                )
            }

        val dialogo =
            MaterialAlertDialogBuilder(this)
                .setTitle(
                    "Editar chofer"
                )
                .setView(
                    formulario.root
                )
                .setNegativeButton(
                    "CANCELAR",
                    null
                )
                .setPositiveButton(
                    "GUARDAR",
                    null
                )
                .create()

        dialogo.setOnShowListener {

            dialogo.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {

                validarEdicionChofer(
                    chofer = chofer,
                    formulario = formulario,
                    dialogo = dialogo
                )
            }
        }

        dialogo.show()
    }

    private fun mostrarSelectorFecha(
        formulario: DialogEditarChoferBinding
    ) {

        val calendario =
            Calendar.getInstance()

        val selector =
            DatePickerDialog(
                this,
                {
                        _, anio, mes, dia ->

                    val fecha =
                        String.format(
                            Locale.ROOT,
                            "%02d/%02d/%04d",
                            dia,
                            mes + 1,
                            anio
                        )

                    formulario
                        .etVencimientoLicencia
                        .setText(
                            fecha
                        )

                    formulario
                        .tilVencimientoLicencia
                        .error = null
                },
                calendario.get(
                    Calendar.YEAR
                ),
                calendario.get(
                    Calendar.MONTH
                ),
                calendario.get(
                    Calendar.DAY_OF_MONTH
                )
            )

        selector.show()
    }

    private fun validarEdicionChofer(
        chofer: ChoferAdmin,
        formulario: DialogEditarChoferBinding,
        dialogo: AlertDialog
    ) {

        limpiarErrores(
            formulario
        )

        val nombres =
            formulario.etNombres.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val apellidos =
            formulario.etApellidos.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val dni =
            formulario.etDni.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val celular =
            formulario.etCelular.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val licencia =
            formulario.etLicencia.text
                ?.toString()
                ?.trim()
                ?.uppercase(Locale.ROOT)
                .orEmpty()

        val categoria =
            formulario.actCategoriaLicencia.text
                ?.toString()
                ?.trim()
                ?.uppercase(Locale.ROOT)
                .orEmpty()

        val vencimiento =
            formulario.etVencimientoLicencia.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val dniRepetido =
            listaCompleta.any { otroChofer ->

                otroChofer.uid != chofer.uid &&
                        otroChofer.dni == dni &&
                        otroChofer.estado !=
                        ESTADO_ELIMINADO
            }

        when {

            nombres.length < 2 -> {

                formulario.tilNombres.error =
                    "Ingresa los nombres."
            }

            apellidos.length < 2 -> {

                formulario.tilApellidos.error =
                    "Ingresa los apellidos."
            }

            dni.length != 8 -> {

                formulario.tilDni.error =
                    "El DNI debe tener 8 dígitos."
            }

            dniRepetido -> {

                formulario.tilDni.error =
                    "Ya existe otro chofer con este DNI."
            }

            celular.length != 9 -> {

                formulario.tilCelular.error =
                    "El celular debe tener 9 dígitos."
            }

            licencia.length < 5 -> {

                formulario.tilLicencia.error =
                    "Ingresa una licencia válida."
            }

            categoria.isBlank() -> {

                formulario
                    .tilCategoriaLicencia
                    .error =
                    "Selecciona una categoría."
            }

            vencimiento.length != 10 -> {

                formulario
                    .tilVencimientoLicencia
                    .error =
                    "Selecciona el vencimiento."
            }

            else -> {

                guardarCambiosChofer(
                    chofer = chofer,
                    dialogo = dialogo,
                    nombres = nombres,
                    apellidos = apellidos,
                    dni = dni,
                    celular = celular,
                    licencia = licencia,
                    categoria = categoria,
                    vencimiento = vencimiento
                )
            }
        }
    }

    private fun guardarCambiosChofer(
        chofer: ChoferAdmin,
        dialogo: AlertDialog,
        nombres: String,
        apellidos: String,
        dni: String,
        celular: String,
        licencia: String,
        categoria: String,
        vencimiento: String
    ) {

        mostrarCargando(true)

        val referenciaChofer =
            firestore
                .collection(
                    COLECCION_CHOFERES
                )
                .document(
                    chofer.uid
                )

        val nombreCompleto =
            "$nombres $apellidos".trim()

        firestore
            .runTransaction { transaccion ->

                /*
                 * Primero realizamos todas las lecturas.
                 */
                val documentoChofer =
                    transaccion.get(
                        referenciaChofer
                    )

                if (!documentoChofer.exists()) {

                    throw IllegalStateException(
                        "No se encontró el chofer."
                    )
                }

                val empresaChofer =
                    textoDocumento(
                        documentoChofer,
                        CAMPO_EMPRESA_ID
                    )

                if (
                    empresaChofer !=
                    empresaIdActual
                ) {

                    throw IllegalStateException(
                        "El chofer pertenece a otra empresa."
                    )
                }

                val vehiculoId =
                    textoDocumento(
                        documentoChofer,
                        CAMPO_VEHICULO_ID
                    )

                val referenciaVehiculo =
                    if (vehiculoId.isNotBlank()) {

                        firestore
                            .collection(
                                COLECCION_VEHICULOS
                            )
                            .document(
                                vehiculoId
                            )

                    } else {

                        null
                    }

                val documentoVehiculo =
                    referenciaVehiculo?.let {
                            referencia ->

                        transaccion.get(
                            referencia
                        )
                    }

                /*
                 * Después de las lecturas,
                 * realizamos las escrituras.
                 */
                transaccion.update(
                    referenciaChofer,
                    mapOf(
                        CAMPO_NOMBRES to nombres,
                        CAMPO_APELLIDOS to apellidos,

                        CAMPO_NOMBRE_COMPLETO to
                                nombreCompleto,

                        CAMPO_DNI to dni,
                        CAMPO_CELULAR to celular,

                        CAMPO_LICENCIA to licencia,

                        CAMPO_CATEGORIA_LICENCIA to
                                categoria,

                        CAMPO_VENCIMIENTO_LICENCIA to
                                vencimiento,

                        CAMPO_FECHA_ACTUALIZACION to
                                FieldValue.serverTimestamp()
                    )
                )

                if (
                    referenciaVehiculo != null &&
                    documentoVehiculo != null &&
                    documentoVehiculo.exists()
                ) {

                    val choferAsignado =
                        textoDocumento(
                            documentoVehiculo,
                            CAMPO_CHOFER_ACTUAL_UID
                        )

                    if (
                        choferAsignado ==
                        chofer.uid
                    ) {

                        transaccion.update(
                            referenciaVehiculo,
                            mapOf(
                                CAMPO_CHOFER_ACTUAL_NOMBRE to
                                        nombreCompleto,

                                CAMPO_FECHA_ACTUALIZACION to
                                        FieldValue.serverTimestamp()
                            )
                        )
                    }
                }

                true
            }
            .addOnSuccessListener {

                mostrarCargando(false)
                dialogo.dismiss()

                Toast.makeText(
                    this,
                    "Datos del chofer actualizados.",
                    Toast.LENGTH_LONG
                ).show()
            }
            .addOnFailureListener { error ->

                mostrarCargando(false)

                Toast.makeText(
                    this,
                    "No se pudo actualizar el chofer: " +
                            (
                                    error.localizedMessage
                                        ?: "error desconocido"
                                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // -------------------------------------------------
    // ASIGNAR VEHÍCULO
    // -------------------------------------------------

    private fun cargarVehiculosParaAsignar(
        chofer: ChoferAdmin
    ) {

        if (chofer.estado != ESTADO_ACTIVO) {

            Toast.makeText(
                this,
                "Activa al chofer antes de asignarle un vehículo.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        if (chofer.viajeActualId.isNotBlank()) {

            Toast.makeText(
                this,
                "El chofer tiene un viaje activo y no puede cambiar de vehículo.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        mostrarCargando(true)

        firestore
            .collection(
                COLECCION_VEHICULOS
            )
            .whereEqualTo(
                CAMPO_EMPRESA_ID,
                empresaIdActual
            )
            .get()
            .addOnSuccessListener { resultado ->

                mostrarCargando(false)

                val vehiculos =
                    resultado.documents
                        .map { documento ->

                            convertirVehiculo(
                                documento
                            )
                        }
                        .filter { vehiculo ->

                            val esVehiculoActual =
                                vehiculo.id ==
                                        chofer.vehiculoId

                            val puedeAsignarse =
                                vehiculo.estado ==
                                        ESTADO_ACTIVO &&

                                        vehiculo.disponible &&

                                        vehiculo.viajeActualId
                                            .isBlank() &&

                                        (
                                                vehiculo
                                                    .choferActualUid
                                                    .isBlank() ||

                                                        vehiculo
                                                            .choferActualUid ==
                                                        chofer.uid
                                                )

                            esVehiculoActual ||
                                    puedeAsignarse
                        }
                        .sortedBy { vehiculo ->

                            vehiculo.placa
                        }

                mostrarDialogoVehiculos(
                    chofer,
                    vehiculos
                )
            }
            .addOnFailureListener { error ->

                mostrarCargando(false)

                Toast.makeText(
                    this,
                    "No se pudieron cargar los vehículos: " +
                            (
                                    error.localizedMessage
                                        ?: "error desconocido"
                                    ),
                    Toast.LENGTH_LONG
                ).show()
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
                    CAMPO_EMPRESA_ID
                ),

            empresaNombre =
                textoDocumento(
                    documento,
                    CAMPO_EMPRESA_NOMBRE
                ),

            placa =
                textoDocumento(
                    documento,
                    CAMPO_PLACA
                ),

            tipo =
                textoDocumento(
                    documento,
                    CAMPO_TIPO
                ),

            marca =
                textoDocumento(
                    documento,
                    CAMPO_MARCA
                ),

            modelo =
                textoDocumento(
                    documento,
                    CAMPO_MODELO
                ),

            color =
                textoDocumento(
                    documento,
                    CAMPO_COLOR
                ),

            anio =
                enteroDocumento(
                    documento,
                    CAMPO_ANIO,
                    0
                ),

            capacidad =
                enteroDocumento(
                    documento,
                    CAMPO_CAPACIDAD,
                    4
                ),

            estado =
                textoDocumento(
                    documento,
                    CAMPO_ESTADO
                ).ifBlank {
                    ESTADO_ACTIVO
                },

            disponible =
                booleanoDocumento(
                    documento,
                    CAMPO_DISPONIBLE,
                    false
                ),

            disponibilidad =
                textoDocumento(
                    documento,
                    CAMPO_DISPONIBILIDAD
                ),

            viajeActualId =
                textoDocumento(
                    documento,
                    CAMPO_VIAJE_ACTUAL_ID
                ),

            choferActualUid =
                textoDocumento(
                    documento,
                    CAMPO_CHOFER_ACTUAL_UID
                )
        )
    }

    private fun mostrarDialogoVehiculos(
        chofer: ChoferAdmin,
        vehiculos: List<Vehiculo>
    ) {

        val formulario =
            DialogSeleccionarVehiculoBinding.inflate(
                layoutInflater
            )

        var vehiculoSeleccionadoId =
            chofer.vehiculoId

        val adaptadorVehiculos =
            SeleccionarVehiculoAdapter(
                choferUidActual =
                    chofer.uid,

                alSeleccionar = { vehiculo ->

                    vehiculoSeleccionadoId =
                        vehiculo.id

                    formulario
                        .radioSinVehiculo
                        .isChecked = false

                    actualizarTarjetaSinVehiculo(
                        formulario = formulario,
                        seleccionada = false
                    )
                }
            )

        formulario
            .recyclerVehiculosAsignacion
            .layoutManager =
            LinearLayoutManager(this)

        formulario
            .recyclerVehiculosAsignacion
            .adapter =
            adaptadorVehiculos

        adaptadorVehiculos.establecerSeleccion(
            chofer.vehiculoId
        )

        formulario.radioSinVehiculo.isChecked =
            chofer.vehiculoId.isBlank()

        actualizarTarjetaSinVehiculo(
            formulario = formulario,
            seleccionada =
                chofer.vehiculoId.isBlank()
        )

        formulario.cardSinVehiculo
            .setOnClickListener {

                vehiculoSeleccionadoId = ""

                formulario.radioSinVehiculo
                    .isChecked = true

                adaptadorVehiculos
                    .establecerSeleccion("")

                actualizarTarjetaSinVehiculo(
                    formulario = formulario,
                    seleccionada = true
                )
            }

        fun aplicarFiltro(
            textoBusqueda: String
        ) {

            val consulta =
                textoBusqueda
                    .trim()
                    .lowercase(Locale.ROOT)

            val filtrados =
                if (consulta.isBlank()) {

                    vehiculos

                } else {

                    vehiculos.filter { vehiculo ->

                        vehiculo.placa
                            .lowercase(Locale.ROOT)
                            .contains(consulta) ||

                                vehiculo.marca
                                    .lowercase(Locale.ROOT)
                                    .contains(consulta) ||

                                vehiculo.modelo
                                    .lowercase(Locale.ROOT)
                                    .contains(consulta) ||

                                vehiculo.tipo
                                    .lowercase(Locale.ROOT)
                                    .contains(consulta) ||

                                vehiculo.color
                                    .lowercase(Locale.ROOT)
                                    .contains(consulta)
                    }
                }

            adaptadorVehiculos.actualizarLista(
                filtrados
            )

            formulario.tvResultados.text =
                when (filtrados.size) {

                    0 -> {
                        "0 vehículos"
                    }

                    1 -> {
                        "1 vehículo"
                    }

                    else -> {
                        "${filtrados.size} vehículos"
                    }
                }

            formulario.contenedorSinResultados.visibility =
                if (filtrados.isEmpty()) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

            formulario.recyclerVehiculosAsignacion.visibility =
                if (filtrados.isEmpty()) {
                    View.GONE
                } else {
                    View.VISIBLE
                }

            formulario.tvMensajeSinResultados.text =
                if (
                    consulta.isNotBlank()
                ) {

                    "No se encontraron vehículos."

                } else {

                    "No hay vehículos disponibles."
                }
        }

        formulario.etBuscarVehiculo
            .addTextChangedListener(

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
                            texto
                                ?.toString()
                                .orEmpty()
                        )
                    }

                    override fun afterTextChanged(
                        texto: Editable?
                    ) = Unit
                }
            )

        aplicarFiltro("")

        val dialogo =
            MaterialAlertDialogBuilder(this)
                .setTitle(
                    "Vehículo de " +
                            chofer.obtenerNombreCompleto()
                )
                .setView(
                    formulario.root
                )
                .setNegativeButton(
                    "CANCELAR",
                    null
                )
                .setPositiveButton(
                    "GUARDAR",
                    null
                )
                .create()

        dialogo.setOnShowListener {

            dialogo.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {

                if (
                    vehiculoSeleccionadoId ==
                    chofer.vehiculoId
                ) {

                    Toast.makeText(
                        this,
                        "El vehículo seleccionado ya está asignado.",
                        Toast.LENGTH_SHORT
                    ).show()

                    dialogo.dismiss()

                    return@setOnClickListener
                }

                val vehiculoSeleccionado =
                    vehiculos.firstOrNull {
                            vehiculo ->

                        vehiculo.id ==
                                vehiculoSeleccionadoId
                    }

                dialogo.dismiss()

                asignarVehiculoAlChofer(
                    chofer = chofer,
                    nuevoVehiculo =
                        vehiculoSeleccionado
                )
            }
        }

        dialogo.show()
    }

    private fun actualizarTarjetaSinVehiculo(
        formulario: DialogSeleccionarVehiculoBinding,
        seleccionada: Boolean
    ) {

        formulario.cardSinVehiculo.strokeWidth =
            if (seleccionada) {
                2
            } else {
                1
            }

        formulario.cardSinVehiculo.setStrokeColor(
            getColor(
                if (seleccionada) {
                    R.color.adminya_primary
                } else {
                    R.color.adminya_border
                }
            )
        )
    }


    private fun asignarVehiculoAlChofer(
        chofer: ChoferAdmin,
        nuevoVehiculo: Vehiculo?
    ) {

        mostrarCargando(true)

        val referenciaChofer =
            firestore
                .collection(
                    COLECCION_CHOFERES
                )
                .document(
                    chofer.uid
                )

        firestore
            .runTransaction { transaccion ->

                /*
                 * LECTURAS
                 */
                val documentoChofer =
                    transaccion.get(
                        referenciaChofer
                    )

                if (!documentoChofer.exists()) {

                    throw IllegalStateException(
                        "No se encontró el chofer."
                    )
                }

                val empresaChofer =
                    textoDocumento(
                        documentoChofer,
                        CAMPO_EMPRESA_ID
                    )

                if (
                    empresaChofer !=
                    empresaIdActual
                ) {

                    throw IllegalStateException(
                        "El chofer pertenece a otra empresa."
                    )
                }

                val viajeActualChofer =
                    textoDocumento(
                        documentoChofer,
                        CAMPO_VIAJE_ACTUAL_ID
                    )

                if (
                    viajeActualChofer.isNotBlank()
                ) {

                    throw IllegalStateException(
                        "El chofer tiene un viaje activo."
                    )
                }

                val vehiculoAnteriorId =
                    textoDocumento(
                        documentoChofer,
                        CAMPO_VEHICULO_ID
                    )

                val referenciaVehiculoAnterior =
                    if (
                        vehiculoAnteriorId.isNotBlank() &&
                        vehiculoAnteriorId !=
                        nuevoVehiculo?.id
                    ) {

                        firestore
                            .collection(
                                COLECCION_VEHICULOS
                            )
                            .document(
                                vehiculoAnteriorId
                            )

                    } else {

                        null
                    }

                val documentoVehiculoAnterior =
                    referenciaVehiculoAnterior
                        ?.let { referencia ->

                            transaccion.get(
                                referencia
                            )
                        }

                val referenciaNuevoVehiculo =
                    nuevoVehiculo?.let {
                            vehiculo ->

                        firestore
                            .collection(
                                COLECCION_VEHICULOS
                            )
                            .document(
                                vehiculo.id
                            )
                    }

                val documentoNuevoVehiculo =
                    referenciaNuevoVehiculo
                        ?.let { referencia ->

                            transaccion.get(
                                referencia
                            )
                        }

                /*
                 * VALIDACIONES
                 */
                if (
                    referenciaNuevoVehiculo != null
                ) {

                    if (
                        documentoNuevoVehiculo == null ||
                        !documentoNuevoVehiculo.exists()
                    ) {

                        throw IllegalStateException(
                            "El vehículo ya no existe."
                        )
                    }

                    val empresaVehiculo =
                        textoDocumento(
                            documentoNuevoVehiculo,
                            CAMPO_EMPRESA_ID
                        )

                    val estadoVehiculo =
                        textoDocumento(
                            documentoNuevoVehiculo,
                            CAMPO_ESTADO
                        )

                    val viajeVehiculo =
                        textoDocumento(
                            documentoNuevoVehiculo,
                            CAMPO_VIAJE_ACTUAL_ID
                        )

                    val otroChoferUid =
                        textoDocumento(
                            documentoNuevoVehiculo,
                            CAMPO_CHOFER_ACTUAL_UID
                        )

                    if (
                        empresaVehiculo !=
                        empresaIdActual
                    ) {

                        throw IllegalStateException(
                            "El vehículo pertenece a otra empresa."
                        )
                    }

                    if (
                        estadoVehiculo !=
                        ESTADO_ACTIVO
                    ) {

                        throw IllegalStateException(
                            "El vehículo no está activo."
                        )
                    }

                    if (viajeVehiculo.isNotBlank()) {

                        throw IllegalStateException(
                            "El vehículo tiene un viaje activo."
                        )
                    }

                    if (
                        otroChoferUid.isNotBlank() &&
                        otroChoferUid !=
                        chofer.uid
                    ) {

                        throw IllegalStateException(
                            "El vehículo ya está asignado a otro chofer."
                        )
                    }
                }

                /*
                 * ESCRITURAS
                 */

                // Limpiar el vehículo anterior.
                if (
                    referenciaVehiculoAnterior != null &&
                    documentoVehiculoAnterior != null &&
                    documentoVehiculoAnterior.exists()
                ) {

                    val uidAsignado =
                        textoDocumento(
                            documentoVehiculoAnterior,
                            CAMPO_CHOFER_ACTUAL_UID
                        )

                    if (
                        uidAsignado ==
                        chofer.uid
                    ) {

                        transaccion.update(
                            referenciaVehiculoAnterior,
                            mapOf(
                                CAMPO_CHOFER_ACTUAL_UID to
                                        "",

                                CAMPO_CHOFER_ACTUAL_NOMBRE to
                                        "",

                                CAMPO_FECHA_ACTUALIZACION to
                                        FieldValue.serverTimestamp()
                            )
                        )
                    }
                }

                val nombreChofer =
                    textoDocumento(
                        documentoChofer,
                        CAMPO_NOMBRE_COMPLETO
                    ).ifBlank {

                        val nombres =
                            textoDocumento(
                                documentoChofer,
                                CAMPO_NOMBRES
                            )

                        val apellidos =
                            textoDocumento(
                                documentoChofer,
                                CAMPO_APELLIDOS
                            )

                        "$nombres $apellidos".trim()
                    }

                if (
                    nuevoVehiculo == null
                ) {

                    transaccion.update(
                        referenciaChofer,
                        mapOf(
                            CAMPO_VEHICULO_ID to "",
                            CAMPO_VEHICULO_PLACA to "",

                            CAMPO_VEHICULO_DESCRIPCION to
                                    "",

                            CAMPO_FECHA_ACTUALIZACION to
                                    FieldValue.serverTimestamp()
                        )
                    )

                } else {

                    val descripcionVehiculo =
                        construirDescripcionVehiculo(
                            nuevoVehiculo
                        )

                    transaccion.update(
                        referenciaChofer,
                        mapOf(
                            CAMPO_VEHICULO_ID to
                                    nuevoVehiculo.id,

                            CAMPO_VEHICULO_PLACA to
                                    nuevoVehiculo.placa,

                            CAMPO_VEHICULO_DESCRIPCION to
                                    descripcionVehiculo,

                            CAMPO_FECHA_ACTUALIZACION to
                                    FieldValue.serverTimestamp()
                        )
                    )

                    transaccion.update(
                        referenciaNuevoVehiculo!!,
                        mapOf(
                            CAMPO_CHOFER_ACTUAL_UID to
                                    chofer.uid,

                            CAMPO_CHOFER_ACTUAL_NOMBRE to
                                    nombreChofer,

                            CAMPO_FECHA_ACTUALIZACION to
                                    FieldValue.serverTimestamp()
                        )
                    )
                }

                true
            }
            .addOnSuccessListener {

                mostrarCargando(false)

                Toast.makeText(
                    this,
                    if (nuevoVehiculo == null) {

                        "Se retiró el vehículo del chofer."

                    } else {

                        "Vehículo asignado correctamente."
                    },
                    Toast.LENGTH_LONG
                ).show()
            }
            .addOnFailureListener { error ->

                mostrarCargando(false)

                Toast.makeText(
                    this,
                    "No se pudo realizar la asignación: " +
                            (
                                    error.localizedMessage
                                        ?: "error desconocido"
                                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun construirDescripcionVehiculo(
        vehiculo: Vehiculo
    ): String {

        val marcaModelo =
            "${vehiculo.marca} ${vehiculo.modelo}"
                .trim()

        return marcaModelo.ifBlank {

            vehiculo.tipo.ifBlank {
                "Vehículo"
            }
        }
    }

    // -------------------------------------------------
    // CAMBIAR ESTADO
    // -------------------------------------------------

    private fun mostrarDialogoEstado(
        chofer: ChoferAdmin
    ) {

        val opciones =
            arrayOf(
                "Activo y disponible",
                "Activo no disponible",
                "Inactivo"
            )

        var seleccion =
            when {

                chofer.estado !=
                        ESTADO_ACTIVO -> 2

                chofer.disponible -> 0

                else -> 1
            }

        MaterialAlertDialogBuilder(this)
            .setTitle(
                "Estado de ${chofer.obtenerNombreCompleto()}"
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
                    chofer.viajeActualId.isNotBlank() &&
                    seleccion == 2
                ) {

                    Toast.makeText(
                        this,
                        "El chofer tiene un viaje activo y no puede desactivarse.",
                        Toast.LENGTH_LONG
                    ).show()

                    return@setPositiveButton
                }

                actualizarEstadoChofer(
                    chofer,
                    seleccion
                )
            }
            .show()
    }

    private fun actualizarEstadoChofer(
        chofer: ChoferAdmin,
        seleccion: Int
    ) {

        val nuevoEstado: String
        val disponible: Boolean

        when (seleccion) {

            0 -> {

                nuevoEstado =
                    ESTADO_ACTIVO

                disponible =
                    true
            }

            1 -> {

                nuevoEstado =
                    ESTADO_ACTIVO

                disponible =
                    false
            }

            else -> {

                nuevoEstado =
                    ESTADO_INACTIVO

                disponible =
                    false
            }
        }

        mostrarCargando(true)

        val referenciaChofer =
            firestore
                .collection(
                    COLECCION_CHOFERES
                )
                .document(
                    chofer.uid
                )

        firestore
            .runTransaction { transaccion ->

                /*
                 * LECTURAS
                 */
                val documentoChofer =
                    transaccion.get(
                        referenciaChofer
                    )

                if (!documentoChofer.exists()) {

                    throw IllegalStateException(
                        "No se encontró el chofer."
                    )
                }

                val viajeActual =
                    textoDocumento(
                        documentoChofer,
                        CAMPO_VIAJE_ACTUAL_ID
                    )

                if (
                    viajeActual.isNotBlank() &&
                    nuevoEstado ==
                    ESTADO_INACTIVO
                ) {

                    throw IllegalStateException(
                        "El chofer tiene un viaje activo."
                    )
                }

                val vehiculoId =
                    textoDocumento(
                        documentoChofer,
                        CAMPO_VEHICULO_ID
                    )

                val referenciaVehiculo =
                    if (
                        nuevoEstado ==
                        ESTADO_INACTIVO &&
                        vehiculoId.isNotBlank()
                    ) {

                        firestore
                            .collection(
                                COLECCION_VEHICULOS
                            )
                            .document(
                                vehiculoId
                            )

                    } else {

                        null
                    }

                val documentoVehiculo =
                    referenciaVehiculo?.let {
                            referencia ->

                        transaccion.get(
                            referencia
                        )
                    }

                /*
                 * ESCRITURAS
                 */
                val cambiosChofer =
                    hashMapOf<String, Any>(

                        CAMPO_ESTADO to
                                nuevoEstado,

                        CAMPO_DISPONIBLE to
                                disponible,

                        CAMPO_DISPONIBILIDAD to
                                if (disponible) {
                                    DISPONIBILIDAD_DISPONIBLE
                                } else {
                                    DISPONIBILIDAD_NO_DISPONIBLE
                                },

                        CAMPO_FECHA_ACTUALIZACION to
                                FieldValue.serverTimestamp()
                    )

                if (
                    nuevoEstado ==
                    ESTADO_INACTIVO
                ) {

                    cambiosChofer[
                        CAMPO_VEHICULO_ID
                    ] = ""

                    cambiosChofer[
                        CAMPO_VEHICULO_PLACA
                    ] = ""

                    cambiosChofer[
                        CAMPO_VEHICULO_DESCRIPCION
                    ] = ""
                }

                transaccion.update(
                    referenciaChofer,
                    cambiosChofer
                )

                if (
                    referenciaVehiculo != null &&
                    documentoVehiculo != null &&
                    documentoVehiculo.exists()
                ) {

                    val uidAsignado =
                        textoDocumento(
                            documentoVehiculo,
                            CAMPO_CHOFER_ACTUAL_UID
                        )

                    if (
                        uidAsignado ==
                        chofer.uid
                    ) {

                        transaccion.update(
                            referenciaVehiculo,
                            mapOf(
                                CAMPO_CHOFER_ACTUAL_UID to
                                        "",

                                CAMPO_CHOFER_ACTUAL_NOMBRE to
                                        "",

                                CAMPO_FECHA_ACTUALIZACION to
                                        FieldValue.serverTimestamp()
                            )
                        )
                    }
                }

                true
            }
            .addOnSuccessListener {

                mostrarCargando(false)

                Toast.makeText(
                    this,
                    "Estado del chofer actualizado.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { error ->

                mostrarCargando(false)

                Toast.makeText(
                    this,
                    "No se pudo cambiar el estado: " +
                            (
                                    error.localizedMessage
                                        ?: "error desconocido"
                                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // -------------------------------------------------
    // UTILIDADES
    // -------------------------------------------------

    private fun limpiarErrores(
        formulario: DialogEditarChoferBinding
    ) {

        formulario.tilNombres.error = null
        formulario.tilApellidos.error = null
        formulario.tilDni.error = null
        formulario.tilCelular.error = null
        formulario.tilLicencia.error = null

        formulario
            .tilCategoriaLicencia
            .error = null

        formulario
            .tilVencimientoLicencia
            .error = null
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
            val valor =
                documento.get(campo)
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
            val valor =
                documento.get(campo)
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

        binding.fabNuevoChofer.isEnabled =
            !mostrar

        binding.btnVolver.isEnabled =
            !mostrar
    }

    private fun abrirLogin() {

        val intent =
            Intent(
                this,
                LoginActivity::class.java
            ).apply {

                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

        startActivity(intent)
        finish()
    }

    private fun removerListeners() {

        listenerPerfil?.remove()
        listenerPerfil = null

        listenerChoferes?.remove()
        listenerChoferes = null
    }

    override fun onStop() {

        removerListeners()

        super.onStop()
    }

    companion object {

        private const val COLECCION_USUARIOS_GESTION =
            "usuariosgestionpasajes"

        private const val COLECCION_CHOFERES =
            "choferespasajes"

        private const val COLECCION_VEHICULOS =
            "vehiculospasajes"

        private const val CAMPO_UID =
            "uid"

        private const val CAMPO_ROL =
            "rol"

        private const val CAMPO_ESTADO =
            "estado"

        private const val CAMPO_EMPRESA_ID =
            "empresaId"

        private const val CAMPO_EMPRESA_NOMBRE =
            "empresaNombre"

        private const val CAMPO_NOMBRES =
            "nombres"

        private const val CAMPO_APELLIDOS =
            "apellidos"

        private const val CAMPO_NOMBRE_COMPLETO =
            "nombreCompleto"

        private const val CAMPO_DNI =
            "dni"

        private const val CAMPO_CELULAR =
            "celular"

        private const val CAMPO_CORREO =
            "correo"

        private const val CAMPO_LICENCIA =
            "licencia"

        private const val CAMPO_CATEGORIA_LICENCIA =
            "categoriaLicencia"

        private const val CAMPO_VENCIMIENTO_LICENCIA =
            "vencimientoLicencia"

        private const val CAMPO_DISPONIBLE =
            "disponible"

        private const val CAMPO_DISPONIBILIDAD =
            "disponibilidad"

        private const val CAMPO_VEHICULO_ID =
            "vehiculoId"

        private const val CAMPO_VEHICULO_PLACA =
            "vehiculoPlaca"

        private const val CAMPO_VEHICULO_DESCRIPCION =
            "vehiculoDescripcion"

        private const val CAMPO_VIAJE_ACTUAL_ID =
            "viajeActualId"

        private const val CAMPO_CHOFER_ACTUAL_UID =
            "choferActualUid"

        private const val CAMPO_CHOFER_ACTUAL_NOMBRE =
            "choferActualNombre"

        private const val CAMPO_PLACA =
            "placa"

        private const val CAMPO_TIPO =
            "tipo"

        private const val CAMPO_MARCA =
            "marca"

        private const val CAMPO_MODELO =
            "modelo"

        private const val CAMPO_COLOR =
            "color"

        private const val CAMPO_ANIO =
            "anio"

        private const val CAMPO_CAPACIDAD =
            "capacidad"

        private const val CAMPO_FECHA_ACTUALIZACION =
            "fechaActualizacion"

        private const val ROL_PROPIETARIO =
            "propietario"

        private const val ESTADO_ACTIVO =
            "activo"

        private const val ESTADO_INACTIVO =
            "inactivo"

        private const val ESTADO_ELIMINADO =
            "eliminado"

        private const val DISPONIBILIDAD_DISPONIBLE =
            "disponible"

        private const val DISPONIBILIDAD_NO_DISPONIBLE =
            "no_disponible"
    }
}