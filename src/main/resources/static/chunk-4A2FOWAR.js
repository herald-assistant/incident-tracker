import{b as Ot,c as Ht,d as K,e as Nt}from"./chunk-FLI7ZIJP.js";import{E as Pt,L as Rt,N as Bt,O as $,W as Lt,X as Et,_ as St,qa as Z,ra as At,sa as Ft,u as wt,y as Dt,z as Mt}from"./chunk-JDDLVULI.js";import{$ as o,Ab as L,D as dt,Db as W,Eb as j,Fb as p,Fc as Tt,Gb as l,Hb as d,Hc as kt,Ib as T,Ic as xt,Jc as x,N as ct,Nb as V,O as M,Oc as It,P as bt,Q as m,Qc as c,Rb as h,Rc as G,Tb as w,Ub as k,Vb as f,Wb as vt,X as mt,Xa as b,Xb as yt,Yb as g,Z as pt,Zb as v,ab as ut,bc as q,cc as Ct,db as ft,dc as _,ea as O,f as nt,fa as H,fc as E,g as D,gc as S,h as it,ia as ht,j as ot,kb as C,l as rt,lb as gt,ma as N,mb as z,na as _t,ob as Q,ra as P,v as st,x as F,xb as u,y as lt,ya as R,zb as B}from"./chunk-7PMGEOYB.js";var J=["*"];var $t=["tabListContainer"],Zt=["tabList"],Kt=["tabListInner"],Yt=["nextPaginator"],Xt=["previousPaginator"];var Ut=["mat-tab-nav-bar",""],Jt=["mat-tab-link",""];var Y="mdc-tab-indicator--active",zt="mdc-tab-indicator--no-transition",U=class{_items;_currentItem;constructor(s){this._items=s}hide(){this._items.forEach(s=>s.deactivateInkBar()),this._currentItem=void 0}alignToElement(s){let t=this._items.find(a=>a.elementRef.nativeElement===s),e=this._currentItem;if(t!==e&&(e?.deactivateInkBar(),t)){let a=e?.elementRef.nativeElement.getBoundingClientRect?.();t.activateInkBar(a),this._currentItem=t}}},te=(()=>{class n{_elementRef=o(R);_inkBarElement=null;_inkBarContentElement=null;_fitToContent=!1;get fitInkBarToContent(){return this._fitToContent}set fitInkBarToContent(t){this._fitToContent!==t&&(this._fitToContent=t,this._inkBarElement&&this._appendInkBarElement())}activateInkBar(t){let e=this._elementRef.nativeElement;if(!t||!e.getBoundingClientRect||!this._inkBarContentElement){e.classList.add(Y);return}let a=e.getBoundingClientRect(),i=t.width/a.width,r=t.left-a.left;e.classList.add(zt),this._inkBarContentElement.style.setProperty("transform",`translateX(${r}px) scaleX(${i})`),e.getBoundingClientRect(),e.classList.remove(zt),e.classList.add(Y),this._inkBarContentElement.style.setProperty("transform","")}deactivateInkBar(){this._elementRef.nativeElement.classList.remove(Y)}ngOnInit(){this._createInkBarElement()}ngOnDestroy(){this._inkBarElement?.remove(),this._inkBarElement=this._inkBarContentElement=null}_createInkBarElement(){let t=this._elementRef.nativeElement.ownerDocument||document,e=this._inkBarElement=t.createElement("span"),a=this._inkBarContentElement=t.createElement("span");e.className="mdc-tab-indicator",a.className="mdc-tab-indicator__content mdc-tab-indicator__content--underline",e.appendChild(this._inkBarContentElement),this._appendInkBarElement()}_appendInkBarElement(){this._inkBarElement;let t=this._fitToContent?this._elementRef.nativeElement.querySelector(".mdc-tab__content"):this._elementRef.nativeElement;t.appendChild(this._inkBarElement)}static \u0275fac=function(e){return new(e||n)};static \u0275dir=z({type:n,inputs:{fitInkBarToContent:[2,"fitInkBarToContent","fitInkBarToContent",c]}})}return n})();var Qt={passive:!0},ee=650,ae=100,ne=(()=>{class n{_elementRef=o(R);_changeDetectorRef=o(It);_viewportRuler=o(St);_dir=o(Lt,{optional:!0});_ngZone=o(_t);_platform=o(wt);_sharedResizeObserver=o(Ot);_injector=o(ht);_renderer=o(ft);_animationsDisabled=Z();_eventCleanups;_scrollDistance=0;_selectedIndexChanged=!1;_destroyed=new D;_showPaginationControls=!1;_disableScrollAfter=!0;_disableScrollBefore=!0;_tabLabelCount;_scrollDistanceChanged=!1;_keyManager;_currentTextContent;_stopScrolling=new D;disablePagination=!1;get selectedIndex(){return this._selectedIndex}set selectedIndex(t){let e=isNaN(t)?0:t;this._selectedIndex!=e&&(this._selectedIndexChanged=!0,this._selectedIndex=e,this._keyManager&&this._keyManager.updateActiveItem(e))}_selectedIndex=0;selectFocusedIndex=new N;indexFocused=new N;constructor(){this._eventCleanups=this._ngZone.runOutsideAngular(()=>[this._renderer.listen(this._elementRef.nativeElement,"mouseleave",()=>this._stopInterval())])}ngAfterViewInit(){this._eventCleanups.push(this._renderer.listen(this._previousPaginator.nativeElement,"touchstart",()=>this._handlePaginatorPress("before"),Qt),this._renderer.listen(this._nextPaginator.nativeElement,"touchstart",()=>this._handlePaginatorPress("after"),Qt))}ngAfterContentInit(){let t=this._dir?this._dir.change:rt("ltr"),e=this._sharedResizeObserver.observe(this._elementRef.nativeElement).pipe(dt(32),m(this._destroyed)),a=this._viewportRuler.change(150).pipe(m(this._destroyed)),i=()=>{this.updatePagination(),this._alignInkBarToSelectedTab()};this._keyManager=new Bt(this._items).withHorizontalOrientation(this._getLayoutDirection()).withHomeAndEnd().withWrap().skipPredicate(()=>!1),this._keyManager.updateActiveItem(Math.max(this._selectedIndex,0)),ut(i,{injector:this._injector}),F(t,a,e,this._items.changes,this._itemsResized()).pipe(m(this._destroyed)).subscribe(()=>{this._ngZone.run(()=>{Promise.resolve().then(()=>{this._scrollDistance=Math.max(0,Math.min(this._getMaxScrollDistance(),this._scrollDistance)),i()})}),this._keyManager?.withHorizontalOrientation(this._getLayoutDirection())}),this._keyManager.change.subscribe(r=>{this.indexFocused.emit(r),this._setTabFocus(r)})}_itemsResized(){return typeof ResizeObserver!="function"?ot:this._items.changes.pipe(M(this._items),bt(t=>new nt(e=>this._ngZone.runOutsideAngular(()=>{let a=new ResizeObserver(i=>e.next(i));return t.forEach(i=>a.observe(i.elementRef.nativeElement)),()=>{a.disconnect()}}))),ct(1),lt(t=>t.some(e=>e.contentRect.width>0&&e.contentRect.height>0)))}ngAfterContentChecked(){this._tabLabelCount!=this._items.length&&(this.updatePagination(),this._tabLabelCount=this._items.length,this._changeDetectorRef.markForCheck()),this._selectedIndexChanged&&(this._scrollToLabel(this._selectedIndex),this._checkScrollingControls(),this._alignInkBarToSelectedTab(),this._selectedIndexChanged=!1,this._changeDetectorRef.markForCheck()),this._scrollDistanceChanged&&(this._updateTabScrollPosition(),this._scrollDistanceChanged=!1,this._changeDetectorRef.markForCheck())}ngOnDestroy(){this._eventCleanups.forEach(t=>t()),this._keyManager?.destroy(),this._destroyed.next(),this._destroyed.complete(),this._stopScrolling.complete()}_handleKeydown(t){if(!Rt(t))switch(t.keyCode){case 13:case 32:if(this.focusIndex!==this.selectedIndex){let e=this._items.get(this.focusIndex);e&&!e.disabled&&(this.selectFocusedIndex.emit(this.focusIndex),this._itemSelected(t))}break;default:this._keyManager?.onKeydown(t)}}_onContentChanges(){let t=this._elementRef.nativeElement.textContent;t!==this._currentTextContent&&(this._currentTextContent=t||"",this._ngZone.run(()=>{this.updatePagination(),this._alignInkBarToSelectedTab(),this._changeDetectorRef.markForCheck()}))}updatePagination(){this._checkPaginationEnabled(),this._checkScrollingControls(),this._updateTabScrollPosition()}get focusIndex(){return this._keyManager?this._keyManager.activeItemIndex:0}set focusIndex(t){!this._isValidIndex(t)||this.focusIndex===t||!this._keyManager||this._keyManager.setActiveItem(t)}_isValidIndex(t){return this._items?!!this._items.toArray()[t]:!0}_setTabFocus(t){if(this._showPaginationControls&&this._scrollToLabel(t),this._items&&this._items.length){this._items.toArray()[t].focus();let e=this._tabListContainer.nativeElement;this._getLayoutDirection()=="ltr"?e.scrollLeft=0:e.scrollLeft=e.scrollWidth-e.offsetWidth}}_getLayoutDirection(){return this._dir&&this._dir.value==="rtl"?"rtl":"ltr"}_updateTabScrollPosition(){if(this.disablePagination)return;let t=this.scrollDistance,e=this._getLayoutDirection()==="ltr"?-t:t;this._tabList.nativeElement.style.transform=`translateX(${Math.round(e)}px)`,(this._platform.TRIDENT||this._platform.EDGE)&&(this._tabListContainer.nativeElement.scrollLeft=0)}get scrollDistance(){return this._scrollDistance}set scrollDistance(t){this._scrollTo(t)}_scrollHeader(t){let e=this._tabListContainer.nativeElement.offsetWidth,a=(t=="before"?-1:1)*e/3;return this._scrollTo(this._scrollDistance+a)}_handlePaginatorClick(t){this._stopInterval(),this._scrollHeader(t)}_scrollToLabel(t){if(this.disablePagination)return;let e=this._items?this._items.toArray()[t]:null;if(!e)return;let a=this._tabListContainer.nativeElement.offsetWidth,{offsetLeft:i,offsetWidth:r}=e.elementRef.nativeElement,y,I;this._getLayoutDirection()=="ltr"?(y=i,I=y+r):(I=this._tabListInner.nativeElement.offsetWidth-i,y=I-r);let A=this.scrollDistance,at=this.scrollDistance+a;y<A?this.scrollDistance-=A-y:I>at&&(this.scrollDistance+=Math.min(I-at,y-A))}_checkPaginationEnabled(){if(this.disablePagination)this._showPaginationControls=!1;else{let t=this._tabListInner.nativeElement.scrollWidth,e=this._elementRef.nativeElement.offsetWidth,a=t-e>=5;a||(this.scrollDistance=0),a!==this._showPaginationControls&&(this._showPaginationControls=a,this._changeDetectorRef.markForCheck())}}_checkScrollingControls(){this.disablePagination?this._disableScrollAfter=this._disableScrollBefore=!0:(this._disableScrollBefore=this.scrollDistance==0,this._disableScrollAfter=this.scrollDistance==this._getMaxScrollDistance(),this._changeDetectorRef.markForCheck())}_getMaxScrollDistance(){let t=this._tabListInner.nativeElement.scrollWidth,e=this._tabListContainer.nativeElement.offsetWidth;return t-e||0}_alignInkBarToSelectedTab(){let t=this._items&&this._items.length?this._items.toArray()[this.selectedIndex]:null,e=t?t.elementRef.nativeElement:null;e?this._inkBar.alignToElement(e):this._inkBar.hide()}_stopInterval(){this._stopScrolling.next()}_handlePaginatorPress(t,e){e&&e.button!=null&&e.button!==0||(this._stopInterval(),st(ee,ae).pipe(m(F(this._stopScrolling,this._destroyed))).subscribe(()=>{let{maxScrollDistance:a,distance:i}=this._scrollHeader(t);(i===0||i>=a)&&this._stopInterval()}))}_scrollTo(t){if(this.disablePagination)return{maxScrollDistance:0,distance:0};let e=this._getMaxScrollDistance();return this._scrollDistance=Math.max(0,Math.min(e,t)),this._scrollDistanceChanged=!0,this._checkScrollingControls(),{maxScrollDistance:e,distance:this._scrollDistance}}static \u0275fac=function(e){return new(e||n)};static \u0275dir=z({type:n,inputs:{disablePagination:[2,"disablePagination","disablePagination",c],selectedIndex:[2,"selectedIndex","selectedIndex",G]},outputs:{selectFocusedIndex:"selectFocusedIndex",indexFocused:"indexFocused"}})}return n})();var ie=new pt("MAT_TABS_CONFIG");var tt=(()=>{class n extends ne{_focusedItem=P(null);get fitInkBarToContent(){return this._fitInkBarToContent.value}set fitInkBarToContent(t){this._fitInkBarToContent.next(t),this._changeDetectorRef.markForCheck()}_fitInkBarToContent=new it(!1);stretchTabs=!0;get animationDuration(){return this._animationDuration}set animationDuration(t){let e=t+"";this._animationDuration=/^\d+$/.test(e)?t+"ms":e}_animationDuration;_items;get backgroundColor(){return this._backgroundColor}set backgroundColor(t){let e=this._elementRef.nativeElement.classList;e.remove("mat-tabs-with-background",`mat-background-${this.backgroundColor}`),t&&e.add("mat-tabs-with-background",`mat-background-${t}`),this._backgroundColor=t}_backgroundColor;get disableRipple(){return this._disableRipple()}set disableRipple(t){this._disableRipple.set(t)}_disableRipple=P(!1);color="primary";tabPanel;_tabListContainer;_tabList;_tabListInner;_nextPaginator;_previousPaginator;_inkBar;constructor(){let t=o(ie,{optional:!0});super(),this.disablePagination=t&&t.disablePagination!=null?t.disablePagination:!1,this.fitInkBarToContent=t&&t.fitInkBarToContent!=null?t.fitInkBarToContent:!1,this.stretchTabs=t&&t.stretchTabs!=null?t.stretchTabs:!0}_itemSelected(){}ngAfterContentInit(){this._inkBar=new U(this._items),this._items.changes.pipe(M(null),m(this._destroyed)).subscribe(()=>this.updateActiveLink()),super.ngAfterContentInit(),this._keyManager.change.pipe(M(null),m(this._destroyed)).subscribe(()=>this._focusedItem.set(this._keyManager?.activeItem||null))}ngAfterViewInit(){this.tabPanel,super.ngAfterViewInit()}updateActiveLink(){if(!this._items)return;let t=this._items.toArray();for(let e=0;e<t.length;e++)if(t[e].active){this.selectedIndex=e,this.tabPanel&&(this.tabPanel._activeTabId=t[e].id),this._focusedItem.set(t[e]),this._changeDetectorRef.markForCheck();return}this.selectedIndex=-1}_getRole(){return this.tabPanel?"tablist":this._elementRef.nativeElement.getAttribute("role")}_hasFocus(t){return this._keyManager?.activeItem===t}static \u0275fac=function(e){return new(e||n)};static \u0275cmp=C({type:n,selectors:[["","mat-tab-nav-bar",""]],contentQueries:function(e,a,i){if(e&1&&vt(i,et,5),e&2){let r;g(r=v())&&(a._items=r)}},viewQuery:function(e,a){if(e&1&&yt($t,7)(Zt,7)(Kt,7)(Yt,5)(Xt,5),e&2){let i;g(i=v())&&(a._tabListContainer=i.first),g(i=v())&&(a._tabList=i.first),g(i=v())&&(a._tabListInner=i.first),g(i=v())&&(a._nextPaginator=i.first),g(i=v())&&(a._previousPaginator=i.first)}},hostAttrs:[1,"mat-mdc-tab-nav-bar","mat-mdc-tab-header"],hostVars:17,hostBindings:function(e,a){e&2&&(u("role",a._getRole()),Ct("--mat-tab-animation-duration",a.animationDuration),_("mat-mdc-tab-header-pagination-controls-enabled",a._showPaginationControls)("mat-mdc-tab-header-rtl",a._getLayoutDirection()=="rtl")("mat-mdc-tab-nav-bar-stretch-tabs",a.stretchTabs)("mat-primary",a.color!=="warn"&&a.color!=="accent")("mat-accent",a.color==="accent")("mat-warn",a.color==="warn")("_mat-animation-noopable",a._animationsDisabled))},inputs:{fitInkBarToContent:[2,"fitInkBarToContent","fitInkBarToContent",c],stretchTabs:[2,"mat-stretch-tabs","stretchTabs",c],animationDuration:"animationDuration",backgroundColor:"backgroundColor",disableRipple:[2,"disableRipple","disableRipple",c],color:"color",tabPanel:"tabPanel"},exportAs:["matTabNavBar","matTabNav"],features:[Q],attrs:Ut,ngContentSelectors:J,decls:13,vars:6,consts:[["previousPaginator",""],["tabListContainer",""],["tabList",""],["tabListInner",""],["nextPaginator",""],["mat-ripple","",1,"mat-mdc-tab-header-pagination","mat-mdc-tab-header-pagination-before",3,"click","mousedown","touchend","matRippleDisabled"],[1,"mat-mdc-tab-header-pagination-chevron"],[1,"mat-mdc-tab-link-container",3,"keydown"],[1,"mat-mdc-tab-list",3,"cdkObserveContent"],[1,"mat-mdc-tab-links"],["mat-ripple","",1,"mat-mdc-tab-header-pagination","mat-mdc-tab-header-pagination-after",3,"mousedown","click","touchend","matRippleDisabled"]],template:function(e,a){e&1&&(k(),l(0,"div",5,0),h("click",function(){return a._handlePaginatorClick("before")})("mousedown",function(r){return a._handlePaginatorPress("before",r)})("touchend",function(){return a._stopInterval()}),T(2,"div",6),d(),l(3,"div",7,1),h("keydown",function(r){return a._handleKeydown(r)}),l(5,"div",8,2),h("cdkObserveContent",function(){return a._onContentChanges()}),l(7,"div",9,3),f(9),d()()(),l(10,"div",10,4),h("mousedown",function(r){return a._handlePaginatorPress("after",r)})("click",function(){return a._handlePaginatorClick("after")})("touchend",function(){return a._stopInterval()}),T(12,"div",6),d()),e&2&&(_("mat-mdc-tab-header-pagination-disabled",a._disableScrollBefore),p("matRippleDisabled",a._disableScrollBefore||a.disableRipple),b(10),_("mat-mdc-tab-header-pagination-disabled",a._disableScrollAfter),p("matRippleDisabled",a._disableScrollAfter||a.disableRipple))},dependencies:[K,Pt],styles:[`.mdc-tab {
  min-width: 90px;
  padding: 0 24px;
  display: flex;
  flex: 1 0 auto;
  justify-content: center;
  box-sizing: border-box;
  border: none;
  outline: none;
  text-align: center;
  white-space: nowrap;
  cursor: pointer;
  z-index: 1;
  touch-action: manipulation;
}

.mdc-tab__content {
  display: flex;
  align-items: center;
  justify-content: center;
  height: inherit;
  pointer-events: none;
}

.mdc-tab__text-label {
  transition: 150ms color linear;
  display: inline-block;
  line-height: 1;
  z-index: 2;
}

.mdc-tab--active .mdc-tab__text-label {
  transition-delay: 100ms;
}

._mat-animation-noopable .mdc-tab__text-label {
  transition: none;
}

.mdc-tab-indicator {
  display: flex;
  position: absolute;
  top: 0;
  left: 0;
  justify-content: center;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 1;
}

.mdc-tab-indicator__content {
  transition: var(--mat-tab-animation-duration, 250ms) transform cubic-bezier(0.4, 0, 0.2, 1);
  transform-origin: left;
  opacity: 0;
}

.mdc-tab-indicator__content--underline {
  align-self: flex-end;
  box-sizing: border-box;
  width: 100%;
  border-top-style: solid;
}

.mdc-tab-indicator--active .mdc-tab-indicator__content {
  opacity: 1;
}

._mat-animation-noopable .mdc-tab-indicator__content, .mdc-tab-indicator--no-transition .mdc-tab-indicator__content {
  transition: none;
}

.mat-mdc-tab-ripple.mat-mdc-tab-ripple {
  position: absolute;
  top: 0;
  left: 0;
  bottom: 0;
  right: 0;
  pointer-events: none;
}

.mat-mdc-tab-header {
  display: flex;
  overflow: hidden;
  position: relative;
  flex-shrink: 0;
}

.mdc-tab-indicator .mdc-tab-indicator__content {
  transition-duration: var(--mat-tab-animation-duration, 250ms);
}

.mat-mdc-tab-header-pagination {
  -webkit-user-select: none;
  user-select: none;
  position: relative;
  display: none;
  justify-content: center;
  align-items: center;
  min-width: 32px;
  cursor: pointer;
  z-index: 2;
  -webkit-tap-highlight-color: transparent;
  touch-action: none;
  box-sizing: content-box;
  outline: 0;
}
.mat-mdc-tab-header-pagination::-moz-focus-inner {
  border: 0;
}
.mat-mdc-tab-header-pagination .mat-ripple-element {
  opacity: 0.12;
  background-color: var(--mat-tab-inactive-ripple-color, var(--mat-sys-on-surface));
}
.mat-mdc-tab-header-pagination-controls-enabled .mat-mdc-tab-header-pagination {
  display: flex;
}

.mat-mdc-tab-header-pagination-before,
.mat-mdc-tab-header-rtl .mat-mdc-tab-header-pagination-after {
  padding-left: 4px;
}
.mat-mdc-tab-header-pagination-before .mat-mdc-tab-header-pagination-chevron,
.mat-mdc-tab-header-rtl .mat-mdc-tab-header-pagination-after .mat-mdc-tab-header-pagination-chevron {
  transform: rotate(-135deg);
}

.mat-mdc-tab-header-rtl .mat-mdc-tab-header-pagination-before,
.mat-mdc-tab-header-pagination-after {
  padding-right: 4px;
}
.mat-mdc-tab-header-rtl .mat-mdc-tab-header-pagination-before .mat-mdc-tab-header-pagination-chevron,
.mat-mdc-tab-header-pagination-after .mat-mdc-tab-header-pagination-chevron {
  transform: rotate(45deg);
}

.mat-mdc-tab-header-pagination-chevron {
  border-style: solid;
  border-width: 2px 2px 0 0;
  height: 8px;
  width: 8px;
  border-color: var(--mat-tab-pagination-icon-color, var(--mat-sys-on-surface));
}

.mat-mdc-tab-header-pagination-disabled {
  box-shadow: none;
  cursor: default;
  pointer-events: none;
}
.mat-mdc-tab-header-pagination-disabled .mat-mdc-tab-header-pagination-chevron {
  opacity: 0.4;
}

.mat-mdc-tab-list {
  flex-grow: 1;
  position: relative;
  transition: transform 500ms cubic-bezier(0.35, 0, 0.25, 1);
}
._mat-animation-noopable .mat-mdc-tab-list {
  transition: none;
}

.mat-mdc-tab-links {
  display: flex;
  flex: 1 0 auto;
}
[mat-align-tabs=center] > .mat-mdc-tab-link-container .mat-mdc-tab-links {
  justify-content: center;
}
[mat-align-tabs=end] > .mat-mdc-tab-link-container .mat-mdc-tab-links {
  justify-content: flex-end;
}
.cdk-drop-list .mat-mdc-tab-links, .mat-mdc-tab-links.cdk-drop-list {
  min-height: var(--mat-tab-container-height, 48px);
}

.mat-mdc-tab-link-container {
  display: flex;
  flex-grow: 1;
  overflow: hidden;
  z-index: 1;
  border-bottom-style: solid;
  border-bottom-width: var(--mat-tab-divider-height, 1px);
  border-bottom-color: var(--mat-tab-divider-color, var(--mat-sys-surface-variant));
}

.mat-mdc-tab-nav-bar.mat-tabs-with-background > .mat-mdc-tab-link-container, .mat-mdc-tab-nav-bar.mat-tabs-with-background > .mat-mdc-tab-header-pagination {
  background-color: var(--mat-tab-background-color);
}
.mat-mdc-tab-nav-bar.mat-tabs-with-background.mat-primary > .mat-mdc-tab-link-container .mat-mdc-tab-link .mdc-tab__text-label {
  color: var(--mat-tab-foreground-color);
}
.mat-mdc-tab-nav-bar.mat-tabs-with-background.mat-primary > .mat-mdc-tab-link-container .mdc-tab-indicator__content--underline {
  border-color: var(--mat-tab-foreground-color);
}
.mat-mdc-tab-nav-bar.mat-tabs-with-background:not(.mat-primary) > .mat-mdc-tab-link-container .mat-mdc-tab-link:not(.mdc-tab--active) .mdc-tab__text-label {
  color: var(--mat-tab-foreground-color);
}
.mat-mdc-tab-nav-bar.mat-tabs-with-background:not(.mat-primary) > .mat-mdc-tab-link-container .mat-mdc-tab-link:not(.mdc-tab--active) .mdc-tab-indicator__content--underline {
  border-color: var(--mat-tab-foreground-color);
}
.mat-mdc-tab-nav-bar.mat-tabs-with-background > .mat-mdc-tab-link-container .mat-mdc-tab-header-pagination-chevron,
.mat-mdc-tab-nav-bar.mat-tabs-with-background > .mat-mdc-tab-link-container .mat-focus-indicator::before, .mat-mdc-tab-nav-bar.mat-tabs-with-background > .mat-mdc-tab-header-pagination .mat-mdc-tab-header-pagination-chevron,
.mat-mdc-tab-nav-bar.mat-tabs-with-background > .mat-mdc-tab-header-pagination .mat-focus-indicator::before {
  border-color: var(--mat-tab-foreground-color);
}
.mat-mdc-tab-nav-bar.mat-tabs-with-background > .mat-mdc-tab-link-container .mat-ripple-element, .mat-mdc-tab-nav-bar.mat-tabs-with-background > .mat-mdc-tab-link-container .mdc-tab__ripple::before, .mat-mdc-tab-nav-bar.mat-tabs-with-background > .mat-mdc-tab-header-pagination .mat-ripple-element, .mat-mdc-tab-nav-bar.mat-tabs-with-background > .mat-mdc-tab-header-pagination .mdc-tab__ripple::before {
  background-color: var(--mat-tab-foreground-color);
}
.mat-mdc-tab-nav-bar.mat-tabs-with-background > .mat-mdc-tab-link-container .mat-mdc-tab-header-pagination-chevron, .mat-mdc-tab-nav-bar.mat-tabs-with-background > .mat-mdc-tab-header-pagination .mat-mdc-tab-header-pagination-chevron {
  color: var(--mat-tab-foreground-color);
}
`],encapsulation:2})}return n})(),et=(()=>{class n extends te{_tabNavBar=o(tt);elementRef=o(R);_focusMonitor=o(Dt);_destroyed=new D;_isActive=!1;_tabIndex=Tt(()=>this._tabNavBar._focusedItem()===this?this.tabIndex:-1);get active(){return this._isActive}set active(t){t!==this._isActive&&(this._isActive=t,this._tabNavBar.updateActiveLink())}disabled=!1;get disableRipple(){return this._disableRipple()}set disableRipple(t){this._disableRipple.set(t)}_disableRipple=P(!1);tabIndex=0;rippleConfig;get rippleDisabled(){return this.disabled||this.disableRipple||this._tabNavBar.disableRipple||!!this.rippleConfig.disabled}id=o($).getId("mat-tab-link-");constructor(){super(),o(Mt).load(Nt);let t=o(Ht,{optional:!0}),e=o(new kt("tabindex"),{optional:!0});this.rippleConfig=t||{},this.tabIndex=e==null?0:parseInt(e)||0,Z()&&(this.rippleConfig.animation={enterDuration:0,exitDuration:0}),this._tabNavBar._fitInkBarToContent.pipe(m(this._destroyed)).subscribe(a=>{this.fitInkBarToContent=a})}focus(){this.elementRef.nativeElement.focus()}ngAfterViewInit(){this._focusMonitor.monitor(this.elementRef)}ngOnDestroy(){this._destroyed.next(),this._destroyed.complete(),super.ngOnDestroy(),this._focusMonitor.stopMonitoring(this.elementRef)}_handleFocus(){this._tabNavBar.focusIndex=this._tabNavBar._items.toArray().indexOf(this)}_handleKeydown(t){(t.keyCode===32||t.keyCode===13)&&(this.disabled?t.preventDefault():this._tabNavBar.tabPanel&&(t.keyCode===32&&t.preventDefault(),this.elementRef.nativeElement.click()))}_getAriaControls(){return this._tabNavBar.tabPanel?this._tabNavBar.tabPanel?.id:this.elementRef.nativeElement.getAttribute("aria-controls")}_getAriaSelected(){return this._tabNavBar.tabPanel?this.active?"true":"false":this.elementRef.nativeElement.getAttribute("aria-selected")}_getAriaCurrent(){return this.active&&!this._tabNavBar.tabPanel?"page":null}_getRole(){return this._tabNavBar.tabPanel?"tab":this.elementRef.nativeElement.getAttribute("role")}static \u0275fac=function(e){return new(e||n)};static \u0275cmp=C({type:n,selectors:[["","mat-tab-link",""],["","matTabLink",""]],hostAttrs:[1,"mdc-tab","mat-mdc-tab-link","mat-focus-indicator"],hostVars:11,hostBindings:function(e,a){e&1&&h("focus",function(){return a._handleFocus()})("keydown",function(r){return a._handleKeydown(r)}),e&2&&(u("aria-controls",a._getAriaControls())("aria-current",a._getAriaCurrent())("aria-disabled",a.disabled)("aria-selected",a._getAriaSelected())("id",a.id)("tabIndex",a._tabIndex())("role",a._getRole()),_("mat-mdc-tab-disabled",a.disabled)("mdc-tab--active",a.active))},inputs:{active:[2,"active","active",c],disabled:[2,"disabled","disabled",c],disableRipple:[2,"disableRipple","disableRipple",c],tabIndex:[2,"tabIndex","tabIndex",t=>t==null?0:G(t)],id:"id"},exportAs:["matTabLink"],features:[Q],attrs:Jt,ngContentSelectors:J,decls:5,vars:2,consts:[[1,"mdc-tab__ripple"],["mat-ripple","",1,"mat-mdc-tab-ripple",3,"matRippleTrigger","matRippleDisabled"],[1,"mdc-tab__content"],[1,"mdc-tab__text-label"]],template:function(e,a){e&1&&(k(),T(0,"span",0)(1,"div",1),l(2,"span",2)(3,"span",3),f(4),d()()),e&2&&(b(),p("matRippleTrigger",a.elementRef.nativeElement)("matRippleDisabled",a.rippleDisabled))},dependencies:[K],styles:[`.mat-mdc-tab-link {
  -webkit-tap-highlight-color: transparent;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-decoration: none;
  background: none;
  height: var(--mat-tab-container-height, 48px);
  font-family: var(--mat-tab-label-text-font, var(--mat-sys-title-small-font));
  font-size: var(--mat-tab-label-text-size, var(--mat-sys-title-small-size));
  letter-spacing: var(--mat-tab-label-text-tracking, var(--mat-sys-title-small-tracking));
  line-height: var(--mat-tab-label-text-line-height, var(--mat-sys-title-small-line-height));
  font-weight: var(--mat-tab-label-text-weight, var(--mat-sys-title-small-weight));
}
.mat-mdc-tab-link.mdc-tab {
  flex-grow: 0;
}
.mat-mdc-tab-link .mdc-tab-indicator__content--underline {
  border-color: var(--mat-tab-active-indicator-color, var(--mat-sys-primary));
  border-top-width: var(--mat-tab-active-indicator-height, 2px);
  border-radius: var(--mat-tab-active-indicator-shape, 0);
}
.mat-mdc-tab-link:hover .mdc-tab__text-label {
  color: var(--mat-tab-inactive-hover-label-text-color, var(--mat-sys-on-surface));
}
.mat-mdc-tab-link:focus .mdc-tab__text-label {
  color: var(--mat-tab-inactive-focus-label-text-color, var(--mat-sys-on-surface));
}
.mat-mdc-tab-link.mdc-tab--active .mdc-tab__text-label {
  color: var(--mat-tab-active-label-text-color, var(--mat-sys-on-surface));
}
.mat-mdc-tab-link.mdc-tab--active .mdc-tab__ripple::before,
.mat-mdc-tab-link.mdc-tab--active .mat-ripple-element {
  background-color: var(--mat-tab-active-ripple-color, var(--mat-sys-on-surface));
}
.mat-mdc-tab-link.mdc-tab--active:hover .mdc-tab__text-label {
  color: var(--mat-tab-active-hover-label-text-color, var(--mat-sys-on-surface));
}
.mat-mdc-tab-link.mdc-tab--active:hover .mdc-tab-indicator__content--underline {
  border-color: var(--mat-tab-active-hover-indicator-color, var(--mat-sys-primary));
}
.mat-mdc-tab-link.mdc-tab--active:focus .mdc-tab__text-label {
  color: var(--mat-tab-active-focus-label-text-color, var(--mat-sys-on-surface));
}
.mat-mdc-tab-link.mdc-tab--active:focus .mdc-tab-indicator__content--underline {
  border-color: var(--mat-tab-active-focus-indicator-color, var(--mat-sys-primary));
}
.mat-mdc-tab-link.mat-mdc-tab-disabled {
  opacity: 0.4;
  pointer-events: none;
}
.mat-mdc-tab-link.mat-mdc-tab-disabled .mdc-tab__content {
  pointer-events: none;
}
.mat-mdc-tab-link.mat-mdc-tab-disabled .mdc-tab__ripple::before,
.mat-mdc-tab-link.mat-mdc-tab-disabled .mat-ripple-element {
  background-color: var(--mat-tab-disabled-ripple-color, var(--mat-sys-on-surface-variant));
}
.mat-mdc-tab-link .mdc-tab__ripple::before {
  content: "";
  display: block;
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  opacity: 0;
  pointer-events: none;
  background-color: var(--mat-tab-inactive-ripple-color, var(--mat-sys-on-surface));
}
.mat-mdc-tab-link .mdc-tab__text-label {
  color: var(--mat-tab-inactive-label-text-color, var(--mat-sys-on-surface));
  display: inline-flex;
  align-items: center;
}
.mat-mdc-tab-link .mdc-tab__content {
  position: relative;
  pointer-events: auto;
}
.mat-mdc-tab-link:hover .mdc-tab__ripple::before {
  opacity: 0.04;
}
.mat-mdc-tab-link.cdk-program-focused .mdc-tab__ripple::before, .mat-mdc-tab-link.cdk-keyboard-focused .mdc-tab__ripple::before {
  opacity: 0.12;
}
.mat-mdc-tab-link .mat-ripple-element {
  opacity: 0.12;
  background-color: var(--mat-tab-inactive-ripple-color, var(--mat-sys-on-surface));
}
.mat-mdc-tab-header.mat-mdc-tab-nav-bar-stretch-tabs .mat-mdc-tab-link {
  flex-grow: 1;
}
.mat-mdc-tab-link::before {
  margin: 5px;
}

@media (max-width: 599px) {
  .mat-mdc-tab-link {
    min-width: 72px;
  }
}
`],encapsulation:2,changeDetection:0})}return n})(),jt=(()=>{class n{id=o($).getId("mat-tab-nav-panel-");_activeTabId;static \u0275fac=function(e){return new(e||n)};static \u0275cmp=C({type:n,selectors:[["mat-tab-nav-panel"]],hostAttrs:["role","tabpanel",1,"mat-mdc-tab-nav-panel"],hostVars:2,hostBindings:function(e,a){e&2&&u("aria-labelledby",a._activeTabId)("id",a.id)},inputs:{id:"id"},exportAs:["matTabNavPanel"],ngContentSelectors:J,decls:1,vars:0,template:function(e,a){e&1&&(k(),f(0))},encapsulation:2,changeDetection:0})}return n})(),Vt=(()=>{class n{static \u0275fac=function(e){return new(e||n)};static \u0275mod=gt({type:n});static \u0275inj=mt({imports:[Et]})}return n})();var re=[[["","tabActions",""]],"*"],se=["[tabActions]","*"],le=(n,s)=>s.id;function de(n,s){n&1&&T(0,"span",8)}function ce(n,s){if(n&1&&(l(0,"span",10),E(1),d()),n&2){let t=w();_("analysis-result-tab__status-icon--error",t.kind==="error"),b(),S(t.kind==="error"?"error":"info")}}function be(n,s){n&1&&B(0,de,1,0,"span",8)(1,ce,2,3,"span",9),n&2&&L(s.kind==="loading"?0:1)}function me(n,s){if(n&1){let t=V();l(0,"button",6),h("click",function(){let a=O(t).$implicit,i=w();return H(i.tabSelected.emit(a.id))}),l(1,"span",7),E(2),d(),B(3,be,2,1),d()}if(n&2){let t,e=s.$implicit,a=w();p("id",a.idPrefix()+"-tab-"+e.id)("active",a.activeTabId()===e.id)("matTooltip",(e.status==null?null:e.status.tooltip)||(e.status==null?null:e.status.label)||"")("matTooltipDisabled",!e.status)("matTooltipShowDelay",120),u("aria-label",e.status?e.tabLabel+" \xB7 "+e.status.label:null)("aria-busy",(e.status==null?null:e.status.kind)==="loading"?"true":null),b(2),S(e.tabLabel),b(),L((t=e.status)?3:-1,t)}}var qt=class n{tabs=x([]);activeTabId=x("");idPrefix=x("analysis");ariaLabel=x("Sekcje wyniku analizy");equalWidth=x(!1,{transform:c});tabSelected=xt();static \u0275fac=function(t){return new(t||n)};static \u0275cmp=C({type:n,selectors:[["app-analysis-result-tabs"]],inputs:{tabs:[1,"tabs"],activeTabId:[1,"activeTabId"],idPrefix:[1,"idPrefix"],ariaLabel:[1,"ariaLabel"],equalWidth:[1,"equalWidth"]},outputs:{tabSelected:"tabSelected"},ngContentSelectors:se,decls:9,vars:6,consts:[["panel",""],[1,"analysis-result-tabs-header"],["mat-tab-nav-bar","",1,"analysis-result-tabs",3,"tabPanel","mat-stretch-tabs"],["mat-tab-link","","type","button","matTooltipPosition","above","matTooltipClass","analysis-rich-tooltip",1,"analysis-result-tab",3,"id","active","matTooltip","matTooltipDisabled","matTooltipShowDelay"],[1,"analysis-result-tabs-actions"],["tabindex","0",3,"id"],["mat-tab-link","","type","button","matTooltipPosition","above","matTooltipClass","analysis-rich-tooltip",1,"analysis-result-tab",3,"click","id","active","matTooltip","matTooltipDisabled","matTooltipShowDelay"],[1,"analysis-result-tab__label"],["aria-hidden","true",1,"analysis-result-tab__spinner"],["aria-hidden","true",1,"analysis-result-tab__status-icon","material-symbols-outlined",3,"analysis-result-tab__status-icon--error"],["aria-hidden","true",1,"analysis-result-tab__status-icon","material-symbols-outlined"]],template:function(t,e){if(t&1&&(k(re),l(0,"div",1)(1,"nav",2),W(2,me,4,9,"button",3,le),d(),l(4,"div",4),f(5),d()(),l(6,"mat-tab-nav-panel",5,0),f(8,1),d()),t&2){let a=q(7);b(),_("analysis-result-tabs--equal-width",e.equalWidth()),p("tabPanel",a)("mat-stretch-tabs",e.equalWidth()),u("aria-label",e.ariaLabel()),b(),j(e.tabs()),b(4),p("id",e.idPrefix()+"-panel")}},dependencies:[Vt,tt,jt,et,Ft,At],styles:["[_nghost-%COMP%]{display:block;min-width:0;max-width:100%;--mat-tab-container-height: 48px;--mat-tab-label-text-font: var(--font-sans);--mat-tab-label-text-size: .875rem;--mat-tab-label-text-weight: 500;--mat-tab-label-text-tracking: normal;--mat-tab-active-indicator-height: 2px;--mat-tab-active-indicator-color: var(--primary);--mat-tab-active-hover-indicator-color: var(--primary);--mat-tab-active-focus-indicator-color: var(--primary);--mat-tab-active-label-text-color: var(--primary-dark);--mat-tab-active-hover-label-text-color: var(--primary-dark);--mat-tab-active-focus-label-text-color: var(--primary-dark);--mat-tab-inactive-label-text-color: var(--text-muted);--mat-tab-inactive-hover-label-text-color: var(--text-main);--mat-tab-inactive-focus-label-text-color: var(--text-main);--mat-tab-pagination-icon-color: var(--text-muted);--mat-tab-divider-color: var(--border)}.analysis-result-tabs-header[_ngcontent-%COMP%]{display:flex;align-items:center;gap:8px;min-width:0;border-radius:inherit}.analysis-result-tabs-actions[_ngcontent-%COMP%]{display:flex;flex:0 0 auto}.analysis-result-tabs-actions[_ngcontent-%COMP%]:empty{display:none}.analysis-result-tabs[_ngcontent-%COMP%]{flex:1;min-width:0;border-radius:inherit}.analysis-result-tab[_ngcontent-%COMP%]{padding-inline:18px;white-space:nowrap}.analysis-result-tabs--equal-width[_ngcontent-%COMP%]   .analysis-result-tab[_ngcontent-%COMP%]{flex:1 0 148px;min-width:148px;padding-inline:12px}.analysis-result-tabs--equal-width[_ngcontent-%COMP%]   .analysis-result-tab__label[_ngcontent-%COMP%]{white-space:normal;line-height:18px}mat-tab-nav-panel[_ngcontent-%COMP%]{display:block;min-width:0}mat-tab-nav-panel[_ngcontent-%COMP%]:focus-visible{outline:2px solid var(--primary);outline-offset:-2px}.analysis-result-tab__spinner[_ngcontent-%COMP%], .analysis-result-tab__status-icon[_ngcontent-%COMP%]{margin-inline-start:8px}.analysis-result-tab__spinner[_ngcontent-%COMP%]{flex:0 0 auto;box-sizing:border-box;width:14px;height:14px;border:2px solid color-mix(in srgb,currentColor 22%,transparent);border-top-color:currentColor;border-radius:50%;animation:_ngcontent-%COMP%_result-tab-spin .8s linear infinite}.analysis-result-tab__status-icon[_ngcontent-%COMP%]{flex:0 0 auto;color:var(--text-muted);font-size:16px}.analysis-result-tab__status-icon--error[_ngcontent-%COMP%]{color:var(--danger-text)}@keyframes _ngcontent-%COMP%_result-tab-spin{to{transform:rotate(360deg)}}@media(prefers-reduced-motion:reduce){.analysis-result-tab__spinner[_ngcontent-%COMP%]{animation:none}}"]})};export{qt as a};
