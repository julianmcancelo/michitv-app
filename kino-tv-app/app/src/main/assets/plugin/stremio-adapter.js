// Kino plugin generated from a Stremio addon. The placeholders below are replaced by StremioPluginConverter.
// Every export starts with `await null`: a throw before the first await aborts the whole call in this engine.

var __F = __STREMIO_FACTS__;
var __TMDB_API = "https://api.themoviedb.org/3";
var __TMDB_IMAGES = "https://image.tmdb.org/t/p/";
var __TMDB_KEY = "__TMDB_KEY_MARKER__";
var __ONLY_P2P = "__ONLY_P2P__";
var __NO_STREAMS = "__NO_STREAMS__";
var __CATALOG_ONLY = "__CATALOG_ONLY__";
var __NOT_THIS_TITLE = "__NOT_THIS_TITLE__";
var __PAGE_MAX = 100;

// An 18+ addon (behaviorHints.adult): every title, channel and live section it gives is marked 18+, so Kino shows them only
// while the person's 18+ code is unlocked on that device and plays them without a trace.
var __ADULT = __F.adult === true;

// Where the addon answers /stream and /subtitles, as Stremio decides which addon to ask: each scope lists the content
// `types` and the id `prefixes` it accepts, null meaning any. No stream scope at all: a catalog-only addon (Cinemeta, Kitsu)
// that plays nothing. Facts without the lists (older ones, tests) mean any type and any id: the behaviour before them.
var __STREAMS = Array.isArray(__F.streams) ? __F.streams : [{ types: null, prefixes: null }];
var __SUBS = Array.isArray(__F.subs) ? __F.subs : (__F.hasSubtitles ? [{ types: null, prefixes: null }] : []);

// Kino shows anime as `series` (mapMeta's kind), and anime addons declare either word: the two are one kind here.
var __SAME_KIND = { series: "anime", anime: "series" };

function typeIn(scope, type) {
  if (!scope.types) return true;
  var t = String(type).toLowerCase();
  return scope.types.indexOf(t) >= 0 || (!!__SAME_KIND[t] && scope.types.indexOf(__SAME_KIND[t]) >= 0);
}

// The type to ask `scopes` for an item of `type` with `id`: its own, unless the covering scope lists only its twin
// (an anime-only addon is asked /stream/anime/... for an episode Kino calls series).
function askedType(scopes, type, id) {
  var t = String(type).toLowerCase(), twin = __SAME_KIND[t];
  if (!twin) return type;
  var own = scopes.some(function (s) { return (!s.types || s.types.indexOf(t) >= 0) && idIn(s, id); });
  if (own) return type;
  return scopes.some(function (s) { return s.types && s.types.indexOf(twin) >= 0 && idIn(s, id); }) ? twin : type;
}

function idIn(scope, id) {
  return !scope.prefixes || scope.prefixes.some(function (p) { return String(id).indexOf(p) === 0; });
}

function covers(scopes, type, id) { return scopes.some(function (s) { return typeIn(s, type) && idIn(s, id); }); }

// Whether the addon would be asked for an IMDb id (what a TMDB title becomes) of `type`: before the id is known.
function takesImdb(scopes, type) {
  return scopes.some(function (s) {
    return typeIn(s, type) && (!s.prefixes || s.prefixes.some(function (p) { return /^tt\d*$/.test(p) || "tt".indexOf(p) === 0; }));
  });
}

// The addon's base address, put together from the person's two settings: the server (`addonUrl`) and the rest of the
// path (`addonPath`, which carries the addon's own configuration, e.g. a debrid key). Never stored anywhere else.
function base() {
  var origin = String(kino.config.get("addonUrl") || "").replace(/\/+$/, "");
  if (!origin) throw kino.error("unavailable", "Falta la dirección del addon");
  var path = String(kino.config.get("addonPath") || "").replace(/^\/+|\/+$/g, "");
  return origin + (path ? "/" + path : "");
}

// The address (server, path, both together) can carry the person's debrid key, and a failed fetch's message may quote the
// URL it asked for (an invalid-URL or connection error does). Nothing derived from a caught error is logged or rethrown
// without passing through here.
function safeMessage(e) {
  var m;
  try { m = (e && e.message !== undefined) ? String(e.message) : String(e); } catch (x) { m = ""; }
  var secrets = [];
  try { secrets.push(base()); } catch (x) {}
  var origin = String(kino.config.get("addonUrl") || "").replace(/\/+$/, "");
  var path = String(kino.config.get("addonPath") || "").replace(/^\/+|\/+$/g, "");
  if (origin) secrets.push(origin);
  if (path) { secrets.push(path); secrets.push(encodeURIComponent(path)); path.split("/").forEach(function (seg) { if (seg.length >= 4) secrets.push(seg); }); }
  secrets.sort(function (a, b) { return b.length - a.length; });
  secrets.forEach(function (sec) { if (sec) m = m.split(sec).join("<addon>"); });
  return m;
}

// The hosts already named by refusedHostLine in this runtime: one line per host, however many requests hit it.
var __refusedHosts = {};

