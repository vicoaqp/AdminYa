package com.pasajesya.adminya
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.pasajesya.adminya.databinding.ItemVehiculoBinding
import java.util.Locale


class VehiculoAdapter(
    private val alEditar: (Vehiculo) -> Unit,
    private val alCambiarEstado: (Vehiculo) -> Unit
) : RecyclerView.Adapter<VehiculoAdapter.VehiculoViewHolder>() {

    private val vehiculos =
        mutableListOf<Vehiculo>()

    fun actualizarLista(
        nuevaLista: List<Vehiculo>
    ) {

        vehiculos.clear()
        vehiculos.addAll(nuevaLista)

        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): VehiculoViewHolder {

        val binding =
            ItemVehiculoBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )

        return VehiculoViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: VehiculoViewHolder,
        position: Int
    ) {

        holder.mostrar(
            vehiculos[position]
        )
    }

    override fun getItemCount(): Int {
        return vehiculos.size
    }

    inner class VehiculoViewHolder(
        private val binding:
        ItemVehiculoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun mostrar(
            vehiculo: Vehiculo
        ) {

            binding.tvPlaca.text =
                vehiculo.placa.ifBlank {
                    "SIN PLACA"
                }

            binding.tvTipoMarca.text =
                construirTipoMarca(vehiculo)

            binding.tvModeloAnio.text =
                construirModeloAnio(vehiculo)

            binding.tvCapacidad.text =
                "Capacidad: ${vehiculo.capacidad} pasajeros"

            binding.tvColor.text =
                "Color: ${vehiculo.color.ifBlank { "No registrado" }}"

            mostrarDisponibilidad(vehiculo)
            mostrarEstado(vehiculo)

            binding.btnEditar.setOnClickListener {
                alEditar(vehiculo)
            }

            binding.btnCambiarEstado.setOnClickListener {
                alCambiarEstado(vehiculo)
            }

            binding.root.setOnClickListener {
                alEditar(vehiculo)
            }
        }

        private fun construirTipoMarca(
            vehiculo: Vehiculo
        ): String {

            val tipo =
                vehiculo.tipo.ifBlank {
                    "Vehículo"
                }

            val marca =
                vehiculo.marca.ifBlank {
                    "Sin marca"
                }

            return "$tipo · $marca"
        }

        private fun construirModeloAnio(
            vehiculo: Vehiculo
        ): String {

            val modelo =
                vehiculo.modelo.ifBlank {
                    "Sin modelo"
                }

            return if (vehiculo.anio > 0) {
                "$modelo · ${vehiculo.anio}"
            } else {
                modelo
            }
        }

        private fun mostrarDisponibilidad(
            vehiculo: Vehiculo
        ) {

            val context =
                binding.root.context

            when {

                vehiculo.estado == "mantenimiento" -> {

                    binding.tvDisponibilidad.text =
                        "En mantenimiento"

                    binding.tvDisponibilidad.setTextColor(
                        ContextCompat.getColor(
                            context,
                            R.color.adminya_warning
                        )
                    )
                }

                vehiculo.estado != "activo" -> {

                    binding.tvDisponibilidad.text =
                        "No disponible"

                    binding.tvDisponibilidad.setTextColor(
                        ContextCompat.getColor(
                            context,
                            R.color.adminya_danger
                        )
                    )
                }

                vehiculo.disponible -> {

                    binding.tvDisponibilidad.text =
                        "Disponible"

                    binding.tvDisponibilidad.setTextColor(
                        ContextCompat.getColor(
                            context,
                            R.color.adminya_success
                        )
                    )
                }

                else -> {

                    binding.tvDisponibilidad.text =
                        "Ocupado / no disponible"

                    binding.tvDisponibilidad.setTextColor(
                        ContextCompat.getColor(
                            context,
                            R.color.adminya_text_secondary
                        )
                    )
                }
            }
        }

        private fun mostrarEstado(
            vehiculo: Vehiculo
        ) {

            val context =
                binding.root.context

            when (vehiculo.estado) {

                "activo" -> {

                    binding.tvEstado.text =
                        "ACTIVO"

                    binding.cardEstado.setCardBackgroundColor(
                        ContextCompat.getColor(
                            context,
                            R.color.adminya_success
                        )
                    )
                }

                "mantenimiento" -> {

                    binding.tvEstado.text =
                        "MANTENIMIENTO"

                    binding.cardEstado.setCardBackgroundColor(
                        ContextCompat.getColor(
                            context,
                            R.color.adminya_warning
                        )
                    )
                }

                "inactivo" -> {

                    binding.tvEstado.text =
                        "INACTIVO"

                    binding.cardEstado.setCardBackgroundColor(
                        ContextCompat.getColor(
                            context,
                            R.color.adminya_danger
                        )
                    )
                }

                else -> {

                    binding.tvEstado.text =
                        vehiculo.estado
                            .replace("_", " ")
                            .uppercase(Locale.ROOT)

                    binding.cardEstado.setCardBackgroundColor(
                        ContextCompat.getColor(
                            context,
                            R.color.adminya_primary
                        )
                    )
                }
            }
        }
    }
}