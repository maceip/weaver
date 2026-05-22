package com.weaver.app.webview

/**
 * Document-start JS that wraps `window.fetch` before any Stitch script runs.
 * Watches for two endpoints and tees their responses into the bridge:
 *
 *   /AppCompanionAgentService/StreamCreateSession
 *     -> Google wrb.fr chunked frames. Each frame carries the prompt echo,
 *        a session id (projects/<pid>/sessions/<sid>), a sequence number,
 *        and a stages array (e.g. ["nplekj15","predict_shared_components",
 *        "Mapping out the components", 1]). Trailer frame ["e", N, ...]
 *        marks the "Request has finished" snackbar moment.
 *
 *   /Nemo/data/batchexecute?rpcids=f6CJY
 *     -> Full project state, including the design-token map
 *        ([[name, hex], ...]) for the active project's theme.
 *
 * Other batchexecute rpcids observed in the HAR fixtures, decoded but not
 * yet teed (none are critical paths today; revisit if Stitch removes
 * StreamCreateSession or we need fine-grained features):
 *
 *   cabgj   -> Mint a new project. Empty `f.req` argument, returns the
 *              fresh `projects/<numericId>` path. Fires when the user
 *              submits a seed prompt from the landing-page composer.
 *   XaOLp   -> Register pre-uploaded /contrib_service/ttl_30d/<id> files
 *              as initial screens on a new project. Returns CDN URLs +
 *              `projects/<pid>/screens/<sid>` + dimensions per image.
 *   eW2RYb  -> Fetch node layout (position + size per screen).
 *   dNS8Mc  -> Fetch prompt history + carries the full session path,
 *              same `projects/.../sessions/<sid>` as StreamCreateSession.
 *   UiiVCf  -> Probe a sub-resource ("design_system" arg). Empty if not
 *              present yet.
 *   ErneX   -> List project files (CDN URLs again).
 *   N5xENe  -> Unknown — small response of layout numbers.
 *   yxssG   -> Telemetry / event recorder. Empty response.
 *
 * Format details from the network-trace*.har fixtures under
 * app/src/test/resources/stitch-fixtures/.
 */
