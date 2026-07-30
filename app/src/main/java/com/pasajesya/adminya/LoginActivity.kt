package com.pasajesya.adminya

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.Intent
import android.util.Patterns
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.pasajesya.adminya.databinding.ActivityLoginBinding
import java.util.Locale

class LoginActivity : AppCompatActivity() {
    private lateinit var binding:
            ActivityLoginBinding

    private lateinit var auth:
            FirebaseAuth

    private lateinit var firestore:
            FirebaseFirestore

    private var navegando = false

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        binding =
            ActivityLoginBinding.inflate(
                layoutInflater
            )

        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        configurarPantalla()
        configurarEventos()
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

        binding.btnIngresar.setOnClickListener {
            iniciarSesion()
        }

        binding.btnRegistrarse.setOnClickListener {
            abrirRegistroPropietario()
        }

        binding.tvRecuperarPassword.setOnClickListener {
            recuperarPassword()
        }

        binding.etPassword.setOnEditorActionListener {
                _, actionId, _ ->

            if (actionId == EditorInfo.IME_ACTION_DONE) {
                iniciarSesion()
                true
            } else {
                false
            }
        }
    }

    private fun iniciarSesion() {

        limpiarErrores()

        val correo = binding.etCorreo
            .text
            ?.toString()
            .orEmpty()
            .trim()
            .lowercase(Locale.ROOT)

        val password = binding.etPassword
            .text
            ?.toString()
            .orEmpty()

        when {

            correo.isBlank() -> {

                binding.tilCorreo.error =
                    "Ingresa tu correo electrónico."

                binding.etCorreo.requestFocus()
            }

            !Patterns.EMAIL_ADDRESS
                .matcher(correo)
                .matches() -> {

                binding.tilCorreo.error =
                    "Ingresa un correo válido."

                binding.etCorreo.requestFocus()
            }

            password.isBlank() -> {

                binding.tilPassword.error =
                    "Ingresa tu contraseña."

                binding.etPassword.requestFocus()
            }

            password.length < 6 -> {

                binding.tilPassword.error =
                    "La contraseña debe tener al menos 6 caracteres."

                binding.etPassword.requestFocus()
            }

            else -> {

                autenticarUsuario(
                    correo = correo,
                    password = password
                )
            }
        }
    }

    private fun autenticarUsuario(
        correo: String,
        password: String
    ) {

        mostrarCargando(
            mostrar = true,
            mensaje = "Ingresando..."
        )

        auth.signInWithEmailAndPassword(
            correo,
            password
        )
            .addOnSuccessListener { resultado ->

                val usuario =
                    resultado.user

                if (usuario == null) {

                    mostrarCargando(false)

                    Toast.makeText(
                        this,
                        "No se pudo identificar al usuario.",
                        Toast.LENGTH_LONG
                    ).show()

                    auth.signOut()
                    return@addOnSuccessListener
                }

                validarCuenta(
                    usuario.uid
                )
            }
            .addOnFailureListener { error ->

                mostrarCargando(false)

                Toast.makeText(
                    this,
                    obtenerMensajeErrorLogin(error),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun validarCuenta(
        uid: String
    ) {

        mostrarCargando(
            mostrar = true,
            mensaje = "Validando cuenta..."
        )

        firestore
            .collection(COLECCION_USUARIOS_GESTION)
            .document(uid)
            .get()
            .addOnSuccessListener { documento ->

                if (!documento.exists()) {

                    mostrarCargando(false)
                    auth.signOut()

                    Toast.makeText(
                        this,
                        "La cuenta existe, pero no está registrada en AdminYa.",
                        Toast.LENGTH_LONG
                    ).show()

                    return@addOnSuccessListener
                }

                procesarUsuario(
                    documento
                )
            }
            .addOnFailureListener { error ->

                mostrarCargando(false)
                auth.signOut()

                Toast.makeText(
                    this,
                    "No se pudo consultar la cuenta: " +
                            (
                                    error.localizedMessage
                                        ?: "error desconocido"
                                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun procesarUsuario(
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

        val registroCompleto =
            leerBooleano(
                documento,
                CAMPO_REGISTRO_COMPLETO,
                valorPredeterminado = true
            )

        when {

            estado != ESTADO_ACTIVO -> {

                mostrarCargando(false)
                auth.signOut()

                Toast.makeText(
                    this,
                    "Tu cuenta está inactiva. Comunícate con el propietario.",
                    Toast.LENGTH_LONG
                ).show()
            }

            !registroCompleto -> {

                mostrarCargando(false)
                auth.signOut()

                Toast.makeText(
                    this,
                    "Tu registro todavía no está completo.",
                    Toast.LENGTH_LONG
                ).show()
            }

            rol == ROL_PROPIETARIO &&
                    empresaId.isBlank() -> {

                mostrarCargando(
                    mostrar = true,
                    mensaje = "Completa tu empresa..."
                )

                abrirCrearEmpresa()
            }

            rol == ROL_PROPIETARIO -> {

                mostrarCargando(
                    mostrar = true,
                    mensaje = "Abriendo administración..."
                )

                abrirMainPropietario()
            }

            rol == ROL_VENDEDOR &&
                    empresaId.isNotBlank() -> {

                mostrarCargando(
                    mostrar = true,
                    mensaje = "Abriendo panel de vendedor..."
                )

               abrirMainVendedor()
            }

            rol == ROL_VENDEDOR -> {

                mostrarCargando(false)
                auth.signOut()

                Toast.makeText(
                    this,
                    "Tu cuenta de vendedor no tiene una empresa asignada.",
                    Toast.LENGTH_LONG
                ).show()
            }

            else -> {

                mostrarCargando(false)
                auth.signOut()

                Toast.makeText(
                    this,
                    "Esta cuenta no tiene autorización para ingresar a AdminYa.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun recuperarPassword() {

        limpiarErrores()

        val correo = binding.etCorreo
            .text
            ?.toString()
            .orEmpty()
            .trim()
            .lowercase(Locale.ROOT)

        when {

            correo.isBlank() -> {

                binding.tilCorreo.error =
                    "Primero escribe tu correo electrónico."

                binding.etCorreo.requestFocus()
            }

            !Patterns.EMAIL_ADDRESS
                .matcher(correo)
                .matches() -> {

                binding.tilCorreo.error =
                    "Escribe un correo válido."

                binding.etCorreo.requestFocus()
            }

            else -> {

                mostrarCargando(
                    mostrar = true,
                    mensaje = "Enviando enlace..."
                )

                auth.sendPasswordResetEmail(
                    correo
                )
                    .addOnSuccessListener {

                        mostrarCargando(false)

                        Toast.makeText(
                            this,
                            "Se envió un enlace de recuperación a $correo",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    .addOnFailureListener { error ->

                        mostrarCargando(false)

                        Toast.makeText(
                            this,
                            "No se pudo enviar el enlace: " +
                                    (
                                            error.localizedMessage
                                                ?: "error desconocido"
                                            ),
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
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
        valorPredeterminado: Boolean
    ): Boolean {

        return when (
            val valor = documento.get(campo)
        ) {

            is Boolean -> valor

            is String -> {
                valor.equals(
                    "true",
                    ignoreCase = true
                )
            }

            is Number -> {
                valor.toInt() != 0
            }

            else -> valorPredeterminado
        }
    }

    private fun obtenerMensajeErrorLogin(
        error: Exception
    ): String {

        return when (error) {

            is FirebaseAuthInvalidUserException -> {
                "No existe una cuenta registrada con este correo."
            }

            is FirebaseAuthInvalidCredentialsException -> {
                "El correo o la contraseña son incorrectos."
            }

            is FirebaseNetworkException -> {
                "No hay conexión con Internet."
            }

            else -> {
                error.localizedMessage
                    ?: "No se pudo iniciar sesión."
            }
        }
    }

    private fun limpiarErrores() {

        binding.tilCorreo.error = null
        binding.tilPassword.error = null
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

        binding.btnIngresar.isEnabled =
            !mostrar

        binding.btnRegistrarse.isEnabled =
            !mostrar

        binding.tvRecuperarPassword.isEnabled =
            !mostrar
    }

    private fun abrirRegistroPropietario() {

        val intent = Intent(
            this,
            RegistroPropietarioActivity::class.java
        )

        startActivity(intent)
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

    override fun onResume() {
        super.onResume()

        navegando = false
        mostrarCargando(false)
    }

    companion object {

        private const val COLECCION_USUARIOS_GESTION =
            "usuariosgestionpasajes"

        private const val CAMPO_ROL =
            "rol"

        private const val CAMPO_ESTADO =
            "estado"

        private const val CAMPO_EMPRESA_ID =
            "empresaId"

        private const val CAMPO_REGISTRO_COMPLETO =
            "registroCompleto"

        private const val ROL_PROPIETARIO =
            "propietario"

        private const val ROL_VENDEDOR =
            "vendedor"

        private const val ESTADO_ACTIVO =
            "activo"
    }
}