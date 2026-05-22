/**
 * Scripts injected into the remote Stitch page via Playwright `addInitScript`.
 *
 * These are the server-side twins of the Android `StitchContentScript` and
 * `StitchFetchInterceptor`. Keep them behaviourally in sync with
 * app/src/main/java/com/weaver/app/webview/Stitch*.kt — the selectors and
 * the wrb.fr parsing are the same; only the native bridge differs.
 *
 * On-device the content script calls `window.Android.post(json)` (an Android
 * @JavascriptInterface). Here we define a shim so the *exact same* content
 * script runs unchanged: `window.Android.post` forwards to the Playwright
 * binding `window.__weaverEmit`, which `StitchSession` exposes.
 */

/** Defines window.Android backed by the Playwright exposeBinding. Runs first. */
export const BRIDGE_SHIM = `
(function(){
  if (window.Android && window.Android.__weaver) return;
  window.Android = {
    __weaver: true,
    post: function(json){
      try { window.__weaverEmit(json); } catch (e) { /* binding not ready yet */ }
    }
  };
})();
`;

/**
 * DOM content script — observes the React Flow canvas and emits typed bridge
 * events. Byte-equivalent in intent to the Kotlin StitchContentScript; the
 * selectors live here so a server-side smoke test can catch Stitch DOM drift.
 */
export const CONTENT_SCRIPT = `
(function(){
  if (window.__weaverBridge) return;
  var A = window.Android;
  if (!A || typeof A.post !== 'function') { console.warn('[weaver] bridge missing'); return; }
  function emit(t,p){ try { A.post(JSON.stringify(Object.assign({type:t},p||{}))); } catch(e){} }

  var NODE='[data-testid^="rf__node-"]', ICON='[data-testid="node-icon"]';
  function translate(el){var m=/translate\\((-?\\d+(?:\\.\\d+)?)px[,\\s]+(-?\\d+(?:\\.\\d+)?)px\\)/.exec(el.style.transform||'');return m?{x:parseFloat(m[1]),y:parseFloat(m[2])}:{x:0,y:0};}
  function nodeType(el){var c=el.className||'';if(/node-design-system/.test(c))return'DesignSystem';if(/node-screen/.test(c))return'Screen';var i=el.querySelector(ICON),t=i?(i.textContent||'').trim():'';if(t==='image')return'Asset';if(t==='palette')return'DesignSystem';if(t==='devices')return'Screen';return'Unknown';}
  function nodeLabel(el){var i=el.querySelector(ICON);if(!i)return null;var p=i.parentElement&&i.parentElement.parentElement;if(!p)return null;var t=p.querySelector('.truncate');return t?(t.textContent||'').trim():null;}
  function nodeThumb(el){var img=el.querySelector('img[src*="googleusercontent"], img[src^="https://"]');return img?img.src:null;}
  function genState(el){if(el.querySelector('img[data-stitch-pending-src]'))return'Streaming';if(el.querySelector('[data-stitch-anim-opacity]'))return'Streaming';return'Complete';}

  function snapshot(){
    var nodes=[],els=document.querySelectorAll(NODE);
    for(var i=0;i<els.length;i++){var el=els[i],pos=translate(el);
      var id=el.getAttribute('data-id')||el.getAttribute('data-testid').replace('rf__node-','');
      nodes.push({id:id,type:nodeType(el),label:nodeLabel(el),x:pos.x,y:pos.y,
        w:parseFloat(el.style.width)||0,h:parseFloat(el.style.height)||0,
        thumb:nodeThumb(el),selected:el.classList.contains('selected')});
      var gs=genState(el); if(gs!=='Complete') emit('generation_progress',{id:id,state:gs});
    }
    emit('nodes_updated',{nodes:nodes});
  }
  function selection(){var ids=[],s=document.querySelectorAll(NODE+'.selected');for(var i=0;i<s.length;i++)ids.push(s[i].getAttribute('data-id'));emit('selection_changed',{ids:ids});}
  function agentLog(){var sp=document.querySelectorAll('span.text-sm.block.whitespace-nowrap.truncate'),e=[],seen={};for(var i=0;i<sp.length;i++){var t=(sp[i].textContent||'').trim();if(!t||seen[t])continue;seen[t]=1;e.push({id:'log-'+i,role:'Agent',text:t,timestamp:Date.now()});}emit('agent_log_updated',{entries:e});}

  var pending=null;
  function schedule(){if(pending)return;pending=setTimeout(function(){pending=null;snapshot();selection();agentLog();},100);}
  var observer=new MutationObserver(schedule);
  function observe(){var r=document.querySelector('.react-flow__viewport')||document.querySelector('.react-flow')||document.body;observer.observe(r,{childList:true,subtree:true,attributes:true,attributeFilter:['class','style','data-id']});}

  window.__weaverBridge={
    receive:function(json){var m;try{m=JSON.parse(json);}catch(e){return;}switch(m.type){
      case 'submit_prompt':{var i=document.querySelector('textarea[placeholder*="change or create"]');if(!i){emit('error',{code:'prompt_missing',message:'composer not found'});return;}var s=Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype,'value').set;s.call(i,m.text);i.dispatchEvent(new Event('input',{bubbles:true}));var b=document.querySelector('[data-testid="generate-button"]');if(b&&!b.disabled)b.click();else i.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true}));break;}
      case 'select_node':{var n=document.querySelector('[data-id="'+CSS.escape(m.id)+'"]');if(n)n.click();break;}
      case 'clear_selection':{var bg=document.querySelector('[data-testid="rf__background"]')||document.querySelector('.react-flow__pane');if(bg){bg.dispatchEvent(new MouseEvent('mousedown',{bubbles:true,clientX:1,clientY:1}));bg.dispatchEvent(new MouseEvent('mouseup',{bubbles:true,clientX:1,clientY:1}));bg.dispatchEvent(new MouseEvent('click',{bubbles:true,clientX:1,clientY:1}));}break;}
      case 'viewport_changed':{window.dispatchEvent(new Event('resize'));break;}
      case 'synthesize_input':{var e=m.event||{},t=e.target?document.querySelector(e.target):document.activeElement;if(!t)return;if(e.event==='click')t.click();else if(e.event==='keydown')t.dispatchEvent(new KeyboardEvent('keydown',{key:e.key,bubbles:true}));break;}
    }}
  };

  var tries=0;(function init(){var c=document.querySelector('.react-flow__viewport, .react-flow');if(c){observe();schedule();}else if(tries++<100){setTimeout(init,100);}else{emit('error',{code:'selector_breakage',message:'react-flow root never appeared after 10s'});}})();
})();
`;

