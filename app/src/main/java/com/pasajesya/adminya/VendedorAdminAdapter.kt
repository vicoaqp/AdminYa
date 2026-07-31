package com.pasajesya.adminya
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.pasajesya.adminya.databinding.ItemVendedorAdminBinding
import java.util.Locale


class VendedorAdminAdapter(
    private val alEditar: (VendedorAdmin) -> Unit,
    private val alCambiarEstado: (VendedorAdmin) -> Unit,
    private val alRestablecerPassword: (VendedorAdmin) -> Unit
) : RecyclerView.Adapter<
        VendedorAdminAdapter.VendedorViewHolder
        >() {

    private val vendedores =
        mutableListOf<VendedorAdmin>()

    fun actualizarLista(
        nuevaLista: List<VendedorAdmin>
    ) {

        vendedores.clear()
        vendedores.addAll(nuevaLista)

        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): VendedorViewHolder {

        val binding =
            ItemVendedorAdminBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )

        return VendedorViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: VendedorViewHolder,
        position: Int
    ) {

        holder.mostrar(
            vendedores[position]
        )
    }

    override fun getItemCount(): Int {

        return vendedores.size
    }

    inner class VendedorViewHolder(
        private val binding:
        ItemVendedorAdminBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun mostrar(
            vendedor: VendedorAdmin
        ) {

            binding.tvNombre.text =
                vendedor
                    .obtenerNombreCompleto()
                    .ifBlank {
                        "Vendedor sin nombre"
                    }

            binding.tvDniCelular.text =
                "DNI: ${vendedor.dni.ifBlank { "No registrado" }}" +
                        " · ${vendedor.celular.ifBlank { "Sin celular" }}"

            binding.tvCorreo.text =
                vendedor.correo.ifBlank {
                    "Correo no registrado"
                }

            mostrarEstado(vendedor)

            binding.btnEditar.setOnClickListener {

                alEditar(vendedor)
            }

            binding.btnCambiarEstado
                .setOnClickListener {

                    alCambiarEstado(vendedor)
                }

            binding.btnRestablecerPassword
                .isEnabled =
                vendedor.correo.isNotBlank()

            binding.btnRestablecerPassword
                .setOnClickListener {

                    alRestablecerPassword(vendedor)
                }
        }

        private fun mostrarEstado(
            vendedor: VendedorAdmin
        ) {

            val contexto =
                binding.root.context

            when (
                vendedor.estado.lowercase(Locale.ROOT)
            ) {

                "activo" -> {

                    binding.tvEstado.text =
                        "ACTIVO"

                    binding.cardEstado
                        .setCardBackgroundColor(
                            ContextCompat.getColor(
                                contexto,
                                R.color.adminya_success
                            )
                        )

                    binding.tvAcceso.text =
                        "Puede ingresar al panel de ventas"

                    binding.tvAcceso.setTextColor(
                        ContextCompat.getColor(
                            contexto,
                            R.color.adminya_success
                        )
                    )
                }

                "suspendido" -> {

                    binding.tvEstado.text =
                        "SUSPENDIDO"

                    binding.cardEstado
                        .setCardBackgroundColor(
                            ContextCompat.getColor(
                                contexto,
                                R.color.adminya_warning
                            )
                        )

                    binding.tvAcceso.text =
                        "Acceso suspendido temporalmente"

                    binding.tvAcceso.setTextColor(
                        ContextCompat.getColor(
                            contexto,
                            R.color.adminya_warning
                        )
                    )
                }

                else -> {

                    binding.tvEstado.text =
                        "INACTIVO"

                    binding.cardEstado
                        .setCardBackgroundColor(
                            ContextCompat.getColor(
                                contexto,
                                R.color.adminya_danger
                            )
                        )

                    binding.tvAcceso.text =
                        "No puede ingresar al panel de ventas"

                    binding.tvAcceso.setTextColor(
                        ContextCompat.getColor(
                            contexto,
                            R.color.adminya_danger
                        )
                    )
                }
            }
        }
    }
}