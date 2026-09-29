package com.pantoja.facilitopos

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<MaterialButton>(R.id.btnLogin)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString().trim()

            if (email.isNotEmpty() && pass.isNotEmpty()) {
                Toast.makeText(this, "Bienvenido a Facilito POS Nativo", Toast.LENGTH_LONG).show()
                // Aquí enlazaremos la pantalla principal de ventas nativa
            } else {
                Toast.makeText(this, "Complete los campos", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
