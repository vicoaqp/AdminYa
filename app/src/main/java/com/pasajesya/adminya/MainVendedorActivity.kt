package com.pasajesya.adminya

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.Intent
import android.view.View
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.pasajesya.adminya.databinding.ActivityMainVendedorBinding
import java.util.Locale

class MainVendedorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainVendedorBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private var listenerPerfil: ListenerRegistration? = null
    private var listenerEmpresa: ListenerRegistration? = null
    private var listenerChoferes: ListenerRegistration? = null
    private var listenerViajes: ListenerRegistration? = null
    private var listenerReservas: ListenerRegistration? = null

    private var empresaIdActual = ""
    private var empresaNombreActual = ""

    private var viajeActualId = ""
    private var viajeActual: ViajePanelVendedor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding =
            ActivityMainVendedorBinding.inflate(layoutInflater)

        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        configurarPantalla()
        configurarEventos()
        prepararPantalla()
    }

    override fun onStart() {
        super.onStart()

        removerListeners()

        empresaIdActual = ""
        empresaNombreActual = ""

        viajeActualId = ""
        viajeActual = null

        prepararPantalla()
        escucharPerfilVendedor()
    }

    private fun configurarPantalla() {

        window.statusBarColor =
            getColor(R.color.adminya_primary_dark)

        window.navigationBarColor =
            getColor(R.color.adminya_background)
    }

    private fun configurarEventos() {

        binding.btnCerrarSesion.setOnClickListener {
            confirmarCerrarSesion()
        }

        binding.btnActualizar.setOnClickListener {
            actualizarInformacion()
        }

        binding.btnCrearViaje.setOnClickListener {

            val intent =
                Intent(
                    this,
                    CrearViajeActivity::class.java
                )

            startActivity(intent)
        }

        binding.btnChoferesDisponibles.setOnClickListener {

            Toast.makeText(
                this,
                "La lista de choferes disponibles será el siguiente módulo.",
                Toast.LENGTH_LONG
            ).show()
        }

        binding.btnVerViajes.setOnClickListener {

            Toast.makeText(
                this,
                "La lista de todos los viajes será el siguiente módulo.",
                Toast.LENGTH_LONG
            ).show()
        }

        binding.btnControlarCarga.setOnClickListener {

            val viaje = viajeActual

            if (viaje == null) {

                Toast.makeText(
                    this,
                    "No hay un viaje activo para controlar.",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            val empresaId =
                viaje.empresaId.ifBlank {
                    empresaIdActual
                }

            if (empresaId.isBlank()) {

                Toast.makeText(
                    this,
                    "No se encontró la empresa del viaje.",
                    Toast.LENGTH_LONG
                ).show()

                return@setOnClickListener
            }

            val intent =
                Intent(
                    this,
                    ControlarCargaActivity::class.java
                ).apply {

                    putExtra(
                        ControlarCargaActivity.EXTRA_EMPRESA_ID,
                        empresaId
                    )

                    putExtra(
                        ControlarCargaActivity.EXTRA_VIAJE_ID,
                        viaje.id
                    )
                }

            startActivity(intent)
        }

        binding.btnVenderPasaje.setOnClickListener {

            val viaje = viajeActual

            if (viaje == null) {

                Toast.makeText(
                    this,
                    "No hay un viaje activo para revisar reservas.",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            val empresaId =
                viaje.empresaId.ifBlank {
                    empresaIdActual
                }

            if (empresaId.isBlank()) {

                Toast.makeText(
                    this,
                    "No se encontró la empresa del viaje.",
                    Toast.LENGTH_LONG
                ).show()

                return@setOnClickListener
            }

            val intent =
                Intent(
                    this,
                    AdministrarReservasActivity::class.java
                ).apply {

                    putExtra(
                        AdministrarReservasActivity.EXTRA_EMPRESA_ID,
                        empresaId
                    )

                    putExtra(
                        AdministrarReservasActivity.EXTRA_VIAJE_ID,
                        viaje.id
                    )
                }

            startActivity(intent)
        }

        binding.btnListaPasajeros.setOnClickListener {

            val viaje = viajeActual

            if (viaje == null) {

                Toast.makeText(
                    this,
                    "No hay un viaje activo.",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            Toast.makeText(
                this,
                "Lista de pasajeros del viaje ${viaje.id}.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun prepararPantalla() {

        binding.tvSaludo.text =
            "Hola, vendedor"

        binding.tvEmpresa.text =
            "Cargando empresa..."

        binding.tvChoferesDisponibles.text =
            "0"

        binding.tvViajesActivos.text =
            "0"

        binding.tvReservasActivas.text =
            "0"

        binding.contenedorViaje.visibility =
            View.GONE

        binding.contenedorSinViaje.visibility =
            View.GONE

        mostrarCargando(
            mostrar = true,
            mensaje = "Cargando operaciones..."
        )
    }

    // -------------------------------------------------
    // PERFIL DEL VENDEDOR
    // -------------------------------------------------

    private fun escucharPerfilVendedor() {

        val usuario = auth.currentUser

        if (usuario == null) {
            abrirLogin()
            return
        }

        mostrarCargando(
            mostrar = true,
            mensaje = "Validando cuenta..."
        )

        listenerPerfil?.remove()

        listenerPerfil = firestore
            .collection(COLECCION_USUARIOS)
            .document(usuario.uid)
            .addSnapshotListener { documento, error ->

                if (error != null) {

                    mostrarCargando(false)

                    Toast.makeText(
                        this,
                        "No se pudo cargar tu cuenta: ${
                            error.localizedMessage
                                ?: "error desconocido"
                        }",
                        Toast.LENGTH_LONG
                    ).show()

                    return@addSnapshotListener
                }

                if (
                    documento == null ||
                    !documento.exists()
                ) {

                    cerrarSesionInvalida(
                        "No se encontró tu perfil de vendedor."
                    )

                    return@addSnapshotListener
                }

                validarPerfilVendedor(documento)
            }
    }

    private fun validarPerfilVendedor(
        documento: DocumentSnapshot
    ) {

        val rol =
            leerTexto(
                documento,
                CAMPO_ROL
            ).lowercase(Locale.ROOT)

        val estado =
            leerTexto(
                documento,
                CAMPO_ESTADO
            ).lowercase(Locale.ROOT)

        val empresaId =
            leerTexto(
                documento,
                CAMPO_EMPRESA_ID
            )

        val empresaNombre =
            leerTexto(
                documento,
                CAMPO_EMPRESA_NOMBRE
            )

        val nombres =
            leerTexto(
                documento,
                CAMPO_NOMBRES
            )

        val nombreCompleto =
            leerTexto(
                documento,
                CAMPO_NOMBRE_COMPLETO
            )

        val registroCompleto =
            leerBooleano(
                documento,
                CAMPO_REGISTRO_COMPLETO,
                true
            )

        when {

            rol != ROL_VENDEDOR -> {

                cerrarSesionInvalida(
                    "Esta cuenta no pertenece a un vendedor."
                )
            }

            estado != ESTADO_ACTIVO -> {

                cerrarSesionInvalida(
                    "Tu cuenta está inactiva o suspendida."
                )
            }

            !registroCompleto -> {

                cerrarSesionInvalida(
                    "Tu registro todavía no está completo."
                )
            }

            empresaId.isBlank() -> {

                cerrarSesionInvalida(
                    "Tu cuenta no tiene una empresa asignada."
                )
            }

            else -> {

                val nombreMostrar =
                    nombres.ifBlank {

                        nombreCompleto
                            .split(" ")
                            .firstOrNull()
                            .orEmpty()
                    }

                binding.tvSaludo.text =
                    if (nombreMostrar.isBlank()) {

                        "Hola, vendedor"

                    } else {

                        "Hola, $nombreMostrar"
                    }

                binding.tvEmpresa.text =
                    empresaNombre.ifBlank {
                        empresaId
                    }

                if (empresaIdActual != empresaId) {

                    empresaIdActual = empresaId
                    empresaNombreActual = empresaNombre

                    escucharEmpresa(empresaId)
                    escucharChoferesDisponibles(empresaId)
                    escucharViajes(empresaId)
                }

                mostrarCargando(false)
            }
        }
    }

    // -------------------------------------------------
    // EMPRESA
    // -------------------------------------------------

    private fun escucharEmpresa(
        empresaId: String
    ) {

        listenerEmpresa?.remove()

        listenerEmpresa = firestore
            .collection(COLECCION_EMPRESAS)
            .document(empresaId)
            .addSnapshotListener { documento, error ->

                if (error != null) {

                    Toast.makeText(
                        this,
                        "No se pudo cargar la empresa: ${
                            error.localizedMessage
                                ?: "error desconocido"
                        }",
                        Toast.LENGTH_LONG
                    ).show()

                    return@addSnapshotListener
                }

                if (
                    documento == null ||
                    !documento.exists()
                ) {

                    cerrarSesionInvalida(
                        "La empresa asignada no existe."
                    )

                    return@addSnapshotListener
                }

                val estadoEmpresa =
                    leerTexto(
                        documento,
                        CAMPO_ESTADO
                    ).lowercase(Locale.ROOT)

                if (
                    estadoEmpresa.isNotBlank() &&
                    estadoEmpresa != ESTADO_ACTIVO
                ) {

                    cerrarSesionInvalida(
                        "La empresa no se encuentra activa."
                    )

                    return@addSnapshotListener
                }

                val nombreEmpresa =
                    leerTexto(
                        documento,
                        CAMPO_NOMBRE
                    ).ifBlank {

                        leerTexto(
                            documento,
                            CAMPO_EMPRESA_NOMBRE
                        )
                    }.ifBlank {

                        leerTexto(
                            documento,
                            CAMPO_RAZON_SOCIAL
                        )
                    }

                if (nombreEmpresa.isNotBlank()) {

                    empresaNombreActual =
                        nombreEmpresa

                    binding.tvEmpresa.text =
                        nombreEmpresa
                }
            }
    }

    // -------------------------------------------------
    // CHOFERES DISPONIBLES
    // -------------------------------------------------

    private fun escucharChoferesDisponibles(
        empresaId: String
    ) {

        listenerChoferes?.remove()

        listenerChoferes = firestore
            .collection(COLECCION_CHOFERES)
            .whereEqualTo(
                CAMPO_EMPRESA_ID,
                empresaId
            )
            .addSnapshotListener { resultado, error ->

                if (error != null) {

                    binding.tvChoferesDisponibles.text =
                        "0"

                    Toast.makeText(
                        this,
                        "No se pudieron cargar los choferes.",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@addSnapshotListener
                }

                val cantidadDisponibles =
                    resultado
                        ?.documents
                        ?.count { documento ->

                            val estado =
                                leerTexto(
                                    documento,
                                    CAMPO_ESTADO
                                ).lowercase(Locale.ROOT)

                            val disponibilidad =
                                leerTexto(
                                    documento,
                                    CAMPO_DISPONIBILIDAD
                                ).lowercase(Locale.ROOT)

                            val disponible =
                                leerBooleano(
                                    documento,
                                    CAMPO_DISPONIBLE,
                                    false
                                )

                            val viajeId =
                                leerTexto(
                                    documento,
                                    CAMPO_VIAJE_ACTUAL_ID
                                )

                            estado == ESTADO_ACTIVO &&
                                    viajeId.isBlank() &&
                                    (
                                            disponible ||
                                                    disponibilidad ==
                                                    DISPONIBILIDAD_DISPONIBLE
                                            )
                        }
                        ?: 0

                binding.tvChoferesDisponibles.text =
                    cantidadDisponibles.toString()
            }
    }

    // -------------------------------------------------
    // VIAJES
    // -------------------------------------------------

    private fun escucharViajes(
        empresaId: String
    ) {

        listenerViajes?.remove()

        listenerViajes = firestore
            .collection(COLECCION_EMPRESAS)
            .document(empresaId)
            .collection(SUBCOLECCION_VIAJES)
            .addSnapshotListener { resultado, error ->

                mostrarCargando(false)

                if (error != null) {

                    binding.tvViajesActivos.text =
                        "0"

                    binding.tvReservasActivas.text =
                        "0"

                    mostrarSinViaje(
                        "No se pudieron cargar los viajes."
                    )

                    Toast.makeText(
                        this,
                        "No se pudieron cargar los viajes: ${
                            error.localizedMessage
                                ?: "error desconocido"
                        }",
                        Toast.LENGTH_LONG
                    ).show()

                    return@addSnapshotListener
                }

                val viajesActivos =
                    resultado
                        ?.documents
                        ?.map { documento ->

                            convertirViaje(documento)
                        }
                        ?.filter { viaje ->

                            ESTADOS_VIAJE_ACTIVOS.contains(
                                viaje.estado
                            )
                        }
                        .orEmpty()

                binding.tvViajesActivos.text =
                    viajesActivos.size.toString()

                val viajeSeleccionado =
                    viajesActivos
                        .sortedWith(
                            compareBy<ViajePanelVendedor> { viaje ->

                                prioridadEstado(
                                    viaje.estado
                                )

                            }.thenBy { viaje ->

                                viaje.fechaOrden
                            }
                        )
                        .firstOrNull()

                if (viajeSeleccionado == null) {

                    viajeActualId = ""
                    viajeActual = null

                    listenerReservas?.remove()
                    listenerReservas = null

                    binding.tvReservasActivas.text =
                        "0"

                    mostrarSinViaje(
                        "Crea un viaje para comenzar a cargar pasajeros."
                    )

                    return@addSnapshotListener
                }

                val cambioViaje =
                    viajeActualId !=
                            viajeSeleccionado.id

                viajeActualId =
                    viajeSeleccionado.id

                viajeActual =
                    viajeSeleccionado

                mostrarViajeActual(
                    viajeSeleccionado
                )

                if (cambioViaje) {

                    escucharReservasViaje(
                        viajeSeleccionado
                    )
                }
            }
    }

    private fun convertirViaje(
        documento: DocumentSnapshot
    ): ViajePanelVendedor {

        val asientosReservados =
            leerListaTextos(
                documento,
                CAMPO_ASIENTOS_RESERVADOS
            )

        val vehiculoDescripcion =
            leerTexto(
                documento,
                CAMPO_VEHICULO_DESCRIPCION
            ).ifBlank {

                leerTexto(
                    documento,
                    CAMPO_VEHICULO
                )
            }

        val choferNombre =
            leerTexto(
                documento,
                CAMPO_CHOFER_NOMBRE
            ).ifBlank {

                leerTexto(
                    documento,
                    CAMPO_CHOFER_ACTUAL_NOMBRE
                )
            }

        val placa =
            leerTexto(
                documento,
                CAMPO_PLACA
            ).ifBlank {

                leerTexto(
                    documento,
                    CAMPO_VEHICULO_PLACA
                )
            }

        val estadoViaje =
            obtenerEstadoViaje(documento)

        return ViajePanelVendedor(

            id = documento.id,

            empresaId =
                leerTexto(
                    documento,
                    CAMPO_EMPRESA_ID
                ),

            origen =
                leerTexto(
                    documento,
                    CAMPO_ORIGEN
                ),

            destino =
                leerTexto(
                    documento,
                    CAMPO_DESTINO
                ),

            fechaSalida =
                leerTexto(
                    documento,
                    CAMPO_FECHA_SALIDA
                ),

            horaSalida =
                leerTexto(
                    documento,
                    CAMPO_HORA_SALIDA
                ),

            estado =
                estadoViaje,

            choferNombre =
                choferNombre,

            vehiculoDescripcion =
                vehiculoDescripcion,

            placa =
                placa,

            capacidad =
                leerEntero(
                    documento,
                    CAMPO_CAPACIDAD,
                    4
                ),

            asientosOcupados =
                leerEntero(
                    documento,
                    CAMPO_ASIENTOS_OCUPADOS,
                    asientosReservados.size
                ),

            fechaOrden =
                obtenerFechaOrden(documento)
        )
    }

    private fun obtenerEstadoViaje(
        documento: DocumentSnapshot
    ): String {

        val estadoOperacion =
            leerTexto(
                documento,
                CAMPO_ESTADO_OPERACION
            ).lowercase(Locale.ROOT)

        val estadoGeneral =
            leerTexto(
                documento,
                CAMPO_ESTADO
            ).lowercase(Locale.ROOT)

        return when {

            estadoOperacion.isNotBlank() -> {
                estadoOperacion
            }

            estadoGeneral.isNotBlank() -> {
                estadoGeneral
            }

            else -> {
                ESTADO_PROGRAMADO
            }
        }
    }

    private fun mostrarViajeActual(
        viaje: ViajePanelVendedor
    ) {

        binding.contenedorSinViaje.visibility =
            View.GONE

        binding.contenedorViaje.visibility =
            View.VISIBLE

        binding.tvRuta.text =
            if (
                viaje.origen.isNotBlank() &&
                viaje.destino.isNotBlank()
            ) {

                "${viaje.origen} → ${viaje.destino}"

            } else {

                "Ruta no especificada"
            }

        binding.tvFechaHora.text =
            construirFechaHora(
                viaje.fechaSalida,
                viaje.horaSalida
            )

        binding.tvChofer.text =
            "Chofer: ${
                viaje.choferNombre.ifBlank {
                    "No asignado"
                }
            }"

        binding.tvVehiculo.text =
            "Vehículo: ${
                viaje.vehiculoDescripcion.ifBlank {
                    "No asignado"
                }
            }"

        binding.tvPlaca.text =
            "Placa: ${
                viaje.placa.ifBlank {
                    "No registrada"
                }
            }"

        binding.tvOcupacion.text =
            "Ocupación: ${viaje.asientosOcupados} " +
                    "de ${viaje.capacidad} pasajeros"

        binding.tvEstadoViaje.text =
            obtenerTextoEstado(
                viaje.estado
            )

        aplicarColorEstado(
            viaje.estado
        )
    }

    private fun mostrarSinViaje(
        mensaje: String
    ) {

        binding.contenedorViaje.visibility =
            View.GONE

        binding.contenedorSinViaje.visibility =
            View.VISIBLE

        binding.tvMensajeSinViaje.text =
            mensaje
    }

    // -------------------------------------------------
    // RESERVAS Y PASAJEROS
    // -------------------------------------------------

    private fun escucharReservasViaje(
        viaje: ViajePanelVendedor
    ) {

        listenerReservas?.remove()

        binding.tvReservasActivas.text =
            "0"

        listenerReservas = firestore
            .collection(COLECCION_RESERVAS)
            .whereEqualTo(
                CAMPO_VIAJE_ID,
                viaje.id
            )
            .addSnapshotListener { resultado, error ->

                if (error != null) {

                    binding.tvReservasActivas.text =
                        "0"

                    Toast.makeText(
                        this,
                        "No se pudieron cargar los pasajeros.",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@addSnapshotListener
                }

                val cantidadPasajeros =
                    resultado
                        ?.documents
                        ?.count { documento ->

                            val empresaReserva =
                                leerTexto(
                                    documento,
                                    CAMPO_EMPRESA_ID
                                )

                            val estadoReserva =
                                leerTextoReserva(
                                    documento
                                ).lowercase(Locale.ROOT)

                            val perteneceEmpresa =
                                empresaReserva.isBlank() ||
                                        empresaReserva ==
                                        empresaIdActual

                            perteneceEmpresa &&
                                    estadoReserva !=
                                    ESTADO_RESERVA_CANCELADA &&
                                    estadoReserva !=
                                    ESTADO_RESERVA_VENCIDA
                        }
                        ?: 0

                binding.tvReservasActivas.text =
                    cantidadPasajeros.toString()
            }
    }

    private fun leerTextoReserva(
        documento: DocumentSnapshot
    ): String {

        return leerTexto(
            documento,
            CAMPO_ESTADO_RESERVA
        ).ifBlank {

            leerTexto(
                documento,
                CAMPO_ESTADO
            )
        }
    }

    // -------------------------------------------------
    // ESTADOS DEL VIAJE
    // -------------------------------------------------

    private fun obtenerTextoEstado(
        estado: String
    ): String {

        return when (estado) {

            ESTADO_PROGRAMADO -> {
                "PROGRAMADO"
            }

            ESTADO_RECIBIENDO -> {
                "CARGANDO"
            }

            ESTADO_DISPONIBLE -> {
                "DISPONIBLE"
            }

            ESTADO_ACTIVO -> {
                "ACTIVO"
            }

            ESTADO_LISTO -> {
                "LISTO PARA SALIR"
            }

            ESTADO_EN_VIAJE -> {
                "EN VIAJE"
            }

            else -> {

                estado
                    .replace("_", " ")
                    .uppercase(Locale.ROOT)
                    .ifBlank {
                        "SIN ESTADO"
                    }
            }
        }
    }

    private fun aplicarColorEstado(
        estado: String
    ) {

        val color =
            when (estado) {

                ESTADO_PROGRAMADO -> {
                    R.color.adminya_primary
                }

                ESTADO_RECIBIENDO,
                ESTADO_DISPONIBLE,
                ESTADO_ACTIVO -> {
                    R.color.adminya_warning
                }

                ESTADO_LISTO -> {
                    R.color.adminya_success
                }

                ESTADO_EN_VIAJE -> {
                    R.color.adminya_danger
                }

                else -> {
                    R.color.adminya_primary
                }
            }

        binding.cardEstadoViaje
            .setCardBackgroundColor(
                getColor(color)
            )
    }

    private fun prioridadEstado(
        estado: String
    ): Int {

        return when (estado) {

            ESTADO_EN_VIAJE -> {
                0
            }

            ESTADO_LISTO -> {
                1
            }

            ESTADO_RECIBIENDO -> {
                2
            }

            ESTADO_PROGRAMADO -> {
                3
            }

            ESTADO_DISPONIBLE,
            ESTADO_ACTIVO -> {
                4
            }

            else -> {
                99
            }
        }
    }

    // -------------------------------------------------
    // ACTUALIZAR INFORMACIÓN
    // -------------------------------------------------

    private fun actualizarInformacion() {

        removerListeners()

        empresaIdActual = ""
        empresaNombreActual = ""

        viajeActualId = ""
        viajeActual = null

        prepararPantalla()
        escucharPerfilVendedor()
    }

    // -------------------------------------------------
    // CERRAR SESIÓN
    // -------------------------------------------------

    private fun confirmarCerrarSesion() {

        MaterialAlertDialogBuilder(this)
            .setTitle("Cerrar sesión")
            .setMessage(
                "¿Deseas salir de tu cuenta de vendedor?"
            )
            .setNegativeButton(
                "CANCELAR",
                null
            )
            .setPositiveButton(
                "SALIR"
            ) { _, _ ->

                cerrarSesion()
            }
            .show()
    }

    private fun cerrarSesion() {

        removerListeners()
        auth.signOut()
        abrirLogin()
    }

    private fun cerrarSesionInvalida(
        mensaje: String
    ) {

        removerListeners()
        auth.signOut()

        Toast.makeText(
            this,
            mensaje,
            Toast.LENGTH_LONG
        ).show()

        abrirLogin()
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

    // -------------------------------------------------
    // UTILIDADES
    // -------------------------------------------------

    private fun leerTexto(
        documento: DocumentSnapshot,
        campo: String
    ): String {

        return documento
            .get(campo)
            ?.toString()
            ?.trim()
            .orEmpty()
    }

    private fun leerEntero(
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
                valor
                    .trim()
                    .toIntOrNull()
                    ?: predeterminado
            }

            else -> {
                predeterminado
            }
        }
    }

    private fun leerBooleano(
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

            is Number -> {
                valor.toInt() != 0
            }

            is String -> {

                when (
                    valor
                        .trim()
                        .lowercase(Locale.ROOT)
                ) {

                    "true",
                    "1",
                    "si",
                    "sí" -> {
                        true
                    }

                    "false",
                    "0",
                    "no" -> {
                        false
                    }

                    else -> {
                        predeterminado
                    }
                }
            }

            else -> {
                predeterminado
            }
        }
    }

    private fun leerListaTextos(
        documento: DocumentSnapshot,
        campo: String
    ): List<String> {

        return (
                documento.get(campo)
                        as? List<*>
                )
            ?.mapNotNull { valor ->

                valor
                    ?.toString()
                    ?.trim()
                    ?.takeIf {
                        it.isNotBlank()
                    }
            }
            .orEmpty()
    }

    private fun obtenerFechaOrden(
        documento: DocumentSnapshot
    ): Long {

        val camposFecha =
            listOf(
                "fechaSalidaOrden",
                "fechaActualizacion",
                "ultimaActualizacion",
                "fechaRegistro",
                "fechaCreacion"
            )

        camposFecha.forEach { campo ->

            val timestamp =
                documento.getTimestamp(campo)

            if (timestamp != null) {

                return timestamp
                    .toDate()
                    .time
            }
        }

        return 0L
    }

    private fun construirFechaHora(
        fecha: String,
        hora: String
    ): String {

        return when {

            fecha.isNotBlank() &&
                    hora.isNotBlank() -> {

                "$fecha · $hora"
            }

            fecha.isNotBlank() -> {
                fecha
            }

            hora.isNotBlank() -> {
                hora
            }

            else -> {
                "Fecha y hora por definir"
            }
        }
    }

    private fun mostrarCargando(
        mostrar: Boolean,
        mensaje: String =
            "Cargando operaciones..."
    ) {

        binding.contenedorCargando.visibility =
            if (mostrar) {

                View.VISIBLE

            } else {

                View.GONE
            }

        binding.tvMensajeCargando.text =
            mensaje

        binding.btnActualizar.isEnabled =
            !mostrar

        binding.btnCerrarSesion.isEnabled =
            !mostrar
    }

    private fun removerListeners() {

        listenerPerfil?.remove()
        listenerPerfil = null

        listenerEmpresa?.remove()
        listenerEmpresa = null

        listenerChoferes?.remove()
        listenerChoferes = null

        listenerViajes?.remove()
        listenerViajes = null

        listenerReservas?.remove()
        listenerReservas = null
    }

    override fun onStop() {

        removerListeners()

        super.onStop()
    }

    companion object {

        private const val COLECCION_USUARIOS =
            "usuariosgestionpasajes"

        private const val COLECCION_EMPRESAS =
            "empresaspasajes"

        private const val COLECCION_CHOFERES =
            "choferespasajes"

        private const val COLECCION_RESERVAS =
            "reservaspasajes"

        private const val SUBCOLECCION_VIAJES =
            "viajespasajes"

        private const val CAMPO_ROL =
            "rol"

        private const val CAMPO_ESTADO =
            "estado"

        private const val CAMPO_ESTADO_OPERACION =
            "estadoOperacion"

        private const val CAMPO_EMPRESA_ID =
            "empresaId"

        private const val CAMPO_EMPRESA_NOMBRE =
            "empresaNombre"

        private const val CAMPO_NOMBRE =
            "nombre"

        private const val CAMPO_RAZON_SOCIAL =
            "razonSocial"

        private const val CAMPO_NOMBRES =
            "nombres"

        private const val CAMPO_NOMBRE_COMPLETO =
            "nombreCompleto"

        private const val CAMPO_REGISTRO_COMPLETO =
            "registroCompleto"

        private const val CAMPO_DISPONIBLE =
            "disponible"

        private const val CAMPO_DISPONIBILIDAD =
            "disponibilidad"

        private const val CAMPO_VIAJE_ACTUAL_ID =
            "viajeActualId"

        private const val CAMPO_VIAJE_ID =
            "viajeId"

        private const val CAMPO_ORIGEN =
            "origen"

        private const val CAMPO_DESTINO =
            "destino"

        private const val CAMPO_FECHA_SALIDA =
            "fechaSalida"

        private const val CAMPO_HORA_SALIDA =
            "horaSalida"

        private const val CAMPO_CHOFER_NOMBRE =
            "choferNombre"

        private const val CAMPO_CHOFER_ACTUAL_NOMBRE =
            "choferActualNombre"

        private const val CAMPO_VEHICULO =
            "vehiculo"

        private const val CAMPO_VEHICULO_DESCRIPCION =
            "vehiculoDescripcion"

        private const val CAMPO_VEHICULO_PLACA =
            "vehiculoPlaca"

        private const val CAMPO_PLACA =
            "placa"

        private const val CAMPO_CAPACIDAD =
            "capacidad"

        private const val CAMPO_ASIENTOS_OCUPADOS =
            "asientosOcupados"

        private const val CAMPO_ASIENTOS_RESERVADOS =
            "asientosReservados"

        private const val CAMPO_ESTADO_RESERVA =
            "estadoReserva"

        private const val ROL_VENDEDOR =
            "vendedor"

        private const val ESTADO_ACTIVO =
            "activo"

        private const val ESTADO_PROGRAMADO =
            "programado"

        private const val ESTADO_RECIBIENDO =
            "recibiendo_pasajeros"

        private const val ESTADO_DISPONIBLE =
            "disponible"

        private const val ESTADO_LISTO =
            "listo_para_salir"

        private const val ESTADO_EN_VIAJE =
            "en_viaje"

        private const val ESTADO_RESERVA_CANCELADA =
            "cancelada"

        private const val ESTADO_RESERVA_VENCIDA =
            "vencida"

        private const val DISPONIBILIDAD_DISPONIBLE =
            "disponible"

        private val ESTADOS_VIAJE_ACTIVOS =
            setOf(
                ESTADO_PROGRAMADO,
                ESTADO_RECIBIENDO,
                ESTADO_DISPONIBLE,
                ESTADO_LISTO,
                ESTADO_EN_VIAJE,
                ESTADO_ACTIVO
            )
    }
}

private data class ViajePanelVendedor(

    val id: String = "",

    val empresaId: String = "",

    val origen: String = "",
    val destino: String = "",

    val fechaSalida: String = "",
    val horaSalida: String = "",

    val estado: String = "",

    val choferNombre: String = "",

    val vehiculoDescripcion: String = "",
    val placa: String = "",

    val capacidad: Int = 4,
    val asientosOcupados: Int = 0,

    val fechaOrden: Long = 0L
)