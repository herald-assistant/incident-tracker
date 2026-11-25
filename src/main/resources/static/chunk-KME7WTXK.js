import{a as qe}from"./chunk-XCRG224L.js";import{d as Qe,e as Ke,g as Ge}from"./chunk-TO5PAZL7.js";import{$ as Ye,L as Be,N as Ne,O as Le,S as je,W as ze,X as He,ba as Xe,ca as Ue,fa as Ve,ia as We,ma as $e,p as Ee,q as Oe,qa as te,y as ee,z as Fe}from"./chunk-R6VYYKR7.js";import{Aa as pe,Ab as j,Bb as z,F as se,Gb as H,Hb as m,Ib as u,Jb as G,Kb as q,Kc as Ae,Lb as ke,Ob as we,P as O,Pb as Ce,Pc as X,Q,R as le,Rc as b,Sb as h,Tb as xe,Ub as _,Vb as Z,Wb as Y,Xb as Se,Y as me,Ya as f,Yb as De,Zb as R,_ as w,_b as P,a as W,aa as l,b as oe,bb as he,cb as _e,cc as Re,d as I,eb as ge,ec as J,fa as C,fc as Pe,g as A,ga as x,gb as fe,gc as d,ha as ue,hb as be,hc as Te,ja as F,ka as ce,l as $,lb as S,mb as ye,na as B,nb as K,oa as de,pb as Me,rb as ve,sa as N,uc as Ie,y as E,yb as D,z as re,za as L}from"./chunk-ATNZ7NZF.js";function Ze(i,r=""){return[...i?.sections??[]].sort((e,t)=>{if(r&&e.id!==t.id){if(e.id===r)return 1;if(t.id===r)return-1}return(e.order??Number.MAX_SAFE_INTEGER)-(t.order??Number.MAX_SAFE_INTEGER)})}function xt(...i){let r=i.filter(t=>!!t);return{references:[...new Map(r.flatMap(t=>t.references).map(t=>[JSON.stringify(t),t])).values()],visibilityLimits:[...new Set(r.flatMap(t=>t.visibilityLimits))],openQuestions:[...new Set(r.flatMap(t=>t.openQuestions))],gaps:[...new Set(r.flatMap(t=>t.gaps))],warnings:[...new Set(r.flatMap(t=>t.warnings))],confidence:r.map(t=>t.confidence).filter(Boolean).join(" / ")}}function Rt(i,r,e=""){let t=r.trim(),n=e.trim();return{fileName:i.endsWith(".md")?i:`${i}.md`,markdown:t,markdownWithMeta:[t,n].filter(Boolean).join(`

`)}}function Pt(i,r,e=""){let t=Ze(i,e),n=t.filter(s=>!e||s.id!==e),o=[`# ${i.header?.trim()||"Wynik analizy"}`,i.subHeader?.trim()?`_${i.subHeader.trim()}_`:"",i.markdownSummary?.trim()||""].filter(Boolean),c=[...o,...n.map(s=>[Je(s.id),`## ${s.title?.trim()||s.id||"Sekcja"}`,s.markdown?.trim()||""].filter(Boolean).join(`

`))].filter(Boolean).join(`

`),g=[...o,...t.map(s=>[Je(s.id),`## ${s.title?.trim()||s.id||"Sekcja"}`,s.markdown?.trim()||"",et(s.meta,"Meta sekcji")].filter(Boolean).join(`

`)),et(i.meta,e?"Ograniczenia i \u017Ar\xF3d\u0142a raportu":"Meta raportu")].filter(Boolean).join(`

`);return{fileName:r,markdown:c,markdownWithMeta:g}}function Je(i){let r=String(i||"").replace(/&/g,"&amp;").replace(/"/g,"&quot;").replace(/</g,"&lt;").replace(/>/g,"&gt;");return r?`<a id="report-section-${r}"></a>`:""}function et(i,r="Meta"){let e=[p("Referencje",(i?.references??[]).map(rt)),p("Limity widoczno\u015Bci",i?.visibilityLimits??[]),p("Otwarte pytania",i?.openQuestions??[]),p("Luki",i?.gaps??[]),p("Ostrze\u017Cenia",i?.warnings??[])].filter(Boolean);return e.length?[`### ${r}`,...e].join(`

`):""}function p(i,r){let e=r.map(t=>t.trim()).filter(Boolean);return e.length?[`**${i}**`,...e.map(t=>`- ${t}`)].join(`
`):""}function Tt(i,r,e,t,n){let a=[`# ${r}`,e,"## Wynik zbiorczy",...t.map(s=>`- ${s}`)],o=s=>[`### ${s.title}`,`Status: ${s.status}`,...s.result.map(v=>`- ${v}`),p("Wymiary",s.dimensions),p("Uzasadnienie",s.evidence)].filter(Boolean).join(`

`),c=[...a,"## Delivery Units",...n.map(o)].join(`

`),g=[...a,"## Delivery Units",...n.map(s=>[o(s),p("Referencje",s.references),p("Limity widoczno\u015Bci",s.limits),p("Luki i ostrze\u017Cenia",s.warnings)].filter(Boolean).join(`

`))].join(`

`);return{fileName:i,markdown:c,markdownWithMeta:g}}function rt(i){return[i.label,i.type,i.target,i.description].map(r=>r?.trim()||"").filter(Boolean).join(" | ")}var pt=["mat-menu-item",""],ht=[[["mat-icon"],["","matMenuItemIcon",""]],"*"],_t=["mat-icon, [matMenuItemIcon]","*"];function gt(i,r){i&1&&(ue(),m(0,"svg",2),G(1,"polygon",3),u())}var ft=["*"];function bt(i,r){if(i&1){let e=we();q(0,"div",0),xe("click",function(){C(e);let n=_();return x(n.closed.emit("click"))})("animationstart",function(n){C(e);let a=_();return x(a._onAnimationStart(n.animationName))})("animationend",function(n){C(e);let a=_();return x(a._onAnimationDone(n.animationName))})("animationcancel",function(n){C(e);let a=_();return x(a._onAnimationDone(n.animationName))}),q(1,"div",1),Y(2),ke()()}if(i&2){let e=_();Pe(e._classList),J("mat-menu-panel-animations-disabled",e._animationsDisabled)("mat-menu-panel-exit-animation",e._panelAnimationState==="void")("mat-menu-panel-animating",e._isAnimating()),Ce("id",e.panelId),D("aria-label",e.ariaLabel||null)("aria-labelledby",e.ariaLabelledby||null)("aria-describedby",e.ariaDescribedby||null)}}var ie=new w("MAT_MENU_PANEL"),T=(()=>{class i{_elementRef=l(L);_document=l(ce);_focusMonitor=l(ee);_parentMenu=l(ie,{optional:!0});_changeDetectorRef=l(X);role="menuitem";disabled=!1;disableRipple=!1;_hovered=new A;_focused=new A;_highlighted=!1;_triggersSubmenu=!1;constructor(){l(Fe).load(Ke),this._parentMenu?.addItem?.(this)}focus(e,t){this._focusMonitor&&e?this._focusMonitor.focusVia(this._getHostElement(),e,t):this._getHostElement().focus(t),this._focused.next(this)}ngAfterViewInit(){this._focusMonitor&&this._focusMonitor.monitor(this._elementRef,!1)}ngOnDestroy(){this._focusMonitor&&this._focusMonitor.stopMonitoring(this._elementRef),this._parentMenu&&this._parentMenu.removeItem&&this._parentMenu.removeItem(this),this._hovered.complete(),this._focused.complete()}_getTabIndex(){return this.disabled?"-1":"0"}_getHostElement(){return this._elementRef.nativeElement}_checkDisabled(e){this.disabled&&(e.preventDefault(),e.stopPropagation())}_handleMouseEnter(){this._hovered.next(this)}getLabel(){let e=this._elementRef.nativeElement.cloneNode(!0),t=e.querySelectorAll("mat-icon, .material-icons");for(let n=0;n<t.length;n++)t[n].remove();return e.textContent?.trim()||""}_setHighlighted(e){this._highlighted=e,this._changeDetectorRef.markForCheck()}_setTriggersSubmenu(e){this._triggersSubmenu=e,this._changeDetectorRef.markForCheck()}_hasFocus(){return this._document&&this._document.activeElement===this._getHostElement()}static \u0275fac=function(t){return new(t||i)};static \u0275cmp=S({type:i,selectors:[["","mat-menu-item",""]],hostAttrs:[1,"mat-mdc-menu-item","mat-focus-indicator"],hostVars:8,hostBindings:function(t,n){t&1&&h("click",function(o){return n._checkDisabled(o)})("mouseenter",function(){return n._handleMouseEnter()}),t&2&&(D("role",n.role)("tabindex",n._getTabIndex())("aria-disabled",n.disabled)("disabled",n.disabled||null),J("mat-mdc-menu-item-highlighted",n._highlighted)("mat-mdc-menu-item-submenu-trigger",n._triggersSubmenu))},inputs:{role:"role",disabled:[2,"disabled","disabled",b],disableRipple:[2,"disableRipple","disableRipple",b]},exportAs:["matMenuItem"],attrs:pt,ngContentSelectors:_t,decls:5,vars:3,consts:[[1,"mat-mdc-menu-item-text"],["matRipple","",1,"mat-mdc-menu-ripple",3,"matRippleDisabled","matRippleTrigger"],["viewBox","0 0 5 10","focusable","false","aria-hidden","true",1,"mat-mdc-menu-submenu-icon"],["points","0,0 5,5 0,10"]],template:function(t,n){t&1&&(Z(ht),Y(0),m(1,"span",0),Y(2,1),u(),G(3,"div",1),j(4,gt,2,0,":svg:svg",2)),t&2&&(f(3),H("matRippleDisabled",n.disableRipple||n.disabled)("matRippleTrigger",n._getHostElement()),f(),z(n._triggersSubmenu?4:-1))},dependencies:[Qe],encapsulation:2,changeDetection:0})}return i})();var yt=new w("MatMenuContent");var Mt=new w("mat-menu-default-options",{providedIn:"root",factory:()=>({overlapTrigger:!1,xPosition:"after",yPosition:"below",backdropClass:"cdk-overlay-transparent-backdrop"})}),ne="_mat-menu-enter",U="_mat-menu-exit",M=(()=>{class i{_elementRef=l(L);_changeDetectorRef=l(X);_injector=l(F);_keyManager;_xPosition;_yPosition;_firstItemFocusRef;_exitFallbackTimeout;_animationsDisabled=te();_allItems;_directDescendantItems=new pe;_classList={};_panelAnimationState="void";_animationDone=new A;_isAnimating=N(!1);parentMenu;direction;overlayPanelClass;backdropClass;ariaLabel;ariaLabelledby;ariaDescribedby;get xPosition(){return this._xPosition}set xPosition(e){this._xPosition=e,this.setPositionClasses()}get yPosition(){return this._yPosition}set yPosition(e){this._yPosition=e,this.setPositionClasses()}templateRef;items;lazyContent;overlapTrigger=!1;hasBackdrop;set panelClass(e){let t=this._previousPanelClass,n=W({},this._classList);t&&t.length&&t.split(" ").forEach(a=>{n[a]=!1}),this._previousPanelClass=e,e&&e.length&&(e.split(" ").forEach(a=>{n[a]=!0}),this._elementRef.nativeElement.className=""),this._classList=n}_previousPanelClass;get classList(){return this.panelClass}set classList(e){this.panelClass=e}closed=new B;close=this.closed;panelId=l(Le).getId("mat-menu-panel-");constructor(){let e=l(Mt);this.overlayPanelClass=e.overlayPanelClass||"",this._xPosition=e.xPosition,this._yPosition=e.yPosition,this.backdropClass=e.backdropClass,this.overlapTrigger=e.overlapTrigger,this.hasBackdrop=e.hasBackdrop}ngOnInit(){this.setPositionClasses()}ngAfterContentInit(){this._updateDirectDescendants(),this._keyManager=new Ne(this._directDescendantItems).withWrap().withTypeAhead().withHomeAndEnd(),this._keyManager.tabOut.subscribe(()=>this.closed.emit("tab")),this._directDescendantItems.changes.pipe(O(this._directDescendantItems),Q(e=>E(...e.map(t=>t._focused)))).subscribe(e=>this._keyManager.updateActiveItem(e)),this._directDescendantItems.changes.subscribe(e=>{let t=this._keyManager;if(this._panelAnimationState==="enter"&&t.activeItem?._hasFocus()){let n=e.toArray(),a=Math.max(0,Math.min(n.length-1,t.activeItemIndex||0));n[a]&&!n[a].disabled?t.setActiveItem(a):t.setNextItemActive()}})}ngOnDestroy(){this._keyManager?.destroy(),this._directDescendantItems.destroy(),this.closed.complete(),this._firstItemFocusRef?.destroy(),clearTimeout(this._exitFallbackTimeout)}_hovered(){return this._directDescendantItems.changes.pipe(O(this._directDescendantItems),Q(t=>E(...t.map(n=>n._hovered))))}addItem(e){}removeItem(e){}_handleKeydown(e){let t=e.keyCode,n=this._keyManager;switch(t){case 27:Be(e)||(e.preventDefault(),this.closed.emit("keydown"));break;case 37:this.parentMenu&&this.direction==="ltr"&&this.closed.emit("keydown");break;case 39:this.parentMenu&&this.direction==="rtl"&&this.closed.emit("keydown");break;default:(t===38||t===40)&&n.setFocusOrigin("keyboard"),n.onKeydown(e);return}}focusFirstItem(e="program"){this._firstItemFocusRef?.destroy(),this._firstItemFocusRef=he(()=>{let t=this._resolvePanel();if(!t||!t.contains(document.activeElement)){let n=this._keyManager;n.setFocusOrigin(e).setFirstItemActive(),!n.activeItem&&t&&t.focus()}},{injector:this._injector})}resetActiveItem(){this._keyManager.setActiveItem(-1)}setElevation(e){}setPositionClasses(e=this.xPosition,t=this.yPosition){this._classList=oe(W({},this._classList),{"mat-menu-before":e==="before","mat-menu-after":e==="after","mat-menu-above":t==="above","mat-menu-below":t==="below"}),this._changeDetectorRef.markForCheck()}_onAnimationDone(e){let t=e===U;(t||e===ne)&&(t&&(clearTimeout(this._exitFallbackTimeout),this._exitFallbackTimeout=void 0),this._animationDone.next(t?"void":"enter"),this._isAnimating.set(!1))}_onAnimationStart(e){(e===ne||e===U)&&this._isAnimating.set(!0)}_setIsOpen(e){if(this._panelAnimationState=e?"enter":"void",e){if(this._keyManager.activeItemIndex===0){let t=this._resolvePanel();t&&(t.scrollTop=0)}}else this._animationsDisabled||(this._exitFallbackTimeout=setTimeout(()=>this._onAnimationDone(U),200));this._animationsDisabled&&setTimeout(()=>{this._onAnimationDone(e?ne:U)}),this._changeDetectorRef.markForCheck()}_updateDirectDescendants(){this._allItems.changes.pipe(O(this._allItems)).subscribe(e=>{this._directDescendantItems.reset(e.filter(t=>t._parentMenu===this)),this._directDescendantItems.notifyOnChanges()})}_resolvePanel(){let e=null;return this._directDescendantItems.length&&(e=this._directDescendantItems.first._getHostElement().closest('[role="menu"]')),e}static \u0275fac=function(t){return new(t||i)};static \u0275cmp=S({type:i,selectors:[["mat-menu"]],contentQueries:function(t,n,a){if(t&1&&Se(a,yt,5)(a,T,5)(a,T,4),t&2){let o;R(o=P())&&(n.lazyContent=o.first),R(o=P())&&(n._allItems=o),R(o=P())&&(n.items=o)}},viewQuery:function(t,n){if(t&1&&De(_e,5),t&2){let a;R(a=P())&&(n.templateRef=a.first)}},hostVars:3,hostBindings:function(t,n){t&2&&D("aria-label",null)("aria-labelledby",null)("aria-describedby",null)},inputs:{backdropClass:"backdropClass",ariaLabel:[0,"aria-label","ariaLabel"],ariaLabelledby:[0,"aria-labelledby","ariaLabelledby"],ariaDescribedby:[0,"aria-describedby","ariaDescribedby"],xPosition:"xPosition",yPosition:"yPosition",overlapTrigger:[2,"overlapTrigger","overlapTrigger",b],hasBackdrop:[2,"hasBackdrop","hasBackdrop",e=>e==null?null:b(e)],panelClass:[0,"class","panelClass"],classList:"classList"},outputs:{closed:"closed",close:"close"},exportAs:["matMenu"],features:[Ie([{provide:ie,useExisting:i}])],ngContentSelectors:ft,decls:1,vars:0,consts:[["tabindex","-1","role","menu",1,"mat-mdc-menu-panel",3,"click","animationstart","animationend","animationcancel","id"],[1,"mat-mdc-menu-content"]],template:function(t,n){t&1&&(Z(),ve(0,bt,3,12,"ng-template"))},styles:[`mat-menu {
  display: none;
}

.mat-mdc-menu-content {
  margin: 0;
  padding: 8px 0;
  outline: 0;
}
.mat-mdc-menu-content,
.mat-mdc-menu-content .mat-mdc-menu-item .mat-mdc-menu-item-text {
  -moz-osx-font-smoothing: grayscale;
  -webkit-font-smoothing: antialiased;
  flex: 1;
  white-space: normal;
  font-family: var(--mat-menu-item-label-text-font, var(--mat-sys-label-large-font));
  line-height: var(--mat-menu-item-label-text-line-height, var(--mat-sys-label-large-line-height));
  font-size: var(--mat-menu-item-label-text-size, var(--mat-sys-label-large-size));
  letter-spacing: var(--mat-menu-item-label-text-tracking, var(--mat-sys-label-large-tracking));
  font-weight: var(--mat-menu-item-label-text-weight, var(--mat-sys-label-large-weight));
}

@keyframes _mat-menu-enter {
  from {
    opacity: 0;
    transform: scale(0.8);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
@keyframes _mat-menu-exit {
  from {
    opacity: 1;
  }
  to {
    opacity: 0;
  }
}
.mat-mdc-menu-panel {
  min-width: 112px;
  max-width: 280px;
  overflow: auto;
  box-sizing: border-box;
  outline: 0;
  animation: _mat-menu-enter 120ms cubic-bezier(0, 0, 0.2, 1);
  border-radius: var(--mat-menu-container-shape, var(--mat-sys-corner-extra-small));
  background-color: var(--mat-menu-container-color, var(--mat-sys-surface-container));
  box-shadow: var(--mat-menu-container-elevation-shadow, 0px 3px 1px -2px rgba(0, 0, 0, 0.2), 0px 2px 2px 0px rgba(0, 0, 0, 0.14), 0px 1px 5px 0px rgba(0, 0, 0, 0.12));
  will-change: transform, opacity;
}
.mat-mdc-menu-panel.mat-menu-panel-exit-animation {
  animation: _mat-menu-exit 100ms 25ms linear forwards;
}
.mat-mdc-menu-panel.mat-menu-panel-animations-disabled {
  animation: none;
}
.mat-mdc-menu-panel.mat-menu-panel-animating {
  pointer-events: none;
}
.mat-mdc-menu-panel.mat-menu-panel-animating:has(.mat-mdc-menu-content:empty) {
  display: none;
}
@media (forced-colors: active) {
  .mat-mdc-menu-panel {
    outline: solid 1px;
  }
}
.mat-mdc-menu-panel .mat-divider {
  border-top-color: var(--mat-menu-divider-color, var(--mat-sys-surface-variant));
  margin-bottom: var(--mat-menu-divider-bottom-spacing, 8px);
  margin-top: var(--mat-menu-divider-top-spacing, 8px);
}

.mat-mdc-menu-item {
  display: flex;
  position: relative;
  align-items: center;
  justify-content: flex-start;
  overflow: hidden;
  padding: 0;
  cursor: pointer;
  width: 100%;
  text-align: left;
  box-sizing: border-box;
  color: inherit;
  font-size: inherit;
  background: none;
  text-decoration: none;
  margin: 0;
  min-height: 48px;
  padding-left: var(--mat-menu-item-leading-spacing, 12px);
  padding-right: var(--mat-menu-item-trailing-spacing, 12px);
  -webkit-user-select: none;
  user-select: none;
  cursor: pointer;
  outline: none;
  border: none;
  -webkit-tap-highlight-color: transparent;
}
.mat-mdc-menu-item::-moz-focus-inner {
  border: 0;
}
[dir=rtl] .mat-mdc-menu-item {
  padding-left: var(--mat-menu-item-trailing-spacing, 12px);
  padding-right: var(--mat-menu-item-leading-spacing, 12px);
}
.mat-mdc-menu-item:has(.material-icons, mat-icon, [matButtonIcon]) {
  padding-left: var(--mat-menu-item-with-icon-leading-spacing, 12px);
  padding-right: var(--mat-menu-item-with-icon-trailing-spacing, 12px);
}
[dir=rtl] .mat-mdc-menu-item:has(.material-icons, mat-icon, [matButtonIcon]) {
  padding-left: var(--mat-menu-item-with-icon-trailing-spacing, 12px);
  padding-right: var(--mat-menu-item-with-icon-leading-spacing, 12px);
}
.mat-mdc-menu-item, .mat-mdc-menu-item:visited, .mat-mdc-menu-item:link {
  color: var(--mat-menu-item-label-text-color, var(--mat-sys-on-surface));
}
.mat-mdc-menu-item .mat-icon-no-color,
.mat-mdc-menu-item .mat-mdc-menu-submenu-icon {
  color: var(--mat-menu-item-icon-color, var(--mat-sys-on-surface-variant));
}
.mat-mdc-menu-item[disabled] {
  cursor: default;
  opacity: 0.38;
}
.mat-mdc-menu-item[disabled]::after {
  display: block;
  position: absolute;
  content: "";
  top: 0;
  left: 0;
  bottom: 0;
  right: 0;
}
.mat-mdc-menu-item:focus {
  outline: 0;
}
.mat-mdc-menu-item .mat-icon {
  flex-shrink: 0;
  margin-right: var(--mat-menu-item-spacing, 12px);
  height: var(--mat-menu-item-icon-size, 24px);
  width: var(--mat-menu-item-icon-size, 24px);
}
[dir=rtl] .mat-mdc-menu-item {
  text-align: right;
}
[dir=rtl] .mat-mdc-menu-item .mat-icon {
  margin-right: 0;
  margin-left: var(--mat-menu-item-spacing, 12px);
}
.mat-mdc-menu-item:not([disabled]):hover {
  background-color: var(--mat-menu-item-hover-state-layer-color, color-mix(in srgb, var(--mat-sys-on-surface) calc(var(--mat-sys-hover-state-layer-opacity) * 100%), transparent));
}
.mat-mdc-menu-item:not([disabled]).cdk-program-focused, .mat-mdc-menu-item:not([disabled]).cdk-keyboard-focused, .mat-mdc-menu-item:not([disabled]).mat-mdc-menu-item-highlighted {
  background-color: var(--mat-menu-item-focus-state-layer-color, color-mix(in srgb, var(--mat-sys-on-surface) calc(var(--mat-sys-focus-state-layer-opacity) * 100%), transparent));
}
@media (forced-colors: active) {
  .mat-mdc-menu-item {
    margin-top: 1px;
  }
}

.mat-mdc-menu-submenu-icon {
  width: var(--mat-menu-item-icon-size, 24px);
  height: 10px;
  fill: currentColor;
  padding-left: var(--mat-menu-item-spacing, 12px);
}
[dir=rtl] .mat-mdc-menu-submenu-icon {
  padding-right: var(--mat-menu-item-spacing, 12px);
  padding-left: 0;
}
[dir=rtl] .mat-mdc-menu-submenu-icon polygon {
  transform: scaleX(-1);
  transform-origin: center;
}
@media (forced-colors: active) {
  .mat-mdc-menu-submenu-icon {
    fill: CanvasText;
  }
}

.mat-mdc-menu-item .mat-mdc-menu-ripple {
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  position: absolute;
  pointer-events: none;
}
`],encapsulation:2,changeDetection:0})}return i})(),vt=new w("mat-menu-scroll-strategy",{providedIn:"root",factory:()=>{let i=l(F);return()=>Xe(i)}});var y=new WeakMap,kt=(()=>{class i{_canHaveBackdrop;_element=l(L);_viewContainerRef=l(be);_menuItemInstance=l(T,{optional:!0,self:!0});_dir=l(ze,{optional:!0});_focusMonitor=l(ee);_ngZone=l(de);_injector=l(F);_scrollStrategy=l(vt);_changeDetectorRef=l(X);_animationsDisabled=te();_portal;_overlayRef=null;_menuOpen=!1;_closingActionsSubscription=I.EMPTY;_menuCloseSubscription=I.EMPTY;_pendingRemoval;_parentMaterialMenu;_parentInnerPadding;_openedBy=void 0;get _menu(){return this._menuInternal}set _menu(e){e!==this._menuInternal&&(this._menuInternal=e,this._menuCloseSubscription.unsubscribe(),e&&(this._parentMaterialMenu,this._menuCloseSubscription=e.close.subscribe(t=>{this._destroyMenu(t),(t==="click"||t==="tab")&&this._parentMaterialMenu&&this._parentMaterialMenu.closed.emit(t)})),this._menuItemInstance?._setTriggersSubmenu(this._triggersSubmenu()))}_menuInternal=null;constructor(e){this._canHaveBackdrop=e;let t=l(ie,{optional:!0});this._parentMaterialMenu=t instanceof M?t:void 0}ngOnDestroy(){this._menu&&this._ownsMenu(this._menu)&&y.delete(this._menu),this._pendingRemoval?.unsubscribe(),this._menuCloseSubscription.unsubscribe(),this._closingActionsSubscription.unsubscribe(),this._overlayRef&&(this._overlayRef.dispose(),this._overlayRef=null)}get menuOpen(){return this._menuOpen}get dir(){return this._dir&&this._dir.value==="rtl"?"rtl":"ltr"}_triggersSubmenu(){return!!(this._menuItemInstance&&this._parentMaterialMenu&&this._menu)}_closeMenu(){this._menu?.close.emit()}_openMenu(e){if(this._triggerIsAriaDisabled())return;let t=this._menu;if(this._menuOpen||!t)return;this._pendingRemoval?.unsubscribe();let n=y.get(t);y.set(t,this),n&&n!==this&&n._closeMenu();let a=this._createOverlay(t),o=a.getConfig(),c=o.positionStrategy;this._setPosition(t,c),this._canHaveBackdrop?o.hasBackdrop=t.hasBackdrop==null?!this._triggersSubmenu():t.hasBackdrop:o.hasBackdrop=t.hasBackdrop??!1,a.hasAttached()||(a.attach(this._getPortal(t)),t.lazyContent?.attach(this.menuData)),this._closingActionsSubscription=this._menuClosingActions().subscribe(()=>this._closeMenu()),t.parentMenu=this._triggersSubmenu()?this._parentMaterialMenu:void 0,t.direction=this.dir,e&&t.focusFirstItem(this._openedBy||"program"),this._setIsMenuOpen(!0),t instanceof M&&(t._setIsOpen(!0),t._directDescendantItems.changes.pipe(le(t.close)).subscribe(()=>{c.withLockedPosition(!1).reapplyLastPosition(),c.withLockedPosition(!0)}))}focus(e,t){this._focusMonitor&&e?this._focusMonitor.focusVia(this._element,e,t):this._element.nativeElement.focus(t)}_destroyMenu(e){let t=this._overlayRef,n=this._menu;!t||!this.menuOpen||(this._closingActionsSubscription.unsubscribe(),this._pendingRemoval?.unsubscribe(),n instanceof M&&this._ownsMenu(n)?(this._pendingRemoval=n._animationDone.pipe(se(1)).subscribe(()=>{t.detach(),y.has(n)||n.lazyContent?.detach()}),n._setIsOpen(!1)):(t.detach(),n?.lazyContent?.detach()),n&&this._ownsMenu(n)&&y.delete(n),this.restoreFocus&&(e==="keydown"||!this._openedBy||!this._triggersSubmenu())&&this.focus(this._openedBy),this._openedBy=void 0,this._setIsMenuOpen(!1))}_setIsMenuOpen(e){e!==this._menuOpen&&(this._menuOpen=e,this._menuOpen?this.menuOpened.emit():this.menuClosed.emit(),this._triggersSubmenu()&&this._menuItemInstance._setHighlighted(e),this._changeDetectorRef.markForCheck())}_createOverlay(e){if(!this._overlayRef){let t=this._getOverlayConfig(e);this._subscribeToPositions(e,t.positionStrategy),this._overlayRef=We(this._injector,t),this._overlayRef.keydownEvents().subscribe(n=>{this._menu instanceof M&&this._menu._handleKeydown(n)})}return this._overlayRef}_getOverlayConfig(e){return new Ue({positionStrategy:Ve(this._injector,this._getOverlayOrigin()).withLockedPosition().withGrowAfterOpen().withTransformOriginOn(".mat-menu-panel, .mat-mdc-menu-panel"),backdropClass:e.backdropClass||"cdk-overlay-transparent-backdrop",panelClass:e.overlayPanelClass,scrollStrategy:this._scrollStrategy(),direction:this._dir||"ltr",disableAnimations:this._animationsDisabled})}_subscribeToPositions(e,t){e.setPositionClasses&&t.positionChanges.subscribe(n=>{this._ngZone.run(()=>{let a=n.connectionPair.overlayX==="start"?"after":"before",o=n.connectionPair.overlayY==="top"?"below":"above";e.setPositionClasses(a,o)})})}_setPosition(e,t){let[n,a]=e.xPosition==="before"?["end","start"]:["start","end"],[o,c]=e.yPosition==="above"?["bottom","top"]:["top","bottom"],[g,s]=[o,c],[v,V]=[n,a],k=0;if(this._triggersSubmenu()){if(V=n=e.xPosition==="before"?"start":"end",a=v=n==="end"?"start":"end",this._parentMaterialMenu){if(this._parentInnerPadding==null){let ae=this._parentMaterialMenu.items.first;this._parentInnerPadding=ae?ae._getHostElement().offsetTop:0}k=o==="bottom"?this._parentInnerPadding:-this._parentInnerPadding}}else e.overlapTrigger||(g=o==="top"?"bottom":"top",s=c==="top"?"bottom":"top");t.withPositions([{originX:n,originY:g,overlayX:v,overlayY:o,offsetY:k},{originX:a,originY:g,overlayX:V,overlayY:o,offsetY:k},{originX:n,originY:s,overlayX:v,overlayY:c,offsetY:-k},{originX:a,originY:s,overlayX:V,overlayY:c,offsetY:-k}])}_menuClosingActions(){let e=this._getOutsideClickStream(this._overlayRef),t=this._overlayRef.detachments(),n=this._parentMaterialMenu?this._parentMaterialMenu.closed:$(),a=this._parentMaterialMenu?this._parentMaterialMenu._hovered().pipe(re(o=>this._menuOpen&&o!==this._menuItemInstance)):$();return E(e,n,a,t)}_getPortal(e){return(!this._portal||this._portal.templateRef!==e.templateRef)&&(this._portal=new je(e.templateRef,this._viewContainerRef)),this._portal}_ownsMenu(e){return y.get(e)===this}_triggerIsAriaDisabled(){return b(this._element.nativeElement.getAttribute("aria-disabled"))}static \u0275fac=function(t){fe()};static \u0275dir=K({type:i})}return i})(),it=(()=>{class i extends kt{_cleanupTouchstart;_hoverSubscription=I.EMPTY;get _deprecatedMatMenuTriggerFor(){return this.menu}set _deprecatedMatMenuTriggerFor(e){this.menu=e}get menu(){return this._menu}set menu(e){this._menu=e}menuData;restoreFocus=!0;menuOpened=new B;onMenuOpen=this.menuOpened;menuClosed=new B;onMenuClose=this.menuClosed;constructor(){super(!0);let e=l(ge);this._cleanupTouchstart=e.listen(this._element.nativeElement,"touchstart",t=>{Oe(t)||(this._openedBy="touch")},{passive:!0})}triggersSubmenu(){return super._triggersSubmenu()}toggleMenu(){return this.menuOpen?this.closeMenu():this.openMenu()}openMenu(){this._openMenu(!0)}closeMenu(){this._closeMenu()}updatePosition(){this._overlayRef?.updatePosition()}ngAfterContentInit(){this._handleHover()}ngOnDestroy(){super.ngOnDestroy(),this._cleanupTouchstart(),this._hoverSubscription.unsubscribe()}_getOverlayOrigin(){return this._element}_getOutsideClickStream(e){return e.backdropClick()}_handleMousedown(e){Ee(e)||(this._openedBy=e.button===0?"mouse":void 0,this.triggersSubmenu()&&e.preventDefault())}_handleKeydown(e){let t=e.keyCode;(t===13||t===32)&&(this._openedBy="keyboard"),this.triggersSubmenu()&&(t===39&&this.dir==="ltr"||t===37&&this.dir==="rtl")&&(this._openedBy="keyboard",this.openMenu())}_handleClick(e){this.triggersSubmenu()?(e.stopPropagation(),this.openMenu()):this.toggleMenu()}_handleHover(){this.triggersSubmenu()&&this._parentMaterialMenu&&(this._hoverSubscription=this._parentMaterialMenu._hovered().subscribe(e=>{e===this._menuItemInstance&&!e.disabled&&this._parentMaterialMenu?._panelAnimationState!=="void"&&(this._openedBy="mouse",this._openMenu(!1))}))}static \u0275fac=function(t){return new(t||i)};static \u0275dir=K({type:i,selectors:[["","mat-menu-trigger-for",""],["","matMenuTriggerFor",""]],hostAttrs:[1,"mat-mdc-menu-trigger"],hostVars:3,hostBindings:function(t,n){t&1&&h("click",function(o){return n._handleClick(o)})("mousedown",function(o){return n._handleMousedown(o)})("keydown",function(o){return n._handleKeydown(o)}),t&2&&D("aria-haspopup",n.menu?"menu":null)("aria-expanded",n.menuOpen)("aria-controls",n.menuOpen?n.menu==null?null:n.menu.panelId:null)},inputs:{_deprecatedMatMenuTriggerFor:[0,"mat-menu-trigger-for","_deprecatedMatMenuTriggerFor"],menu:[0,"matMenuTriggerFor","menu"],menuData:[0,"matMenuTriggerData","menuData"],restoreFocus:[0,"matMenuTriggerRestoreFocus","restoreFocus"]},outputs:{menuOpened:"menuOpened",onMenuOpen:"onMenuOpen",menuClosed:"menuClosed",onMenuClose:"onMenuClose"},exportAs:["matMenuTrigger"],features:[Me]})}return i})();var at=(()=>{class i{static \u0275fac=function(t){return new(t||i)};static \u0275mod=ye({type:i});static \u0275inj=me({imports:[Ge,$e,He,Ye]})}return i})();function Ct(i,r){if(i&1&&(m(0,"span",8),d(1),u()),i&2){let e=_();f(),Te(e.feedback())}}var ot=class i{document=Ae(null);feedback=N("");async copy(r){let e=this.content(r);e&&this.feedback.set(await qe(e)?"Skopiowano Markdown.":"Nie uda\u0142o si\u0119 skopiowa\u0107 Markdown.")}download(r){let e=this.content(r),t=this.document();if(!t||!e)return;let n=r?t.fileName.replace(/\.md$/i,"-meta.md"):t.fileName,a=URL.createObjectURL(new Blob([e],{type:"text/markdown;charset=utf-8"})),o=window.document.createElement("a");o.href=a,o.download=n,window.document.body.appendChild(o),o.click(),o.remove(),window.setTimeout(()=>URL.revokeObjectURL(a),0),this.feedback.set("Pobrano Markdown.")}content(r){let e=this.document();return(r?e?.markdownWithMeta:e?.markdown)?.trim()||""}static \u0275fac=function(e){return new(e||i)};static \u0275cmp=S({type:i,selectors:[["app-analysis-share-menu"]],inputs:{document:[1,"document"]},decls:26,vars:3,consts:[["shareMenu","matMenu"],["type","button","aria-label","Udost\u0119pnij wynik","title","Udost\u0119pnij wynik",1,"analysis-share-menu__trigger",3,"click","disabled","matMenuTriggerFor"],["aria-hidden","true",1,"material-symbols-outlined"],["aria-label","Udost\u0119pnij wynik",1,"analysis-share-menu-panel"],["mat-menu-item","","type","button","aria-label","Kopiuj Markdown",3,"click"],["mat-menu-item","","type","button","aria-label","Kopiuj Markdown z meta",3,"click"],["mat-menu-item","","type","button","aria-label","Pobierz Markdown",3,"click"],["mat-menu-item","","type","button","aria-label","Pobierz Markdown z meta",3,"click"],["role","status",1,"analysis-share-menu__feedback"]],template:function(e,t){if(e&1&&(m(0,"button",1),h("click",function(a){return a.stopPropagation()}),m(1,"span",2),d(2,"share"),u()(),m(3,"mat-menu",3,0)(5,"button",4),h("click",function(){return t.copy(!1)}),m(6,"span",2),d(7,"content_copy"),u(),m(8,"span"),d(9,"Markdown"),u()(),m(10,"button",5),h("click",function(){return t.copy(!0)}),m(11,"span",2),d(12,"content_copy"),u(),m(13,"span"),d(14,"Markdown + Meta"),u()(),m(15,"button",6),h("click",function(){return t.download(!1)}),m(16,"span",2),d(17,"download"),u(),m(18,"span"),d(19,"Markdown"),u()(),m(20,"button",7),h("click",function(){return t.download(!0)}),m(21,"span",2),d(22,"download"),u(),m(23,"span"),d(24,"Markdown + Meta"),u()()(),j(25,Ct,2,1,"span",8)),e&2){let n,a=Re(4);H("disabled",!((n=t.document())!=null&&n.markdown))("matMenuTriggerFor",a),f(25),z(t.feedback()?25:-1)}},dependencies:[at,M,T,it],styles:["[_nghost-%COMP%]{display:inline-flex;align-items:center;gap:.5rem}.analysis-share-menu__trigger[_ngcontent-%COMP%]{display:inline-grid;place-items:center;width:2.5rem;height:2.5rem;border:1px solid var(--border);border-radius:var(--radius-md);background:var(--surface);color:var(--text-main);cursor:pointer}.analysis-share-menu__trigger[_ngcontent-%COMP%]:hover:not(:disabled){border-color:var(--border-strong);background:var(--surface-alt);color:var(--primary-dark)}.analysis-share-menu__trigger[_ngcontent-%COMP%]:focus-visible{outline:none;box-shadow:var(--focus-ring)}.analysis-share-menu__trigger[_ngcontent-%COMP%]:disabled{opacity:.45;cursor:default}.analysis-share-menu__feedback[_ngcontent-%COMP%]{font-size:.75rem;color:var(--text-muted)}"]})};export{Ze as a,xt as b,Rt as c,Pt as d,p as e,Tt as f,ot as g};
