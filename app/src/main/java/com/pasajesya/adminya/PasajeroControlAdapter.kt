package com.pasajesya.adminya

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.pasajesya.adminya.databinding.ItemPasajeroControlBinding
import java.util.Locale

class PasajeroControlAdapter(

    private val alCambiarEmbarque:
        (PasajeroControl) -> Unit

) : RecyclerView.Adapter<
        PasajeroControlAdapter.PasajeroViewHolder
        >() {

    private var listaCompleta:
            List<PasajeroControl> = emptyList()

    private var listaVisible:
            List<PasajeroControl> = emptyList()

    private var accionesHabilitadas = true

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): PasajeroViewHolder {

        val binding =
            ItemPasajeroControlBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )

        return PasajeroViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: PasajeroViewHolder,
        position: Int
    ) {

        holder.mostrar(
            listaVisible[position]
        )
    }

    override fun getItemCount(): Int {
        return listaVisible.size
    }

    fun actualizarLista(
        pasajeros: List<PasajeroControl>
    ) {

        listaCompleta =
            pasajeros.toList()

        listaVisible =
            pasajeros.toList()

        notifyDataSetChanged()
    }

    fun establecerAccionesHabilitadas(
        habilitadas: Boolean
    ) {

        accionesHabilitadas =
            habilitadas

        notifyDataSetChanged()
    }

    fun filtrar(
        consulta: String
    ) {

        val texto =
            consulta
                .trim()
                .lowercase(Locale.ROOT)

        listaVisible =
            if (texto.isBlank()) {

                listaCompleta

            } else {

                listaCompleta.filter { pasajero ->

                    pasajero.pasajeroNombre
                        .lowercase(Locale.ROOT)
                        .contains(texto) ||

                            pasajero.pasajeroDni
                                .contains(texto) ||

                            pasajero.pasajeroCelular
                                .contains(texto) ||

                            pasajero.numeroAsiento
                                .lowercase(Locale.ROOT)
                                .contains(texto)
                }
            }

        notifyDataSetChanged()
    }

    inner class PasajeroViewHolder(

        private val binding:
        ItemPasajeroControlBinding

    ) : RecyclerView.ViewHolder(binding.root) {

        fun mostrar(
            pasajero: PasajeroControl
        ) {

            binding.tvNombre.text =
                pasajero.pasajeroNombre.ifBlank {
                    "Pasajero sin nombre"
                }

            binding.tvDni.text =
                "DNI: ${
                    pasajero.pasajeroDni.ifBlank {
                        "No registrado"
                    }
                }"

            binding.tvCelular.text =
                "Celular: ${
                    pasajero.pasajeroCelular.ifBlank {
                        "No registrado"
                    }
                }"

            binding.tvAsiento.text =
                "Asiento: ${
                    pasajero.numeroAsiento.ifBlank {
                        "Sin asignar"
                    }
                }"

            binding.tvCantidad.text =
                if (pasajero.cantidadPasajeros == 1) {

                    "Cantidad: 1 pasajero"

                } else {

                    "Cantidad: ${pasajero.cantidadPasajeros} pasajeros"
                }

            mostrarEstado(
                pasajero
            )

            binding.btnCambiarEstado.isEnabled =
                accionesHabilitadas

            binding.btnCambiarEstado.alpha =
                if (accionesHabilitadas) {
                    1f
                } else {
                    0.5f
                }

            binding.btnCambiarEstado.setOnClickListener {

                if (accionesHabilitadas) {
                    alCambiarEmbarque(pasajero)
                }
            }
        }

        private fun mostrarEstado(
            pasajero: PasajeroControl
        ) {

            val contexto =
                binding.root.context

            if (pasajero.embarcado) {

                binding.tvEstado.text =
                    "EMBARCADO"

                binding.cardEstado
                    .setCardBackgroundColor(
                        ContextCompat.getColor(
                            contexto,
                            R.color.adminya_success
                        )
                    )

                binding.btnCambiarEstado.text =
                    "DESMARCAR EMBARQUE"

                binding.btnCambiarEstado
                    .setBackgroundColor(
                        ContextCompat.getColor(
                            contexto,
                            R.color.adminya_danger
                        )
                    )

            } else {

                binding.tvEstado.text =
                    "PENDIENTE"

                binding.cardEstado
                    .setCardBackgroundColor(
                        ContextCompat.getColor(
                            contexto,
                            R.color.adminya_warning
                        )
                    )

                binding.btnCambiarEstado.text =
                    "MARCAR COMO EMBARCADO"

                binding.btnCambiarEstado
                    .setBackgroundColor(
                        ContextCompat.getColor(
                            contexto,
                            R.color.adminya_success
                        )
                    )
            }
        }
    }
}