/**
 * fetch interceptor — tees Stitch's StreamCreateSession + batchexecute f6CJY
 * into typed bridge events. Server-side twin of StitchFetchInterceptor.
 */
export const FETCH_INTERCEPTOR = `
(function(){
  if (window.__weaverFetchHooked) return;
  window.__weaverFetchHooked = true;
  function emit(t,p){var A=window.Android;if(!A)return;try{A.post(JSON.stringify(Object.assign({type:t},p||{})));}catch(e){}}
  function strip(s){if(s.indexOf(")]}'")===0){var n=s.indexOf('\\n');return n>=0?s.substring(n+1):'';}return s;}
  function frames(buf){var out=[];while(true){var nl=buf.indexOf('\\n');if(nl<0)break;var head=buf.substring(0,nl).trim();if(head===''){buf=buf.substring(nl+1);continue;}var sz=parseInt(head,10);if(isNaN(sz))break;if(buf.length<nl+sz)break;out.push(buf.substring(nl+1,nl+sz));buf=buf.substring(nl+sz);}return{buf:buf,out:out};}
  function stages(node){var r=[];(function walk(n){if(!Array.isArray(n))return;if(n.length>=3&&typeof n[0]==='string'&&n[0].length<32&&typeof n[1]==='string'&&n[1].length<64&&typeof n[2]==='string'&&n[2].length>0&&n[2].length<200&&(n.length===3||typeof n[3]==='number')){r.push({id:n[0],key:n[1],label:n[2],status:n[3]||0});return;}for(var i=0;i<n.length;i++)walk(n[i]);})(node);return r;}
  function tokens(node){var o={};(function walk(n){if(!Array.isArray(n))return;if(n.length===2&&typeof n[0]==='string'&&typeof n[1]==='string'&&/^#[0-9a-fA-F]{3,8}$/.test(n[1])){o[n[0]]=n[1];return;}for(var i=0;i<n.length;i++)walk(n[i]);})(node);return o;}
  function streamFrame(raw){var p;try{p=JSON.parse(raw);}catch(e){return;}if(!Array.isArray(p)||!p[0])return;var row=p[0];if(row[0]==='e'&&typeof row[1]==='number'){emit('session_finished',{sessionId:window.__weaverLastSessionId||'',totalBytes:row[4]||0});return;}if(row[0]!=='wrb.fr'||typeof row[2]!=='string')return;var inner;try{inner=JSON.parse(row[2]);}catch(e){return;}if(!Array.isArray(inner))return;var sp=inner[0];if(typeof sp==='string'&&sp.indexOf('projects/')===0){var parts=sp.split('/'),pid=parts[1],sid=parts[3];if(sid&&sid!==window.__weaverLastSessionId){window.__weaverLastSessionId=sid;emit('session_started',{projectId:pid,sessionId:sid});}var seq=typeof inner[2]==='number'?inner[2]:0,st=stages(inner);if(st.length||seq>0)emit('session_progress',{sessionId:sid,seqNo:seq,stages:st});}}
  function batchFrame(raw){var p;try{p=JSON.parse(raw);}catch(e){return;}if(!Array.isArray(p)||!p[0])return;var row=p[0];if(row[0]!=='wrb.fr'||typeof row[2]!=='string')return;var inner;try{inner=JSON.parse(row[2]);}catch(e){return;}if(!Array.isArray(inner)||typeof inner[0]!=='string'||inner[0].indexOf('projects/')!==0)return;var pid=inner[0].split('/')[1],tk=tokens(inner);if(Object.keys(tk).length)emit('project_theme',{projectId:pid,tokens:tk});}
  function teeStream(res){var c;try{c=res.clone();}catch(e){return;}if(!c.body||!c.body.getReader)return;var rd=c.body.getReader(),dec=new TextDecoder(),buf='',stripped=false;(function pump(){rd.read().then(function(r){if(r.done)return;buf+=dec.decode(r.value,{stream:true});if(!stripped){var s=strip(buf);if(s!==buf){buf=s;stripped=true;}else if(buf.length>12)stripped=true;}if(stripped){var f=frames(buf);buf=f.buf;for(var i=0;i<f.out.length;i++)streamFrame(f.out[i]);}pump();}).catch(function(){});})();}
  function teeBatch(res){var c;try{c=res.clone();}catch(e){return;}c.text().then(function(t){var f=frames(strip(t));for(var i=0;i<f.out.length;i++)batchFrame(f.out[i]);}).catch(function(){});}
  var of=window.fetch;if(!of)return;
  window.fetch=function(input,init){return of.call(this,input,init).then(function(res){try{var u=typeof input==='string'?input:(input&&input.url)||'';if(u.indexOf('StreamCreateSession')>=0)teeStream(res);else if(u.indexOf('batchexecute')>=0&&u.indexOf('rpcids=f6CJY')>=0)teeBatch(res);}catch(e){}return res;});};
})();
`;
