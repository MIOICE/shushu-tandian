const $=selector=>document.querySelector(selector);
const format=value=>Number(value||0).toLocaleString();

async function api(url){
  const response=await fetch(url,{headers:{Accept:'application/json'},cache:'no-store'});
  if(!response.ok)throw new Error(`HTTP ${response.status}`);
  const body=await response.json();
  if(!body.success)throw new Error(body.errorMsg||'请求失败');
  return body.data;
}

function setStatus(selector,online){
  const element=$(selector);
  element.textContent=online?'正常':'异常';
  element.className=`status ${online?'online':'offline'}`;
}

function renderMetrics(metrics){
  const requests=Number(metrics.requests??metrics.totalRequests??0);
  const localHits=Number(metrics.localHits||0);
  const redisHits=Number(metrics.redisHits||0);
  const dbQueries=Number(metrics.databaseQueries||0);
  const suppliedRate=Number(metrics.hitRate);
  const calculatedRate=requests>0?(localHits+redisHits)/requests:0;
  const rate=Number.isFinite(suppliedRate)&&suppliedRate>=0?suppliedRate:calculatedRate;
  const percent=Math.max(0,Math.min(100,rate*100));
  $('#hitRate').textContent=requests>0?`${percent.toFixed(2)}%`:'暂无请求';
  $('#requests').textContent=format(requests);
  $('#localHits').textContent=format(localHits);
  $('#redisHits').textContent=format(redisHits);
  $('#dbQueries').textContent=format(dbQueries);
  $('#hitGauge').style.background=`conic-gradient(var(--orange) ${percent*3.6}deg,#292e37 0deg)`;
  $('#cacheNote').textContent=requests>0?`实时统计：${format(requests)} 次店铺详情访问中，${format(localHits+redisHits)} 次由缓存直接响应。`:'缓存指标接口正常，当前实例尚无店铺详情访问记录。';
}

async function refreshDashboard(){
  const button=$('#refreshButton');
  button.disabled=true;
  button.textContent='刷新中…';
  let cacheHealthy=false;
  let businessHealthy=false;
  try{
    const metrics=await api('/shop/cache/stats');
    renderMetrics(metrics||{});
    cacheHealthy=true;
  }catch(error){
    $('#cacheNote').textContent=`缓存指标读取失败：${error.message}`;
  }
  try{
    const campuses=await api('/campus');
    if(Array.isArray(campuses)&&campuses.length){
      await api(`/shop/of/campus?campusId=${campuses[0].id}&studentOnly=false&sort=hot&current=1`);
      businessHealthy=true;
    }
  }catch(error){businessHealthy=false}
  setStatus('#apiStatus',businessHealthy);
  setStatus('#dbStatus',businessHealthy);
  setStatus('#redisStatus',cacheHealthy);
  const healthy=businessHealthy&&cacheHealthy;
  const overall=$('#overallHealth');
  overall.className=`health-pill ${healthy?'healthy':'unhealthy'}`;
  overall.querySelector('span').textContent=healthy?'核心服务正常':'部分服务异常';
  $('#updatedAt').textContent=new Date().toLocaleTimeString('zh-CN',{hour12:false});
  button.disabled=false;
  button.textContent='刷新数据';
}

$('#refreshButton').addEventListener('click',refreshDashboard);
refreshDashboard();
setInterval(refreshDashboard,15000);
