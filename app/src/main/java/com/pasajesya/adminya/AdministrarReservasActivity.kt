package com.pasajesya.adminya

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.View
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.pasajesya.adminya.databinding.ActivityAdministrarReservasBinding
import java.util.Locale
import kotlin.math.max
import android.graphics.Color
import android.widget.TextView
import com.google.android.material.card.MaterialCardView

class AdministrarReservasActivity : AppCompatActivity() {

    private lateinit var binding:
            ActivityAdministrarReservasBinding

    private lateinit var auth:
            FirebaseAuth

    private lateinit var firestore:
            FirebaseFirestore

    private lateinit var adapter:
            ReservaPagoAdapter

    private var listenerPerfil:
            ListenerRegistration? = null

    private var listenerViaje:
            ListenerRegistration? = null

    private var listenerReservas:
            ListenerRegistration? = null

    private var empresaId = ""
    private var viajeId = ""

    private var vendedorNombre = ""

    private var filtroActual =
        FILTRO_TODAS

    private var reservasCompletas:
            List<ReservaPagoAdmin> = emptyList()

    private var datosIniciados = false
    private var procesando = false

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        binding =
            ActivityAdministrarReservasBinding.inflate(
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
        configurarFiltros()
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
            ReservaPagoAdapter(

                alConfirmarPago = { reserva ->
                    confirmarPago(reserva)
                },

                alCancelar = { reserva ->
                    confirmarCancelacion(reserva)
                },

                alVerDetalle = { reserva ->
                    mostrarDetalle(reserva)
                }
            )

        binding.rvReservas.layoutManager =
            LinearLayoutManager(this)

        binding.rvReservas.adapter =
            adapter

        binding.rvReservas
            .setHasFixedSize(false)
    }

    private fun configurarFiltros() {

        binding.chipTodas.setOnClickListener {
            filtroActual = FILTRO_TODAS
            aplicarFiltro()
        }

        binding.chipPendientes.setOnClickListener {
            filtroActual = ESTADO_PENDIENTE
            aplicarFiltro()
        }

        binding.chipConfirmadas.setOnClickListener {
            filtroActual = ESTADO_CONFIRMADA
            aplicarFiltro()
        }

        binding.chipCanceladas.setOnClickListener {
            filtroActual = ESTADO_CANCELADA
            aplicarFiltro()
        }
    }

    private fun configurarEventos() {

        binding.btnVolver.setOnClickListener {
            finish()
        }

        binding.etBuscar.doAfterTextChanged {
            aplicarFiltro()
        }

        binding.btnVenderOficina.setOnClickListener {

            val intent =
                Intent(
                    this,
                    VentaOficinaActivity::class.java
                ).apply {

                    putExtra(
                        VentaOficinaActivity.EXTRA_EMPRESA_ID,
                        empresaId
                    )

                    putExtra(
                        VentaOficinaActivity.EXTRA_VIAJE_ID,
                        viajeId
                    )
                }

            startActivity(intent)
        }
    }

