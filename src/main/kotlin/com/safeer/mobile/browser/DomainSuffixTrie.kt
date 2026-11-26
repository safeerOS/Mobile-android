package com.safeer.mobile.browser

/**
 * 🌲 DomainSuffixTrie
 * Visoko-zmogljiva drevesna struktura (Radix / Suffix Trie) za O(k) preverjanje domen in poddomen.
 * Domene shranjuje v obratnem vrstnem redu segmentov (npr. ["com", "doubleclick"] za doubleclick.com).
 * Če je v drevesu "badsite.com", avtomatsko ujame tudi "a.b.badsite.com" brez počasnih nizovnih zank.
 */
class DomainSuffixTrie {

    class Node {
        val children = HashMap<String, Node>()
        var isTerminal: Boolean = false
        var category: String? = null
        var sourceFeed: String? = null
    }

    data class MatchResult(
        val isMatched: Boolean,
        val matchedDomain: String,
        val category: String?,
        val sourceFeed: String?
    )

    private val root = Node()
    var size: Int = 0
        private set

    /**
     * Vstavi domeno v Suffix Trie.
     */
    fun insert(domain: String, category: String? = null, sourceFeed: String? = null) {
        val clean = domain.trim().lowercase()
        if (clean.isEmpty() || clean.startsWith("#")) return

        val parts = clean.split('.').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return

        var current = root
        // Vstavljamo od TLD proti poddomeni (od zadaj naprej)
        for (i in parts.size - 1 downTo 0) {
            val part = parts[i]
            current = current.children.getOrPut(part) { Node() }
        }
        if (!current.isTerminal) {
            current.isTerminal = true
            current.category = category
            current.sourceFeed = sourceFeed
            size++
        }
    }

    /**
     * Vstavi več domen naenkrat.
     */
    fun insertAll(domains: Collection<String>, category: String? = null, sourceFeed: String? = null) {
        for (d in domains) {
            insert(d, category, sourceFeed)
        }
    }

    /**
     * Preveri, ali gostitelj (host) ali katerakoli njegova nad-domena ustreza vnosu v drevesu.
     * Primer: Za "analytics.sub.track.com" preveri [com -> track -> sub -> analytics].
     * Če je [com -> track] označen kot terminal, vrne true takoj!
     */
    fun matches(host: String): Boolean {
        return findMatch(host) != null
    }

    /**
     * Poišče podrobnosti ujemanja (kategorija grožnje, vir itd.).
     */
    fun findMatch(host: String): MatchResult? {
        val clean = host.trim().lowercase()
        if (clean.isEmpty()) return null

        val parts = clean.split('.').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null

        var current = root
        var matchedPartsCount = 0

        for (i in parts.size - 1 downTo 0) {
            val part = parts[i]
            val next = current.children[part] ?: return null
            current = next
            matchedPartsCount++

            if (current.isTerminal) {
                val matchedDomain = parts.subList(parts.size - matchedPartsCount, parts.size).joinToString(".")
                return MatchResult(
                    isMatched = true,
                    matchedDomain = matchedDomain,
                    category = current.category,
                    sourceFeed = current.sourceFeed
                )
            }
        }

        return if (current.isTerminal) {
            MatchResult(
                isMatched = true,
                matchedDomain = clean,
                category = current.category,
                sourceFeed = current.sourceFeed
            )
        } else {
            null
        }
    }

    /**
     * Počisti drevo.
     */
    fun clear() {
        root.children.clear()
        size = 0
    }

