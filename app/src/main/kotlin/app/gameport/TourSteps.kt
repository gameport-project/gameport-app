package app.gameport

import app.gameport.core.designsystem.TourIds
import app.gameport.core.designsystem.TourStep
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.gameport.core.model.TourKind

/**
 * The stops of every tour, in the order of the screen: the buttons of a top bar from left to right, then what lies below. A part a device or a game does
 * not have (the VR tabs on a phone, the notices of a game with none) is left out by the tour itself, so a list holds every stop that can exist.
 */
internal fun tourSteps(kind: TourKind): List<TourStep> = when (kind) {
    TourKind.HOME -> listOf(
        TourStep(TourIds.HOME_SEARCH, R.string.tour_home_search_title, R.string.tour_home_search_text),
        TourStep(TourIds.HOME_FILTERS, R.string.tour_home_filters_title, R.string.tour_home_filters_text),
        TourStep(TourIds.HOME_DOWNLOADS, R.string.tour_home_downloads_title, R.string.tour_home_downloads_text),
        TourStep(TourIds.HOME_STORE, R.string.tour_home_store_title, R.string.tour_home_store_text),
        TourStep(TourIds.HOME_COMPAT, R.string.tour_home_compat_title, R.string.tour_home_compat_text),
        TourStep(TourIds.HOME_SETTINGS, R.string.tour_home_settings_title, R.string.tour_home_settings_text),
        TourStep(TourIds.HOME_TABS, R.string.tour_home_tabs_title, R.string.tour_home_tabs_text),
        TourStep(TourIds.HOME_NEW, R.string.tour_home_new_title, R.string.tour_home_new_text),
        TourStep(TourIds.HOME_ALL, R.string.tour_home_all_title, R.string.tour_home_all_text),
    )
    TourKind.STORE -> listOf(
        TourStep(TourIds.STORE_TABS, R.string.tour_store_tabs_title, R.string.tour_store_tabs_text),
        TourStep(TourIds.STORE_SEARCH, R.string.tour_store_search_title, R.string.tour_store_search_text),
        TourStep(TourIds.STORE_FILTERS, R.string.tour_store_filters_title, R.string.tour_store_filters_text),
        TourStep(TourIds.STORE_SORT, R.string.tour_store_sort_title, R.string.tour_store_sort_text),
        TourStep(TourIds.STORE_VIEW_LIST, R.string.tour_store_view_list_title, R.string.tour_store_view_list_text, demo = TourIds.DEMO_STORE_LIST),
        TourStep(TourIds.STORE_VIEW_LIST, R.string.tour_store_view_columns_title, R.string.tour_store_view_columns_text, demo = TourIds.DEMO_STORE_COLUMNS),
        TourStep(TourIds.STORE_VIEW_LIST, R.string.tour_store_view_columns_title, R.string.tour_store_view_columns_again_text, demo = TourIds.DEMO_STORE_COLUMNS_AGAIN),
        TourStep(TourIds.STORE_VIEW_COVERS, R.string.tour_store_view_covers_title, R.string.tour_store_view_covers_text, demo = TourIds.DEMO_STORE_COVERS),
        TourStep(TourIds.STORE_STATE, R.string.tour_store_state_title, R.string.tour_store_state_text),
        TourStep(TourIds.STORE_LIST, R.string.tour_store_list_title, R.string.tour_store_list_text),
    )
    TourKind.STORE_GAME -> listOf(
        TourStep(TourIds.STOREGAME_LABELS, R.string.tour_storegame_labels_title, R.string.tour_storegame_labels_text),
        TourStep(TourIds.STOREGAME_BUTTONS, R.string.tour_storegame_buttons_title, R.string.tour_storegame_buttons_text),
        TourStep(TourIds.STOREGAME_DETAILS, R.string.tour_storegame_details_title, R.string.tour_storegame_details_text),
    )
    TourKind.COMPAT_TABLE -> listOf(
        TourStep(TourIds.TABLE_SEARCH, R.string.tour_table_search_title, R.string.tour_table_search_text),
        TourStep(TourIds.TABLE_FILTERS, R.string.tour_table_filters_title, R.string.tour_table_filters_text),
        TourStep(TourIds.TABLE_CARD, R.string.tour_table_card_title, R.string.tour_table_card_text),
        TourStep(TourIds.TABLE_UNFOLD, R.string.tour_table_unfold_title, R.string.tour_table_unfold_text),
    )
    TourKind.DOWNLOADS -> listOf(
        TourStep(TourIds.DOWNLOADS_HEADER, R.string.tour_downloads_header_title, R.string.tour_downloads_header_text),
        TourStep(TourIds.DOWNLOADS_UPDATES, R.string.tour_downloads_updates_title, R.string.tour_downloads_updates_text),
        TourStep(TourIds.DOWNLOADS_ENTRY, R.string.tour_downloads_entry_title, R.string.tour_downloads_entry_text),
    )
    TourKind.GAME -> listOf(
        TourStep(TourIds.GAME_LABELS, R.string.tour_game_labels_title, R.string.tour_game_labels_text),
        TourStep(TourIds.GAME_ACTIONS, R.string.tour_game_actions_title, R.string.tour_game_actions_text),
        TourStep(TourIds.GAME_NOTES, R.string.tour_game_notes_title, R.string.tour_game_notes_text),
        TourStep(TourIds.GAME_MENU, R.string.tour_game_menu_title, R.string.tour_game_menu_text),
        TourStep(TourIds.GAME_SETTINGS, R.string.tour_game_settings_title, R.string.tour_game_settings_text),
        TourStep(TourIds.GAME_ISSUES, R.string.tour_game_issues_title, R.string.tour_game_issues_text),
        TourStep(TourIds.GAME_INFO, R.string.tour_game_info_title, R.string.tour_game_info_text),
    )
    TourKind.GAME_COMPAT -> listOf(
        TourStep(TourIds.GAMECOMPAT_FRAME, R.string.tour_gamecompat_frame_title, R.string.tour_gamecompat_frame_text),
        TourStep(TourIds.GAMECOMPAT_RECENTER, R.string.tour_gamecompat_recenter_title, R.string.tour_gamecompat_recenter_text),
    )
    TourKind.GAME_CONTROLLERS -> listOf(
        TourStep(TourIds.CONTROLLERS_SWITCH, R.string.tour_controllers_switch_title, R.string.tour_controllers_switch_text),
        TourStep(TourIds.CONTROLLERS_CARDS, R.string.tour_controllers_cards_title, R.string.tour_controllers_cards_text),
        TourStep(TourIds.CONTROLLERS_RESET, R.string.tour_controllers_reset_title, R.string.tour_controllers_reset_text),
    )
    TourKind.SETTINGS -> listOf(
        TourStep(TourIds.SETTINGS_CATEGORIES, R.string.tour_settings_categories_title, R.string.tour_settings_categories_text),
        TourStep(TourIds.SETTINGS_PAGE, R.string.tour_settings_look_title, R.string.tour_settings_look_text, demo = TourIds.DEMO_SETTINGS_LOOK),
        TourStep(TourIds.SETTINGS_HELP_ITEM, R.string.tour_settings_help_title, R.string.tour_settings_help_text, demo = TourIds.DEMO_SETTINGS_HELP),
    )
    TourKind.GAME_SAVES -> listOf(
        TourStep(TourIds.SAVES_REFRESH, R.string.tour_saves_refresh_title, R.string.tour_saves_refresh_text),
        TourStep(TourIds.SAVES_SUMMARY, R.string.tour_saves_summary_title, R.string.tour_saves_summary_text),
        TourStep(TourIds.SAVES_FILES, R.string.tour_saves_files_title, R.string.tour_saves_files_text),
        TourStep(TourIds.SAVES_ACTIONS, R.string.tour_saves_actions_title, R.string.tour_saves_actions_text),
    )
}

