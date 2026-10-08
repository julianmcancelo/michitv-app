/// <reference path="./kino.d.ts" />

const HOST = 'https://vww.animeflv.one'

const BROWSER_UA = "Mozilla/5.0 (X11; Linux x86_64; rv:157.0) Gecko/20100101 Firefox/157.0"

// Limpia el título para obtener el nombre base de la serie (igual que corregir_SerieName en Python)
function cleanTitle(title) {
    return title
        .replace(/&#8217;/g, "'")
        .replace(/^Ver\s+/i, '')
        // Elimina "Episodio 13", "Capítulo 5", etc. (con o sin número)
        .replace(/(?:Capítulo|Capitulo|Episodio|Episode)\s*\d*/gi, '')
        .replace(/Movie|\(Sin Relleno\)|\(TV\)/gi, '')
        .replace(/Sub\s|Español|Latino|Castellano|HD|Temporada\s+\d+|\(\d{4}\)/gi, '')
        // Limpia espacios dobles que puedan quedar al borrar texto
        .replace(/\s+/g, ' ')
        .trim()
}

// Genera un ID estable y válido para Kino (máx 128 caracteres, solo alfanumérico y _)
function getStableId(str) {
    return btoa(str)
        .replace(/[^A-Za-z0-9]/g, '_')
        .substring(0, 128)
}

// 1. Pantalla de Inicio: Muestra filas con elementos reales para que Kino no las descarte
export async function home() {
    await null // Regla de oro: siempre un await antes de cualquier validación
    const rows = []

    // Fila 1: Últimos episodios
    try {
        const res1 = await kino.fetch(`${HOST}/`)
        if (res1.ok) {
            const html = await res1.text()
            const match = html.match(
                /Últimos episodios agregados<([\s\S]*?)Últimos animes agregados</
            )
            const bloque = match ? match[1] : ''
            const articles = bloque.match(/<article[\s\S]*?<\/article>/g) || []
            const items = []

                        for (let i = 0; i < Math.min(20, articles.length); i++) {
                const article = articles[i]
                const urlMatch = article.match(/href="([^"]+)"/)
                const titleMatch = article.match(/alt="([^"]+)"/) || article.match(/title="([^"]+)"/)
                const thumbMatch = article.match(/src="([^"]+)"/)
                const episMatch = article.match(/<u>Episodio\s+([^<]+)<\/u>/i)

                if (urlMatch && titleMatch) {
                    let itemUrl = urlMatch[1]
                    if (itemUrl.startsWith('./')) itemUrl = HOST + itemUrl.substring(1)
                    else if (itemUrl.startsWith('/')) itemUrl = HOST + itemUrl

                    // Usamos cleanTitle para eliminar el "episodio X" redundante del alt
                    const cleanName = cleanTitle(titleMatch[1])
                    const thumb = thumbMatch ? thumbMatch[1] : ''
                    const epis = episMatch ? episMatch[1].trim() : '1'

                    items.push({
                        id: getStableId(itemUrl),
                        ref: `act:resolve_ep|url:${itemUrl}`,
                        // Título limpio, sin códigos de color y bien formateado
                        title: `Episodio ${epis} - ${cleanName}`,
                        kind: 'movie',
                        poster: thumb.startsWith('http') ? thumb : HOST + thumb
                    })
                }
            }
            if (items.length > 0)
                rows.push({
                    id: 'last_epis',
                    title: 'Últimos episodios',
                    items,
                    ref: 'act:last_epis',
                    genre: 'anime'
                })
        }
    } catch (e) {
        /* ignorar errores de red para no romper home() */
    }

    // Fila 2: Catálogo
    try {
        const res2 = await kino.fetch(`${HOST}/animes?tipo=anime`)
        if (res2.ok) {
            const html = await res2.text()
            const match = html.match(/<button>Filtrar<\/button>([\s\S]*)$/)
            const bloque = match ? match[1] : html
            const articles = bloque.match(/<article[\s\S]*?<\/article>/g) || []
            const items = []

            for (let i = 0; i < Math.min(20, articles.length); i++) {
                const article = articles[i]
                const urlMatch = article.match(/href="([^"]+)"/)
                const titleMatch =
                    article.match(/alt="([^"]+)"/) || article.match(/title="([^"]+)"/)
                const thumbMatch = article.match(/src="([^"]+)"/)

                if (urlMatch && titleMatch) {
                    let itemUrl = urlMatch[1]
                    if (itemUrl.startsWith('./')) itemUrl = HOST + itemUrl.substring(1)
                    else if (itemUrl.startsWith('/')) itemUrl = HOST + itemUrl

                    const title = titleMatch[1]
                        .replace(/&#8217;/g, "'")
                        .replace(/^Ver\s+/i, '')
                    const thumb = thumbMatch ? thumbMatch[1] : ''
                    const cleanName = cleanTitle(title)

                    items.push({
                        id: getStableId(itemUrl),
                        ref: `act:episodios|url:${itemUrl}|name:${cleanName}`,
                        title: title,
                        kind: 'series',
                        poster: thumb.startsWith('http') ? thumb : HOST + thumb
                    })
                }
            }
            if (items.length > 0)
                rows.push({
                    id: 'catalogo',
                    title: 'Catálogo',
                    items,
                    ref: 'act:list_all|url:/animes?tipo=anime',
                    genre: 'anime'
                })
        }
    } catch (e) {
        /* ignorar */
    }

    if (rows.length === 0) {
        rows.push({
            id: 'fallback',
            title: 'Usa la búsqueda para encontrar animes',
            items: [],
            genre: 'anime'
        })
    }
    return rows
}

