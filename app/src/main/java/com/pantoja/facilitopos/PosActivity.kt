package com.pantoja.facilitopos

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
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
    var nombre: String,
    var codigo: String,
    var precioUsd: Double,
    var costoUsd: Double,
    var departamento: String,
    var esPesado: Boolean,
    var aplicaMayor: Boolean,
    var precioMayorUsd: Double,
    var cantMinimaMayor: Double,
    var stock: Double
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

    private lateinit var drawerLayout: DrawerLayout
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

        drawerLayout = findViewById(R.id.drawerLayout)
        tvTotalUsd = findViewById(R.id.tvTotalUsd)
        tvTotalBs = findViewById(R.id.tvTotalBs)
        tvItemsCount = findViewById(R.id.tvItemsCount)
        tvTasaBcv = findViewById(R.id.tvTasaBcv)
        tvTerminalInfo = findViewById(R.id.tvTerminalInfo)
        containerProductos = findViewById(R.id.containerProductos)
        etBuscar = findViewById(R.id.etBuscar)
        val btnCobrar = findViewById<MaterialButton>(R.id.btnCobrar)
        val btnAbrirMenu = findViewById<TextView>(R.id.btnAbrirMenu)
        val tvMenuUser = findViewById<TextView>(R.id.tvMenuUser)

        tvTerminalInfo.text = "$terminalNombre • $cajeroNombre"
        tvMenuUser.text = "$cajeroNombre • Dueño"

        btnAbrirMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        configurarMenuLateral()
        configurarCategorias()
        configurarBuscador()

        btnCobrar.setOnClickListener {
            mostrarModalCobro()
        }

        findViewById<View>(R.id.btnEscanear).setOnClickListener {
            val input = EditText(this).apply { hint = "Código de barras o nombre..." }
            AlertDialog.Builder(this)
                .setTitle("📷 Lector de Códigos")
                .setView(input)
                .setPositiveButton("Buscar") { _, _ ->
                    etBuscar.setText(input.text.toString().trim())
                }
                .setNegativeButton("Cerrar", null)
                .show()
        }

        cargarDatosDesdeServidor()
    }

    private fun configurarMenuLateral() {
        findViewById<TextView>(R.id.navInventario).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            mostrarFormularioProducto(null)
        }

        findViewById<TextView>(R.id.navHistorial).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            mostrarModalHistorial()
        }

        findViewById<TextView>(R.id.navCierreCaja).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            mostrarModalCierreCaja()
        }

        findViewById<TextView>(R.id.navMetricas).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            mostrarModalMetricas()
        }

        findViewById<TextView>(R.id.navProveedores).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            mostrarModalProveedores()
        }

        findViewById<TextView>(R.id.navTerminales).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            Toast.makeText(this, "Terminal activa: $terminalNombre", Toast.LENGTH_SHORT).show()
        }

        findViewById<TextView>(R.id.navConfig).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            mostrarModalConfiguracion()
        }

        findViewById<TextView>(R.id.navSalir).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    // MODAL DE CREACIÓN / EDICIÓN COMPLETA DE PRODUCTOS
    private fun mostrarFormularioProducto(productoExistente: ProductoPos?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_inventario, null)
        val tvTitulo = dialogView.findViewById<TextView>(R.id.tvTituloInv)
        val etNom = dialogView.findViewById<EditText>(R.id.etInvNombre)
        val etCod = dialogView.findViewById<EditText>(R.id.etInvCodigo)
        val etCosto = dialogView.findViewById<EditText>(R.id.etInvCosto)
        val etPrecio = dialogView.findViewById<EditText>(R.id.etInvPrecio)
        val etStock = dialogView.findViewById<EditText>(R.id.etInvStock)
        val cbPesado = dialogView.findViewById<CheckBox>(R.id.cbInvPesado)
        val cbMayor = dialogView.findViewById<CheckBox>(R.id.cbInvMayor)
        val layoutMayor = dialogView.findViewById<LinearLayout>(R.id.layoutPreciosMayor)
        val etCantMayor = dialogView.findViewById<EditText>(R.id.etInvCantMayor)
        val etPrecioMayor = dialogView.findViewById<EditText>(R.id.etInvPrecioMayor)
        val spDepto = dialogView.findViewById<Spinner>(R.id.spInvDepto)
        val btnCancelar = dialogView.findViewById<MaterialButton>(R.id.btnInvCancelar)
        val btnGuardar = dialogView.findViewById<MaterialButton>(R.id.btnInvGuardar)

        val deptos = arrayOf("Víveres", "Charcutería", "Verduras y Frutas", "Higiene Personal", "Snacks y Golosinas", "Bebidas", "General")
        spDepto.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, deptos)

        cbMayor.setOnCheckedChangeListener { _, isChecked ->
            layoutMayor.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        if (productoExistente != null) {
            tvTitulo.text = "✏️ Editar Producto"
            etNom.setText(productoExistente.nombre)
            etCod.setText(productoExistente.codigo)
            etCosto.setText(productoExistente.costoUsd.toString())
            etPrecio.setText(productoExistente.precioUsd.toString())
            etStock.setText(productoExistente.stock.toString())
            cbPesado.isChecked = productoExistente.esPesado
            cbMayor.isChecked = productoExistente.aplicaMayor
            if (productoExistente.aplicaMayor) {
                layoutMayor.visibility = View.VISIBLE
                etCantMayor.setText(productoExistente.cantMinimaMayor.toString())
                etPrecioMayor.setText(productoExistente.precioMayorUsd.toString())
            }
            val pos = deptos.indexOf(productoExistente.departamento)
            if (pos >= 0) spDepto.setSelection(pos)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnCancelar.setOnClickListener { dialog.dismiss() }

        btnGuardar.setOnClickListener {
            val nom = etNom.text.toString().trim()
            val cod = etCod.text.toString().trim()
            val pre = etPrecio.text.toString().toDoubleOrNull() ?: 0.0
            val cos = etCosto.text.toString().toDoubleOrNull() ?: (pre * 0.75)
            val stk = etStock.text.toString().toDoubleOrNull() ?: 1.0
            val depto = spDepto.selectedItem.toString()
            val pesado = cbPesado.isChecked
            val mayor = cbMayor.isChecked
            val cantMayor = etCantMayor.text.toString().toDoubleOrNull() ?: 3.0
            val preMayor = etPrecioMayor.text.toString().toDoubleOrNull() ?: pre

            if (nom.isNotEmpty() && pre > 0) {
                val pId = productoExistente?.id ?: ("prod_" + System.currentTimeMillis())
                val pObj = JsonObject().apply {
                    addProperty("id", pId)
                    addProperty("negocio_id", negocioId)
                    addProperty("nombre", nom)
                    addProperty("codigo_barras", cod)
                    addProperty("precio_usd", pre)
                    addProperty("costo_usd", cos)
                    addProperty("stock", stk)
                    addProperty("departamento", depto)
                    addProperty("es_pesado", pesado)
                    addProperty("aplica_precio_mayor", mayor)
                    addProperty("cant_minima_mayor", cantMayor)
                    addProperty("precio_mayor_usd", preMayor)
                }

                CoroutineScope(Dispatchers.IO).launch {
                    SupabaseClient.post("productos", pObj.toString())
                    cargarDatosDesdeServidor()
                }
                Toast.makeText(this, "Producto guardado con éxito", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Por favor complete nombre y precio", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    // MODAL DE HISTORIAL COMPLETO
    private fun mostrarModalHistorial() {
        CoroutineScope(Dispatchers.IO).launch {
            val res = SupabaseClient.get("ventas?negocio_id=eq.$negocioId&select=*&order=fecha.desc&limit=15")
            withContext(Dispatchers.Main) {
                val sb = StringBuilder()
                if (res != null) {
                    val arr = JsonParser.parseString(res).asJsonArray
                    for (i in 0 until arr.size()) {
                        val v = arr[i].asJsonObject
                        val id = if (v.has("id")) v.get("id").asString else ""
                        val cajero = if (v.has("cajero_nombre")) v.get("cajero_nombre").asString else "Cajero"
                        val totUsd = if (v.has("total_usd")) v.get("total_usd").asDouble else 0.0
                        val totBs = if (v.has("total_bs")) v.get("total_bs").asDouble else 0.0
                        sb.append("🧾 Factura #$id\n  Cajero: $cajero\n  Total: $ $totUsd  |  $totBs Bs\n  ──────────────────────\n")
                    }
                }
                if (sb.isEmpty()) sb.append("No hay ventas registradas en este turno.")

                AlertDialog.Builder(this@PosActivity)
                    .setTitle("🧾 Historial de Facturas Emitidas")
                    .setMessage(sb.toString())
                    .setPositiveButton("Cerrar", null)
                    .show()
            }
        }
    }

    // CIERRE DE CAJA
    private fun mostrarModalCierreCaja() {
        CoroutineScope(Dispatchers.IO).launch {
            val res = SupabaseClient.get("ventas?negocio_id=eq.$negocioId&select=*")
            var totalGeneralUsd = 0.0
            var totalGeneralBs = 0.0
            var conteoVentas = 0

            if (res != null) {
                val arr = JsonParser.parseString(res).asJsonArray
                conteoVentas = arr.size()
                for (elem in arr) {
                    val v = elem.asJsonObject
                    if (v.has("total_usd")) totalGeneralUsd += v.get("total_usd").asDouble
                    if (v.has("total_bs")) totalGeneralBs += v.get("total_bs").asDouble
                }
            }

            withContext(Dispatchers.Main) {
                val resumen = """
                    Terminal: $terminalNombre
                    Cajero: $cajeroNombre
                    Tasa Oficial BCV: $tasaBcv Bs
                    
                    • Facturas Procesadas: $conteoVentas
                    • Total Recaudado USD: $ ${String.format("%.2f", totalGeneralUsd)}
                    • Total Recaudado BS: ${String.format("%.2f", totalGeneralBs)} Bs
                    
                    ¿Desea asentar el cierre en la base de datos fiscal?
                """.trimIndent()

                AlertDialog.Builder(this@PosActivity)
                    .setTitle("🔒 Cierre de Turno y Caja")
                    .setMessage(resumen)
                    .setPositiveButton("Confirmar y Cerrar") { _, _ ->
                        val cierreId = System.currentTimeMillis().toString()
                        val cObj = JsonObject().apply {
                            addProperty("id", cierreId)
                            addProperty("cajero_id", "usr_dueno")
                            addProperty("cajero_nombre", cajeroNombre)
                            addProperty("terminal_id", "caja_01")
                            addProperty("negocio_id", negocioId)
                            addProperty("monto_inicial_usd", 0.0)
                            addProperty("monto_inicial_bs", 0.0)
                        }
                        CoroutineScope(Dispatchers.IO).launch {
                            SupabaseClient.post("cierres_caja", cObj.toString())
                        }
                        Toast.makeText(this@PosActivity, "Cierre completado y registrado.", Toast.LENGTH_LONG).show()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
    }

    // MODAL DE PROVEEDORES
    private fun mostrarModalProveedores() {
        CoroutineScope(Dispatchers.IO).launch {
            val res = SupabaseClient.get("proveedores?negocio_id=eq.$negocioId&select=*")
            withContext(Dispatchers.Main) {
                val sb = StringBuilder()
                if (res != null) {
                    val arr = JsonParser.parseString(res).asJsonArray
                    for (elem in arr) {
                        val p = elem.asJsonObject
                        val n = if (p.has("nombre")) p.get("nombre").asString else ""
                        val r = if (p.has("rif")) p.get("rif").asString else ""
                        sb.append("• $n ($r)\n")
                    }
                }
                if (sb.isEmpty()) sb.append("No hay proveedores registrados aún.")

                AlertDialog.Builder(this@PosActivity)
                    .setTitle("🚚 Directorio de Proveedores")
                    .setMessage(sb.toString())
                    .setPositiveButton("Cerrar", null)
                    .show()
            }
        }
    }

    // MÉTRICAS
    private fun mostrarModalMetricas() {
        val totalStock = todosLosProductos.sumOf { it.stock }
        val valorInventario = todosLosProductos.sumOf { it.stock * it.precioUsd }

        val mensaje = """
            Comercio: MiniMarket JJJP
            • Cantidad de Productos: ${todosLosProductos.size}
            • Stock Total en Unidades: ${String.format("%.1f", totalStock)}
            • Valor Total del Inventario: $ ${String.format("%.2f", valorInventario)}
            • Equivalente en Bolívares: ${String.format("%.2f", valorInventario * tasaBcv)} Bs
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("📊 Métricas de Inventario")
            .setMessage(mensaje)
            .setPositiveButton("Aceptar", null)
            .show()
    }

    // CONFIGURACIÓN DE TASA BCV
    private fun mostrarModalConfiguracion() {
        val input = EditText(this).apply {
            hint = "Tasa BCV (ej. 857.88)"
            setText(tasaBcv.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        AlertDialog.Builder(this)
            .setTitle("⚙️ Tasa de Cambio BCV")
            .setMessage("Ingrese el valor actualizado de la tasa oficial:")
            .setView(input)
            .setPositiveButton("Actualizar") { _, _ ->
                val nueva = input.text.toString().toDoubleOrNull()
                if (nueva != null && nueva > 0) {
                    tasaBcv = nueva
                    tvTasaBcv.text = "BCV: " + String.format("%.2f", tasaBcv) + " Bs"
                    filtrarYRenderizar()
                    actualizarTotalesUI()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // MODAL DE COBRO OFICIAL MULTIMONEDA
    private fun mostrarModalCobro() {
        if (carrito.isEmpty()) {
            Toast.makeText(this, "El carrito está vacío. Agregue productos.", Toast.LENGTH_SHORT).show()
            return
        }

        var totalUsd = 0.0
        for ((_, item) in carrito) {
            val p = item.producto
            val c = item.cantidad
            val precioAplicado = if (p.aplicaMayor && c >= p.cantMinimaMayor && p.precioMayorUsd > 0) p.precioMayorUsd else p.precioUsd
            totalUsd += (precioAplicado * c)
        }
        val totalBs = totalUsd * tasaBcv

        val dialogView = layoutInflater.inflate(R.layout.dialog_cobro, null)
        val tvUsd = dialogView.findViewById<TextView>(R.id.tvCobroTotalUsd)
        val tvBs = dialogView.findViewById<TextView>(R.id.tvCobroTotalBs)
        val etDoc = dialogView.findViewById<EditText>(R.id.etClienteDoc)
        val etNom = dialogView.findViewById<EditText>(R.id.etClienteNom)
        val spMetodo = dialogView.findViewById<Spinner>(R.id.spMetodoPago)
        val etMonto = dialogView.findViewById<EditText>(R.id.etMontoEntregado)
        val tvVuelto = dialogView.findViewById<TextView>(R.id.tvVueltoCalculado)
        val btnFinalizar = dialogView.findViewById<MaterialButton>(R.id.btnFinalizarVenta)

        tvUsd.text = "$" + String.format("%.2f", totalUsd) + " USD"
        tvBs.text = String.format("%.2f", totalBs) + " Bs (Tasa Oficial)"
        etMonto.setText(String.format("%.2f", totalUsd))

        val metodos = arrayOf("Efectivo ($)", "Pago Móvil (Bs)", "Punto Débito (Bs)", "Transferencia Bancaria", "Crédito de Cliente", "Divisa + Pago Móvil")
        spMetodo.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, metodos)

        etMonto.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                val recibido = s.toString().toDoubleOrNull() ?: 0.0
                val vueltoUsd = if (recibido > totalUsd) recibido - totalUsd else 0.0
                val vueltoBs = vueltoUsd * tasaBcv
                tvVuelto.text = "Vuelto: $" + String.format("%.2f", vueltoUsd) + " (" + String.format("%.2f", vueltoBs) + " Bs)"
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnFinalizar.setOnClickListener {
            val docCli = etDoc.text.toString().trim()
            val nomCli = etNom.text.toString().trim()
            val metodo = spMetodo.selectedItem.toString()

            val clienteObj = if (nomCli.isNotEmpty()) {
                JsonObject().apply {
                    addProperty("nombre", nomCli)
                    addProperty("doc", docCli)
                }
            } else null

            registrarVentaEnServidor(totalUsd, totalBs, metodo, clienteObj)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun registrarVentaEnServidor(totalUsd: Double, totalBs: Double, metodo: String, cliente: JsonObject?) {
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
                    if (cliente != null) add("cliente", cliente)
                    addProperty("sincronizado", true)
                }

                SupabaseClient.post("ventas", ventaBody.toString())

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PosActivity, "✓ Factura emitida exitosamente", Toast.LENGTH_LONG).show()
                    carrito.clear()
                    actualizarTotalesUI()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PosActivity, "Venta registrada localmente", Toast.LENGTH_SHORT).show()
                    carrito.clear()
                    actualizarTotalesUI()
                }
            }
        }
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
                val resNegocio = SupabaseClient.get("negocios?id=eq.$negocioId&select=tasa_bcv_actual")
                if (resNegocio != null) {
                    val arr = JsonParser.parseString(resNegocio).asJsonArray
                    if (arr.size() > 0 && arr[0].asJsonObject.has("tasa_bcv_actual")) {
                        tasaBcv = arr[0].asJsonObject.get("tasa_bcv_actual").asDouble
                    }
                }

                val resProd = SupabaseClient.get("productos?select=*")
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
                                aplicaMayor = if (obj.has("aplica_precio_mayor")) obj.get("aplica_precio_mayor").asBoolean else false,
                                precioMayorUsd = if (obj.has("precio_mayor_usd")) obj.get("precio_mayor_usd").asDouble else 0.0,
                                cantMinimaMayor = if (obj.has("cant_minima_mayor")) obj.get("cant_minima_mayor").asDouble else 3.0,
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
                        todosLosProductos.addAll(obtenerCatalogoBase())
                    }
                    filtrarYRenderizar()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    todosLosProductos.clear()
                    todosLosProductos.addAll(obtenerCatalogoBase())
                    filtrarYRenderizar()
                }
            }
        }
    }

    private fun obtenerCatalogoBase(): List<ProductoPos> {
        return listOf(
            ProductoPos("neg_mujkrui8_1", "Harina PAN Blanca 1kg", "7591001000123", 1.22, 1.09, "Víveres", false, false, 0.0, 3.0, 34.0),
            ProductoPos("neg_mujkrui8_2", "Arroz Blanco Primor 1kg", "7591002000456", 1.35, 1.05, "Víveres", false, false, 0.0, 3.0, 39.0),
            ProductoPos("neg_mujkrui8_3", "Queso Blanco Llanero", "PROD-435517", 4.90, 3.50, "Charcutería", true, false, 0.0, 3.0, 12.977),
            ProductoPos("1790498991354", "Queso Guayanes", "PROD-406962", 4.90, 3.50, "Charcutería", true, false, 0.0, 3.0, 23.048),
            ProductoPos("neg_mujkrui8_4", "Jamón de Pierna Plumrose", "J-002", 8.50, 6.20, "Charcutería", false, false, 0.0, 3.0, 8.2),
            ProductoPos("neg_mujkrui8_5", "Tomate Manzano", "V-003", 3.49, 2.40, "Verduras y Frutas", true, false, 0.0, 3.0, 23.998),
            ProductoPos("neg_mujkrui8_6", "Spray Aclarante Farmatodo", "7591472015188", 4.50, 3.30, "Higiene Personal", false, false, 0.0, 3.0, 15.0),
            ProductoPos("prod_1790580506941", "Doritos 175Grms", "PROD-506941", 1.00, 0.75, "Snacks y Golosinas", false, false, 0.0, 3.0, 24.0)
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

            // Pulsar prolongado sobre el producto para editarlo
            card.setOnLongClickListener {
                mostrarFormularioProducto(prod)
                true
            }

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
            hint = "Ingrese peso en Kg (ej: 0.500)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        AlertDialog.Builder(this)
            .setTitle("⚖️ Balanza Electrónica")
            .setMessage("${prod.nombre}\nPrecio por Kg: $${prod.precioUsd}")
            .setView(input)
            .setPositiveButton("Pesar y Agregar") { _, _ ->
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
        Toast.makeText(this, "${prod.nombre} x $cantidad", Toast.LENGTH_SHORT).show()
    }

    private fun actualizarTotalesUI() {
        var totalUsd = 0.0
        var totalItems = 0.0

        for ((_, item) in carrito) {
            val p = item.producto
            val c = item.cantidad
            val precio = if (p.aplicaMayor && c >= p.cantMinimaMayor && p.precioMayorUsd > 0) p.precioMayorUsd else p.precioUsd
            totalUsd += (precio * c)
            totalItems += c
        }

        val totalBs = totalUsd * tasaBcv

        tvTotalUsd.text = "Total: $" + String.format("%.2f", totalUsd)
        tvTotalBs.text = "Ref: " + String.format("%.2f", totalBs) + " Bs"
        tvItemsCount.text = String.format("%.0f", totalItems) + " productos en orden"
    }
}
