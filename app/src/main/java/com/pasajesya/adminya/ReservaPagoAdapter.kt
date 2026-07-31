package com.pasajesya.adminya
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.pasajesya.adminya.databinding.ItemReservaPagoBinding
import java.util.Locale

class ReservaPagoAdapter(

    private val alConfirmarPago:
        (ReservaPagoAdmin) -> Unit,

    private val alCancelar:
        (ReservaPagoAdmin) -> Unit,

    private val alVerDetalle:
        (ReservaPagoAdmin) -> Unit

) : RecyclerView.Adapter<
        ReservaPagoAdapter.ReservaViewHolder
        >() {

    private var lista:
            List<ReservaPagoAdmin> = emptyList()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ReservaViewHolder {

        val binding =
            ItemReservaPagoBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )

        return ReservaViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ReservaViewHolder,
        position: Int
    ) {

        holder.mostrar(lista[position])
    }

    override fun getItemCount(): Int {
        return lista.size
    }

    fun actualizarLista(
        nuevaLista: List<ReservaPagoAdmin>
    ) {

        lista = nuevaLista.toList()
        notifyDataSetChanged()
    }

    inner class ReservaViewHolder(

        private val binding:
        ItemReservaPagoBinding

    ) : RecyclerView.ViewHolder(binding.root) {

        fun mostrar(
            reserva: ReservaPagoAdmin
        ) {

            val contexto =
                binding.root.context

            val nombreMostrar =
                reserva.pasajeroNombre.ifBlank {
                    reserva.usuarioCorreo.ifBlank {
                        "Usuario de PasajesYa"
                    }
                }

            binding.tvNombre.text =
                nombreMostrar

            binding.tvCorreo.text =
                "Correo: ${
                    reserva.usuarioCorreo.ifBlank {
                        "No registrado"
                    }
                }"

            binding.tvDni.text =
                "DNI: ${
                    reserva.pasajeroDni.ifBlank {
                        "No registrado"
                    }
                }"

            binding.tvCelular.text =
                "Celular: ${
                    reserva.pasajeroCelular.ifBlank {
                        "No registrado"
                    }
                }"

            binding.tvAsiento.text =
                "Asiento: ${
                    reserva.asiento.ifBlank {
                        "Sin asignar"
                    }
                }"

            binding.tvPrecio.text =
                String.format(
                    Locale("es", "PE"),
                    "S/ %.2f",
                    reserva.precio
                )

            mostrarEstadoReserva(reserva)
            mostrarEstadoPago(reserva)

            binding.btnDetalle.setOnClickListener {
                alVerDetalle(reserva)
            }

            binding.btnConfirmarPago.setOnClickListener {

                if (
                    reserva.estadoReserva
                        .lowercase(Locale.ROOT) ==
                    ESTADO_PENDIENTE
                ) {
                    alConfirmarPago(reserva)
                }
            }

            binding.btnCancelar.setOnClickListener {
                alCancelar(reserva)
            }

            val abordo =
                reserva.estadoAbordaje
                    .lowercase(Locale.ROOT) ==
                        ESTADO_ABORDO ||
                        reserva.boletoValidado

            val estado =
                reserva.estadoReserva
                    .lowercase(Locale.ROOT)

            binding.btnCancelar.visibility =
                if (
                    estado != ESTADO_CANCELADA &&
                    estado != ESTADO_VENCIDA &&
                    !abordo
                ) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

            binding.btnConfirmarPago.visibility =
                if (
                    estado == ESTADO_CANCELADA ||
                    estado == ESTADO_VENCIDA
                ) {
                    View.GONE
                } else {
                    View.VISIBLE
                }

            when {

                abordo -> {

                    binding.btnConfirmarPago.text =
                        "PASAJERO YA ABORDÓ"

                    binding.btnConfirmarPago.isEnabled =
                        false

                    binding.btnConfirmarPago.alpha =
                        0.6f
                }

                estado == ESTADO_CONFIRMADA -> {

                    binding.btnConfirmarPago.text =
                        "PAGO CONFIRMADO"

                    binding.btnConfirmarPago.isEnabled =
                        false

                    binding.btnConfirmarPago.alpha =
                        0.7f
                }

                estado == ESTADO_PENDIENTE -> {

                    binding.btnConfirmarPago.text =
                        "CONFIRMAR PAGO"

                    binding.btnConfirmarPago.isEnabled =
                        true

                    binding.btnConfirmarPago.alpha =
                        1f
                }

                else -> {

                    binding.btnConfirmarPago.isEnabled =
                        false

                    binding.btnConfirmarPago.alpha =
                        0.6f
                }
            }

            if (abordo) {

                binding.cardEstadoPago
                    .setCardBackgroundColor(
                        ContextCompat.getColor(
                            contexto,
                            R.color.adminya_success
                        )
                    )

                binding.tvEstadoPago.text =
                    "PAGO CONFIRMADO · ABORDÓ"

                binding.tvEstadoPago.setTextColor(
                    ContextCompat.getColor(
                        contexto,
                        android.R.color.white
                    )
                )
            }
        }

        private fun mostrarEstadoReserva(
            reserva: ReservaPagoAdmin
        ) {

            val contexto =
                binding.root.context

            when (
                reserva.estadoReserva
                    .lowercase(Locale.ROOT)
            ) {

                ESTADO_CONFIRMADA -> {

                    binding.tvEstadoReserva.text =
                        "CONFIRMADA"

                    binding.cardEstadoReserva
                        .setCardBackgroundColor(
                            ContextCompat.getColor(
                                contexto,
                                R.color.adminya_success
                            )
                        )
                }

                ESTADO_CANCELADA,
                ESTADO_VENCIDA -> {

                    binding.tvEstadoReserva.text =
                        "CANCELADA"

                    binding.cardEstadoReserva
                        .setCardBackgroundColor(
                            ContextCompat.getColor(
                                contexto,
                                R.color.adminya_danger
                            )
                        )
                }

                else -> {

                    binding.tvEstadoReserva.text =
                        "PENDIENTE"

                    binding.cardEstadoReserva
                        .setCardBackgroundColor(
                            ContextCompat.getColor(
                                contexto,
                                R.color.adminya_warning
                            )
                        )
                }
            }
        }

        private fun mostrarEstadoPago(
            reserva: ReservaPagoAdmin
        ) {

            val contexto =
                binding.root.context

            when (
                reserva.estadoPago
                    .lowercase(Locale.ROOT)
            ) {

                ESTADO_PAGO_CONFIRMADO -> {

                    binding.tvEstadoPago.text =
                        "PAGO CONFIRMADO"

                    binding.cardEstadoPago
                        .setCardBackgroundColor(
                            ContextCompat.getColor(
                                contexto,
                                R.color.adminya_success
                            )
                        )

                    binding.tvEstadoPago.setTextColor(
                        ContextCompat.getColor(
                            contexto,
                            android.R.color.white
                        )
                    )
                }

                ESTADO_PAGO_CANCELADO -> {

                    binding.tvEstadoPago.text =
                        "PAGO CANCELADO"

                    binding.cardEstadoPago
                        .setCardBackgroundColor(
                            ContextCompat.getColor(
                                contexto,
                                R.color.adminya_danger
                            )
                        )

                    binding.tvEstadoPago.setTextColor(
                        ContextCompat.getColor(
                            contexto,
                            android.R.color.white
                        )
                    )
                }

                else -> {

                    binding.tvEstadoPago.text =
                        "PAGO PENDIENTE"

                    binding.cardEstadoPago
                        .setCardBackgroundColor(
                            ContextCompat.getColor(
                                contexto,
                                R.color.adminya_surface
                            )
                        )

                    binding.tvEstadoPago.setTextColor(
                        ContextCompat.getColor(
                            contexto,
                            R.color.adminya_warning
                        )
                    )
                }
            }
        }
    }

    companion object {

        private const val ESTADO_PENDIENTE =
            "pendiente"

        private const val ESTADO_CONFIRMADA =
            "confirmada"

        private const val ESTADO_CANCELADA =
            "cancelada"

        private const val ESTADO_VENCIDA =
            "vencida"

        private const val ESTADO_PAGO_CONFIRMADO =
            "confirmado"

        private const val ESTADO_PAGO_CANCELADO =
            "cancelado"

        private const val ESTADO_ABORDO =
            "abordo"
    }
}