// 2. Navegación y Paginación ("Ver más")
export async function browse(ref, cursor) {
    await null

    let cursorData = { url: `${HOST}/`, page: 1 }
    if (cursor) {
        try {
            cursorData = JSON.parse(atob(cursor))
        } catch (e) { }
    }

    const parts = ref.split('|')
    const action = parts[0].replace('act:', '')

    let url = cursorData.url
    if (action === 'list_all' && !cursor) {
        const urlPart = parts.find(p => p.startsWith('url:'))
        if (urlPart) url = HOST + urlPart.replace('url:', '')
    } else if (action === 'last_epis' || action === 'list_last') {
        url = HOST + '/'
    }

    const response = await kino.fetch(url)
    if (!response.ok)
        throw kino.error('unavailable', 'No se pudo conectar a AnimeFLV')
    const html = await response.text()

    const items = []
    let bloque = html

    if (action === 'last_epis') {
        const match = html.match(
            /Últimos episodios agregados<([\s\S]*?)Últimos animes agregados</
        )
        bloque = match ? match[1] : ''
    } else if (action === 'list_last') {
        const match = html.match(/Últimos animes agregados<([\s\S]*?)<\/section>/)
        bloque = match ? match[1] : ''
    } else {
        const match = html.match(/<button>Filtrar<\/button>([\s\S]*)$/)
        bloque = match ? match[1] : html
    }

    const articles = bloque.match(/<article[\s\S]*?<\/article>/g) || []

    for (const article of articles) {
        const urlMatch = article.match(/href="([^"]+)"/)
        
        const titleMatch =
            article.match(/alt="([^"]+)"/) || article.match(/title="([^"]+)"/)
        const thumbMatch = article.match(/src="([^"]+)"/)

        if (!urlMatch || !titleMatch) continue

        let itemUrl = urlMatch[1]
        if (itemUrl.includes('/noticias/')) continue

        let title = titleMatch[1].replace(/&#8217;/g, "'").replace(/^Ver\s+/i, '')
        let thumb = thumbMatch ? thumbMatch[1] : ''

        if (itemUrl.startsWith('./')) itemUrl = HOST + itemUrl.substring(1)
        else if (itemUrl.startsWith('/')) itemUrl = HOST + itemUrl

        const cleanName = cleanTitle(title)
        const isMovie =
            article.includes('>Pelicula<') || itemUrl.includes('?tipo=pelicula')

        if (action === 'last_epis') {
            const episMatch = article.match(/<u>Episodio\s+([^<]+)<\/u>/i)
            const epis = episMatch ? episMatch[1].trim() : '1'
            items.push({
                id: getStableId(itemUrl),
                ref: `act:resolve_ep|url:${itemUrl}`,
                title: `[COLOR goldenrod]Epis.[/COLOR] ${epis} ${title}`,
                kind: 'movie',
                poster: thumb.startsWith('http') ? thumb : HOST + thumb
            })
        } else {
            items.push({
                id: getStableId(itemUrl),
                ref: `act:episodios|url:${itemUrl}|name:${cleanName}`,
                title: title,
                kind: isMovie ? 'movie' : 'series',
                poster: thumb.startsWith('http') ? thumb : HOST + thumb
            })
        }
    }

    let next = null
    if (action !== 'last_epis' && action !== 'list_last') {
        const nextPageMatch = html.match(
            /<ul class="pag">[\s\S]*?<a class="se"[\s\S]*?<\/a>[\s\S]*?href="([^"]+)"/
        )
        if (nextPageMatch) {
            let nextUrl = nextPageMatch[1]
            if (nextUrl.includes('&pag=')) {
                if (nextUrl.startsWith('/')) nextUrl = HOST + nextUrl
                next = btoa(JSON.stringify({ url: nextUrl, page: cursorData.page + 1 }))
            }
        }
    }

    return { items, next }
}

