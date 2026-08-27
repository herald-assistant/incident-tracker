var u=class extends Error{constructor(a,o,e){super(`${a} (wiersz ${o}, kolumna ${e})`);this.line=o;this.column=e;this.name="CsvParseError"}};function w(t){return"\uFEFF"+t.map(n=>n.map(h).join(";")).join(`\r
`)+`\r
`}function g(t,n){let a=new Blob([n],{type:"text/csv;charset=utf-8"}),o=URL.createObjectURL(a),e=document.createElement("a");e.href=o,e.download=t,document.body.appendChild(e),e.click(),e.remove(),window.setTimeout(()=>URL.revokeObjectURL(o),0)}function C(t){let n=t.startsWith("\uFEFF")?t.slice(1):t;if(n.length===0)return[];let a=[],o=[],e="",f=!1,d=!1,s=!1,c=1,l=1,p=()=>{o.push(e),e="",d=!1},m=()=>{p(),a.push(o),o=[],s=!0};for(let i=0;i<n.length;i+=1){let r=n[i];if(f){if(r==='"'){if(n[i+1]==='"'){e+='"',i+=1,l+=2,s=!1;continue}f=!1,d=!0}else if(r==="\r"||r===`
`){r==="\r"&&n[i+1]===`
`&&(i+=1),e+=`
`,c+=1,l=1,s=!1;continue}else e+=r;l+=1,s=!1;continue}if(d&&r!==";"&&r!=="\r"&&r!==`
`)throw new u("Nieoczekiwany znak po zamknieciu cytowanej komorki",c,l);if(r==='"'){if(e.length>0)throw new u("Cudzyslow moze rozpoczynac tylko pusta komorke",c,l);f=!0,s=!1}else if(r===";")p(),s=!1;else if(r==="\r"||r===`
`){r==="\r"&&n[i+1]===`
`&&(i+=1),m(),c+=1,l=1;continue}else e+=r,s=!1;l+=1}if(f)throw new u("Niezamknieta cytowana komorka",c,l);return(!s||o.length>0||e.length>0||d)&&m(),a}function h(t){if(t==null)return"";let n=typeof t=="number"?String(t).replace(".",","):String(t);return/[;"\r\n]/.test(n)?`"${n.replace(/"/g,'""')}"`:n}export{u as a,w as b,g as c,C as d};