internal object StitchFetchInterceptor {
    val source: String =
        """
(function(){
  if (window.__weaverFetchHooked) return;
  window.__weaverFetchHooked = true;

  function emit(type, payload) {
    var A = window.Android;
    if (!A || typeof A.post !== 'function') return;
    try { A.post(JSON.stringify(Object.assign({type: type}, payload || {}))); } catch (e) {}
  }

  function stripXssi(s) {
    if (s.indexOf(")]}'") === 0) {
      var nl = s.indexOf('\n');
      return nl >= 0 ? s.substring(nl + 1) : '';
    }
    return s;
  }

  // Parses Google's wrb.fr framing: a decimal length line, then that many
  // chars STARTING FROM the length line's terminating \n (so the count
  // includes that \n). The count is JS-string chars, not UTF-8 bytes.
  // Skips the blank line after the XSSI guard. Returns the remainder buffer
  // plus any complete frames. (See server/src/bridge/wrbfr.ts — the tested
  // reference implementation.)
  function consumeFrames(buf) {
    var frames = [];
    while (true) {
      var nl = buf.indexOf('\n');
      if (nl < 0) break;
      var head = buf.substring(0, nl).trim();
      if (head === '') { buf = buf.substring(nl + 1); continue; }
      var sz = parseInt(head, 10);
      if (isNaN(sz)) break;
      if (buf.length < nl + sz) break;
      frames.push(buf.substring(nl + 1, nl + sz));
      buf = buf.substring(nl + sz);
    }
    return { buf: buf, frames: frames };
  }

  function handleStreamFrame(raw) {
    var parsed;
    try { parsed = JSON.parse(raw); } catch (e) { return; }
    if (!Array.isArray(parsed) || !parsed[0]) return;
    var row = parsed[0];
    // ["wrb.fr", null|"rpcid", "<json-string>"]
    if (row[0] === 'e' && typeof row[1] === 'number') {
      // Trailer frame: ["e", frameCount, null, null, totalBytes]
      emit('session_finished', { sessionId: window.__weaverLastSessionId || '', totalBytes: row[4] || 0 });
      return;
    }
    if (row[0] !== 'wrb.fr' || typeof row[2] !== 'string') return;
    var inner;
    try { inner = JSON.parse(row[2]); } catch (e) { return; }
    if (!Array.isArray(inner)) return;

    // Inner shape for StreamCreateSession:
    //   [sessionPath, null, seqNo, requestEcho, stagesContainer, ...]
    // sessionPath = "projects/<pid>/sessions/<sid>"
    var sessionPath = inner[0];
    if (typeof sessionPath === 'string' && sessionPath.indexOf('projects/') === 0) {
      var parts = sessionPath.split('/');
      var projectId = parts[1];
      var sessionId = parts[3];
      if (sessionId && sessionId !== window.__weaverLastSessionId) {
        window.__weaverLastSessionId = sessionId;
        emit('session_started', { projectId: projectId, sessionId: sessionId });
      }
      var seqNo = (typeof inner[2] === 'number') ? inner[2] : 0;
      var stages = collectStages(inner);
      if (stages.length || seqNo > 0) {
        emit('session_progress', { sessionId: sessionId, seqNo: seqNo, stages: stages });
      }
    }
  }

  // Walk the response tree for [id, key, label, status] stage tuples.
  // Stitch nests them several arrays deep; depth-first search is cheap
  // because frames are < 25KB and structure is small.
  function collectStages(node) {
    var out = [];
    function walk(n) {
      if (!Array.isArray(n)) return;
      // Heuristic: a stage tuple is [string, string, string, number?] of
      // length 3 or 4 where the first two strings are short identifiers
      // and the third is a longer label.
      if (n.length >= 3 &&
          typeof n[0] === 'string' && n[0].length < 32 &&
          typeof n[1] === 'string' && n[1].length < 64 &&
          typeof n[2] === 'string' && n[2].length > 0 && n[2].length < 200 &&
          (n.length === 3 || typeof n[3] === 'number')) {
        out.push({ id: n[0], key: n[1], label: n[2], status: (n[3] || 0) });
        return;
      }
      for (var i = 0; i < n.length; i++) walk(n[i]);
    }
    walk(node);
    return out;
  }

  function teeStreamCreateSession(response) {
    var clone;
    try { clone = response.clone(); } catch (e) { return; }
    if (!clone.body || !clone.body.getReader) return;
    var reader = clone.body.getReader();
    var dec = new TextDecoder();
    var buf = '';
    var stripped = false;
    function pump() {
      reader.read().then(function(r) {
        if (r.done) return;
        buf += dec.decode(r.value, { stream: true });
        if (!stripped) {
          var prefix = stripXssi(buf);
          if (prefix !== buf) { buf = prefix; stripped = true; }
          else if (buf.length > 12) { stripped = true; }
        }
        if (stripped) {
          var c = consumeFrames(buf);
          buf = c.buf;
          for (var i = 0; i < c.frames.length; i++) handleStreamFrame(c.frames[i]);
        }
        pump();
      }).catch(function(){});
    }
    pump();
  }

  function teeBatchExecute(response) {
    var clone;
    try { clone = response.clone(); } catch (e) { return; }
    clone.text().then(function(text) {
      var body = stripXssi(text);
      var c = consumeFrames(body);
      for (var i = 0; i < c.frames.length; i++) handleBatchFrame(c.frames[i]);
    }).catch(function(){});
  }

  function handleBatchFrame(raw) {
    var parsed;
    try { parsed = JSON.parse(raw); } catch (e) { return; }
    if (!Array.isArray(parsed) || !parsed[0]) return;
    var row = parsed[0];
    if (row[0] !== 'wrb.fr' || typeof row[2] !== 'string') return;
    var inner;
    try { inner = JSON.parse(row[2]); } catch (e) { return; }
    if (!Array.isArray(inner) || typeof inner[0] !== 'string' || inner[0].indexOf('projects/') !== 0) return;
    var projectId = inner[0].split('/')[1];
    var tokens = collectThemeTokens(inner);
    if (Object.keys(tokens).length > 0) {
      emit('project_theme', { projectId: projectId, tokens: tokens });
    }
  }

  // Theme tokens live as [[name, "#hex"], ...] arrays nested inside the
  // project state. Walk and pick up any pair that looks like one.
  function collectThemeTokens(node) {
    var out = {};
    function walk(n) {
      if (!Array.isArray(n)) return;
      if (n.length === 2 &&
          typeof n[0] === 'string' &&
          typeof n[1] === 'string' &&
          /^#[0-9a-fA-F]{3,8}$/.test(n[1])) {
        out[n[0]] = n[1];
        return;
      }
      for (var i = 0; i < n.length; i++) walk(n[i]);
    }
    walk(node);
    return out;
  }

  var origFetch = window.fetch;
  if (!origFetch) return;
  window.fetch = function(input, init) {
    return origFetch.call(this, input, init).then(function(response) {
      try {
        var url = (typeof input === 'string') ? input : (input && input.url) || '';
        if (url.indexOf('StreamCreateSession') >= 0) {
          teeStreamCreateSession(response);
        } else if (url.indexOf('batchexecute') >= 0 && url.indexOf('rpcids=f6CJY') >= 0) {
          teeBatchExecute(response);
        }
      } catch (e) {}
      return response;
    });
  };
})();
        """.trimIndent()
}
