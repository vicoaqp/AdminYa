package com.pasajesya.adminya

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.pasajesya.adminya.databinding.ItemSeleccionarVehiculoBinding

class SeleccionarVehiculoAdapter(
    private val choferUidActual: String,
    private val alSeleccionar: (Vehiculo) -> Unit
) : RecyclerView.Adapter<
        SeleccionarVehiculoAdapter.VehiculoViewHolder
        >() {

    private val vehiculos =
        mutableListOf<Vehiculo>()

    private var vehiculoSeleccionadoId = ""

    fun actualizarLista(
        nuevaLista: List<Vehiculo>
    ) {

        vehiculos.clear()
        vehiculos.addAll(nuevaLista)

        notifyDataSetChanged()
    }

    fun establecerSeleccion(
        vehiculoId: String
    ) {

        vehiculoSeleccionadoId =
            vehiculoId

        notifyDataSetChanged()
    }

    fun obtenerSeleccionId(): String {

        return vehiculoSeleccionadoId
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): VehiculoViewHolder {

        val binding =
            ItemSeleccionarVehiculoBinding.inflate(
                LayoutInflater.from(
                    parent.context
                ),
                parent,
                false
            )

        return VehiculoViewHolder(
            binding
        )
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
        ItemSeleccionarVehiculoBinding
    ) : RecyclerView.ViewHolder(
        binding.root
    ) {

        fun mostrar(
            vehiculo: Vehiculo
        ) {

            val seleccionado =
                vehiculo.id ==
                        vehiculoSeleccionadoId

            binding.radioVehiculo.isChecked =
                seleccionado

            binding.root.isChecked =
                seleccionado

            binding.root.strokeWidth =
                if (seleccionado) {
                    2
                } else {
                    1
                }

            binding.root.setStrokeColor(
                ContextCompat.getColor(
                    binding.root.context,
                    if (seleccionado) {
                        R.color.adminya_primary
                    } else {
                        R.color.adminya_border
                    }
                )
            )

            binding.tvPlaca.text =
                vehiculo.placa.ifBlank {
                    "SIN PLACA"
                }

            binding.tvDescripcion.text =
                construirDescripcion(
                    vehiculo
                )

            binding.tvDetalle.text =
                construirDetalle(
                    vehiculo
                )

            mostrarEstado(
                vehiculo
            )

            binding.root.setOnClickListener {

                seleccionarVehiculo(
                    vehiculo
                )
            }

            binding.radioVehiculo
                .setOnClickListener {

                    seleccionarVehiculo(
                        vehiculo
                    )
                }
        }

        private fun seleccionarVehiculo(
            vehiculo: Vehiculo
        ) {

            vehiculoSeleccionadoId =
                vehiculo.id

            notifyDataSetChanged()

            alSeleccionar(
                vehiculo
            )
        }

        private fun construirDescripcion(
            vehiculo: Vehiculo
        ): String {

            val marcaModelo =
                "${vehiculo.marca} ${vehiculo.modelo}"
                    .trim()
                    .ifBlank {
                        "Vehículo sin descripción"
                    }

            return if (
                vehiculo.tipo.isNotBlank()
            ) {

                "$marcaModelo · ${vehiculo.tipo}"

            } else {

                marcaModelo
            }
        }

        private fun construirDetalle(
            vehiculo: Vehiculo
        ): String {

            val capacidad =
                if (vehiculo.capacidad == 1) {
                    "1 pasajero"
                } else {
                    "${vehiculo.capacidad} pasajeros"
                }

            val color =
                vehiculo.color.ifBlank {
                    "Color no registrado"
                }

            return "$capacidad · $color"
        }

        private fun mostrarEstado(
            vehiculo: Vehiculo
        ) {

            val context =
                binding.root.context

            when {

                vehiculo.choferActualUid ==
                        choferUidActual -> {

                    binding.tvEstado.text =
                        "ACTUAL"

                    binding.tvEstado.setTextColor(
                        ContextCompat.getColor(
                            context,
                            R.color.adminya_primary
                        )
                    )
                }

                vehiculo.disponible -> {

                    binding.tvEstado.text =
                        "DISPONIBLE"

                    binding.tvEstado.setTextColor(
                        ContextCompat.getColor(
                            context,
                            R.color.adminya_success
                        )
                    )
                }

                else -> {

                    binding.tvEstado.text =
                        "NO DISPONIBLE"

                    binding.tvEstado.setTextColor(
                        ContextCompat.getColor(
                            context,
                            R.color.adminya_warning
                        )
                    )
                }
            }
        }
    }
}