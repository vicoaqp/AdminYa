package com.pasajesya.adminya

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.util.Patterns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.pasajesya.adminya.databinding.ActivityVentaOficinaBinding
import java.util.Locale
import kotlin.math.max

class VentaOficinaActivity : AppCompatActivity() {

    private lateinit var binding:
            ActivityVentaOficinaBinding

    private lateinit var auth:
            FirebaseAuth

    private lateinit var firestore:
            FirebaseFirestore

    private var listenerPerfil:
            ListenerRegistration? = null

    private var listenerViaje:
            ListenerRegistration? = null

    private var empresaId = ""
    private var viajeId = ""

    private var empresaNombre = ""
    private var vendedorNombre = ""

    private var origenViaje = ""
    private var destinoViaje = ""
    private var fechaSalidaViaje = ""
    private var horaSalidaViaje = ""
    private var puntoEmbarqueViaje = ""
    private var placaViaje = ""
    private var vehiculoViaje = ""

    private var capacidadViaje = 4
    private var precioViaje = 0.0

    private var asientoSeleccionado = ""
    private var metodoPagoSeleccionado = "Efectivo"

    private var asientosDisponibles:
            List<String> = emptyList()

    private var datosIniciados = false
    private var procesando = false

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        binding =
            ActivityVentaOficinaBinding.inflate(
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
        configurarMetodosPago()
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

    private fun configurarMetodosPago() {

        val metodos =
            listOf(
                "Efectivo",
                "Yape",
                "Plin",
                "Transferencia",
                "Otro"
            )

        val adapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                metodos
            )

        binding.actMetodoPago.setAdapter(
            adapter
        )

        binding.actMetodoPago.setText(
            "Efectivo",
            false
        )

        metodoPagoSeleccionado =
            "Efectivo"

        binding.actMetodoPago
            .setOnItemClickListener {
                    _, _, posicion, _ ->

                metodoPagoSeleccionado =
                    metodos.getOrElse(
                        posicion
                    ) {
                        "Efectivo"
                    }

                binding.tilMetodoPago.error =
                    null
            }
    }

    private fun configurarEventos() {

        binding.btnVolver.setOnClickListener {
            finish()
        }

        binding.actAsiento
            .setOnItemClickListener {
                    _, _, posicion, _ ->

                asientoSeleccionado =
                    asientosDisponibles
                        .getOrNull(posicion)
                        .orEmpty()

                binding.tilAsiento.error =
                    null
            }

        binding.btnVenderPasaje
            .setOnClickListener {

                validarFormulario()
            }
    }

    private fun prepararPantalla() {

        binding.tvRuta.text =
            "Cargando viaje..."

        binding.tvFechaHora.text =
            "Cargando fecha y hora..."

        binding.tvAsientosDisponibles.text =
            "Cargando asientos disponibles..."

        mostrarCargando(
            true,
            "Cargando información..."
        )
    }

    // -------------------------------------------------
    // PERFIL DEL VENDEDOR
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

        listenerPerfil?.remove()

