package com.safeer.mobile.browser

import com.safeer.mobile.browser.GooglePravila as G

/** isGoogleDomain samo po gostitelju (tedenski pregled 4. 11. 2026; ista pravila kot TV). */
fun main() {
    for (u in listOf("https://www.google.com/search?q=x", "https://google.si/", "https://www.google.co.uk/", "https://mail.google.com/",
                     "https://accounts.google.com/ServiceLogin", "https://www.gstatic.com/x.js", "https://fonts.googleapis.com/css",
                     "https://www.recaptcha.net/recaptcha/api.js", "https://accounts.youtube.com/", "https://WWW.GOOGLE.COM./",
                     "https://user@www.google.com:443/")) {
        check(G.jeGoogle(u)) { "Google: $u" }
    }
    // Prej (niz kjerkoli) so te veljale za Google in ostale brez nasih skript.
    for (u in listOf("https://google.zlo.example/", "https://www.google.zlo.example/x", "https://zlo.example/recaptcha/x",
                     "https://zlo.example/?u=google.com/search", "https://zlo.example/google.si/search", "https://recaptcha-zlo.example/",
                     "https://gstatic.com.zlo.example/", "https://www.google.com@zlo.example/", "https://zlo.example\\@www.google.com/",
                     "https://www.youtube.com/watch?v=x", "https://r1.googlevideo.com/", "https://i.ytimg.com/", "javascript:alert(1)//google.com",
                     null, "", "file:///android_asset/brave_home.html")) {
        check(!G.jeGoogle(u)) { "ni Google: $u" }
    }
    check(G.gostitelj("https://a.b@Www.Google.com:8443/p?q#s") == "www.google.com")
    println("GooglePravilaTest: OK")
}