// 3. Búsqueda
export async function search(query) {
    await null
    const searchUrl = `${HOST}/animes?buscar=${encodeURIComponent(query.q)}`
    const response = await kino.fetch(searchUrl)
    if (!response.ok)
        throw kino.error('unavailable', 'No se pudo realizar la búsqueda')
    const html = await response.text()

    const items = []
    const bloqueMatch = html.match(/<button>Filtrar<\/button>([\s\S]*)$/)
    const bloque = bloqueMatch ? bloqueMatch[1] : html
    const articles = bloque.match(/<article[\s\S]*?<\/article>/g) || []

    for (const article of articles) {
        const urlMatch = article.match(/href="([^"]+)"/)
        const titleMatch =
            article.match(/alt="([^"]+)"/) || article.match(/title="([^"]+)"/)
        const thumbMatch = article.match(/src="([^"]+)"/)

        if (!urlMatch || !titleMatch) continue

        let itemUrl = urlMatch[1]
        let title = titleMatch[1].replace(/&#8217;/g, "'").replace(/^Ver\s+/i, '')
        let thumb = thumbMatch ? thumbMatch[1] : ''

        if (itemUrl.startsWith('./')) itemUrl = HOST + itemUrl.substring(1)
        else if (itemUrl.startsWith('/')) itemUrl = HOST + itemUrl

        const cleanName = cleanTitle(title)
        const isMovie =
            article.includes('>Pelicula<') || itemUrl.includes('?tipo=pelicula')

        items.push({
            id: getStableId(itemUrl),
            ref: `act:episodios|url:${itemUrl}|name:${cleanName}`,
            title: title,
            kind: isMovie ? 'movie' : 'series',
            poster: thumb.startsWith('http') ? thumb : HOST + thumb
        })
    }
    return items
}

// 4. Lista de Episodios
// 4. Lista de Episodios
export async function episodes(ref) {
    await null
    const parts = ref.split('|')
    const urlPart = parts.find(p => p.startsWith('url:'))
    const namePart = parts.find(p => p.startsWith('name:'))

    if (!urlPart) throw kino.error('not_found', 'URL no encontrada')

    const url = urlPart.replace('url:', '')
    const serieName = namePart ? namePart.replace('name:', '') : 'Anime'

    const response = await kino.fetch(url)
    if (!response.ok)
        throw kino.error('unavailable', 'No se pudo cargar el anime')
    const html = await response.text()

    if (html.includes('Proximamente<')) {
        throw kino.error('not_found', 'Este anime está próximo a estrenarse')
    }

    const epsMatch = html.match(/var\s+eps\s*=\s*(\[[\s\S]*?\]);/)
    if (!epsMatch) throw kino.error('not_found', 'No se encontraron episodios')

    try {
        // Parseamos el array JSON correctamente. 
        // El formato real en la web es: [["10","0",""],["9","0",""],["8","0",""],...]
        // donde el primer elemento de cada sub-array es el número de episodio.
        const epsArray = JSON.parse(epsMatch[1])
        const episodes = []

        for (let i = 0; i < epsArray.length; i++) {
            const epData = epsArray[i]
            const epNum = epData[0] // El número de episodio real (ej: "10", "9", "3")
            
            // Construimos la URL reemplazando /anime/ por /ver/ y añadiendo el número
            const epUrl = url.replace('/anime/', '/ver/') + '-' + epNum
            
            // Limpiamos el número para mostrarlo y ordenarlo correctamente
            const epNumClean = epNum.replace(/[^0-9]/g, '')
            const epNumber = epNumClean ? parseInt(epNumClean, 10) : (i + 1)

            episodes.push({
                number: epNumber,
                title: `Episodio ${epNum}`,
                ref: `act:resolve_ep|url:${epUrl}|name:${serieName}`,
                season: 1
            })
        }

        // Ordenamos los episodios de menor a mayor número para una mejor experiencia de usuario
        episodes.sort((a, b) => a.number - b.number)

        return { series: { title: serieName }, episodes }
    } catch (e) {
        kino.log(`Error al procesar episodios: ${e.message}`)
        throw kino.error('not_found', 'Error al procesar la lista de episodios')
    }
}

// =====================================================================
// FUNCIONES AUXILIARES DE RESOLUCIÓN
// =====================================================================

