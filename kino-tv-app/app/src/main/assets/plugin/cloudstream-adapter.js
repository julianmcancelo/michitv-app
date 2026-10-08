// Kino adapter for one CloudStream plugin running in the CloudStream bridge. Generated per plugin by
// CloudStreamPluginConverter; it never fetches: kino.cloudstream (granted only to these records) does the work.
// Every export starts with `await null`: a throw before the first await aborts the whole call in this engine.
var FACTS = __CLOUDSTREAM_FACTS__;

// An NSFW plugin (FACTS.adult): every item it gives is marked adult, so Kino shows it only while the app's 18+ code is unlocked.
var ADULT = FACTS.adult === true;

// Allowlists: a type that is in none of them (live, peer-to-peer, custom media...) is dropped from results. NSFW is a
// movie only in an NSFW plugin; any other plugin drops it. Live entries are En vivo channels (liveChannels), never titles.
// Audio (apiVersion 8): Music and Audio are music (an album, playlist or track), Podcast and AudioBook are podcasts.
var MOVIE_TYPES = { Movie: 1, AnimeMovie: 1, Documentary: 1 };
var SERIES_TYPES = { TvSeries: 1, Anime: 1, OVA: 1, Cartoon: 1, AsianDrama: 1, Others: 1 };
var AUDIO_KINDS = { Music: "music", Audio: "music", Podcast: "podcast", AudioBook: "podcast" };

