/* 2FA Demo 前端：登录（可选 2FA 二步）→ 个人页（2FA 设置） */
const $ = (id) => document.getElementById(id);

async function api(path, options = {}) {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  return res.json();   // 统一返回 {code, message, data}
}

function show(view) {
  $('view-login').classList.toggle('hidden', view !== 'login');
  $('view-profile').classList.toggle('hidden', view !== 'profile');
}

function err(id, msg) { $(id).textContent = msg || ''; }

/* ---------- 登录 ---------- */
let challengeId = null;

$('login-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  err('login-error', '');
  const form = new FormData(e.target);
  const r = await api('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username: form.get('username'), password: form.get('password') }),
  });
  if (r.code !== 200) { err('login-error', r.message); return; }

  if (r.data.requires2fa) {
    challengeId = r.data.challengeId;
    $('login-2fa-type').textContent = r.data.type;
    $('step-2fa').classList.remove('hidden');
    $('verify-code').focus();
  } else {
    await enterProfile();
  }
});

$('verify-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  err('login-error', '');
  const r = await api('/api/auth/login/2fa', {
    method: 'POST',
    body: JSON.stringify({ challengeId, code: $('verify-code').value }),
  });
  if (r.code !== 200) { err('login-error', r.message); return; }
  await enterProfile();
});

/* ---------- 个人页 ---------- */
async function enterProfile() {
  const r = await api('/api/auth/me');
  if (r.code !== 200) { show('login'); return; }
  renderProfile(r.data);
  show('profile');
}

function renderProfile(user) {
  $('profile-name').textContent = user.displayName;
  $('profile-name').insertAdjacentText('beforeend', '');
  $('profile-username').textContent = '@' + user.username;
  const tf = user.twoFactor;
  const enabled = tf.enabled;
  $('2fa-status').textContent = enabled ? `已启用（${tf.type}）` : '未启用';
  $('2fa-setup').classList.toggle('hidden', enabled);
  $('2fa-enabled-info').classList.toggle('hidden', !enabled);
  if (enabled) {
    $('2fa-detail').textContent = tf.type === 'HOTP'
      ? `类型 HOTP ｜ 服务器 counter = ${tf.counter}`
      : `类型 TOTP ｜ 周期 ${tf.period} 秒`;
    $('hotp-hint').style.display = tf.type === 'HOTP' ? '' : 'none';
  }
}

$('btn-logout').addEventListener('click', async () => {
  await api('/api/auth/logout', { method: 'POST', body: '{}' });
  $('step-2fa').classList.add('hidden');
  $('login-form').reset();
  show('login');
});

/* ---------- 2FA 设置 ---------- */
$('btn-gen-qr').addEventListener('click', async () => {
  err('profile-error', '');
  const type = document.querySelector('input[name="2fa-type"]:checked').value;
  const r = await api('/api/2fa/setup', { method: 'POST', body: JSON.stringify({ type }) });
  if (r.code !== 200) { err('profile-error', r.message); return; }
  renderSetupResult(r.data.otpauthUri);
});

function renderSetupResult(uri) {
  $('qr-img').src = '/api/2fa/qr.png?uri=' + encodeURIComponent(uri);
  $('otpauth-uri').value = uri;
  $('setup-result').classList.remove('hidden');
}

// 切换类型时自动按新类型重新生成，避免展示上一个类型的旧二维码/URI
document.querySelectorAll('input[name="2fa-type"]').forEach((radio) => {
  radio.addEventListener('change', async () => {
    if ($('setup-result').classList.contains('hidden')) return;
    err('profile-error', '');
    const r = await api('/api/2fa/setup', {
      method: 'POST',
      body: JSON.stringify({ type: radio.value }),
    });
    if (r.code !== 200) { err('profile-error', r.message); return; }
    renderSetupResult(r.data.otpauthUri);
  });
});

$('btn-copy-uri').addEventListener('click', async () => {
  const text = $('otpauth-uri').value;
  try {
    await navigator.clipboard.writeText(text);
  } catch {
    $('otpauth-uri').select();
    document.execCommand('copy');
  }
  const btn = $('btn-copy-uri');
  btn.textContent = '已复制';
  setTimeout(() => (btn.textContent = '复制 URI'), 1500);
});

$('btn-enable').addEventListener('click', async () => {
  err('profile-error', '');
  const r = await api('/api/2fa/enable', {
    method: 'POST',
    body: JSON.stringify({ code: $('enable-code').value }),
  });
  if (r.code !== 200) { err('profile-error', r.message); return; }
  $('setup-result').classList.add('hidden');
  $('enable-code').value = '';
  await enterProfile();
});

$('btn-disable').addEventListener('click', async () => {
  if (!confirm('确定禁用 2FA？')) return;
  err('profile-error', '');
  const r = await api('/api/2fa/disable', { method: 'POST', body: '{}' });
  if (r.code !== 200) { err('profile-error', r.message); return; }
  await enterProfile();
});

/* ---------- 启动：检查会话 ---------- */
api('/api/auth/me').then((r) => {
  if (r.code === 200) { renderProfile(r.data); show('profile'); }
  else { show('login'); }
});
