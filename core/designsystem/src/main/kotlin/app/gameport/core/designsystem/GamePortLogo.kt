package app.gameport.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource

/** The GamePort logo, white, for the dark gradient every page sits on. Size it with [modifier]. */
@Composable
fun GamePortLogo(modifier: Modifier = Modifier) {
    Image(painter = painterResource(R.drawable.gameport_logo), contentDescription = null, modifier = modifier)
}
