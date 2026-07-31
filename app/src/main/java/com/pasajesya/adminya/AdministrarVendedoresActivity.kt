package com.pasajesya.adminya

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.pasajesya.adminya.databinding.ActivityAdministrarVendedoresBinding
import com.pasajesya.adminya.databinding.DialogEditarVendedorBinding
import java.util.Locale

class AdministrarVendedoresActivity : AppCompatActivity() {

    private lateinit var binding:
            ActivityAdministrarVendedoresBinding

    private lateinit var auth:
            FirebaseAuth

    private lateinit var firestore:
            FirebaseFirestore

    private lateinit var adaptador:
            VendedorAdminAdapter

    private var listenerPerfil:
            ListenerRegistration? = null

    private var listenerVendedores:
            ListenerRegistration? = null

    private var empresaIdActual = ""
    private var empresaNombreActual = ""

    private val listaCompleta =
        mutableListOf<VendedorAdmin>()

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        binding =
            ActivityAdministrarVendedoresBinding.inflate(
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
            VendedorAdminAdapter(

                alEditar = { vendedor ->

                    mostrarFormularioEditar(
                        vendedor
                    )
                },

                alCambiarEstado = { vendedor ->

                    mostrarDialogoEstado(
                        vendedor
                    )
                },

                alRestablecerPassword = { vendedor ->

                    confirmarRestablecimiento(
                        vendedor
                    )
                }
            )

        binding.recyclerVendedores.layoutManager =
            LinearLayoutManager(this)

        binding.recyclerVendedores.adapter =
            adaptador
    }

