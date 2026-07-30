package com.pasajesya.adminya

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.Intent
import android.util.Patterns
import android.view.View
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.pasajesya.adminya.databinding.ActivityRegistroPropietarioBinding
import java.util.Locale

class RegistroPropietarioActivity : AppCompatActivity() {
    private lateinit var binding:
            ActivityRegistroPropietarioBinding

    private lateinit var auth:
            FirebaseAuth

    private lateinit var firestore:
            FirebaseFirestore

    private var registrando = false

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        binding =
            ActivityRegistroPropietarioBinding.inflate(
                layoutInflater
            )

        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        window.statusBarColor =
            getColor(
                R.color.adminya_primary_dark
            )

        configurarEventos()
    }

    private fun configurarEventos() {

        binding.btnVolver.setOnClickListener {
            finish()
        }

        binding.btnCrearCuenta.setOnClickListener {
            validarFormulario()
        }
    }

    private fun validarFormulario() {

        limpiarErrores()

        val nombres =
            texto(binding.etNombres.text)

        val apellidos =
            texto(binding.etApellidos.text)

        val dni =
            texto(binding.etDni.text)

        val celular =
            texto(binding.etCelular.text)

        val correo =
            texto(binding.etCorreo.text)
                .lowercase(Locale.ROOT)

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
                    "Ingresa tus nombres."

                binding.etNombres.requestFocus()
            }

            apellidos.length < 2 -> {

                binding.tilApellidos.error =
                    "Ingresa tus apellidos."

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

                binding.etConfirmarPassword.requestFocus()
            }

            !binding.checkTerminos.isChecked -> {

                Toast.makeText(
                    this,
                    "Confirma que tus datos son correctos.",
                    Toast.LENGTH_LONG
                ).show()
            }

            else -> {

                crearCuenta(
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

    private fun crearCuenta(
        nombres: String,
        apellidos: String,
        dni: String,
        celular: String,
        correo: String,
        password: String
    ) {

        if (registrando) {
            return
        }

        registrando = true
        mostrarCargando(true)

        auth.createUserWithEmailAndPassword(
            correo,
            password
        )
            .addOnSuccessListener { resultado ->

                val usuario =
                    resultado.user

                if (usuario == null) {

                    registrando = false
                    mostrarCargando(false)

                    Toast.makeText(
                        this,
                        "No se pudo crear la cuenta.",
                        Toast.LENGTH_LONG
                    ).show()

                    return@addOnSuccessListener
                }

                guardarPropietario(
                    uid = usuario.uid,
                    nombres = nombres,
                    apellidos = apellidos,
                    dni = dni,
                    celular = celular,
                    correo = correo
                )
            }
            .addOnFailureListener { error ->

                registrando = false
                mostrarCargando(false)

                Toast.makeText(
                    this,
                    error.localizedMessage
                        ?: "No se pudo registrar la cuenta.",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun guardarPropietario(
        uid: String,
        nombres: String,
        apellidos: String,
        dni: String,
        celular: String,
        correo: String
    ) {

        val propietario = hashMapOf<String, Any>(

            "uid" to uid,
            "nombres" to nombres,
            "apellidos" to apellidos,
            "dni" to dni,
            "celular" to celular,
            "correo" to correo,

            "rol" to "propietario",
            "estado" to "activo",

            "empresaId" to "",
            "empresaNombre" to "",

            "registroCompleto" to true,

            "fechaRegistro" to
                    FieldValue.serverTimestamp()
        )

        firestore
            .collection("usuariosgestionpasajes")
            .document(uid)
            .set(propietario)
            .addOnSuccessListener {

                registrando = false
                mostrarCargando(false)

                Toast.makeText(
                    this,
                    "Cuenta creada correctamente.",
                    Toast.LENGTH_LONG
                ).show()

                abrirCrearEmpresa()
            }
            .addOnFailureListener { error ->

                val usuarioCreado =
                    auth.currentUser

                usuarioCreado
                    ?.delete()
                    ?.addOnCompleteListener {

                        auth.signOut()
                    }

                registrando = false
                mostrarCargando(false)

                Toast.makeText(
                    this,
                    "No se pudieron guardar los datos: " +
                            (
                                    error.localizedMessage
                                        ?: "error desconocido"
                                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
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

    private fun texto(
        valor: CharSequence?
    ): String {

        return valor
            ?.toString()
            ?.trim()
            .orEmpty()
    }

    private fun limpiarErrores() {

        binding.tilNombres.error = null
        binding.tilApellidos.error = null
        binding.tilDni.error = null
        binding.tilCelular.error = null
        binding.tilCorreo.error = null
        binding.tilPassword.error = null
        binding.tilConfirmarPassword.error = null
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

        binding.btnCrearCuenta.isEnabled =
            !mostrar

        binding.btnVolver.isEnabled =
            !mostrar
    }
}