package com.pantoja.facilitopos

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
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

        val prefs = getSharedPreferences("pos_prefs", Context.MODE_PRIVATE)
        val usuarioNombre = prefs.getString("usuario_nombre", "Angel Pantoja") ?: "Angel Pantoja"
        val cajaNombre = prefs.getString("caja_nombre", "Caja 01") ?: "Caja 01"

        val tvTotalUsd = findViewById<TextView>(R.id.tvTotalUsd)
        val tvTotalBs = findViewById<TextView>(R.id.tvTotalBs)
        val tvTasaBcv = findViewById<TextView>(R.id.tvTasaBcv)
        val btnCobrar = findViewById<MaterialButton>(R.id.btnCobrar)
        val containerProductos = findViewById<LinearLayout>(R.id.containerProductos)

        tvTasaBcv.text = "BCV: " + String.format("%.2f", tasaBcv) + " Bs | " + cajaNombre

        btnCobrar.setOnClickListener {
            if (totalUsd > 0) {
                Toast.makeText(this, "Venta Exitosa: $" + String.format("%.2f", totalUsd) + " cobrados por " + usuarioNombre, Toast.LENGTH_LONG).show()
                totalUsd = 0.0
                tvTotalUsd.text = "Total: $0.00"
                tvTotalBs.text = "0.00 Bs"
            } else {
                Toast.makeText(this, "Seleccione productos para la venta", Toast.LENGTH_SHORT).show()
            }
        }

        // Cargar productos en hilo secundario
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resNegocio = SupabaseClient.get("negocios?id=eq.neg_mujkrui8&select=tasa_bcv_actual")
                if (resNegocio != null) {
                    val arrNeg = JsonParser.parseString(resNegocio).asJsonArray
                    if (arrNeg.size() > 0 && arrNeg[0].asJsonObject.has("tasa_bcv_actual")) {
                        tasaBcv = arrNeg[0].asJsonObject.get("tasa_bcv_actual").asDouble
                    }
                }

                val res = SupabaseClient.get("productos?select=*&limit=30")
                withContext(Dispatchers.Main) {
                    tvTasaBcv.text = "BCV: " + String.format("%.2f", tasaBcv) + " Bs"

                    if (res != null) {
                        val items = JsonParser.parseString(res).asJsonArray
                        containerProductos.removeAllViews()

                        for (item in items) {
                            val obj = item.asJsonObject
                            val nombre = if (obj.has("nombre") && !obj.get("nombre").isJsonNull) obj.get("nombre").asString else "Producto"
                            val precio = if (obj.has("precio_usd") && !obj.get("precio_usd").isJsonNull) obj.get("precio_usd").asDouble else 0.0

                            val card = MaterialCardView(this@PosActivity).apply {
                                radius = 20f
                                elevation = 3f
                                setCardBackgroundColor(Color.WHITE)
                                val lp = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                )
                                lp.setMargins(0, 0, 0, 16)
                                layoutParams = lp
                            }

                            val row = LinearLayout(this@PosActivity).apply {
                                orientation = LinearLayout.HORIZONTAL
                                setPadding(24, 20, 24, 20)
                            }

                            val infoLayout = LinearLayout(this@PosActivity).apply {
                                orientation = LinearLayout.VERTICAL
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            }

                            val tvNom = TextView(this@PosActivity).apply {
                                text = nombre
                                textSize = 15f
                                setTextColor(Color.parseColor("#0F172A"))
                                setTypeface(null, android.graphics.Typeface.BOLD)
                            }

                            val tvPre = TextView(this@PosActivity).apply {
                                text = "$" + String.format("%.2f", precio) + "  •  " + String.format("%.2f", precio * tasaBcv) + " Bs"
                                textSize = 13f
                                setTextColor(Color.parseColor("#059669"))
                                setTypeface(null, android.graphics.Typeface.BOLD)
                            }

                            infoLayout.addView(tvNom)
                            infoLayout.addView(tvPre)

                            val btnAdd = Button(this@PosActivity).apply {
                                text = "+ Agregar"
                                textSize = 11f
                                setTextColor(Color.WHITE)
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
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PosActivity, "Catálogo offline", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
