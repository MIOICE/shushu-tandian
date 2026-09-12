const $ = selector => document.querySelector(selector);
const format = value => Number(value || 0).toLocaleString();
const escapeHtml = value => String(value ?? '').replace(/[&<>'"]/g, character => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'
}[character]));

const publisherState = { campuses: [], shops: [], activities: [] };

async function api(url, options = {}) {
  const response = await fetch(url, {
    method: options.method || 'GET',
    headers: {
      Accept: 'application/json',
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(options.headers || {})
    },
    body: options.body ? JSON.stringify(options.body) : undefined,
    cache: 'no-store'
  });
  let result;
  try {
    result = await response.json();
  } catch (error) {
    throw new Error(`服务返回异常（HTTP ${response.status}）`);
  }
  if (!response.ok || !result.success) throw new Error(result.errorMsg || `HTTP ${response.status}`);
  return result.data;
}

function setStatus(selector, online) {
  const element = $(selector);
  element.textContent = online ? '正常' : '异常';
  element.className = `status ${online ? 'online' : 'offline'}`;
}

function renderMetrics(metrics) {
  const requests = Number(metrics.requests ?? metrics.totalRequests ?? 0);
  const localHits = Number(metrics.localHits || 0);
  const redisHits = Number(metrics.redisHits || 0);
  const dbQueries = Number(metrics.databaseQueries || 0);
  const suppliedRate = Number(metrics.hitRate);
  const calculatedRate = requests > 0 ? (localHits + redisHits) / requests : 0;
  const rate = Number.isFinite(suppliedRate) && suppliedRate >= 0 ? suppliedRate : calculatedRate;
  const percent = Math.max(0, Math.min(100, rate * 100));
  $('#hitRate').textContent = requests > 0 ? `${percent.toFixed(2)}%` : '暂无请求';
  $('#requests').textContent = format(requests);
  $('#localHits').textContent = format(localHits);
  $('#redisHits').textContent = format(redisHits);
  $('#dbQueries').textContent = format(dbQueries);
  $('#hitGauge').style.background = `conic-gradient(var(--blue) ${percent * 3.6}deg,#e8e2dc 0deg)`;
  $('#cacheNote').textContent = requests > 0
    ? `实时统计：${format(requests)} 次店铺详情访问中，${format(localHits + redisHits)} 次由缓存直接响应。`
    : '缓存指标接口正常，当前实例尚无店铺详情访问记录。';
}

async function refreshDashboard() {
  const button = $('#refreshButton');
  button.disabled = true;
  button.textContent = '刷新中…';
  let cacheHealthy = false;
  let businessHealthy = false;
  try {
    const metrics = await api('/shop/cache/stats');
    renderMetrics(metrics || {});
    cacheHealthy = true;
  } catch (error) {
    $('#cacheNote').textContent = `缓存指标读取失败：${error.message}`;
  }
  try {
    const campuses = publisherState.campuses.length ? publisherState.campuses : await api('/campus');
    if (Array.isArray(campuses) && campuses.length) {
      await api(`/shop/of/campus?campusId=${campuses[0].id}&studentOnly=false&sort=hot&current=1`);
      businessHealthy = true;
    }
  } catch (error) {
    businessHealthy = false;
  }
  setStatus('#apiStatus', businessHealthy);
  setStatus('#dbStatus', businessHealthy);
  setStatus('#redisStatus', cacheHealthy);
  const healthy = businessHealthy && cacheHealthy;
  const overall = $('#overallHealth');
  overall.className = `health-pill ${healthy ? 'healthy' : 'unhealthy'}`;
  overall.querySelector('span').textContent = healthy ? '核心服务正常' : '部分服务异常';
  $('#updatedAt').textContent = new Date().toLocaleTimeString('zh-CN', { hour12: false });
  button.disabled = false;
  button.textContent = '刷新数据';
}

function localDateTimeValue(date) {
  const shifted = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return shifted.toISOString().slice(0, 16);
}

