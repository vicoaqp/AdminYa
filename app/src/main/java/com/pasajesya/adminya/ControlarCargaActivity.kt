package com.pasajesya.adminya

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.pasajesya.adminya.databinding.ActivityControlarCargaBinding
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
class ControlarCargaActivity : AppCompatActivity() {
    private lateinit var binding:
            ActivityControlarCargaBinding

    private lateinit var auth:
            FirebaseAuth

    private lateinit var firestore:
            FirebaseFirestore

    private lateinit var adapter:
            PasajeroControlAdapter

    private var listenerPerfil:
            ListenerRegistration? = null

    private var listenerViaje:
            ListenerRegistration? = null

    private var listenerReservas:
            ListenerRegistration? = null

    private var empresaId = ""
    private var viajeId = ""

    private var vendedorNombre = ""
    private var estadoViajeActual = ""

    private var listaPasajeros:
            List<PasajeroControl> = emptyList()

    private var accionesHabilitadas = true
    private var datosIniciados = false
    private var procesando = false

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        binding =
            ActivityControlarCargaBinding.inflate(
                layoutInflater
            )

        setContentView(binding.root)

        auth =
            FirebaseAuth.getInstance()

        firestore =
            FirebaseFirestore.getInstance()

        empresaId =
            intent.getStringExtra(
                EXTRA_EMPRESA_ID
            ).orEmpty()

        viajeId =
            intent.getStringExtra(
                EXTRA_VIAJE_ID
            ).orEmpty()

        if (
            empresaId.isBlank() ||
            viajeId.isBlank()
        ) {

            Toast.makeText(
                this,
                "No se recibió la información del viaje.",
                Toast.LENGTH_LONG
            ).show()

            finish()
            return
        }

