package com.pasajesya.adminya

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.pasajesya.adminya.databinding.ActivityMiEmpresaBinding
import java.text.Normalizer
import java.util.Locale

class MiEmpresaActivity : AppCompatActivity() {
    private lateinit var binding:
            ActivityMiEmpresaBinding

    private lateinit var auth:
            FirebaseAuth

    private lateinit var firestore:
            FirebaseFirestore

    private var listenerPerfil:
            ListenerRegistration? = null

    private var empresaIdActual = ""

    private var guardando = false
    private var navegando = false

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        binding =
            ActivityMiEmpresaBinding.inflate(
                layoutInflater
            )

        setContentView(binding.root)

        auth =
            FirebaseAuth.getInstance()

        firestore =
            FirebaseFirestore.getInstance()

        configurarPantalla()
        configurarEventos()
        prepararPantalla()
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

    private fun configurarEventos() {

        binding.btnVolver.setOnClickListener {
            finish()
        }

        binding.btnGuardarCambios
            .setOnClickListener {

                validarFormulario()
            }
    }

    private fun prepararPantalla() {

        binding.tvNombreEmpresa.text =
            "Cargando empresa..."

        binding.tvNombreCorto.text =
            "Nombre corto"

        binding.tvEstadoEmpresa.text =
            "CARGANDO"

        binding.tvEmpresaId.text =
            "ID de empresa"

        binding.etNombreEmpresa.setText("")
        binding.etNombreCorto.setText("")
        binding.etRuc.setText("")
        binding.etTelefono.setText("")
        binding.etDireccion.setText("")
        binding.etCiudad.setText("")
        binding.etDescripcion.setText("")

        binding.switchVisibleUsuarios.isChecked =
            true

        mostrarCargando(
            mostrar = true,
            mensaje = "Cargando empresa..."
        )
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
            mensaje = "Validando propietario..."
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
                        "No se pudo cargar tu cuenta: " +
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

        val empresaId =
            leerTexto(
                documento,
                CAMPO_EMPRESA_ID
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

            empresaIdActual != empresaId -> {

                empresaIdActual = empresaId

                cargarEmpresa(
                    empresaId
                )
            }

            else -> {

                mostrarCargando(false)
            }
        }
    }

