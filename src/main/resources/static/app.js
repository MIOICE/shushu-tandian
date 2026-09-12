const state={campuses:[],shops:[],campusId:null,category:'all',query:'',studentOnly:false,sort:'hot'};
const $=selector=>document.querySelector(selector);
const escapeHtml=value=>String(value??'').replace(/[&<>'"]/g,ch=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[ch]));
const firstImage=shop=>(shop.images||'').split(',')[0];
const scoreText=score=>score?(Number(score)/10).toFixed(1):'暂无';

async function api(url){const response=await fetch(url,{headers:{Accept:'application/json'}});if(!response.ok)throw new Error(`HTTP ${response.status}`);const body=await response.json();if(!body.success)throw new Error(body.errorMsg||'请求失败');return body.data}

async function bootstrap(){
  try{
    state.campuses=await api('/campus');
    $('#campusTotal').textContent=state.campuses.length;
    state.campusId=state.campuses.find(item=>item.id===2)?.id||state.campuses[0]?.id;
    renderCampuses();await loadShops();
  }catch(error){showToast(`服务连接失败：${error.message}`);renderEmpty('暂时无法连接服务','鼠鼠正在努力恢复，请稍后再试')}
}

function renderCampuses(){
  $('#campusList').innerHTML=state.campuses.map(item=>`<button class="campus-button ${item.id===state.campusId?'active':''}" data-id="${item.id}" type="button"><b>${escapeHtml(item.name.replace(/校区$/,''))}</b><small>${escapeHtml(item.city)} · ${escapeHtml(item.address)}</small></button>`).join('');
  document.querySelectorAll('.campus-button').forEach(button=>button.addEventListener('click',()=>{state.campusId=Number(button.dataset.id);renderCampuses();loadShops()}));
}
async function loadShops(){
  if(!state.campusId)return;
  $('#shopGrid').classList.add('is-loading');
  try{
    state.shops=await api(`/shop/of/campus?campusId=${state.campusId}&studentOnly=${state.studentOnly}&sort=${state.sort}&current=1`);
    $('#shopTotal').textContent=state.shops.length;
    renderShops();renderWeeklyList()
  }catch(error){renderEmpty('店铺加载失败','鼠鼠暂时没有找到店铺，请稍后再试')}finally{$('#shopGrid').classList.remove('is-loading')}
}
function filteredShops(){
  return state.shops.filter(shop=>{
    const categoryOk=state.category==='all'||(state.category==='food'?shop.typeId===1:shop.typeId!==1);
    const haystack=`${shop.name} ${shop.area} ${shop.tags}`.toLowerCase();
    return categoryOk&&haystack.includes(state.query.toLowerCase());
  })
}
function renderShops(){
  const shops=filteredShops();const campus=state.campuses.find(item=>item.id===state.campusId);
  $('#resultHint').textContent=`${campus?.name||'当前校区'} · 找到 ${shops.length} 家好店`;
  if(!shops.length){renderEmpty('鼠鼠还没找到符合条件的店','换个分类或关闭学生优惠筛选试试');return}
  $('#shopGrid').innerHTML=shops.map((shop,index)=>{
    const image=firstImage(shop);const tags=(shop.tags||'校园周边').split(',').slice(0,3);
    return `<article class="shop-card" data-id="${shop.id}" tabindex="0"><div class="shop-cover"><div class="image-fallback">鼠</div>${image?`<img src="${escapeHtml(image)}" alt="${escapeHtml(shop.name)}" loading="lazy" referrerpolicy="no-referrer" onerror="this.remove()">`:''}${shop.studentDiscount?'<span class="badge">学生专享</span>':''}<span class="rank">TOP ${index+1}</span></div><div class="card-body"><div class="card-title-row"><h3>${escapeHtml(shop.name)}</h3><span class="price">¥${shop.avgPrice||'--'}<small>/人</small></span></div><div class="rating"><span class="stars">★★★★★</span><b>${scoreText(shop.score)}</b><span>${Number(shop.comments||0).toLocaleString()}条评价</span></div><div class="tag-list">${tags.map(tag=>`<span class="tag">${escapeHtml(tag)}</span>`).join('')}</div><div class="card-meta"><span>⌖ ${escapeHtml(shop.area||shop.campusName)}</span><span>已热卖 ${Number(shop.sold||0).toLocaleString()}</span></div></div></article>`
  }).join('');
  document.querySelectorAll('.shop-card').forEach(card=>{const open=()=>openShop(Number(card.dataset.id));card.addEventListener('click',open);card.addEventListener('keydown',event=>{if(event.key==='Enter')open()})})
}
function renderWeeklyList(){
  const shops=[...state.shops].sort((a,b)=>Number(b.sold||0)-Number(a.sold||0)).slice(0,3);
  $('#weeklyList').innerHTML=shops.length?shops.map((shop,index)=>`<button class="weekly-item" data-id="${shop.id}" type="button"><span class="weekly-rank">${index+1}</span><span class="weekly-copy"><b>${escapeHtml(shop.name)}</b><small>${escapeHtml(shop.area||shop.campusName||'校园周边')} · ${Number(shop.sold||0).toLocaleString()} 人气</small></span><span class="weekly-score">${scoreText(shop.score)} ★</span></button>`).join(''):'<p>这所校园的热榜正在生成中…</p>';
  document.querySelectorAll('.weekly-item').forEach(button=>button.addEventListener('click',()=>openShop(Number(button.dataset.id))))
}
function renderEmpty(title,detail){$('#shopGrid').innerHTML=`<div class="empty-state"><b>${escapeHtml(title)}</b><span>${escapeHtml(detail)}</span></div>`}
async function openShop(id){
  try{
    const shop=await api(`/shop/${id}`);const image=firstImage(shop);
    $('#dialogContent').innerHTML=`${image?`<img class="dialog-cover" src="${escapeHtml(image)}" alt="${escapeHtml(shop.name)}" referrerpolicy="no-referrer">`:''}<div class="dialog-body"><span class="section-kicker">${shop.studentDiscount?'STUDENT SPECIAL':'CAMPUS PICK'}</span><h2>${escapeHtml(shop.name)}</h2><p>⌖ ${escapeHtml(shop.address)}<br>营业时间：${escapeHtml(shop.openHours||'以商家实际为准')}</p><div class="tag-list">${(shop.tags||'校园周边').split(',').map(tag=>`<span class="tag">${escapeHtml(tag)}</span>`).join('')}</div><div class="dialog-stats"><div><span>综合评分</span><b>${scoreText(shop.score)} ★</b></div><div><span>人均消费</span><b>¥${shop.avgPrice||'--'}</b></div><div><span>校园热度</span><b>${Number(shop.sold||0).toLocaleString()}</b></div></div></div>`;
    $('#shopDialog').showModal()
  }catch(error){showToast(`详情加载失败：${error.message}`)}
}
function showToast(message){const toast=$('#toast');toast.textContent=message;toast.classList.add('show');clearTimeout(showToast.timer);showToast.timer=setTimeout(()=>toast.classList.remove('show'),2600)}

$('#sortSelect').addEventListener('change',event=>{state.sort=event.target.value;loadShops()});
$('#studentOnly').addEventListener('change',event=>{state.studentOnly=event.target.checked;loadShops()});
$('#searchInput').addEventListener('input',event=>{state.query=event.target.value.trim();renderShops()});
$('#categoryTabs').addEventListener('click',event=>{const button=event.target.closest('button');if(!button)return;state.category=button.dataset.category;document.querySelectorAll('#categoryTabs button').forEach(item=>item.classList.toggle('active',item===button));renderShops()});
$('#randomButton').addEventListener('click',()=>{const shops=filteredShops();if(!shops.length)return showToast('当前没有可推荐的店铺');openShop(shops[Math.floor(Math.random()*shops.length)].id)});
$('#dialogClose').addEventListener('click',()=>$('#shopDialog').close());
$('#shopDialog').addEventListener('click',event=>{if(event.target===$('#shopDialog'))$('#shopDialog').close()});
bootstrap();
