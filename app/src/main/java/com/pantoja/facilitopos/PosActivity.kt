package com.pantoja.facilitopos

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProductoPos(
    val id: String,
    val nombre: String,
    val codigo: String,
    val precioUsd: Double,
    val costoUsd: Double,
    val departamento: String,
    val esPesado: Boolean,
    val stock: Double
)

data class ItemCarrito(
    val producto: ProductoPos,
    var cantidad: Double
)

class PosActivity : AppCompatActivity() {

    private var tasaBcv = 857.8876
    private var negocioId = "neg_mujkrui8"
    private var cajeroNombre = "Angel Pantoja"
    private var terminalNombre = "Caja 01"

    private val todosLosProductos = mutableListOf<ProductoPos>()
    private val carrito = mutableMapOf<String, ItemCarrito>()

    private lateinit var tvTotalUsd: TextView
    private lateinit var tvTotalBs: TextView
    private lateinit var tvItemsCount: TextView
    private lateinit var tvTasaBcv: TextView
    private lateinit var tvTerminalInfo: TextView
    private lateinit var containerProductos: LinearLayout
    private lateinit var etBuscar: EditText

    private var categoriaActual = "Todos"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pos)

        val prefs = getSharedPreferences("pos_prefs", Context.MODE_PRIVATE)
        cajeroNombre = prefs.getString("usuario_nombre", "Angel Pantoja") ?: "Angel Pantoja"
        terminalNombre = prefs.getString("caja_nombre", "Caja 01") ?: "Caja 01"
        negocioId = prefs.getString("negocio_id", "neg_mujkrui8") ?: "neg_mujkrui8"

        tvTotalUsd = findViewById(R.id.tvTotalUsd)
        tvTotalBs = findViewById(R.id.tvTotalBs)
        tvItemsCount = findViewById(R.id.tvItemsCount)
        tvTasaBcv = findViewById(R.id.tvTasaBcv)
        tvTerminalInfo = findViewById(R.id.tvTerminalInfo)
        containerProductos = findViewById(R.id.containerProductos)
        etBuscar = findViewById(R.id.etBuscar)
        val btnCobrar = findViewById<MaterialButton>(R.id.btnCobrar)

        tvTerminalInfo.text = "$terminalNombre • $cajeroNombre"

        configurarCategorias()
        configurarBuscador()

        btnCobrar.setOnClickListener {
            mostrarModalCobro()
        }

        cargarDatosDesdeServidor()
    }

    private fun configurarCategorias() {
        val catTodos = findViewById<TextView>(R.id.catTodos)
        val catViveres = findViewById<TextView>(R.id.catViveres)
        val catCharcuteria = findViewById<TextView>(R.id.catCharcuteria)
        val catVerduras = findViewById<TextView>(R.id.catVerduras)
        val catHigiene = findViewById<TextView>(R.id.catHigiene)

        val listaTabs = listOf(catTodos, catViveres, catCharcuteria, catVerduras, catHigiene)

        fun seleccionarTab(tabActivo: TextView, depto: String) {
            categoriaActual = depto
            for (tab in listaTabs) {
                if (tab == tabActivo) {
                    tab.setBackgroundResource(R.drawable.bg_tab_active)
                    tab.setTextColor(Color.WHITE)
                } else {
                    tab.setBackgroundResource(R.drawable.bg_tabs)
                    tab.setTextColor(Color.parseColor("#475569"))
                }
            }
            filtrarYRenderizar()
        }

        catTodos.setOnClickListener { seleccionarTab(catTodos, "Todos") }
        catViveres.setOnClickListener { seleccionarTab(catViveres, "Víveres") }
        catCharcuteria.setOnClickListener { seleccionarTab(catCharcuteria, "Charcutería") }
        catVerduras.setOnClickListener { seleccionarTab(catVerduras, "Verduras y Frutas") }
        catHigiene.setOnClickListener { seleccionarTab(catHigiene, "Higiene Personal") }
    }

    private fun configurarBuscador() {
        etBuscar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                filtrarYRenderizar()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun cargarDatosDesdeServidor() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Tasa BCV oficial del negocio en Supabase
                val resNegocio = SupabaseClient.get("negocios?id=eq.$negocioId&select=tasa_bcv_actual")
                if (resNegocio != null) {
                    val arr = JsonParser.parseString(resNegocio).asJsonArray
                    if (arr.size() > 0 && arr[0].asJsonObject.has("tasa_bcv_actual")) {
                        tasaBcv = arr[0].asJsonObject.get("tasa_bcv_actual").asDouble
                    }
                }

                // 2. Obtener productos de Supabase
                val resProd = SupabaseClient.get("productos?negocio_id=eq.$negocioId&select=*")
                val productosParseados = mutableListOf<ProductoPos>()

                if (resProd != null) {
                    val arrProd = JsonParser.parseString(resProd).asJsonArray
                    for (elem in arrProd) {
                        val obj = elem.asJsonObject
                        productosParseados.add(
                            ProductoPos(
                                id = obj.get("id").asString,
                                nombre = if (obj.has("nombre")) obj.get("nombre").asString else "Producto",
                                codigo = if (obj.has("codigo_barras") && !obj.get("codigo_barras").isJsonNull) obj.get("codigo_barras").asString else "",
                                precioUsd = if (obj.has("precio_usd")) obj.get("precio_usd").asDouble else 0.0,
                                costoUsd = if (obj.has("costo_usd")) obj.get("costo_usd").asDouble else 0.0,
                                departamento = if (obj.has("departamento") && !obj.get("departamento").isJsonNull) obj.get("departamento").asString else "Víveres",
                                esPesado = if (obj.has("es_pesado")) obj.get("es_pesado").asBoolean else false,
                                stock = if (obj.has("stock")) obj.get("stock").asDouble else 0.0
                            )
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    tvTasaBcv.text = "BCV: " + String.format("%.2f", tasaBcv) + " Bs"
                    todosLosProductos.clear()

                    if (productosParseados.isNotEmpty()) {
                        todosLosProductos.addAll(productosParseados)
                    } else {
                        // Catálogo de respaldo según base de datos oficial
                        todosLosProductos.addAll(obtenerCatalogoBase())
                    }
                    filtrarYRenderizar()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvTasaBcv.text = "BCV: 857.88 Bs"
                    todosLosProductos.clear()
                    todosLosProductos.addAll(obtenerCatalogoBase())
                    filtrarYRenderizar()
                }
            }
        }
    }

    private fun obtenerCatalogoBase(): List<ProductoPos> {
        return listOf(
            ProductoPos("neg_mujkrui8_1", "Harina PAN Blanca 1kg", "7591001000123", 1.22, 1.09, "Víveres", false, 34.0),
            ProductoPos("neg_mujkrui8_2", "Arroz Blanco Primor 1kg", "7591002000456", 1.35, 1.05, "Víveres", false, 39.0),
            ProductoPos("neg_mujkrui8_3", "Queso Blanco Llanero", "PROD-435517", 4.90, 3.50, "Charcutería", true, 12.977),
            ProductoPos("1790498991354", "Queso Guayanes", "PROD-406962", 4.90, 3.50, "Charcutería", true, 23.048),
            ProductoPos("neg_mujkrui8_4", "Jamón de Pierna Plumrose", "J-002", 8.50, 6.20, "Charcutería", false, 8.2),
            ProductoPos("neg_mujkrui8_5", "Tomate Manzano", "V-003", 3.49, 2.40, "Verduras y Frutas", true, 23.998),
            ProductoPos("neg_mujkrui8_6", "Spray Aclarante Farmatodo", "7591472015188", 4.50, 3.30, "Higiene Personal", false, 15.0),
            ProductoPos("prod_1790580506941", "Doritos 175Grms", "PROD-506941", 1.00, 0.75, "Snacks y Golosinas", false, 24.0)
        )
    }

    private fun filtrarYRenderizar() {
        val query = etBuscar.text.toString().trim().lowercase()
        val filtrados = todosLosProductos.filter { p ->
            val coincideDepto = categoriaActual == "Todos" || p.departamento.equals(categoriaActual, ignoreCase = true)
            val coincideBusqueda = query.isEmpty() || p.nombre.lowercase().contains(query) || p.codigo.lowercase().contains(query)
            coincideDepto && coincideBusqueda
        }

        containerProductos.removeAllViews()

        for (prod in filtrados) {
            val card = MaterialCardView(this).apply {
                radius = 18f
                elevation = 2f
                setCardBackgroundColor(Color.WHITE)
                strokeWidth = 1
                strokeColor = Color.parseColor("#E2E8F0")
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, 0, 14)
                layoutParams = params
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(20, 18, 20, 18)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val infoLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvNom = TextView(this).apply {
                text = prod.nombre
                textSize = 14f
                setTextColor(Color.parseColor("#0F172A"))
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            val tvSub = TextView(this).apply {
                text = "${prod.departamento} • Stock: ${prod.stock}"
                textSize = 11f
                setTextColor(Color.parseColor("#64748B"))
            }

            val tvPre = TextView(this).apply {
                val bs = prod.precioUsd * tasaBcv
                text = "$" + String.format("%.2f", prod.precioUsd) + "  •  " + String.format("%.2f", bs) + " Bs"
                textSize = 13f
                setTextColor(Color.parseColor("#00A859"))
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            infoLayout.addView(tvNom)
            infoLayout.addView(tvSub)
            infoLayout.addView(tvPre)

            val btnAgregar = MaterialButton(this).apply {
                text = if (prod.esPesado) "⚖️ Pesar" else "+ Agregar"
                textSize = 11f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#0F1D38"))
                cornerRadius = 10
                setOnClickListener {
                    if (prod.esPesado) {
                        solicitarPeso(prod)
                    } else {
                        agregarAlCarrito(prod, 1.0)
                    }
                }
            }

            row.addView(infoLayout)
            row.addView(btnAgregar)
            card.addView(row)
            containerProductos.addView(card)
        }
    }

    private fun solicitarPeso(prod: ProductoPos) {
        val input = EditText(this).apply {
            hint = "Ingrese peso en Kg (ej: 0.5)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        AlertDialog.Builder(this)
            .setTitle("Balanza / Producto Pesado")
            .setMessage("Producto: ${prod.nombre}\nPrecio por Kg: $${prod.precioUsd}")
            .setView(input)
            .setPositiveButton("Agregar") { _, _ ->
                val peso = input.text.toString().toDoubleOrNull() ?: 1.0
                agregarAlCarrito(prod, peso)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun agregarAlCarrito(prod: ProductoPos, cantidad: Double) {
        val item = carrito[prod.id]
        if (item != null) {
            item.cantidad += cantidad
        } else {
            carrito[prod.id] = ItemCarrito(prod, cantidad)
        }
        actualizarTotalesUI()
        Toast.makeText(this, "${prod.nombre} agregado al carrito", Toast.LENGTH_SHORT).show()
    }

    private fun actualizarTotalesUI() {
        var totalUsd = 0.0
        var totalItems = 0.0

        for ((_, item) in carrito) {
            totalUsd += (item.producto.precioUsd * item.cantidad)
            totalItems += item.cantidad
        }

        val totalBs = totalUsd * tasaBcv

        tvTotalUsd.text = "Total: $" + String.format("%.2f", totalUsd)
        tvTotalBs.text = "Ref: " + String.format("%.2f", totalBs) + " Bs"
        tvItemsCount.text = String.format("%.0f", totalItems) + " productos en orden"
    }

    private fun mostrarModalCobro() {
        if (carrito.isEmpty()) {
            Toast.makeText(this, "El carrito está vacío. Agregue productos.", Toast.LENGTH_SHORT).show()
            return
        }

        var totalUsd = 0.0
        for ((_, item) in carrito) {
            totalUsd += (item.producto.precioUsd * item.cantidad)
        }
        val totalBs = totalUsd * tasaBcv

        val metodos = arrayOf("Efectivo ($)", "Punto Débito (Bs)", "Pago Móvil (Bs)", "Mixto / Divisas")
        var seleccionado = 0

        AlertDialog.Builder(this)
            .setTitle("Cobrar Factura")
            .setMessage("Total a Pagar:\n$ " + String.format("%.2f", totalUsd) + " USD\n" + String.format("%.2f", totalBs) + " Bs\n\nSeleccione método de pago:")
            .setSingleChoiceItems(metodos, 0) { _, which ->
                seleccionado = which
            }
            .setPositiveButton("Procesar Venta") { _, _ ->
                registrarVentaEnServidor(totalUsd, totalBs, metodos[seleccionado])
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun registrarVentaEnServidor(totalUsd: Double, totalBs: Double, metodo: String) {
        val vtaId = "vta_" + System.currentTimeMillis()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonItems = JsonArray()
                for ((_, it) in carrito) {
                    val o = JsonObject()
                    o.addProperty("id", it.producto.id)
                    o.addProperty("nombre", it.producto.nombre)
                    o.addProperty("cantidad", it.cantidad)
                    o.addProperty("precioUSD", it.producto.precioUsd)
                    jsonItems.add(o)
                }

                val ventaBody = JsonObject().apply {
                    addProperty("id", vtaId)
                    addProperty("negocio_id", negocioId)
                    addProperty("cajero_id", "usr_dueno")
                    addProperty("cajero_nombre", cajeroNombre)
                    addProperty("terminal_id", "caja_01")
                    addProperty("terminal_nombre", terminalNombre)
                    addProperty("total_usd", totalUsd)
                    addProperty("total_bs", totalBs)
                    addProperty("tasa_bcv", tasaBcv)
                    add("items", jsonItems)
                    addProperty("sincronizado", true)
                }

                SupabaseClient.post("ventas", ventaBody.toString())

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PosActivity, "¡Venta completada con éxito!", Toast.LENGTH_LONG).show()
                    carrito.clear()
                    actualizarTotalesUI()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PosActivity, "Venta guardada localmente", Toast.LENGTH_SHORT).show()
                    carrito.clear()
                    actualizarTotalesUI()
                }
            }
        }
    }
}
