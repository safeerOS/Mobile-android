package com.safeer.mobile.browser

fun main() {
    // Brez omrezja: javna imena razresimo v javni naslov, »notranji.primer« v zasebnega.
    PdfPreusmeritve.razresi = { h ->
        when (h) {
            "notranji.primer" -> listOf(java.net.InetAddress.getByAddress(h, byteArrayOf(10, 0, 0, 7)))
            "mesan.primer" -> listOf(java.net.InetAddress.getByAddress(h, byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34)),
                java.net.InetAddress.getByAddress(h, byteArrayOf(127, 0, 0, 1)))
            else -> listOf(java.net.InetAddress.getByAddress(h, byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34)))
        }
    }
    check(PdfPreusmeritve.dovoljen("https://safeer.si/1.pdf", "https://safeer.si/2.pdf"))
    check(PdfPreusmeritve.dovoljen("https://safeer.si", "https://drug-host.com/pdf"))
    
    // HTTP v HTTP samo na istem gostitelju
    check(PdfPreusmeritve.dovoljen("http://safeer.si", "http://safeer.si/nov.pdf"))
    check(!PdfPreusmeritve.dovoljen("http://safeer.si", "http://drug-host.com/nov.pdf"))
    
    // HTTP v HTTPS
    check(PdfPreusmeritve.dovoljen("http://safeer.si", "https://drug-host.com"))
    
    // HTTPS v HTTP prepovedano
    check(!PdfPreusmeritve.dovoljen("https://safeer.si", "http://safeer.si"))
    
    // Javni v lokalni/zasebni prepovedano
    check(!PdfPreusmeritve.dovoljen("https://safeer.si", "https://127.0.0.1/pdf"))
    check(!PdfPreusmeritve.dovoljen("https://safeer.si", "https://localhost/pdf"))
    check(!PdfPreusmeritve.dovoljen("https://safeer.si", "https://192.168.1.1/pdf"))
    check(!PdfPreusmeritve.dovoljen("https://safeer.si", "https://10.0.0.1/pdf"))
    
    for (h in listOf("LOCALHOST", "localhost.", "127.0.0.2", "0.0.0.0", "[::1]", "[::]", "[fe80::1]", "[fd00::1]",
                     "[::ffff:127.0.0.1]", "[::ffff:192.168.1.1]", "169.254.169.254", "172.16.0.1")) {
        check(PdfPreusmeritve.jeLokalniAliZasebni(h)) { "zaseben $h" }
        check(!PdfPreusmeritve.dovoljen("https://safeer.si", "https://$h/pdf")) { "preusmeritev na $h" }
    }
    for (h in listOf("safeer.si", "8.8.8.8", "[2001:4860:4860::8888]", "10.evil.com", "[::ffff:8.8.8.8]")) {
        check(!PdfPreusmeritve.jeLokalniAliZasebni(h)) { "javni $h" }
    }
    check(!PdfPreusmeritve.dovoljen("https://safeer.si", "https://notranji.primer/pdf")) { "ime z zasebnim naslovom" }
    check(!PdfPreusmeritve.dovoljen("https://safeer.si", "https://mesan.primer/pdf")) { "vsaj en zasebni naslov" }
    check(PdfPreusmeritve.dovoljen("https://safeer.si", "https://10.evil.com/pdf"))

    // Zasebni v zasebni dovoljeno
    check(PdfPreusmeritve.dovoljen("https://192.168.1.1", "https://192.168.1.2"))
    
    // Zasebni v javni dovoljeno
    check(PdfPreusmeritve.dovoljen("https://192.168.1.1", "https://safeer.si"))

    println("PdfPreusmeritveTest: OK")
}
