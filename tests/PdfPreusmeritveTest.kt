package com.safeer.mobile.browser

fun main() {
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
    
    // Zasebni v zasebni dovoljeno
    check(PdfPreusmeritve.dovoljen("https://192.168.1.1", "https://192.168.1.2"))
    
    // Zasebni v javni dovoljeno
    check(PdfPreusmeritve.dovoljen("https://192.168.1.1", "https://safeer.si"))

    println("PdfPreusmeritveTest: OK")
}