/** The name of the page a tour belongs to, shown when the complete tour enters it. */
internal fun tourPageName(kind: TourKind): Int = when (kind) {
    TourKind.HOME -> R.string.tour_enter_home
    TourKind.STORE -> R.string.tour_enter_store
    TourKind.STORE_GAME -> R.string.tour_enter_store_game
    TourKind.COMPAT_TABLE -> R.string.tour_enter_table
    TourKind.DOWNLOADS -> R.string.tour_enter_downloads
    TourKind.GAME -> R.string.tour_enter_game
    TourKind.GAME_COMPAT -> R.string.tour_enter_game_compat
    TourKind.GAME_CONTROLLERS -> R.string.tour_enter_controllers
    TourKind.GAME_SAVES -> R.string.tour_enter_saves
    TourKind.SETTINGS -> R.string.tour_enter_settings
}

/** The mark of the page a tour belongs to: the same icon as the button that opens it. */
@Composable
internal fun TourPageIcon(kind: TourKind) {
    val size = Modifier.size(64.dp)
    when (kind) {
        TourKind.HOME -> Icon(Icons.Filled.Home, null, size, Color.White)
        TourKind.STORE, TourKind.STORE_GAME -> Icon(Icons.Filled.Storefront, null, size, Color(0xFF4FC3F7))
        TourKind.COMPAT_TABLE -> app.gameport.core.designsystem.CompatToolsIcon(size = 72.dp)
        TourKind.DOWNLOADS -> Icon(Icons.Filled.Download, null, size, Color.White)
        TourKind.GAME -> Icon(Icons.Filled.VideogameAsset, null, size, Color.White)
        TourKind.GAME_COMPAT -> Icon(Icons.Filled.Build, null, size, Color(0xFFFFB74D))
        TourKind.GAME_CONTROLLERS -> Icon(Icons.Filled.SportsEsports, null, size, Color.White)
        TourKind.GAME_SAVES -> Icon(Icons.Filled.CloudSync, null, size, Color.White)
        TourKind.SETTINGS -> Icon(Icons.Filled.Settings, null, size, Color.White)
    }
}