    private fun configurarEventos() {

        binding.btnVolver.setOnClickListener {
            finish()
        }

        binding.fabNuevoVendedor
            .setOnClickListener {

                val intent =
                    Intent(
                        this,
                        CrearVendedorActivity::class.java
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
                COLECCION_USUARIOS
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

                procesarPerfil(
                    documento
                )
            }
    }

    private fun procesarPerfil(
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

                escucharVendedores(
                    empresaId
                )
            }
        }
    }

    private fun escucharVendedores(
        empresaId: String
    ) {

        listenerVendedores?.remove()
        listenerVendedores = null

        listenerVendedores = firestore
            .collection(
                COLECCION_USUARIOS
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
                        "No se pudieron cargar los vendedores: " +
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

                        convertirVendedor(
                            documento
                        )
                    }
                    ?.filter { vendedor ->

                        vendedor.rol == ROL_VENDEDOR &&
                                vendedor.estado !=
                                ESTADO_ELIMINADO
                    }
                    ?.sortedBy { vendedor ->

                        vendedor
                            .obtenerNombreCompleto()
                            .lowercase(Locale.ROOT)
                    }
                    ?.let { vendedores ->

                        listaCompleta.addAll(
                            vendedores
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

    private fun convertirVendedor(
        documento: DocumentSnapshot
    ): VendedorAdmin {

        return VendedorAdmin(

            uid =
                documento.id,

            empresaId =
                leerTexto(
                    documento,
                    CAMPO_EMPRESA_ID
                ),

            empresaNombre =
                leerTexto(
                    documento,
                    CAMPO_EMPRESA_NOMBRE
                ),

            nombres =
                leerTexto(
                    documento,
                    CAMPO_NOMBRES
                ),

            apellidos =
                leerTexto(
                    documento,
                    CAMPO_APELLIDOS
                ),

            nombreCompleto =
                leerTexto(
                    documento,
                    CAMPO_NOMBRE_COMPLETO
                ),

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

            correo =
                leerTexto(
                    documento,
                    CAMPO_CORREO
                ),

            rol =
                leerTexto(
                    documento,
                    CAMPO_ROL
                ).lowercase(Locale.ROOT),

            estado =
                leerTexto(
                    documento,
                    CAMPO_ESTADO
                ).lowercase(Locale.ROOT)
                    .ifBlank {
                        ESTADO_ACTIVO
                    },

            registroCompleto =
                leerBooleano(
                    documento,
                    CAMPO_REGISTRO_COMPLETO,
                    true
                ),

            debeCambiarPassword =
                leerBooleano(
                    documento,
                    CAMPO_DEBE_CAMBIAR_PASSWORD,
                    true
                )
        )
    }

    private fun actualizarContadores() {

        binding.tvTotal.text =
            listaCompleta.size.toString()

        binding.tvActivos.text =
            listaCompleta.count { vendedor ->

                vendedor.estado ==
                        ESTADO_ACTIVO
            }.toString()

        binding.tvInactivos.text =
            listaCompleta.count { vendedor ->

                vendedor.estado !=
                        ESTADO_ACTIVO
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

                listaCompleta.filter { vendedor ->

                    vendedor.obtenerNombreCompleto()
                        .lowercase(Locale.ROOT)
                        .contains(consulta) ||

                            vendedor.dni
                                .lowercase(Locale.ROOT)
                                .contains(consulta) ||

                            vendedor.celular
                                .lowercase(Locale.ROOT)
                                .contains(consulta) ||

                            vendedor.correo
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

        binding.recyclerVendedores.visibility =
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

                "No se encontraron vendedores."

            } else {

                "Todavía no tienes vendedores registrados."
            }
    }

    private fun mostrarFormularioEditar(
        vendedor: VendedorAdmin
    ) {

        val formulario =
            DialogEditarVendedorBinding.inflate(
                layoutInflater
            )

        formulario.tvCorreoCuenta.text =
            "Cuenta: ${vendedor.correo}"

        formulario.etNombres.setText(
            vendedor.nombres
        )

        formulario.etApellidos.setText(
            vendedor.apellidos
        )

        formulario.etDni.setText(
            vendedor.dni
        )

        formulario.etCelular.setText(
            vendedor.celular
        )

        val dialogo =
            MaterialAlertDialogBuilder(this)
                .setTitle(
                    "Editar vendedor"
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

                validarEdicion(
                    vendedor = vendedor,
                    formulario = formulario,
                    dialogo = dialogo
                )
            }
        }

        dialogo.show()
    }

    private fun validarEdicion(
        vendedor: VendedorAdmin,
        formulario: DialogEditarVendedorBinding,
        dialogo: AlertDialog
    ) {

        formulario.tilNombres.error = null
        formulario.tilApellidos.error = null
        formulario.tilDni.error = null
        formulario.tilCelular.error = null

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

        val dniRepetido =
            listaCompleta.any { otroVendedor ->

                otroVendedor.uid != vendedor.uid &&
                        otroVendedor.dni == dni &&
                        otroVendedor.estado !=
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

            dni.length != 8 ||
                    !dni.all { it.isDigit() } -> {

                formulario.tilDni.error =
                    "El DNI debe tener 8 dígitos."
            }

            dniRepetido -> {

                formulario.tilDni.error =
                    "Ya existe otro vendedor con este DNI."
            }

            celular.length != 9 ||
                    !celular.all { it.isDigit() } -> {

                formulario.tilCelular.error =
                    "El celular debe tener 9 dígitos."
            }

            else -> {

                guardarCambios(
                    vendedor = vendedor,
                    formularioDialogo = dialogo,
                    nombres = nombres,
                    apellidos = apellidos,
                    dni = dni,
                    celular = celular
                )
            }
        }
    }

    private fun guardarCambios(
        vendedor: VendedorAdmin,
        formularioDialogo: AlertDialog,
        nombres: String,
        apellidos: String,
        dni: String,
        celular: String
    ) {

        mostrarCargando(true)

        val nombreCompleto =
            "$nombres $apellidos".trim()

        firestore
            .collection(
                COLECCION_USUARIOS
            )
            .document(
                vendedor.uid
            )
            .update(
                mapOf(
                    CAMPO_NOMBRES to
                            nombres,

                    CAMPO_APELLIDOS to
                            apellidos,

                    CAMPO_NOMBRE_COMPLETO to
                            nombreCompleto,

                    CAMPO_DNI to
                            dni,

                    CAMPO_CELULAR to
                            celular,

                    CAMPO_FECHA_ACTUALIZACION to
                            FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {

                mostrarCargando(false)
                formularioDialogo.dismiss()

                Toast.makeText(
                    this,
                    "Datos del vendedor actualizados.",
                    Toast.LENGTH_LONG
                ).show()
            }
            .addOnFailureListener { error ->

                mostrarCargando(false)

                Toast.makeText(
                    this,
                    "No se pudo actualizar: " +
                            (
                                    error.localizedMessage
                                        ?: "error desconocido"
                                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun mostrarDialogoEstado(
        vendedor: VendedorAdmin
    ) {

        val opciones =
            arrayOf(
                "Activo",
                "Inactivo",
                "Suspendido"
            )

        var seleccion =
            when (vendedor.estado) {

                ESTADO_INACTIVO -> 1
                ESTADO_SUSPENDIDO -> 2
                else -> 0
            }

        MaterialAlertDialogBuilder(this)
            .setTitle(
                "Estado de ${vendedor.obtenerNombreCompleto()}"
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

                val nuevoEstado =
                    when (seleccion) {

                        1 -> ESTADO_INACTIVO
                        2 -> ESTADO_SUSPENDIDO
                        else -> ESTADO_ACTIVO
                    }

                actualizarEstado(
                    vendedor,
                    nuevoEstado
                )
            }
            .show()
    }

    private fun actualizarEstado(
        vendedor: VendedorAdmin,
        nuevoEstado: String
    ) {

        mostrarCargando(true)

        firestore
            .collection(
                COLECCION_USUARIOS
            )
            .document(
                vendedor.uid
            )
            .update(
                mapOf(
                    CAMPO_ESTADO to
                            nuevoEstado,

                    CAMPO_FECHA_ACTUALIZACION to
                            FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {

                mostrarCargando(false)

                Toast.makeText(
                    this,
                    "Estado del vendedor actualizado.",
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

    private fun confirmarRestablecimiento(
        vendedor: VendedorAdmin
    ) {

        if (vendedor.correo.isBlank()) {

            Toast.makeText(
                this,
                "El vendedor no tiene un correo registrado.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(
                "Cambiar contraseña"
            )
            .setMessage(
                "Se enviará un enlace de cambio de contraseña a:\n\n" +
                        vendedor.correo
            )
            .setNegativeButton(
                "CANCELAR",
                null
            )
            .setPositiveButton(
                "ENVIAR"
            ) { _, _ ->

                enviarRestablecimiento(
                    vendedor.correo
                )
            }
            .show()
    }

    private fun enviarRestablecimiento(
        correo: String
    ) {

        mostrarCargando(true)

        auth.sendPasswordResetEmail(
            correo
        )
            .addOnSuccessListener {

                mostrarCargando(false)

                Toast.makeText(
                    this,
                    "Se envió el correo para cambiar la contraseña.",
                    Toast.LENGTH_LONG
                ).show()
            }
            .addOnFailureListener { error ->

                mostrarCargando(false)

                Toast.makeText(
                    this,
                    "No se pudo enviar el correo: " +
                            (
                                    error.localizedMessage
                                        ?: "error desconocido"
                                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
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

        binding.fabNuevoVendedor.isEnabled =
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

        listenerVendedores?.remove()
        listenerVendedores = null
    }

    override fun onStop() {

        removerListeners()

        super.onStop()
    }

    companion object {

        private const val COLECCION_USUARIOS =
            "usuariosgestionpasajes"

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

        private const val CAMPO_REGISTRO_COMPLETO =
            "registroCompleto"

        private const val CAMPO_DEBE_CAMBIAR_PASSWORD =
            "debeCambiarPassword"

        private const val CAMPO_FECHA_ACTUALIZACION =
            "fechaActualizacion"

        private const val ROL_PROPIETARIO =
            "propietario"

        private const val ROL_VENDEDOR =
            "vendedor"

        private const val ESTADO_ACTIVO =
            "activo"

        private const val ESTADO_INACTIVO =
            "inactivo"

        private const val ESTADO_SUSPENDIDO =
            "suspendido"

        private const val ESTADO_ELIMINADO =
            "eliminado"
    }
}