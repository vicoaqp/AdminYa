package com.pasajesya.adminya

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.pasajesya.adminya.databinding.ActivityCrearChoferBinding
import java.util.Calendar
import java.util.Locale

class CrearChoferActivity : AppCompatActivity() {
    private lateinit var binding:
            ActivityCrearChoferBinding

    private lateinit var auth:
            FirebaseAuth

    private lateinit var functions:
            FirebaseFunctions

    private var creandoChofer = false

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        binding =
            ActivityCrearChoferBinding.inflate(
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
        configurarCategorias()
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

    private fun configurarCategorias() {

        val categorias = listOf(
            "A-I",
            "A-IIA",
            "A-IIB",
            "A-IIIA",
            "A-IIIB",
            "A-IIIC"
        )

        val adaptador =
            ArrayAdapter(
                this,
                android.R.layout
                    .simple_dropdown_item_1line,
                categorias
            )

        binding.actCategoriaLicencia
            .setAdapter(
                adaptador
            )
    }

    private fun configurarEventos() {

        binding.btnVolver.setOnClickListener {
            finish()
        }

        binding.etVencimientoLicencia
            .setOnClickListener {

                mostrarSelectorFecha()
            }

        binding.tilVencimientoLicencia
            .setEndIconOnClickListener {

                mostrarSelectorFecha()
            }

        binding.btnCrearChofer
            .setOnClickListener {

                validarFormulario()
            }
    }

    private fun mostrarSelectorFecha() {

        val calendario =
            Calendar.getInstance()

        val selector = DatePickerDialog(
            this,
            {
                    _, anio, mes, dia ->

                val fecha = String.format(
                    Locale.ROOT,
                    "%02d/%02d/%04d",
                    dia,
                    mes + 1,
                    anio
                )

                binding
                    .etVencimientoLicencia
                    .setText(fecha)

                binding
                    .tilVencimientoLicencia
                    .error = null
            },
            calendario.get(
                Calendar.YEAR
            ),
            calendario.get(
                Calendar.MONTH
            ),
            calendario.get(
                Calendar.DAY_OF_MONTH
            )
        )

        selector.datePicker.minDate =
            System.currentTimeMillis()

        selector.show()
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

        val licencia =
            texto(
                binding.etLicencia.text
            ).uppercase(Locale.ROOT)

        val categoriaLicencia =
            texto(
                binding.actCategoriaLicencia.text
            ).uppercase(Locale.ROOT)

        val vencimientoLicencia =
            texto(
                binding.etVencimientoLicencia.text
            )

        val correo =
            texto(
                binding.etCorreo.text
            ).lowercase(Locale.ROOT)

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

            dni.length != 8 -> {

                binding.tilDni.error =
                    "El DNI debe tener 8 dígitos."

                binding.etDni.requestFocus()
            }

            celular.length != 9 -> {

                binding.tilCelular.error =
                    "El celular debe tener 9 dígitos."

                binding.etCelular.requestFocus()
            }

            licencia.length < 5 -> {

                binding.tilLicencia.error =
                    "Ingresa el número de licencia."

                binding.etLicencia.requestFocus()
            }

            categoriaLicencia.isBlank() -> {

                binding.tilCategoriaLicencia.error =
                    "Selecciona la categoría."

                binding.actCategoriaLicencia
                    .requestFocus()
            }

            vencimientoLicencia.length != 10 -> {

                binding
                    .tilVencimientoLicencia
                    .error =
                    "Selecciona la fecha de vencimiento."
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

                crearChofer(
                    nombres = nombres,
                    apellidos = apellidos,
                    dni = dni,
                    celular = celular,
                    licencia = licencia,
                    categoriaLicencia =
                        categoriaLicencia,
                    vencimientoLicencia =
                        vencimientoLicencia,
                    correo = correo,
                    password = password,
                    disponible =
                        binding.switchDisponible
                            .isChecked
                )
            }
        }
    }

    private fun crearChofer(
        nombres: String,
        apellidos: String,
        dni: String,
        celular: String,
        licencia: String,
        categoriaLicencia: String,
        vencimientoLicencia: String,
        correo: String,
        password: String,
        disponible: Boolean
    ) {

        if (creandoChofer) {
            return
        }

        creandoChofer = true

        mostrarCargando(
            mostrar = true,
            mensaje = "Creando cuenta del chofer..."
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

                "licencia" to
                        licencia,

                "categoriaLicencia" to
                        categoriaLicencia,

                "vencimientoLicencia" to
                        vencimientoLicencia,

                "correo" to
                        correo,

                "password" to
                        password,

                "disponible" to
                        disponible
            )

        functions
            .getHttpsCallable(
                FUNCION_CREAR_CHOFER
            )
            .call(datos)
            .addOnSuccessListener { resultado ->

                creandoChofer = false
                mostrarCargando(false)

                val respuesta =
                    resultado.data
                            as? Map<*, *>

                val uid =
                    respuesta
                        ?.get("uid")
                        ?.toString()
                        .orEmpty()

                mostrarRegistroCorrecto(
                    nombres = "$nombres $apellidos",
                    correo = correo,
                    password = password,
                    uid = uid
                )
            }
            .addOnFailureListener { error ->

                creandoChofer = false
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
        uid: String
    ) {

        val mensaje = buildString {

            append(
                "El conductor fue registrado correctamente.\n\n"
            )

            append(
                "Conductor:\n$nombres\n\n"
            )

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
                "Entrega estos datos al conductor " +
                        "para que ingrese a ChoferYa."
            )
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(
                "Chofer registrado"
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
                    ?: "No tienes permiso para crear choferes."
            }

            FirebaseFunctionsException.Code.INVALID_ARGUMENT -> {

                errorFunciones.message
                    ?: "Revisa los datos ingresados."
            }

            FirebaseFunctionsException.Code.ALREADY_EXISTS -> {

                "El correo ya está registrado " +
                        "en otra cuenta."
            }

            FirebaseFunctionsException.Code.FAILED_PRECONDITION -> {

                errorFunciones.message
                    ?: "No se cumple una condición necesaria."
            }

            FirebaseFunctionsException.Code.UNAVAILABLE -> {

                "No se pudo conectar con el servidor."
            }

            else -> {

                error.localizedMessage
                    ?: "No se pudo crear el chofer."
            }
        }
    }

    private fun limpiarErrores() {

        binding.tilNombres.error = null
        binding.tilApellidos.error = null
        binding.tilDni.error = null
        binding.tilCelular.error = null
        binding.tilLicencia.error = null

        binding.tilCategoriaLicencia.error =
            null

        binding.tilVencimientoLicencia.error =
            null

        binding.tilCorreo.error = null
        binding.tilPassword.error = null

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

        binding.btnCrearChofer.isEnabled =
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

    companion object {

        private const val REGION_FUNCIONES =
            "us-central1"

        private const val FUNCION_CREAR_CHOFER =
            "crearChofer"
    }
}