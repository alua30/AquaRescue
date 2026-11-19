package com.aquarescue.app

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.graphics.toArgb
import com.aquarescue.app.navigation.AquaRescueNavApp
import com.aquarescue.app.presentation.AppLanguage
import com.aquarescue.app.ui.theme.AquaRescueTheme
import com.aquarescue.app.ui.theme.MarineBackground

/**
 * AppCompatActivity нужна для per-app language API: при смене языка AppCompat
 * пересоздаёт Activity с новой конфигурацией, без перезапуска приложения.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Русский — язык по умолчанию: если пользователь ещё не выбирал язык,
        // фиксируем его явно (иначе Android подставил бы язык системы).
        if (!AppLanguage.isSelected()) {
            AppLanguage.apply(AppLanguage.DEFAULT)
        }
        // Тёмная тема всегда: светлые значки системных панелей на тёмном фоне.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(MarineBackground.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(MarineBackground.toArgb()),
        )
        setContent {
            AquaRescueTheme {
                AquaRescueNavApp()
            }
        }
    }
}
