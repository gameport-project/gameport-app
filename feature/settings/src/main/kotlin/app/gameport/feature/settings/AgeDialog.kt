package app.gameport.feature.settings

import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gameport.core.designsystem.BackdropDialog
import app.gameport.core.model.AgePolicy
import app.gameport.core.settings.AgeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class AgeViewModel @Inject constructor(private val store: AgeStore) : ViewModel() {
    val said: StateFlow<Boolean?> = store.said
    val canSeeAdult: StateFlow<Boolean> = store.canSeeAdult

    fun onAnswer(adult: Boolean) = store.answer(adult)
}

/** Asks once whether the player is an adult, as long as the question was not answered. The settings page has the switch to change the answer. */
@Composable
fun AgeAsk(viewModel: AgeViewModel = hiltViewModel()) {
    val said by viewModel.said.collectAsStateWithLifecycle()
    if (said != null) return
    BackdropDialog(
        // The question is answered, not closed.
        onDismissRequest = {},
        title = { Text(stringResource(R.string.age_title)) },
        text = { Text(stringResource(R.string.age_text, AgePolicy.ADULT_AGE)) },
        confirmButton = { Button(onClick = { viewModel.onAnswer(true) }) { Text(stringResource(R.string.age_yes, AgePolicy.ADULT_AGE)) } },
        dismissButton = { OutlinedButton(onClick = { viewModel.onAnswer(false) }) { Text(stringResource(R.string.age_no)) } },
    )
}
