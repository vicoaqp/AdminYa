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
import com.pasajesya.adminya.databinding.ActivityMainPropietarioBinding
import java.util.Locale

class MainPropietarioActivity : AppCompatActivity() {
    private lateinit var binding:
            ActivityMainPropietarioBinding

    private lateinit var auth:
            FirebaseAuth

    private lateinit var firestore:
            FirebaseFirestore

    private var listenerPerfil:
            ListenerRegistration? = null

    private var listenerEmpresa:
            ListenerRegistration? = null

    private var listenerChoferes:
            ListenerRegistration? = null

    private var listenerVendedores:
            ListenerRegistration? = null

    private var listenerVehiculos:
            ListenerRegistration? = null

    private var empresaIdActual = ""

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        binding =
            ActivityMainPropietarioBinding.inflate(
                layoutInflater
            )

        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        configurarPantalla()
        configurarEventos()
        prepararPantalla()
    }

    override fun onStart() {
        super.onStart()

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

    private fun prepararPantalla() {

        binding.tvSaludo.text =
            "Hola, propietario"

        binding.tvEmpresa.text =
            "Cargando empresa..."

        binding.tvTotalChoferes.text = "0"
        binding.tvDetalleChoferes.text =
            "0 activos · 0 disponibles"

        binding.tvTotalVendedores.text = "0"
        binding.tvDetalleVendedores.text =
            "0 vendedores activos"

        binding.tvTotalVehiculos.text = "0"
        binding.tvDetalleVehiculos.text =
            "0 vehículos disponibles"

        mostrarCargando(
            mostrar = true,
            mensaje = "Cargando tu empresa..."
        )
    }

    private fun configurarEventos() {

        binding.btnCerrarSesion
            .setOnClickListener {

                confirmarCerrarSesion()
            }

        binding.btnActualizar
            .setOnClickListener {

                actualizarInformacion()
            }

        binding.cardChoferes
            .setOnClickListener {

                mostrarModuloPendiente(
                    "Administración de choferes"
                )
            }

        binding.cardVendedores
            .setOnClickListener {

                mostrarModuloPendiente(
                    "Administración de vendedores"
                )
            }

        binding.cardVehiculos
            .setOnClickListener {

                mostrarModuloPendiente(
                    "Administración de vehículos"
                )
            }

        binding.btnMiEmpresa
            .setOnClickListener {

                val intent = Intent(
                    this,
                    MiEmpresaActivity::class.java
                )

                startActivity(intent)
            }

        binding.btnChoferes.setOnClickListener {

            val intent = Intent(
                this,
                AdministrarChoferesActivity::class.java
            )

            startActivity(intent)
        }

        binding.btnVendedores
            .setOnClickListener {

                val intent =
                    Intent(
                        this,
                        CrearVendedorActivity::class.java
                    )

                startActivity(intent)
            }

        binding.btnVehiculos.setOnClickListener {

            val intent = Intent(
                this,
                AdministrarVehiculosActivity::class.java
            )

            startActivity(intent)
        }

        binding.btnPerfil
            .setOnClickListener {

                mostrarModuloPendiente(
                    "Perfil del propietario"
                )
            }
    }

    private fun escucharPerfilPropietario() {

        val usuarioActual =
            auth.currentUser

        if (usuarioActual == null) {
            abrirLogin()
            return
        }

        listenerPerfil?.remove()
        listenerPerfil = null

        mostrarCargando(
            mostrar = true,
            mensaje = "Validando cuenta..."
        )

        listenerPerfil = firestore
            .collection(
                COLECCION_USUARIOS_GESTION
            )
            .document(
                usuarioActual.uid
            )
            .addSnapshotListener {
                    documento, error ->

                if (error != null) {

                    mostrarCargando(false)

                    Toast.makeText(
                        this,
                        "No se pudo cargar tu perfil: " +
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

                    cerrarSesionInvalida(
                        "Tu cuenta no está registrada en AdminYa."
                    )

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
            leerTexto(
                documento,
                CAMPO_ROL
            ).lowercase(Locale.ROOT)

        val estado =
            leerTexto(
                documento,
                CAMPO_ESTADO
            ).lowercase(Locale.ROOT)

        val nombres =
            leerTexto(
                documento,
                CAMPO_NOMBRES
            )

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

        when {

            rol != ROL_PROPIETARIO -> {

                cerrarSesionInvalida(
                    "Esta cuenta no pertenece a un propietario."
                )
            }

            estado != ESTADO_ACTIVO -> {

                cerrarSesionInvalida(
                    "Tu cuenta de propietario no está activa."
                )
            }

            empresaId.isBlank() -> {

                mostrarCargando(false)

                abrirCrearEmpresa()
            }

            else -> {

                binding.tvSaludo.text =
                    if (nombres.isBlank()) {

                        "Hola, propietario"

                    } else {

                        "Hola, $nombres"
                    }

                binding.tvEmpresa.text =
                    empresaNombre.ifBlank {

                        empresaId
                    }

                if (
                    empresaIdActual != empresaId
                ) {

                    empresaIdActual = empresaId

                    removerListenersEmpresa()

                    escucharEmpresa(
                        empresaId
                    )

                    escucharChoferes(
                        empresaId
                    )

                    escucharVendedores(
                        empresaId
                    )

                    escucharVehiculos(
                        empresaId
                    )
                }

                mostrarCargando(false)
            }
        }
    }

    private fun escucharEmpresa(
        empresaId: String
    ) {

        listenerEmpresa?.remove()
        listenerEmpresa = null

        listenerEmpresa = firestore
            .collection(
                COLECCION_EMPRESAS
            )
            .document(
                empresaId
            )
            .addSnapshotListener {
                    documento, error ->

                if (error != null) {

                    Toast.makeText(
                        this,
                        "No se pudieron cargar los datos de la empresa.",
                        Toast.LENGTH_LONG
                    ).show()

                    return@addSnapshotListener
                }

                if (
                    documento == null ||
                    !documento.exists()
                ) {

                    binding.tvEmpresa.text =
                        "Empresa no encontrada"

                    return@addSnapshotListener
                }

                val nombreEmpresa =
                    leerTexto(
                        documento,
                        CAMPO_NOMBRE_EMPRESA
                    )

                if (nombreEmpresa.isNotBlank()) {

                    binding.tvEmpresa.text =
                        nombreEmpresa
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

                if (error != null) {

                    binding.tvTotalChoferes.text =
                        "0"

                    binding.tvDetalleChoferes.text =
                        "No se pudieron cargar"

                    return@addSnapshotListener
                }

                if (resultado == null) {
                    return@addSnapshotListener
                }

                val choferes =
                    resultado.documents.filter { documento ->

                        leerTexto(
                            documento,
                            CAMPO_ESTADO
                        ).lowercase(Locale.ROOT) !=
                                ESTADO_ELIMINADO
                    }

                val activos =
                    choferes.count { documento ->

                        leerTexto(
                            documento,
                            CAMPO_ESTADO
                        ).lowercase(Locale.ROOT) ==
                                ESTADO_ACTIVO
                    }

                val disponibles =
                    choferes.count { documento ->

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

                        val disponibleBooleano =
                            leerBooleano(
                                documento,
                                CAMPO_DISPONIBLE
                            )

                        estado == ESTADO_ACTIVO &&
                                (
                                        disponibilidad ==
                                                DISPONIBILIDAD_DISPONIBLE ||
                                                disponibleBooleano
                                        )
                    }

                binding.tvTotalChoferes.text =
                    choferes.size.toString()

                binding.tvDetalleChoferes.text =
                    "$activos activos · " +
                            "$disponibles disponibles"
            }
    }

    private fun escucharVendedores(
        empresaId: String
    ) {

        listenerVendedores?.remove()
        listenerVendedores = null

        /*
         * Consultamos solamente por empresaId.
         * El rol vendedor se filtra en el teléfono.
         * Así no se necesita índice compuesto.
         */
        listenerVendedores = firestore
            .collection(
                COLECCION_USUARIOS_GESTION
            )
            .whereEqualTo(
                CAMPO_EMPRESA_ID,
                empresaId
            )
            .addSnapshotListener {
                    resultado, error ->

                if (error != null) {

                    binding.tvTotalVendedores.text =
                        "0"

                    binding.tvDetalleVendedores.text =
                        "No se pudieron cargar"

                    return@addSnapshotListener
                }

                if (resultado == null) {
                    return@addSnapshotListener
                }

                val vendedores =
                    resultado.documents.filter { documento ->

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

                        rol == ROL_VENDEDOR &&
                                estado !=
                                ESTADO_ELIMINADO
                    }

                val activos =
                    vendedores.count { documento ->

                        leerTexto(
                            documento,
                            CAMPO_ESTADO
                        ).lowercase(Locale.ROOT) ==
                                ESTADO_ACTIVO
                    }

                binding.tvTotalVendedores.text =
                    vendedores.size.toString()

                binding.tvDetalleVendedores.text =
                    "$activos vendedores activos"
            }
    }

    private fun escucharVehiculos(
        empresaId: String
    ) {

        listenerVehiculos?.remove()
        listenerVehiculos = null

        listenerVehiculos = firestore
            .collection(
                COLECCION_VEHICULOS
            )
            .whereEqualTo(
                CAMPO_EMPRESA_ID,
                empresaId
            )
            .addSnapshotListener {
                    resultado, error ->

                if (error != null) {

                    binding.tvTotalVehiculos.text =
                        "0"

                    binding.tvDetalleVehiculos.text =
                        "No se pudieron cargar"

                    return@addSnapshotListener
                }

                if (resultado == null) {
                    return@addSnapshotListener
                }

                val vehiculos =
                    resultado.documents.filter { documento ->

                        leerTexto(
                            documento,
                            CAMPO_ESTADO
                        ).lowercase(Locale.ROOT) !=
                                ESTADO_ELIMINADO
                    }

                val disponibles =
                    vehiculos.count { documento ->

                        leerTexto(
                            documento,
                            CAMPO_ESTADO
                        ).lowercase(Locale.ROOT) ==
                                DISPONIBILIDAD_DISPONIBLE
                    }

                binding.tvTotalVehiculos.text =
                    vehiculos.size.toString()

                binding.tvDetalleVehiculos.text =
                    "$disponibles vehículos disponibles"
            }
    }

    private fun actualizarInformacion() {

        empresaIdActual = ""

        removerTodosLosListeners()

        prepararPantalla()

        escucharPerfilPropietario()
    }

    private fun mostrarModuloPendiente(
        modulo: String
    ) {

        Toast.makeText(
            this,
            "$modulo será el siguiente módulo que crearemos.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun confirmarCerrarSesion() {

        MaterialAlertDialogBuilder(this)
            .setTitle(
                "Cerrar sesión"
            )
            .setMessage(
                "¿Deseas salir de tu cuenta de AdminYa?"
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

        removerTodosLosListeners()

        auth.signOut()

        abrirLogin()
    }

    private fun cerrarSesionInvalida(
        mensaje: String
    ) {

        removerTodosLosListeners()

        auth.signOut()

        Toast.makeText(
            this,
            mensaje,
            Toast.LENGTH_LONG
        ).show()

        abrirLogin()
    }

    private fun abrirCrearEmpresa() {

        val intent = Intent(
            this,
            CrearEmpresaActivity::class.java
        ).apply {

            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        startActivity(intent)
        finish()
    }

    private fun abrirLogin() {

        val intent = Intent(
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

    private fun mostrarCargando(
        mostrar: Boolean,
        mensaje: String = "Cargando..."
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

    private fun leerTexto(
        documento: DocumentSnapshot,
        campo: String
    ): String {

        return when (
            val valor = documento.get(campo)
        ) {

            is String -> {
                valor.trim()
            }

            is Number -> {
                valor.toString()
            }

            is Boolean -> {
                valor.toString()
            }

            else -> {
                ""
            }
        }
    }

    private fun leerBooleano(
        documento: DocumentSnapshot,
        campo: String
    ): Boolean {

        return when (
            val valor = documento.get(campo)
        ) {

            is Boolean -> {
                valor
            }

            is String -> {

                valor.trim().equals(
                    "true",
                    ignoreCase = true
                )
            }

            is Number -> {
                valor.toInt() != 0
            }

            else -> {
                false
            }
        }
    }

    private fun removerListenersEmpresa() {

        listenerEmpresa?.remove()
        listenerChoferes?.remove()
        listenerVendedores?.remove()
        listenerVehiculos?.remove()

        listenerEmpresa = null
        listenerChoferes = null
        listenerVendedores = null
        listenerVehiculos = null
    }

    private fun removerTodosLosListeners() {

        listenerPerfil?.remove()

        listenerPerfil = null

        removerListenersEmpresa()
    }

    override fun onStop() {

        removerTodosLosListeners()

        super.onStop()
    }

    companion object {

        private const val COLECCION_USUARIOS_GESTION =
            "usuariosgestionpasajes"

        private const val COLECCION_EMPRESAS =
            "empresaspasajes"

        private const val COLECCION_CHOFERES =
            "choferespasajes"

        private const val COLECCION_VEHICULOS =
            "vehiculospasajes"

        private const val CAMPO_NOMBRES =
            "nombres"

        private const val CAMPO_ROL =
            "rol"

        private const val CAMPO_ESTADO =
            "estado"

        private const val CAMPO_EMPRESA_ID =
            "empresaId"

        private const val CAMPO_EMPRESA_NOMBRE =
            "empresaNombre"

        private const val CAMPO_NOMBRE_EMPRESA =
            "nombre"

        private const val CAMPO_DISPONIBILIDAD =
            "disponibilidad"

        private const val CAMPO_DISPONIBLE =
            "disponible"

        private const val ROL_PROPIETARIO =
            "propietario"

        private const val ROL_VENDEDOR =
            "vendedor"

        private const val ESTADO_ACTIVO =
            "activo"

        private const val ESTADO_ELIMINADO =
            "eliminado"

        private const val DISPONIBILIDAD_DISPONIBLE =
            "disponible"
    }
}