// A request the addon redirected to a host this plugin may not reach (kino.fetch's `host_not_allowed`). Kino discovers (and
// declares, at install and on "Buscar actualización") only the hosts the addon's CATALOGS redirect to: for a catalog this is a
// new one that update will ask the person for; for anything else (meta, streams, subtitles) the line only names the host.
// One line per host, naming the HOST only: just a host name is taken out of the message, never a path or a query.
function refusedHostLine(e, path) {
  if (!e || e.code !== "host_not_allowed") return;
  var m = /host no permitido: ([A-Za-z0-9.-]{1,253})/.exec(safeMessage(e));
  var host = m ? m[1].toLowerCase() : "";
  // The person's own server is never named (like the rest of its address).
  var own = /^[a-z]+:\/\/([^\/:?#]+)/i.exec(String(kino.config.get("addonUrl") || ""));
  if (own && own[1].toLowerCase() === host) host = "";
  if (__refusedHosts[host]) return;
  __refusedHosts[host] = 1;
  var catalog = String(path || "").indexOf("/catalog/") === 0;
  console.log("[Kino] the addon sent a request on to " + (host || "a server") + ", which this plugin is not approved for" +
    (catalog ? ": a new catalog host, \"Buscar actualización\" asks the person for it"
             : " (Kino only discovers the hosts the addon's catalogs redirect to)"));
}

async function getJson(path, timeoutMs) {
  var r;
  try {
    r = await kino.fetch(base() + path, { headers: { Accept: "application/json" }, timeoutMs: timeoutMs || 12000 });
  } catch (e) {
    refusedHostLine(e, path);
    throw kino.error((e && e.code) || "unavailable", safeMessage(e));
  }
  if (!r.ok) throw kino.error(r.status === 404 ? "not_found" : "unavailable", safeMessage("El addon respondió " + r.status));
  try { return r.json(); } catch (e) { throw kino.error("unavailable", "El addon respondió algo que no es JSON"); }
}

function hash6(s) {
  var h = 5381;
  for (var i = 0; i < s.length; i++) h = ((h * 33) ^ s.charCodeAt(i)) >>> 0;
  return ("000000" + h.toString(16)).slice(-6);
}

// A Stremio id as a Kino item id: legal characters only; an altered one gets a short hash so two ids never collide.
function slug(id) {
  var s = String(id);
  if (/^[A-Za-z0-9._~-]{1,100}$/.test(s)) return s;
  return s.replace(/[^A-Za-z0-9._~-]/g, "_").slice(0, 100) + "-" + hash6(s);
}

function num(v) { var n = parseFloat(v); return isFinite(n) ? n : undefined; }
function year(v) { var m = /(\d{4})/.exec(String(v || "")); return m ? m[1] : undefined; }
function minutes(v) { var m = /(\d+)/.exec(String(v || "")); var n = m ? parseInt(m[1], 10) : 0; return n >= 1 && n <= 1000 ? n : undefined; }
function http(u) { return (typeof u === "string" && /^https?:\/\//i.test(u)) ? u : undefined; }
function imdbOf(id) { return /^tt\d{5,10}$/.test(String(id)) ? String(id) : undefined; }

// An addon meta preview as a Kino item. `ref` carries the addon's own type (an `anime` stays `anime`: /meta is asked
// exactly where Stremio would ask it) and id for episodes/resolve; `kind` is Kino's.
function mapMeta(m, fallbackType) {
  if (!m || !m.id || !m.name) return null;
  var type = String(m.type || fallbackType || "movie");
  var kind = (type === "series" || type === "anime") ? "series" : "movie";
  var item = {
    id: slug(m.id), ref: JSON.stringify({ t: type, id: String(m.id) }),
    title: String(m.name), kind: kind, poster: http(m.poster), backdrop: http(m.background),
    year: year(m.releaseInfo || m.year || m.released), overview: m.description ? String(m.description) : undefined,
    genres: Array.isArray(m.genres) ? m.genres.slice(0, 5).map(String) : undefined,
    runtimeMinutes: minutes(m.runtime),
  };
  if (__ADULT) item.adult = true;
  var rating = num(m.imdbRating);
  if (rating !== undefined && rating >= 0 && rating <= 10) item.rating = rating;
  var ids = idsOf(m, m.id);
  if (ids) item.ids = ids;
  return item;
}

// The ids Kino can look the title up by: IMDb, and TMDB when the addon names it (Cinemeta's `moviedb_id`).
function idsOf(m, id) {
  var ids = {}, imdb = imdbOf(m.imdb_id || id), tmdbId = Number(m.moviedb_id);
  if (imdb) ids.imdb = imdb;
  if (typeof m.moviedb_id !== "boolean" && tmdbId > 0 && tmdbId <= 2147483647 && Math.floor(tmdbId) === tmdbId) ids.tmdb = tmdbId;
  return (ids.imdb || ids.tmdb) ? ids : undefined;
}

var __TYPE_WORDS = { movie: "Películas", series: "Series", anime: "Anime" };

// A Home row's title carries its type the way Kino's own rows do ("Popular · Películas"): one addon often names a movie
// and a series catalog alike (Cinemeta: "Popular" twice), and only the type tells them apart. Left alone when the name
// already says it, or the type is one Kino has no word for.
function rowTitle(c) {
  var name = String(c.name || c.id), word = __TYPE_WORDS[String(c.type).toLowerCase()];
  if (!word) return name;
  var lower = name.toLowerCase();
  if (lower.indexOf(word.toLowerCase()) >= 0 || lower.indexOf(String(c.type).toLowerCase()) >= 0) return name;
  return name + " · " + word;
}

function catalogPath(c, skip, search) {
  var extra = [];
  if (search) extra.push("search=" + encodeURIComponent(search));
  if (skip > 0) extra.push("skip=" + skip);
  return "/catalog/" + encodeURIComponent(c.type) + "/" + encodeURIComponent(c.id) + (extra.length ? "/" + extra.join("&") : "") + ".json";
}

async function loadCatalog(c, skip, search) {
  var body = await getJson(catalogPath(c, skip, search));
  var metas = (body && Array.isArray(body.metas)) ? body.metas : [];
  var mapped = metas.map(function (m) { return mapMeta(m, c.type); });
  return { items: mapped.filter(Boolean), mapped: mapped, count: metas.length };
}

export async function home() {
  await null;
  var rows = await Promise.all(__F.rows.slice(0, 20).map(function (c, i) {
    return loadCatalog(c, 0, null).then(function (p) {
      return { id: "c" + i + "-" + slug(c.type + "-" + c.id), title: rowTitle(c), ref: JSON.stringify({ c: c.type + "/" + c.id }), items: p.items.slice(0, 60) };
    }).catch(function (e) {
      console.log("[Kino] catalog " + c.type + "/" + c.id + " failed: " + safeMessage(e));
      return null;
    });
  }));
  return rows.filter(function (r) { return r && r.items.length; });
}

// The last page browse answered per catalog: an addon that ignores `skip` answers the same page again and again.
var __lastBrowse = {};  // catalog -> { skip, sig } of the last page answered
var __SKIP_MAX = 20000;

export async function browse(ref, cursor) {
  await null;
  var r = (typeof ref === "string") ? JSON.parse(ref) : ref;
  var parts = String((r && r.c) || "").split("/");
  var cat = { type: parts[0], id: parts.slice(1).join("/") };
  var skip = cursor ? parseInt(cursor, 10) || 0 : 0;
  var page = await loadCatalog(cat, skip, null);
  // Stremio pages by "how many already": skip=N. Only the first __PAGE_MAX metas are shown, so next counts only those.
  var shown = Math.min(page.count, __PAGE_MAX);
  var items = page.mapped.slice(0, shown).filter(Boolean);
  var sig = items.map(function (it) { return it.id; }).join("|");
  var key = cat.type + "/" + cat.id;
  // Only a request for a DIFFERENT skip that answers the identical page proves the addon ignores skip; the same
  // cursor asked twice (retry, prefetch) must answer the same page both times.
  var last = __lastBrowse[key];
  var repeated = skip > 0 && sig !== "" && !!last && last.skip !== skip && last.sig === sig;
  if (!repeated) __lastBrowse[key] = { skip: skip, sig: sig };
  if (repeated) return { items: [] };
  var out = { items: items };
  // Stop on an empty page, on a page that adds nothing new, and past a sane depth.
  if (shown > 0 && skip + shown < __SKIP_MAX) out.next = String(skip + shown);
  return out;
}

// ---- TMDB (copied from the Nuvio adapter): the language, one request helper, artwork ----

function tmdbLanguage() {
  var lang = String(kino.lang || "");
  return lang.toLowerCase().indexOf("es") === 0 ? "es-MX" : (lang || "en-US");
}

async function tmdb(path, timeoutMs, params) {
  var opts = { headers: { Accept: "application/json" } };
  if (timeoutMs) opts.timeoutMs = timeoutMs;
  var r = await kino.fetch(__TMDB_API + path + "?api_key=" + encodeURIComponent(__TMDB_KEY) +
    "&language=" + encodeURIComponent(tmdbLanguage()) + (params || ""), opts);
  if (!r.ok) throw kino.error(r.status === 404 ? "not_found" : "unavailable", "TMDB respondió " + r.status);
  return r.json();
}

function tmdbImage(size, path) { return path ? __TMDB_IMAGES + size + path : undefined; }

// TMDB id -> IMDb id (what addons key their streams by). "movie" or "tv". `timeoutMs`: 6 s unless the caller is tighter.
async function imdbForTmdb(kind, tmdbId, timeoutMs) {
  var ext = await tmdb("/" + kind + "/" + tmdbId + "/external_ids", timeoutMs || 6000);
  var imdb = imdbOf(ext && ext.imdb_id);
  if (!imdb) throw kino.error("not_found", "TMDB no conoce el código IMDb de este título");
  return imdb;
}

function mediaKind(type, season, episode) {
  if (type === "series" || type === "tv") return "tv";
  if (type === "movie") return "movie";
  return (season > 0 && episode > 0) ? "tv" : "movie";
}

function tmdbArt(m) {
  return {
    year: year(m.release_date || m.first_air_date), poster: tmdbImage("w500", m.poster_path),
    backdrop: tmdbImage("w1280", m.backdrop_path), overview: m.overview || undefined,
  };
}

// A TMDB title as an item. A series with no episode chosen is a `series` item; otherwise a playable `movie` item.
function tmdbItem(tmdbId, kind, title, art, season, episode) {
  if (kind === "tv" && !(season > 0 && episode > 0)) {
    var show = Object.assign({ id: tmdbId + "-series", ref: JSON.stringify({ tmdbId: tmdbId, type: "series" }), title: title, kind: "series", ids: { tmdb: tmdbId } }, art);
    if (__ADULT) show.adult = true;
    return show;
  }
  var type = kind === "tv" ? "series" : "movie";
  var item = Object.assign({
    id: tmdbId + "-" + type + "-" + (kind === "tv" ? season : 0) + "-" + (kind === "tv" ? episode : 0),
    ref: JSON.stringify({ tmdbId: tmdbId, type: type, season: kind === "tv" ? season : 0, episode: kind === "tv" ? episode : 0 }),
    title: title, kind: "movie",
  }, art);
  if (kind === "movie") item.ids = { tmdb: tmdbId };
  if (__ADULT) item.adult = true;
  return item;
}

// A typed search with no addon search catalog: TMDB's own search, one request (copied from the Nuvio adapter).
async function tmdbTextSearch(query) {
  var q = String(query.q || "").trim();
  if (!q) return [];
  var season = query.season || 0, episode = query.episode || 0;
  // Only what the addon can play: a TMDB title it would never be asked to stream is not one of its titles.
  var kinds = (query.type === "series" ? ["tv"] : ["movie", "tv"]).filter(function (k) { return takesImdb(__STREAMS, k === "tv" ? "series" : "movie"); });
  if (!kinds.length) return [];
  var path = kinds.length > 1 ? "/search/multi" : "/search/" + kinds[0];
  var body;
  try {
    body = await tmdb(path, 8000, "&query=" + encodeURIComponent(q) + "&include_adult=false&page=1");
  } catch (e) {
    console.log("[Kino] TMDB search failed: " + safeMessage(e));
    return [];
  }
  var matches = [];
  ((body && Array.isArray(body.results)) ? body.results : []).forEach(function (m) {
    if (!m || typeof m.id !== "number" || m.id <= 0) return;
    var kind = kinds.length > 1 ? m.media_type : kinds[0];
    if (kinds.indexOf(kind) < 0) return;
    var title = kind === "movie" ? (m.title || m.original_title) : (m.name || m.original_name);
    if (!title) return;
    var original = kind === "movie" ? m.original_title : m.original_name;
    matches.push({ m: m, kind: kind, title: String(title), original: original ? String(original) : "" });
  });
  matches.sort(function (a, b) { return (Number(b.m.popularity) || 0) - (Number(a.m.popularity) || 0); });
  var forms = [q, query.originalTitle].concat(Array.isArray(query.altTitles) ? query.altTitles : [])
    .filter(function (t) { return typeof t === "string" && t.trim().length > 0; });
  matches = kino.rank.sortBySimilarity(matches, forms, function (x) { return [x.title, x.original]; });
  return matches.slice(0, 10).map(function (x) { return tmdbItem(x.m.id, x.kind, x.title, tmdbArt(x.m), season, episode); });
}

// A search inside one Home row's "Ver más" (Kino's scopedSearch, `within` = the row's ref): only a row whose catalog takes
// the `search` extra ("s" in the facts) asks the addon, paged by skip like browse; any other answers null, and Kino then
// filters the row's pages itself. A row narrowed by anything else than its catalog never gets here (refs carry only `c`).
async function searchWithin(query) {
  var r = null;
  try { r = (typeof query.within === "string") ? JSON.parse(query.within) : query.within; } catch (e) { r = null; }
  var key = String((r && r.c) || "");
  var row = __F.rows.filter(function (c) { return c.s === true && c.type + "/" + c.id === key; })[0];
  var q = String(query.q || "").trim();
  if (!row) return null;
  if (!q) return { items: [] };
  var skip = query.cursor ? parseInt(query.cursor, 10) || 0 : 0;
  var page = await loadCatalog(row, skip, q);
  var shown = Math.min(page.count, __PAGE_MAX);
  var out = { items: page.mapped.slice(0, shown).filter(Boolean) };
  if (shown > 0 && skip + shown < __SKIP_MAX) out.next = String(skip + shown);
  return out;
}

export async function search(query) {
  await null;
  if (query.within !== undefined && query.within !== null) return searchWithin(query);
  if (!query.tmdbId) {
    var q = String(query.q || "").trim();
    if (!q) return [];
    var cats = __F.search.filter(function (c) { return query.type === "series" ? c.type !== "movie" : (query.type === "movie" ? c.type !== "series" : true); });
    if (cats.length) {
      var pages = await Promise.all(cats.slice(0, 3).map(function (c) {
        return loadCatalog(c, 0, q).catch(function (e) {
          console.log("[Kino] search catalog " + c.type + "/" + c.id + " failed: " + safeMessage(e));
          return { items: [] };
        });
      }));
      var seen = {}, items = [];
      pages.forEach(function (p) { p.items.forEach(function (it) { if (!seen[it.id]) { seen[it.id] = 1; items.push(it); } }); });
      if (items.length) return items.slice(0, 100);
    }
    return tmdbTextSearch(query);
  }
  var season = query.season || 0, episode = query.episode || 0;
  var kind = mediaKind(query.type, season, episode);
  // A title card (or "Ver otras fuentes"): only an addon that plays this kind by IMDb id has it. A catalog-only addon
  // (Cinemeta) answers nothing here -- it would list a title it can never play.
  if (!takesImdb(__STREAMS, kind === "tv" ? "series" : "movie")) return [];
  var meta = null;
  try { meta = await tmdb("/" + kind + "/" + query.tmdbId, 4000); } catch (e) {
    console.log("[Kino] TMDB detail failed: " + safeMessage(e));
    meta = null;
  }
  var m = meta || {};
  var title = query.q || query.originalTitle || m.title || m.name || "";
  return [tmdbItem(query.tmdbId, kind, title, tmdbArt(m), season, episode)];
}

function metaVideos(meta) {
  var list = [], seenIds = {}, seenNumbers = {};
  (Array.isArray(meta.videos) ? meta.videos : []).forEach(function (v) {
    if (!v || !v.id) return;
    var s = parseInt(v.season, 10), e = parseInt(v.episode !== undefined ? v.episode : v.number, 10);
    if (!(s > 0) || !(e > 0)) return;
    // The app turns an out-of-range season into 1, which would collide with a real season 1 episode (daily shows use the year).
    if (s > 999 || e > 99999) return;
    var key = s + ":" + e;
    if (seenIds[String(v.id)] || seenNumbers[key]) return;
    seenIds[String(v.id)] = 1; seenNumbers[key] = 1;
    list.push({
      season: s, number: e, ref: JSON.stringify({ t: "series", id: String(v.id) }),
      title: (v.title || v.name) ? String(v.title || v.name) : undefined, still: http(v.thumbnail),
      overview: v.overview || v.description ? String(v.overview || v.description) : undefined,
      airDate: /^\d{4}-\d{2}-\d{2}/.test(String(v.released || "")) ? String(v.released).slice(0, 10) : undefined,
    });
  });
  list.sort(function (a, b) { return a.season - b.season || a.number - b.number; });
  return list;
}

async function episodesFromTmdb(r) {
  var imdb = await imdbForTmdb("tv", r.tmdbId);
  var show = await tmdb("/tv/" + r.tmdbId, 8000);
  var numbers = (show.seasons || []).map(function (s) { return s.season_number; }).filter(function (n) { return typeof n === "number" && n > 0; });
  var seasons = await Promise.all(numbers.map(function (n) { return tmdb("/tv/" + r.tmdbId + "/season/" + n, 8000).catch(function () { return null; }); }));
  var today = new Date().toISOString().slice(0, 10);
  var list = [];
  seasons.forEach(function (s, i) {
    if (!s) return;
    (s.episodes || []).forEach(function (e) {
      if (typeof e.episode_number !== "number" || e.episode_number < 1) return;
      if (e.air_date && e.air_date > today) return;
      list.push({
        season: numbers[i], number: e.episode_number,
        ref: JSON.stringify({ t: "series", id: imdb + ":" + numbers[i] + ":" + e.episode_number }),
        title: e.name || undefined, still: tmdbImage("w300", e.still_path), overview: e.overview || undefined,
        airDate: e.air_date || undefined, runtimeMinutes: e.runtime || undefined,
      });
    });
  });
  if (numbers.length && !seasons.some(function (s) { return s; })) throw kino.error("unavailable", "TMDB no dio ninguna temporada");
  return {
    series: Object.assign({ title: show.name || undefined, ids: { tmdb: r.tmdbId, imdb: imdb },
      genres: (show.genres || []).map(function (g) { return g.name; }).filter(Boolean) }, tmdbArt(show)),
    episodes: list,
  };
}

// The addon's /meta for `id`, asked under `type`. A series/anime the addon does not know under that type is asked once
// more under its twin: a ref saved before anime items kept their own type says `series` for a Kitsu anime.
async function metaOf(type, id) {
  var path = function (t) { return "/meta/" + encodeURIComponent(t) + "/" + encodeURIComponent(id) + ".json"; };
  var twin = __SAME_KIND[type.toLowerCase()], body;
  try {
    body = await getJson(path(type));
  } catch (e) {
    if (!twin || !e || e.code !== "not_found") throw e;
    return ((await getJson(path(twin))) || {}).meta || {};
  }
  if (twin && !(body && body.meta)) {
    var other = await getJson(path(twin)).catch(function () { return null; });
    if (other && other.meta) return other.meta;
  }
  return (body && body.meta) || {};
}

export async function episodes(ref) {
  await null;
  var r = (typeof ref === "string") ? JSON.parse(ref) : ref;
  if (r.tmdbId && !r.id) return episodesFromTmdb(r);
  if (!__F.hasMeta) throw kino.error("unavailable", "Este addon no tiene capítulos");
  var meta = await metaOf(String(r.t || "series"), r.id);
  var list = metaVideos(meta);
  if (!list.length) throw kino.error("not_found", "El addon no lista capítulos de este título");
  var series = { title: meta.name ? String(meta.name) : undefined, poster: http(meta.poster), backdrop: http(meta.background),
    overview: meta.description ? String(meta.description) : undefined, year: year(meta.releaseInfo || meta.released),
    genres: Array.isArray(meta.genres) ? meta.genres.slice(0, 5).map(String) : undefined };
  var ids = idsOf(meta, r.id);
  if (ids) series.ids = ids;
  return { series: series, episodes: list };
}

// ---- Title details for other titles (Kino's `meta` capability) ----

// Where the addon answers /meta: like __STREAMS. Facts without the list (older ones) mean none: never asked.
var __METAS = Array.isArray(__F.metas) ? __F.metas : [];

// A meta's `links` of one category (Stremio's "imdb", "Genres", "Cast"…), case aside.
function metaLinks(m, category) {
  return (Array.isArray(m.links) ? m.links : []).filter(function (l) {
    return l && typeof l === "object" && String(l.category || "").toLowerCase() === category;
  });
}

// The IMDb rating as Kino's `ratings`: `imdbRating`, else the "imdb" link's name (where AIOMetadata-style addons put it).
function metaRatings(m) {
  var raw = m.imdbRating;
  if (raw === undefined || raw === null || raw === "") { var l = metaLinks(m, "imdb")[0]; raw = l && l.name; }
  var n = num(raw);
  return (n !== undefined && n >= 0 && n <= 10) ? [{ source: "imdb", value: String(Math.round(n * 10) / 10) }] : [];
}

// The cast as Kino's: `app_extras.cast` ({ name, character, photo }), else `cast` (names or such objects), else the "Cast"
// links' names; 20 at most, each name once.
function metaCast(m) {
  var extras = m.app_extras && Array.isArray(m.app_extras.cast) ? m.app_extras.cast : null;
  var list = extras || (Array.isArray(m.cast) ? m.cast : metaLinks(m, "cast"));
  var out = [], seen = {};
  list.forEach(function (c) {
    var name = typeof c === "string" ? c : (c && typeof c === "object" ? c.name : null);
    name = name == null ? "" : String(name).trim();
    if (!name || seen[name] || out.length >= 20) return;
    seen[name] = 1;
    var member = { name: name.slice(0, 60) };
    if (c && typeof c === "object") {
      if (c.character) member.character = String(c.character).slice(0, 60);
      var photo = http(c.photo);
      if (photo) member.photo = photo;
    }
    out.push(member);
  });
  return out;
}

// Kino asks only to FILL what TMDB and AniList left empty on a title's info page (or for a title TMDB does not know, a
// kitsu: id). The query carries every id Kino knows; the first the addon covers is asked, as Stremio would.
export async function meta(query) {
  await null;
  var q = (typeof query === "string") ? JSON.parse(query) : (query || {});
  if (!__METAS.length) return null;
  var type = q.type === "series" ? "series" : "movie";
  var ids = q.ids || {};
  var candidates = [];
  if (q.id) candidates.push(String(q.id));
  if (ids.imdb) candidates.push(String(ids.imdb));
  if (ids.tmdb) candidates.push("tmdb:" + ids.tmdb);
  if (ids.kitsu) candidates.push("kitsu:" + ids.kitsu);
  if (ids.mal) candidates.push("mal:" + ids.mal);
  if (ids.anilist) candidates.push("anilist:" + ids.anilist);
  var id = null;
  for (var i = 0; i < candidates.length && !id; i++) if (covers(__METAS, type, candidates[i])) id = candidates[i];
  if (!id) return null;
  var body;
  try {
    body = await getJson("/meta/" + encodeURIComponent(askedType(__METAS, type, id)) + "/" + encodeURIComponent(id) + ".json", 6000);
  } catch (e) {
    if (e && e.code === "not_found") return null;
    throw e;
  }
  var m = body && body.meta;
  if (!m || typeof m !== "object") return null;
  var out = {
    title: m.name ? String(m.name) : undefined, overview: m.description ? String(m.description) : undefined,
    poster: http(m.poster), backdrop: http(m.background), year: year(m.releaseInfo || m.year || m.released),
    genres: Array.isArray(m.genres) ? m.genres.slice(0, 5).map(String) : undefined, runtimeMinutes: minutes(m.runtime),
  };
  var logo = http(m.logo);
  if (logo) out.logo = logo;
  var ratings = metaRatings(m);
  if (ratings.length) out.ratings = ratings;
  var cast = metaCast(m);
  if (cast.length) out.cast = cast;
  if (type === "series") {
    out.episodes = metaVideos(m).map(function (v) {
      var ref = JSON.parse(v.ref);
      return { season: v.season, number: v.number, title: v.title, overview: v.overview, still: v.still, airDate: v.airDate, id: ref.id };
    });
  }
  return out;
}

// ---- Streams ----

// A stream with any of these is peer-to-peer, Usenet or an archive: nothing Kino can play.
var __P2P_KEYS = ["infoHash", "nzbUrl", "rarUrls", "zipUrls", "tgzUrls", "tarUrls", "servers"];

function isP2pLike(s) { return __P2P_KEYS.some(function (k) { return s[k] !== undefined && s[k] !== null; }); }

// Only a plain http(s) `url` can be played; YouTube and external-page streams carry no media file.
function isPlayable(s) {
  return !!s && typeof s === "object" && !isP2pLike(s) && !!http(s.url) &&
    (s.ytId === undefined || s.ytId === null) && (s.externalUrl === undefined || s.externalUrl === null);
}

// Only what the addon says ABOUT the stream: its url is not evidence of resolution or audio (".../720/", "4k.", "dts" tokens).
function streamText(s) {
  return [s.name, s.title, s.description].map(function (v) { try { return v == null ? "" : String(v); } catch (e) { return ""; } }).join(" ").toLowerCase();
}

// Lower is better. Web ready, then HLS/MP4, then resolution (2160p last: most devices cannot decode it), then no HEVC+DTS,
// then 8-bit over 10-bit (H.264 High 10 "Hi10P", common in anime: many phones have no decoder for it).
function rankStream(s) {
  var t = streamText(s), r = 0;
  if (s.behaviorHints && s.behaviorHints.notWebReady) r += 1000;
  var res = /(^|[^a-z0-9])(2160p?|4k|uhd)([^a-z0-9]|$)/.test(t) ? 3 : /(^|[^a-z0-9])(1080p|fhd)([^a-z0-9]|$)/.test(t) ? 0 : /(^|[^a-z0-9])720p([^a-z0-9]|$)/.test(t) ? 1 : 2;
  r += res * 10;
  var hevc = /hevc|x265|h\.?265/.test(t), badAudio = /(^|[^a-z0-9])(dts(?![a-z])|ddp(?![a-z])|dd\+)|truehd|e-?ac-?3|atmos/.test(t);
  if (hevc && badAudio) r += 5;
  if (/(^|[^a-z0-9])(10.?bits?|hi10p?)([^a-z0-9]|$)/.test(t)) r += 2;
  if (/\.m3u8|\.mp4/.test(String(s.url).toLowerCase())) r -= 1;
  return r;
}

// The direct streams best first; ties keep the addon's own order (addons list their best first). Kino plays the first and
// moves on to the next by itself when one cannot play on the device (Stream `alternatives`).
function rankStreams(playable) {
  return playable
    .map(function (s, i) { return { s: s, i: i, r: rankStream(s) }; })
    .sort(function (a, b) { return a.r - b.r || a.i - b.i; })
    .map(function (x) { return x.s; });
}

// A stream's `url`, `headers` and `mime` as Kino takes them.
function streamFields(s) {
  var out = { url: s.url };
  var hints = s.behaviorHints || {};
  var headers = hints.proxyHeaders && hints.proxyHeaders.request;
  if (headers && typeof headers === "object" && !Array.isArray(headers)) {
    var h = {};
    Object.keys(headers).filter(function (k) { return typeof headers[k] === "string"; }).slice(0, 20).forEach(function (k) { h[k] = headers[k]; });
    if (Object.keys(h).length) out.headers = h;
  }
  var path = String(s.url).split(/[?#]/)[0].toLowerCase();
  if (/\.m3u8$/.test(path)) out.mime = "application/vnd.apple.mpegurl"; else if (/\.mpd$/.test(path)) out.mime = "application/dash+xml";
  return out;
}

var __LANG = { eng: "en", spa: "es", por: "pt", fra: "fr", fre: "fr", deu: "de", ger: "de", ita: "it", rus: "ru", jpn: "ja", kor: "ko", zho: "zh", chi: "zh", ara: "ar", hin: "hi", tur: "tr", pol: "pl", nld: "nl", dut: "nl", swe: "sv", pob: "pt", spn: "es" };

// A Stremio language tag as Kino's: three-letter codes to two, and a translator's machine-translated mark -- GTSubs answers
// "esgt" for Spanish made by Google Translate -- split off as `gt`.
function subLang(raw) {
  var r = String(raw || "und").toLowerCase(), m = /^([a-z]{2,3})gt$/.exec(r);
  // Not a language tag at all (Subtis says "⚡ SUBTIS ⚡"): the addon's own language, when its name or description says one.
  if (!/^[a-z]{2,3}(-[a-z0-9]{2,8})?$/.test(r) && !m) return { lang: __F.subtitleLang || "und", gt: false };
  var base = m && !__LANG[r] ? m[1] : r;
  return { lang: __LANG[base] || base.slice(0, 20), gt: !!(m && !__LANG[r]) };
}

// The host of an http(s) URL, lowercase, or "".
function hostOf(u) { var m = /^[a-z]+:\/\/([^\/:?#]+)/i.exec(String(u || "")); return m ? m[1].toLowerCase() : ""; }

// A track the addon translated by machine: marked so (a "gt" language), or any track of an addon that says it translates
// (__F.translator) -- and, either way, a file on the addon's own server: what it hands over from OpenSubtitles stays the original.
function isTranslated(x, url) {
  if (!subLang(x && x.lang).gt && !__F.translator) return false;
  var own = hostOf(kino.config.get("addonUrl"));
  return !!own && hostOf(url) === own;
}

// A Stremio subtitles list as Kino's: 30 at most, one per URL. `labels`: each also named by its release (OpenSubtitles'
// `movieReleaseName` / `subtitleFileName`) and marked `translated` when the addon machine-translated it, for the
// subtitles() answer -- a Stream's subtitles have neither.
function mapSubtitles(list, labels) {
  var out = [], seen = {};
  (Array.isArray(list) ? list : []).forEach(function (x) {
    var url = x && http(x.url);
    if (!url || seen[url] || out.length >= 30) return;
    seen[url] = 1;
    var sub = { lang: subLang(x.lang).lang, url: url };
    var path = url.split(/[?#]/)[0].toLowerCase();
    if (/\.vtt$/.test(path)) sub.format = "vtt"; else if (/\.srt$/.test(path)) sub.format = "srt";
    if (labels) {
      var name = String(x.movieReleaseName || x.subtitleFileName || "").replace(/\.(srt|vtt|sub|ass)$/i, "").trim();
      if (name) sub.label = name.slice(0, 60);
      if (isTranslated(x, url)) sub.translated = true;
    }
    out.push(sub);
  });
  return out;
}

// Stremio types that play as live channels (StremioManifest.LIVE_TYPES): no subtitles, no episodes. A station of a `music`
// radio catalog is marked by its ref instead (`l: 1`, see liveChannels).
function isLiveType(t) { return t === "tv" || t === "channel" || t === "radio"; }

// The Stremio type a ref is asked under, known before any request.
function targetType(r) {
  if (r.id) return r.t || "movie";
  return mediaKind(r.type, r.season, r.episode) === "tv" ? "series" : "movie";
}

// Which stream request a ref means: {type, id}. A TMDB-origin ref is turned into an IMDb id here.
async function streamTarget(r) {
  if (r.id) return { type: targetType(r), id: String(r.id), raw: r };
  var kind = mediaKind(r.type, r.season, r.episode);
  if (kind === "tv" && !(r.season > 0 && r.episode > 0)) throw kino.error("not_found", "falta elegir temporada y capítulo");
  var imdb = await imdbForTmdb(kind, r.tmdbId);
  return kind === "tv" ? { type: "series", id: imdb + ":" + r.season + ":" + r.episode, raw: r } : { type: "movie", id: imdb, raw: r };
}

export async function resolve(ref) {
  await null;
  var r = (typeof ref === "string") ? JSON.parse(ref) : ref;
  // Never a /stream request the addon does not answer: a catalog-only addon plays nothing (Kino says so and offers the
  // title's other sources), and an id outside its types or prefixes is not one Stremio would ask it about.
  if (!__STREAMS.length) throw kino.error("not_found", __CATALOG_ONLY);
  var type = targetType(r);
  if (!__STREAMS.some(function (s) { return typeIn(s, type); })) throw kino.error("not_found", __NOT_THIS_TITLE);
  if (!r.id && !takesImdb(__STREAMS, type)) throw kino.error("not_found", __NOT_THIS_TITLE);
  var target = await streamTarget(r);
  if (!covers(__STREAMS, target.type, target.id)) throw kino.error("not_found", __NOT_THIS_TITLE);
  var enc = encodeURIComponent(target.id);
  var body = await getJson("/stream/" + encodeURIComponent(askedType(__STREAMS, target.type, target.id)) + "/" + enc + ".json", 20000);
  var streams = (body && Array.isArray(body.streams)) ? body.streams : [];
  var p2p = streams.filter(function (s) { return s && typeof s === "object" && isP2pLike(s); }).length;
  var playable = streams.filter(isPlayable);
  // Counts only: a stream URL, a header value or a subtitle URL can carry the person's debrid key.
  console.log("[Kino] streams: " + streams.length + " from the addon; dropped " + p2p + " p2p-like; " + playable.length + " playable");
  if (!playable.length) {
    if (p2p > 0) throw kino.error("unavailable", __ONLY_P2P);
    throw kino.error("not_found", __NO_STREAMS);
  }
  var ranked = rankStreams(playable), best = ranked[0];
  var out = streamFields(best);
  var alternatives = ranked.slice(1, 9).map(streamFields);
  if (alternatives.length) out.alternatives = alternatives;
  var subs = mapSubtitles(best.subtitles);
  if (!isLiveType(target.type) && r.l !== 1 && covers(__SUBS, target.type, target.id)) {
    try {
      var sb = await getJson("/subtitles/" + encodeURIComponent(askedType(__SUBS, target.type, target.id)) + "/" + enc + ".json", 5000);
      subs = mapSubtitles(subs.concat(sb && Array.isArray(sb.subtitles) ? sb.subtitles : []));
    } catch (e) { console.log("[Kino] subtitles failed: " + safeMessage(e)); }
  }
  if (subs.length) out.subtitles = subs;
  return out;
}

// The extra of a subtitles request for the playing file, as Stremio sends it ("/videoHash=…&videoSize=…&filename=…"): only
// what Kino knows of it (`file: { hash?, size?, name? }`), URL-encoded; "" when nothing is, so the path stays the plain one.
function subtitlesExtra(file) {
  var f = (file && typeof file === "object") ? file : {}, extra = [];
  if (typeof f.hash === "string" && /^[0-9a-f]{16}$/.test(f.hash)) extra.push("videoHash=" + encodeURIComponent(f.hash));
  var size = Number(f.size);
  if (size > 0 && Math.floor(size) === size) extra.push("videoSize=" + encodeURIComponent(String(size)));
  if (typeof f.name === "string" && f.name) {
    // Some addons answer only with both (Subtis: a name and a size, "0" accepted): an unknown size goes as 0 beside a name.
    if (!(size > 0 && Math.floor(size) === size)) extra.push("videoSize=0");
    extra.push("filename=" + encodeURIComponent(f.name));
  }
  return extra.length ? "/" + extra.join("&") : "";
}

// Kino's online subtitle search for a movie or an episode it knows by id (any source, not only this addon's titles):
// { imdbId?, tmdbId?, kind: "movie" | "series", season?, episode?, file? }. Asked only where Stremio would ask the addon (its
// subtitles types and id prefixes); a TMDB-only title becomes an IMDb id first. Nothing to ask, or nothing found: [].
export async function subtitles(args) {
  await null;
  var a = args || {};
  var type = a.kind === "series" ? "series" : "movie";
  var season = parseInt(a.season, 10), episode = parseInt(a.episode, 10);
  if (type === "series" && !(season > 0 && episode > 0)) return [];
  if (!__SUBS.some(function (s) { return typeIn(s, type); })) return [];
  var imdb = imdbOf(a.imdbId);
  if (!imdb) {
    var tmdbId = parseInt(a.tmdbId, 10);
    if (!(tmdbId > 0) || !takesImdb(__SUBS, type)) return [];
    // Both requests fit Kino's 10 s for the whole call: 4 s for TMDB, 5 s for the addon.
    try { imdb = await imdbForTmdb(type === "series" ? "tv" : "movie", tmdbId, 4000); } catch (e) { return []; }
  }
  var id = type === "series" ? imdb + ":" + season + ":" + episode : imdb;
  if (!covers(__SUBS, type, id)) return [];
  var body;
  try {
    body = await getJson("/subtitles/" + encodeURIComponent(askedType(__SUBS, type, id)) + "/" + encodeURIComponent(id) + subtitlesExtra(a.file) + ".json", 5000);
  } catch (e) {
    if (e && e.code === "not_found") return [];
    throw e;
  }
  // Kino keeps 30 tracks: the person's languages first (an addon lists dozens of languages), in their order.
  var langs = Array.isArray(a.languages) ? a.languages.map(function (l) { return String(l).toLowerCase(); }) : [];
  var rank = function (x) {
    var i = langs.indexOf(subLang(x && x.lang).lang);
    return i < 0 ? langs.length : i;
  };
  // A translator's notice tracks (GTSubs' "info:<lang>", a two-hour cue explaining how to use it) are no subtitles.
  var list = (body && Array.isArray(body.subtitles) ? body.subtitles : [])
    .filter(function (x) { return !(x && /^info:/.test(String(x.id || ""))); })
    .map(function (x, i) { return { x: x, i: i }; });
  list.sort(function (p, q) { return (rank(p.x) - rank(q.x)) || (p.i - q.i); });
  var found = mapSubtitles(list.map(function (p) { return p.x; }), true);
  console.log("[Kino] subtitles: " + found.length + " from the addon");
  return found;
}

// ---- Live channels: Stremio `tv` / `channel` / `radio` catalogs ----

function liveCategoryId(c) { return slug(c.type + "-" + c.id); }

export async function liveCategories() {
  await null;
  var seen = {}, out = [];
  __F.live.forEach(function (c) {
    var id = liveCategoryId(c);
    if (out.length >= 200 || seen[id]) return;
    seen[id] = 1;
    var title = String(c.name || c.id);
    // A radio section says so ("Top · Radio"), the way a Home row names its type: En vivo lists it among TV channels.
    if (String(c.type).toLowerCase() === "radio" && !/radio/i.test(title)) title += " · Radio";
    // A `music` radio catalog is named for radio already (that is how it was told apart); nothing to add.
    out.push(__ADULT ? { id: id, title: title, adult: true } : { id: id, title: title });
  });
  return out;
}

// A catalog that fails is logged (without the address) and the call fails, so the app caches nothing.
export async function liveChannels(args) {
  await null;
  var cat = null;
  __F.live.forEach(function (c) { if (!cat && liveCategoryId(c) === (args && args.categoryId)) cat = c; });
  if (!cat) return { items: [] };
  var skip = (args && args.cursor) ? parseInt(args.cursor, 10) || 0 : 0;
  var metas;
  try {
    var body = await getJson(catalogPath(cat, skip, null));
    metas = (body && Array.isArray(body.metas)) ? body.metas : [];
  } catch (e) {
    console.log("[Kino] live catalog " + cat.type + "/" + cat.id + " failed: " + safeMessage(e));
    // Throw, never an empty page: the app would show "no channels" on page 1 and cache a truncated list on page 2+.
    throw kino.error("unavailable", "No pude cargar los canales de este addon");
  }
  var shown = metas.slice(0, 500);
  var out = { items: liveItems(cat, shown, {}) };
  if (shown.length > 0 && skip + shown.length < __SKIP_MAX) out.next = String(skip + shown.length);
  return out;
}

// Addon metas as Kino channels; `seen` (id -> 1) is shared across calls that make one answer. A channel's id is the same
// whether it was listed or found by liveSearch: favourites and recents match either way.
function liveItems(cat, metas, seen) {
  var items = [];
  metas.forEach(function (m) {
    if (!m || !m.id || !m.name) return;
    var id = slug(m.id);
    // The app reserves the `~` prefix for its own channels and would silently drop this one.
    if (id.charAt(0) === "~") id = "_" + id.slice(1) + "-" + hash6(String(m.id));
    if (seen[id]) return;
    seen[id] = 1;
    var channel = { id: id, title: String(m.name), ref: JSON.stringify({ t: cat.type, id: String(m.id), l: 1 }), logo: http(m.logo) || http(m.poster) };
    if (__ADULT) channel.adult = true;
    items.push(channel);
  });
  return items;
}

// En vivo's search for channels never listed (a big catalog lists its first pages only): the addon's searchable tv/channel
// catalogs, asked with `search=`, in parallel. One that fails is logged and skipped; all failing fails the call.
export async function liveSearch(args) {
  await null;
  var q = String((args && args.query) || "").trim();
  var cats = Array.isArray(__F.liveSearch) ? __F.liveSearch : [];
  if (!q || !cats.length) return { items: [] };
  var failed = 0;
  var pages = await Promise.all(cats.map(function (c) {
    return getJson(catalogPath(c, 0, q)).then(function (body) {
      return { cat: c, metas: (body && Array.isArray(body.metas)) ? body.metas : [] };
    }).catch(function (e) {
      failed++;
      console.log("[Kino] live search " + c.type + "/" + c.id + " failed: " + safeMessage(e));
      return null;
    });
  }));
  if (failed === cats.length) throw kino.error("unavailable", "No pude buscar canales en este addon");
  var seen = {}, items = [];
  pages.forEach(function (p) { if (p) items = items.concat(liveItems(p.cat, p.metas.slice(0, 100), seen)); });
  return { items: items.slice(0, 100) };
}
