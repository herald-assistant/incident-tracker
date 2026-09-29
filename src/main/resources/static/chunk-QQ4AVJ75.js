function r(t){if(!t)return"";let e=new Date(t);return Number.isNaN(e.getTime())?"":new Intl.DateTimeFormat("pl-PL",{dateStyle:"short",timeStyle:"short"}).format(e)}export{r as a};
