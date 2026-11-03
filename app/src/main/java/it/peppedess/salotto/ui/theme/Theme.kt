package it.peppedess.salotto.ui.theme

import android.os.Build
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val Bg = Color(0xFF0B0D10)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SalottoTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val base = if (Build.VERSION.SDK_INT >= 31) {
        dynamicDarkColorScheme(context)
    } else {
        darkColorScheme(
            primary = Color(0xFFA8C7FA),
            onPrimary = Color(0xFF062E6F),
            primaryContainer = Color(0xFF0842A0),
            onPrimaryContainer = Color(0xFFD3E3FD),
            secondary = Color(0xFFC2E7FF),
            tertiary = Color(0xFFE7C2FF),
        )
    }
    MaterialExpressiveTheme(
        colorScheme = base.copy(background = Bg, surface = Bg),
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
