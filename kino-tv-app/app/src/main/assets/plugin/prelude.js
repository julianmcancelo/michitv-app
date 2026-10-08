// The `kino` API a plugin sees, and the JS half of the bridge to Kotlin (PluginRuntime). Loaded
// after web.js, as `(<this file>)(env, L)`: env = { apiVersion, appVersion, lang }, L = the limits
// PluginRuntime passes (every number lives in Kotlin, pinned to docs/plugins/contract.json).
//
// Everything that crosses into Kotlin is capped HERE, before it crosses: the 64 MB memory limit
// bounds only the QuickJS heap, so a string that reached Kotlin would already be copied onto the
// app's heap (a 40 MB `home()` answer was an OOM on a TV box). The built-ins it relies on are
// captured at load so a plugin can't swap them; `__kinoNative` is emptied and frozen (only these
// wrappers hold its functions); every function here is frozen, so its `name` can't be changed.
(function (env, L) {
  'use strict';
  // quickjs-kt defines __kinoNative non-configurable, so it can't be deleted; its functions can
  // (they work unbound). Take them, then leave an empty frozen object.
  const native = globalThis.__kinoNative;
  const n = {};
  for (const k of Object.getOwnPropertyNames(native)) { n[k] = native[k]; delete native[k]; }
  Object.freeze(native);
  const S = String, E = Error, TE = TypeError, P = Promise, stringify = JSON.stringify, parse = JSON.parse;
  const slice = Function.prototype.call.bind(String.prototype.slice);
  // No JS number ever crosses the bridge: every one goes as its String() form and Kotlin parses it
  // (PluginRuntime.wireLong). On a 32-bit build QuickJS NaN-boxes its values, and quickjs-kt
  // alpha13's argument conversion reads a float64's tag without normalising it, so any number that
  // is not an int32 (2591999999, 1.5, a Date.now() result) failed the host call with "unsupported
  // js value type" -- measured on a Fire TV (armeabi-v7a); 64-bit devices and the JVM never saw it.
  const wire = (a) => typeof a === 'number' ? S(a) : a;
  // A failed host call reaches the plugin as a real Error with a message, never a bare null (what
  // a native conversion failure surfaced as).
  const asError = (e) => {
    try { if (e !== null && typeof e === 'object' && typeof e.message === 'string') return e; } catch (_) { /* a hostile getter */ }
    return new E(e === null || e === undefined ? 'a call to Kino failed without saying why' : S(e));
  };
  // A sync host function never throws across the bridge (quickjs-kt would rethrow it at the end of the
  // call even if the plugin caught it): PluginRuntime.hostCall answers L.hostErrorPrefix + message instead,
  // and this turns it back into an ordinary Error the plugin can catch.
  const hostErrorPrefix = L.hostErrorPrefix;
  const asyncNatives = ['fetch', 'sleep', 'browserCapture', 'browserPage', 'cloudstream', 'meta', 'tmdb'];
  for (const k of Object.getOwnPropertyNames(n)) {
    const f = n[k];
    if (typeof f !== 'function') continue;
    // Fixed parameters, never a spread: spreading runs Array.prototype[Symbol.iterator], which plugin code can replace.
    n[k] = asyncNatives.indexOf(k) >= 0
      ? async (a0, a1, a2) => {
        try { return await f(wire(a0), wire(a1), wire(a2)); } catch (e) { throw asError(e); }
      }
      : (a0, a1, a2) => {
        let r;
        try { r = f(wire(a0), wire(a1), wire(a2)); } catch (e) { throw asError(e); }
        if (typeof r === 'string' && slice(r, 0, hostErrorPrefix.length) === hostErrorPrefix) throw new E(slice(r, hostErrorPrefix.length));
        return r;
      };
  }
  const charCodeAt = Function.prototype.call.bind(String.prototype.charCodeAt);
  const fromCharCode = String.fromCharCode;
  const join = Array.prototype.join;
  const define = Object.defineProperty, freeze = Object.freeze, keysOf = Object.keys;
  const defineAll = Object.defineProperties, reflectDefine = Reflect.defineProperty;
  const ownKeys = Reflect.ownKeys, apply = Reflect.apply, ownDescriptor = Object.getOwnPropertyDescriptor;
  const protoOf = Object.getPrototypeOf;
  const isInteger = Number.isInteger;
  const OP = Object.prototype, defineGetter = OP.__defineGetter__, defineSetter = OP.__defineSetter__;
  const hasOwn = Function.prototype.call.bind(OP.hasOwnProperty);
  // Frozen, non-configurable globals from web.js: safe to hold on to.
  const encodeUtf8 = Function.prototype.call.bind(TextEncoder.prototype.encode, new TextEncoder());
  const textDecoder = new TextDecoder();
  const decodeUtf8 = Function.prototype.call.bind(TextDecoder.prototype.decode, textDecoder);
  const toBase64 = btoa, fromBase64 = atob;
  // QuickJS intrinsics: NOT frozen by web.js, so a plugin could reassign Uint8Array or repoint its
  // prototype's methods. Safe to capture only because this whole IIFE runs before plugin.js is even
  // added as a module -- nothing plugin-controlled has executed yet.
  const toUpperCase = Function.prototype.call.bind(String.prototype.toUpperCase);
  const indexOf = Function.prototype.call.bind(Array.prototype.indexOf);
  const subarray = Function.prototype.call.bind(Uint8Array.prototype.subarray);
  const U8 = Uint8Array;

  // A function's `name` is read by quickjs-kt's native code when an error is built or a rejection
  // is tracked in that function's frame; at ~20 MB the native side fails an allocation and
  // crashes the whole process (measured). BEST-EFFORT guard: these define APIs refuse a long name,
  // an accessor name, or making it writable. It is not airtight -- `delete g.name` +
  // `Object.setPrototypeOf` + assignment, or a huge computed key, still produce a huge name -- and
  // the crash sentinel (PluginCrashSentinel) is the backstop. Everything else passes through.
  const nameRefused = () => new TE('a function name can\'t be changed to more than ' + L.maxFunctionNameChars + ' characters');
  const safeNameDescriptor = (desc) => {
    if (desc === null || typeof desc !== 'object') return desc;
    const d = {};
    for (const k of ['value', 'writable', 'enumerable', 'configurable', 'get', 'set']) if (k in desc) d[k] = desc[k];
    // The flags are booleans the way the engine reads them: `writable: 1` is writable.
    for (const k of ['writable', 'enumerable', 'configurable']) if (k in d) d[k] = !!d[k];
    if ('get' in d || 'set' in d || d.writable === true) throw nameRefused();
    if (typeof d.value === 'string' && d.value.length > L.maxFunctionNameChars) throw nameRefused();
    return d;
  };
  const isName = (key) => typeof key !== 'symbol' && S(key) === 'name';
  Object.defineProperty = freeze(function defineProperty(o, key, desc) {
    if (typeof o === 'function' && isName(key)) return define(o, 'name', safeNameDescriptor(desc));
    return define(o, key, desc);
  });
  Object.defineProperties = freeze(function defineProperties(o, props) {
    if (typeof o !== 'function' || props === null || typeof props !== 'object') return defineAll(o, props);
    // Same keys as the native algorithm (own ENUMERABLE ones, string or symbol), each descriptor
    // read once; defined on the copy, never assigned, so "__proto__" stays an ordinary key.
    const copy = {};
    for (const k of ownKeys(props)) {
      const own = ownDescriptor(props, k);
      if (own === undefined || !own.enumerable) continue;
      const desc = props[k];
      define(copy, k, { value: isName(k) ? safeNameDescriptor(desc) : desc, enumerable: true, configurable: true, writable: true });
    }
    return defineAll(o, copy);
  });
  Reflect.defineProperty = freeze(function defineProperty(o, key, desc) {
    // A non-object descriptor goes straight to the native one, which throws TypeError.
    if (typeof o === 'function' && isName(key) && desc !== null && typeof desc === 'object') {
      let safe;
      try { safe = safeNameDescriptor(desc); } catch (e) { if (e instanceof TE) return false; throw e; }
      return reflectDefine(o, 'name', safe);
    }
    return reflectDefine(o, key, desc);
  });
  define(OP, '__defineGetter__', { value: freeze(function __defineGetter__(key, fn) {
    if (typeof this === 'function' && isName(key)) throw nameRefused();
    return apply(defineGetter, this, [key, fn]);
  }), writable: true, configurable: true, enumerable: false });
  define(OP, '__defineSetter__', { value: freeze(function __defineSetter__(key, fn) {
    if (typeof this === 'function' && isName(key)) throw nameRefused();
    return apply(defineSetter, this, [key, fn]);
  }), writable: true, configurable: true, enumerable: false });

  const toStr = (x) => (typeof x === 'string' ? x : S(x));
  const cut = (s, max) => (s.length > max ? slice(s, 0, max) : s);
  const str = (x) => { if (typeof x === 'string') return x; try { return toStr(stringify(x)); } catch (e) { return toStr(x); } };
  const line = (args) => { let out = ''; for (let i = 0; i < args.length && out.length <= L.maxLogChars; i++) out += (i ? ' ' : '') + str(args[i]); return cut(out, L.maxLogChars); };
  const log = (level, args) => n.log(level, line(args));
  // kino.log(...) plus kino.log.report(...): a line that also tells Kino's error board the plugin served a
  // degraded result (Kotlin sends it only when the plugin's telemetry is on, at most once an hour per area).
  const kinoLog = (...a) => log('info', a);
  define(kinoLog, 'report', { value: freeze((...a) => log('report', a)), writable: false, configurable: false, enumerable: true });
  freeze(kinoLog);

  // Typed errors. The name carries the code -- "KinoError_auth_required" -- because a rejection
  // nobody handles never reaches __kinoCall (the engine fails the whole evaluate with it): Kotlin
  // then reads the code from the engine's "<name>: <message>" text.
  // Both parts are short by construction (validated code, message cut to L.maxErrorMessageChars),
  // so native code never formats a plugin-sized name; and the name is a valid JNI class name that
  // names no class, because quickjs-kt hands it to FindClass (a '[' aborted a debug build). See
  // PluginErrors.
  // [a-z_]{1,32}, checked without RegExp: RegExp.prototype.test looks up `exec` at call time, and
  // a plugin that replaced it could pass a 30 MB "code" into an error name.
  const isCode = (c) => {
    if (typeof c !== 'string' || c.length < 1 || c.length > 32) return false;
    for (let i = 0; i < c.length; i++) { const x = charCodeAt(c, i); if (!((x >= 97 && x <= 122) || x === 95)) return false; }
    return true;
  };
  const codedError = (code, message) => {
    const c = isCode(code) ? code : 'unknown';
    let m;
    try { m = message === undefined || message === null ? '' : toStr(message); } catch (e) { m = ''; }
    const err = new E(cut(m, L.maxErrorMessageChars));
    define(err, 'name', { value: 'KinoError_' + c, writable: false, configurable: false, enumerable: false });
    define(err, 'code', { value: c, writable: false, configurable: false, enumerable: true });
    return err;
  };
  // kino.error's third argument: `{ userMessage }`, the sentence the plugin wrote for the person.
  // Read as an own data property (a getter or a Proxy trap never runs), a string only, cut to one
  // character over the limit so Kotlin can tell a too-long one (it is then not shown, never
  // shortened). Reported now, by code and detail (ErrorSentences): a rejection nobody handles
  // never reaches __kinoCall's catch. Kotlin decides whether it is shown (PluginErrors.shownSentence).
  const said = (err, options) => {
    let s;
    try {
      if (options === null || typeof options !== 'object') return err;
      const d = ownDescriptor(options, 'userMessage');
      s = d && typeof d.value === 'string' ? cut(d.value, L.maxUserMessageChars + 1) : null;
    } catch (_) { s = null; }
    if (s === null) return err;
    define(err, 'userMessage', { value: s, writable: false, configurable: false, enumerable: true });
    try { n.said(err.code, err.message, s); } catch (_) { /* a sentence never breaks the error */ }
    return err;
  };
  // What a failed native call carries: its code, or 'network' for a binding that just threw.
  const nativeFailure = (e, fallbackCode) => {
    let m = '';
    try { const own = e !== null && typeof e === 'object' ? e.message : e; m = typeof own === 'string' ? own : ''; } catch (_) { m = ''; }
    return codedError(fallbackCode, cut(m, L.maxErrorMessageChars));
  };

  // --- kino.fetch ---
  // Method, redirect and body checks here are for a clear message; DefaultPluginHost re-checks all
  // three against the very same PluginHttp.METHODS/BODY_KINDS/REDIRECT_MODES constants these three
  // lists are read from (contract.json is the single source; see PluginContractParityTest.fetch).
  // The one limit that must hold whatever a plugin did to the built-ins is the size of `req`
  // (a primitive string from the captured JSON.stringify): that is what crosses. Every loop here
  // over plugin-reachable data is index-based over a captured Object.keys() array -- never
  // for...of, which resolves through the live Array.prototype[Symbol.iterator] (the same trap
  // cryptoCall's own comment documents).
  const METHODS = L.fetchMethods;
  const BODY_KINDS = L.fetchBodyKinds;
  const REDIRECT_MODES = L.fetchRedirectModes;
  const bodyOf = (b) => {
    if (b === undefined || b === null) return null;
    let out;
    if (typeof b === 'string') out = { kind: 'text', value: b };
    else if (typeof b !== 'object') out = { kind: 'text', value: toStr(b) };
    else if (hasOwn(b, 'json')) {
      const text = stringify(b.json);
      out = { kind: 'json', value: text === undefined ? 'null' : text };
    } else if (hasOwn(b, 'form')) {
      const f = b.form;
      if (f === null || typeof f !== 'object') throw codedError('invalid_request', 'body.form must be an object');
      const keys = keysOf(f);
      const fields = [];
      for (let i = 0; i < keys.length; i++) { const k = keys[i]; fields[fields.length] = [toStr(k), toStr(f[k])]; }
      out = { kind: 'form', value: fields };
    } else if (hasOwn(b, 'base64')) {
      out = { kind: 'base64', value: toStr(b.base64) };
    } else {
      throw codedError('invalid_request', 'body must be a string, { json }, { form } or { base64 }');
    }
    if (indexOf(BODY_KINDS, out.kind) === -1) throw codedError('invalid_request', 'unknown body type');
    return out;
  };
  const fetch = async function fetch(url, opts) {
    const o = opts === undefined || opts === null ? {} : opts;
    const method = o.method === undefined ? 'GET' : toUpperCase(toStr(o.method));
    if (indexOf(METHODS, method) === -1) throw codedError('invalid_request', 'method not allowed: ' + cut(method, 20));
    const redirect = o.redirect === undefined ? 'follow' : toStr(o.redirect);
    if (indexOf(REDIRECT_MODES, redirect) === -1) throw codedError('invalid_request', 'redirect must be "follow" or "manual"');
    const headers = o.headers === undefined || o.headers === null ? {} : o.headers;
    const plainHeaders = {};
    const headerKeys = keysOf(headers);
    for (let i = 0; i < headerKeys.length; i++) { const k = headerKeys[i]; plainHeaders[toStr(k)] = toStr(headers[k]); }
    const body = bodyOf(o.body);
    const req = toStr(stringify({
      url: toStr(url), method, headers: plainHeaders, body,
      redirect, cookies: o.cookies !== false, timeoutMs: isInteger(o.timeoutMs) ? o.timeoutMs : 0,
    }));
    if (req.length > L.maxRequestChars) throw codedError('too_large', 'request too large (over 1 MB)');
    let raw;
    // Read now: the native request starts after this job, maybe after the call it belongs to has answered (PendingFetches).
    const epoch = n.fetchEpoch();
    try { raw = await n.fetch(req, epoch); } catch (e) { throw nativeFailure(e, 'network'); }
    const r = parse(raw);
    // The call this request belonged to already answered and dropped it (PendingFetches): nothing waits for it.
    if (r.dropped === true) return new P(() => {});
    if (r.error) throw codedError(r.error.code, r.error.message);
    const text = typeof r.text === 'string' ? r.text : null;
    const b64 = typeof r.base64 === 'string' ? r.base64 : null;
    // Only one of the two usually crosses (see PluginHttp.Response); the other is derived here.
    const bodyText = () => (text !== null ? text : decodeUtf8(bytesOf(b64)));
    const bytesOf = (s) => { const bin = fromBase64(s); const out = new U8(bin.length); for (let i = 0; i < bin.length; i++) out[i] = charCodeAt(bin, i); return out; };
    // Pieces joined once: QuickJS copies the whole string on every `+=` (see web.js).
    const binary = (bytes) => { const parts = []; for (let i = 0; i < bytes.length; i += 8192) parts[parts.length] = apply(fromCharCode, null, subarray(bytes, i, i + 8192)); return apply(join, parts, ['']); };
    return freeze({
      ok: r.ok, status: r.status, url: r.url, headers: freeze(r.headers),
      text: freeze(function text() { return bodyText(); }),
      json: freeze(function json() { return parse(bodyText()); }),
      base64: freeze(function base64() { return b64 !== null ? b64 : toBase64(binary(encodeUtf8(text))); }),
    });
  };

  // --- kino.crypto (synchronous; errors carry code crypto_error) ---
  const ENC = ['utf8', 'hex', 'base64'];
  const maxCharsFor = (encoding) => (encoding === 'hex' ? L.cryptoMaxDataBytes * 2 : encoding === 'base64' ? (((L.cryptoMaxDataBytes + 2) / 3) | 0) * 4 + 4 : L.cryptoMaxDataBytes);
  const cryptoCall = (op) => {
    const req = {};
    // Index-based, not for...of: for...of on the array keysOf(op) resolves through the LIVE
    // Array.prototype[Symbol.iterator], not a captured built-in. A plugin that replaces it (see
    // the hostile test) would make this loop silently visit nothing, dropping every field instead
    // of refusing an oversized one -- the opposite of what this cap exists to do.
    const opKeys = keysOf(op);
    for (let i = 0; i < opKeys.length; i++) {
      const k = opKeys[i];
      const v = op[k];
      // A field the caller left out arrives as undefined; one set to null (e.g. `{ padding: null }`)
      // must fall back to its Kotlin default the same way, on every platform -- the two org.json
      // builds this app ships against (JVM unit tests vs. Android) disagree on what a literal JSON
      // null becomes on the other side of optString.
      if (v === undefined || v === null) continue;
      if (typeof v === 'string' && (k === 'in' || k === 'out' || k === 'keyEnc' || k === 'ivEnc' || k === 'aadEnc' || k === 'sigEnc') && ENC.indexOf(v) === -1) {
        throw codedError('crypto_error', 'unknown encoding: ' + cut(v, 20));
      }
      req[k] = v;
    }
    const fields = [['data', 'in'], ['key', 'keyEnc'], ['iv', 'ivEnc'], ['aad', 'aadEnc'], ['password', 'keyEnc'], ['salt', 'in']];
    for (let i = 0; i < fields.length; i++) {
      const field = fields[i][0], enc = fields[i][1];
      if (req[field] === undefined) continue;
      req[field] = toStr(req[field]);
      // "data" is the one field whose Kotlin-side default encoding depends on the operation:
      // encrypt defaults to utf8 in, decrypt to base64 in (it decrypts what encrypt just gave back).
      // A base64 5 MB payload is ~7 M characters, well past the utf8-sized cap -- using the wrong
      // default here refused a decrypt of ciphertext that came from a perfectly valid 5 MB encrypt.
      const defaultEnc = field === 'data' && req.op === 'decrypt' ? 'base64' : 'utf8';
      if (req[field].length > maxCharsFor(req[enc] || defaultEnc)) throw codedError('crypto_error', '"' + field + '" is over 5 MB');
    }
    // The per-field checks above give the clear message; this one is the cap that always holds.
    const json = toStr(stringify(req));
    if (json.length > L.cryptoMaxRequestChars) throw codedError('crypto_error', 'data too large (over 5 MB)');
    const r = parse(n.crypto(json));
    if (hasOwn(r, 'error')) throw codedError('crypto_error', r.error);
    return r.ok;
  };
  const opts = (o) => (o === undefined || o === null ? {} : o);
  const cryptoFns = {
    hash: freeze(function hash(alg, data, o) {
      const p = opts(o);
      return cryptoCall({ op: 'hash', alg: toStr(alg), data, in: p.inputEncoding, out: p.outputEncoding });
    }),
    hmac: freeze(function hmac(alg, key, data, o) {
      const p = opts(o);
      return cryptoCall({ op: 'hmac', alg: toStr(alg), key, data, keyEnc: p.keyEncoding, in: p.inputEncoding, out: p.outputEncoding });
    }),
    encrypt: freeze(function encrypt(alg, o) {
      const p = opts(o);
      return cryptoCall({ op: 'encrypt', alg: toStr(alg), key: p.key, iv: p.iv, data: p.data, aad: p.aad, padding: p.padding, keyEnc: p.keyEncoding, ivEnc: p.ivEncoding, aadEnc: p.aadEncoding, in: p.inputEncoding, out: p.outputEncoding });
    }),
    decrypt: freeze(function decrypt(alg, o) {
      const p = opts(o);
      return cryptoCall({ op: 'decrypt', alg: toStr(alg), key: p.key, iv: p.iv, data: p.data, aad: p.aad, padding: p.padding, keyEnc: p.keyEncoding, ivEnc: p.ivEncoding, aadEnc: p.aadEncoding, in: p.inputEncoding, out: p.outputEncoding });
    }),
    pbkdf2: freeze(function pbkdf2(hash, password, salt, iterations, keyLength, o) {
      const p = opts(o);
      if (!isInteger(iterations) || !isInteger(keyLength)) throw codedError('crypto_error', 'iterations and keyLength must be whole numbers');
      return cryptoCall({ op: 'pbkdf2', hash: toStr(hash), password, salt, iterations, keyLength, keyEnc: p.keyEncoding, in: p.inputEncoding, out: p.outputEncoding });
    }),
    randomBytes: freeze(function randomBytes(count, outputEncoding) {
      if (!isInteger(count)) throw codedError('crypto_error', 'randomBytes needs a whole number');
      return cryptoCall({ op: 'random', n: count, out: outputEncoding });
    }),
    uuid: freeze(function uuid() { return cryptoCall({ op: 'uuid' }); }),
  };
  // --- kino.crypto key pairs (apiVersion 6): a private key never leaves Kotlin; the plugin holds a
  // handle only this runtime's PluginKeyRing knows. Public keys cross as { type, namedCurve?, jwk,
  // spki, raw }. Every answer is rebuilt here into fresh frozen objects in a fixed key order (a jwk
  // in WebCrypto's alphabetical order), whatever order Kotlin's JSON kept.
  if (env.apiVersion >= L.keyPairsApiVersion) {
    const isObj = (v) => v !== null && typeof v === 'object';
    const optStr = (v) => (v === undefined || v === null ? undefined : toStr(v));
    const handleOf = (k) => (typeof k === 'string' ? k : isObj(k) && typeof k.handle === 'string' ? k.handle : undefined);
    const jwkIn = (j) => ({ kty: optStr(j.kty), crv: optStr(j.crv), x: optStr(j.x), y: optStr(j.y) });
    // A public key as generateKeyPair/importKey answered it (its spki), or a bare { jwk }.
    const putPublic = (req, p) => {
      if (isObj(p) && typeof p.spki === 'string') req.pub = p.spki;
      else if (isObj(p) && isObj(p.jwk)) req.pubJwk = jwkIn(p.jwk);
    };
    const jwkOut = (j) => freeze(j.kty === 'EC' ? { crv: j.crv, kty: j.kty, x: j.x, y: j.y } : { crv: j.crv, kty: j.kty, x: j.x });
    const publicOut = (p) => freeze(typeof p.namedCurve === 'string'
      ? { type: p.type, namedCurve: p.namedCurve, jwk: jwkOut(p.jwk), spki: p.spki, raw: p.raw }
      : { type: p.type, jwk: jwkOut(p.jwk), spki: p.spki, raw: p.raw });
    const privateOut = (k) => freeze(typeof k.namedCurve === 'string'
      ? { type: k.type, namedCurve: k.namedCurve, handle: k.handle }
      : { type: k.type, handle: k.handle });
    cryptoFns.generateKeyPair = freeze(function generateKeyPair(o) {
      const p = opts(o);
      const r = cryptoCall({ op: 'generateKeyPair', type: optStr(p.type), namedCurve: optStr(p.namedCurve) });
      return freeze({ privateKey: privateOut(r.privateKey), publicKey: publicOut(r.publicKey) });
    });
    cryptoFns.importKey = freeze(function importKey(o) {
      const p = opts(o);
      const format = optStr(p.format);
      const req = { op: 'importKey', format, type: optStr(p.type), namedCurve: optStr(p.namedCurve) };
      if (format === 'jwk') {
        if (!isObj(p.key)) throw codedError('crypto_error', 'importKey with format "jwk" needs key: a JWK object');
        req.jwk = jwkIn(p.key);
      } else {
        req.key = optStr(p.key);
      }
      return publicOut(cryptoCall(req));
    });
    cryptoFns.sign = freeze(function sign(o) {
      const p = opts(o);
      return cryptoCall({ op: 'sign', key: handleOf(p.key), data: p.data, in: p.encoding, hash: optStr(p.hash), format: optStr(p.format), out: p.outputEncoding });
    });
    cryptoFns.verify = freeze(function verify(o) {
      const p = opts(o);
      const req = { op: 'verify', data: p.data, in: p.encoding, signature: optStr(p.signature), sigEnc: p.signatureEncoding, hash: optStr(p.hash), format: optStr(p.format) };
      putPublic(req, p.key);
      if (req.pub === undefined && req.pubJwk === undefined) req.key = handleOf(p.key);
      return cryptoCall(req) === true;
    });
    cryptoFns.deriveSharedSecret = freeze(function deriveSharedSecret(o) {
      const p = opts(o);
      const req = { op: 'deriveSharedSecret', key: handleOf(p.privateKey), out: p.outputEncoding };
      putPublic(req, p.publicKey);
      return cryptoCall(req);
    });
  }
  const crypto = freeze(cryptoFns);

  // --- kino.sleep ---
  const sleep = async function sleep(ms) {
    if (!isInteger(ms) || ms < 0 || ms > L.sleepMaxMs) throw codedError('invalid_request', 'kino.sleep takes 0 to ' + L.sleepMaxMs + ' ms');
    await n.sleep(ms);
  };

  // --- kino.browser.capture (apiVersion 6) ---
  // A hidden page the plugin's resolve may open (Kotlin's PluginBrowser holds every gate: the manifest's approved
  // "browser": true, resolve only, the start host, no home-network subrequest, one page at a time). Its answer
  // comes back as data, like kino.fetch's, and a failure is thrown here as a typed, catchable error.
  const browserCapture = async function capture(url, opts) {
    const o = opts === undefined || opts === null ? {} : opts;
    const req = { url: toStr(url), autoplay: o.autoplay !== false };
    if (o.timeoutMs !== undefined) {
      if (!isInteger(o.timeoutMs) || o.timeoutMs < 1 || o.timeoutMs > L.browserMaxTimeoutMs) throw codedError('invalid_request', 'timeoutMs takes 1 to ' + L.browserMaxTimeoutMs + ' ms');
      req.timeoutMs = o.timeoutMs;
    }
    if (o.match !== undefined && o.match !== null) {
      const m = toStr(o.match);
      if (m.length < 1 || m.length > L.browserMaxMatchChars) throw codedError('invalid_request', 'match must be 1 to ' + L.browserMaxMatchChars + ' characters');
      req.match = m;
    }
    if (o.headers !== undefined && o.headers !== null) {
      const plain = {};
      const keys = keysOf(o.headers);
      for (let i = 0; i < keys.length; i++) { const k = keys[i]; plain[toStr(k)] = toStr(o.headers[k]); }
      req.headers = plain;
    }
    // Kino 0.9.54 (kino.browser.captureAll === true): every matching request, a cookie to wait for, an answer on timeout.
    // Kotlin's PluginBrowser.parseRequest checks the same again (types, limits, a valid cookie name and patterns).
    const flagOf = (name) => {
      const v = o[name];
      if (v === undefined || v === null) return false;
      if (v !== true && v !== false) throw codedError('invalid_request', name + ' must be true or false');
      return v;
    };
    if (flagOf('captureAll')) req.captureAll = true;
    if (flagOf('returnCookiesOnTimeout')) req.returnCookiesOnTimeout = true;
    if (o.alsoMatch !== undefined && o.alsoMatch !== null) {
      if (!req.captureAll) throw codedError('invalid_request', 'alsoMatch only works with captureAll');
      if (!Array.isArray(o.alsoMatch) || o.alsoMatch.length < 1 || o.alsoMatch.length > L.browserMaxAlsoMatch) throw codedError('invalid_request', 'alsoMatch takes 1 to ' + L.browserMaxAlsoMatch + ' expressions');
      req.alsoMatch = o.alsoMatch.map((p) => {
        const t = p instanceof RegExp ? p.source : toStr(p);
        if (t.length < 1 || t.length > L.browserMaxMatchChars) throw codedError('invalid_request', 'each alsoMatch must be 1 to ' + L.browserMaxMatchChars + ' characters');
        return t;
      });
    }
    if (o.waitForCookie !== undefined && o.waitForCookie !== null) {
      const w = toStr(o.waitForCookie);
      if (w.length < 1 || w.length > L.maxCookieNameChars) throw codedError('invalid_request', 'waitForCookie must be a cookie name');
      req.waitForCookie = w;
    }
    const text = toStr(stringify(req));
    if (text.length > L.maxRequestChars) throw codedError('too_large', 'request too large');
    let raw;
    try { raw = await n.browserCapture(text); } catch (e) { throw nativeFailure(e, 'browser_unavailable'); }
    const r = parse(raw);
    if (r.error) throw codedError(r.error.code, r.error.message);
    return r;
  };

  // --- kino.browser.page (apiVersion 6, "browser": "pages") ---
  // The same hidden page, READ instead of captured: its HTML once it is past the site's automatic browser check (and
  // matches waitFor, a JavaScript pattern run inside the page). Kotlin's PluginBrowser holds every gate (the approved
  // "browser": "pages", only from the exports the person starts, the start host, one page at a time, a budget
  // per minute). Kino never clicks or types in it: a page that asks for a human answers 'blocked'.
  const browserPage = async function page(url, opts) {
    const o = opts === undefined || opts === null ? {} : opts;
    const req = { url: toStr(url) };
    if (o.timeoutMs !== undefined) {
      if (!isInteger(o.timeoutMs) || o.timeoutMs < 1 || o.timeoutMs > L.browserMaxTimeoutMs) throw codedError('invalid_request', 'timeoutMs takes 1 to ' + L.browserMaxTimeoutMs + ' ms');
      req.timeoutMs = o.timeoutMs;
    }
    if (o.waitFor !== undefined && o.waitFor !== null) {
      const isRx = o.waitFor instanceof RegExp;
      const w = isRx ? o.waitFor.source : toStr(o.waitFor);
      // A RegExp keeps its m and s flags (it is always case-insensitive; g, y, u, d change nothing for a test).
      const flags = isRx ? String(o.waitFor.flags).replace(/[^ms]/g, '') : '';
      if (w.length < 1 || w.length > L.browserMaxMatchChars) throw codedError('invalid_request', 'waitFor must be 1 to ' + L.browserMaxMatchChars + ' characters');
      try { new RegExp(w, 'i' + flags); } catch (e) { throw codedError('invalid_request', 'waitFor is not a valid regular expression'); }
      req.waitFor = w;
      if (flags) req.waitForFlags = flags;
    }
    const text = toStr(stringify(req));
    if (text.length > L.maxRequestChars) throw codedError('too_large', 'request too large');
    let raw;
    try { raw = await n.browserPage(text); } catch (e) { throw nativeFailure(e, 'browser_unavailable'); }
    const r = parse(raw);
    if (r.error) throw codedError(r.error.code, r.error.message);
    return r;
  };

  // --- kino.cloudstream (only plugins Kino generates from a CloudStream repository; spec §12) ---
  // Kotlin defines n.cloudstream only for such a plugin's host (CloudStreamCapable). The bridge's own codes are mapped
  // onto PluginErrors' set here; the message keeps the specific reason. 'needs_bridge' (the complement is missing, not
  // Kino's or outdated) is Kino's own and goes through as is: the screens offer the complement for it.
  const cloudstreamCode = (c) => (c === 'plugin_error' ? 'not_found' : c === 'needs_bridge' ? 'needs_bridge' : 'unavailable');
  const cloudstreamCall = async function cloudstreamCall(op, args) {
    const text = toStr(stringify(args));
    if (text.length > L.maxRequestChars) throw codedError('invalid_request', 'request too large');
    let raw;
    try { raw = await n.cloudstream(op, text); } catch (e) { throw nativeFailure(e, 'unavailable'); }
    const r = parse(raw);
    if (r && r.error) throw codedError(cloudstreamCode(r.error.code), r.error.message);
    return r;
  };
  // --- kino.meta and kino.tmdb (Kino 0.9.53, no new apiVersion: feature-detect with typeof) ---
  // Both answer as data, like kino.fetch: Kotlin checks every field (KinoMetaRequest, KinoTmdbRequest) and holds every
  // limit (a per-plugin budget, caps, timeouts); a failure is thrown here as a typed, catchable error. A `userMessage` on
  // one is KINO'S sentence for the person (no_tmdb_key's), set on the error for the plugin to show; never reported as the
  // plugin's own (said), so Kino words an uncaught one itself.
  const serviceError = (e) => {
    const err = codedError(e.code, e.message);
    if (typeof e.userMessage === 'string') {
      define(err, 'userMessage', { value: cut(e.userMessage, L.maxUserMessageChars), writable: false, configurable: false, enumerable: true });
    }
    return err;
  };
  const serviceCall = async (native, req, max, api) => {
    let text;
    try { text = toStr(stringify(req)); } catch (e) { throw codedError('invalid_request', api + ': the query can\'t be turned into JSON'); }
    if (text.length > max) throw codedError('invalid_request', api + ': the query is too large');
    let raw;
    try { raw = await native(text); } catch (e) { throw nativeFailure(e, 'unavailable'); }
    const r = parse(raw);
    if (r.error) throw serviceError(r.error);
    return r;
  };
  const META_ID_KEYS = ['imdb', 'tmdb', 'tvdb', 'kitsu', 'mal', 'anilist'];
  const meta = async function meta(query) {
    if (query === null || typeof query !== 'object') throw codedError('invalid_request', 'kino.meta needs { type, ids }');
    const req = { type: query.type, lang: query.lang };
    const ids = query.ids;
    if (ids !== null && typeof ids === 'object') {
      req.ids = {};
      for (let i = 0; i < META_ID_KEYS.length; i++) { const k = META_ID_KEYS[i]; if (ids[k] !== undefined) req.ids[k] = ids[k]; }
    }
    const r = await serviceCall(n.meta, req, L.metaMaxRequestChars, 'kino.meta');
    return r.meta === undefined ? null : r.meta;
  };
  const tmdb = async function tmdb(path, params) {
    const req = { path: toStr(path) };
    if (params !== undefined && params !== null) {
      if (typeof params !== 'object' || Array.isArray(params)) throw codedError('invalid_request', 'kino.tmdb: params must be an object');
      const plain = {};
      const keys = keysOf(params);
      for (let i = 0; i < keys.length; i++) { const k = keys[i]; plain[toStr(k)] = params[k]; }
      req.params = plain;
    }
    const r = await serviceCall(n.tmdb, req, L.tmdbMaxRequestChars, 'kino.tmdb');
    return r.json;
  };

  // --- kino.storage ---
  const storage = freeze({
    get: freeze(function get(k) { const key = toStr(k); if (key.length > L.storageMaxBytes) return null; const v = n.storageGet(key); return v == null ? null : v; }),
    set: freeze(function set(k, v, o) {
      const key = toStr(k), value = toStr(v);
      if (key.length + value.length > L.storageMaxBytes) throw codedError('too_large', 'plugin storage full (256 KB)');
      const p = opts(o);
      let ttlMs = null;
      if (p.ttlMs !== undefined && p.ttlMs !== null) {
        if (!isInteger(p.ttlMs) || p.ttlMs <= 0 || p.ttlMs > L.storageMaxTtlMs) {
          throw codedError('invalid_request', 'kino.storage.set: ttlMs must be a whole number above 0 and up to ' + L.storageMaxTtlMs + ' ms (30 days)');
        }
        ttlMs = p.ttlMs;
      }
      n.storageSet(key, value, ttlMs);
    }),
    remove: freeze(function remove(k) { const key = toStr(k); if (key.length <= L.storageMaxBytes) n.storageRemove(key); }),
    keys: freeze(function keys() { return parse(n.storageKeys()); }),
  });

  // --- kino.config: read once, read-only. Values are bounded by the manifest's settings schema. ---
  const configValues = freeze(parse(n.config()));
  const config = freeze({
    get: freeze(function get(key) { const k = toStr(key); return hasOwn(configValues, k) ? configValues[k] : undefined; }),
    all: freeze(function all() { const out = {}; for (const k of keysOf(configValues)) out[k] = configValues[k]; return out; }),
  });

  // --- kino.cookies ---
  const cookies = freeze({
    get: freeze(function get(url, name) {
      const u = cut(toStr(url), L.maxUrlChars), nm = cut(toStr(name), L.maxCookieNameChars);
      const v = n.cookieGet(u, nm);
      return v == null ? null : v;
    }),
    clear: freeze(function clear() { n.cookiesClear(); }),
  });

  // --- kino.rank: same algorithm as the Node kit's kino-rank.mjs (see kit.test.mjs's
  // "kino.rank: the shim and the runtime run the exact same code"). Pure JS, no native call: it
  // never crosses into Kotlin, so none of the hardening kino.fetch/kino.crypto need above applies
  // here -- a plugin that broke its own Array.prototype only ever breaks its own kino.rank results.
  // --- kino.rank shared core: BEGIN (byte-identical in kino-rank.mjs and prelude.js) ---
  const FOLD_ACCENTS = {
    á: "a", à: "a", ä: "a", â: "a", ã: "a", å: "a", é: "e", è: "e", ë: "e", ê: "e",
    í: "i", ì: "i", ï: "i", î: "i", ó: "o", ò: "o", ö: "o", ô: "o", õ: "o",
    ú: "u", ù: "u", ü: "u", û: "u", ñ: "n", ç: "c",
  };

  // NFKD decomposes an accented letter into its plain letter plus a combining mark (e.g. "ã" ->
  // "a" + U+0303); stripping the marks folds every decomposable Latin accent at once. `.normalize`
  // is feature-detected, not assumed, because this same code also runs inside Kino's sandboxed JS
  // engine: the manual table above is the fallback for an engine where it is missing.
  function foldAccents(text) {
    if (typeof text.normalize === "function") {
      return text.normalize("NFKD").replace(/[̀-ͯ]/g, "");
    }
    return text.replace(/[áàäâãåéèëêíìïîóòöôõúùüûñç]/g, (c) => FOLD_ACCENTS[c]);
  }

  // Words of 3+ letters, folded to plain lowercase ascii. 1-2 letter words ("el", "de", "a", "of")
  // are dropped: they are exactly what makes unrelated titles look alike.
  function titleTokens(text) {
    const plain = foldAccents(String(text || "").toLowerCase());
    return new Set((plain.match(/[a-z0-9]+/g) || []).filter((w) => w.length > 2));
  }

  // `getTitle(item)` may return a string, an array of them (a title known in more than one field or
  // language), or fail outright (it may throw, or answer something that is not a string at all):
  // every string form is kept, and anything else -- a thrown error included -- is treated as "no
  // title for this item" here rather than left to crash the caller.
  function stringFormsOf(getTitle, item) {
    let value;
    try {
      value = getTitle(item);
    } catch (e) {
      value = null;
    }
    return [].concat(value).filter((form) => typeof form === "string");
  }

  function tokensFromForms(forms) {
    const tokens = new Set();
    for (const form of forms) for (const t of titleTokens(form)) tokens.add(t);
    return tokens;
  }

  function sharedCount(a, b) {
    let n = 0;
    for (const t of a) if (b.has(t)) n++;
    return n;
  }

  // `query` may be one title or several forms of it (a backend may only know a title in one
  // language): a bare string is treated the same as a one-element array. Anything that is not a
  // usable string is dropped rather than thrown.
  function queryForms(query) {
    return [].concat(query).filter((q) => typeof q === "string" && q.length > 0);
  }

  function defaultGetTitle(item) {
    return item.title;
  }

  // The items that share the most words with any form of `query` go first; ties keep the order
  // `items` was given in (stable). No usable token in `query` at all: the order is left untouched.
  // `items` not an array: nothing to rank, so an empty list comes back instead of a thrown error. An
  // item with no usable title sorts after every item that has one (score -1, below any real score,
  // which is never negative), in `items`' own order among themselves.
  function sortBySimilarity(items, query, getTitle) {
    if (!Array.isArray(items)) return [];
    const of = getTitle || defaultGetTitle;
    const requested = queryForms(query).map(titleTokens).filter((t) => t.size > 0);
    if (requested.length === 0) return items;
    return items
      .map((item, index) => {
        const forms = stringFormsOf(of, item);
        const ofItem = tokensFromForms(forms);
        const score = forms.length === 0 ? -1 : Math.max(...requested.map((r) => sharedCount(r, ofItem)));
        return { item, index, score };
      })
      .sort((a, b) => b.score - a.score || a.index - b.index)
      .map((x) => x.item);
  }

  // Minimum share of a requested title's distinctive words an item must carry to count as a real
  // match (0.6 = "most of them").
  const MIN_RELEVANCE = 0.6;

  // Reordering alone (sortBySimilarity) still leaves a page of near-misses when the title genuinely
  // is not on the backend; this drops them, so an absent title comes back with 0 results instead.
  // `items` not an array: nothing to filter, so an empty list comes back instead of a thrown error.
  // An item with no usable title can never be judged relevant, so it is dropped along with the
  // actual near-misses.
  function filterRelevant(items, query, getTitle) {
    if (!Array.isArray(items)) return [];
    const of = getTitle || defaultGetTitle;
    const forms = queryForms(query).map(titleTokens).filter((t) => t.size > 0);
    if (forms.length === 0) return items;
    return items.filter((item) => {
      const itemForms = stringFormsOf(of, item);
      if (itemForms.length === 0) return false;
      const ofItem = tokensFromForms(itemForms);
      return forms.some((form) => sharedCount(form, ofItem) / form.size >= MIN_RELEVANCE);
    });
  }

  // Ask a loose-matching backend the title's HEAD, not the whole thing: a long title returns
  // everything that shares one common word with it, drowning the real match; the head alone keeps
  // the backend's own ranking useful. A one- or two-letter head ("El", "A") identifies nothing, so
  // the whole text is used instead. Not a plain "-": that would cut inside a hyphenated word like
  // "Spider-Man".
  function shortQuery(query) {
    const text = String(query || "").trim();
    const head = text.split(/[:,|–—]/)[0].trim();
    return head.length >= 3 ? head : text;
  }
  // --- kino.rank shared core: END ---
  const rank = freeze({
    shortQuery: freeze(shortQuery),
    sortBySimilarity: freeze(sortBySimilarity),
    filterRelevant: freeze(filterRelevant),
  });

  const kino = {
    apiVersion: env.apiVersion,
    appVersion: env.appVersion,
    lang: env.lang,
    fetch: freeze(fetch),
    html: freeze({
      select: freeze(function select(html, css) {
        const selector = toStr(css);
        if (selector.length > L.maxSelectorChars) throw codedError('invalid_request', 'CSS selector too long (over ' + L.maxSelectorChars + ' characters)');
        return parse(n.select(cut(toStr(html), L.maxHtmlChars), selector));
      }),
    }),
    storage,
    config,
    cookies,
    crypto,
    rank,
    sleep: freeze(sleep),
    // A marker, not the value: Kotlin swaps it for the sealed value inside kino.fetch, toward the
    // manifest's own hosts only. A name is at most 32 characters, so cutting to 64 before it
    // crosses can never turn an undeclared name into a declared one.
    secret: freeze(function secret(name) {
      const s = toStr(name);
      const m = n.secret(cut(s, 64));
      if (!m) throw codedError('not_allowed', 'this plugin doesn\'t declare the secret ' + cut(s, 40));
      return m;
    }),
    error: freeze(function error(code, message, options) { return said(codedError(code, message), options); }),
    log: kinoLog,
  };
  if (typeof n.meta === 'function') kino.meta = freeze(meta);
  if (typeof n.tmdb === 'function') kino.tmdb = freeze(tmdb);
  if (env.apiVersion >= L.browserApiVersion && typeof n.browserCapture === 'function') {
    // captureAll: true is the Kino 0.9.54 options' feature flag (contract.json additiveFromApp "kino.browser.captureAll").
    kino.browser = env.apiVersion >= L.browserPageApiVersion && typeof n.browserPage === 'function'
      ? freeze({ capture: freeze(browserCapture), page: freeze(browserPage), captureAll: true })
      : freeze({ capture: freeze(browserCapture), captureAll: true });
  }
  if (typeof n.cloudstream === 'function') {
    kino.cloudstream = freeze({
      search: freeze(function search(provider, query) { return cloudstreamCall('search', { provider: provider | 0, query: toStr(query) }); }),
      mainPage: freeze(function mainPage(provider, index, page) { return cloudstreamCall('mainPage', { provider: provider | 0, index: index | 0, page: page | 0 }); }),
      load: freeze(function load(provider, url) { return cloudstreamCall('load', { provider: provider | 0, url: toStr(url) }); }),
      loadLinks: freeze(function loadLinks(provider, data) { return cloudstreamCall('loadLinks', { provider: provider | 0, data: toStr(data) }); }),
    });
  }
  // --- The signing lane (SigningLaneHost): `sign` is a pure function of its argument, so storage,
  // cookies and sleep are refused HERE, with an error the plugin can catch and read. Kotlin refuses
  // them too, but a throw from a sync native binding can't be caught in JS and may lose its message.
  // n.signingLane exists only on the lane runtime's __kinoNative: a feature detect, nothing plugin.js can set itself.
  if (typeof n.signingLane === 'function') {
    const refuse = (api) => codedError('not_allowed', 'sign() can\'t use ' + api + ': whatever you need must come in signContext');
    const refused = (api) => freeze(function refused() { throw refuse(api); });
    kino.storage = freeze({ get: refused('kino.storage'), set: refused('kino.storage'), remove: refused('kino.storage'), keys: refused('kino.storage') });
    kino.cookies = freeze({ get: refused('kino.cookies'), clear: refused('kino.cookies') });
    kino.sleep = freeze(async function sleep() { throw refuse('kino.sleep'); });
    if (kino.meta) kino.meta = freeze(async function meta() { throw refuse('kino.meta'); });
    if (kino.tmdb) kino.tmdb = freeze(async function tmdb() { throw refuse('kino.tmdb'); });
    if (kino.browser) {
      const off = async function refusedBrowser() { throw refuse('kino.browser'); };
      kino.browser = kino.browser.page ? freeze({ capture: freeze(off), page: freeze(off), captureAll: true }) : freeze({ capture: freeze(off), captureAll: true });
    }
  }
  globalThis.kino = freeze(kino);
  globalThis.console = freeze({
    log: freeze((...a) => log('info', a)), info: freeze((...a) => log('info', a)),
    warn: freeze((...a) => log('warn', a)), error: freeze((...a) => log('error', a)),
  });
  // A thrown value's message reaches Kotlin (and the screen) as the exception text, so it's
  // rebuilt here: a short plain string, no stack. Every step can be hostile (a throwing getter or
  // toString, a Proxy, a Symbol, 30 MB of text), hence the captured built-ins and the fallback.
  // What __kinoCall throws carries an OWN, fixed name: native code formats an error as
  // "<name>: <message>" and would otherwise read a plugin-controlled Error.prototype.name (30 MB,
  // measured). The prototype itself is left alone so `this.name = 'MyErr'` in a plugin's Error
  // subclass keeps working.
  const kinoError = (message, code) => {
    const err = new E(message);
    define(err, 'name', { value: code ? 'KinoError_' + code : 'Error', writable: false, configurable: false, enumerable: false });
    return err;
  };
  const errorText = (e) => {
    try {
      let m = e;
      if (e !== null && (typeof e === 'object' || typeof e === 'function')) {
        const own = e.message;
        if (own !== undefined) m = own;
      }
      if (m === null || m === undefined || typeof m === 'symbol') return L.thrownFallback;
      const text = typeof m === 'string' ? m : S(m);
      if (typeof text !== 'string' || text.length === 0) return L.thrownFallback;
      return cut(text, L.maxErrorChars);
    } catch (_) {
      return L.thrownFallback;
    }
  };
  // The code of a typed error, read without running plugin code (own data property only).
  const errorCode = (e) => {
    try {
      if (e === null || typeof e !== 'object') return null;
      const d = ownDescriptor(e, 'code');
      return d && isCode(d.value) ? d.value : null;
    } catch (_) {
      return null;
    }
  };
  // What a thrown value was, for a debug plugin's author only: its own type name and stack, which the
  // rethrow below drops on purpose. Sent to Kotlin under the call's id (n.thrown), where it rides on
  // the failure as PluginException.thrown and only PluginDebugLog reads it; the error every other path
  // reads is unchanged. Own DATA properties only (a getter is never called), the name looked up at most
  // 8 prototypes deep, both cut here before they cross.
  const thrownName = (e) => {
    let o = e;
    for (let i = 0; i < 8 && o !== null && (typeof o === 'object' || typeof o === 'function'); i++) {
      const d = ownDescriptor(o, 'name');
      if (d) return typeof d.value === 'string' ? cut(d.value, L.maxThrownNameChars) : '';
      o = protoOf(o);
    }
    return '';
  };
  const thrownStack = (e) => {
    if (e === null || typeof e !== 'object') return '';
    const d = ownDescriptor(e, 'stack');
    return d && typeof d.value === 'string' ? cut(d.value, L.maxErrorChars) : '';
  };
  const reportThrown = (callId, e) => {
    if (typeof callId !== 'string' || callId.length > 20) return;
    let name = '', stack = '';
    try { name = thrownName(e); } catch (_) { name = ''; }
    try { stack = thrownStack(e); } catch (_) { stack = ''; }
    try { n.thrown(callId, name, stack); } catch (_) { /* debugging never breaks a call */ }
  };
  // A call's JSON crosses to Kotlin with every UTF-16 surrogate written as a `\uXXXX` escape. quickjs-kt
  // hands a JS string over as JS_ToCString (standard UTF-8, so an emoji is one 4-byte sequence) into
  // NewStringUTF, which reads MODIFIED UTF-8, where 4-byte sequences do not exist: the JVM garbled each
  // emoji and dropped the end of the string, so a resolve whose labels carried one (Nuvio scrapers'
  // "🎬 Inception - 2010") failed with "Unterminated string" (measured, NuvioResultEncodingTest). Surrogates
  // only occur inside JSON strings, where the escape means the very same character, and everything else
  // is identical in both encodings (JSON already escapes U+0000). A loop over charCodeAt, not a regex:
  // a regex replace looks RegExp.prototype.exec up when it runs, and plugin code can replace it.
  const numberToString = Function.prototype.call.bind(Number.prototype.toString);
  // Null when the escaped text would be longer than `max` (each escape adds five characters): it stops there instead
  // of building a string up to six times the cap. The pieces go into a null-prototype array-like and are joined once
  // (a concatenation per surrogate took 2 s for 100 000 emoji), no Array.prototype setter in the way.
  const escapeSurrogates = (s, max) => {
    const parts = { __proto__: null };
    let count = 0, from = 0, length = s.length;
    for (let i = 0; i < s.length; i++) {
      const c = charCodeAt(s, i);
      if (c < 0xd800 || c > 0xdfff) continue;
      length += 5;
      if (length > max) return null;
      parts[count++] = slice(s, from, i);
      parts[count++] = '\\u' + numberToString(c, 16);
      from = i + 1;
    }
    if (count === 0) return s;
    parts[count++] = slice(s, from);
    parts.length = count;
    return apply(join, parts, ['']);
  };
  // A plugin that sets `globalThis.__kinoDropPendingFetches = true` (a converted Nuvio scraper) does not make its
  // answer wait for the requests it left behind: once the export settles they are dropped (PendingFetches). Any
  // other plugin keeps its requests running to the end, as before.
  const dropPendingFetches = () => {
    let wanted = false;
    try { wanted = globalThis.__kinoDropPendingFetches === true; } catch (e) { wanted = false; }
    if (wanted) n.dropPendingFetches();
  };
  // Frozen too: its `name` is read natively when the rethrow below builds an error in its frame
  // (a plugin renamed it to 20 MB and crashed the process, measured).
  define(globalThis, '__kinoCall', {
    value: freeze(async (name, argJson, callId) => {
      let out;
      try {
        const fn = globalThis.__kinoExports[name];
        if (typeof fn !== 'function') throw codedError('not_allowed', 'the plugin doesn\'t export ' + name);
        const arg = parse(argJson);
        // browse(ref, cursor) and a retried resolve(ref, options) are the two-argument calls:
        // Kotlin sends { ref, cursor } and { ref, options }. A first resolve gets its argument as is (a ref string, or the object a converted Nuvio scraper takes).
        out = stringify(await (name === 'browse' ? fn(arg.ref, arg.cursor)
          : name === 'resolve' && arg !== null && typeof arg === 'object' && 'ref' in arg && 'options' in arg ? fn(arg.ref, arg.options)
          : fn(arg)));
      } catch (e) {
        dropPendingFetches();
        const text = errorText(e);
        // A thrown value with no message leaves nothing to go on: log at least its type.
        if (text === L.thrownFallback) log('warn', ['a call failed with a value that has no message, of type', typeof e]);
        reportThrown(callId, e);
        throw kinoError(text, errorCode(e));
      }
      dropPendingFetches();
      if (typeof out !== 'string') return 'null';
      // Refused before the escaping work too, not only after it.
      if (out.length > L.maxResultChars) throw kinoError(L.resultTooBig, null);
      out = escapeSurrogates(out, L.maxResultChars);
      if (out === null || out.length > L.maxResultChars) throw kinoError(L.resultTooBig, null);
      return out;
    }),
    writable: false, configurable: false, enumerable: false,
  });
})