function setDefaultCampaignTime() {
  const now = new Date();
  $('#campaignBegin').value = localDateTimeValue(new Date(now.getTime() + 5 * 60000));
  $('#campaignEnd').value = localDateTimeValue(new Date(now.getTime() + 7 * 24 * 60 * 60000));
}

async function initializePublisher() {
  setDefaultCampaignTime();
  const rememberedToken = sessionStorage.getItem('shushu_ops_token');
  if (rememberedToken) {
    $('#opsToken').value = rememberedToken;
    $('#rememberToken').checked = true;
  }
  try {
    publisherState.campuses = await api('/campus');
    $('#campaignCampus').innerHTML = publisherState.campuses
      .map(campus => `<option value="${campus.id}">${escapeHtml(campus.name)} · ${escapeHtml(campus.city)}</option>`).join('');
    if (publisherState.campuses.length) {
      const defaultCampus = publisherState.campuses.find(campus => Number(campus.id) === 4) || publisherState.campuses[0];
      $('#campaignCampus').value = String(defaultCampus.id);
      await changeCampaignCampus();
    }
  } catch (error) {
    $('#campaignCampus').innerHTML = '<option value="">校区加载失败</option>';
    showPublishResult(`初始化失败：${error.message}`, false);
  }
}

async function changeCampaignCampus() {
  const campusId = Number($('#campaignCampus').value);
  const campus = publisherState.campuses.find(item => Number(item.id) === campusId);
  $('#campaignCampusName').textContent = campus?.name || '尚未选择';
  $('#campaignShop').disabled = true;
  $('#campaignShop').innerHTML = '<option value="">正在加载店铺…</option>';
  if (!campusId) return;
  const [shopResult, activityResult] = await Promise.allSettled([
    api(`/shop/of/campus?campusId=${campusId}&studentOnly=false&sort=hot&current=1`),
    api(`/voucher/seckill/active?campusId=${campusId}`)
  ]);
  if (shopResult.status === 'fulfilled') {
    publisherState.shops = shopResult.value || [];
    $('#campaignShop').innerHTML = publisherState.shops.length
      ? publisherState.shops.map(shop => `<option value="${shop.id}">${escapeHtml(shop.name)}</option>`).join('')
      : '<option value="">当前校区暂无店铺</option>';
    $('#campaignShop').disabled = !publisherState.shops.length;
  } else {
    publisherState.shops = [];
    $('#campaignShop').innerHTML = '<option value="">店铺加载失败</option>';
  }
  publisherState.activities = activityResult.status === 'fulfilled' ? activityResult.value || [] : [];
  renderCampaigns(activityResult.status === 'rejected' ? activityResult.reason : null);
}

function money(cents) {
  return `¥${(Number(cents || 0) / 100).toFixed(2)}`;
}

