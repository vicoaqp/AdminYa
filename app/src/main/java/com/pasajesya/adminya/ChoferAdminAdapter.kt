package com.pasajesya.adminya
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.pasajesya.adminya.databinding.ItemChoferAdminBinding
import java.util.Locale

class ChoferAdminAdapter(
    private val alEditar: (ChoferAdmin) -> Unit,
    private val alAsignarVehiculo: (ChoferAdmin) -> Unit,
    private val alCambiarEstado: (ChoferAdmin) -> Unit
) : RecyclerView.Adapter<ChoferAdminAdapter.ChoferViewHolder>() {

    private val choferes =
        mutableListOf<ChoferAdmin>()

    fun actualizarLista(
        nuevaLista: List<ChoferAdmin>
    ) {

        choferes.clear()
        choferes.addAll(nuevaLista)

        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ChoferViewHolder {

        val binding =
            ItemChoferAdminBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )

        return ChoferViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ChoferViewHolder,
        position: Int
    ) {

        holder.mostrar(
            choferes[position]
        )
    }

    override fun getItemCount(): Int {
        return choferes.size
    }

    inner class ChoferViewHolder(
        private val binding:
        ItemChoferAdminBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun mostrar(
            chofer: ChoferAdmin
        ) {

            binding.tvNombre.text =
                chofer.obtenerNombreCompleto()
                    .ifBlank {
                        "Chofer sin nombre"
                    }

            binding.tvDniCelular.text =
                construirDniCelular(
                    chofer
                )

            binding.tvLicencia.text =
                construirLicencia(
                    chofer
                )

            binding.tvCorreo.text =
                "Correo: ${chofer.correo.ifBlank { "No registrado" }}"

            binding.tvVehiculo.text =
                construirVehiculo(
                    chofer
                )

            mostrarEstado(
                chofer
            )

            mostrarDisponibilidad(
                chofer
            )

            binding.btnEditar.setOnClickListener {

                alEditar(
                    chofer
                )
            }

            binding.btnAsignarVehiculo
                .setOnClickListener {

                    alAsignarVehiculo(
                        chofer
                    )
                }

            binding.btnCambiarEstado
                .setOnClickListener {

                    alCambiarEstado(
                        chofer
                    )
                }
        }

        private fun construirDniCelular(
            chofer: ChoferAdmin
        ): String {

            val dni =
                chofer.dni.ifBlank {
                    "Sin DNI"
                }

            val celular =
                chofer.celular.ifBlank {
                    "Sin celular"
                }

            return "DNI: $dni · $celular"
        }

        private fun construirLicencia(
            chofer: ChoferAdmin
        ): String {

            val licencia =
                chofer.licencia.ifBlank {
                    "Sin licencia"
                }

            val categoria =
                chofer.categoriaLicencia
                    .ifBlank {
                        "Sin categoría"
                    }

            return "Licencia: $licencia · $categoria"
        }

        private fun construirVehiculo(
            chofer: ChoferAdmin
        ): String {

            if (chofer.vehiculoId.isBlank()) {

                return "Vehículo: Sin vehículo asignado"
            }

            val placa =
                chofer.vehiculoPlaca.ifBlank {
                    "Sin placa"
                }

            val descripcion =
                chofer.vehiculoDescripcion
                    .ifBlank {
                        "Vehículo asignado"
                    }

            return "Vehículo: $placa · $descripcion"
        }

        private fun mostrarEstado(
            chofer: ChoferAdmin
        ) {

            val context =
                binding.root.context

            when (
                chofer.estado.lowercase(Locale.ROOT)
            ) {

                "activo" -> {

                    binding.tvEstado.text =
                        "ACTIVO"

                    binding.cardEstado
                        .setCardBackgroundColor(
                            ContextCompat.getColor(
                                context,
                                R.color.adminya_success
                            )
                        )
                }

                "inactivo" -> {

                    binding.tvEstado.text =
                        "INACTIVO"

                    binding.cardEstado
                        .setCardBackgroundColor(
                            ContextCompat.getColor(
                                context,
                                R.color.adminya_danger
                            )
                        )
                }

                "suspendido" -> {

                    binding.tvEstado.text =
                        "SUSPENDIDO"

                    binding.cardEstado
                        .setCardBackgroundColor(
                            ContextCompat.getColor(
                                context,
                                R.color.adminya_warning
                            )
                        )
                }

                else -> {

                    binding.tvEstado.text =
                        chofer.estado
                            .replace("_", " ")
                            .uppercase(Locale.ROOT)

                    binding.cardEstado
                        .setCardBackgroundColor(
                            ContextCompat.getColor(
                                context,
                                R.color.adminya_primary
                            )
                        )
                }
            }
        }

        private fun mostrarDisponibilidad(
            chofer: ChoferAdmin
        ) {

            val context =
                binding.root.context

            when {

                chofer.estado != "activo" -> {

                    binding.tvDisponibilidad.text =
                        "No disponible: cuenta inactiva"

                    binding.tvDisponibilidad
                        .setTextColor(
                            ContextCompat.getColor(
                                context,
                                R.color.adminya_danger
                            )
                        )
                }

                chofer.viajeActualId.isNotBlank() -> {

                    binding.tvDisponibilidad.text =
                        "Actualmente tiene un viaje asignado"

                    binding.tvDisponibilidad
                        .setTextColor(
                            ContextCompat.getColor(
                                context,
                                R.color.adminya_warning
                            )
                        )
                }

                chofer.disponible -> {

                    binding.tvDisponibilidad.text =
                        "Disponible para viajes"

                    binding.tvDisponibilidad
                        .setTextColor(
                            ContextCompat.getColor(
                                context,
                                R.color.adminya_success
                            )
                        )
                }

                else -> {

                    binding.tvDisponibilidad.text =
                        "No disponible para viajes"

                    binding.tvDisponibilidad
                        .setTextColor(
                            ContextCompat.getColor(
                                context,
                                R.color.adminya_text_secondary
                            )
                        )
                }
            }
        }
    }
}