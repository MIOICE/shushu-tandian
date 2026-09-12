const state = {
  campuses: [], shops: [], activities: [], vouchers: new Map(), campusId: null,
  category: 'all', query: '', studentOnly: false, sort: 'hot', user: null
};

const $ = selector => document.querySelector(selector);
const wait = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds));
const escapeHtml = value => String(value ?? '').replace(/[&<>'"]/g, character => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'
}[character]));
const firstImage = shop => (shop.images || '').split(',')[0];
const scoreText = score => score ? (Number(score) / 10).toFixed(1) : '暂无';
const money = cents => `¥${(Number(cents || 0) / 100).toFixed(2).replace(/\.00$/, '')}`;
const tokenKey = 'shushu_token';
const fingerprintKey = 'shushu_device_fingerprint';

function getFingerprint() {
  let fingerprint = localStorage.getItem(fingerprintKey);
  if (!fingerprint) {
    fingerprint = globalThis.crypto?.randomUUID?.() || `web-${Date.now()}-${Math.random().toString(16).slice(2)}`;
    localStorage.setItem(fingerprintKey, fingerprint);
  }
  return fingerprint;
}

async function api(url, options = {}) {
  const method = options.method || 'GET';
  const retries = method === 'GET' ? 1 : 0;
  let lastError;
  for (let attempt = 0; attempt <= retries; attempt++) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 8000);
    const token = localStorage.getItem(tokenKey);
    const headers = {
      Accept: 'application/json',
      'X-Device-Fingerprint': getFingerprint(),
      ...(token ? { Authorization: token } : {}),
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(options.headers || {})
    };
    try {
      const response = await fetch(url, {
        method, headers, body: options.body ? JSON.stringify(options.body) : undefined,
        cache: 'no-store', signal: controller.signal
      });
      if (response.status === 401) {
        const unauthorized = new Error('请先登录');
        unauthorized.noRetry = true;
        throw unauthorized;
      }
      if (!response.ok) throw new Error(`服务异常（${response.status}）`);
      const result = await response.json();
      if (!result.success) throw new Error(result.errorMsg || '请求失败');
      return result.data;
    } catch (error) {
      lastError = error;
      if (error.noRetry) throw error;
      if (attempt < retries) await wait(350);
    } finally {
      clearTimeout(timeout);
    }
  }
  throw new Error(lastError?.name === 'AbortError' ? '请求超时，请稍后重试' : lastError?.message || '请求失败');
}

async function bootstrap() {
  try {
    state.campuses = await api('/campus');
    $('#campusTotal').textContent = state.campuses.length;
    await restoreSession();
    const preferredCampus = state.user?.campusId;
    state.campusId = state.campuses.find(item => item.id === preferredCampus)?.id
      || state.campuses.find(item => item.id === 2)?.id || state.campuses[0]?.id;
    renderCampuses();
    await loadShops();
  } catch (error) {
    showToast(`服务连接失败：${error.message}`);
    renderEmpty('暂时无法连接服务', '鼠鼠正在努力恢复，请稍后再试');
  }
}

async function restoreSession() {
  if (!localStorage.getItem(tokenKey)) {
    renderAccount();
    return;
  }
  try {
    state.user = await api('/user/me');
  } catch (error) {
    localStorage.removeItem(tokenKey);
    state.user = null;
  }
  renderAccount();
}

function renderAccount() {
  $('#userLabel').textContent = state.user?.nickName || '登录 / 注册';
  $('#loginPanel').hidden = Boolean(state.user);
  $('#profilePanel').hidden = !state.user;
  if (!state.user) return;
  $('#profileName').textContent = state.user.nickName || '鼠鼠同学';
  const campus = state.campuses.find(item => item.id === (state.user.campusId || state.campusId));
  $('#profileCampus').textContent = campus ? `当前校园：${campus.name}` : '选择校区后获得更准确的推荐';
}

function renderCampuses() {
  $('#campusList').innerHTML = state.campuses.map(item => `
    <button class="campus-button ${item.id === state.campusId ? 'active' : ''}" data-id="${item.id}" type="button">
      <b>${escapeHtml(item.name.replace(/校区$/, ''))}</b>
      <small>${escapeHtml(item.city)} · ${escapeHtml(item.address)}</small>
    </button>`).join('');
  document.querySelectorAll('.campus-button').forEach(button => button.addEventListener('click', async () => {
    state.campusId = Number(button.dataset.id);
    renderCampuses();
    renderAccount();
    await loadShops();
    if (state.user) {
      try {
        await api(`/user/campus/${state.campusId}`, { method: 'PUT' });
        state.user.campusId = state.campusId;
        renderAccount();
      } catch (error) {
        showToast(`校区偏好保存失败：${error.message}`);
      }
    }
  }));
}