// "movie", "series", "music", "podcast", or null when the title must not be shown.
function kindOf(type) {
  if (!type) return "series";
  if (MOVIE_TYPES[type] === 1) return "movie";
  if (SERIES_TYPES[type] === 1) return "series";
  if (Object.prototype.hasOwnProperty.call(AUDIO_KINDS, type)) return AUDIO_KINDS[type];
  if (type === "NSFW" && ADULT) return "movie";
  return null;
}
function isAudioType(type) { return !!type && Object.prototype.hasOwnProperty.call(AUDIO_KINDS, type); }
// A ref is [provider, value, kind]: "t" a title (value = its url), "e" an episode (value = its load-links data, often a URL too),
// "c" a live channel (value = its url).
function refOf(provider, value, kind) { return JSON.stringify([provider, value, kind]); }
function parseRef(ref) { var a = JSON.parse(ref); return { provider: a[0] | 0, value: String(a[1]), kind: a[2] === "e" || a[2] === "c" ? a[2] : "t" }; }
function idOf(provider, url) {
  var h = 0; for (var i = 0; i < url.length; i++) { h = ((h << 5) - h + url.charCodeAt(i)) | 0; }
  return "p" + provider + "-" + (h >>> 0).toString(36);
}
function item(provider, it) {
  var kind = kindOf(it.type);
  if (kind === null || !it.url) return null;
  var out = { id: idOf(provider, it.url), ref: refOf(provider, it.url, "t"), title: it.name || "Sin título", kind: kind };
  if (it.poster) out.poster = it.poster;
  if (it.year) out.year = it.year;
  if (ADULT) out.adult = true;
  return out;
}
// The bridge's `load` answers, kept a few minutes per title: the title page's `details`, then `resolve` (or `episodes`)
// right after it, pay for one `load`, not two. Small and short-lived: a site's page may change. Never kept: an answer
// with no episodes (a site hiccup must not stick for minutes) or a long one (the runtime's memory is small); `fresh`
// (a resolve Kino retries) always asks again.
var LOADS = [];
var MAX_LOADS = 3, MAX_CACHED_EPISODES = 500, LOAD_TTL_MS = 10 * 60 * 1000;
async function loadOf(provider, url, fresh) {
  var k = provider + " " + url, now = Date.now();
  for (var i = 0; i < LOADS.length; i++) {
    if (LOADS[i].k !== k) continue;
    if (!fresh && now - LOADS[i].at < LOAD_TTL_MS) return LOADS[i].v;
    LOADS.splice(i, 1);
    break;
  }
  var v = await kino.cloudstream.load(provider, url);
  var n = v && v.episodes ? v.episodes.length : 0;
  if (n > 0 && n <= MAX_CACHED_EPISODES) {
    LOADS.push({ k: k, at: now, v: v });
    if (LOADS.length > MAX_LOADS) LOADS.shift();
  }
  return v;
}
// What Kino's title page shows about a title (its SeriesInfo shape): everything the bridge's `load().title` carries.
function detailsOf(t) {
  var d = {};
  if (!t) return d;
  if (t.name) d.title = t.name;
  if (t.plot) d.overview = t.plot;
  if (t.poster) d.poster = t.poster;
  if (t.background) d.backdrop = t.background;
  if (t.tags && t.tags.length) d.genres = t.tags.slice(0, 10);
  if (t.year) d.year = String(t.year);
  if (typeof t.rating === "number" && t.rating > 0 && t.rating <= 10) d.rating = t.rating;
  if (t.durationMin > 0) d.runtimeMinutes = t.durationMin;
  var ids = idsOf(t.ids);
  if (ids) d.ids = ids;
  return d;
}
// The title's ids from the plugin's own sync data (the bridge's `load().title.ids`, newer bridges only): what lets Kino's
// own pipeline (TMDB, AniList, the person's meta plugins) describe the title like any other. Only well-formed ones.
function idsOf(x) {
  if (!x || typeof x !== "object") return null;
  var out = {}, any = false;
  if (typeof x.imdb === "string" && /^tt\d{5,10}$/.test(x.imdb)) { out.imdb = x.imdb; any = true; }
  var nums = ["tmdb", "mal", "anilist", "kitsu"];
  for (var i = 0; i < nums.length; i++) {
    var v = x[nums[i]];
    if (typeof v === "string" && /^\d{1,10}$/.test(v)) v = Number(v);
    if (typeof v === "number" && v > 0 && v === Math.floor(v)) { out[nums[i]] = v; any = true; }
  }
  return any ? out : null;
}
// No ids from the plugin: TMDB, through Kino's own `kino.tmdb` (the person's key; feature-detected), searched by the title's
// name and year, and an id taken ONLY on a confident match: one result whose title (or original title) is the same once
// accents, case and punctuation are dropped, in the same year. Anything else (no year, no match, two matches, no key, an
// error) is no id: never a guess. Remembered on the cached load answer, so the page and the play ask once.
function plainName(x) {
  return String(x || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().replace(/[^a-z0-9]+/g, "");
}
// The search never costs a listing: `episodes` never searches (it only reuses an outcome `details` already found), and
// `details` searches only while its own 20 s limit can hold the search's worst case (kino.tmdb gives up after 15 s): a
// slow `load` leaves the title without a TMDB id rather than without its details. No JS-side race: the engine finishes a
// call only once every async job it started has ended, so a timer raced against the search would hold every call.
var DETAILS_LIMIT_MS = 20000, TMDB_WORST_MS = 15000, TMDB_MARGIN_MS = 2000;
async function tmdbIdFor(t, series) {
  if (typeof kino.tmdb !== "function" || !t || !t.name || !t.year) return null;
  var year = String(t.year), name = plainName(t.name);
  if (!name) return null;
  var r;
  try {
    r = await kino.tmdb(series ? "/search/tv" : "/search/movie",
      series ? { query: t.name, first_air_date_year: t.year, language: "es-MX" } : { query: t.name, year: t.year, language: "es-MX" });
  } catch (e) { return null; }
  var found = 0, hits = 0, list = (r && r.results) || [];
  for (var i = 0; i < list.length; i++) {
    var x = list[i];
    var date = String((series ? x.first_air_date : x.release_date) || "");
    if (date.slice(0, 4) !== year) continue;
    var a = plainName(series ? x.name : x.title), b = plainName(series ? x.original_name : x.original_title);
    if (a !== name && b !== name) continue;
    if (typeof x.id === "number" && x.id > 0 && x.id !== found) { found = x.id; hits++; }
  }
  return hits === 1 ? found : null;
}
// The search's outcome per title (an id, or null for no match, an error or the bound), kept as long as a load answer:
// the page and the next visits ask once, whatever the outcome. Never asked by resolve.
var TMDB_IDS = [];
var MAX_TMDB_IDS = 20;
function rememberedTmdb(k) {
  var now = Date.now();
  for (var i = 0; i < TMDB_IDS.length; i++) {
    if (TMDB_IDS[i].k !== k) continue;
    if (now - TMDB_IDS[i].at < LOAD_TTL_MS) return TMDB_IDS[i];
    TMDB_IDS.splice(i, 1);
    break;
  }
  return null;
}
// An adult (18+) title is never looked up anywhere: no TMDB search, and no ids handed to Kino (so TMDB, AniList and the
// person's meta plugins are never asked about it).
function adultTitle(t) { return ADULT || (t && t.type === "NSFW"); }
// [detailsOf] for a loaded title, with a confident TMDB id when the plugin gave no ids at all ([tmdbIdFor]). [search]: may
// ask TMDB now (only `details`, within its time); otherwise only an outcome already found is used.
async function describedTitle(provider, url, info, search) {
  var d = detailsOf(info.title);
  if (adultTitle(info.title)) { delete d.ids; return d; }
  if (d.ids || isAudioType(info.title.type)) return d;
  var k = provider + " " + url, known = rememberedTmdb(k);
  if (!known && search) {
    var series = kindOf(info.title.type) === "series";
    known = { k: k, at: Date.now(), id: await tmdbIdFor(info.title, series) };
    TMDB_IDS.push(known);
    if (TMDB_IDS.length > MAX_TMDB_IDS) TMDB_IDS.shift();
  }
  if (known && known.id) d.ids = { tmdb: known.id };
  return d;
}
function items(provider, list) { var out = []; for (var i = 0; i < list.length; i++) { var x = item(provider, list[i]); if (x) out.push(x); } return out; }

export async function search(query) {
  await null;
  // A provider listing only Live has no titles: its channels are found by liveSearch, so it is never asked here.
  var ps = [];
  for (var p = 0; p < FACTS.providers.length; p++) if (!FACTS.providers[p].onlyLive) ps.push(p);
  var out = [];
  for (var k = 0; k < ps.length; k++) {
    try { out = out.concat(items(ps[k], (await kino.cloudstream.search(ps[k], query.q)).items)); }
    catch (e) { if (ps.length === 1 || (e && e.code === "needs_bridge")) throw e; }
  }
  return out;
}

export async function home() {
  await null;
  var rows = [];
  for (var p = 0; p < FACTS.providers.length; p++) {
    var pages = FACTS.providers[p].mainPages;
    // A provider listing only Live has nothing for Home: its pages are En vivo categories.
    if (!FACTS.providers[p].hasMainPage || FACTS.providers[p].onlyLive) continue;
    for (var i = 0; i < pages.length && i < 12; i++) {
      try {
        var r = await kino.cloudstream.mainPage(p, i, 1);
        for (var j = 0; j < r.rows.length; j++) {
          var its = items(p, r.rows[j].items);
          if (its.length) rows.push({ id: "p" + p + "-" + i + "-" + j, title: r.rows[j].name || pages[i].name, items: its, ref: JSON.stringify([p, i, j]) });
        }
      } catch (e) { /* one broken section never hides the others */ }
    }
  }
  return rows;
}

export async function browse(ref, cursor) {
  await null;
  // [provider, page] or [provider, page, row]: one main page can answer several rows, each its own Home row and "Ver más";
  // a ref without the row (older adapters') browses every row of the page.
  var a = JSON.parse(ref), page = cursor ? (cursor | 0) : 2, row = a.length > 2 ? (a[2] | 0) : -1;
  var r = await kino.cloudstream.mainPage(a[0] | 0, a[1] | 0, page);
  var its = []; for (var j = 0; j < r.rows.length; j++) if (row < 0 || j === row) its = its.concat(items(a[0] | 0, r.rows[j].items));
  return r.hasNext ? { items: its, next: String(page + 1) } : { items: its };
}

export async function episodes(ref) {
  await null;
  var r = parseRef(ref);
  var info = await loadOf(r.provider, r.value);
  // An album's tracks or a podcast's episodes come as episodes too: a track without its own art shows the cover, and a
  // single track (a one-episode list) carries the title's name and length. The title's tags are genres, never an artist.
  var audio = isAudioType(info.title.type), single = audio && info.episodes.length === 1;
  var eps = [];
  for (var i = 0; i < info.episodes.length; i++) {
    var e = info.episodes[i];
    var ep = { number: e.episode || (i + 1), ref: refOf(r.provider, e.data, "e") };
    if (e.season) ep.season = e.season;
    if (e.name) ep.title = e.name; else if (single && info.title.name) ep.title = info.title.name;
    if (e.poster) ep.still = e.poster; else if (audio && info.title.poster) ep.still = info.title.poster;
    if (e.description) ep.overview = e.description;
    if (single && info.title.durationMin > 0) ep.runtimeMinutes = info.title.durationMin;
    eps.push(ep);
  }
  var series = await describedTitle(r.provider, r.value, info, false);
  series.title = info.title.name;
  return { series: series, episodes: eps };
}

// A movie's title page (Kino's optional `details` export): the same `load` resolve uses, from the cache when it is fresh.
export async function details(ref) {
  await null;
  var r = parseRef(ref);
  if (r.kind !== "t") return null;
  var started = Date.now();
  var info = await loadOf(r.provider, r.value);
  return describedTitle(r.provider, r.value, info, Date.now() - started + TMDB_WORST_MS + TMDB_MARGIN_MS <= DETAILS_LIMIT_MS);
}

export async function resolve(ref, options) {
  await null;
  var retry = !!(options && options.retry);
  var r = parseRef(ref);
  var data = r.value;
  // A title ref carries the title url: its playable data comes from load(), which gives a movie as one episode. A title
  // listed as a movie with several (an NSFW title, a series a plugin mislabels) plays its first one.
  // A channel ref carries the channel url and plays the same way. An episode ref already carries the data, whatever it looks like.
  var what = r.kind === "c" ? "este canal" : "este título";
  if (r.kind !== "e") {
    var info = await loadOf(r.provider, data, retry);
    if (!info.episodes.length) throw kino.error("not_found", "Esta fuente no encontró un enlace para " + what);
    data = info.episodes[0].data;
  }
  var got = await kino.cloudstream.loadLinks(r.provider, data);
  if (!got.links.length) throw kino.error("not_found", "Esta fuente no encontró enlaces para " + what);
  var best = got.links[0];
  var out = { url: best.url, headers: best.headers, label: best.name };
  if (best.linkType === "M3U8") out.mime = "application/x-mpegURL";
  if (best.linkType === "DASH") out.mime = "application/dash+xml";
  var alts = [];
  for (var i = 1; i < got.links.length && alts.length < 8; i++) {
    var l = got.links[i];
    var a = { url: l.url, headers: l.headers, label: l.name };
    if (l.linkType === "M3U8") a.mime = "application/x-mpegURL";
    if (l.linkType === "DASH") a.mime = "application/dash+xml";
    alts.push(a);
  }
  if (alts.length) out.alternatives = alts;
  var subs = [];
  for (var s = 0; s < got.subtitles.length && subs.length < 30; s++) subs.push({ lang: got.subtitles[s].lang, url: got.subtitles[s].url });
  if (subs.length) out.subtitles = subs;
  return out;
}

// En vivo (capability "channels", only while some provider lists Live): each main page of a Live provider is a category,
// its Live entries are channels, played through resolve() with a "c" ref. Limits are contract.json's `live` ones.
function liveProviders() { var out = []; for (var p = 0; p < FACTS.providers.length; p++) if (FACTS.providers[p].live) out.push(p); return out; }
function channel(provider, it) {
  if (!it || it.type !== "Live" || !it.url) return null;
  var ch = { id: idOf(provider, it.url), title: it.name || "Canal", ref: refOf(provider, it.url, "c") };
  if (it.poster) ch.logo = it.poster;
  if (ADULT) ch.adult = true;
  return ch;
}

export async function liveCategories() {
  await null;
  var cats = [];
  var ps = liveProviders();
  for (var k = 0; k < ps.length && cats.length < 200; k++) {
    // Without a main page (hasMainPage false) its pages can't be listed: such a provider is reached by liveSearch only.
    var p = ps[k], pages = FACTS.providers[p].hasMainPage ? (FACTS.providers[p].mainPages || []) : [];
    for (var i = 0; i < pages.length && cats.length < 200; i++) {
      var c = { id: "p" + p + "-" + i, title: pages[i].name || FACTS.providers[p].name };
      if (ADULT) c.adult = true;
      cats.push(c);
    }
  }
  return cats;
}

export async function liveChannels(arg) {
  await null;
  var m = /^p(\d+)-(\d+)$/.exec(String(arg.categoryId));
  if (!m) return { items: [] };
  var p = m[1] | 0, i = m[2] | 0, page = Math.max(1, arg.cursor ? (arg.cursor | 0) : 1);
  var pr = FACTS.providers[p];
  if (!pr || !pr.live || !pr.hasMainPage || i >= (pr.mainPages || []).length) return { items: [] };
  var r;
  // A page that fails is an empty page: one broken category never breaks En vivo.
  try { r = await kino.cloudstream.mainPage(p, i, page); } catch (e) { return { items: [] }; }
  var items = [];
  // A malformed page is an empty one, like a failed page.
  var rows = (r && r.rows) || [];
  for (var j = 0; j < rows.length; j++) for (var n = 0, its = (rows[j] && rows[j].items) || []; n < its.length && items.length < 500; n++) {
    var ch = channel(p, its[n]);
    if (!ch) continue;
    ch.categoryId = arg.categoryId;
    items.push(ch);
  }
  return r && r.hasNext ? { items: items, next: String(page + 1) } : { items: items };
}

export async function liveSearch(arg) {
  await null;
  var out = [], ps = liveProviders();
  for (var k = 0; k < ps.length && out.length < 100; k++) {
    try {
      var r = await kino.cloudstream.search(ps[k], arg.query);
      for (var n = 0; n < r.items.length && out.length < 100; n++) {
        var ch = channel(ps[k], r.items[n]);
        if (!ch) continue;
        // A hit names no category: a plain plugin says so, so it never counts as 18+ when the categories can't be read.
        if (!ADULT) ch.adult = false;
        out.push(ch);
      }
    } catch (e) { /* one broken provider never hides the others */ }
  }
  return out;
}
