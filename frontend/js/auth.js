const s0=getSession();if(s0)location.replace(s0.role+'.html');
const rq=new URLSearchParams(location.search).get('role');
$(`input[value=${['admin','doctor','patient'].includes(rq)?rq:'patient'}]`).checked=true;
$('#pw-t').onclick=()=>{const p=$('#pw');p.type=p.type==='password'?'text':'password'};
$('#fp').onclick=e=>{e.preventDefault();modal('Forgot password','<p>Password recovery is disabled in this prototype. No real authentication or password database is connected.</p>')};
$('#lf').onsubmit=e=>{e.preventDefault();const u=$('#uid').value.trim(),p=$('#pw').value.trim();$('#e1').textContent=u?'':'Enter your User ID or email.';$('#e2').textContent=p?'':'Enter your password.';if(!u||!p)return;
const b=$('#lb');b.classList.add('load');b.textContent='Signing you in...';setTimeout(()=>login(u,$('input[name=role]:checked').value,$('#rm').checked,p),700)};
$$('[data-demo]').forEach(b=>b.onclick=()=>login(b.dataset.demo==='admin'?'Admin':b.dataset.demo==='doctor'?'Doctor':'Adarsh',b.dataset.demo,$('#rm').checked));