async function loadShops() {
  if (!state.campusId) return;
  $('#shopGrid').classList.add('is-loading');
  $('#seckillGrid').innerHTML = '<div class="seckill-placeholder">正在加载当前校区活动…</div>';
  const [shopResult, activityResult] = await Promise.allSettled([
      api(`/shop/of/campus?campusId=${state.campusId}&studentOnly=${state.studentOnly}&sort=${state.sort}&current=1`),
      api(`/voucher/seckill/active?campusId=${state.campusId}`)
  ]);
  try {
    if (shopResult.status === 'rejected') throw shopResult.reason;
    state.shops = shopResult.value || [];
    $('#shopTotal').textContent = state.shops.length;
    renderShops();
    renderWeeklyList();
  } catch (error) {
    renderEmpty('店铺加载失败', error.message);
  } finally {
    $('#shopGrid').classList.remove('is-loading');
  }
  if (activityResult.status === 'fulfilled') {
    state.activities = activityResult.value || [];
    state.activities.forEach(voucher => state.vouchers.set(String(voucher.id), voucher));
    renderSeckill();
  } else {
    state.activities = [];
    $('#seckillGrid').innerHTML = `<div class="seckill-placeholder">活动加载失败：${escapeHtml(activityResult.reason?.message || '请稍后再试')}</div>`;
  }
}

function filteredShops() {
  return state.shops.filter(shop => {
    const categoryOk = state.category === 'all' || (state.category === 'food' ? shop.typeId === 1 : shop.typeId !== 1);
    const haystack = `${shop.name} ${shop.area} ${shop.tags}`.toLowerCase();
    return categoryOk && haystack.includes(state.query.toLowerCase());
  });
}

function renderShops() {
  const shops = filteredShops();
  const campus = state.campuses.find(item => item.id === state.campusId);
  $('#resultHint').textContent = `${campus?.name || '当前校区'} · 找到 ${shops.length} 家好店`;
  if (!shops.length) {
    renderEmpty('鼠鼠还没找到符合条件的店', '换个分类或关闭学生优惠筛选试试');
    return;
  }
  $('#shopGrid').innerHTML = shops.map((shop, index) => {
    const image = firstImage(shop);
    const tags = (shop.tags || '校园周边').split(',').slice(0, 3);
    return `<article class="shop-card" data-id="${shop.id}" tabindex="0">
      <div class="shop-cover"><div class="image-fallback">鼠</div>${image ? `<img src="${escapeHtml(image)}" alt="${escapeHtml(shop.name)}" loading="lazy" referrerpolicy="no-referrer" onerror="this.remove()">` : ''}${shop.studentDiscount ? '<span class="badge">学生专享</span>' : ''}<span class="rank">TOP ${index + 1}</span></div>
      <div class="card-body"><div class="card-title-row"><h3>${escapeHtml(shop.name)}</h3><span class="price">¥${shop.avgPrice || '--'}<small>/人</small></span></div>
      <div class="rating"><span class="stars">★★★★★</span><b>${scoreText(shop.score)}</b><span>${Number(shop.comments || 0).toLocaleString()}条评价</span></div>
      <div class="tag-list">${tags.map(tag => `<span class="tag">${escapeHtml(tag)}</span>`).join('')}</div>
      <div class="card-meta"><span>⌖ ${escapeHtml(shop.area || shop.campusName)}</span><span>已热卖 ${Number(shop.sold || 0).toLocaleString()}</span></div></div>
    </article>`;
  }).join('');
  document.querySelectorAll('.shop-card').forEach(card => {
    const open = () => openShop(Number(card.dataset.id));
    card.addEventListener('click', open);
    card.addEventListener('keydown', event => { if (event.key === 'Enter') open(); });
  });
}

