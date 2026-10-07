package com.pitstophub.app.data

import android.content.Context
import com.pitstophub.app.data.model.CatalogFile
import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.model.TeamBio
import com.pitstophub.app.data.remote.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString

/** Catalogo estatico (14 categorias) exportado do PWA para `assets/motorsport_data.json`. */
class StaticCatalog(private val context: Context) {

    private val file: CatalogFile by lazy {
        context.assets.open(ASSET_NAME).bufferedReader().use { AppJson.decodeFromString<CatalogFile>(it.readText()) }
    }

    suspend fun load(): CatalogFile = withContext(Dispatchers.IO) { file }

    suspend fun categories(): List<Category> = load().categories

    suspend fun category(id: String): Category? = load().categories.firstOrNull { it.id == id }

    suspend fun teamBio(categoryId: String, teamId: String): TeamBio? = load().teamBios["$categoryId:$teamId"]

    companion object {
        const val ASSET_NAME = "motorsport_data.json"
    }
}