function formatCampaignTime(value) {
  if (!value) return '--';
  return new Date(value).toLocaleString('zh-CN', { hour12: false, month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
}

function renderCampaigns(error) {
  $('#campaignCount').textContent = `${publisherState.activities.length} 个活动`;
  if (error) {
    $('#campaignList').innerHTML = `<div class="campaign-empty">活动读取失败：${escapeHtml(error.message)}</div>`;
    return;
  }
  if (!publisherState.activities.length) {
    $('#campaignList').innerHTML = '<div class="campaign-empty">当前校区暂无活动，可以发布第一场秒杀</div>';
    return;
  }
  const now = Date.now();
  $('#campaignList').innerHTML = publisherState.activities.map(voucher => {
    const upcoming = new Date(voucher.beginTime).getTime() > now;
    return `<article class="campaign-item">
      <div class="campaign-item-top"><span>${upcoming ? '即将开始' : '进行中'}${voucher.studentOnly ? ' · 学生专享' : ''}</span><em>余量 ${Number(voucher.stock || 0)}</em></div>
      <b>${escapeHtml(voucher.title)}</b>
      <small>${escapeHtml(voucher.shopName || '校园店铺')}<br>${formatCampaignTime(voucher.beginTime)} — ${formatCampaignTime(voucher.endTime)}</small>
      <div class="campaign-price"><strong>${money(voucher.payValue)}</strong><del>${money(voucher.actualValue)}</del></div>
    </article>`;
  }).join('');
}

function showPublishResult(message, success) {
  const result = $('#publishResult');
  result.hidden = false;
  result.className = `publish-result ${success ? 'success' : 'error'}`;
  result.textContent = message;
}

function campaignPayload() {
  const payYuan = Number($('#campaignPayValue').value);
  const actualYuan = Number($('#campaignActualValue').value);
  const stock = Number($('#campaignStock').value);
  const beginTime = $('#campaignBegin').value;
  const endTime = $('#campaignEnd').value;
  if (!Number.isFinite(payYuan) || !Number.isFinite(actualYuan) || payYuan < 0 || actualYuan <= 0 || payYuan > actualYuan) {
    throw new Error('秒杀价必须小于等于抵扣面额');
  }
  if (!Number.isInteger(stock) || stock <= 0) throw new Error('库存必须是大于 0 的整数');
  if (!beginTime || !endTime || new Date(beginTime) >= new Date(endTime)) throw new Error('活动结束时间必须晚于开始时间');
  if (new Date(endTime).getTime() <= Date.now()) throw new Error('活动结束时间必须晚于当前时间');
  return {
    campusId: Number($('#campaignCampus').value),
    shopId: Number($('#campaignShop').value),
    title: $('#campaignTitle').value.trim(),
    subTitle: $('#campaignSubtitle').value.trim(),
    rules: $('#campaignRules').value.trim(),
    payValue: Math.round(payYuan * 100),
    actualValue: Math.round(actualYuan * 100),
    stock,
    studentOnly: $('#campaignStudentOnly').checked ? 1 : 0,
    status: 1,
    beginTime: beginTime.length === 16 ? `${beginTime}:00` : beginTime,
    endTime: endTime.length === 16 ? `${endTime}:00` : endTime
  };
}

async function publishCampaign(event) {
  event.preventDefault();
  const button = $('#publishButton');
  const token = $('#opsToken').value.trim();
  if (!token) return showPublishResult('请输入运营令牌', false);
  button.disabled = true;
  button.textContent = '正在发布…';
  try {
    const payload = campaignPayload();
    if (!payload.campusId || !payload.shopId || !payload.title) throw new Error('校区、店铺和活动标题不能为空');
    const voucherId = await api('/voucher/seckill', {
      method: 'POST',
      headers: { 'X-Ops-Token': token, 'X-Device-Fingerprint': 'shushu-operations-dashboard' },
      body: payload
    });
    if ($('#rememberToken').checked) sessionStorage.setItem('shushu_ops_token', token);
    else sessionStorage.removeItem('shushu_ops_token');
    showPublishResult(`发布成功，活动编号 ${voucherId}。Redis 库存已初始化，用户首页刷新后即可参与。`, true);
    $('#campaignTitle').value = '';
    $('#campaignSubtitle').value = '';
    $('#campaignRules').value = '';
    setDefaultCampaignTime();
    await changeCampaignCampus();
  } catch (error) {
    showPublishResult(`发布失败：${error.message}`, false);
  } finally {
    button.disabled = false;
    button.textContent = '确认发布活动';
  }
}

function initializeNavigation() {
  const links = [...document.querySelectorAll('.side-nav a')];
  links.forEach(link => {
    link.addEventListener('click', () => {
      links.forEach(item => item.classList.toggle('active', item === link));
      $('#pageContextTitle').textContent = link.dataset.pageTitle;
    });
  });
}

$('#refreshButton').addEventListener('click', refreshDashboard);
$('#campaignCampus').addEventListener('change', changeCampaignCampus);
$('#campaignForm').addEventListener('submit', publishCampaign);
$('#toggleToken').addEventListener('click', () => {
  const input = $('#opsToken');
  const showing = input.type === 'text';
  input.type = showing ? 'password' : 'text';
  $('#toggleToken').textContent = showing ? '显示' : '隐藏';
});

initializeNavigation();
initializePublisher();
refreshDashboard();
setInterval(refreshDashboard, 15000);