        configurarPantalla()
        configurarLista()
        configurarEventos()
        prepararPantalla()
    }

    override fun onStart() {
        super.onStart()

        removerListeners()

        datosIniciados = false
        escucharPerfilVendedor()
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

        adapter =
            PasajeroControlAdapter { pasajero ->

                confirmarCambioEmbarque(
                    pasajero
                )
            }

        binding.rvPasajeros.layoutManager =
            LinearLayoutManager(this)

        binding.rvPasajeros.adapter =
            adapter

        binding.rvPasajeros
            .setHasFixedSize(false)
    }

    private fun configurarEventos() {

        binding.btnVolver.setOnClickListener {
            finish()
        }

        binding.etBuscar.doAfterTextChanged {
            aplicarFiltro()
        }

        binding.btnListoSalir.setOnClickListener {
            confirmarListoParaSalir()
        }
    }

    private fun prepararPantalla() {

        binding.tvRuta.text =
            "Cargando viaje..."

        binding.tvFechaHora.text =
            "Cargando fecha y hora..."

        binding.tvTotalPasajeros.text =
            "0"

        binding.tvEmbarcados.text =
            "0"

        binding.tvPendientes.text =
            "0"

        binding.tvTotalRegistros.text =
            "0 reservas"

        binding.btnListoSalir.isEnabled =
            false

        binding.progressCargando.visibility =
            View.VISIBLE

        binding.rvPasajeros.visibility =
            View.GONE

        binding.contenedorVacio.visibility =
            View.GONE
    }

    // -------------------------------------------------
    // VALIDAR VENDEDOR
    // -------------------------------------------------

    private fun escucharPerfilVendedor() {

        val usuario =
            auth.currentUser

        if (usuario == null) {

            Toast.makeText(
                this,
                "La sesión ha finalizado.",
                Toast.LENGTH_LONG
            ).show()

            finish()
            return
        }

        listenerPerfil =
            firestore
                .collection(
                    COLECCION_USUARIOS
                )
                .document(
                    usuario.uid
                )
                .addSnapshotListener {
                        documento, error ->

                    if (error != null) {

                        Toast.makeText(
                            this,
                            "No se pudo validar tu cuenta: ${
                                error.localizedMessage
                                    ?: "error desconocido"
                            }",
                            Toast.LENGTH_LONG
                        ).show()

                        finish()
                        return@addSnapshotListener
                    }

                    if (
                        documento == null ||
                        !documento.exists()
                    ) {

                        Toast.makeText(
                            this,
                            "No se encontró tu perfil.",
                            Toast.LENGTH_LONG
                        ).show()

                        finish()
                        return@addSnapshotListener
                    }

                    validarPerfil(
                        documento
                    )
                }
    }

    private fun validarPerfil(
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

        val empresaPerfil =
            leerTexto(
                documento,
                CAMPO_EMPRESA_ID
            )

        vendedorNombre =
            leerTexto(
                documento,
                CAMPO_NOMBRE_COMPLETO
            ).ifBlank {

                val nombres =
                    leerTexto(
                        documento,
                        CAMPO_NOMBRES
                    )

                val apellidos =
                    leerTexto(
                        documento,
                        CAMPO_APELLIDOS
                    )

                "$nombres $apellidos".trim()
            }

        when {

            rol != ROL_VENDEDOR -> {

                Toast.makeText(
                    this,
                    "Esta sección pertenece al vendedor.",
                    Toast.LENGTH_LONG
                ).show()

                finish()
            }

            estado != ESTADO_ACTIVO -> {

                Toast.makeText(
                    this,
                    "Tu cuenta no está activa.",
                    Toast.LENGTH_LONG
                ).show()

                finish()
            }

            empresaPerfil != empresaId -> {

                Toast.makeText(
                    this,
                    "El viaje pertenece a otra empresa.",
                    Toast.LENGTH_LONG
                ).show()

                finish()
            }

            !datosIniciados -> {

                datosIniciados = true

                escucharViaje()
                escucharReservas()
            }
        }
    }

    // -------------------------------------------------
    // VIAJE
    // -------------------------------------------------

    private fun escucharViaje() {

        listenerViaje?.remove()

        listenerViaje =
            referenciaViaje()
                .addSnapshotListener {
                        documento, error ->

                    if (error != null) {

                        Toast.makeText(
                            this,
                            "No se pudo cargar el viaje: ${
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

                        Toast.makeText(
                            this,
                            "El viaje ya no existe.",
                            Toast.LENGTH_LONG
                        ).show()

                        finish()
                        return@addSnapshotListener
                    }

                    mostrarDatosViaje(
                        documento
                    )
                }
    }

    private fun mostrarDatosViaje(
        documento: DocumentSnapshot
    ) {

        val origen =
            leerTexto(
                documento,
                CAMPO_ORIGEN
            )

        val destino =
            leerTexto(
                documento,
                CAMPO_DESTINO
            )

        val fecha =
            leerTexto(
                documento,
                CAMPO_FECHA_SALIDA
            )

        val hora =
            leerTexto(
                documento,
                CAMPO_HORA_SALIDA
            )

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

        estadoViajeActual =
            estadoOperacion.ifBlank {
                estadoGeneral
            }

        binding.tvRuta.text =
            if (
                origen.isNotBlank() &&
                destino.isNotBlank()
            ) {

                "$origen → $destino"

            } else {

                "Ruta no registrada"
            }

        binding.tvFechaHora.text =
            when {

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
                    "Fecha y hora no registradas"
                }
            }

        accionesHabilitadas =
            estadoViajeActual !in
                    ESTADOS_VIAJE_BLOQUEADOS

        adapter.establecerAccionesHabilitadas(
            accionesHabilitadas
        )

        actualizarBotonListoSalir()
    }

    // -------------------------------------------------
    // RESERVAS
    // -------------------------------------------------

    private fun escucharReservas() {

        listenerReservas?.remove()

        listenerReservas =
            firestore
                .collection(
                    COLECCION_RESERVAS
                )
                .whereEqualTo(
                    CAMPO_VIAJE_ID,
                    viajeId
                )
                .addSnapshotListener {
                        resultado, error ->

                    binding.progressCargando.visibility =
                        View.GONE

                    if (error != null) {

                        Toast.makeText(
                            this,
                            "No se pudieron cargar los pasajeros: ${
                                error.localizedMessage
                                    ?: "error desconocido"
                            }",
                            Toast.LENGTH_LONG
                        ).show()

                        mostrarListaVacia(
                            "No se pudieron cargar los pasajeros."
                        )

                        return@addSnapshotListener
                    }

                    listaPasajeros =
                        resultado
                            ?.documents
                            ?.map { documento ->

                                convertirPasajero(
                                    documento
                                )
                            }
                            ?.filter { pasajero ->

                                val empresaCorrecta =
                                    pasajero.empresaId.isBlank() ||
                                            pasajero.empresaId ==
                                            empresaId

                                val estado =
                                    pasajero.estadoReserva
                                        .lowercase(
                                            Locale.ROOT
                                        )

                                empresaCorrecta &&
                                        estado !=
                                        ESTADO_CANCELADA &&
                                        estado !=
                                        ESTADO_VENCIDA
                            }
                            ?.sortedWith(
                                compareBy<PasajeroControl> {
                                        pasajero ->

                                    pasajero.numeroAsiento
                                        .toIntOrNull()
                                        ?: Int.MAX_VALUE
                                }.thenBy {
                                        pasajero ->

                                    pasajero.pasajeroNombre
                                        .lowercase(
                                            Locale.ROOT
                                        )
                                }
                            )
                            .orEmpty()

                    adapter.actualizarLista(
                        listaPasajeros
                    )

                    actualizarResumen()
                    aplicarFiltro()
                }
    }

    private fun convertirPasajero(
        documento: DocumentSnapshot
    ): PasajeroControl {

        val estadoReserva =
            leerTexto(
                documento,
                CAMPO_ESTADO_RESERVA
            ).ifBlank {

                leerTexto(
                    documento,
                    CAMPO_ESTADO
                )
            }.ifBlank {

                ESTADO_CONFIRMADA
            }.lowercase(Locale.ROOT)

        val embarcado =
            leerBooleano(
                documento,
                CAMPO_EMBARQUE_CONFIRMADO,
                false
            ) ||
                    estadoReserva ==
                    ESTADO_EMBARCADO

        val nombre =
            leerTexto(
                documento,
                CAMPO_PASAJERO_NOMBRE
            ).ifBlank {

                leerTexto(
                    documento,
                    "nombrePasajero"
                )
            }.ifBlank {

                leerTexto(
                    documento,
                    CAMPO_NOMBRE_COMPLETO
                )
            }

        val dni =
            leerTexto(
                documento,
                CAMPO_PASAJERO_DNI
            ).ifBlank {

                leerTexto(
                    documento,
                    CAMPO_DNI
                )
            }

        val celular =
            leerTexto(
                documento,
                CAMPO_PASAJERO_CELULAR
            ).ifBlank {

                leerTexto(
                    documento,
                    CAMPO_CELULAR
                )
            }

        val asiento =
            leerTexto(
                documento,
                CAMPO_NUMERO_ASIENTO
            ).ifBlank {

                leerTexto(
                    documento,
                    "asiento"
                )
            }

        return PasajeroControl(

            reservaId =
                documento.id,

            empresaId =
                leerTexto(
                    documento,
                    CAMPO_EMPRESA_ID
                ),

            viajeId =
                leerTexto(
                    documento,
                    CAMPO_VIAJE_ID
                ),

            pasajeroNombre =
                nombre,

            pasajeroDni =
                dni,

            pasajeroCelular =
                celular,

            numeroAsiento =
                asiento,

            cantidadPasajeros =
                max(
                    1,
                    leerEntero(
                        documento,
                        CAMPO_CANTIDAD_PASAJEROS,
                        1
                    )
                ),

            estadoReserva =
                estadoReserva,

            embarcado =
                embarcado
        )
    }

    private fun actualizarResumen() {

        val totalPasajeros =
            listaPasajeros.sumOf {
                it.cantidadPasajeros
            }

        val embarcados =
            listaPasajeros
                .filter {
                    it.embarcado
                }
                .sumOf {
                    it.cantidadPasajeros
                }

        val pendientes =
            max(
                0,
                totalPasajeros - embarcados
            )

        binding.tvTotalPasajeros.text =
            totalPasajeros.toString()

        binding.tvEmbarcados.text =
            embarcados.toString()

        binding.tvPendientes.text =
            pendientes.toString()

        actualizarBotonListoSalir()
    }

    private fun aplicarFiltro() {

        val consulta =
            binding.etBuscar.text
                ?.toString()
                ?.trim()
                .orEmpty()

        adapter.filtrar(
            consulta
        )

        val cantidad =
            adapter.itemCount

        binding.tvTotalRegistros.text =
            when (cantidad) {

                0 -> {
                    "0 reservas"
                }

                1 -> {
                    "1 reserva"
                }

                else -> {
                    "$cantidad reservas"
                }
            }

        val listaVacia =
            cantidad == 0

        binding.rvPasajeros.visibility =
            if (listaVacia) {
                View.GONE
            } else {
                View.VISIBLE
            }

        binding.contenedorVacio.visibility =
            if (listaVacia) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.tvMensajeVacio.text =
            if (
                consulta.isNotBlank()
            ) {

                "No encontramos pasajeros con esa búsqueda."

            } else {

                "Todavía no existen reservas para este viaje."
            }
    }

    private fun mostrarListaVacia(
        mensaje: String
    ) {

        binding.rvPasajeros.visibility =
            View.GONE

        binding.contenedorVacio.visibility =
            View.VISIBLE

        binding.tvMensajeVacio.text =
            mensaje
    }

    // -------------------------------------------------
    // MARCAR EMBARQUE
    // -------------------------------------------------

    private fun confirmarCambioEmbarque(
        pasajero: PasajeroControl
    ) {

        val marcarEmbarcado =
            !pasajero.embarcado

        val titulo =
            if (marcarEmbarcado) {

                "Confirmar embarque"

            } else {

                "Desmarcar embarque"
            }

        val mensaje =
            if (marcarEmbarcado) {

                "¿Confirmas que ${pasajero.pasajeroNombre} " +
                        "ya abordó el vehículo?"

            } else {

                "¿Deseas marcar nuevamente a " +
                        "${pasajero.pasajeroNombre} como pendiente?"
            }

        MaterialAlertDialogBuilder(this)
            .setTitle(titulo)
            .setMessage(mensaje)
            .setNegativeButton(
                "CANCELAR",
                null
            )
            .setPositiveButton(
                "CONFIRMAR"
            ) { _, _ ->

                cambiarEstadoEmbarque(
                    pasajero,
                    marcarEmbarcado
                )
            }
            .show()
    }

    private fun cambiarEstadoEmbarque(
        pasajero: PasajeroControl,
        marcarEmbarcado: Boolean
    ) {

        if (procesando) {
            return
        }

        val usuario =
            auth.currentUser
                ?: return

        procesando = true

        mostrarProcesando(
            true,
            if (marcarEmbarcado) {
                "Confirmando embarque..."
            } else {
                "Desmarcando embarque..."
            }
        )

        val referenciaReserva =
            firestore
                .collection(
                    COLECCION_RESERVAS
                )
                .document(
                    pasajero.reservaId
                )

        val referenciaViaje =
            referenciaViaje()

        firestore
            .runTransaction { transaccion ->

                val documentoReserva =
                    transaccion.get(
                        referenciaReserva
                    )

                val documentoViaje =
                    transaccion.get(
                        referenciaViaje
                    )

                if (!documentoReserva.exists()) {

                    throw IllegalStateException(
                        "La reserva ya no existe."
                    )
                }

                if (!documentoViaje.exists()) {

                    throw IllegalStateException(
                        "El viaje ya no existe."
                    )
                }

                val empresaReserva =
                    leerTexto(
                        documentoReserva,
                        CAMPO_EMPRESA_ID
                    )

                val viajeReserva =
                    leerTexto(
                        documentoReserva,
                        CAMPO_VIAJE_ID
                    )

                if (
                    empresaReserva.isNotBlank() &&
                    empresaReserva != empresaId
                ) {

                    throw IllegalStateException(
                        "La reserva pertenece a otra empresa."
                    )
                }

                if (viajeReserva != viajeId) {

                    throw IllegalStateException(
                        "La reserva pertenece a otro viaje."
                    )
                }

                val estadoReserva =
                    leerTexto(
                        documentoReserva,
                        CAMPO_ESTADO_RESERVA
                    ).ifBlank {

                        leerTexto(
                            documentoReserva,
                            CAMPO_ESTADO
                        )
                    }.lowercase(Locale.ROOT)

                if (
                    estadoReserva ==
                    ESTADO_CANCELADA ||

                    estadoReserva ==
                    ESTADO_VENCIDA
                ) {

                    throw IllegalStateException(
                        "La reserva está cancelada o vencida."
                    )
                }

                val yaEmbarcado =
                    leerBooleano(
                        documentoReserva,
                        CAMPO_EMBARQUE_CONFIRMADO,
                        false
                    ) ||
                            estadoReserva ==
                            ESTADO_EMBARCADO

                if (
                    yaEmbarcado ==
                    marcarEmbarcado
                ) {

                    return@runTransaction false
                }

                val cantidad =
                    max(
                        1,
                        leerEntero(
                            documentoReserva,
                            CAMPO_CANTIDAD_PASAJEROS,
                            1
                        )
                    )

                val embarcadosActuales =
                    leerEntero(
                        documentoViaje,
                        CAMPO_PASAJEROS_EMBARCADOS,
                        0
                    )

                val capacidad =
                    leerEntero(
                        documentoViaje,
                        CAMPO_CAPACIDAD,
                        Int.MAX_VALUE
                    )

                val nuevosEmbarcados =
                    if (marcarEmbarcado) {

                        min(
                            capacidad,
                            embarcadosActuales +
                                    cantidad
                        )

                    } else {

                        max(
                            0,
                            embarcadosActuales -
                                    cantidad
                        )
                    }

                val cambiosReserva =
                    if (marcarEmbarcado) {

                        mapOf(
                            CAMPO_ESTADO_RESERVA to
                                    ESTADO_EMBARCADO,

                            CAMPO_EMBARQUE_CONFIRMADO to
                                    true,

                            CAMPO_FECHA_EMBARQUE to
                                    FieldValue.serverTimestamp(),

                            CAMPO_CONTROLADO_POR_UID to
                                    usuario.uid,

                            CAMPO_CONTROLADO_POR_NOMBRE to
                                    vendedorNombre,

                            CAMPO_FECHA_ACTUALIZACION to
                                    FieldValue.serverTimestamp()
                        )

                    } else {

                        mapOf(
                            CAMPO_ESTADO_RESERVA to
                                    ESTADO_CONFIRMADA,

                            CAMPO_EMBARQUE_CONFIRMADO to
                                    false,

                            CAMPO_FECHA_EMBARQUE to
                                    FieldValue.delete(),

                            CAMPO_CONTROLADO_POR_UID to
                                    FieldValue.delete(),

                            CAMPO_CONTROLADO_POR_NOMBRE to
                                    FieldValue.delete(),

                            CAMPO_FECHA_ACTUALIZACION to
                                    FieldValue.serverTimestamp()
                        )
                    }

                transaccion.update(
                    referenciaReserva,
                    cambiosReserva
                )

                transaccion.update(
                    referenciaViaje,
                    mapOf(
                        CAMPO_PASAJEROS_EMBARCADOS to
                                nuevosEmbarcados,

                        CAMPO_FECHA_ACTUALIZACION to
                                FieldValue.serverTimestamp()
                    )
                )

                true
            }
            .addOnSuccessListener {

                procesando = false
                mostrarProcesando(false)

                Toast.makeText(
                    this,
                    if (marcarEmbarcado) {
                        "Pasajero marcado como embarcado."
                    } else {
                        "Pasajero marcado como pendiente."
                    },
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { error ->

                procesando = false
                mostrarProcesando(false)

                Toast.makeText(
                    this,
                    "No se pudo actualizar: ${
                        error.localizedMessage
                            ?: "error desconocido"
                    }",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // -------------------------------------------------
    // LISTO PARA SALIR
    // -------------------------------------------------

    private fun actualizarBotonListoSalir() {

        val hayPasajeros =
            listaPasajeros.isNotEmpty()

        val todosEmbarcados =
            hayPasajeros &&
                    listaPasajeros.all {
                        it.embarcado
                    }

        val viajeYaListo =
            estadoViajeActual ==
                    ESTADO_LISTO_SALIR

        binding.btnListoSalir.text =
            if (viajeYaListo) {

                "VIAJE LISTO PARA SALIR"

            } else {

                "MARCAR LISTO PARA SALIR"
            }

        binding.btnListoSalir.isEnabled =
            accionesHabilitadas &&
                    todosEmbarcados &&
                    !viajeYaListo

        binding.btnListoSalir.alpha =
            if (
                binding.btnListoSalir
                    .isEnabled
            ) {
                1f
            } else {
                0.5f
            }
    }

    private fun confirmarListoParaSalir() {

        val total =
            listaPasajeros.sumOf {
                it.cantidadPasajeros
            }

        if (total == 0) {

            Toast.makeText(
                this,
                "El viaje todavía no tiene pasajeros.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val pendientes =
            listaPasajeros.any {
                !it.embarcado
            }

        if (pendientes) {

            Toast.makeText(
                this,
                "Aún existen pasajeros pendientes de embarque.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(
                "Viaje listo para salir"
            )
            .setMessage(
                "Todos los pasajeros están embarcados.\n\n" +
                        "¿Deseas marcar el viaje como listo para salir?"
            )
            .setNegativeButton(
                "CANCELAR",
                null
            )
            .setPositiveButton(
                "MARCAR LISTO"
            ) { _, _ ->

                verificarReservasYMarcarListo()
            }
            .show()
    }

    private fun verificarReservasYMarcarListo() {

        if (procesando) {
            return
        }

        procesando = true

        mostrarProcesando(
            true,
            "Verificando pasajeros..."
        )

        firestore
            .collection(
                COLECCION_RESERVAS
            )
            .whereEqualTo(
                CAMPO_VIAJE_ID,
                viajeId
            )
            .get()
            .addOnSuccessListener { resultado ->

                val reservasActivas =
                    resultado.documents.filter {
                            documento ->

                        val empresaReserva =
                            leerTexto(
                                documento,
                                CAMPO_EMPRESA_ID
                            )

                        val estado =
                            leerTexto(
                                documento,
                                CAMPO_ESTADO_RESERVA
                            ).ifBlank {

                                leerTexto(
                                    documento,
                                    CAMPO_ESTADO
                                )
                            }.lowercase(Locale.ROOT)

                        val empresaCorrecta =
                            empresaReserva.isBlank() ||
                                    empresaReserva ==
                                    empresaId

                        empresaCorrecta &&
                                estado !=
                                ESTADO_CANCELADA &&
                                estado !=
                                ESTADO_VENCIDA
                    }

                if (reservasActivas.isEmpty()) {

                    procesando = false
                    mostrarProcesando(false)

                    Toast.makeText(
                        this,
                        "El viaje no tiene reservas activas.",
                        Toast.LENGTH_LONG
                    ).show()

                    return@addOnSuccessListener
                }

                val existePendiente =
                    reservasActivas.any {
                            documento ->

                        val estado =
                            leerTexto(
                                documento,
                                CAMPO_ESTADO_RESERVA
                            ).ifBlank {

                                leerTexto(
                                    documento,
                                    CAMPO_ESTADO
                                )
                            }.lowercase(Locale.ROOT)

                        val embarcado =
                            leerBooleano(
                                documento,
                                CAMPO_EMBARQUE_CONFIRMADO,
                                false
                            ) ||
                                    estado ==
                                    ESTADO_EMBARCADO

                        !embarcado
                    }

                if (existePendiente) {

                    procesando = false
                    mostrarProcesando(false)

                    Toast.makeText(
                        this,
                        "Todavía existen pasajeros pendientes.",
                        Toast.LENGTH_LONG
                    ).show()

                    return@addOnSuccessListener
                }

                marcarViajeListo()
            }
            .addOnFailureListener { error ->

                procesando = false
                mostrarProcesando(false)

                Toast.makeText(
                    this,
                    "No se pudieron verificar las reservas: ${
                        error.localizedMessage
                            ?: "error desconocido"
                    }",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun marcarViajeListo() {

        val usuario =
            auth.currentUser

        if (usuario == null) {

            procesando = false
            mostrarProcesando(false)
            finish()
            return
        }

        val referencia =
            referenciaViaje()

        firestore
            .runTransaction { transaccion ->

                val documento =
                    transaccion.get(
                        referencia
                    )

                if (!documento.exists()) {

                    throw IllegalStateException(
                        "El viaje ya no existe."
                    )
                }

                val empresaViaje =
                    leerTexto(
                        documento,
                        CAMPO_EMPRESA_ID
                    )

                if (
                    empresaViaje.isNotBlank() &&
                    empresaViaje != empresaId
                ) {

                    throw IllegalStateException(
                        "El viaje pertenece a otra empresa."
                    )
                }

                val estado =
                    leerTexto(
                        documento,
                        CAMPO_ESTADO_OPERACION
                    ).ifBlank {

                        leerTexto(
                            documento,
                            CAMPO_ESTADO
                        )
                    }.lowercase(Locale.ROOT)

                if (
                    estado ==
                    ESTADO_EN_VIAJE ||

                    estado ==
                    ESTADO_FINALIZADO ||

                    estado ==
                    ESTADO_CANCELADO
                ) {

                    throw IllegalStateException(
                        "El viaje ya no puede modificarse."
                    )
                }

                transaccion.update(
                    referencia,
                    mapOf(
                        CAMPO_ESTADO to
                                ESTADO_LISTO_SALIR,

                        CAMPO_ESTADO_OPERACION to
                                ESTADO_LISTO_SALIR,

                        CAMPO_LISTO_PARA_SALIR to
                                true,

                        CAMPO_FECHA_LISTO_SALIR to
                                FieldValue.serverTimestamp(),

                        CAMPO_CONTROLADO_POR_UID to
                                usuario.uid,

                        CAMPO_CONTROLADO_POR_NOMBRE to
                                vendedorNombre,

                        CAMPO_FECHA_ACTUALIZACION to
                                FieldValue.serverTimestamp()
                    )
                )
            }
            .addOnSuccessListener {

                procesando = false
                mostrarProcesando(false)

                MaterialAlertDialogBuilder(this)
                    .setTitle(
                        "Viaje listo"
                    )
                    .setMessage(
                        "El viaje fue marcado correctamente " +
                                "como listo para salir."
                    )
                    .setCancelable(false)
                    .setPositiveButton(
                        "ENTENDIDO"
                    ) { _, _ ->

                        finish()
                    }
                    .show()
            }
            .addOnFailureListener { error ->

                procesando = false
                mostrarProcesando(false)

                Toast.makeText(
                    this,
                    "No se pudo actualizar el viaje: ${
                        error.localizedMessage
                            ?: "error desconocido"
                    }",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // -------------------------------------------------
    // UTILIDADES
    // -------------------------------------------------

    private fun referenciaViaje() =
        firestore
            .collection(
                COLECCION_EMPRESAS
            )
            .document(
                empresaId
            )
            .collection(
                SUBCOLECCION_VIAJES
            )
            .document(
                viajeId
            )

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

    private fun mostrarProcesando(
        mostrar: Boolean,
        mensaje: String = "Procesando..."
    ) {

        binding.contenedorProcesando.visibility =
            if (mostrar) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.tvMensajeProcesando.text =
            mensaje
    }

    private fun removerListeners() {

        listenerPerfil?.remove()
        listenerPerfil = null

        listenerViaje?.remove()
        listenerViaje = null

        listenerReservas?.remove()
        listenerReservas = null
    }

    override fun onStop() {

        removerListeners()

        super.onStop()
    }

    companion object {

        const val EXTRA_EMPRESA_ID =
            "extra_empresa_id"

        const val EXTRA_VIAJE_ID =
            "extra_viaje_id"

        private const val COLECCION_USUARIOS =
            "usuariosgestionpasajes"

        private const val COLECCION_EMPRESAS =
            "empresaspasajes"

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

        private const val CAMPO_VIAJE_ID =
            "viajeId"

        private const val CAMPO_NOMBRES =
            "nombres"

        private const val CAMPO_APELLIDOS =
            "apellidos"

        private const val CAMPO_NOMBRE_COMPLETO =
            "nombreCompleto"

        private const val CAMPO_ORIGEN =
            "origen"

        private const val CAMPO_DESTINO =
            "destino"

        private const val CAMPO_FECHA_SALIDA =
            "fechaSalida"

        private const val CAMPO_HORA_SALIDA =
            "horaSalida"

        private const val CAMPO_PASAJERO_NOMBRE =
            "pasajeroNombre"

        private const val CAMPO_PASAJERO_DNI =
            "pasajeroDni"

        private const val CAMPO_PASAJERO_CELULAR =
            "pasajeroCelular"

        private const val CAMPO_DNI =
            "dni"

        private const val CAMPO_CELULAR =
            "celular"

        private const val CAMPO_NUMERO_ASIENTO =
            "numeroAsiento"

        private const val CAMPO_CANTIDAD_PASAJEROS =
            "cantidadPasajeros"

        private const val CAMPO_ESTADO_RESERVA =
            "estadoReserva"

        private const val CAMPO_EMBARQUE_CONFIRMADO =
            "embarqueConfirmado"

        private const val CAMPO_FECHA_EMBARQUE =
            "fechaEmbarque"

        private const val CAMPO_PASAJEROS_EMBARCADOS =
            "pasajerosEmbarcados"

        private const val CAMPO_CAPACIDAD =
            "capacidad"

        private const val CAMPO_CONTROLADO_POR_UID =
            "controladoPorUid"

        private const val CAMPO_CONTROLADO_POR_NOMBRE =
            "controladoPorNombre"

        private const val CAMPO_FECHA_ACTUALIZACION =
            "fechaActualizacion"

        private const val CAMPO_LISTO_PARA_SALIR =
            "listoParaSalir"

        private const val CAMPO_FECHA_LISTO_SALIR =
            "fechaListoParaSalir"

        private const val ROL_VENDEDOR =
            "vendedor"

        private const val ESTADO_ACTIVO =
            "activo"

        private const val ESTADO_CONFIRMADA =
            "confirmada"

        private const val ESTADO_EMBARCADO =
            "embarcado"

        private const val ESTADO_CANCELADA =
            "cancelada"

        private const val ESTADO_VENCIDA =
            "vencida"

        private const val ESTADO_LISTO_SALIR =
            "listo_para_salir"

        private const val ESTADO_EN_VIAJE =
            "en_viaje"

        private const val ESTADO_FINALIZADO =
            "finalizado"

        private const val ESTADO_CANCELADO =
            "cancelado"

        private val ESTADOS_VIAJE_BLOQUEADOS =
            setOf(
                ESTADO_LISTO_SALIR,
                ESTADO_EN_VIAJE,
                ESTADO_FINALIZADO,
                ESTADO_CANCELADO
            )
    }
}