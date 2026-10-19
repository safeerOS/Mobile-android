# Safeer Mobile Browser 1.0.35

**Strani ne zaznajo blokatorja tudi pri strožjih preverbah.** Skriti vabni elementi javijo običajen položaj (tudi `offsetParent`), sledilne slikice se naložijo, oglasna knjižnica Google (`googletag`) je videti pripravljena, seznam oglasnih in sledilnih naslovov je razširjen. Splošna zaščita, brez receptov za posamezne strani; blokiranje ostaja nespremenjeno.

**Popravek:** vrnjena je zaščita prijavnih in podpisanih povezav (OAuth, `state`, `code`, podpisi, povratni naslovi): čistilec sledilnih parametrov jih ne spreminja. V 1.0.34 je bila po pomoti izpuščena.