    private fun prepararPantalla() {

        binding.tvRuta.text =
            "Cargando viaje..."

        binding.tvFechaHora.text =
            "Cargando fecha y hora..."

        binding.tvResumenAsientos.text =
            "Cargando..."

        binding.tvPendientes.text = "0"
        binding.tvConfirmadas.text = "0"
        binding.tvCanceladas.text = "0"

        binding.tvTotalRegistros.text =
            "0 reservas"

        binding.progressCargando.visibility =
            View.VISIBLE

        binding.rvReservas.visibility =
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
                .collection(COLECCION_USUARIOS)
                .document(usuario.uid)
                .addSnapshotListener { documento, error ->

                    if (error != null) {

                        Toast.makeText(
                            this,
                            "No se pudo validar tu cuenta.",
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

                    validarPerfil(documento)
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
                .addSnapshotListener { documento, error ->

                    if (error != null) {

                        Toast.makeText(
                            this,
                            "No se pudo cargar el viaje.",
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

                    actualizarMapaAsientos(documento)
                }
    }

    // -------------------------------------------------
    // RESERVAS
    // -------------------------------------------------

    private fun escucharReservas() {

        listenerReservas?.remove()

        listenerReservas =
            firestore
                .collection(COLECCION_RESERVAS)
                .whereEqualTo(
                    CAMPO_VIAJE_ID,
                    viajeId
                )
                .addSnapshotListener { resultado, error ->

                    binding.progressCargando.visibility =
                        View.GONE

                    if (error != null) {

                        Toast.makeText(
                            this,
                            "No se pudieron cargar las reservas: ${
                                error.localizedMessage
                                    ?: "error desconocido"
                            }",
                            Toast.LENGTH_LONG
                        ).show()

                        return@addSnapshotListener
                    }

                    reservasCompletas =
                        resultado
                            ?.documents
                            ?.map { documento ->
                                convertirReserva(documento)
                            }
                            ?.filter { reserva ->
                                reserva.empresaId == empresaId
                            }
                            ?.sortedByDescending {
                                it.fechaOrden
                            }
                            .orEmpty()

                    actualizarResumen()
                    aplicarFiltro()
                }
    }

    private fun convertirReserva(
        documento: DocumentSnapshot
    ): ReservaPagoAdmin {

        val nombre =
            leerTexto(
                documento,
                CAMPO_PASAJERO_NOMBRE
            ).ifBlank {

                leerTexto(
                    documento,
                    CAMPO_NOMBRE_PASAJERO
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

        return ReservaPagoAdmin(

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

            usuarioId =
                leerTexto(
                    documento,
                    CAMPO_USUARIO_ID
                ),

            usuarioCorreo =
                leerTexto(
                    documento,
                    CAMPO_USUARIO_CORREO
                ),

            pasajeroNombre =
                nombre,

            pasajeroDni =
                dni,

            pasajeroCelular =
                celular,

            asiento =
                leerTexto(
                    documento,
                    CAMPO_ASIENTO
                ).uppercase(Locale.ROOT),

            precio =
                leerDouble(
                    documento,
                    CAMPO_PRECIO,
                    0.0
                ),

            estadoReserva =
                leerTexto(
                    documento,
                    CAMPO_ESTADO_RESERVA
                ).ifBlank {
                    ESTADO_PENDIENTE
                }.lowercase(Locale.ROOT),

            estadoPago =
                leerTexto(
                    documento,
                    CAMPO_ESTADO_PAGO
                ).ifBlank {
                    ESTADO_PAGO_PENDIENTE
                }.lowercase(Locale.ROOT),

            estadoAbordaje =
                leerTexto(
                    documento,
                    CAMPO_ESTADO_ABORDAJE
                ).ifBlank {
                    ESTADO_ABORDAJE_PENDIENTE
                }.lowercase(Locale.ROOT),

            boletoValidado =
                leerBooleano(
                    documento,
                    CAMPO_BOLETO_VALIDADO,
                    false
                ),

            fechaOrden =
                documento
                    .getTimestamp(
                        CAMPO_FECHA_RESERVA
                    )
                    ?.toDate()
                    ?.time
                    ?: 0L
        )
    }

    private fun actualizarResumen() {

        val pendientes =
            reservasCompletas.count {
                it.estadoReserva ==
                        ESTADO_PENDIENTE
            }

        val confirmadas =
            reservasCompletas.count {
                it.estadoReserva ==
                        ESTADO_CONFIRMADA
            }

        val canceladas =
            reservasCompletas.count {
                it.estadoReserva ==
                        ESTADO_CANCELADA ||
                        it.estadoReserva ==
                        ESTADO_VENCIDA
            }

        binding.tvPendientes.text =
            pendientes.toString()

        binding.tvConfirmadas.text =
            confirmadas.toString()

        binding.tvCanceladas.text =
            canceladas.toString()
    }

    private fun aplicarFiltro() {

        val consulta =
            binding.etBuscar.text
                ?.toString()
                ?.trim()
                ?.lowercase(Locale.ROOT)
                .orEmpty()

        val listaFiltrada =
            reservasCompletas.filter { reserva ->

                val coincideEstado =
                    when (filtroActual) {

                        FILTRO_TODAS -> {
                            true
                        }

                        ESTADO_CANCELADA -> {
                            reserva.estadoReserva ==
                                    ESTADO_CANCELADA ||
                                    reserva.estadoReserva ==
                                    ESTADO_VENCIDA
                        }

                        else -> {
                            reserva.estadoReserva ==
                                    filtroActual
                        }
                    }

                val coincideBusqueda =
                    consulta.isBlank() ||

                            reserva.pasajeroNombre
                                .lowercase(Locale.ROOT)
                                .contains(consulta) ||

                            reserva.pasajeroDni
                                .contains(consulta) ||

                            reserva.pasajeroCelular
                                .contains(consulta) ||

                            reserva.usuarioCorreo
                                .lowercase(Locale.ROOT)
                                .contains(consulta) ||

                            reserva.asiento
                                .lowercase(Locale.ROOT)
                                .contains(consulta)

                coincideEstado &&
                        coincideBusqueda
            }

        adapter.actualizarLista(
            listaFiltrada
        )

        val cantidad =
            listaFiltrada.size

        binding.tvTotalRegistros.text =
            when (cantidad) {

                0 -> "0 reservas"
                1 -> "1 reserva"
                else -> "$cantidad reservas"
            }

        binding.rvReservas.visibility =
            if (cantidad == 0) {
                View.GONE
            } else {
                View.VISIBLE
            }

        binding.contenedorVacio.visibility =
            if (cantidad == 0) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.tvMensajeVacio.text =
            if (
                consulta.isNotBlank() ||
                filtroActual != FILTRO_TODAS
            ) {

                "No existen reservas con este filtro."

            } else {

                "Todavía no existen reservas para este viaje."
            }
    }

    // -------------------------------------------------
    // CONFIRMAR PAGO
    // -------------------------------------------------

    private fun confirmarPago(
        reserva: ReservaPagoAdmin
    ) {

        MaterialAlertDialogBuilder(this)
            .setTitle("Confirmar pago")
            .setMessage(
                "¿Confirmas que recibiste el pago?\n\n" +
                        "Usuario: ${
                            reserva.pasajeroNombre.ifBlank {
                                reserva.usuarioCorreo
                            }
                        }\n" +
                        "Asiento: ${reserva.asiento}\n" +
                        String.format(
                            Locale("es", "PE"),
                            "Monto: S/ %.2f",
                            reserva.precio
                        ) +
                        "\n\nEl usuario verá inmediatamente " +
                        "su pasaje como confirmado."
            )
            .setNegativeButton(
                "CANCELAR",
                null
            )
            .setPositiveButton(
                "CONFIRMAR PAGO"
            ) { _, _ ->

                ejecutarConfirmacionPago(
                    reserva
                )
            }
            .show()
    }

    private fun ejecutarConfirmacionPago(
        reserva: ReservaPagoAdmin
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
            "Confirmando pago..."
        )

        val referencia =
            firestore
                .collection(COLECCION_RESERVAS)
                .document(reserva.reservaId)

        firestore
            .runTransaction { transaccion ->

                val documento =
                    transaccion.get(referencia)

                if (!documento.exists()) {

                    throw IllegalStateException(
                        "La reserva ya no existe."
                    )
                }

                val empresaReserva =
                    leerTexto(
                        documento,
                        CAMPO_EMPRESA_ID
                    )

                val viajeReserva =
                    leerTexto(
                        documento,
                        CAMPO_VIAJE_ID
                    )

                val estadoReserva =
                    leerTexto(
                        documento,
                        CAMPO_ESTADO_RESERVA
                    ).lowercase(Locale.ROOT)

                val estadoAbordaje =
                    leerTexto(
                        documento,
                        CAMPO_ESTADO_ABORDAJE
                    ).lowercase(Locale.ROOT)

                val boletoValidado =
                    leerBooleano(
                        documento,
                        CAMPO_BOLETO_VALIDADO,
                        false
                    )

                if (
                    empresaReserva != empresaId ||
                    viajeReserva != viajeId
                ) {

                    throw IllegalStateException(
                        "La reserva pertenece a otro viaje."
                    )
                }

                if (
                    estadoReserva !=
                    ESTADO_PENDIENTE
                ) {

                    throw IllegalStateException(
                        "La reserva ya no está pendiente."
                    )
                }

                if (
                    estadoAbordaje ==
                    ESTADO_ABORDO ||
                    boletoValidado
                ) {

                    throw IllegalStateException(
                        "El pasajero ya tiene el abordaje confirmado."
                    )
                }

                transaccion.update(
                    referencia,
                    mapOf(
                        CAMPO_ESTADO_RESERVA to
                                ESTADO_CONFIRMADA,

                        CAMPO_ESTADO_PAGO to
                                ESTADO_PAGO_CONFIRMADO,

                        CAMPO_ESTADO_ABORDAJE to
                                ESTADO_ABORDAJE_PENDIENTE,

                        CAMPO_BOLETO_VALIDADO to
                                false,

                        CAMPO_FECHA_CONFIRMACION_PAGO to
                                FieldValue.serverTimestamp(),

                        CAMPO_CONFIRMADO_POR_UID to
                                usuario.uid,

                        CAMPO_CONFIRMADO_POR_NOMBRE to
                                vendedorNombre,

                        CAMPO_FECHA_ACTUALIZACION to
                                FieldValue.serverTimestamp(),

                        CAMPO_ULTIMA_ACTUALIZACION to
                                FieldValue.serverTimestamp()
                    )
                )
            }
            .addOnSuccessListener {

                procesando = false
                mostrarProcesando(false)

                Toast.makeText(
                    this,
                    "Pago confirmado correctamente.",
                    Toast.LENGTH_LONG
                ).show()
            }
            .addOnFailureListener { error ->

                procesando = false
                mostrarProcesando(false)

                Toast.makeText(
                    this,
                    error.localizedMessage
                        ?: "No se pudo confirmar el pago.",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // -------------------------------------------------
    // CANCELAR RESERVA
    // -------------------------------------------------

    private fun confirmarCancelacion(
        reserva: ReservaPagoAdmin
    ) {

        MaterialAlertDialogBuilder(this)
            .setTitle("Cancelar reserva")
            .setMessage(
                "¿Deseas cancelar esta reserva?\n\n" +
                        "Asiento: ${reserva.asiento}\n" +
                        "Usuario: ${
                            reserva.pasajeroNombre.ifBlank {
                                reserva.usuarioCorreo
                            }
                        }\n\n" +
                        "El asiento volverá a estar disponible."
            )
            .setNegativeButton(
                "NO",
                null
            )
            .setPositiveButton(
                "SÍ, CANCELAR"
            ) { _, _ ->

                cancelarReserva(reserva)
            }
            .show()
    }

    private fun cancelarReserva(
        reserva: ReservaPagoAdmin
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
            "Cancelando reserva..."
        )

        val referenciaReserva =
            firestore
                .collection(COLECCION_RESERVAS)
                .document(reserva.reservaId)

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

                val estadoReserva =
                    leerTexto(
                        documentoReserva,
                        CAMPO_ESTADO_RESERVA
                    ).lowercase(Locale.ROOT)

                val estadoAbordaje =
                    leerTexto(
                        documentoReserva,
                        CAMPO_ESTADO_ABORDAJE
                    ).lowercase(Locale.ROOT)

                val boletoValidado =
                    leerBooleano(
                        documentoReserva,
                        CAMPO_BOLETO_VALIDADO,
                        false
                    )

                if (
                    empresaReserva != empresaId ||
                    viajeReserva != viajeId
                ) {

                    throw IllegalStateException(
                        "La reserva pertenece a otro viaje."
                    )
                }

                if (
                    estadoReserva ==
                    ESTADO_CANCELADA ||
                    estadoReserva ==
                    ESTADO_VENCIDA
                ) {

                    throw IllegalStateException(
                        "La reserva ya está cancelada."
                    )
                }

                if (
                    estadoAbordaje ==
                    ESTADO_ABORDO ||
                    boletoValidado
                ) {

                    throw IllegalStateException(
                        "No se puede cancelar porque el pasajero ya abordó."
                    )
                }

                if (documentoViaje.exists()) {

                    val capacidad =
                        max(
                            1,
                            leerEntero(
                                documentoViaje,
                                CAMPO_CAPACIDAD,
                                4
                            )
                        )

                    val asiento =
                        normalizarAsiento(
                            leerTexto(
                                documentoReserva,
                                CAMPO_ASIENTO
                            ),
                            capacidad
                        )

                    val listaAsientos =
                        leerListaTextos(
                            documentoViaje,
                            CAMPO_ASIENTOS_RESERVADOS
                        )
                            .map { codigo ->
                                normalizarAsiento(
                                    codigo,
                                    capacidad
                                )
                            }
                            .filter { codigo ->
                                codigo.isNotBlank()
                            }
                            .distinct()
                            .toMutableList()

                    listaAsientos.removeAll { codigo ->
                        codigo == asiento
                    }

                    val disponiblesDocumento =
                        leerListaTextos(
                            documentoViaje,
                            CAMPO_ASIENTOS_DISPONIBLES
                        )

                    val todosLosAsientos =
                        obtenerAsientosTotales(
                            capacidad = capacidad,
                            disponiblesDocumento =
                                disponiblesDocumento,
                            reservados = listaAsientos
                        )

                    val asientosDisponibles =
                        todosLosAsientos.filter { codigo ->
                            !listaAsientos.contains(codigo)
                        }

                    transaccion.update(
                        referenciaViaje,
                        mapOf(
                            CAMPO_ASIENTOS_RESERVADOS to
                                    listaAsientos,

                            CAMPO_ASIENTOS_DISPONIBLES to
                                    asientosDisponibles,

                            CAMPO_ASIENTOS_OCUPADOS to
                                    listaAsientos.size,

                            CAMPO_CUPOS_DISPONIBLES to
                                    max(
                                        0,
                                        capacidad -
                                                listaAsientos.size
                                    ),

                            CAMPO_FECHA_ACTUALIZACION to
                                    FieldValue.serverTimestamp(),

                            CAMPO_ULTIMA_ACTUALIZACION to
                                    FieldValue.serverTimestamp()
                        )
                    )
                }

                transaccion.update(
                    referenciaReserva,
                    mapOf(
                        CAMPO_ESTADO_RESERVA to
                                ESTADO_CANCELADA,

                        CAMPO_ESTADO_PAGO to
                                ESTADO_PAGO_CANCELADO,

                        CAMPO_ESTADO_ABORDAJE to
                                ESTADO_ABORDAJE_CANCELADO,

                        CAMPO_BOLETO_VALIDADO to
                                false,

                        CAMPO_FECHA_CANCELACION to
                                FieldValue.serverTimestamp(),

                        CAMPO_CANCELADO_POR_UID to
                                usuario.uid,

                        CAMPO_CANCELADO_POR_NOMBRE to
                                vendedorNombre,

                        CAMPO_FECHA_ACTUALIZACION to
                                FieldValue.serverTimestamp(),

                        CAMPO_ULTIMA_ACTUALIZACION to
                                FieldValue.serverTimestamp()
                    )
                )
            }
            .addOnSuccessListener {

                procesando = false
                mostrarProcesando(false)

                Toast.makeText(
                    this,
                    "Reserva cancelada y asiento liberado.",
                    Toast.LENGTH_LONG
                ).show()
            }
            .addOnFailureListener { error ->

                procesando = false
                mostrarProcesando(false)

                Toast.makeText(
                    this,
                    error.localizedMessage
                        ?: "No se pudo cancelar la reserva.",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // -------------------------------------------------
    // DETALLE
    // -------------------------------------------------

    private fun mostrarDetalle(
        reserva: ReservaPagoAdmin
    ) {

        val abordaje =
            if (
                reserva.estadoAbordaje ==
                ESTADO_ABORDO ||
                reserva.boletoValidado
            ) {
                "Abordó"
            } else {
                reserva.estadoAbordaje
                    .ifBlank {
                        ESTADO_ABORDAJE_PENDIENTE
                    }
            }

        val mensaje =
            buildString {

                append(
                    "Usuario: ${
                        reserva.pasajeroNombre.ifBlank {
                            reserva.usuarioCorreo
                        }
                    }\n"
                )

                append(
                    "Correo: ${
                        reserva.usuarioCorreo.ifBlank {
                            "No registrado"
                        }
                    }\n"
                )

                append(
                    "DNI: ${
                        reserva.pasajeroDni.ifBlank {
                            "No registrado"
                        }
                    }\n"
                )

                append(
                    "Celular: ${
                        reserva.pasajeroCelular.ifBlank {
                            "No registrado"
                        }
                    }\n\n"
                )

                append(
                    "Asiento: ${reserva.asiento}\n"
                )

                append(
                    String.format(
                        Locale("es", "PE"),
                        "Precio: S/ %.2f\n\n",
                        reserva.precio
                    )
                )

                append(
                    "Reserva: ${reserva.estadoReserva}\n"
                )

                append(
                    "Pago: ${reserva.estadoPago}\n"
                )

                append(
                    "Abordaje: $abordaje"
                )
            }

        MaterialAlertDialogBuilder(this)
            .setTitle("Detalle de la reserva")
            .setMessage(mensaje)
            .setPositiveButton(
                "CERRAR",
                null
            )
            .show()
    }


    // -------------------------------------------------
    // MAPA VISUAL DE ASIENTOS
    // -------------------------------------------------

    private fun actualizarMapaAsientos(
        documento: DocumentSnapshot
    ) {

        val capacidad =
            max(
                1,
                leerEntero(
                    documento,
                    CAMPO_CAPACIDAD,
                    4
                )
            )

        val asientosReservados =
            leerListaTextos(
                documento,
                CAMPO_ASIENTOS_RESERVADOS
            )
                .map { asiento ->
                    normalizarAsiento(
                        asiento,
                        capacidad
                    )
                }
                .filter { asiento ->
                    asiento.isNotBlank()
                }
                .toSet()

        actualizarAsientoVisual(
            codigo = "A1",
            ocupado = asientosReservados.contains("A1"),
            card = binding.cardAsientoA1,
            textoEstado = binding.tvEstadoA1
        )

        actualizarAsientoVisual(
            codigo = "B1",
            ocupado = asientosReservados.contains("B1"),
            card = binding.cardAsientoB1,
            textoEstado = binding.tvEstadoB1
        )

        actualizarAsientoVisual(
            codigo = "B2",
            ocupado = asientosReservados.contains("B2"),
            card = binding.cardAsientoB2,
            textoEstado = binding.tvEstadoB2
        )

        actualizarAsientoVisual(
            codigo = "B3",
            ocupado = asientosReservados.contains("B3"),
            card = binding.cardAsientoB3,
            textoEstado = binding.tvEstadoB3
        )

        val asientosMapa =
            listOf(
                "A1",
                "B1",
                "B2",
                "B3"
            )

        val libresMapa =
            asientosMapa.count { asiento ->
                !asientosReservados.contains(asiento)
            }

        binding.tvResumenAsientos.text =
            when (libresMapa) {

                0 -> {
                    "Unidad completa"
                }

                1 -> {
                    "1 asiento libre"
                }

                else -> {
                    "$libresMapa asientos libres"
                }
            }
    }

    private fun actualizarAsientoVisual(
        codigo: String,
        ocupado: Boolean,
        card: MaterialCardView,
        textoEstado: TextView
    ) {

        if (ocupado) {

            card.setCardBackgroundColor(
                Color.parseColor("#FDECEC")
            )

            card.strokeColor =
                getColor(
                    R.color.adminya_danger
                )

            textoEstado.text =
                "OCUPADO"

            textoEstado.setTextColor(
                getColor(
                    R.color.adminya_danger
                )
            )

            card.contentDescription =
                "Asiento $codigo ocupado"

        } else {

            card.setCardBackgroundColor(
                Color.parseColor("#EAF7EF")
            )

            card.strokeColor =
                getColor(
                    R.color.adminya_success
                )

            textoEstado.text =
                "DISPONIBLE"

            textoEstado.setTextColor(
                getColor(
                    R.color.adminya_success
                )
            )

            card.contentDescription =
                "Asiento $codigo disponible"
        }

        card.isClickable = false
        card.isFocusable = false
    }

    private fun normalizarAsiento(
        asientoRecibido: String,
        capacidad: Int = 4
    ): String {

        val asiento =
            asientoRecibido
                .trim()
                .uppercase(Locale.ROOT)

        if (capacidad == 4) {

            return when (asiento) {

                "1" -> "A1"
                "2" -> "B1"
                "3" -> "B2"
                "4" -> "B3"

                else -> asiento
            }
        }

        return asiento
    }

    private fun obtenerAsientosTotales(
        capacidad: Int,
        disponiblesDocumento: List<String>,
        reservados: List<String>
    ): List<String> {

        if (capacidad == 4) {

            return listOf(
                "A1",
                "B1",
                "B2",
                "B3"
            )
        }

        val existentes =
            (
                    disponiblesDocumento +
                            reservados
                    )
                .map { asiento ->
                    asiento
                        .trim()
                        .uppercase(Locale.ROOT)
                }
                .filter { asiento ->
                    asiento.isNotBlank()
                }
                .distinct()

        if (existentes.size >= capacidad) {
            return existentes.take(capacidad)
        }

        return (1..capacidad).map { numero ->
            numero.toString()
        }
    }

    // -------------------------------------------------
    // UTILIDADES
    // -------------------------------------------------

    private fun referenciaViaje() =
        firestore
            .collection(COLECCION_EMPRESAS)
            .document(empresaId)
            .collection(SUBCOLECCION_VIAJES)
            .document(viajeId)

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

            is Number -> valor.toInt()

            is String ->
                valor.toIntOrNull()
                    ?: predeterminado

            else -> predeterminado
        }
    }

    private fun leerDouble(
        documento: DocumentSnapshot,
        campo: String,
        predeterminado: Double
    ): Double {

        return when (
            val valor = documento.get(campo)
        ) {

            is Number -> valor.toDouble()

            is String ->
                valor.replace(",", ".")
                    .toDoubleOrNull()
                    ?: predeterminado

            else -> predeterminado
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
                    ?.takeIf { texto ->
                        texto.isNotBlank()
                    }
            }
            .orEmpty()
    }

    private fun leerBooleano(
        documento: DocumentSnapshot,
        campo: String,
        predeterminado: Boolean
    ): Boolean {

        return when (
            val valor = documento.get(campo)
        ) {

            is Boolean -> valor

            is Number ->
                valor.toInt() != 0

            is String ->
                when (
                    valor.lowercase(Locale.ROOT)
                ) {

                    "true",
                    "1",
                    "si",
                    "sí" -> true

                    "false",
                    "0",
                    "no" -> false

                    else -> predeterminado
                }

            else -> predeterminado
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
            "extra_empresa_id_reservas"

        const val EXTRA_VIAJE_ID =
            "extra_viaje_id_reservas"

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

        private const val CAMPO_USUARIO_ID =
            "usuarioId"

        private const val CAMPO_USUARIO_CORREO =
            "usuarioCorreo"

        private const val CAMPO_PASAJERO_NOMBRE =
            "pasajeroNombre"

        private const val CAMPO_NOMBRE_PASAJERO =
            "nombrePasajero"

        private const val CAMPO_PASAJERO_DNI =
            "pasajeroDni"

        private const val CAMPO_PASAJERO_CELULAR =
            "pasajeroCelular"

        private const val CAMPO_DNI =
            "dni"

        private const val CAMPO_CELULAR =
            "celular"

        private const val CAMPO_ASIENTO =
            "asiento"

        private const val CAMPO_PRECIO =
            "precio"

        private const val CAMPO_ESTADO_RESERVA =
            "estadoReserva"

        private const val CAMPO_ESTADO_PAGO =
            "estadoPago"

        private const val CAMPO_ESTADO_ABORDAJE =
            "estadoAbordaje"

        private const val CAMPO_BOLETO_VALIDADO =
            "boletoValidado"

        private const val CAMPO_FECHA_RESERVA =
            "fechaReserva"

        private const val CAMPO_FECHA_CONFIRMACION_PAGO =
            "fechaConfirmacionPago"

        private const val CAMPO_CONFIRMADO_POR_UID =
            "confirmadoPorUid"

        private const val CAMPO_CONFIRMADO_POR_NOMBRE =
            "confirmadoPorNombre"

        private const val CAMPO_FECHA_CANCELACION =
            "fechaCancelacion"

        private const val CAMPO_CANCELADO_POR_UID =
            "canceladoPorUid"

        private const val CAMPO_CANCELADO_POR_NOMBRE =
            "canceladoPorNombre"

        private const val CAMPO_ASIENTOS_RESERVADOS =
            "asientosReservados"

        private const val CAMPO_ASIENTOS_DISPONIBLES =
            "asientosDisponibles"

        private const val CAMPO_ASIENTOS_OCUPADOS =
            "asientosOcupados"

        private const val CAMPO_CAPACIDAD =
            "capacidad"

        private const val CAMPO_CUPOS_DISPONIBLES =
            "cuposDisponibles"

        private const val CAMPO_FECHA_ACTUALIZACION =
            "fechaActualizacion"

        private const val CAMPO_ULTIMA_ACTUALIZACION =
            "ultimaActualizacion"

        private const val ROL_VENDEDOR =
            "vendedor"

        private const val ESTADO_ACTIVO =
            "activo"

        private const val ESTADO_PENDIENTE =
            "pendiente"

        private const val ESTADO_CONFIRMADA =
            "confirmada"

        private const val ESTADO_CANCELADA =
            "cancelada"

        private const val ESTADO_VENCIDA =
            "vencida"

        private const val ESTADO_PAGO_PENDIENTE =
            "pendiente"

        private const val ESTADO_PAGO_CONFIRMADO =
            "confirmado"

        private const val ESTADO_PAGO_CANCELADO =
            "cancelado"

        private const val ESTADO_ABORDAJE_PENDIENTE =
            "pendiente"

        private const val ESTADO_ABORDAJE_CANCELADO =
            "cancelado"

        private const val ESTADO_ABORDO =
            "abordo"

        private const val FILTRO_TODAS =
            "todas"
    }
}