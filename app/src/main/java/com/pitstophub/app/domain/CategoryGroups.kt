package com.pitstophub.app.domain

import androidx.annotation.StringRes
import com.pitstophub.app.R

/** Agrupamento do menu de categorias (mesmo do NAV_GROUPS do PWA). */
data class CategoryGroup(@StringRes val title: Int, val ids: List<String>)

object CategoryGroups {
    val all: List<CategoryGroup> = listOf(
        CategoryGroup(R.string.group_formulas, listOf("f1", "f2", "f3", "f1-academy")),
        CategoryGroup(
            R.string.group_endurance,
            listOf("wec", "imsa", "dtm", "gtwc-europe", "gtwc-america", "gtwc-asia"),
        ),
        CategoryGroup(R.string.group_brasil, listOf("nascar-brasil", "porsche-cup", "endurance-brasil")),
        CategoryGroup(R.string.group_american, listOf("indy", "nascar")),
        CategoryGroup(R.string.group_rally, listOf("wrc")),
    )
}
