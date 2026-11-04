package com.safeer.mobile.browser

import android.app.Application
import com.safeer.mobile.browser.cast.Dostop

/**
 * Neodvisni pregled 4. 11. 2026 09:10: prvo, kar tece v procesu (pred vsako dejavnostjo, storitvijo in sporocilom Linka).
 * Dostop si tu zapomni, katere naprave so bile v krogu te naprave pred uvedbo dovoljenj - podedovan dostop potem ne
 * sloni na casu, ki ga v krog vpise druga naprava.
 */
class SafeerAplikacija : Application() {
    override fun onCreate() {
        super.onCreate()
        Dostop.zapomniZnane(this)
    }
}