    private fun cargarEmpresa(
        empresaId: String
    ) {

        val usuarioActual =
            auth.currentUser

        if (usuarioActual == null) {

            abrirLogin()
            return
        }

        mostrarCargando(
            mostrar = true,
            mensaje = "Cargando datos de la empresa..."
        )

        firestore
            .collection(
                COLECCION_EMPRESAS
            )
            .document(
                empresaId
            )
            .get()
            .addOnSuccessListener { documento ->

                mostrarCargando(false)

                if (!documento.exists()) {

                    Toast.makeText(
                        this,
                        "No se encontró la empresa registrada.",
                        Toast.LENGTH_LONG
                    ).show()

                    return@addOnSuccessListener
                }

                val propietarioUid =
                    leerTexto(
                        documento,
                        CAMPO_PROPIETARIO_UID
                    )

                if (
                    propietarioUid.isNotBlank() &&
                    propietarioUid != usuarioActual.uid
                ) {

                    cerrarSesionInvalida(
                        "Esta empresa pertenece a otro propietario."
                    )

                    return@addOnSuccessListener
                }

                mostrarDatosEmpresa(
                    documento
                )
            }
            .addOnFailureListener { error ->

                mostrarCargando(false)

                Toast.makeText(
                    this,
                    "No se pudieron cargar los datos: " +
                            (
                                    error.localizedMessage
                                        ?: "error desconocido"
                                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun mostrarDatosEmpresa(
        documento: DocumentSnapshot
    ) {

        val nombre =
            leerTexto(
                documento,
                CAMPO_NOMBRE
            )

        val nombreCorto =
            leerTexto(
                documento,
                CAMPO_NOMBRE_CORTO
            )

        val ruc =
            leerTexto(
                documento,
                CAMPO_RUC
            )

        val telefono =
            leerTexto(
                documento,
                CAMPO_TELEFONO
            )

        val direccion =
            leerTexto(
                documento,
                CAMPO_DIRECCION
            )

        val ciudad =
            leerTexto(
                documento,
                CAMPO_CIUDAD
            )

        val descripcion =
            leerTexto(
                documento,
                CAMPO_DESCRIPCION
            )

        val estado =
            leerTexto(
                documento,
                CAMPO_ESTADO
            ).lowercase(Locale.ROOT)

        val visibleUsuarios =
            leerBooleano(
                documento,
                CAMPO_VISIBLE_USUARIOS,
                true
            )

        binding.tvNombreEmpresa.text =
            nombre.ifBlank {
                "Empresa sin nombre"
            }

        binding.tvNombreCorto.text =
            nombreCorto.ifBlank {
                "Sin nombre corto"
            }

        binding.etNombreEmpresa.setText(
            nombre
        )

        binding.etNombreCorto.setText(
            nombreCorto
        )

        binding.etRuc.setText(
            ruc
        )

        binding.etTelefono.setText(
            telefono
        )

        binding.etDireccion.setText(
            direccion
        )

        binding.etCiudad.setText(
            ciudad
        )

        binding.etDescripcion.setText(
            descripcion
        )

        binding.switchVisibleUsuarios.isChecked =
            visibleUsuarios

        binding.tvEmpresaId.text =
            "ID: $empresaIdActual"

        mostrarEstadoEmpresa(
            estado
        )
    }

    private fun mostrarEstadoEmpresa(
        estado: String
    ) {

        when (estado) {

            ESTADO_ACTIVO -> {

                binding.tvEstadoEmpresa.text =
                    "ACTIVA"

                binding.cardEstadoEmpresa
                    .setCardBackgroundColor(
                        getColor(
                            R.color.adminya_success
                        )
                    )
            }

            ESTADO_INACTIVO -> {

                binding.tvEstadoEmpresa.text =
                    "INACTIVA"

                binding.cardEstadoEmpresa
                    .setCardBackgroundColor(
                        getColor(
                            R.color.adminya_warning
                        )
                    )
            }

            ESTADO_SUSPENDIDO -> {

                binding.tvEstadoEmpresa.text =
                    "SUSPENDIDA"

                binding.cardEstadoEmpresa
                    .setCardBackgroundColor(
                        getColor(
                            R.color.adminya_danger
                        )
                    )
            }

            else -> {

                binding.tvEstadoEmpresa.text =
                    estado
                        .ifBlank {
                            "SIN ESTADO"
                        }
                        .replace(
                            "_",
                            " "
                        )
                        .uppercase(Locale.ROOT)

                binding.cardEstadoEmpresa
                    .setCardBackgroundColor(
                        getColor(
                            R.color.adminya_primary
                        )
                    )
            }
        }
    }

    private fun validarFormulario() {

        limpiarErrores()

        val nombreEmpresa =
            texto(
                binding.etNombreEmpresa.text
            )

        val nombreCorto =
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

        when {

            nombreEmpresa.length < 3 -> {

                binding.tilNombreEmpresa.error =
                    "Ingresa el nombre de la empresa."

                binding.etNombreEmpresa.requestFocus()
            }

            nombreEmpresa.length > 60 -> {

                binding.tilNombreEmpresa.error =
                    "El nombre debe tener máximo 60 caracteres."

                binding.etNombreEmpresa.requestFocus()
            }

            nombreCorto.isNotBlank() &&
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
                    "Ingresa una dirección válida."

                binding.etDireccion.requestFocus()
            }

            ciudad.length < 3 -> {

                binding.tilCiudad.error =
                    "Ingresa la ciudad o localidad."

                binding.etCiudad.requestFocus()
            }

            descripcion.length > 250 -> {

                binding.tilDescripcion.error =
                    "La descripción debe tener máximo 250 caracteres."

                binding.etDescripcion.requestFocus()
            }

            else -> {

                guardarCambios(
                    nombreEmpresa =
                        nombreEmpresa,

                    nombreCorto =
                        nombreCorto.ifBlank {

                            generarNombreCorto(
                                nombreEmpresa
                            )
                        },

                    ruc =
                        ruc,

                    telefono =
                        telefono,

                    direccion =
                        direccion,

                    ciudad =
                        ciudad,

                    descripcion =
                        descripcion,

                    visibleUsuarios =
                        binding
                            .switchVisibleUsuarios
                            .isChecked
                )
            }
        }
    }

    private fun guardarCambios(
        nombreEmpresa: String,
        nombreCorto: String,
        ruc: String,
        telefono: String,
        direccion: String,
        ciudad: String,
        descripcion: String,
        visibleUsuarios: Boolean
    ) {

        if (guardando) {
            return
        }

        val usuarioActual =
            auth.currentUser

        if (usuarioActual == null) {

            abrirLogin()
            return
        }

        if (empresaIdActual.isBlank()) {

            Toast.makeText(
                this,
                "No se pudo identificar la empresa.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        guardando = true

        mostrarCargando(
            mostrar = true,
            mensaje = "Guardando cambios..."
        )

        val referenciaEmpresa =
            firestore
                .collection(
                    COLECCION_EMPRESAS
                )
                .document(
                    empresaIdActual
                )

        val referenciaPropietario =
            firestore
                .collection(
                    COLECCION_USUARIOS_GESTION
                )
                .document(
                    usuarioActual.uid
                )

        val cambiosEmpresa =
            hashMapOf<String, Any>(

                CAMPO_NOMBRE to
                        nombreEmpresa,

                CAMPO_NOMBRE_CORTO to
                        nombreCorto.uppercase(
                            Locale.ROOT
                        ),

                CAMPO_NOMBRE_BUSQUEDA to
                        normalizarBusqueda(
                            nombreEmpresa
                        ),

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

                CAMPO_VISIBLE_USUARIOS to
                        visibleUsuarios,

                CAMPO_PROPIETARIO_UID to
                        usuarioActual.uid,

                CAMPO_EMPRESA_ID to
                        empresaIdActual,

                CAMPO_FECHA_ACTUALIZACION to
                        FieldValue.serverTimestamp()
            )

        val cambiosPropietario =
            hashMapOf<String, Any>(

                CAMPO_EMPRESA_NOMBRE to
                        nombreEmpresa,

                CAMPO_FECHA_ACTUALIZACION to
                        FieldValue.serverTimestamp()
            )

        /*
         * Ambas actualizaciones se guardan juntas:
         *
         * 1. empresaspasajes/{empresaId}
         * 2. usuariosgestionpasajes/{uidPropietario}
         */
        firestore
            .runBatch { lote ->

                lote.update(
                    referenciaEmpresa,
                    cambiosEmpresa
                )

                lote.update(
                    referenciaPropietario,
                    cambiosPropietario
                )
            }
            .addOnSuccessListener {

                guardando = false
                mostrarCargando(false)

                binding.tvNombreEmpresa.text =
                    nombreEmpresa

                binding.tvNombreCorto.text =
                    nombreCorto.uppercase(
                        Locale.ROOT
                    )

                Toast.makeText(
                    this,
                    "Datos de la empresa actualizados.",
                    Toast.LENGTH_LONG
                ).show()
            }
            .addOnFailureListener { error ->

                guardando = false
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

                "No tienes permiso para modificar esta empresa."
            }

            mensaje.contains(
                "NOT_FOUND",
                ignoreCase = true
            ) -> {

                "No se encontró el documento de la empresa."
            }

            mensaje.contains(
                "offline",
                ignoreCase = true
            ) -> {

                "No hay conexión con Internet."
            }

            mensaje.isNotBlank() -> {

                "No se pudieron guardar los cambios: $mensaje"
            }

            else -> {

                "No se pudieron guardar los cambios."
            }
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

        return palabras
            .take(3)
            .joinToString("") { palabra ->

                palabra
                    .first()
                    .uppercaseChar()
                    .toString()
            }
            .ifBlank {

                nombreEmpresa
                    .take(3)
                    .uppercase(Locale.ROOT)
            }
    }

    private fun normalizarBusqueda(
        valor: String
    ): String {

        val sinTildes =
            Normalizer.normalize(
                valor,
                Normalizer.Form.NFD
            )
                .replace(
                    Regex(
                        "\\p{InCombiningDiacriticalMarks}+"
                    ),
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
        campo: String,
        valorPredeterminado: Boolean
    ): Boolean {

        return when (
            val valor = documento.get(campo)
        ) {

            is Boolean -> {
                valor
            }

            is String -> {

                valor
                    .trim()
                    .equals(
                        "true",
                        ignoreCase = true
                    )
            }

            is Number -> {
                valor.toInt() != 0
            }

            else -> {
                valorPredeterminado
            }
        }
    }

    private fun limpiarErrores() {

        binding.tilNombreEmpresa.error =
            null

        binding.tilNombreCorto.error =
            null

        binding.tilRuc.error =
            null

        binding.tilTelefono.error =
            null

        binding.tilDireccion.error =
            null

        binding.tilCiudad.error =
            null

        binding.tilDescripcion.error =
            null
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

        binding.btnGuardarCambios.isEnabled =
            !mostrar

        binding.btnVolver.isEnabled =
            !mostrar
    }

    private fun cerrarSesionInvalida(
        mensaje: String
    ) {

        listenerPerfil?.remove()
        listenerPerfil = null

        auth.signOut()

        Toast.makeText(
            this,
            mensaje,
            Toast.LENGTH_LONG
        ).show()

        abrirLogin()
    }

    private fun abrirCrearEmpresa() {

        navegarA(
            CrearEmpresaActivity::class.java
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

    override fun onStop() {

        listenerPerfil?.remove()
        listenerPerfil = null

        super.onStop()
    }

    companion object {

        private const val COLECCION_USUARIOS_GESTION =
            "usuariosgestionpasajes"

        private const val COLECCION_EMPRESAS =
            "empresaspasajes"

        private const val CAMPO_ROL =
            "rol"

        private const val CAMPO_ESTADO =
            "estado"

        private const val CAMPO_EMPRESA_ID =
            "empresaId"

        private const val CAMPO_EMPRESA_NOMBRE =
            "empresaNombre"

        private const val CAMPO_PROPIETARIO_UID =
            "propietarioUid"

        private const val CAMPO_NOMBRE =
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

        private const val CAMPO_VISIBLE_USUARIOS =
            "visibleUsuarios"

        private const val CAMPO_FECHA_ACTUALIZACION =
            "fechaActualizacion"

        private const val ROL_PROPIETARIO =
            "propietario"

        private const val ESTADO_ACTIVO =
            "activo"

        private const val ESTADO_INACTIVO =
            "inactivo"

        private const val ESTADO_SUSPENDIDO =
            "suspendido"
    }
}