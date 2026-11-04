package it.peppedess.salotto.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import it.peppedess.salotto.R

/** Sfondo di Google TV: grigio quasi nero, leggermente freddo. */
val Bg = Color(0xFF0F1115)

/** Google Sans (licenza SIL OFL), il carattere di Google TV. */
val GoogleSans = FontFamily(
    Font(R.font.google_sans_400, FontWeight.Normal),
    Font(R.font.google_sans_500, FontWeight.Medium),
    Font(R.font.google_sans_500, FontWeight.SemiBold),
    Font(R.font.google_sans_700, FontWeight.Bold),
)

private fun TextStyle.sans() = copy(fontFamily = GoogleSans)

private val TvTypography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.sans(),
        displayMedium = t.displayMedium.sans(),
        displaySmall = t.displaySmall.sans(),
        headlineLarge = t.headlineLarge.sans(),
        headlineMedium = t.headlineMedium.sans(),
        headlineSmall = t.headlineSmall.sans(),
        titleLarge = t.titleLarge.sans(),
        titleMedium = t.titleMedium.sans(),
        titleSmall = t.titleSmall.sans(),
        bodyLarge = t.bodyLarge.sans(),
        bodyMedium = t.bodyMedium.sans(),
        bodySmall = t.bodySmall.sans(),
        labelLarge = t.labelLarge.sans(),
        labelMedium = t.labelMedium.sans(),
        labelSmall = t.labelSmall.sans(),
    )
}

/**
 * Colori fissi come su Google TV (non quelli dello sfondo del telefono):
 * grigi neutri e l'azzurro di Google per pulsanti e barre di avanzamento.
 */
private val TvColors = darkColorScheme(
    primary = Color(0xFFE8EAED),
    onPrimary = Color(0xFF15171B),
    primaryContainer = Color(0xFF2B2F36),
    onPrimaryContainer = Color(0xFFE8EAED),
    secondary = Color(0xFF8AB4F8),
    onSecondary = Color(0xFF0B1D3A),
    tertiary = Color(0xFF8AB4F8),
    background = Bg,
    surface = Color(0xFF1B1E24),
    surfaceContainer = Color(0xFF1F2228),
    surfaceContainerHigh = Color(0xFF262A31),
    surfaceContainerHighest = Color(0xFF2D3139),
    onSurface = Color(0xFFE8EAED),
    onSurfaceVariant = Color(0xFFBDC1C6),
    outline = Color(0xFF5F6368),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SalottoTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = TvColors,
        typography = TvTypography,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
