package com.weaver.app.webview

internal object StitchContentScript {

    // Stitch's DOM is owned by Google and will change. Keep selectors centralized here
    // so a nightly smoke test can detect breakage and one file holds the patch surface.
    private const val NODE_SELECTOR = "[data-stitch-canvas] [data-design-node]"
    private const val PROMPT_INPUT_SELECTOR = "[data-stitch-prompt] textarea"
    private const val EXPORT_TRIGGER_SELECTOR = "[data-stitch-export]"

    val source: String = buildString {
        append("(function(){")
        append("if(window.__weaverBridge)return;")
        append("var A=window.Android;")
        append("if(!A||typeof A.post!=='function'){console.warn('[weaver] native bridge missing');return;}")
        append("function emit(t,p){try{A.post(JSON.stringify(Object.assign({type:t},p||{})));}catch(e){A.post(JSON.stringify({type:'error',code:'emit_failed',message:String(e)}));}}")
        append("function snapshotNodes(){var nodes=[];document.querySelectorAll('").append(NODE_SELECTOR).append("').forEach(function(el){var r=el.getBoundingClientRect();nodes.push({id:el.getAttribute('data-design-node'),x:r.left,y:r.top,w:r.width,h:r.height,thumb:el.getAttribute('data-thumb-url')||null,revision:Number(el.getAttribute('data-revision')||0)});});emit('nodes_updated',{nodes:nodes});}")
        append("function selectionFromDom(){var ids=[];document.querySelectorAll('[data-design-node][data-selected=\"true\"]').forEach(function(el){ids.push(el.getAttribute('data-design-node'));});emit('selection_changed',{ids:ids});}")
        append("function thumbnailFor(id){var el=document.querySelector('[data-design-node=\"'+CSS.escape(id)+'\"] canvas, [data-design-node=\"'+CSS.escape(id)+'\"] img');if(!el)return null;try{if(el.tagName==='CANVAS')return el.toDataURL('image/png');if(el.tagName==='IMG'&&el.src)return el.src;}catch(e){return null;}return null;}")
        append("var observer=new MutationObserver(function(){snapshotNodes();selectionFromDom();});")
        append("observer.observe(document.body,{childList:true,subtree:true,attributes:true,attributeFilter:['data-selected','data-revision','data-thumb-url']});")
        append("window.__weaverBridge={")
        append("receive:function(json){var m;try{m=JSON.parse(json);}catch(e){return;}switch(m.type){")
        append("case 'submit_prompt':{var i=document.querySelector('").append(PROMPT_INPUT_SELECTOR).append("');if(!i){emit('error',{code:'prompt_missing',message:'prompt input not found'});return;}var s=Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype,'value').set;s.call(i,m.text);i.dispatchEvent(new Event('input',{bubbles:true}));var f=i.closest('form');if(f){f.requestSubmit?f.requestSubmit():f.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));}break;}")
        append("case 'select_node':{var n=document.querySelector('[data-design-node=\"'+CSS.escape(m.id)+'\"]');if(n)n.click();break;}")
        append("case 'request_export':{var b=document.querySelector('").append(EXPORT_TRIGGER_SELECTOR).append("[data-kind=\"'+CSS.escape(m.kind)+'\"]');if(b)b.click();else emit('error',{code:'export_unavailable',message:'export '+m.kind+' not exposed'});break;}")
        append("case 'viewport_changed':{window.dispatchEvent(new Event('resize'));break;}")
        append("case 'synthesize_input':{var e=m.event||{};var t=e.target?document.querySelector(e.target):document.activeElement;if(!t)return;if(e.event==='click')t.click();else if(e.event==='keydown')t.dispatchEvent(new KeyboardEvent('keydown',{key:e.key,bubbles:true}));break;}")
        append("}},")
        append("requestThumbnail:function(id){var d=thumbnailFor(id);if(d)emit('asset_ready',{id:id,kind:'Thumbnail',data:d});}")
        append("};")
        append("setTimeout(function(){snapshotNodes();selectionFromDom();},750);")
        append("})();")
    }
}