    /**
     * Zapise vse vpise v datoteko za [DomenskoKazalo] (4. 11. 2026, prenos s TV: drevo grozenj ob zagonu iz mmap namesto
     * gradnje v kopici). Vsak oznaceni vozel je ena domena; ujemanje ostane isto (najkrajsa shranjena pripona obvelja,
     * kategorija in vir prvega vpisa). [kljuc] pove, iz cesa je drevo zgrajeno.
     */
    fun zapisiKazalo(datoteka: java.io.File, kljuc: ByteArray) {
        val domene = ArrayList<String>(size)
        val vrednosti = ArrayList<Int>(size)
        val besede = ArrayList<String?>()
        val indeksi = HashMap<String?, Int>()
        fun beseda(b: String?): Int = indeksi.getOrPut(b) { besede.add(b); besede.size - 1 }
        // Obhod v globino s skladom (brez rekurzije: globina je stevilo delov domene, a sklad je cenejsi od klicev).
        val sklad = ArrayDeque<Pair<Node, String>>()
        sklad.addLast(root to "")
        while (sklad.isNotEmpty()) {
            val (vozel, pripona) = sklad.removeLast()
            if (vozel !== root && vozel.isTerminal) {
                val k = beseda(vozel.category)
                val v = beseda(vozel.sourceFeed)
                require(k < 0x8000 && v < 0x10000) { "prevec razlicnih kategorij ali virov" }
                domene.add(pripona)
                vrednosti.add((k shl 16) or v)
            }
            for ((del, otrok) in vozel.children) sklad.addLast(otrok to if (pripona.isEmpty()) del else "$del.$pripona")
        }
        val red = domene.indices.sortedWith { a, b -> domene[a].compareTo(domene[b]) }
        val tabela = com.safeer.threatfeed.SortedStringTable.Table(
            red.size,
            { i -> domene[red[i]] },
            { i -> vrednosti[red[i]] },
        )
        com.safeer.threatfeed.SortedStringTable.write(datoteka, kljuc, besede, listOf(tabela))
    }
}

/**
 * Isti seznam kot [DomainSuffixTrie], odprt iz datoteke (FileChannel.map, prenos s TV 4. 11. 2026): ni v kopici in ga ni
 * treba graditi ob zagonu. Iskanje je natanko isto - najkrajsa shranjena pripona gostitelja obvelja.
 */
class DomenskoKazalo private constructor(private val tabela: com.safeer.threatfeed.SortedStringTable) {
    val size: Int get() = tabela.size(0)
    val bajtov: Long get() = tabela.bytes

    fun matches(host: String): Boolean = findMatch(host) != null

    fun findMatch(host: String): DomainSuffixTrie.MatchResult? {
        // Ista normalizacija kot DomainSuffixTrie.findMatch: male crke, brez praznih delov.
        val clean = host.trim().lowercase()
        if (clean.isEmpty() || tabela.size(0) == 0) return null
        // Navaden gostitelj (brez praznih delov) brez razbijanja na dele; sicer kot drevo: prazni deli odpadejo.
        val g = if (clean[0] != '.' && clean[clean.length - 1] != '.' && !clean.contains("..")) clean
            else clean.split('.').filter { it.isNotEmpty() }.joinToString(".").ifEmpty { return null }
        var odmik = g.length
        while (true) {
            odmik = g.lastIndexOf('.', odmik - 1)
            val zacetekPripone = if (odmik < 0) 0 else odmik + 1
            val v = tabela.find(0, g, zacetekPripone)
            if (v >= 0) {
                return DomainSuffixTrie.MatchResult(
                    isMatched = true,
                    matchedDomain = g.substring(zacetekPripone),
                    category = tabela.words.getOrNull(v ushr 16),
                    sourceFeed = tabela.words.getOrNull(v and 0xffff)
                )
            }
            if (odmik <= 0) return null
        }
    }

    /** Celotna datoteka je neposkodovana (CRC32); klici v ozadju, ne na glavni niti. */
    fun neposkodovano(): Boolean = tabela.bodyIntact()

    companion object {
        /** Odpre kazalo, ce je datoteka cela in zgrajena iz [kljuc]; sicer null. Bere samo glavo (ms). */
        fun odpri(datoteka: java.io.File, kljuc: ByteArray): DomenskoKazalo? {
            val t = com.safeer.threatfeed.SortedStringTable.open(datoteka, kljuc) ?: return null
            return if (t.tableCount == 1) DomenskoKazalo(t) else null
        }
    }
}