function decodeHex(hex) {
    let str = ''
    for (let i = 0; i < hex.length; i += 2) {
        str += String.fromCharCode(parseInt(hex.substring(i, i + 2), 16))
    }
    return str
}

// Desempaquetador de scripts eval (fallback)
function unpackEval(script) {
    const m = script.match(
        /eval\(function\(p,a,c,k,e,[a-z]\)\{[\s\S]*?\}\s*\('([\s\S]+?)',\s*(\d+),\s*(\d+),\s*'([\s\S]+?)'\.split\('\|'\)/
    )
    if (!m) return null

    const payload = m[1]
    const radix = parseInt(m[2])
    const symtab = m[4].split('|')
    const chars = '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ'

    const unbase = s => {
        let r = 0
        for (const c of s) {
            r = r * radix + chars.indexOf(c)
        }
        return r
    }

    return payload.replace(/\b([0-9a-zA-Z]+)\b/g, match => {
        const idx = unbase(match)
        return !isNaN(idx) && symtab[idx] && symtab[idx] !== ''
            ? symtab[idx]
            : match
    })
}

function normalizeServerUrl(url) {
    let normalized = url

    if (
        normalized.includes('filemoon') ||
        normalized.includes('fmoonembed') ||
        normalized.includes('embedmoon') ||
        normalized.includes('moonjscdn') ||
        normalized.includes('l1afav') ||
        normalized.includes('byse')
    ) {
        normalized = normalized
            .replace(/\/filemoon\.[a-z]+/g, '/filemoon.sx')
            .replace(/\/fmoonembed\.[a-z]+/g, '/filemoon.sx')
            .replace(/\/embedmoon\.[a-z]+/g, '/filemoon.sx')
            .replace(/\/moonjscdn\.[a-z]+/g, '/filemoon.sx')
            .replace(/\/l1afav\.[a-z]+/g, '/filemoon.sx')
            .replace(/\/byse[a-z]*\.[a-z]+/g, '/filemoon.sx')

        normalized = normalized.replace(
            /https?:\/\/[a-zA-Z0-9.-]+\//,
            'https://filemoon.sx/'
        )
    } else if (
        normalized.includes('streamwish') ||
        normalized.includes('wish') ||
        normalized.includes('dhcplay') ||
        normalized.includes('streamhg') ||
        normalized.includes('hlsflex')
    ) {
        normalized = normalized
            .replace(/\/streamwish\.[a-z]+/g, '/streamwish.to')
            .replace(/\/strwish\.[a-z]+/g, '/streamwish.to')
            .replace(/\/embedwish\.[a-z]+/g, '/streamwish.to')
            .replace(/\/dhcplay\.[a-z]+/g, '/streamwish.to')
            .replace(/\/streamhg\.[a-z]+/g, '/streamwish.to')

        normalized = normalized.replace(
            /https?:\/\/[a-zA-Z0-9.-]+\//,
            'https://streamwish.to/'
        )
    } else if (
        normalized.includes('filelions') ||
        normalized.includes('azipcdn') ||
        normalized.includes('alions') ||
        normalized.includes('peytonepre') ||
        normalized.includes('smoothpre') ||
        normalized.includes('movearnpre')
    ) {
        normalized = normalized
            .replace(/\/filelions\.[a-z]+/g, '/filelions.to')
            .replace(/\/azipcdn\.[a-z]+/g, '/filelions.to')
            .replace(/\/smoothpre\.[a-z]+/g, '/filelions.to')
            .replace(/\/movearnpre\.[a-z]+/g, '/filelions.to')

        normalized = normalized.replace(
            /https?:\/\/[a-zA-Z0-9.-]+\//,
            'https://filelions.to/'
        )
    } else if (
        normalized.includes('lulustream') ||
        normalized.includes('luluvdo') ||
        normalized.includes('luluvid') ||
        normalized.includes('ponmi') ||
        normalized.includes('d00ds')
    ) {
        normalized = normalized
            .replace(/\/luluvdo\.[a-z]+/g, '/lulustream.com')
            .replace(/\/luluvid\.[a-z]+/g, '/lulustream.com')
            .replace(/\/ponmi\.[a-z]+/g, '/lulustream.com')

        normalized = normalized.replace(
            /https?:\/\/[a-zA-Z0-9.-]+\//,
            'https://lulustream.com/'
        )
    } else if (
        normalized.includes('vidguard') ||
        normalized.includes('vgfplay') ||
        normalized.includes('listeamed') ||
        normalized.includes('vid-guard')
    ) {
        normalized = normalized
            .replace(/\/vidguard\.[a-z]+/g, '/vgembed.com')
            .replace(/\/vgfplay\.[a-z]+/g, '/vgembed.com')
            .replace(/\/listeamed\.[a-z]+/g, '/vgembed.com')

        normalized = normalized.replace(
            /https?:\/\/[a-zA-Z0-9.-]+\//,
            'https://vgembed.com/'
        )
    }

    return normalized
}

// =====================================================================
// VALIDADORES
// =====================================================================

async function isVideoValid(url, headers) {
    try {
        const response = await kino.fetch(url, {
            method: 'GET',
            headers,
            timeoutMs: 15000
        })

        if (!response.ok) return false

        const html = await response.text()

        if (html.length < 3000) return false

        const errorPatterns = [
            /blocked/i,
            /copyright/i,
            /deleted/i,
            /removed/i,
            /not\s+found/i,
            /private\s+video/i,
            /violation/i,
            /infringement/i,
            /dmca/i,
            /not\s+available/i,
            /Restricted/i,
            /заблокирован/i,
            /удален/i,
            /недоступен/i,
            /правообладатель/i
        ]

        for (const regex of errorPatterns) {
            if (regex.test(html)) return false
        }

        return true
    } catch (e) {
        return false
    }
}

async function isMegaValid(url, headers) {
    try {
        const response = await kino.fetch(url, {
            method: 'GET',
            headers,
            timeoutMs: 15000
        })

        if (!response.ok) return false

        const html = await response.text()

        const megaErrorPatterns = [
            /login|sign\s+in|sign\s+up/i,
            /download\s+page|file\s+download/i,
            /account\s+required|premium\s+account/i,
            /file\s+not\s+found|no\s+such\s+file/i
        ]

        for (const regex of megaErrorPatterns) {
            if (regex.test(html)) return false
        }

        if (html.length < 5000) return false

        return true
    } catch (e) {
        return false
    }
}


// Resolvedores activos
const RESOLVERS = {
    Voe: resolverUrlVoe,
    MP4Upload: resolverUrlMp4Upload
    // Filemoon: extractStreamingByse, // (Opcional, lo manejamos aparte por seguridad)
};

const SERVER_PRIORITY = Object.keys(RESOLVERS);

// =====================================================================
// FUNCIÓN PRINCIPAL
// =====================================================================

export async function resolve(ref) {
    await null

    const parts = ref.split('|')
    const urlPart = parts.find(p => p.startsWith('url:'))

    if (!urlPart) {
        throw kino.error('not_found', 'URL no encontrada')
    }

    const url = urlPart.replace('url:', '')

    kino.log('══════════════════════════════════════')
    kino.log('🔎 INICIANDO RESOLUCIÓN')
    kino.log(`🌐 URL: ${url}`)
    kino.log('══════════════════════════════════════')

    const response = await kino.fetch(url)

    if (!response.ok) {
        throw kino.error('unavailable', 'No se pudo cargar el episodio')
    }

    const html = await response.text()

    if (html.includes('POW_CHALLENGE')) {
        throw kino.error(
            'unavailable',
            'Este enlace requiere verificación (PoW) no soportada.'
        )
    }

    const dEncrypt = html.match(/data-encrypt="([^"]+)"/)?.[1]

    const validUrls = []

    const unsupportedDomains = [
        '1fichier',
        'fembed',
        'embedsito',
        'streamsb',
        'embedsb',
        'nyuu',
        '4sync',
        'rpmplayer',
        'streamium.xyz',
        'pelispng',
        'pelistop',
        '/descargas/',
        'mystream',
        'zippyshare'
    ]

    // ================================================================
    // OBTENER SERVIDORES
    // ================================================================

    if (dEncrypt) {
        try {
            const flvResponse = await kino.fetch('https://vww.animeflv.one/flv', {
                method: 'POST',
                headers: {
                    Referer: url,
                    'X-Requested-With': 'XMLHttpRequest'
                },
                body: {
                    form: {
                        acc: 'opt',
                        i: dEncrypt
                    }
                }
            })

            if (flvResponse.ok) {
                const data1 = await flvResponse.text()

                const liMatches = data1.match(/<li[\s\S]*?<\/li>/g) || []

                kino.log('')
                kino.log('📋 SERVIDORES ENCONTRADOS')
                kino.log(`Total encontrados: ${liMatches.length}`)
                kino.log('──────────────────────────────────────')

                for (const li of liMatches) {
                    const encrypt = li.match(/encrypt="([^"]+)"/)?.[1]
                    const srvMatch = li.match(/title="Opción\s*(.*?)"/i)
                    const srv = srvMatch
                        ? srvMatch[1].trim().toLowerCase()
                        : 'desconocido'

                    if (!encrypt) {
                        kino.log(`⚠️ ${srv} → sin enlace`)
                        continue
                    }

                    let finalUrl = ''

                    if (encrypt.includes('.eyJs')) {
                        const b64Match = encrypt.match(/\.eyJs(.*?)\./)
                        if (b64Match) {
                            try {
                                const obj = JSON.parse(atob(b64Match[1] + '='))
                                if (obj.link) {
                                    finalUrl = obj.link.startsWith('http')
                                        ? obj.link
                                        : 'https://vww.animeflv.one' + obj.link
                                }
                            } catch (e) { }
                        }
                    }

                    if (
                        !finalUrl &&
                        /^[0-9a-fA-F]+$/.test(encrypt) &&
                        encrypt.length % 2 === 0
                    ) {
                        try {
                            const decoded = decodeHex(encrypt)
                            if (decoded.startsWith('http')) {
                                finalUrl = decoded
                            }
                        } catch (e) { }
                    }

                    if (!finalUrl) {
                        kino.log(`⚠️ ${srv} → URL no reconocida`)
                        continue
                    }

                    const unsupportedMatch = unsupportedDomains.find(domain =>
                        finalUrl.includes(domain)
                    )

                    if (unsupportedMatch) {
                        kino.log(`❌ ${srv} → ${unsupportedMatch}`)
                        kino.log(`   ${finalUrl}`)
                        continue
                    }

                    const normalizedUrl = normalizeServerUrl(finalUrl)

                    kino.log(`✅ ${srv} → aceptado`)
                    kino.log(`   ${normalizedUrl}`)

                    validUrls.push({
                        url: normalizedUrl,
                        server: srv
                    })
                }

                kino.log('──────────────────────────────────────')
                kino.log(`✅ Aceptados: ${validUrls.length}`)
                kino.log(`❌ Rechazados: ${liMatches.length - validUrls.length}`)
                kino.log('══════════════════════════════════════')
            }
        } catch (e) {
            kino.log(`⚠️ Error obteniendo servidores: ${e.message}`)
        }
    }

    // ================================================================
    // FILTRAR Y PRIORIZAR SERVIDORES
    // ================================================================
    const filteredUrls = validUrls.filter(item => {
        const srvLower = item.server.toLowerCase();
        const isKnown = SERVER_PRIORITY.some(key => srvLower.includes(key.toLowerCase()));
        //const isByseOrFilemoon = srvLower.includes('filemoon') || srvLower.includes('byse') || item.url.includes('filemoon') || item.url.includes('byse');
        //return isKnown || isByseOrFilemoon;
        return isKnown;
    });

    if (filteredUrls.length === 0) {
        kino.log('❌ NINGÚN SERVIDOR COINCIDE CON LOS RESOLVEDORES DISPONIBLES');
        throw kino.error('not_found', 'No hay servidores compatibles disponibles para este episodio.');
    }

    filteredUrls.sort((a, b) => {
        const getIndex = (srv) => {
            const lowerSrv = srv.toLowerCase();
            for (let i = 0; i < SERVER_PRIORITY.length; i++) {
                if (lowerSrv.includes(SERVER_PRIORITY[i].toLowerCase())) return i;
            }
            return 999; // Filemoon/byse u otros van al final
        };
        return getIndex(a.server) - getIndex(b.server);
    });

    kino.log('📋 SERVIDORES FILTRADOS Y PRIORIZADOS:');
    filteredUrls.forEach((item, idx) => kino.log(`  ${idx + 1}. [${item.server}] ${item.url}`));
    kino.log('══════════════════════════════════════');
    // ================================================================

    const testHeaders = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
        Referer: 'https://vww.animeflv.one/'
    }

    kino.log('')
    kino.log('🚀 PROBANDO SERVIDORES ACEPTADOS')
    kino.log('══════════════════════════════════════')

    // CAMBIO: Iteramos sobre filteredUrls en lugar de validUrls
    for (const { url: testUrl, server } of filteredUrls) {
        kino.log(`🔍 [PRUEBA] ${server} | ${testUrl}`)
        const resolverKey = Object.keys(RESOLVERS).find(key => server.toLowerCase().includes(key.toLowerCase()));
        //este se puede quitar ya qu eno lo estamos usando arriba
        const isByseOrFilemoon = server.includes('filemoon') || server.includes('byse') || testUrl.includes('filemoon') || testUrl.includes('byse');
        try {
            let isValid = true
            let isHead = false



            if (!resolverKey && !isByseOrFilemoon) {
                continue
            }

            if (server.includes('okru') || server.includes('mail.ru')) {
                isValid = await isVideoValid(testUrl, testHeaders)
                if (!isValid) {
                    kino.log(`❌ [RECHAZADO] ${server} → contenido bloqueado/eliminado`)
                    continue
                }
            }

            if (server.includes('mega')) {
                isValid = await isMegaValid(testUrl, testHeaders)
                if (!isValid) {
                    kino.log(`❌ [MEGA RECHAZADO] ${server}`)
                    continue
                }
            }

            let headResponse
            try {
                headResponse = await kino.fetch(testUrl, {
                    method: 'HEAD',
                    headers: testHeaders,
                    timeoutMs: 10000
                })
                kino.log(`   ↳ [HEAD] Status: ${headResponse.status}`)
            } catch (e) {
                kino.log(`   ⚠️ [HEAD] Falló o no soportado`)
            }

            if (headResponse && (headResponse.ok || headResponse.status === 301 || headResponse.status === 302)) {
                kino.log(`🎉 [ÉXITO] ${server} [URL] ${testUrl}`)
                isHead = true

            }

            if (!isHead) {
                kino.log(`   ↳ [GET] Intentando validar...`)
                const getResponse = await kino.fetch(testUrl, {
                    method: 'GET',
                    headers: testHeaders,
                    timeoutMs: 15000
                })
                kino.log(`   ↳ [GET] Status: ${getResponse.status}`)

                if (getResponse.ok || getResponse.status === 301 || getResponse.status === 302) {
                    kino.log(`🎉 [ÉXITO] Servidor válido: ${server} url: ${testUrl}`)


                } else {
                    kino.log(`❌ [FALLÓ] ${server} → ${getResponse.status}`)
                    continue
                }
            }

        } catch (e) {
            kino.log(`💥 [EXCEPCIÓN] ${server} → ${e.message}`)
            continue
        }



        // ==================================================
        // Aplicamos los resolvedores
        //===============================================
        // EJECUCIÓN DEL RESOLVEDOR (GET exitoso)
        // if (isByseOrFilemoon) {
        //     kino.log(`🎬 [${testUrl}] Extrayendo streaming Filemoon/Byse...`)
        //     try {
        //         const result = await extractStreamingByse(testUrl);
        //         if (result && result.sources && result.sources.length > 0) {
        //             return { url: result.sources[0].file || result.sources[0].url, headers: { Referer: 'https://vww.animeflv.one/' } };
        //         }
        //     } catch (e) {
        //         kino.log(`❌ [FALLÓ] extractStreamingByse: ${e.message}`);
        //         continue;
        //     }
        // } else if (resolverKey) {
        //     kino.log(`⚙️ [${server}] Ejecutando resolvedor: ${resolverKey}`);
        //     try {
        //         const resolvedData = await RESOLVERS[resolverKey](testUrl);
        //         if (resolvedData && resolvedData.url) return resolvedData;
        //     } catch (e) {
        //         kino.log(`❌ [FALLÓ] ${resolverKey}: ${e.message}`);
        //         continue;
        //     }
        // }

        if (resolverKey) {
            kino.log(`⚙️ [${server}] Ejecutando resolvedor: ${resolverKey}`);
            try {
                const resolvedData = await RESOLVERS[resolverKey](testUrl);
                if (resolvedData && resolvedData.url) return resolvedData;
            } catch (e) {
                kino.log(`❌ [FALLÓ] ${resolverKey}: ${e.message}`);
                continue;
            }
        }





    }

    // ================================================================
    // RESPALDO: DATA-DWN
    // ================================================================

    kino.log('')
    kino.log('🔄 BUSCANDO RESPALDO data-dwn...')

    const dwnMatch = html.match(/data-dwn=(.*?)(?:>Descargar<|>\s*<i)/)

    if (dwnMatch) {
        let cleanBlock = dwnMatch[1].replace(/&quot;/g, '"')
        const foundUrls = cleanBlock.match(/https?:[^\s"'\]\},]+/g) || []
        kino.log(`📋 URLs encontradas en data-dwn: ${foundUrls.length}`)

        for (let dirtyUrl of foundUrls) {
            let cleanUrl = dirtyUrl.replace(/\\\//g, '/')
            const unsupportedMatch = unsupportedDomains.find(domain => cleanUrl.includes(domain))

            if (unsupportedMatch) {
                kino.log(`❌ [data-dwn] ${unsupportedMatch}`)
                continue
            }

            cleanUrl = normalizeServerUrl(cleanUrl)
            kino.log(`✅ [data-dwn] ${cleanUrl}`)

            if (cleanUrl.includes('mega.nz')) {
                const isValid = await isMegaValid(cleanUrl, testHeaders)
                if (!isValid) {
                    kino.log(`❌ [data-dwn] Mega rechazado`)
                    continue
                }
            }

            try {
                const testResponse = await kino.fetch(cleanUrl, {
                    method: 'HEAD',
                    headers: testHeaders,
                    timeoutMs: 10000
                })

                if (testResponse.ok || testResponse.status === 301 || testResponse.status === 302) {
                    kino.log(`🎉 [data-dwn] Servidor válido`)
                    return { url: cleanUrl }
                }
            } catch (e) {
                kino.log(`⚠️ [data-dwn] Error: ${e.message}`)
            }
        }
    }

    kino.log('══════════════════════════════════════')
    kino.log('❌ NINGÚN SERVIDOR DISPONIBLE')
    kino.log('══════════════════════════════════════')

    throw kino.error(
        'not_found',
        'Ningún servidor respondió correctamente. Intenta con otro episodio.'
    )
}


// ==========================================
// VOE
// ===========================================


// Voe hides its sources in a JSON-wrapped string: rot13, junk markers, base64, a -3 char shift,
// reversed, base64 again.
function voeDecode(packed) {
    let s = packed.replace(/[a-zA-Z]/g, (c) => {
        const base = c <= "Z" ? 65 : 97;
        return String.fromCharCode(((c.charCodeAt(0) - base + 13) % 26) + base);
    });
    for (const junk of ["@$", "^^", "~@", "%?", "*~", "!!", "#&"]) s = s.split(junk).join("");
    s = atob(s);
    let shifted = "";
    for (let i = 0; i < s.length; i++) shifted += String.fromCharCode(s.charCodeAt(i) - 3);
    return JSON.parse(atob(shifted.split("").reverse().join("")));
}

function voeSourceFrom(html) {
    const packed = html.match(/<script type="application\/json">\s*\[\s*"([^"]+)"\s*\]\s*<\/script>/);
    if (packed) {
        const data = voeDecode(packed[1]);
        if (data && data.source) return data.source;
    }
    // Older Voe pages.
    const hls = html.match(/["']hls["']\s*:\s*["']([^"']+)["']/);
    if (hls) {
        const v = hls[1];
        return v.startsWith("http") ? v : atob(v);
    }
    return null;
}

// Voe: voe.sx answers with a JS redirect to a mirror domain that rotates now and then; the
// mirror holds the player. The HLS lives on a CDN whose domain rotates too (hence streamHosts).
async function resolverUrlVoe(embedUrl) {
    let url = embedUrl;
    for (let hop = 0; hop < 3; hop++) {
        const r = await kino.fetch(url, { headers: { "User-Agent": BROWSER_UA } });
        if (!r.ok) throw new Error("voe HTTP " + r.status);
        const html = await r.text();
        const source = voeSourceFrom(html);
        if (source) {
            return {
                url: source,
                mime: "application/vnd.apple.mpegurl",
                headers: { "User-Agent": BROWSER_UA },
                expiresInSeconds: 3 * 3600,
            };
        }
        const next = html.match(/window\.location\.href\s*=\s*['"]([^'"]+)['"]/);
        if (!next) break;
        url = next[1];
    }
    throw new Error("voe: no source in page");
}




// ==========================================
// mp4upload
// ===========================================
function decodeEscapes(s) {
    return String(s).replace(/\\u([0-9a-fA-F]{4})/g, (_, h) => String.fromCharCode(parseInt(h, 16))).replace(/\\\//g, "/");
}
// MP4Upload: one progressive MP4 (AV1 10-bit 1080p today) on a:183-style hosts that only answer
// with the site's Referer.
async function resolverUrlMp4Upload(embedUrl) {
    const r = await kino.fetch(embedUrl, { headers: { "User-Agent": BROWSER_UA } });
    if (!r.ok) throw new Error("mp4upload HTTP " + r.status);
    const html = await r.text();
    const m =
        html.match(/src\s*:\s*["'](https?:\/\/[^"']+\.mp4[^"']*)["']/i) ||
        html.match(/["']?file["']?\s*:\s*["'](https?:\/\/[^"']+)["']/i) ||
        html.match(/<source[^>]+src\s*=\s*["'](https?:\/\/[^"']+)["']/i);
    if (!m) throw new Error("mp4upload: no video in embed");
    return {
        url: decodeEscapes(m[1]),
        mime: "video/mp4",
        headers: { Referer: "https://www.mp4upload.com/", "User-Agent": BROWSER_UA },
        expiresInSeconds: 3600,
    };
}