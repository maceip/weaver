package com.weaver.app.webview

/**
 * Content script injected into the Stitch editor. The DOM is React Flow inside
 * an `app-companion-430619.appspot.com` page (host the WebView directly at that
 * URL — the public `stitch.withgoogle.com` page wraps it in a sandbox+srcdoc
 * iframe that we can't reach from the parent).
 *
 * All Stitch-side selectors live in this file. When Stitch changes their DOM,
 * patch here — every other layer reads through the bridge schema.
 *
 * Sourced from a real DOM export, not guesses:
 *   [data-testid^="rf__node-"] .react-flow__node
 *   data-id="<uuid>"
 *   class contains react-flow__node-node-screen|node-design-system|...
 *   class contains "selected" when the node is selected
 *   style="transform: translate(<x>px,<y>px); width:<w>px; height:<h>px"
 *   inner <span data-testid="node-icon">{icon}</span> + sibling .truncate label
 *   inner <img src="https://lh3.googleusercontent.com/aida/...">
 */
internal object StitchContentScript {

    private const val NODE_SELECTOR = "[data-testid^=\"rf__node-\"]"
    private const val LABEL_ICON_SELECTOR = "[data-testid=\"node-icon\"]"

    val source: String = buildString {
        append("(function(){")
        append("if(window.__weaverBridge)return;")
        append("var A=window.Android;")
        append("if(!A||typeof A.post!=='function'){console.warn('[weaver] native bridge missing');return;}")
        append("function emit(t,p){try{A.post(JSON.stringify(Object.assign({type:t},p||{})));}catch(e){A.post(JSON.stringify({type:'error',code:'emit_failed',message:String(e)}));}}")

        // Parse "translate(123px, 456px)" out of the React Flow transform style.
        append("function parseTranslate(el){var m=/translate\\((-?\\d+(?:\\.\\d+)?)px[,\\s]+(-?\\d+(?:\\.\\d+)?)px\\)/.exec(el.style.transform||'');return m?{x:parseFloat(m[1]),y:parseFloat(m[2])}:{x:0,y:0};}")

        // Type discriminator: class-name first (most specific), icon fallback.
        append("function nodeType(el){var c=el.className||'';if(/react-flow__node-node-design-system/.test(c))return'DesignSystem';if(/react-flow__node-node-screen/.test(c))return'Screen';var icon=el.querySelector('").append(LABEL_ICON_SELECTOR).append("');var t=icon?(icon.textContent||'').trim():'';if(t==='image')return'Asset';if(t==='palette')return'DesignSystem';if(t==='devices')return'Screen';return'Unknown';}")

        // Label: text in the .truncate span sibling of the icon span.
        append("function nodeLabel(el){var icon=el.querySelector('").append(LABEL_ICON_SELECTOR).append("');if(!icon)return null;var p=icon.parentElement&&icon.parentElement.parentElement;if(!p)return null;var t=p.querySelector('.truncate');return t?(t.textContent||'').trim():null;}")

        // Thumbnail: first <img src=...> inside the node body. Stitch hosts these on lh3.googleusercontent.com.
        append("function nodeThumb(el){var img=el.querySelector('img[src*=\"googleusercontent\"], img[src^=\"https://\"]');return img?img.src:null;}")

        append("function snapshotNodes(){var nodes=[],els=document.querySelectorAll('").append(NODE_SELECTOR).append("');for(var i=0;i<els.length;i++){var el=els[i];var pos=parseTranslate(el);var w=parseFloat(el.style.width)||0;var h=parseFloat(el.style.height)||0;nodes.push({id:el.getAttribute('data-id')||el.getAttribute('data-testid').replace('rf__node-',''),type:nodeType(el),label:nodeLabel(el),x:pos.x,y:pos.y,w:w,h:h,thumb:nodeThumb(el),selected:el.classList.contains('selected')});}emit('nodes_updated',{nodes:nodes});}")

        append("function selectionFromDom(){var ids=[],sel=document.querySelectorAll('").append(NODE_SELECTOR).append(".selected');for(var i=0;i<sel.length;i++){ids.push(sel[i].getAttribute('data-id'));}emit('selection_changed',{ids:ids});}")

        // Debounce DOM mutations — React Flow batches its own updates, but pan/zoom
        // can fire dozens per frame. 100ms is generous; 16ms (one frame) is the floor.
        append("var pending=null;function schedule(){if(pending)return;pending=setTimeout(function(){pending=null;snapshotNodes();selectionFromDom();},100);}")

        append("var observer=new MutationObserver(schedule);")
        // Observe the React Flow viewport root; falls back to body if not present yet.
        append("function startObserving(){var root=document.querySelector('.react-flow__viewport')||document.querySelector('.react-flow')||document.body;observer.observe(root,{childList:true,subtree:true,attributes:true,attributeFilter:['class','style','data-id']});}")

        append("window.__weaverBridge={")
        append("receive:function(json){var m;try{m=JSON.parse(json);}catch(e){return;}switch(m.type){")
        append("case 'submit_prompt':{var i=document.querySelector('textarea[placeholder], textarea');if(!i){emit('error',{code:'prompt_missing',message:'prompt textarea not found'});return;}var s=Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype,'value').set;s.call(i,m.text);i.dispatchEvent(new Event('input',{bubbles:true}));i.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true}));break;}")
        append("case 'select_node':{var n=document.querySelector('[data-id=\"'+CSS.escape(m.id)+'\"]');if(n)n.click();break;}")
        append("case 'clear_selection':{var bg=document.querySelector('[data-testid=\"rf__background\"], .react-flow__pane');if(bg)bg.dispatchEvent(new MouseEvent('mousedown',{bubbles:true,clientX:1,clientY:1}));break;}")
        append("case 'viewport_changed':{window.dispatchEvent(new Event('resize'));break;}")
        append("case 'synthesize_input':{var e=m.event||{};var t=e.target?document.querySelector(e.target):document.activeElement;if(!t)return;if(e.event==='click')t.click();else if(e.event==='keydown')t.dispatchEvent(new KeyboardEvent('keydown',{key:e.key,bubbles:true}));break;}")
        append("}}};")

        // First snapshot once the canvas is in the DOM. Probe up to 10s.
        append("var tries=0;(function init(){var canvas=document.querySelector('.react-flow__viewport, .react-flow');if(canvas){startObserving();schedule();}else if(tries++<100){setTimeout(init,100);}else{emit('error',{code:'canvas_missing',message:'react-flow root never appeared'});}})();")

        append("})();")
    }
}
