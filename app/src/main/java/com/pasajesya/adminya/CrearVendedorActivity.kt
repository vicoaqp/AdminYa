package com.pasajesya.adminya

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.pasajesya.adminya.databinding.ActivityCrearVendedorBinding
import java.util.Locale

class CrearVendedorActivity : AppCompatActivity() {
    private lateinit var binding:
            ActivityCrearVendedorBinding

    private lateinit var auth:
            FirebaseAuth

    private lateinit var functions:
            FirebaseFunctions

    private var creandoVendedor = false

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        binding =
            ActivityCrearVendedorBinding.inflate(
                layoutInflater
            )

        setContentView(binding.root)

        auth =
            FirebaseAuth.getInstance()

        functions =
            FirebaseFunctions.getInstance(
                REGION_FUNCIONES
            )

        if (auth.currentUser == null) {

            abrirLogin()
            return
        }

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

        binding.btnVolver.setOnClickListener {
            finish()
        }

        binding.btnCrearVendedor
            .setOnClickListener {

                validarFormulario()
            }
    }

    private fun validarFormulario() {

        limpiarErrores()

        val nombres =
            texto(
                binding.etNombres.text
            )

        val apellidos =
            texto(
                binding.etApellidos.text
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
            ).lowercase(Locale.ROOT)

        /*
         * La contraseña no se recorta.
         * Los espacios podrían formar parte de ella.
         */
        val password =
            binding.etPassword.text
                ?.toString()
                .orEmpty()

        val confirmarPassword =
            binding.etConfirmarPassword.text
                ?.toString()
                .orEmpty()

        when {

            nombres.length < 2 -> {

                binding.tilNombres.error =
                    "Ingresa los nombres."

                binding.etNombres.requestFocus()
            }

            apellidos.length < 2 -> {

                binding.tilApellidos.error =
                    "Ingresa los apellidos."

                binding.etApellidos.requestFocus()
            }

            dni.length != 8 ||
                    !dni.all { caracter ->
                        caracter.isDigit()
                    } -> {

                binding.tilDni.error =
                    "El DNI debe tener 8 dígitos."

                binding.etDni.requestFocus()
            }

            celular.length != 9 ||
                    !celular.all { caracter ->
                        caracter.isDigit()
                    } -> {

                binding.tilCelular.error =
                    "El celular debe tener 9 dígitos."

                binding.etCelular.requestFocus()
            }

            !Patterns.EMAIL_ADDRESS
                .matcher(correo)
                .matches() -> {

                binding.tilCorreo.error =
                    "Ingresa un correo válido."

                binding.etCorreo.requestFocus()
            }

            password.length < 6 -> {

                binding.tilPassword.error =
                    "La contraseña debe tener al menos 6 caracteres."

                binding.etPassword.requestFocus()
            }

            password != confirmarPassword -> {

                binding.tilConfirmarPassword.error =
                    "Las contraseñas no coinciden."

                binding.etConfirmarPassword
                    .requestFocus()
            }

            !binding.checkConfirmacion
                .isChecked -> {

                Toast.makeText(
                    this,
                    "Confirma que los datos son correctos.",
                    Toast.LENGTH_LONG
                ).show()
            }

            else -> {

                crearVendedor(
                    nombres = nombres,
                    apellidos = apellidos,
                    dni = dni,
                    celular = celular,
                    correo = correo,
                    password = password
                )
            }
        }
    }

    private fun crearVendedor(
        nombres: String,
        apellidos: String,
        dni: String,
        celular: String,
        correo: String,
        password: String
    ) {

        if (creandoVendedor) {
            return
        }

        if (auth.currentUser == null) {

            abrirLogin()
            return
        }

        creandoVendedor = true

        mostrarCargando(
            mostrar = true,
            mensaje = "Creando cuenta del vendedor..."
        )

        val datos =
            hashMapOf<String, Any>(

                "nombres" to
                        nombres,

                "apellidos" to
                        apellidos,

                "dni" to
                        dni,

                "celular" to
                        celular,

                "correo" to
                        correo,

                "password" to
                        password
            )

        functions
            .getHttpsCallable(
                FUNCION_CREAR_VENDEDOR
            )
            .call(datos)
            .addOnSuccessListener { resultado ->

                creandoVendedor = false
                mostrarCargando(false)

                val respuesta =
                    resultado.data
                            as? Map<*, *>

                val uid =
                    respuesta
                        ?.get("uid")
                        ?.toString()
                        .orEmpty()

                val empresaNombre =
                    respuesta
                        ?.get("empresaNombre")
                        ?.toString()
                        .orEmpty()

                mostrarRegistroCorrecto(
                    nombres =
                        "$nombres $apellidos".trim(),

                    correo =
                        correo,

                    password =
                        password,

                    uid =
                        uid,

                    empresaNombre =
                        empresaNombre
                )
            }
            .addOnFailureListener { error ->

                creandoVendedor = false
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

    private fun mostrarRegistroCorrecto(
        nombres: String,
        correo: String,
        password: String,
        uid: String,
        empresaNombre: String
    ) {

        val mensaje =
            buildString {

                append(
                    "La cuenta fue creada correctamente.\n\n"
                )

                append(
                    "Vendedor:\n$nombres\n\n"
                )

                if (empresaNombre.isNotBlank()) {

                    append(
                        "Empresa:\n$empresaNombre\n\n"
                    )
                }

                append(
                    "Correo de acceso:\n$correo\n\n"
                )

                append(
                    "Contraseña temporal:\n$password\n\n"
                )

                if (uid.isNotBlank()) {

                    append(
                        "UID:\n$uid\n\n"
                    )
                }

                append(
                    "Entrega el correo y la contraseña " +
                            "al vendedor para que ingrese a AdminYa."
                )
            }

        MaterialAlertDialogBuilder(this)
            .setTitle(
                "Vendedor registrado"
            )
            .setMessage(
                mensaje
            )
            .setCancelable(false)
            .setPositiveButton(
                "ENTENDIDO"
            ) { _, _ ->

                finish()
            }
            .show()
    }

    private fun obtenerMensajeError(
        error: Exception
    ): String {

        val errorFunciones =
            error as? FirebaseFunctionsException

        return when (
            errorFunciones?.code
        ) {

            FirebaseFunctionsException.Code.UNAUTHENTICATED -> {

                "Tu sesión ha terminado. " +
                        "Vuelve a iniciar sesión."
            }

            FirebaseFunctionsException.Code.PERMISSION_DENIED -> {

                errorFunciones.message
                    ?: "No tienes permiso para crear vendedores."
            }

            FirebaseFunctionsException.Code.INVALID_ARGUMENT -> {

                errorFunciones.message
                    ?: "Revisa los datos ingresados."
            }

            FirebaseFunctionsException.Code.ALREADY_EXISTS -> {

                errorFunciones.message
                    ?: "El correo o DNI ya está registrado."
            }

            FirebaseFunctionsException.Code.FAILED_PRECONDITION -> {

                errorFunciones.message
                    ?: "No se cumple una condición necesaria."
            }

            FirebaseFunctionsException.Code.UNAVAILABLE -> {

                "No se pudo conectar con el servidor."
            }

            FirebaseFunctionsException.Code.INTERNAL -> {

                errorFunciones.message
                    ?: "El servidor no pudo crear la cuenta."
            }

            else -> {

                error.localizedMessage
                    ?: "No se pudo crear el vendedor."
            }
        }
    }

    private fun limpiarErrores() {

        binding.tilNombres.error =
            null

        binding.tilApellidos.error =
            null

        binding.tilDni.error =
            null

        binding.tilCelular.error =
            null

        binding.tilCorreo.error =
            null

        binding.tilPassword.error =
            null

        binding.tilConfirmarPassword.error =
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

        binding.btnCrearVendedor.isEnabled =
            !mostrar

        binding.btnVolver.isEnabled =
            !mostrar
    }

    private fun texto(
        valor: CharSequence?
    ): String {

        return valor
            ?.toString()
            ?.trim()
            .orEmpty()
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

    companion object {

        private const val REGION_FUNCIONES =
            "us-central1"

        private const val FUNCION_CREAR_VENDEDOR =
            "crearVendedor"
    }
}