function renderWeeklyList() {
  const shops = [...state.shops].sort((a, b) => Number(b.sold || 0) - Number(a.sold || 0)).slice(0, 3);
  $('#weeklyList').innerHTML = shops.length ? shops.map((shop, index) => `
    <button class="weekly-item" data-id="${shop.id}" type="button"><span class="weekly-rank">${index + 1}</span>
      <span class="weekly-copy"><b>${escapeHtml(shop.name)}</b><small>${escapeHtml(shop.area || shop.campusName || '校园周边')} · ${Number(shop.sold || 0).toLocaleString()} 人气</small></span>
      <span class="weekly-score">${scoreText(shop.score)} ★</span></button>`).join('') : '<p>这所校园的热榜正在生成中…</p>';
  document.querySelectorAll('.weekly-item').forEach(button => button.addEventListener('click', () => openShop(Number(button.dataset.id))));
}

function activityStatus(voucher) {
  const now = Date.now();
  const begin = new Date(voucher.beginTime).getTime();
  const end = new Date(voucher.endTime).getTime();
  if (now < begin) return { text: '即将开始', disabled: true, className: 'upcoming' };
  if (now >= end) return { text: '已结束', disabled: true, className: 'ended' };
  if (Number(voucher.stock || 0) <= 0) return { text: '已抢光', disabled: true, className: 'sold-out' };
  return { text: state.user ? '立即抢购' : '登录后抢', disabled: false, className: 'active' };
}

function renderSeckill() {
  if (!state.activities.length) {
    $('#seckillGrid').innerHTML = '<div class="seckill-placeholder"><b>当前校区暂无进行中的秒杀</b><span>切换校区看看，鼠鼠会持续上新福利</span></div>';
    $('#seckillCountdown').textContent = '--:--:--';
    return;
  }
  $('#seckillGrid').innerHTML = state.activities.map(voucher => {
    const status = activityStatus(voucher);
    const discount = Math.max(0, Number(voucher.actualValue || 0) - Number(voucher.payValue || 0));
    return `<article class="seckill-card ${status.className}">
      <div class="seckill-card-top"><span>${voucher.studentOnly ? '学生认证专享' : '校园用户可抢'}</span><em>剩余 ${Number(voucher.stock || 0)}</em></div>
      <h3>${escapeHtml(voucher.title)}</h3><p>${escapeHtml(voucher.shopName || '校园好店')} · ${escapeHtml(voucher.subTitle || '限时优惠')}</p>
      <div class="seckill-price"><strong>${money(voucher.payValue)}</strong><del>${money(voucher.actualValue)}</del><span>立省 ${money(discount)}</span></div>
      <button class="seckill-button" data-voucher-id="${voucher.id}" type="button" ${status.disabled ? 'disabled' : ''}>${status.text}</button>
    </article>`;
  }).join('');
  updateCountdown();
}

function updateCountdown() {
  const now = Date.now();
  const target = state.activities.map(item => new Date(item.endTime).getTime()).filter(time => time > now).sort()[0];
  if (!target) return;
  const seconds = Math.max(0, Math.floor((target - now) / 1000));
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const remainder = seconds % 60;
  $('#seckillCountdown').textContent = days > 30 ? '长期有效' : `${days ? `${days}天 ` : ''}${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(remainder).padStart(2, '0')}`;
}

function voucherMarkup(voucher) {
  state.vouchers.set(String(voucher.id), voucher);
  const status = voucher.type === 1 ? activityStatus(voucher) : null;
  return `<div class="voucher-row">
    <div class="voucher-value"><b>${money(voucher.actualValue)}</b><span>${voucher.type === 1 ? '秒杀券' : '代金券'}</span></div>
    <div class="voucher-copy"><b>${escapeHtml(voucher.title)}</b><span>${escapeHtml(voucher.subTitle || voucher.rules || '到店可用')}</span></div>
    ${voucher.type === 1 ? `<button class="mini-seckill" data-voucher-id="${voucher.id}" type="button" ${status.disabled ? 'disabled' : ''}>${status.text}<small>${money(voucher.payValue)}</small></button>` : '<span class="voucher-use">到店使用</span>'}
  </div>`;
}

