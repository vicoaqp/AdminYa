package com.pasajesya.adminya

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.pasajesya.adminya.databinding.ActivityCrearViajeBinding
import java.util.Calendar
import java.util.Locale

class CrearViajeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCrearViajeBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private var listenerPerfil: ListenerRegistration? = null
    private var listenerChoferes: ListenerRegistration? = null
    private var listenerVehiculos: ListenerRegistration? = null

    private var empresaIdActual = ""
    private var empresaNombreActual = ""
    private var vendedorNombreActual = ""

    private val listaChoferes =
        mutableListOf<ChoferParaViaje>()

    private val listaVehiculos =
        mutableListOf<VehiculoParaViaje>()

    private val opcionesChoferes =
        mutableListOf<OpcionChoferViaje>()

    private var choferSeleccionado: ChoferParaViaje? = null
    private var vehiculoSeleccionado: VehiculoParaViaje? = null

    private val fechaHoraSeleccionada =
        Calendar.getInstance()

    private var fechaSeleccionada = false
    private var horaSeleccionada = false
    private var creandoViaje = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding =
            ActivityCrearViajeBinding.inflate(layoutInflater)

        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        configurarPantalla()
        configurarEventos()
        limpiarVehiculoSeleccionado()
    }

    override fun onStart() {
        super.onStart()

        removerListeners()

        empresaIdActual = ""
        empresaNombreActual = ""
        vendedorNombreActual = ""

        listaChoferes.clear()
        listaVehiculos.clear()
        opcionesChoferes.clear()

        choferSeleccionado = null
        vehiculoSeleccionado = null

        escucharPerfilVendedor()
    }

    private fun configurarPantalla() {

        window.statusBarColor =
            getColor(R.color.adminya_primary_dark)

        window.navigationBarColor =
            getColor(R.color.adminya_background)
    }

    private fun configurarEventos() {

        binding.btnVolver.setOnClickListener {
            finish()
        }

        binding.etFecha.setOnClickListener {
            mostrarSelectorFecha()
        }

        binding.etHora.setOnClickListener {
            mostrarSelectorHora()
        }

        binding.actChofer.setOnItemClickListener { _, _, posicion, _ ->

            seleccionarChofer(posicion)
        }

        binding.btnCrearViaje.setOnClickListener {
            validarFormulario()
        }
    }

    // -------------------------------------------------
    // PERFIL DE LA VENDEDORA
    // -------------------------------------------------

    private fun escucharPerfilVendedor() {

        val usuario = auth.currentUser

        if (usuario == null) {

            Toast.makeText(
                this,
                "La sesión ha finalizado.",
                Toast.LENGTH_LONG
            ).show()

            finish()
            return
        }

        mostrarCargando(
            true,
            "Validando vendedor..."
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
                            error.localizedMessage ?: "error desconocido"
                        }",
                        Toast.LENGTH_LONG
                    ).show()

                    return@addSnapshotListener
                }

                if (documento == null || !documento.exists()) {

                    mostrarCargando(false)

                    Toast.makeText(
                        this,
                        "No se encontró tu perfil.",
                        Toast.LENGTH_LONG
                    ).show()

                    finish()
                    return@addSnapshotListener
                }

                procesarPerfilVendedor(documento)
            }
    }

    private fun procesarPerfilVendedor(
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

        val nombreCompleto =
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
                    "Esta cuenta no pertenece a un vendedor.",
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

            empresaId.isBlank() -> {

                mostrarCargando(false)

                Toast.makeText(
                    this,
                    "Tu cuenta no tiene una empresa asignada.",
                    Toast.LENGTH_LONG
                ).show()

                finish()
            }

            empresaIdActual != empresaId -> {

                empresaIdActual = empresaId
                empresaNombreActual = empresaNombre
                vendedorNombreActual = nombreCompleto

                binding.tvEmpresa.text =
                    empresaNombre.ifBlank {
                        empresaId
                    }

                escucharChoferes(empresaId)
                escucharVehiculos(empresaId)
            }
        }
    }

    // -------------------------------------------------
    // CHOFERES
    // -------------------------------------------------

    private fun escucharChoferes(
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

                    mostrarCargando(false)

                    Toast.makeText(
                        this,
                        "No se pudieron cargar los choferes: ${
                            error.localizedMessage ?: "error desconocido"
                        }",
                        Toast.LENGTH_LONG
                    ).show()

                    return@addSnapshotListener
                }

                listaChoferes.clear()

                resultado
                    ?.documents
                    ?.map { documento ->
                        convertirChofer(documento)
                    }
                    ?.let { choferes ->
                        listaChoferes.addAll(choferes)
                    }

                actualizarOpcionesChoferes()
            }
    }

    private fun convertirChofer(
        documento: DocumentSnapshot
    ): ChoferParaViaje {

        val nombreCompleto =
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

        return ChoferParaViaje(

            uid = documento.id,

            empresaId =
                leerTexto(
                    documento,
                    CAMPO_EMPRESA_ID
                ),

            nombreCompleto = nombreCompleto,

            dni =
                leerTexto(
                    documento,
                    CAMPO_DNI
                ),

            celular =
                leerTexto(
                    documento,
                    CAMPO_CELULAR
                ),

            estado =
                leerTexto(
                    documento,
                    CAMPO_ESTADO
                ).lowercase(Locale.ROOT),

            disponible =
                leerBooleano(
                    documento,
                    CAMPO_DISPONIBLE,
                    false
                ),

            disponibilidad =
                leerTexto(
                    documento,
                    CAMPO_DISPONIBILIDAD
                ).lowercase(Locale.ROOT),

            vehiculoId =
                leerTexto(
                    documento,
                    CAMPO_VEHICULO_ID
                ),

            viajeActualId =
                leerTexto(
                    documento,
                    CAMPO_VIAJE_ACTUAL_ID
                )
        )
    }

    // -------------------------------------------------
    // VEHÍCULOS
    // -------------------------------------------------

    private fun escucharVehiculos(
        empresaId: String
    ) {

        listenerVehiculos?.remove()

        listenerVehiculos = firestore
            .collection(COLECCION_VEHICULOS)
            .whereEqualTo(
                CAMPO_EMPRESA_ID,
                empresaId
            )
            .addSnapshotListener { resultado, error ->

                mostrarCargando(false)

                if (error != null) {

                    Toast.makeText(
                        this,
                        "No se pudieron cargar los vehículos: ${
                            error.localizedMessage ?: "error desconocido"
                        }",
                        Toast.LENGTH_LONG
                    ).show()

                    return@addSnapshotListener
                }

                listaVehiculos.clear()

                resultado
                    ?.documents
                    ?.map { documento ->
                        convertirVehiculo(documento)
                    }
                    ?.let { vehiculos ->
                        listaVehiculos.addAll(vehiculos)
                    }

                actualizarOpcionesChoferes()
            }
    }

    private fun convertirVehiculo(
        documento: DocumentSnapshot
    ): VehiculoParaViaje {

        return VehiculoParaViaje(

            id = documento.id,

            empresaId =
                leerTexto(
                    documento,
                    CAMPO_EMPRESA_ID
                ),

            placa =
                leerTexto(
                    documento,
                    CAMPO_PLACA
                ),

            tipo =
                leerTexto(
                    documento,
                    CAMPO_TIPO
                ),

            marca =
                leerTexto(
                    documento,
                    CAMPO_MARCA
                ),

            modelo =
                leerTexto(
                    documento,
                    CAMPO_MODELO
                ),

            color =
                leerTexto(
                    documento,
                    CAMPO_COLOR
                ),

            capacidad =
                leerEntero(
                    documento,
                    CAMPO_CAPACIDAD,
                    4
                ),

            estado =
                leerTexto(
                    documento,
                    CAMPO_ESTADO
                ).lowercase(Locale.ROOT),

            disponible =
                leerBooleano(
                    documento,
                    CAMPO_DISPONIBLE,
                    false
                ),

            disponibilidad =
                leerTexto(
                    documento,
                    CAMPO_DISPONIBILIDAD
                ).lowercase(Locale.ROOT),

            viajeActualId =
                leerTexto(
                    documento,
                    CAMPO_VIAJE_ACTUAL_ID
                ),

            choferActualUid =
                leerTexto(
                    documento,
                    CAMPO_CHOFER_ACTUAL_UID
                )
        )
    }

    // -------------------------------------------------
    // COMBINAR CHOFER Y VEHÍCULO
    // -------------------------------------------------

    private fun actualizarOpcionesChoferes() {

        opcionesChoferes.clear()

        listaChoferes.forEach { chofer ->

            val choferDisponible =
                chofer.estado == ESTADO_ACTIVO &&
                        chofer.viajeActualId.isBlank() &&
                        (
                                chofer.disponible ||
                                        chofer.disponibilidad ==
                                        DISPONIBILIDAD_DISPONIBLE
                                ) &&
                        chofer.vehiculoId.isNotBlank()

            if (!choferDisponible) {
                return@forEach
            }

            val vehiculo =
                listaVehiculos.firstOrNull { item ->

                    item.id == chofer.vehiculoId

                } ?: return@forEach

            val vehiculoDisponible =
                vehiculo.estado == ESTADO_ACTIVO &&
                        vehiculo.viajeActualId.isBlank() &&
                        (
                                vehiculo.disponible ||
                                        vehiculo.disponibilidad ==
                                        DISPONIBILIDAD_DISPONIBLE
                                ) &&
                        (
                                vehiculo.choferActualUid.isBlank() ||
                                        vehiculo.choferActualUid ==
                                        chofer.uid
                                )

            if (!vehiculoDisponible) {
                return@forEach
            }

            opcionesChoferes.add(
                OpcionChoferViaje(
                    chofer = chofer,
                    vehiculo = vehiculo
                )
            )
        }

        opcionesChoferes.sortBy {
            it.chofer.nombreCompleto
                .lowercase(Locale.ROOT)
        }

        val textos =
            opcionesChoferes.map { opcion ->

                val dni =
                    opcion.chofer.dni.ifBlank {
                        "Sin DNI"
                    }

                val placa =
                    opcion.vehiculo.placa.ifBlank {
                        "Sin placa"
                    }

                "${opcion.chofer.nombreCompleto} · DNI $dni · $placa"
            }

        val adapterChoferes =
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                textos
            )

        binding.actChofer.setAdapter(adapterChoferes)

        val uidSeleccionado =
            choferSeleccionado?.uid.orEmpty()

        val sigueDisponible =
            opcionesChoferes.any {
                it.chofer.uid == uidSeleccionado
            }

        if (!sigueDisponible) {

            choferSeleccionado = null
            vehiculoSeleccionado = null

            binding.actChofer.setText(
                "",
                false
            )

            limpiarVehiculoSeleccionado()
        }

        binding.tilChofer.helperText =
            when (opcionesChoferes.size) {

                0 -> {
                    "No hay choferes con vehículo disponible."
                }

                1 -> {
                    "1 chofer disponible."
                }

                else -> {
                    "${opcionesChoferes.size} choferes disponibles."
                }
            }
    }

    private fun seleccionarChofer(
        posicion: Int
    ) {

        val opcion =
            opcionesChoferes.getOrNull(posicion)
                ?: return

        choferSeleccionado =
            opcion.chofer

        vehiculoSeleccionado =
            opcion.vehiculo

        binding.tilChofer.error = null

        mostrarVehiculoSeleccionado(
            opcion.vehiculo
        )
    }

    private fun mostrarVehiculoSeleccionado(
        vehiculo: VehiculoParaViaje
    ) {

        val descripcion =
            construirDescripcionVehiculo(
                vehiculo
            )

        binding.tvVehiculoSeleccionado.text =
            descripcion

        binding.tvPlacaSeleccionada.text =
            "Placa: ${
                vehiculo.placa.ifBlank {
                    "No registrada"
                }
            }"

        binding.tvCapacidadSeleccionada.text =
            "Capacidad: ${vehiculo.capacidad} pasajeros"
    }

    private fun limpiarVehiculoSeleccionado() {

        binding.tvVehiculoSeleccionado.text =
            "Selecciona un chofer"

        binding.tvPlacaSeleccionada.text =
            "Placa: —"

        binding.tvCapacidadSeleccionada.text =
            "Capacidad: —"
    }

    // -------------------------------------------------
    // FECHA
    // -------------------------------------------------

    private fun mostrarSelectorFecha() {

        val calendario =
            Calendar.getInstance()

        DatePickerDialog(
            this,
            { _, anio, mes, dia ->

                fechaHoraSeleccionada.set(
                    Calendar.YEAR,
                    anio
                )

                fechaHoraSeleccionada.set(
                    Calendar.MONTH,
                    mes
                )

                fechaHoraSeleccionada.set(
                    Calendar.DAY_OF_MONTH,
                    dia
                )

                fechaSeleccionada = true

                binding.etFecha.setText(
                    String.format(
                        Locale.ROOT,
                        "%02d/%02d/%04d",
                        dia,
                        mes + 1,
                        anio
                    )
                )

                binding.tilFecha.error = null
            },
            calendario.get(Calendar.YEAR),
            calendario.get(Calendar.MONTH),
            calendario.get(Calendar.DAY_OF_MONTH)
        ).apply {

            datePicker.minDate =
                System.currentTimeMillis() - 1000L

        }.show()
    }

    // -------------------------------------------------
    // HORA
    // -------------------------------------------------

    private fun mostrarSelectorHora() {

        val calendario =
            Calendar.getInstance()

        TimePickerDialog(
            this,
            { _, hora, minuto ->

                fechaHoraSeleccionada.set(
                    Calendar.HOUR_OF_DAY,
                    hora
                )

                fechaHoraSeleccionada.set(
                    Calendar.MINUTE,
                    minuto
                )

                fechaHoraSeleccionada.set(
                    Calendar.SECOND,
                    0
                )

                fechaHoraSeleccionada.set(
                    Calendar.MILLISECOND,
                    0
                )

                horaSeleccionada = true

                binding.etHora.setText(
                    String.format(
                        Locale.ROOT,
                        "%02d:%02d",
                        hora,
                        minuto
                    )
                )

                binding.tilHora.error = null
            },
            calendario.get(Calendar.HOUR_OF_DAY),
            calendario.get(Calendar.MINUTE),
            false
        ).show()
    }

    // -------------------------------------------------
    // VALIDACIÓN
    // -------------------------------------------------

    private fun validarFormulario() {

        limpiarErrores()

        val origen =
            texto(binding.etOrigen.text)

        val destino =
            texto(binding.etDestino.text)

        val fecha =
            texto(binding.etFecha.text)

        val hora =
            texto(binding.etHora.text)

        val puntoEmbarque =
            texto(binding.etPuntoEmbarque.text)

        val precio =
            texto(binding.etPrecio.text)
                .replace(",", ".")
                .toDoubleOrNull()

        val observaciones =
            texto(binding.etObservaciones.text)

        val chofer =
            choferSeleccionado

        val vehiculo =
            vehiculoSeleccionado

        when {

            origen.length < 2 -> {

                binding.tilOrigen.error =
                    "Ingresa el lugar de origen."

                binding.etOrigen.requestFocus()
            }

            destino.length < 2 -> {

                binding.tilDestino.error =
                    "Ingresa el lugar de destino."

                binding.etDestino.requestFocus()
            }

            origen.equals(
                destino,
                ignoreCase = true
            ) -> {

                binding.tilDestino.error =
                    "El destino debe ser diferente al origen."
            }

            !fechaSeleccionada ||
                    fecha.isBlank() -> {

                binding.tilFecha.error =
                    "Selecciona la fecha."
            }

            !horaSeleccionada ||
                    hora.isBlank() -> {

                binding.tilHora.error =
                    "Selecciona la hora."
            }

            fechaHoraSeleccionada.timeInMillis <=
                    System.currentTimeMillis() -> {

                binding.tilHora.error =
                    "La fecha y hora deben ser posteriores a la actual."
            }

            puntoEmbarque.length < 3 -> {

                binding.tilPuntoEmbarque.error =
                    "Ingresa el punto de embarque."

                binding.etPuntoEmbarque.requestFocus()
            }

            precio == null ||
                    precio <= 0.0 -> {

                binding.tilPrecio.error =
                    "Ingresa un precio válido."

                binding.etPrecio.requestFocus()
            }

            precio > 1000.0 -> {

                binding.tilPrecio.error =
                    "El precio ingresado es demasiado alto."
            }

            chofer == null ||
                    vehiculo == null -> {

                binding.tilChofer.error =
                    "Selecciona un chofer disponible."
            }

            vehiculo.capacidad <= 0 -> {

                binding.tilChofer.error =
                    "El vehículo no tiene una capacidad válida."
            }

            !binding.checkConfirmacion.isChecked -> {

                Toast.makeText(
                    this,
                    "Confirma que los datos son correctos.",
                    Toast.LENGTH_LONG
                ).show()
            }

            else -> {

                confirmarCreacion(
                    origen = origen,
                    destino = destino,
                    fecha = fecha,
                    hora = hora,
                    puntoEmbarque = puntoEmbarque,
                    precio = precio,
                    observaciones = observaciones,
                    chofer = chofer,
                    vehiculo = vehiculo
                )
            }
        }
    }

    private fun confirmarCreacion(
        origen: String,
        destino: String,
        fecha: String,
        hora: String,
        puntoEmbarque: String,
        precio: Double,
        observaciones: String,
        chofer: ChoferParaViaje,
        vehiculo: VehiculoParaViaje
    ) {

        val descripcionVehiculo =
            construirDescripcionVehiculo(
                vehiculo
            )

        val mensaje =
            buildString {

                append("Ruta:\n")
                append("$origen → $destino\n\n")

                append("Salida:\n")
                append("$fecha · $hora\n\n")

                append("Punto de embarque:\n")
                append("$puntoEmbarque\n\n")

                append("Chofer:\n")
                append("${chofer.nombreCompleto}\n\n")

                append("Vehículo:\n")
                append("$descripcionVehiculo\n")

                append(
                    "Placa: ${
                        vehiculo.placa.ifBlank {
                            "No registrada"
                        }
                    }\n"
                )

                append(
                    "Capacidad: ${vehiculo.capacidad} pasajeros\n\n"
                )

                append("Precio por pasajero:\n")
                append("S/ ${formatearPrecio(precio)}")
            }

        MaterialAlertDialogBuilder(this)
            .setTitle("Confirmar viaje")
            .setMessage(mensaje)
            .setNegativeButton(
                "REVISAR",
                null
            )
            .setPositiveButton(
                "CREAR VIAJE"
            ) { _, _ ->

                crearViaje(
                    origen = origen,
                    destino = destino,
                    fecha = fecha,
                    hora = hora,
                    puntoEmbarque = puntoEmbarque,
                    precio = precio,
                    observaciones = observaciones,
                    chofer = chofer,
                    vehiculo = vehiculo
                )
            }
            .show()
    }

    // -------------------------------------------------
    // CREAR VIAJE EN FIRESTORE
    // -------------------------------------------------

    private fun crearViaje(
        origen: String,
        destino: String,
        fecha: String,
        hora: String,
        puntoEmbarque: String,
        precio: Double,
        observaciones: String,
        chofer: ChoferParaViaje,
        vehiculo: VehiculoParaViaje
    ) {

        if (creandoViaje) {
            return
        }

        val usuario = auth.currentUser

        if (usuario == null) {

            finish()
            return
        }

        if (empresaIdActual.isBlank()) {

            Toast.makeText(
                this,
                "No se encontró la empresa.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        creandoViaje = true

        mostrarCargando(
            true,
            "Creando viaje..."
        )

        val referenciaVendedor =
            firestore
                .collection(COLECCION_USUARIOS)
                .document(usuario.uid)

        val referenciaChofer =
            firestore
                .collection(COLECCION_CHOFERES)
                .document(chofer.uid)

        val referenciaVehiculo =
            firestore
                .collection(COLECCION_VEHICULOS)
                .document(vehiculo.id)

        val referenciaViaje =
            firestore
                .collection(COLECCION_EMPRESAS)
                .document(empresaIdActual)
                .collection(SUBCOLECCION_VIAJES)
                .document()

        val fechaSalidaTimestamp =
            Timestamp(fechaHoraSeleccionada.time)

        firestore
            .runTransaction { transaccion ->

                /*
                 * Primero se realizan todas las lecturas.
                 */

                val documentoVendedor =
                    transaccion.get(referenciaVendedor)

                val documentoChofer =
                    transaccion.get(referenciaChofer)

                val documentoVehiculo =
                    transaccion.get(referenciaVehiculo)

                if (!documentoVendedor.exists()) {
                    throw IllegalStateException(
                        "No se encontró el vendedor."
                    )
                }

                if (!documentoChofer.exists()) {
                    throw IllegalStateException(
                        "El chofer ya no existe."
                    )
                }

                if (!documentoVehiculo.exists()) {
                    throw IllegalStateException(
                        "El vehículo ya no existe."
                    )
                }

                /*
                 * Validar vendedor.
                 */

                val rolVendedor =
                    leerTexto(
                        documentoVendedor,
                        CAMPO_ROL
                    ).lowercase(Locale.ROOT)

                val estadoVendedor =
                    leerTexto(
                        documentoVendedor,
                        CAMPO_ESTADO
                    ).lowercase(Locale.ROOT)

                val empresaVendedor =
                    leerTexto(
                        documentoVendedor,
                        CAMPO_EMPRESA_ID
                    )

                if (rolVendedor != ROL_VENDEDOR) {

                    throw IllegalStateException(
                        "La cuenta no pertenece a un vendedor."
                    )
                }

                if (estadoVendedor != ESTADO_ACTIVO) {

                    throw IllegalStateException(
                        "La cuenta del vendedor no está activa."
                    )
                }

                if (empresaVendedor != empresaIdActual) {

                    throw IllegalStateException(
                        "La cuenta pertenece a otra empresa."
                    )
                }

                /*
                 * Validar chofer.
                 */

                val empresaChofer =
                    leerTexto(
                        documentoChofer,
                        CAMPO_EMPRESA_ID
                    )

                val estadoChofer =
                    leerTexto(
                        documentoChofer,
                        CAMPO_ESTADO
                    ).lowercase(Locale.ROOT)

                val disponibleChofer =
                    leerBooleano(
                        documentoChofer,
                        CAMPO_DISPONIBLE,
                        false
                    ) ||
                            leerTexto(
                                documentoChofer,
                                CAMPO_DISPONIBILIDAD
                            ).lowercase(Locale.ROOT) ==
                            DISPONIBILIDAD_DISPONIBLE

                val viajeActualChofer =
                    leerTexto(
                        documentoChofer,
                        CAMPO_VIAJE_ACTUAL_ID
                    )

                val vehiculoIdChofer =
                    leerTexto(
                        documentoChofer,
                        CAMPO_VEHICULO_ID
                    )

                if (empresaChofer != empresaIdActual) {

                    throw IllegalStateException(
                        "El chofer pertenece a otra empresa."
                    )
                }

                if (estadoChofer != ESTADO_ACTIVO) {

                    throw IllegalStateException(
                        "El chofer no está activo."
                    )
                }

                if (!disponibleChofer) {

                    throw IllegalStateException(
                        "El chofer ya no está disponible."
                    )
                }

                if (viajeActualChofer.isNotBlank()) {

                    throw IllegalStateException(
                        "El chofer ya tiene otro viaje."
                    )
                }

                if (vehiculoIdChofer != vehiculo.id) {

                    throw IllegalStateException(
                        "El vehículo ya no está asignado al chofer."
                    )
                }

                /*
                 * Validar vehículo.
                 */

                val empresaVehiculo =
                    leerTexto(
                        documentoVehiculo,
                        CAMPO_EMPRESA_ID
                    )

                val estadoVehiculo =
                    leerTexto(
                        documentoVehiculo,
                        CAMPO_ESTADO
                    ).lowercase(Locale.ROOT)

                val disponibleVehiculo =
                    leerBooleano(
                        documentoVehiculo,
                        CAMPO_DISPONIBLE,
                        false
                    ) ||
                            leerTexto(
                                documentoVehiculo,
                                CAMPO_DISPONIBILIDAD
                            ).lowercase(Locale.ROOT) ==
                            DISPONIBILIDAD_DISPONIBLE

                val viajeActualVehiculo =
                    leerTexto(
                        documentoVehiculo,
                        CAMPO_VIAJE_ACTUAL_ID
                    )

                val choferActualUid =
                    leerTexto(
                        documentoVehiculo,
                        CAMPO_CHOFER_ACTUAL_UID
                    )

                if (empresaVehiculo != empresaIdActual) {

                    throw IllegalStateException(
                        "El vehículo pertenece a otra empresa."
                    )
                }

                if (estadoVehiculo != ESTADO_ACTIVO) {

                    throw IllegalStateException(
                        "El vehículo no está activo."
                    )
                }

                if (!disponibleVehiculo) {

                    throw IllegalStateException(
                        "El vehículo ya no está disponible."
                    )
                }

                if (viajeActualVehiculo.isNotBlank()) {

                    throw IllegalStateException(
                        "El vehículo ya tiene otro viaje."
                    )
                }

                if (
                    choferActualUid.isNotBlank() &&
                    choferActualUid != chofer.uid
                ) {

                    throw IllegalStateException(
                        "El vehículo está asignado a otro chofer."
                    )
                }

                /*
                 * Datos que se guardarán en el viaje.
                 */

                val descripcionVehiculo =
                    construirDescripcionVehiculo(
                        vehiculo
                    )

                val asientosDisponibles =
                    (1..vehiculo.capacidad).map {
                        it.toString()
                    }

                val datosViaje =
                    hashMapOf<String, Any>(

                        "viajeId" to referenciaViaje.id,

                        "empresaId" to empresaIdActual,
                        "empresaNombre" to empresaNombreActual,

                        "origen" to origen,
                        "destino" to destino,
                        "ruta" to "$origen - $destino",

                        "fechaSalida" to fecha,
                        "fecha" to fecha,

                        "horaSalida" to hora,
                        "hora" to hora,

                        "fechaSalidaOrden" to fechaSalidaTimestamp,

                        "puntoEmbarque" to puntoEmbarque,

                        "precio" to precio,
                        "precioPasaje" to precio,

                        "observaciones" to observaciones,

                        "choferUid" to chofer.uid,
                        "choferId" to chofer.uid,
                        "choferNombre" to chofer.nombreCompleto,
                        "choferCelular" to chofer.celular,

                        "vehiculoId" to vehiculo.id,
                        "vehiculo" to descripcionVehiculo,
                        "vehiculoDescripcion" to descripcionVehiculo,

                        "placa" to vehiculo.placa,
                        "vehiculoPlaca" to vehiculo.placa,

                        "tipoVehiculo" to vehiculo.tipo,

                        "capacidad" to vehiculo.capacidad,
                        "asientosTotales" to vehiculo.capacidad,

                        "asientosOcupados" to 0,

                        "asientosReservados" to
                                emptyList<String>(),

                        "asientosDisponibles" to
                                asientosDisponibles,

                        "cupos" to vehiculo.capacidad,

                        "cuposDisponibles" to
                                vehiculo.capacidad,

                        "estado" to ESTADO_DISPONIBLE,

                        "estadoOperacion" to
                                ESTADO_PROGRAMADO,

                        "visibleUsuarios" to true,

                        "vendedorUid" to usuario.uid,

                        "vendedorNombre" to
                                vendedorNombreActual,

                        "creadoPorUid" to usuario.uid,

                        "creadoPorNombre" to
                                vendedorNombreActual,

                        "fechaRegistro" to
                                FieldValue.serverTimestamp(),

                        "fechaActualizacion" to
                                FieldValue.serverTimestamp()
                    )

                /*
                 * Escrituras.
                 */

                transaccion.set(
                    referenciaViaje,
                    datosViaje
                )

                transaccion.update(
                    referenciaChofer,
                    mapOf(
                        CAMPO_VIAJE_ACTUAL_ID to
                                referenciaViaje.id,

                        CAMPO_DISPONIBLE to false,

                        CAMPO_DISPONIBILIDAD to
                                DISPONIBILIDAD_NO_DISPONIBLE,

                        CAMPO_FECHA_ACTUALIZACION to
                                FieldValue.serverTimestamp()
                    )
                )

                transaccion.update(
                    referenciaVehiculo,
                    mapOf(
                        CAMPO_VIAJE_ACTUAL_ID to
                                referenciaViaje.id,

                        CAMPO_DISPONIBLE to false,

                        CAMPO_DISPONIBILIDAD to
                                DISPONIBILIDAD_NO_DISPONIBLE,

                        CAMPO_CHOFER_ACTUAL_UID to
                                chofer.uid,

                        CAMPO_CHOFER_ACTUAL_NOMBRE to
                                chofer.nombreCompleto,

                        CAMPO_FECHA_ACTUALIZACION to
                                FieldValue.serverTimestamp()
                    )
                )

                referenciaViaje.id
            }
            .addOnSuccessListener { viajeId ->

                creandoViaje = false
                mostrarCargando(false)

                mostrarViajeCreado(
                    viajeId = viajeId,
                    origen = origen,
                    destino = destino,
                    fecha = fecha,
                    hora = hora
                )
            }
            .addOnFailureListener { error ->

                creandoViaje = false
                mostrarCargando(false)

                Toast.makeText(
                    this,
                    "No se pudo crear el viaje: ${
                        error.localizedMessage ?: "error desconocido"
                    }",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun mostrarViajeCreado(
        viajeId: String,
        origen: String,
        destino: String,
        fecha: String,
        hora: String
    ) {

        MaterialAlertDialogBuilder(this)
            .setTitle("Viaje creado")
            .setMessage(
                "El viaje fue registrado correctamente.\n\n" +
                        "Ruta: $origen → $destino\n" +
                        "Salida: $fecha · $hora\n\n" +
                        "Código del viaje:\n$viajeId"
            )
            .setCancelable(false)
            .setPositiveButton(
                "ENTENDIDO"
            ) { _, _ ->

                finish()
            }
            .show()
    }

    // -------------------------------------------------
    // UTILIDADES
    // -------------------------------------------------

    private fun limpiarErrores() {

        binding.tilOrigen.error = null
        binding.tilDestino.error = null
        binding.tilFecha.error = null
        binding.tilHora.error = null
        binding.tilPuntoEmbarque.error = null
        binding.tilPrecio.error = null
        binding.tilChofer.error = null
        binding.tilObservaciones.error = null
    }

    private fun construirDescripcionVehiculo(
        vehiculo: VehiculoParaViaje
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

    private fun formatearPrecio(
        precio: Double
    ): String {

        return String.format(
            Locale.ROOT,
            "%.2f",
            precio
        )
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
                valor.toIntOrNull()
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
                    "sí" -> true

                    "false",
                    "0",
                    "no" -> false

                    else -> predeterminado
                }
            }

            else -> {
                predeterminado
            }
        }
    }

    private fun mostrarCargando(
        mostrar: Boolean,
        mensaje: String = "Procesando..."
    ) {

        binding.contenedorCargando.visibility =
            if (mostrar) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.tvMensajeCargando.text =
            mensaje

        binding.btnCrearViaje.isEnabled =
            !mostrar

        binding.btnVolver.isEnabled =
            !mostrar
    }

    private fun removerListeners() {

        listenerPerfil?.remove()
        listenerPerfil = null

        listenerChoferes?.remove()
        listenerChoferes = null

        listenerVehiculos?.remove()
        listenerVehiculos = null
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

        private const val COLECCION_VEHICULOS =
            "vehiculospasajes"

        private const val SUBCOLECCION_VIAJES =
            "viajespasajes"

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

        private const val CAMPO_DISPONIBLE =
            "disponible"

        private const val CAMPO_DISPONIBILIDAD =
            "disponibilidad"

        private const val CAMPO_VEHICULO_ID =
            "vehiculoId"

        private const val CAMPO_VIAJE_ACTUAL_ID =
            "viajeActualId"

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

        private const val CAMPO_CAPACIDAD =
            "capacidad"

        private const val CAMPO_CHOFER_ACTUAL_UID =
            "choferActualUid"

        private const val CAMPO_CHOFER_ACTUAL_NOMBRE =
            "choferActualNombre"

        private const val CAMPO_FECHA_ACTUALIZACION =
            "fechaActualizacion"

        private const val ROL_VENDEDOR =
            "vendedor"

        private const val ESTADO_ACTIVO =
            "activo"

        private const val ESTADO_PROGRAMADO =
            "programado"

        private const val ESTADO_DISPONIBLE =
            "disponible"

        private const val DISPONIBILIDAD_DISPONIBLE =
            "disponible"

        private const val DISPONIBILIDAD_NO_DISPONIBLE =
            "no_disponible"
    }
}

private data class ChoferParaViaje(

    val uid: String = "",
    val empresaId: String = "",

    val nombreCompleto: String = "",
    val dni: String = "",
    val celular: String = "",

    val estado: String = "activo",

    val disponible: Boolean = false,
    val disponibilidad: String = "",

    val vehiculoId: String = "",
    val viajeActualId: String = ""
)

private data class VehiculoParaViaje(

    val id: String = "",
    val empresaId: String = "",

    val placa: String = "",
    val tipo: String = "",
    val marca: String = "",
    val modelo: String = "",
    val color: String = "",

    val capacidad: Int = 4,

    val estado: String = "activo",

    val disponible: Boolean = false,
    val disponibilidad: String = "",

    val viajeActualId: String = "",
    val choferActualUid: String = ""
)

private data class OpcionChoferViaje(

    val chofer: ChoferParaViaje,
    val vehiculo: VehiculoParaViaje
)