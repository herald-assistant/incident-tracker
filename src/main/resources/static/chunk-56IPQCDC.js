function c(e){return"\uFEFF"+e.map(n=>n.map(i).join(";")).join(`\r
`)+`\r
`}function l(e,n){let o=new Blob([n],{type:"text/csv;charset=utf-8"}),r=URL.createObjectURL(o),t=document.createElement("a");t.href=r,t.download=e,document.body.appendChild(t),t.click(),t.remove(),window.setTimeout(()=>URL.revokeObjectURL(r),0)}function i(e){if(e==null)return"";let n=typeof e=="number"?String(e).replace(".",","):String(e);return/[;"\r\n]/.test(n)?`"${n.replace(/"/g,'""')}"`:n}export{c as a,l as b};