async function openShop(id) {
  try {
    const [shop, vouchers] = await Promise.all([api(`/shop/${id}`), api(`/voucher/list/${id}`)]);
    const image = firstImage(shop);
    $('#dialogContent').innerHTML = `${image ? `<img class="dialog-cover" src="${escapeHtml(image)}" alt="${escapeHtml(shop.name)}" referrerpolicy="no-referrer">` : ''}
      <div class="dialog-body"><span class="section-kicker">${shop.studentDiscount ? 'STUDENT SPECIAL' : 'CAMPUS PICK'}</span><h2>${escapeHtml(shop.name)}</h2>
      <p>⌖ ${escapeHtml(shop.address)}<br>营业时间：${escapeHtml(shop.openHours || '以商家实际为准')}</p>
      <div class="tag-list">${(shop.tags || '校园周边').split(',').map(tag => `<span class="tag">${escapeHtml(tag)}</span>`).join('')}</div>
      <div class="dialog-stats"><div><span>综合评分</span><b>${scoreText(shop.score)} ★</b></div><div><span>人均消费</span><b>¥${shop.avgPrice || '--'}</b></div><div><span>校园热度</span><b>${Number(shop.sold || 0).toLocaleString()}</b></div></div>
      <div class="voucher-section"><h3>优惠与秒杀</h3>${vouchers?.length ? vouchers.map(voucherMarkup).join('') : '<p class="no-voucher">这家店暂时没有可用优惠券</p>'}</div></div>`;
    $('#shopDialog').showModal();
  } catch (error) {
    showToast(`详情加载失败：${error.message}`);
  }
}

async function claimVoucher(voucherId) {
  if (!state.user) {
    $('#accountDialog').showModal();
    showToast('请先用手机号登录');
    return;
  }
  const button = document.querySelector(`[data-voucher-id="${voucherId}"]:not([disabled])`);
  if (button) button.disabled = true;
  try {
    const orderId = await api(`/voucher-order/seckill/${voucherId}`, { method: 'POST' });
    showToast('抢购请求已受理，正在创建订单…');
    const order = await pollOrder(String(orderId));
    showToast(order ? `抢购成功：${order.statusDescription}` : '请求已受理，请到我的订单查看');
    await loadOrders(false);
    if ($('#shopDialog').open) $('#shopDialog').close();
    $('#ordersDialog').showModal();
  } catch (error) {
    if (error.message === '请先登录') {
      localStorage.removeItem(tokenKey);
      state.user = null;
      renderAccount();
      if (!$('#accountDialog').open) $('#accountDialog').showModal();
    }
    showToast(error.message);
  } finally {
    if (button) button.disabled = false;
  }
}

async function pollOrder(orderId) {
  for (let attempt = 0; attempt < 40; attempt++) {
    await wait(750);
    try {
      return await api(`/voucher-order/${encodeURIComponent(orderId)}`);
    } catch (error) {
      if (!error.message.includes('订单不存在')) throw error;
    }
  }
  return null;
}

