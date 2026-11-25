package com.safeer.mobile.browser.link

/**
 * Tedenski pregled 25. 11. 2026 (V3): zetoni streznika datotek telefona - rok, meja stevila, preklic ob izklopu in ob
 * umiku naprave iz kroga (pravila kot na TV). Vsak scenarij ima pozitivno kontrolo (veljaven zeton gre skozi), da
 * shramba, ki zavrne vse, ne prestane preizkusa. Napake se stejejo, izpis pokaze vse.
 */
private var preverb = 0
private var napak = 0

private fun preveri(opis: String, pogoj: Boolean) {
    preverb++
    if (!pogoj) { napak++; println("  NAPAKA  $opis") }
}

private var stevec = 0
private fun shramba() = ZetoniDatotek { "z" + (++stevec) }
private val DA = { true }
private val NI_UMIKA = { _: String -> false }

private fun rok() {
    val s = shramba()
    val z = s.zetonZa("n-a", 1_000L)
    preveri("kontrola: svez zeton velja", s.velja(z, 1_000L, DA, NI_UMIKA))
    preveri("kontrola: zeton, rabljen pred 11 h, velja", s.velja(z, 1_000L + 11 * 3600_000L, DA, NI_UMIKA))
    // Zadnja raba je bila ob 11 h; 12 h po njej ne velja vec.
    preveri("zeton po 12 h brez rabe ne velja", !s.velja(z, 1_000L + 23 * 3600_000L + 1, DA, NI_UMIKA))

    // Tok, ki tece, drzi zeton pri zivljenju, a najvec 7 dni od izdaje.
    val t = shramba()
    val zt = t.zetonZa("n-b", 0L)
    var cas = 0L
    var zivel = true
    while (cas < ZetoniDatotek.NAJDLJE_MS - 3600_000L) { cas += 3600_000L; zivel = zivel && t.velja(zt, cas, DA, NI_UMIKA) }
    preveri("kontrola: rabljen zeton zivi do 7 dni", zivel)
    preveri("zeton po 7 dneh od izdaje ne velja, tudi ce je rabljen", !t.velja(zt, ZetoniDatotek.NAJDLJE_MS, DA, NI_UMIKA))

    // Po polovici roka naprava dobi nov zeton, prejsnji velja do konca svojega roka.
    val u = shramba()
    val z1 = u.zetonZa("n-c", 0L)
    preveri("pred polovico roka isti zeton", u.zetonZa("n-c", 1000L) == z1)
    val z2 = u.zetonZa("n-c", ZetoniDatotek.VELJA_MS / 2)
    preveri("po polovici roka nov zeton", z2 != z1)
    preveri("kontrola: prejsnji velja naprej", u.velja(z1, ZetoniDatotek.VELJA_MS / 2, DA, NI_UMIKA))
    preveri("kontrola: novi velja", u.velja(z2, ZetoniDatotek.VELJA_MS / 2, DA, NI_UMIKA))
    preveri("napacen zeton ne velja", !u.velja("ni-tak", 0L, DA, NI_UMIKA) && !u.velja("", 0L, DA, NI_UMIKA) && !u.velja(null, 0L, DA, NI_UMIKA))
}

private fun meja() {
    val s = shramba()
    var cas = 0L
    val prvi = s.zetonZa("n-0", cas)
    val izdani = ArrayList<String>()
    for (i in 1..200) { cas += 10; izdani.add(s.zetonZa("n-$i", cas)) }
    preveri("najvec ${ZetoniDatotek.NAJVEC} zetonov", s.stevilo == ZetoniDatotek.NAJVEC)
    preveri("najstarejsi nerabljen zeton odpade", !s.velja(prvi, cas, DA, NI_UMIKA))
    preveri("kontrola: zadnji izdani velja", s.velja(izdani.last(), cas, DA, NI_UMIKA))
    // Enak cas rabe: ravnokar izdani zeton ne sme odpasti.
    val e = shramba()
    for (i in 1..ZetoniDatotek.NAJVEC) e.zetonZa("e-$i", 5L)
    val nov = e.zetonZa("e-nov", 5L)
    preveri("pri enakem casu rabe novi zeton ostane", e.velja(nov, 5L, DA, NI_UMIKA) && e.stevilo == ZetoniDatotek.NAJVEC)
}

