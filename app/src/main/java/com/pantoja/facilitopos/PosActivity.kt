package com.pantoja.facilitopos

import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PosActivity : AppCompatActivity() {

    private var tasaBcv = 857.88
    private var totalUsd = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pos)

        val tvTotalUsd = findViewById<TextView>(R.id.tvTotalUsd)
        val tvTotalBs = findViewById<TextView>(R.id.tvTotalBs)
        val btnCobrar = findViewById<MaterialButton>(R.id.btnCobrar)
        val containerProductos = findViewById<LinearLayout>(R.id.containerProductos)

        btnCobrar.setOnClickListener {
            if (totalUsd > 0) {
                Toast.makeText(this, "Venta procesada con éxito ($" + String.format("%.2f", totalUsd) + ")", Toast.LENGTH_LONG).show()
                totalUsd = 0.0
                tvTotalUsd.text = "Total: $0.00"
                tvTotalBs.text = "0.00 Bs"
            } else {
                Toast.makeText(this, "Seleccione al menos un producto", Toast.LENGTH_SHORT).show()
            }
        }

        // Cargar productos desde Supabase
        CoroutineScope(Dispatchers.IO).launch {
            val res = SupabaseClient.get("productos?select=*&limit=20")
            withContext(Dispatchers.Main) {
                if (res != null) {
                    val items = JsonParser.parseString(res).asJsonArray
                    containerProductos.removeAllViews()

                    for (item in items) {
                        val obj = item.asJsonObject
                        val nombre = if (obj.has("nombre")) obj.get("nombre").asString else "Producto"
                        val precio = if (obj.has("precio_usd")) obj.get("precio_usd").asDouble else 0.0

                        // Crear tarjeta nativa para cada producto
                        val card = MaterialCardView(this@PosActivity).apply {
                            radius = 16f
                            elevation = 2f
                            setCardBackgroundColor(Color.WHITE)
                            val params = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            params.setMargins(0, 0, 0, 14)
                            layoutParams = params
                        }

                        val row = LinearLayout(this@PosActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            setPadding(20, 20, 20, 20)
                        }

                        val infoLayout = LinearLayout(this@PosActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        }

                        val tvNom = TextView(this@PosActivity).apply {
                            text = nombre
                            textSize = 14f
                            setTextColor(Color.parseColor("#1F2937"))
                        }

                        val tvPre = TextView(this@PosActivity).apply {
                            text = "$" + String.format("%.2f", precio) + " | " + String.format("%.2f", precio * tasaBcv) + " Bs"
                            textSize = 12f
                            setTextColor(Color.parseColor("#059669"))
                        }

                        infoLayout.addView(tvNom)
                        infoLayout.addView(tvPre)

                        val btnAdd = MaterialButton(this@PosActivity).apply {
                            text = "+ Agregar"
                            textSize = 11f
                            setBackgroundColor(Color.parseColor("#0F1D38"))
                            setOnClickListener {
                                totalUsd += precio
                                tvTotalUsd.text = "Total: $" + String.format("%.2f", totalUsd)
                                tvTotalBs.text = String.format("%.2f", totalUsd * tasaBcv) + " Bs"
                            }
                        }

                        row.addView(infoLayout)
                        row.addView(btnAdd)
                        card.addView(row)
                        containerProductos.addView(card)
                    }
                }
            }
        }
    }
}