        listenerPerfil =
            firestore
                .collection(COLECCION_USUARIOS)
                .document(usuario.uid)
                .addSnapshotListener {
                        documento, error ->

                    if (error != null) {

                        mostrarCargando(false)

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

                        mostrarCargando(false)

                        Toast.makeText(
                            this,
                            "No se encontró tu perfil.",
                            Toast.LENGTH_LONG
                        ).show()

                        finish()
                        return@addSnapshotListener
                    }

                    validarPerfilVendedor(
                        documento
                    )
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

        val empresaPerfil =
            leerTexto(
                documento,
                CAMPO_EMPRESA_ID
            )

        empresaNombre =
            leerTexto(
                documento,
                CAMPO_EMPRESA_NOMBRE
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

                mostrarCargando(false)

                Toast.makeText(
                    this,
                    "Esta sección pertenece al vendedor.",
                    Toast.LENGTH_LONG
                ).show()

                finish()
            }

            estado != ESTADO_ACTIVO -> {

                mostrarCargando(false)

                Toast.makeText(
                    this,
                    "Tu cuenta no está activa.",
                    Toast.LENGTH_LONG
                ).show()

                finish()
            }

            empresaPerfil != empresaId -> {

                mostrarCargando(false)

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
            }
        }
    }

    // -------------------------------------------------
    // VIAJE Y ASIENTOS
    // -------------------------------------------------

    private fun escucharViaje() {

        listenerViaje?.remove()

        listenerViaje =
            referenciaViaje()
                .addSnapshotListener {
                        documento, error ->

                    mostrarCargando(false)

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

                    cargarDatosViaje(
                        documento
                    )
                }
    }

    private fun cargarDatosViaje(
        documento: DocumentSnapshot
    ) {

        empresaNombre =
            leerTexto(
                documento,
                CAMPO_EMPRESA_NOMBRE
            ).ifBlank {
                empresaNombre
            }

        origenViaje =
            leerTexto(
                documento,
                CAMPO_ORIGEN
            )

        destinoViaje =
            leerTexto(
                documento,
                CAMPO_DESTINO
            )

        fechaSalidaViaje =
            leerTexto(
                documento,
                CAMPO_FECHA_SALIDA
            )

        horaSalidaViaje =
            leerTexto(
                documento,
                CAMPO_HORA_SALIDA
            )

        puntoEmbarqueViaje =
            leerTexto(
                documento,
                CAMPO_PUNTO_EMBARQUE
            )

        placaViaje =
            leerTexto(
                documento,
                CAMPO_PLACA
            ).ifBlank {

                leerTexto(
                    documento,
                    CAMPO_VEHICULO_PLACA
                )
            }

        vehiculoViaje =
            leerTexto(
                documento,
                CAMPO_VEHICULO_DESCRIPCION
            ).ifBlank {

                leerTexto(
                    documento,
                    CAMPO_VEHICULO
                )
            }

        capacidadViaje =
            max(
                1,
                leerEntero(
                    documento,
                    CAMPO_CAPACIDAD,
                    4
                )
            )

        precioViaje =
            leerDouble(
                documento,
                CAMPO_PRECIO,
                0.0
            )

        binding.tvRuta.text =
            if (
                origenViaje.isNotBlank() &&
                destinoViaje.isNotBlank()
            ) {

                "$origenViaje → $destinoViaje"

            } else {

                "Ruta no registrada"
            }

        binding.tvFechaHora.text =
            construirFechaHora(
                fechaSalidaViaje,
                horaSalidaViaje
            )

        if (
            binding.etPrecio.text
                ?.toString()
                ?.trim()
                .isNullOrBlank()
        ) {

            binding.etPrecio.setText(
                if (precioViaje > 0.0) {
                    formatearPrecio(
                        precioViaje
                    )
                } else {
                    ""
                }
            )
        }

        actualizarAsientos(
            documento
        )
    }

    private fun actualizarAsientos(
        documento: DocumentSnapshot
    ) {

        val reservados =
            leerListaTextos(
                documento,
                CAMPO_ASIENTOS_RESERVADOS
            )
                .map {
                    normalizarAsiento(
                        it,
                        capacidadViaje
                    )
                }
                .filter {
                    it.isNotBlank()
                }
                .distinct()

        val disponiblesDocumento =
            leerListaTextos(
                documento,
                CAMPO_ASIENTOS_DISPONIBLES
            )
                .map {
                    normalizarAsiento(
                        it,
                        capacidadViaje
                    )
                }
                .filter {
                    it.isNotBlank()
                }

        val todosLosAsientos =
            obtenerAsientosTotales(
                capacidad = capacidadViaje,
                disponiblesDocumento =
                    disponiblesDocumento,
                reservados = reservados
            )

        asientosDisponibles =
            todosLosAsientos.filter {
                    asiento ->

                !reservados.contains(
                    asiento
                )
            }

        val adapterAsientos =
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                asientosDisponibles
            )

        binding.actAsiento.setAdapter(
            adapterAsientos
        )

        val asientoAnterior =
            asientoSeleccionado

        if (
            asientoAnterior.isBlank() ||
            !asientosDisponibles.contains(
                asientoAnterior
            )
        ) {

            asientoSeleccionado = ""

            binding.actAsiento.setText(
                "",
                false
            )
        }

        binding.tvAsientosDisponibles.text =
            when (asientosDisponibles.size) {

                0 -> {
                    "No quedan asientos disponibles."
                }

                1 -> {
                    "Queda 1 asiento disponible."
                }

                else -> {
                    "Quedan ${asientosDisponibles.size} " +
                            "asientos disponibles."
                }
            }

        binding.actAsiento.isEnabled =
            asientosDisponibles.isNotEmpty()

        binding.btnVenderPasaje.isEnabled =
            asientosDisponibles.isNotEmpty() &&
                    !procesando
    }

    private fun obtenerAsientosTotales(
        capacidad: Int,
        disponiblesDocumento: List<String>,
        reservados: List<String>
    ): List<String> {

        /*
         * PasajesYa utiliza estos códigos para autos
         * de cuatro pasajeros.
         */
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
                .map {
                    it.trim()
                        .uppercase(Locale.ROOT)
                }
                .filter {
                    it.isNotBlank()
                }
                .distinct()

        if (existentes.size >= capacidad) {

            return existentes
                .take(capacidad)
        }

        return (1..capacidad).map {
            it.toString()
        }
    }

    private fun normalizarAsiento(
        asientoRecibido: String,
        capacidad: Int
    ): String {

        val asiento =
            asientoRecibido
                .trim()
                .uppercase(Locale.ROOT)

        /*
         * Corrige viajes antiguos que pudieran tener
         * asientos 1, 2, 3 y 4.
         */
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

    // -------------------------------------------------
    // VALIDACIÓN
    // -------------------------------------------------

    private fun validarFormulario() {

        limpiarErrores()

        val nombre =
            texto(
                binding.etNombre.text
            )

        val dni =
            texto(
                binding.etDni.text
            )

        val celular =
            texto(
                binding.etCelular.text
            )

        val correo =
            texto(
                binding.etCorreo.text
            )
                .lowercase(Locale.ROOT)

        val precio =
            texto(
                binding.etPrecio.text
            )
                .replace(",", ".")
                .toDoubleOrNull()

        when {

            nombre.length < 3 -> {

                binding.tilNombre.error =
                    "Ingresa el nombre completo."

                binding.etNombre.requestFocus()
            }

            dni.length != 8 ||
                    !dni.all {
                        it.isDigit()
                    } -> {

                binding.tilDni.error =
                    "El DNI debe tener 8 números."

                binding.etDni.requestFocus()
            }

            celular.length != 9 ||
                    !celular.all {
                        it.isDigit()
                    } -> {

                binding.tilCelular.error =
                    "El celular debe tener 9 números."

                binding.etCelular.requestFocus()
            }

            correo.isNotBlank() &&
                    !Patterns.EMAIL_ADDRESS
                        .matcher(correo)
                        .matches() -> {

                binding.tilCorreo.error =
                    "Ingresa un correo válido."

                binding.etCorreo.requestFocus()
            }

            asientoSeleccionado.isBlank() -> {

                binding.tilAsiento.error =
                    "Selecciona un asiento disponible."
            }

            precio == null ||
                    precio <= 0.0 -> {

                binding.tilPrecio.error =
                    "Ingresa un precio válido."

                binding.etPrecio.requestFocus()
            }

            metodoPagoSeleccionado.isBlank() -> {

                binding.tilMetodoPago.error =
                    "Selecciona el método de pago."
            }

            !binding.checkPagoConfirmado.isChecked -> {

                Toast.makeText(
                    this,
                    "Confirma que recibiste el pago completo.",
                    Toast.LENGTH_LONG
                ).show()
            }

            else -> {

                confirmarVenta(
                    nombre = nombre,
                    dni = dni,
                    celular = celular,
                    correo = correo,
                    asiento = asientoSeleccionado,
                    precio = precio,
                    metodoPago =
                        metodoPagoSeleccionado
                )
            }
        }
    }

    private fun confirmarVenta(
        nombre: String,
        dni: String,
        celular: String,
        correo: String,
        asiento: String,
        precio: Double,
        metodoPago: String
    ) {

        val mensaje =
            buildString {

                append("Pasajero:\n")
                append("$nombre\n\n")

                append("DNI: $dni\n")
                append("Celular: $celular\n")

                if (correo.isNotBlank()) {
                    append("Correo: $correo\n")
                }

                append("\nAsiento: $asiento\n")

                append(
                    "Precio: S/ ${
                        formatearPrecio(
                            precio
                        )
                    }\n"
                )

                append(
                    "Método de pago: $metodoPago\n\n"
                )

                append(
                    "La reserva quedará confirmada y pagada."
                )
            }

        MaterialAlertDialogBuilder(this)
            .setTitle(
                "Confirmar venta"
            )
            .setMessage(
                mensaje
            )
            .setNegativeButton(
                "REVISAR",
                null
            )
            .setPositiveButton(
                "VENDER PASAJE"
            ) { _, _ ->

                registrarVenta(
                    nombre = nombre,
                    dni = dni,
                    celular = celular,
                    correo = correo,
                    asiento = asiento,
                    precio = precio,
                    metodoPago = metodoPago
                )
            }
            .show()
    }

    // -------------------------------------------------
    // REGISTRAR VENTA
    // -------------------------------------------------

    private fun registrarVenta(
        nombre: String,
        dni: String,
        celular: String,
        correo: String,
        asiento: String,
        precio: Double,
        metodoPago: String
    ) {

        if (procesando) {
            return
        }

        val usuario =
            auth.currentUser
                ?: return

        procesando = true

        mostrarCargando(
            true,
            "Registrando venta..."
        )

        val referenciaPerfil =
            firestore
                .collection(
                    COLECCION_USUARIOS
                )
                .document(
                    usuario.uid
                )

        val referenciaViaje =
            referenciaViaje()

        val referenciaReserva =
            firestore
                .collection(
                    COLECCION_RESERVAS
                )
                .document()

        firestore
            .runTransaction {
                    transaccion ->

                /*
                 * Todas las lecturas deben realizarse
                 * antes de las escrituras.
                 */

                val documentoPerfil =
                    transaccion.get(
                        referenciaPerfil
                    )

                val documentoViaje =
                    transaccion.get(
                        referenciaViaje
                    )

                if (!documentoPerfil.exists()) {

                    throw IllegalStateException(
                        "No se encontró el vendedor."
                    )
                }

                if (!documentoViaje.exists()) {

                    throw IllegalStateException(
                        "El viaje ya no existe."
                    )
                }

                validarVendedorTransaccion(
                    documentoPerfil
                )

                val empresaViaje =
                    leerTexto(
                        documentoViaje,
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

                val estadoViaje =
                    leerTexto(
                        documentoViaje,
                        CAMPO_ESTADO
                    ).lowercase(Locale.ROOT)

                if (
                    estadoViaje !in
                    ESTADOS_VIAJE_CON_VENTA
                ) {

                    throw IllegalStateException(
                        "El viaje ya no permite vender pasajes."
                    )
                }

                val capacidad =
                    max(
                        1,
                        leerEntero(
                            documentoViaje,
                            CAMPO_CAPACIDAD,
                            capacidadViaje
                        )
                    )

                val asientoNormalizado =
                    normalizarAsiento(
                        asiento,
                        capacidad
                    )

                val asientosReservados =
                    leerListaTextos(
                        documentoViaje,
                        CAMPO_ASIENTOS_RESERVADOS
                    )
                        .map {
                            normalizarAsiento(
                                it,
                                capacidad
                            )
                        }
                        .filter {
                            it.isNotBlank()
                        }
                        .distinct()
                        .toMutableList()

                if (
                    asientosReservados.contains(
                        asientoNormalizado
                    )
                ) {

                    throw IllegalStateException(
                        "El asiento acaba de ser ocupado."
                    )
                }

                if (
                    asientosReservados.size >=
                    capacidad
                ) {

                    throw IllegalStateException(
                        "El vehículo ya está completo."
                    )
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
                        reservados =
                            asientosReservados
                    )

                if (
                    !todosLosAsientos.contains(
                        asientoNormalizado
                    )
                ) {

                    throw IllegalStateException(
                        "El asiento seleccionado no es válido."
                    )
                }

                asientosReservados.add(
                    asientoNormalizado
                )

                val asientosActualizados =
                    asientosReservados
                        .distinct()

                val asientosLibres =
                    todosLosAsientos.filter {
                            codigo ->

                        !asientosActualizados
                            .contains(codigo)
                    }

                val origen =
                    leerTexto(
                        documentoViaje,
                        CAMPO_ORIGEN
                    )

                val destino =
                    leerTexto(
                        documentoViaje,
                        CAMPO_DESTINO
                    )

                val fechaSalida =
                    leerTexto(
                        documentoViaje,
                        CAMPO_FECHA_SALIDA
                    )

                val horaSalida =
                    leerTexto(
                        documentoViaje,
                        CAMPO_HORA_SALIDA
                    )

                val puntoEmbarque =
                    leerTexto(
                        documentoViaje,
                        CAMPO_PUNTO_EMBARQUE
                    )

                val placa =
                    leerTexto(
                        documentoViaje,
                        CAMPO_PLACA
                    ).ifBlank {

                        leerTexto(
                            documentoViaje,
                            CAMPO_VEHICULO_PLACA
                        )
                    }

                val vehiculo =
                    leerTexto(
                        documentoViaje,
                        CAMPO_VEHICULO_DESCRIPCION
                    ).ifBlank {

                        leerTexto(
                            documentoViaje,
                            CAMPO_VEHICULO
                        )
                    }

                val empresaNombreViaje =
                    leerTexto(
                        documentoViaje,
                        CAMPO_EMPRESA_NOMBRE
                    ).ifBlank {
                        empresaNombre
                    }

                val datosReserva =
                    hashMapOf<String, Any>(

                        CAMPO_RESERVA_ID to
                                referenciaReserva.id,

                        CAMPO_EMPRESA_ID to
                                empresaId,

                        CAMPO_EMPRESA_NOMBRE to
                                empresaNombreViaje,

                        CAMPO_VIAJE_ID to
                                viajeId,

                        /*
                         * No existe una cuenta PasajesYa
                         * vinculada en una venta de oficina.
                         */
                        CAMPO_USUARIO_ID to
                                "",

                        CAMPO_USUARIO_CORREO to
                                correo,

                        CAMPO_PASAJERO_NOMBRE to
                                nombre,

                        CAMPO_NOMBRE_PASAJERO to
                                nombre,

                        CAMPO_PASAJERO_DNI to
                                dni,

                        CAMPO_DNI to
                                dni,

                        CAMPO_PASAJERO_CELULAR to
                                celular,

                        CAMPO_CELULAR to
                                celular,

                        CAMPO_ORIGEN to
                                origen,

                        CAMPO_DESTINO to
                                destino,

                        CAMPO_FECHA_SALIDA to
                                fechaSalida,

                        CAMPO_HORA_SALIDA to
                                horaSalida,

                        CAMPO_PUNTO_EMBARQUE to
                                puntoEmbarque,

                        CAMPO_PLACA to
                                placa,

                        CAMPO_VEHICULO_DESCRIPCION to
                                vehiculo,

                        CAMPO_ASIENTO to
                                asientoNormalizado,

                        CAMPO_NUMERO_ASIENTO to
                                asientoNormalizado,

                        CAMPO_CANTIDAD_PASAJEROS to
                                1,

                        CAMPO_PRECIO to
                                precio,

                        CAMPO_PRECIO_TOTAL to
                                precio,

                        CAMPO_METODO_PAGO to
                                metodoPago.lowercase(
                                    Locale.ROOT
                                ),

                        CAMPO_ESTADO_RESERVA to
                                ESTADO_CONFIRMADA,

                        CAMPO_ESTADO_PAGO to
                                ESTADO_PAGO_CONFIRMADO,

                        CAMPO_ESTADO_ABORDAJE to
                                ESTADO_ABORDAJE_PENDIENTE,

                        CAMPO_BOLETO_VALIDADO to
                                false,

                        CAMPO_TIPO_REGISTRO to
                                TIPO_VENTA_OFICINA,

                        CAMPO_CANAL_VENTA to
                                CANAL_OFICINA,

                        CAMPO_VENDEDOR_UID to
                                usuario.uid,

                        CAMPO_VENDEDOR_NOMBRE to
                                vendedorNombre,

                        CAMPO_CONFIRMADO_POR_UID to
                                usuario.uid,

                        CAMPO_CONFIRMADO_POR_NOMBRE to
                                vendedorNombre,

                        CAMPO_FECHA_RESERVA to
                                FieldValue.serverTimestamp(),

                        CAMPO_FECHA_CONFIRMACION_PAGO to
                                FieldValue.serverTimestamp(),

                        CAMPO_FECHA_ACTUALIZACION to
                                FieldValue.serverTimestamp(),

                        CAMPO_ULTIMA_ACTUALIZACION to
                                FieldValue.serverTimestamp()
                    )

                /*
                 * Escrituras de la transacción.
                 */

                transaccion.set(
                    referenciaReserva,
                    datosReserva
                )

                transaccion.update(
                    referenciaViaje,
                    mapOf(
                        CAMPO_ASIENTOS_RESERVADOS to
                                asientosActualizados,

                        CAMPO_ASIENTOS_DISPONIBLES to
                                asientosLibres,

                        CAMPO_ASIENTOS_OCUPADOS to
                                asientosActualizados.size,

                        CAMPO_CUPOS_DISPONIBLES to
                                max(
                                    0,
                                    capacidad -
                                            asientosActualizados.size
                                ),

                        CAMPO_FECHA_ACTUALIZACION to
                                FieldValue.serverTimestamp(),

                        CAMPO_ULTIMA_ACTUALIZACION to
                                FieldValue.serverTimestamp()
                    )
                )

                referenciaReserva.id
            }
            .addOnSuccessListener {
                    reservaId ->

                procesando = false
                mostrarCargando(false)

                mostrarVentaRealizada(
                    reservaId = reservaId,
                    nombre = nombre,
                    asiento = asiento,
                    precio = precio
                )
            }
            .addOnFailureListener {
                    error ->

                procesando = false
                mostrarCargando(false)

                Toast.makeText(
                    this,
                    error.localizedMessage
                        ?: "No se pudo registrar la venta.",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun validarVendedorTransaccion(
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

        if (rol != ROL_VENDEDOR) {

            throw IllegalStateException(
                "La cuenta no pertenece a un vendedor."
            )
        }

        if (estado != ESTADO_ACTIVO) {

            throw IllegalStateException(
                "La cuenta del vendedor no está activa."
            )
        }

        if (empresaPerfil != empresaId) {

            throw IllegalStateException(
                "El vendedor pertenece a otra empresa."
            )
        }
    }

    private fun mostrarVentaRealizada(
        reservaId: String,
        nombre: String,
        asiento: String,
        precio: Double
    ) {

        MaterialAlertDialogBuilder(this)
            .setTitle(
                "Pasaje vendido"
            )
            .setMessage(
                "La venta fue registrada correctamente.\n\n" +
                        "Pasajero: $nombre\n" +
                        "Asiento: $asiento\n" +
                        "Precio: S/ ${
                            formatearPrecio(
                                precio
                            )
                        }\n\n" +
                        "Código:\n$reservaId"
            )
            .setCancelable(false)
            .setNegativeButton(
                "TERMINAR"
            ) { _, _ ->

                finish()
            }
            .setPositiveButton(
                "VENDER OTRO"
            ) { _, _ ->

                limpiarFormulario()
            }
            .show()
    }

    private fun limpiarFormulario() {

        binding.etNombre.text?.clear()
        binding.etDni.text?.clear()
        binding.etCelular.text?.clear()
        binding.etCorreo.text?.clear()

        binding.actAsiento.setText(
            "",
            false
        )

        asientoSeleccionado = ""

        binding.checkPagoConfirmado.isChecked =
            false

        binding.etNombre.requestFocus()
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

    private fun limpiarErrores() {

        binding.tilNombre.error = null
        binding.tilDni.error = null
        binding.tilCelular.error = null
        binding.tilCorreo.error = null
        binding.tilAsiento.error = null
        binding.tilPrecio.error = null
        binding.tilMetodoPago.error = null
    }

    private fun texto(
        valor: CharSequence?
    ): String {

        return valor
            ?.toString()
            ?.trim()
            .orEmpty()
    }

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
                valor.trim()
                    .toIntOrNull()
                    ?: predeterminado
            }

            else -> {
                predeterminado
            }
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

            is Number -> {
                valor.toDouble()
            }

            is String -> {
                valor.trim()
                    .replace(",", ".")
                    .toDoubleOrNull()
                    ?: predeterminado
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
            ?.mapNotNull {
                    valor ->

                valor
                    ?.toString()
                    ?.trim()
                    ?.takeIf {
                        it.isNotBlank()
                    }
            }
            .orEmpty()
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
                "Fecha y hora no registradas"
            }
        }
    }

    private fun formatearPrecio(
        precio: Double
    ): String {

        return String.format(
            Locale("es", "PE"),
            "%.2f",
            precio
        )
    }

    private fun mostrarCargando(
        mostrar: Boolean,
        mensaje: String =
            "Procesando..."
    ) {

        binding.contenedorCargando.visibility =
            if (mostrar) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.tvMensajeCargando.text =
            mensaje

        binding.btnVolver.isEnabled =
            !mostrar

        binding.btnVenderPasaje.isEnabled =
            !mostrar &&
                    asientosDisponibles.isNotEmpty()
    }

    private fun removerListeners() {

        listenerPerfil?.remove()
        listenerPerfil = null

        listenerViaje?.remove()
        listenerViaje = null
    }

    override fun onStop() {

        removerListeners()
        super.onStop()
    }

    companion object {

        const val EXTRA_EMPRESA_ID =
            "extra_empresa_id_venta_oficina"

        const val EXTRA_VIAJE_ID =
            "extra_viaje_id_venta_oficina"

        private const val COLECCION_USUARIOS =
            "usuariosgestionpasajes"

        private const val COLECCION_EMPRESAS =
            "empresaspasajes"

        private const val COLECCION_RESERVAS =
            "reservaspasajes"

        private const val SUBCOLECCION_VIAJES =
            "viajespasajes"

        private const val CAMPO_RESERVA_ID =
            "reservaId"

        private const val CAMPO_ROL =
            "rol"

        private const val CAMPO_ESTADO =
            "estado"

        private const val CAMPO_EMPRESA_ID =
            "empresaId"

        private const val CAMPO_EMPRESA_NOMBRE =
            "empresaNombre"

        private const val CAMPO_VIAJE_ID =
            "viajeId"

        private const val CAMPO_NOMBRES =
            "nombres"

        private const val CAMPO_APELLIDOS =
            "apellidos"

        private const val CAMPO_NOMBRE_COMPLETO =
            "nombreCompleto"

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

        private const val CAMPO_ORIGEN =
            "origen"

        private const val CAMPO_DESTINO =
            "destino"

        private const val CAMPO_FECHA_SALIDA =
            "fechaSalida"

        private const val CAMPO_HORA_SALIDA =
            "horaSalida"

        private const val CAMPO_PUNTO_EMBARQUE =
            "puntoEmbarque"

        private const val CAMPO_PLACA =
            "placa"

        private const val CAMPO_VEHICULO_PLACA =
            "vehiculoPlaca"

        private const val CAMPO_VEHICULO =
            "vehiculo"

        private const val CAMPO_VEHICULO_DESCRIPCION =
            "vehiculoDescripcion"

        private const val CAMPO_ASIENTO =
            "asiento"

        private const val CAMPO_NUMERO_ASIENTO =
            "numeroAsiento"

        private const val CAMPO_CANTIDAD_PASAJEROS =
            "cantidadPasajeros"

        private const val CAMPO_PRECIO =
            "precio"

        private const val CAMPO_PRECIO_TOTAL =
            "precioTotal"

        private const val CAMPO_METODO_PAGO =
            "metodoPago"

        private const val CAMPO_ESTADO_RESERVA =
            "estadoReserva"

        private const val CAMPO_ESTADO_PAGO =
            "estadoPago"

        private const val CAMPO_ESTADO_ABORDAJE =
            "estadoAbordaje"

        private const val CAMPO_BOLETO_VALIDADO =
            "boletoValidado"

        private const val CAMPO_TIPO_REGISTRO =
            "tipoRegistro"

        private const val CAMPO_CANAL_VENTA =
            "canalVenta"

        private const val CAMPO_VENDEDOR_UID =
            "vendedorUid"

        private const val CAMPO_VENDEDOR_NOMBRE =
            "vendedorNombre"

        private const val CAMPO_CONFIRMADO_POR_UID =
            "confirmadoPorUid"

        private const val CAMPO_CONFIRMADO_POR_NOMBRE =
            "confirmadoPorNombre"

        private const val CAMPO_FECHA_RESERVA =
            "fechaReserva"

        private const val CAMPO_FECHA_CONFIRMACION_PAGO =
            "fechaConfirmacionPago"

        private const val CAMPO_FECHA_ACTUALIZACION =
            "fechaActualizacion"

        private const val CAMPO_ULTIMA_ACTUALIZACION =
            "ultimaActualizacion"

        private const val CAMPO_CAPACIDAD =
            "capacidad"

        private const val CAMPO_ASIENTOS_RESERVADOS =
            "asientosReservados"

        private const val CAMPO_ASIENTOS_DISPONIBLES =
            "asientosDisponibles"

        private const val CAMPO_ASIENTOS_OCUPADOS =
            "asientosOcupados"

        private const val CAMPO_CUPOS_DISPONIBLES =
            "cuposDisponibles"

        private const val ROL_VENDEDOR =
            "vendedor"

        private const val ESTADO_ACTIVO =
            "activo"

        private const val ESTADO_DISPONIBLE =
            "disponible"

        private const val ESTADO_PROGRAMADO =
            "programado"

        private const val ESTADO_RECIBIENDO =
            "recibiendo_pasajeros"

        private const val ESTADO_CONFIRMADA =
            "confirmada"

        private const val ESTADO_PAGO_CONFIRMADO =
            "confirmado"

        private const val ESTADO_ABORDAJE_PENDIENTE =
            "pendiente"

        private const val TIPO_VENTA_OFICINA =
            "venta_oficina"

        private const val CANAL_OFICINA =
            "oficina"

        private val ESTADOS_VIAJE_CON_VENTA =
            setOf(
                ESTADO_ACTIVO,
                ESTADO_DISPONIBLE,
                ESTADO_PROGRAMADO,
                ESTADO_RECIBIENDO
            )
    }
}