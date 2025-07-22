package com.example.myautofillservice.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "websites")
data class WebSite(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String, // Nombre asignado por el usuario
    val pageId: String, // ID técnico generado automáticamente
    val url: String?, // URL real si está disponible
    val domain: String?, // Dominio real si está disponible
    val signature: String, // Firma técnica de la página
    val isUserNamed: Boolean = false, // Si el usuario asignó el nombre manualmente
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long = System.currentTimeMillis()
)