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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.pasajesya.adminya.databinding.ActivityCrearEmpresaBinding
import java.text.Normalizer
import java.util.Locale

class CrearEmpresaActivity : AppCompatActivity() {
    private lateinit var binding:
            ActivityCrearEmpresaBinding

    private lateinit var auth:
            FirebaseAuth

    private lateinit var firestore:
            FirebaseFirestore

    private var creandoEmpresa = false
    private var navegando = false

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        binding =
            ActivityCrearEmpresaBinding.inflate(
                layoutInflater
            )

        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        configurarPantalla()
        configurarEventos()
        verificarCuentaPropietario()
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

    private fun configurarEventos() {

        binding.btnCrearEmpresa
            .setOnClickListener {

                validarFormulario()
            }

        binding.btnCerrarSesion
            .setOnClickListener {

                confirmarCerrarSesion()
            }
    }

    private fun verificarCuentaPropietario() {

        val usuarioActual =
            auth.currentUser

        if (usuarioActual == null) {

            abrirLogin()
            return
        }

        mostrarCargando(
            mostrar = true,
            mensaje = "Validando tu cuenta..."
        )

        firestore
            .collection(
                COLECCION_USUARIOS_GESTION
            )
            .document(
                usuarioActual.uid
            )
            .get()
            .addOnSuccessListener { documento ->

                mostrarCargando(false)

                if (!documento.exists()) {

                    cerrarSesionInvalida(
                        "Tu cuenta no está registrada en AdminYa."
                    )

                    return@addOnSuccessListener
                }

                val rol =
                    documento.get(CAMPO_ROL)
                        ?.toString()
                        ?.trim()
                        ?.lowercase(Locale.ROOT)
                        .orEmpty()

                val estado =
                    documento.get(CAMPO_ESTADO)
                        ?.toString()
                        ?.trim()
                        ?.lowercase(Locale.ROOT)
                        .orEmpty()

                val empresaId =
                    documento.get(CAMPO_EMPRESA_ID)
                        ?.toString()
                        ?.trim()
                        .orEmpty()

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

                    empresaId.isNotBlank() -> {

                        /*
                         * Ya tiene una empresa.
                         * No permitimos crear otra desde aquí.
                         */
                        abrirMainPropietario()
                    }
                }
            }
            .addOnFailureListener { error ->

                mostrarCargando(false)

                Toast.makeText(
                    this,
                    "No se pudo validar tu cuenta: " +
                            (
                                    error.localizedMessage
                                        ?: "error desconocido"
                                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun validarFormulario() {

        limpiarErrores()

        val nombreEmpresa =
            texto(
                binding.etNombreEmpresa.text
            )

        val nombreCortoIngresado =
            texto(
                binding.etNombreCorto.text
            )

        val ruc =
            texto(
                binding.etRuc.text
            )

        val telefono =
            texto(
                binding.etTelefono.text
            )

        val direccion =
            texto(
                binding.etDireccion.text
            )

        val ciudad =
            texto(
                binding.etCiudad.text
            )

        val descripcion =
            texto(
                binding.etDescripcion.text
            )

        val nombreCorto =
            nombreCortoIngresado.ifBlank {

                generarNombreCorto(
                    nombreEmpresa
                )
            }

        when {

            nombreEmpresa.length < 3 -> {

                binding.tilNombreEmpresa.error =
                    "Ingresa el nombre de la empresa."

                binding.etNombreEmpresa.requestFocus()
            }

            nombreEmpresa.length > 60 -> {

                binding.tilNombreEmpresa.error =
                    "El nombre es demasiado largo."

                binding.etNombreEmpresa.requestFocus()
            }

            nombreCorto.length > 10 -> {

                binding.tilNombreCorto.error =
                    "El nombre corto debe tener máximo 10 caracteres."

                binding.etNombreCorto.requestFocus()
            }

            ruc.isNotBlank() &&
                    ruc.length != 11 -> {

                binding.tilRuc.error =
                    "El RUC debe tener 11 dígitos."

                binding.etRuc.requestFocus()
            }

            telefono.length != 9 -> {

                binding.tilTelefono.error =
                    "El celular debe tener 9 dígitos."

                binding.etTelefono.requestFocus()
            }

            direccion.length < 5 -> {

                binding.tilDireccion.error =
                    "Ingresa la dirección de la empresa."

                binding.etDireccion.requestFocus()
            }

            ciudad.length < 3 -> {

                binding.tilCiudad.error =
                    "Ingresa la ciudad o localidad."

                binding.etCiudad.requestFocus()
            }

            !binding.checkConfirmacion.isChecked -> {

                Toast.makeText(
                    this,
                    "Confirma que eres responsable de la empresa.",
                    Toast.LENGTH_LONG
                ).show()
            }

            else -> {

                crearEmpresa(
                    nombreEmpresa = nombreEmpresa,
                    nombreCorto = nombreCorto,
                    ruc = ruc,
                    telefono = telefono,
                    direccion = direccion,
                    ciudad = ciudad,
                    descripcion = descripcion
                )
            }
        }
    }

    private fun crearEmpresa(
        nombreEmpresa: String,
        nombreCorto: String,
        ruc: String,
        telefono: String,
        direccion: String,
        ciudad: String,
        descripcion: String
    ) {

        if (creandoEmpresa) {
            return
        }

        val usuarioActual =
            auth.currentUser

        if (usuarioActual == null) {

            abrirLogin()
            return
        }

        creandoEmpresa = true

        mostrarCargando(
            mostrar = true,
            mensaje = "Creando tu empresa..."
        )

        /*
         * Firestore genera un ID automático.
         * Ejemplo:
         *
         * empresaspasajes/4gT7hK...
         */
        val empresaReferencia =
            firestore
                .collection(
                    COLECCION_EMPRESAS
                )
                .document()

        val propietarioReferencia =
            firestore
                .collection(
                    COLECCION_USUARIOS_GESTION
                )
                .document(
                    usuarioActual.uid
                )

        val empresaId =
            empresaReferencia.id

        val nombreBusqueda =
            normalizarBusqueda(
                nombreEmpresa
            )

        val datosEmpresa =
            hashMapOf<String, Any>(

                CAMPO_EMPRESA_ID to
                        empresaId,

                CAMPO_PROPIETARIO_UID to
                        usuarioActual.uid,

                CAMPO_NOMBRE_EMPRESA to
                        nombreEmpresa,

                CAMPO_NOMBRE_CORTO to
                        nombreCorto.uppercase(
                            Locale.ROOT
                        ),

                CAMPO_NOMBRE_BUSQUEDA to
                        nombreBusqueda,

                CAMPO_RUC to
                        ruc,

                CAMPO_TELEFONO to
                        telefono,

                CAMPO_DIRECCION to
                        direccion,

                CAMPO_CIUDAD to
                        ciudad,

                CAMPO_DESCRIPCION to
                        descripcion,

                CAMPO_LOGO_URL to
                        "",

                CAMPO_COLOR_PRINCIPAL to
                        COLOR_PRINCIPAL_PREDETERMINADO,

                CAMPO_COLOR_SECUNDARIO to
                        COLOR_SECUNDARIO_PREDETERMINADO,

                CAMPO_ESTADO to
                        ESTADO_ACTIVO,

                CAMPO_VISIBLE_USUARIOS to
                        true,

                CAMPO_ORDEN to
                        0,

                CAMPO_RUTAS_RESUMEN to
                        "",

                CAMPO_FECHA_REGISTRO to
                        FieldValue.serverTimestamp(),

                CAMPO_FECHA_ACTUALIZACION to
                        FieldValue.serverTimestamp()
            )

        val actualizacionPropietario =
            hashMapOf<String, Any>(

                CAMPO_EMPRESA_ID to
                        empresaId,

                CAMPO_EMPRESA_NOMBRE to
                        nombreEmpresa,

                CAMPO_FECHA_ACTUALIZACION to
                        FieldValue.serverTimestamp()
            )

        /*
         * La empresa y el propietario se actualizan
         * dentro de una sola transacción.
         */
        firestore
            .runTransaction { transaccion ->

                val perfil =
                    transaccion.get(
                        propietarioReferencia
                    )

                if (!perfil.exists()) {

                    throw IllegalStateException(
                        "No se encontró el perfil del propietario."
                    )
                }

                val rol =
                    perfil.get(CAMPO_ROL)
                        ?.toString()
                        ?.trim()
                        ?.lowercase(Locale.ROOT)
                        .orEmpty()

                val estado =
                    perfil.get(CAMPO_ESTADO)
                        ?.toString()
                        ?.trim()
                        ?.lowercase(Locale.ROOT)
                        .orEmpty()

                val empresaActual =
                    perfil.get(CAMPO_EMPRESA_ID)
                        ?.toString()
                        ?.trim()
                        .orEmpty()

                when {

                    rol != ROL_PROPIETARIO -> {

                        throw IllegalStateException(
                            "La cuenta no pertenece a un propietario."
                        )
                    }

                    estado != ESTADO_ACTIVO -> {

                        throw IllegalStateException(
                            "La cuenta del propietario no está activa."
                        )
                    }

                    empresaActual.isNotBlank() -> {

                        /*
                         * Ya tiene empresa.
                         * No realizamos una segunda creación.
                         */
                        return@runTransaction empresaActual
                    }
                }

                transaccion.set(
                    empresaReferencia,
                    datosEmpresa
                )

                transaccion.update(
                    propietarioReferencia,
                    actualizacionPropietario
                )

                empresaId
            }
            .addOnSuccessListener {

                creandoEmpresa = false
                mostrarCargando(false)

                Toast.makeText(
                    this,
                    "Empresa registrada correctamente.",
                    Toast.LENGTH_LONG
                ).show()

                abrirMainPropietario()
            }
            .addOnFailureListener { error ->

                creandoEmpresa = false
                mostrarCargando(false)

                Toast.makeText(
                    this,
                    obtenerMensajeError(
                        error
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun generarNombreCorto(
        nombreEmpresa: String
    ): String {

        val palabras =
            nombreEmpresa
                .trim()
                .split(
                    Regex("\\s+")
                )
                .filter { palabra ->

                    palabra.isNotBlank()
                }

        val iniciales =
            palabras
                .take(3)
                .joinToString("") { palabra ->

                    palabra
                        .first()
                        .uppercaseChar()
                        .toString()
                }

        return iniciales.ifBlank {

            nombreEmpresa
                .take(3)
                .uppercase(Locale.ROOT)
        }
    }

    private fun normalizarBusqueda(
        texto: String
    ): String {

        val sinTildes =
            Normalizer.normalize(
                texto,
                Normalizer.Form.NFD
            )
                .replace(
                    Regex("\\p{InCombiningDiacriticalMarks}+"),
                    ""
                )

        return sinTildes
            .trim()
            .lowercase(Locale.ROOT)
            .replace(
                Regex("\\s+"),
                " "
            )
    }

    private fun obtenerMensajeError(
        error: Exception
    ): String {

        val mensaje =
            error.localizedMessage
                .orEmpty()

        return when {

            mensaje.contains(
                "PERMISSION_DENIED",
                ignoreCase = true
            ) -> {

                "Firestore no permite crear la empresa. " +
                        "Revisa las reglas de seguridad."
            }

            mensaje.contains(
                "offline",
                ignoreCase = true
            ) -> {

                "No hay conexión con Internet."
            }

            mensaje.isNotBlank() -> {

                "No se pudo crear la empresa: $mensaje"
            }

            else -> {

                "No se pudo crear la empresa."
            }
        }
    }

    private fun confirmarCerrarSesion() {

        MaterialAlertDialogBuilder(this)
            .setTitle(
                "Cerrar sesión"
            )
            .setMessage(
                "¿Deseas salir sin registrar tu empresa?"
            )
            .setNegativeButton(
                "CANCELAR",
                null
            )
            .setPositiveButton(
                "SALIR"
            ) { _, _ ->

                auth.signOut()
                abrirLogin()
            }
            .show()
    }

    private fun cerrarSesionInvalida(
        mensaje: String
    ) {

        auth.signOut()

        Toast.makeText(
            this,
            mensaje,
            Toast.LENGTH_LONG
        ).show()

        abrirLogin()
    }

    private fun abrirMainPropietario() {

        navegarA(
            MainPropietarioActivity::class.java
        )
    }

    private fun abrirLogin() {

        navegarA(
            LoginActivity::class.java
        )
    }

    private fun navegarA(
        actividad: Class<*>
    ) {

        if (navegando) {
            return
        }

        navegando = true

        val intent =
            Intent(
                this,
                actividad
            ).apply {

                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

        startActivity(intent)
        finish()
    }

    private fun texto(
        valor: CharSequence?
    ): String {

        return valor
            ?.toString()
            ?.trim()
            .orEmpty()
    }

    private fun limpiarErrores() {

        binding.tilNombreEmpresa.error = null
        binding.tilNombreCorto.error = null
        binding.tilRuc.error = null
        binding.tilTelefono.error = null
        binding.tilDireccion.error = null
        binding.tilCiudad.error = null
        binding.tilDescripcion.error = null
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

        binding.btnCrearEmpresa.isEnabled =
            !mostrar

        binding.btnCerrarSesion.isEnabled =
            !mostrar
    }

    companion object {

        private const val COLECCION_USUARIOS_GESTION =
            "usuariosgestionpasajes"

        private const val COLECCION_EMPRESAS =
            "empresaspasajes"

        private const val CAMPO_EMPRESA_ID =
            "empresaId"

        private const val CAMPO_EMPRESA_NOMBRE =
            "empresaNombre"

        private const val CAMPO_PROPIETARIO_UID =
            "propietarioUid"

        private const val CAMPO_NOMBRE_EMPRESA =
            "nombre"

        private const val CAMPO_NOMBRE_CORTO =
            "nombreCorto"

        private const val CAMPO_NOMBRE_BUSQUEDA =
            "nombreBusqueda"

        private const val CAMPO_RUC =
            "ruc"

        private const val CAMPO_TELEFONO =
            "telefono"

        private const val CAMPO_DIRECCION =
            "direccion"

        private const val CAMPO_CIUDAD =
            "ciudad"

        private const val CAMPO_DESCRIPCION =
            "descripcion"

        private const val CAMPO_LOGO_URL =
            "logoUrl"

        private const val CAMPO_COLOR_PRINCIPAL =
            "colorPrincipal"

        private const val CAMPO_COLOR_SECUNDARIO =
            "colorSecundario"

        private const val CAMPO_ESTADO =
            "estado"

        private const val CAMPO_VISIBLE_USUARIOS =
            "visibleUsuarios"

        private const val CAMPO_ORDEN =
            "orden"

        private const val CAMPO_RUTAS_RESUMEN =
            "rutasResumen"

        private const val CAMPO_ROL =
            "rol"

        private const val CAMPO_FECHA_REGISTRO =
            "fechaRegistro"

        private const val CAMPO_FECHA_ACTUALIZACION =
            "fechaActualizacion"

        private const val ROL_PROPIETARIO =
            "propietario"

        private const val ESTADO_ACTIVO =
            "activo"

        private const val COLOR_PRINCIPAL_PREDETERMINADO =
            "#5B2C83"

        private const val COLOR_SECUNDARIO_PREDETERMINADO =
            "#29123F"
    }
}