async function loadOrders(openDialog = true) {
  if (!state.user) return;
  if (openDialog) {
    $('#accountDialog').close();
    $('#ordersDialog').showModal();
  }
  $('#orderList').innerHTML = '<div class="order-empty">正在加载订单…</div>';
  try {
    const orders = await api('/voucher-order/me?current=1');
    $('#orderList').innerHTML = orders?.length ? orders.map(order => {
      const voucher = state.vouchers.get(String(order.voucherId));
      return `<article class="order-card"><div><span class="order-status status-${order.status}">${escapeHtml(order.statusDescription || '处理中')}</span>
        <h3>${escapeHtml(voucher?.title || `优惠券 #${order.voucherId}`)}</h3><p>订单号 ${escapeHtml(order.id)} · ${formatDate(order.createTime)}</p></div>
        <div class="order-actions">${order.status === 1 ? `<button data-order-action="pay" data-order-id="${order.id}" type="button">模拟支付</button><button data-order-action="cancel" data-order-id="${order.id}" type="button">取消订单</button>` : ''}</div></article>`;
    }).join('') : '<div class="order-empty"><b>还没有订单</b><span>去秒杀活动抢一份校园优惠吧</span></div>';
  } catch (error) {
    $('#orderList').innerHTML = `<div class="order-empty">订单加载失败：${escapeHtml(error.message)}</div>`;
  }
}

async function changeOrder(orderId, action) {
  try {
    await api(`/voucher-order/${encodeURIComponent(orderId)}/${action}`, { method: 'POST' });
    showToast(action === 'pay' ? '支付成功' : '订单已取消');
    await loadOrders(false);
    await loadShops();
  } catch (error) {
    showToast(error.message);
  }
}

function formatDate(value) {
  if (!value) return '--';
  return new Date(value).toLocaleString('zh-CN', { hour12: false });
}

function renderEmpty(title, detail) {
  $('#shopGrid').innerHTML = `<div class="empty-state"><b>${escapeHtml(title)}</b><span>${escapeHtml(detail)}</span></div>`;
}

function showToast(message) {
  const toast = $('#toast');
  toast.textContent = message;
  toast.classList.add('show');
  clearTimeout(showToast.timer);
  showToast.timer = setTimeout(() => toast.classList.remove('show'), 3000);
}

function closeOnBackdrop(dialog) {
  dialog.addEventListener('click', event => { if (event.target === dialog) dialog.close(); });
}

$('#sortSelect').addEventListener('change', event => { state.sort = event.target.value; loadShops(); });
$('#studentOnly').addEventListener('change', event => { state.studentOnly = event.target.checked; loadShops(); });
$('#searchInput').addEventListener('input', event => { state.query = event.target.value.trim(); renderShops(); });
$('#categoryTabs').addEventListener('click', event => {
  const button = event.target.closest('button');
  if (!button) return;
  state.category = button.dataset.category;
  document.querySelectorAll('#categoryTabs button').forEach(item => item.classList.toggle('active', item === button));
  renderShops();
});
$('#randomButton').addEventListener('click', () => {
  const shops = filteredShops();
  if (!shops.length) return showToast('当前没有可推荐的店铺');
  openShop(shops[Math.floor(Math.random() * shops.length)].id);
});
$('#userButton').addEventListener('click', () => { renderAccount(); $('#accountDialog').showModal(); });
$('#sendCodeButton').addEventListener('click', async () => {
  const phone = $('#phoneInput').value.trim();
  if (!/^1\d{10}$/.test(phone)) return showToast('请输入正确的 11 位手机号');
  const button = $('#sendCodeButton');
  button.disabled = true;
  try {
    const developmentCode = await api(`/user/code?phone=${encodeURIComponent(phone)}`, { method: 'POST' });
    if (developmentCode) {
      $('#codeInput').value = developmentCode;
      $('#codeTip').textContent = `本地开发验证码：${developmentCode}（已自动填写）`;
    } else {
      $('#codeTip').textContent = '验证码已发送，请查看短信。';
    }
    let seconds = 60;
    button.textContent = `${seconds}s 后重发`;
    const timer = setInterval(() => {
      seconds -= 1;
      button.textContent = `${seconds}s 后重发`;
      if (seconds <= 0) { clearInterval(timer); button.disabled = false; button.textContent = '获取验证码'; }
    }, 1000);
  } catch (error) {
    button.disabled = false;
    showToast(error.message);
  }
});
$('#loginForm').addEventListener('submit', async event => {
  event.preventDefault();
  const phone = $('#phoneInput').value.trim();
  const code = $('#codeInput').value.trim();
  if (!/^1\d{10}$/.test(phone) || !/^\d{6}$/.test(code)) return showToast('请填写正确的手机号和验证码');
  const submit = $('#loginSubmit');
  submit.disabled = true;
  try {
    const token = await api('/user/login', { method: 'POST', body: { phone, code } });
    localStorage.setItem(tokenKey, token);
    await restoreSession();
    $('#accountDialog').close();
    showToast(`欢迎回来，${state.user?.nickName || '鼠鼠同学'}`);
    renderSeckill();
  } catch (error) {
    showToast(error.message);
  } finally {
    submit.disabled = false;
  }
});
$('#logoutButton').addEventListener('click', async () => {
  try { await api('/user/logout', { method: 'POST' }); } catch (error) { /* 本地状态仍需清理 */ }
  localStorage.removeItem(tokenKey);
  state.user = null;
  renderAccount();
  renderSeckill();
  $('#accountDialog').close();
  showToast('已退出登录');
});
$('#ordersButton').addEventListener('click', () => loadOrders(true));
$('#seckillGrid').addEventListener('click', event => {
  const button = event.target.closest('[data-voucher-id]');
  if (button && !button.disabled) claimVoucher(button.dataset.voucherId);
});
$('#dialogContent').addEventListener('click', event => {
  const button = event.target.closest('[data-voucher-id]');
  if (button && !button.disabled) claimVoucher(button.dataset.voucherId);
});
$('#orderList').addEventListener('click', event => {
  const button = event.target.closest('[data-order-action]');
  if (button) changeOrder(button.dataset.orderId, button.dataset.orderAction);
});
$('#dialogClose').addEventListener('click', () => $('#shopDialog').close());
$('#accountClose').addEventListener('click', () => $('#accountDialog').close());
$('#ordersClose').addEventListener('click', () => $('#ordersDialog').close());
closeOnBackdrop($('#shopDialog'));
closeOnBackdrop($('#accountDialog'));
closeOnBackdrop($('#ordersDialog'));
setInterval(updateCountdown, 1000);
bootstrap();
