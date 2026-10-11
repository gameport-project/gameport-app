package app.gameport.feature.store

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The filters the player asked to keep: the list opens with them on, every time, until they are forgotten. */
@Singleton
class StoreFilterStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("gameport_store", Context.MODE_PRIVATE)

    /** The filters kept, or none when nothing was kept. */
    fun kept(): StoreFilters? = prefs.getString(KEY, null)?.let(StoreFilters::decode)

    fun keep(filters: StoreFilters) = prefs.edit().putString(KEY, filters.encode()).apply()

    fun forget() = prefs.edit().remove(KEY).apply()

    /** How the list is shown, as the player chose it last. */
    fun viewMode(): StoreViewMode = StoreViewMode.entries.firstOrNull { it.name == prefs.getString(VIEW, null) } ?: StoreViewMode.LIST

    fun setViewMode(mode: StoreViewMode) = prefs.edit().putString(VIEW, mode.name).apply()

    /** How many games a row of the list holds, 2 to 4: four until the player chooses otherwise. */
    fun listColumns(): Int = prefs.getInt(COLUMNS, 4).coerceIn(2, 4)

    fun setListColumns(count: Int) = prefs.edit().putInt(COLUMNS, count.coerceIn(2, 4)).apply()

    /** How the list is ordered, as the player chose it last. */
    fun sort(): StoreSort = StoreSort.entries.firstOrNull { it.name == prefs.getString(SORT, null) } ?: StoreSort.DEFAULT

    fun setSort(sort: StoreSort) = prefs.edit().putString(SORT, sort.name).apply()

    private companion object {
        const val SORT = "sort"
        const val COLUMNS = "list_columns"
        const val KEY = "kept_filters"
        const val VIEW = "view_mode"
    }
}
