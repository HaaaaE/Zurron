package compose.project.zurron

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    val state = rememberWindowState(
        size = DpSize(400.dp, 800.dp),
        position = WindowPosition(2000.dp, 300.dp)
    )
    Window(
        onCloseRequest = ::exitApplication,
        title = "Zurron",
        state = state,
        alwaysOnTop = true
    ) {
        App()
    }
}