private fun umik() {
    val s = shramba()
    val a = s.zetonZa("n-a", 0L)
    val b = s.zetonZa("n-b", 0L)
    val umaknjeni = HashSet<String>()
    val umaknjena = { id: String -> id in umaknjeni }
    preveri("kontrola: zeton naprave v krogu velja", s.velja(a, 1L, DA, umaknjena))
    umaknjeni.add("n-a")
    preveri("zeton umaknjene naprave ne velja", !s.velja(a, 2L, DA, umaknjena))
    umaknjeni.clear()
    preveri("zeton umaknjene naprave je preklican (ne ozivi)", !s.velja(a, 3L, DA, umaknjena))
    preveri("kontrola: druga naprava ostane", s.velja(b, 3L, DA, umaknjena))
    // Prejsnji zeton (id#izdan) iste naprave pade tudi.
    val u = shramba()
    val st = u.zetonZa("n-c", 0L)
    val nv = u.zetonZa("n-c", ZetoniDatotek.VELJA_MS / 2)
    val cas = ZetoniDatotek.VELJA_MS / 2 + 1
    preveri("kontrola: oba zetona naprave veljata", u.velja(st, cas, DA, NI_UMIKA) && u.velja(nv, cas, DA, NI_UMIKA))
    val samoC = { id: String -> id == "n-c" }
    preveri("ob umiku pade tudi prejsnji zeton naprave", !u.velja(st, cas, DA, samoC) && !u.velja(nv, cas, DA, samoC))
    // Napaka pri preverbi kroga = umaknjena (varna smer).
    val n = shramba()
    val zn = n.zetonZa("n-d", 0L)
    preveri("napaka pri preverbi kroga zavrne", !n.velja(zn, 1L, DA) { throw IllegalStateException("krog") })
}

private fun izklop() {
    val s = shramba()
    val z = s.zetonZa("n-a", 0L)
    var deli = true
    val d = { deli }
    preveri("kontrola: zeton ob vklopljenem deljenju velja", s.velja(z, 1L, d, NI_UMIKA))
    deli = false
    preveri("zeton po izklopu deljenja ne velja", !s.velja(z, 2L, d, NI_UMIKA))
    deli = true
    preveri("po ponovnem vklopu stari zeton ne ozivi", !s.velja(z, 3L, d, NI_UMIKA))
    val p = shramba()
    val zp = p.zetonZa("n-b", 0L)
    p.pocisti()
    preveri("pocisti preklice vse zetone", !p.velja(zp, 1L, DA, NI_UMIKA) && p.stevilo == 0)
}

/** Streznik pravila res uporablja za VSAKO zahtevo, tudi POST (DatotekeStreznik rabi Android in tu ne tece). */
private fun uporaba(izvorna: String) {
    check(izvorna.isNotEmpty()) { "NAPAKA: manjka pot do izvorne kode (argument)" }
    val vir = java.io.File(izvorna, "com/safeer/mobile/browser/link/DatotekeStreznik.kt").readText()
    preveri("zetoni so ZetoniDatotek", vir.contains("private val zetoni = ZetoniDatotek {"))
    preveri("preverba dobi stikalo in umik", vir.contains("zetoni.velja(z, zdaj(), deli = { vklopljeno(ctx) }, umaknjena = { umaknjena(ctx, it) || !smeDatoteke(ctx, it) })"))
    preveri("zeton velja samo z dovoljenjem datoteke", vir.contains("DostopPravila.Zahteva.DATOTEKE") && vir.contains("DostopShramba.zmoznosti(ctx, idNaprave)"))
    preveri("umik iz kroga Link Core", vir.contains("KrogNaprave.krog(ctx).jeUmaknjen(idNaprave)"))
    preveri("izklop preklice zetone", vir.contains("if (!vklop) zetoni.pocisti()"))
    val postrezi = vir.substringAfter("private fun postrezi(s: Socket)").substringBefore("private fun preskoci(")
    val preverba = postrezi.indexOf("if (!zetonVelja(zeton))")
    preveri("zeton se preveri pred urejanjem (POST)", preverba >= 0 && postrezi.indexOf("uredi(ctx, uri") > preverba)
}

fun main(args: Array<String>) {
    rok()
    meja()
    umik()
    izklop()
    uporaba(args.firstOrNull().orEmpty())
    if (napak > 0) { println("ZetoniDatotekTest: PADEL ($napak od $preverb preverb)"); kotlin.system.exitProcess(1) }
    println("ZetoniDatotekTest: OK ($preverb preverb)")
}
