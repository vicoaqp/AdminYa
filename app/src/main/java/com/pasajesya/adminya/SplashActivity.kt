package com.pasajesya.adminya

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.Intent
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.pasajesya.adminya.databinding.ActivitySplashBinding
import java.util.Locale

class SplashActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplashBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private var navegando = false

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        binding = ActivitySplashBinding.inflate(
            layoutInflater
        )

        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        configurarPantalla()

        binding.tvEstadoSplash.text =
            "Iniciando AdminYa..."

        binding.root.postDelayed(
            {
                verificarSesion()
            },
            TIEMPO_SPLASH
        )
    }

    private fun configurarPantalla() {

        window.statusBarColor =
            getColor(
                R.color.adminya_primary_dark
            )

        window.navigationBarColor =
            getColor(
                R.color.adminya_primary_dark
            )
    }

    private fun verificarSesion() {

        val usuarioActual =
            auth.currentUser

        /*
         * No existe una sesión iniciada.
         */
        if (usuarioActual == null) {

            binding.tvEstadoSplash.text =
                "Abriendo inicio de sesión..."

            abrirLogin()
            return
        }

        binding.tvEstadoSplash.text =
            "Validando cuenta..."

        validarUsuarioGestion(
            usuarioActual.uid
        )
    }

    private fun validarUsuarioGestion(
        uid: String
    ) {

        firestore
            .collection(COLECCION_USUARIOS_GESTION)
            .document(uid)
            .get()
            .addOnSuccessListener { documento ->

                if (!documento.exists()) {

                    cerrarSesionInvalida(
                        "Tu cuenta no está registrada en AdminYa."
                    )

                    return@addOnSuccessListener
                }

                val rol = documento
                    .getString(CAMPO_ROL)
                    .orEmpty()
                    .trim()
                    .lowercase(Locale.ROOT)

                val estado = documento
                    .getString(CAMPO_ESTADO)
                    .orEmpty()
                    .trim()
                    .lowercase(Locale.ROOT)

                val empresaId = documento
                    .getString(CAMPO_EMPRESA_ID)
                    .orEmpty()
                    .trim()

                val registroCompleto =
                    documento.getBoolean(
                        CAMPO_REGISTRO_COMPLETO
                    ) ?: true

                when {

                    estado != ESTADO_ACTIVO -> {

                        cerrarSesionInvalida(
                            "Tu cuenta de AdminYa no está activa."
                        )
                    }

                    !registroCompleto -> {

                        cerrarSesionInvalida(
                            "Tu registro todavía no está completo."
                        )
                    }

                    rol == ROL_PROPIETARIO -> {

                        validarPropietario(
                            empresaId = empresaId
                        )
                    }

                    rol == ROL_VENDEDOR -> {

                        validarVendedor(
                            empresaId = empresaId
                        )
                    }

                    else -> {

                        cerrarSesionInvalida(
                            "Tu cuenta no tiene un rol válido."
                        )
                    }
                }
            }
            .addOnFailureListener { error ->

                binding.tvEstadoSplash.text =
                    "No se pudo validar la cuenta"

                Toast.makeText(
                    this,
                    "Error al consultar tu cuenta: " +
                            (
                                    error.localizedMessage
                                        ?: "error desconocido"
                                    ),
                    Toast.LENGTH_LONG
                ).show()

                abrirLogin()
            }
    }

    private fun validarPropietario(
        empresaId: String
    ) {

        /*
         * El propietario se registró, pero todavía
         * no creó su empresa.
         */
        if (empresaId.isBlank()) {

            binding.tvEstadoSplash.text =
                "Completa el registro de tu empresa..."

            abrirCrearEmpresa()
            return
        }

        /*
         * Verificamos que la empresa exista.
         */
        binding.tvEstadoSplash.text =
            "Cargando tu empresa..."

        firestore
            .collection(COLECCION_EMPRESAS)
            .document(empresaId)
            .get()
            .addOnSuccessListener { empresa ->

                if (!empresa.exists()) {

                    Toast.makeText(
                        this,
                        "No se encontró la empresa asignada. " +
                                "Debes registrarla nuevamente.",
                        Toast.LENGTH_LONG
                    ).show()

                    abrirCrearEmpresa()
                    return@addOnSuccessListener
                }

                val estadoEmpresa = empresa
                    .getString(CAMPO_ESTADO)
                    .orEmpty()
                    .trim()
                    .lowercase(Locale.ROOT)

                val propietarioUid = empresa
                    .getString(CAMPO_PROPIETARIO_UID)
                    .orEmpty()
                    .trim()

                val usuarioActual =
                    auth.currentUser

                if (usuarioActual == null) {
                    abrirLogin()
                    return@addOnSuccessListener
                }

                when {

                    estadoEmpresa != ESTADO_ACTIVO -> {

                        cerrarSesionInvalida(
                            "La empresa asignada no está activa."
                        )
                    }

                    propietarioUid.isNotBlank() &&
                            propietarioUid !=
                            usuarioActual.uid -> {

                        cerrarSesionInvalida(
                            "La empresa está registrada " +
                                    "con otro propietario."
                        )
                    }

                    else -> {

                        binding.tvEstadoSplash.text =
                            "Bienvenido a AdminYa"

                        abrirMainPropietario()
                    }
                }
            }
            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    "No se pudo cargar la empresa: " +
                            (
                                    error.localizedMessage
                                        ?: "error desconocido"
                                    ),
                    Toast.LENGTH_LONG
                ).show()

                abrirLogin()
            }
    }

    private fun validarVendedor(
        empresaId: String
    ) {

        if (empresaId.isBlank()) {

            cerrarSesionInvalida(
                "Tu cuenta de vendedor no tiene " +
                        "una empresa asignada."
            )

            return
        }

        binding.tvEstadoSplash.text =
            "Cargando panel de ventas..."

        firestore
            .collection(COLECCION_EMPRESAS)
            .document(empresaId)
            .get()
            .addOnSuccessListener { empresa ->

                if (!empresa.exists()) {

                    cerrarSesionInvalida(
                        "La empresa asignada no existe."
                    )

                    return@addOnSuccessListener
                }

                val estadoEmpresa = empresa
                    .getString(CAMPO_ESTADO)
                    .orEmpty()
                    .trim()
                    .lowercase(Locale.ROOT)

                if (estadoEmpresa != ESTADO_ACTIVO) {

                    cerrarSesionInvalida(
                        "La empresa asignada no está activa."
                    )

                    return@addOnSuccessListener
                }

                abrirMainVendedor()
            }
            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    "No se pudo cargar la empresa: " +
                            (
                                    error.localizedMessage
                                        ?: "error desconocido"
                                    ),
                    Toast.LENGTH_LONG
                ).show()

                abrirLogin()
            }
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

    private fun abrirLogin() {

        navegarA(
            LoginActivity::class.java
        )
    }

    private fun abrirCrearEmpresa() {

        navegarA(
            CrearEmpresaActivity::class.java
        )
    }

    private fun abrirMainPropietario() {

        navegarA(
            MainPropietarioActivity::class.java
        )
    }

    private fun abrirMainVendedor() {

        navegarA(
            MainVendedorActivity::class.java
        )
    }

    private fun navegarA(
        actividad: Class<*>
    ) {

        if (navegando) {
            return
        }

        navegando = true

        val intent = Intent(
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

    companion object {

        private const val TIEMPO_SPLASH =
            1600L

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

        private const val CAMPO_REGISTRO_COMPLETO =
            "registroCompleto"

        private const val CAMPO_PROPIETARIO_UID =
            "propietarioUid"

        private const val ROL_PROPIETARIO =
            "propietario"

        private const val ROL_VENDEDOR =
            "vendedor"

        private const val ESTADO_ACTIVO =
            "activo"
    }
}