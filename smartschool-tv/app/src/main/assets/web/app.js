/* Smart School Web Remote - vanilla JS SPA */
const $ = s => document.querySelector(s);
const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const rp = n => 'Rp ' + Number(n || 0).toLocaleString('id-ID');
let ME = null, CLASSES = [];

async function api(method, url, body) {
  const r = await fetch('/api' + url, { method, headers: body ? { 'Content-Type': 'application/json' } : {}, body: body ? JSON.stringify(body) : undefined, credentials: 'same-origin' });
  const data = await r.json().catch(() => ({}));
  if (r.status === 401 && url !== '/auth/login') { showLogin(); throw new Error(data.error || 'Login ulang'); }
  if (!r.ok) throw new Error(data.error || 'Gagal');
  return data;
}
function toast(m) { const t = $('#toast'); t.textContent = m; t.classList.remove('hidden'); setTimeout(() => t.classList.add('hidden'), 2200); }

/* ---------- auth ---------- */
function showLogin() { $('#shell').classList.add('hidden'); $('#login').classList.remove('hidden'); }
async function doLogin(e) {
  e.preventDefault(); $('#lerr').textContent = '';
  try { const r = await api('POST', '/auth/login', { username: $('#lu').value, password: $('#lp').value }); ME = r.user; boot(); }
  catch (er) { $('#lerr').textContent = er.message; }
}
async function logout() { await api('POST', '/auth/logout').catch(() => {}); ME = null; showLogin(); }

/* ---------- routing ---------- */
const PAGES = {
  dashboard: { title: 'Dashboard', icon: '▦', render: pageDashboard },
  kiosk: { title: 'Kiosk Scan', icon: '📷', render: pageKiosk },
  attendances: { title: 'Rekap Absen', icon: '📋', render: pageAttendances },
  leaves: { title: 'Izin / Sakit', icon: '📝', render: pageLeaves },
  idcard: { title: 'Kartu Pelajar', icon: '🪪', render: pageIdCard },
  finance: { title: 'Keuangan SPP', icon: '💳', render: pageFinance },
  savings: { title: 'Tabungan Siswa', icon: '💰', render: pageSavings },
  queue: { title: "Antrian Qur'an", icon: '📿', render: pageQueue },
  students: { title: 'Siswa', icon: '👥', render: pageStudents },
  classes: { title: 'Kelas', icon: '🏫', render: pageClasses },
  teachers: { title: 'Guru', icon: '👤', render: pageTeachers, admin: true },
  whatsapp: { title: 'WhatsApp', icon: '💬', render: pageWhatsApp, admin: true },
  settings: { title: 'Pengaturan', icon: '⚙', render: pageSettings, admin: true },
};
function boot() {
  $('#login').classList.add('hidden'); $('#shell').classList.remove('hidden');
  $('#who').textContent = `${ME.name} (${ME.role})`;
  $('#nav').innerHTML = Object.entries(PAGES).filter(([, p]) => !p.admin || ME.role === 'admin')
    .map(([k, p]) => `<a class="navlink" data-p="${k}" href="#${k}">${p.icon} ${p.title}</a>`).join('');
  route();
}
async function route() {
  if (!ME) return;
  let key = location.hash.slice(1) || 'dashboard';
  if (!PAGES[key] || (PAGES[key].admin && ME.role !== 'admin')) key = 'dashboard';
  document.querySelectorAll('.navlink').forEach(a => a.classList.toggle('active', a.dataset.p === key));
  document.body.classList.remove('open');
  $('#ptitle').textContent = PAGES[key].title;
  $('#page').innerHTML = '<p class="muted">Memuat...</p>';
  try { CLASSES = await api('GET', '/classes'); await PAGES[key].render($('#page')); }
  catch (e) { $('#page').innerHTML = `<p class="err">${esc(e.message)}</p>`; }
}
window.addEventListener('hashchange', route);

/* ---------- modal ---------- */
function openModal(title, html, onSave) {
  $('#mtitle').textContent = title; $('#mbody').innerHTML = html; $('#merr').textContent = '';
  $('#modal').classList.remove('hidden');
  $('#msave').onclick = async () => { try { await onSave(); closeModal(); route(); toast('Tersimpan'); } catch (e) { $('#merr').textContent = e.message; } };
}
function closeModal() { $('#modal').classList.add('hidden'); }
const val = id => document.getElementById(id).value;
const classOpts = (sel, blank = '-') => `<option value="">${blank}</option>` + CLASSES.map(c => `<option value="${c.id}" ${c.id == sel ? 'selected' : ''}>${esc(c.name)}</option>`).join('');
async function del(url, what) { if (!confirm(`Hapus ${what}?`)) return; try { await api('DELETE', url); toast('Dihapus'); route(); } catch (e) { alert(e.message); } }

/* ---------- pages ---------- */
async function pageDashboard(el) {
  const s = await api('GET', '/dashboard/stats');
  const t = (l, v, c = '#fff') => `<div class="card stat"><div class="muted small">${l}</div><div class="v" style="color:${c}">${v}</div></div>`;
  el.innerHTML = `<h2>Kehadiran Hari Ini <span class="muted small">${s.date}</span></h2>
  <section class="grid">${t('Hadir', s.hadir, '#4ade80')}${t('Terlambat', s.terlambat, '#fbbf24')}${t('Izin', s.izin, '#38bdf8')}${t('Sakit', s.sakit, '#a78bfa')}${t('Alpa', s.alpa, '#f87171')}${t('Belum Scan', s.belumScan, '#94a3b8')}</section>
  <h2>Ringkasan</h2>
  <section class="grid">${t('Siswa Aktif', s.totalStudents)}${t('Kelas', s.totalClasses)}${t('Guru', s.totalTeachers)}${t('Izin Menunggu', s.pendingLeaves, '#fbbf24')}${t('Tagihan Belum Lunas', s.unpaidBills, '#f87171')}${t('Total Tabungan', rp(s.totalSavings), '#14b8a6')}</section>`;
}

async function pageKiosk(el) {
  el.innerHTML = `<h2>Kiosk Scanner Web Remote</h2>
  <div class="card" style="margin-bottom:16px">
    <p class="muted">Gunakan scanner barcode/RFID USB, atau ketikkan NIS/token QR siswa untuk simulasi absen.</p>
    <div class="row">
      <input class="grow" id="kcode" placeholder="Scan kartu RFID atau ketikkan NIS/QR..." autofocus>
      <button class="btn primary" id="kbtn">Proses Absen</button>
    </div>
    <div id="kresult" style="margin-top:14px"></div>
  </div>
  <h3>Riwayat Scan Terkini Hari Ini</h3>
  <div id="krecent" class="tbl"></div>`;

  async function loadRecent() {
    const list = await api('GET', '/attendances/recent');
    $('#krecent').innerHTML = `<table><tr><th>Waktu</th><th>NIS</th><th>Nama</th><th>Kelas</th><th>Status</th><th>Keterangan</th></tr>
    ${list.map(a => `<tr><td>${esc(a.timeIn || '-')}</td><td>${esc(a.nis)}</td><td>${esc(a.name)}</td><td>${esc(a.className || '-')}</td>
    <td><span class="badge" style="color:${a.status==='HADIR'?'#4ade80':a.status==='TERLAMBAT'?'#fbbf24':'#f87171'}">${a.status}</span></td>
    <td class="muted">${esc(a.notes || '-')}</td></tr>`).join('')}</table>`;
  }
  loadRecent();

  async function scan() {
    const code = $('#kcode').value.trim();
    if (!code) return;
    try {
      const res = await api('POST', '/attendance/scan', { code });
      $('#kcode').value = '';
      const color = !res.success ? '#f87171' : res.isDuplicate ? '#fbbf24' : res.attendance?.status==='TERLAMBAT' ? '#eab308' : '#4ade80';
      $('#kresult').innerHTML = `<div class="card" style="border:2px solid ${color};background:${color}15">
        <h3 style="color:${color};margin:0">${esc(res.message)}</h3>
        ${res.student ? `<p style="margin:4px 0 0"><b>${esc(res.student.name)}</b> (${esc(res.student.nis)}) - Kelas ${esc(res.className || '-')}</p>` : ''}
      </div>`;
      loadRecent();
    } catch (e) {
      $('#kresult').innerHTML = `<p class="err">${esc(e.message)}</p>`;
    }
  }

  $('#kbtn').onclick = scan;
  $('#kcode').onkeydown = e => { if (e.key === 'Enter') scan(); };
}

let attDate = '', attClass = '', attStatus = '', attQ = '';
async function pageAttendances(el) {
  if (!attDate) attDate = new Date().toISOString().slice(0, 10);
  const rows = await api('GET', `/attendances?date=${attDate}&classId=${attClass}&status=${attStatus}&q=${encodeURIComponent(attQ)}`);
  el.innerHTML = `<div class="row" style="margin-bottom:12px">
    <input type="date" id="atd" value="${attDate}" style="width:auto">
    <select id="atc" style="width:auto">${classOpts(attClass, 'Semua kelas')}</select>
    <select id="ats" style="width:auto">
      <option value="">Semua status</option>
      <option value="HADIR" ${attStatus==='HADIR'?'selected':''}>HADIR</option>
      <option value="TERLAMBAT" ${attStatus==='TERLAMBAT'?'selected':''}>TERLAMBAT</option>
      <option value="IZIN" ${attStatus==='IZIN'?'selected':''}>IZIN</option>
      <option value="SAKIT" ${attStatus==='SAKIT'?'selected':''}>SAKIT</option>
      <option value="ALPA" ${attStatus==='ALPA'?'selected':''}>ALPA</option>
    </select>
    <input class="grow" id="atq" placeholder="Cari nama/NIS" value="${esc(attQ)}">
    <button class="btn primary" onclick="manualAttendance()">+ Manual</button>
    <a class="btn" href="/api/attendances/export/csv?date=${attDate}&classId=${attClass}" target="_blank">Export CSV</a>
  </div>
  <div class="tbl"><table><tr><th>NIS</th><th>Nama</th><th>Kelas</th><th>Status</th><th>Jam Masuk</th><th>Terlambat</th><th>Catatan</th><th>Dicatat</th><th></th></tr>
  ${rows.map(a => `<tr><td>${esc(a.nis)}</td><td>${esc(a.name)}</td><td>${esc(a.className || '-')}</td>
    <td><span class="badge" style="color:${a.status==='HADIR'?'#4ade80':a.status==='TERLAMBAT'?'#fbbf24':a.status==='IZIN'?'#38bdf8':a.status==='SAKIT'?'#a78bfa':'#f87171'}">${a.status}</span></td>
    <td>${esc(a.timeIn || '-')}</td><td>${a.lateMinutes > 0 ? a.lateMinutes + ' mnt' : '-'}</td>
    <td class="muted">${esc(a.notes || '-')}</td><td class="small muted">${esc(a.createdBy)}</td>
    <td>${ME.role === 'admin' ? `<button class="btn sm danger" onclick="del('/attendances/${a.id}','absen ini')">Hapus</button>` : ''}</td></tr>`).join('')}
  </table></div><p class="muted small">${rows.length} catatan kehadiran</p>`;

  $('#atd').onchange = e => { attDate = e.target.value; route(); };
  $('#atc').onchange = e => { attClass = e.target.value; route(); };
  $('#ats').onchange = e => { attStatus = e.target.value; route(); };
  let tm; $('#atq').oninput = e => { clearTimeout(tm); tm = setTimeout(() => { attQ = e.target.value; route(); }, 400); };
}

async function manualAttendance() {
  const students = await api('GET', '/students');
  openModal('Input Absensi Manual', `<label>Pilih Siswa<select id="m_sid">${students.map(s => `<option value="${s.id}">${esc(s.nis)} - ${esc(s.name)} (${esc(s.className || '-')})</option>`).join('')}</select></label>
  <div class="two"><label>Tanggal<input type="date" id="m_dt" value="${attDate || new Date().toISOString().slice(0, 10)}"></label>
  <label>Status<select id="m_st"><option>HADIR</option><option>TERLAMBAT</option><option>IZIN</option><option>SAKIT</option><option>ALPA</option></select></label></div>
  <label>Catatan<input id="m_nt" placeholder="Keterangan tambahan"></label>`,
  () => api('POST', '/attendances/manual', { studentId: +val('m_sid'), date: val('m_dt'), status: val('m_st'), notes: val('m_nt') }));
}

async function pageLeaves(el) {
  const rows = await api('GET', '/leaves');
  el.innerHTML = `<div class="row end" style="margin-bottom:12px"><button class="btn primary" onclick="addLeave()">+ Ajukan Izin</button></div>
  <div class="tbl"><table><tr><th>Siswa</th><th>Kelas</th><th>Tipe</th><th>Periode</th><th>Alasan</th><th>Status</th><th>Aksi</th></tr>
  ${rows.map(l => `<tr><td>${esc(l.nis)} - <b>${esc(l.studentName)}</b></td><td>${esc(l.className || '-')}</td>
    <td><span class="badge" style="color:${l.type==='SAKIT'?'#a78bfa':'#38bdf8'}">${l.type}</span></td>
    <td>${esc(l.startDate)} s/d ${esc(l.endDate)}</td><td>${esc(l.reason || '-')}</td>
    <td><span class="badge" style="color:${l.status==='APPROVED'?'#4ade80':l.status==='REJECTED'?'#f87171':'#fbbf24'}">${l.status}</span></td>
    <td class="row">${l.status === 'PENDING' ? `
      <button class="btn sm primary" onclick="approveLeave(${l.id})">Setujui</button>
      <button class="btn sm danger" onclick="rejectLeave(${l.id})">Tolak</button>` : `<span class="muted small">${l.rejectionNote || 'Selesai'}</span>`}
    </td></tr>`).join('')}</table></div>`;
}

async function approveLeave(id) {
  try { await api('POST', `/leaves/${id}/approve`); toast('Izin disetujui'); route(); } catch (e) { alert(e.message); }
}
async function rejectLeave(id) {
  const note = prompt('Alasan penolakan:');
  if (note === null) return;
  try { await api('POST', `/leaves/${id}/reject`, { note }); toast('Izin ditolak'); route(); } catch (e) { alert(e.message); }
}
async function addLeave() {
  const students = await api('GET', '/students');
  const today = new Date().toISOString().slice(0, 10);
  openModal('Pengajuan Izin / Sakit', `<label>Pilih Siswa<select id="l_sid">${students.map(s => `<option value="${s.id}">${esc(s.nis)} - ${esc(s.name)} (${esc(s.className || '-')})</option>`).join('')}</select></label>
  <div class="two"><label>Tipe<select id="l_tp"><option value="IZIN">IZIN</option><option value="SAKIT">SAKIT</option></select></label>
  <label>Tanggal Mulai<input type="date" id="l_sd" value="${today}"></label></div>
  <label>Tanggal Selesai<input type="date" id="l_ed" value="${today}"></label>
  <label>Alasan<textarea id="l_rs" rows="3" placeholder="Alasan izin atau sakit..."></textarea></label>`,
  () => api('POST', '/leaves', { studentId: +val('l_sid'), type: val('l_tp'), startDate: val('l_sd'), endDate: val('l_ed'), reason: val('l_rs') }));
}

let idCardClass = '';
async function pageIdCard(el) {
  const students = await api('GET', `/students?classId=${idCardClass}`);
  el.innerHTML = `<h2>Cetak Kartu Pelajar (CR80)</h2>
  <div class="row" style="margin-bottom:16px">
    <select id="idc_c" style="width:auto">${classOpts(idCardClass, 'Semua Kelas')}</select>
    <button class="btn primary" onclick="window.print()">🖨 Cetak Halaman (Print/PDF)</button>
  </div>
  <div id="cardGrid" style="display:grid;grid-template-columns:repeat(auto-fill,minmax(340px,1fr));gap:16px">
    ${students.map(s => `
      <div class="card" style="background:linear-gradient(135deg,#0f766e,#042f2e);border:2px solid #2dd4bf;border-radius:14px;padding:14px;color:#fff;display:flex;flex-direction:column;justify-content:space-between;height:210px">
        <div style="display:flex;justify-content:space-between;align-items:center;border-bottom:1px solid rgba(255,255,255,0.2);padding-bottom:6px">
          <div><b style="font-size:13px;letter-spacing:1px">KARTU PELAJAR</b><div class="small muted" style="color:#ccfbf1">${esc(s.className||'-')}</div></div>
          <span style="font-size:18px">🎓</span>
        </div>
        <div style="display:flex;gap:12px;align-items:center;margin:10px 0">
          <div style="width:58px;height:58px;background:#111a2e;border:1.5px solid #2dd4bf;border-radius:8px;display:flex;align-items:center;justify-content:center;font-size:24px;font-weight:bold">${esc(s.name.slice(0,1))}</div>
          <div style="flex:1">
            <h4 style="margin:0 0 4px;font-size:16px">${esc(s.name)}</h4>
            <div class="small">NIS: <b>${esc(s.nis)}</b></div>
            <div class="small" style="color:#99f6e4">RFID: ${esc(s.rfidUid||'-')}</div>
          </div>
          <img src="https://api.qrserver.com/v1/create-qr-code/?size=120x120&data=${encodeURIComponent(s.qrToken)}" style="width:64px;height:64px;background:#fff;padding:2px;border-radius:6px" alt="QR" onerror="this.style.display='none'">
        </div>
        <div style="display:flex;justify-content:space-between;align-items:center;border-top:1px solid rgba(255,255,255,0.2);padding-top:6px;font-size:11px">
          <span class="muted" style="color:#ccfbf1">Token: ${esc(s.qrToken)}</span>
          <button class="btn sm" style="background:#14b8a6;color:#042f2e;font-weight:bold" onclick="sendCardWa(${s.id})">WA Ortu</button>
        </div>
      </div>
    `).join('')}
  </div>`;

  $('#idc_c').onchange = e => { idCardClass = e.target.value; route(); };
}

async function sendCardWa(id) {
  try {
    const res = await api('POST', `/students/${id}/send-idcard-wa`);
    if (res.ok) toast('Kartu pelajar dikirim ke WhatsApp');
    else alert('Gagal: nomor WhatsApp belum terdaftar');
  } catch (e) { alert(e.message); }
}

let finStatus = '', finClass = '', finQ = '';
async function pageFinance(el) {
  const bills = await api('GET', `/finance/bills?status=${finStatus}&classId=${finClass}&q=${encodeURIComponent(finQ)}`);
  el.innerHTML = `<h2>Keuangan & Tagihan SPP</h2>
  <div class="row" style="margin-bottom:14px">
    <select id="fb_st" style="width:auto">
      <option value="">Semua Status</option>
      <option value="UNPAID" ${finStatus==='UNPAID'?'selected':''}>BELUM LUNAS</option>
      <option value="PAID" ${finStatus==='PAID'?'selected':''}>LUNAS</option>
    </select>
    <select id="fb_cl" style="width:auto">${classOpts(finClass, 'Semua Kelas')}</select>
    <input class="grow" id="fb_q" placeholder="Cari siswa atau kode tagihan..." value="${esc(finQ)}">
    ${ME.role === 'admin' ? '<button class="btn primary" id="fbtn_gen">+ Generate SPP Bulan Ini</button>' : ''}
  </div>
  <div class="tbl"><table><tr><th>Kode</th><th>Siswa</th><th>Kelas</th><th>Tagihan</th><th>Nominal</th><th>Status</th><th>Aksi</th></tr>
  ${bills.map(b => `<tr><td><b>${esc(b.billCode)}</b></td><td>${esc(b.studentName)}</td><td>${esc(b.className||'-')}</td><td>${esc(b.title)}</td>
    <td><b style="color:#14b8a6">${rp(b.totalAmount)}</b></td>
    <td><span class="badge" style="color:${b.status==='PAID'?'#4ade80':'#f87171'}">${b.status}</span></td>
    <td class="row">
      ${b.status !== 'PAID' ? `
        <button class="btn sm primary" onclick="payBill(${b.id})">Bayar Tunai</button>
        <button class="btn sm" onclick="showQris(${b.id},'${esc(b.studentName)}','${esc(b.title)}',${b.totalAmount})">QRIS</button>
      ` : '<span class="muted small">Lunas</span>'}
    </td></tr>`).join('')}
  </table></div><p class="muted small">${bills.length} tagihan ditampilkan</p>`;

  $('#fb_st').onchange = e => { finStatus = e.target.value; route(); };
  $('#fb_cl').onchange = e => { finClass = e.target.value; route(); };
  let tm; $('#fb_q').oninput = e => { clearTimeout(tm); tm = setTimeout(() => { finQ = e.target.value; route(); }, 400); };

  if ($('#fbtn_gen')) {
    $('#fbtn_gen').onclick = async () => {
      const cur = new Date().toISOString().slice(0, 7);
      if (!confirm(`Generate tagihan SPP untuk semua siswa aktif periode ${cur}?`)) return;
      try {
        const res = await api('POST', '/finance/bills/generate-monthly', { period: cur, categoryId: 1 });
        alert(`Berhasil membuat ${res.count} tagihan baru.`);
        route();
      } catch (e) { alert(e.message); }
    };
  }
}

async function payBill(id) {
  if (!confirm('Tandai tagihan ini sebagai Lunas (Pembayaran Tunai)?')) return;
  try {
    await api('POST', `/finance/bills/${id}/pay-cash`);
    toast('Pembayaran berhasil dicatat Lunas');
    route();
  } catch (e) { alert(e.message); }
}

async function showQris(id, name, title, amount) {
  try {
    const res = await api('GET', `/finance/bills/${id}/qris`);
    const qris = res.qris;
    openModal('QRIS Dinamis Tagihan', `<div style="text-align:center">
      <h3>${esc(name)}</h3><p class="muted">${esc(title)}</p>
      <h2 style="color:#14b8a6;margin:8px 0">${rp(amount)}</h2>
      <img src="https://api.qrserver.com/v1/create-qr-code/?size=260x260&data=${encodeURIComponent(qris)}" style="width:220px;height:220px;background:#fff;padding:8px;border-radius:10px" alt="QRIS">
      <p class="small muted" style="margin-top:8px">Scan via BCA Mobile, GoPay, OVO, Dana, ShopeePay, atau m-Banking apapun</p>
    </div>`, () => {});
  } catch (e) { alert(e.message); }
}

async function pageSavings(el) {
  let savClass = '', savStudentId = '';
  const students = await api('GET', `/students?classId=${savClass}`);
  const history = await api('GET', `/savings/history?classId=${savClass}&studentId=${savStudentId}`);

  el.innerHTML = `<div class="card" style="margin-bottom:16px">
    <div class="row wrap" style="gap:12px;align-items:flex-end">
      <div style="flex:1;min-width:200px">
        <label>Filter Kelas<select id="sav_c">${classOpts(savClass, 'Semua Kelas')}</select></label>
      </div>
      <div style="flex:1;min-width:240px">
        <label>Pilih Siswa (Untuk Transaksi)<select id="sav_s">
          <option value="">-- Pilih Siswa --</option>
          ${students.map(s => `<option value="${s.id}" ${savStudentId == s.id ? 'selected' : ''}>${esc(s.name)} (${esc(s.className || '-')}) - Saldo: ${rp(s.savingsBalance)}</option>`).join('')}
        </select></label>
      </div>
      <div class="row" style="gap:8px">
        <button id="btn_sav_in" class="btn primary" ${!savStudentId ? 'disabled' : ''}>+ Setor Saldo</button>
        <button id="btn_sav_out" class="btn" style="border-color:#f87171;color:#f87171" ${!savStudentId ? 'disabled' : ''}>- Tarik Saldo</button>
      </div>
    </div>
  </div>

  <div class="card">
    <div class="row between" style="margin-bottom:12px">
      <h3>Riwayat Mutasi Tabungan Terkini</h3>
      <span class="muted small">${history.length} transaksi</span>
    </div>
    <div class="tbl"><table>
      <tr><th>Tanggal</th><th>Siswa</th><th>Kelas</th><th>Tipe</th><th>Nominal</th><th>Saldo Akhir</th><th>Catatan</th><th>Petugas</th></tr>
      ${history.length === 0 ? '<tr><td colspan="8" class="muted center">Belum ada mutasi tabungan</td></tr>' : ''}
      ${history.map(h => {
        const isSetor = h.type === 'SETOR';
        return `<tr>
          <td>${esc(h.transactionDate)}</td>
          <td><b>${esc(h.studentName)}</b></td>
          <td>${esc(h.className || '-')}</td>
          <td><span class="badge" style="background:${isSetor ? '#065f46' : '#7f1d1d'};color:${isSetor ? '#6ee7b7' : '#fca5a5'}">${esc(h.type)}</span></td>
          <td style="font-weight:600;color:${isSetor ? '#10b981' : '#f87171'}">${isSetor ? '+' : '-'} ${rp(h.amount)}</td>
          <td><b>${rp(h.balanceAfter)}</b></td>
          <td class="muted small">${esc(h.note || '-')}</td>
          <td class="muted small">${esc(h.teacherName || 'Admin')}</td>
        </tr>`;
      }).join('')}
    </table></div>
  </div>`;

  $('#sav_c').onchange = e => { savClass = e.target.value; savStudentId = ''; renderSav(); };
  $('#sav_s').onchange = e => {
    savStudentId = e.target.value;
    $('#btn_sav_in').disabled = !savStudentId;
    $('#btn_sav_out').disabled = !savStudentId;
    renderSav();
  };

  async function renderSav() {
    const stus = await api('GET', `/students?classId=${savClass}`);
    const hist = await api('GET', `/savings/history?classId=${savClass}&studentId=${savStudentId}`);
    $('#sav_s').innerHTML = `<option value="">-- Pilih Siswa --</option>` +
      stus.map(s => `<option value="${s.id}" ${savStudentId == s.id ? 'selected' : ''}>${esc(s.name)} (${esc(s.className || '-')}) - Saldo: ${rp(s.savingsBalance)}</option>`).join('');
    $('#btn_sav_in').disabled = !savStudentId;
    $('#btn_sav_out').disabled = !savStudentId;

    const tbody = $('#page table');
    if (tbody) {
      tbody.innerHTML = `<tr><th>Tanggal</th><th>Siswa</th><th>Kelas</th><th>Tipe</th><th>Nominal</th><th>Saldo Akhir</th><th>Catatan</th><th>Petugas</th></tr>` +
      (hist.length === 0 ? '<tr><td colspan="8" class="muted center">Belum ada mutasi tabungan</td></tr>' : '') +
      hist.map(h => {
        const isSetor = h.type === 'SETOR';
        return `<tr>
          <td>${esc(h.transactionDate)}</td>
          <td><b>${esc(h.studentName)}</b></td>
          <td>${esc(h.className || '-')}</td>
          <td><span class="badge" style="background:${isSetor ? '#065f46' : '#7f1d1d'};color:${isSetor ? '#6ee7b7' : '#fca5a5'}">${esc(h.type)}</span></td>
          <td style="font-weight:600;color:${isSetor ? '#10b981' : '#f87171'}">${isSetor ? '+' : '-'} ${rp(h.amount)}</td>
          <td><b>${rp(h.balanceAfter)}</b></td>
          <td class="muted small">${esc(h.note || '-')}</td>
          <td class="muted small">${esc(h.teacherName || 'Admin')}</td>
        </tr>`;
      }).join('');
    }
  }

  $('#btn_sav_in').onclick = () => {
    if (!savStudentId) return;
    openModal('Setor Tabungan Siswa', `
      <label>Nominal Setor (Rp)<input id="sav_amt" type="number" min="1000" step="1000" placeholder="10000"></label>
      <label>Catatan / Sumber Dana<input id="sav_note" placeholder="Uang saku / Titipan orang tua"></label>
    `, async () => {
      const amount = +val('sav_amt');
      const note = val('sav_note');
      if (amount <= 0) throw new Error('Nominal tidak valid');
      await api('POST', '/savings/deposit', { studentId: +savStudentId, amount, note });
      toast('Setoran berhasil disimpan & notifikasi WhatsApp dikirim ke ortu');
      renderSav();
    });
  };

  $('#btn_sav_out').onclick = () => {
    if (!savStudentId) return;
    openModal('Tarik Saldo Tabungan', `
      <label>Nominal Tarik (Rp)<input id="sav_amt" type="number" min="1000" step="1000" placeholder="10000"></label>
      <label>Keperluan Penarikan<input id="sav_note" placeholder="Beli buku / Uang saku"></label>
    `, async () => {
      const amount = +val('sav_amt');
      const note = val('sav_note');
      if (amount <= 0) throw new Error('Nominal tidak valid');
      await api('POST', '/savings/withdraw', { studentId: +savStudentId, amount, note });
      toast('Penarikan berhasil dicatat & notifikasi WhatsApp dikirim ke ortu');
      renderSav();
    });
  };
}

async function pageQueue(el) {
  let qClass = '', qStudentId = '';
  const students = await api('GET', `/students?classId=${qClass}`);
  const qData = await api('GET', '/queue/today');

  function getStatusBadge(st) {
    const color = st === 'CALLING' ? '#fbbf24' : st === 'SERVING' ? '#38bdf8' : st === 'COMPLETED' ? '#4ade80' : st === 'SKIPPED' ? '#f87171' : '#94a3b8';
    return `<span class="badge" style="border:1px solid ${color};color:${color}">${esc(st)}</span>`;
  }

  el.innerHTML = `
  <div class="row wrap" style="gap:16px;margin-bottom:16px">
    <!-- Panggilan Aktif -->
    <div class="card" style="flex:1;min-width:280px;text-align:center">
      <div class="muted small" style="text-transform:uppercase;letter-spacing:1px;font-weight:600">Nomor Antrian Sedang Dipanggil</div>
      <div id="q_display_num" style="font-size:56px;font-weight:800;color:#14b8a6;margin:12px 0">
        ${qData.calling ? '#' + String(qData.calling.queueNumber).padStart(2, '0') : '--'}
      </div>
      <div id="q_display_name" style="font-size:18px;font-weight:700">
        ${qData.calling ? esc(qData.calling.studentName) : 'Belum Ada Santri'}
      </div>
      <div id="q_display_class" class="muted small" style="margin-bottom:16px">
        ${qData.calling ? esc(qData.calling.className || '-') + ' • NIS ' + esc(qData.calling.nis) : 'Menunggu panggilan meja pengampu'}
      </div>
      <div class="row center" style="gap:8px">
        <button id="btn_q_next" class="btn primary">▶ Panggil Antrian Berikutnya</button>
        ${qData.calling ? `
          <button id="btn_q_recall" class="btn">📢 Panggil Ulang</button>
          <button id="btn_q_done" class="btn" style="border-color:#4ade80;color:#4ade80">✓ Selesai</button>
          <button id="btn_q_skip" class="btn danger">✕ Lewati</button>
        ` : ''}
      </div>
    </div>

    <!-- Meja Registrasi Antrian -->
    <div class="card" style="flex:1.2;min-width:320px">
      <h3>Daftarkan Santri ke Antrian Hari Ini</h3>
      <div class="row wrap" style="gap:8px;margin-bottom:12px;align-items:flex-end">
        <div style="flex:1;min-width:140px">
          <label>Filter Kelas<select id="q_reg_class">${classOpts(qClass, 'Semua Kelas')}</select></label>
        </div>
        <div style="flex:2;min-width:180px">
          <label>Pilih Santri<select id="q_reg_stu">
            <option value="">-- Pilih Santri --</option>
            ${students.map(s => `<option value="${s.id}">${esc(s.name)} (${esc(s.className || '-')})</option>`).join('')}
          </select></label>
        </div>
        <button id="btn_q_reg" class="btn primary" style="height:38px">+ Ambil Nomor</button>
      </div>
      <p class="muted small">Nomor antrian akan berurutan otomatis sesuai tanggal hari ini.</p>
    </div>
  </div>

  <div class="card">
    <div class="row between" style="margin-bottom:12px">
      <h3>Daftar Seluruh Antrian Hari Ini (${qData.list?.length || 0})</h3>
      <button class="btn sm" onclick="route()">🔄 Muat Ulang</button>
    </div>
    <div class="tbl"><table>
      <tr><th>No</th><th>Santri</th><th>Kelas</th><th>NIS</th><th>Status</th><th>Panggilan</th><th>Aksi Meja</th></tr>
      ${(!qData.list || qData.list.length === 0) ? '<tr><td colspan="7" class="muted center">Belum ada antrian terdaftar hari ini</td></tr>' : ''}
      ${(qData.list || []).map(q => `<tr>
        <td><b>#${String(q.queueNumber).padStart(2, '0')}</b></td>
        <td><b>${esc(q.studentName)}</b></td>
        <td>${esc(q.className || '-')}</td>
        <td class="muted">${esc(q.nis)}</td>
        <td>${getStatusBadge(q.status)}</td>
        <td>${q.callCount}x</td>
        <td class="row">
          ${q.status === 'WAITING' || q.status === 'SKIPPED' ? `
            <button class="btn sm" onclick="recallQ(${q.id})">Panggil</button>
          ` : ''}
          ${q.status === 'CALLING' ? `
            <button class="btn sm" style="color:#4ade80" onclick="doneQ(${q.id})">Selesai</button>
            <button class="btn sm danger" onclick="skipQ(${q.id})">Lewati</button>
          ` : ''}
        </td>
      </tr>`).join('')}
    </table></div>
  </div>`;

  $('#q_reg_class').onchange = async e => {
    qClass = e.target.value;
    const stus = await api('GET', `/students?classId=${qClass}`);
    $('#q_reg_stu').innerHTML = '<option value="">-- Pilih Santri --</option>' +
      stus.map(s => `<option value="${s.id}">${esc(s.name)} (${esc(s.className || '-')})</option>`).join('');
  };

  $('#btn_q_reg').onclick = async () => {
    const sid = $('#q_reg_stu').value;
    if (!sid) return alert('Pilih santri terlebih dahulu');
    try {
      const res = await api('POST', '/queue/register', { studentId: +sid });
      toast(`Santri berhasil masuk antrian nomor #${res.queue.queueNumber}`);
      route();
    } catch (e) { alert(e.message); }
  };

  $('#btn_q_next').onclick = async () => {
    try {
      const res = await api('POST', '/queue/call-next');
      if (!res.called) alert('Tidak ada santri yang sedang menunggu antrian.');
      else toast(`Memanggil antrian #${res.called.queueNumber}: ${res.called.studentName}`);
      route();
    } catch (e) { alert(e.message); }
  };

  if ($('#btn_q_recall')) {
    $('#btn_q_recall').onclick = async () => {
      if (!qData.calling) return;
      try {
        await api('POST', `/queue/${qData.calling.id}/recall`);
        toast(`Panggilan ulang untuk #${qData.calling.queueNumber}`);
        route();
      } catch (e) { alert(e.message); }
    };
  }

  if ($('#btn_q_done')) {
    $('#btn_q_done').onclick = async () => {
      if (!qData.calling) return;
      try {
        await api('POST', `/queue/${qData.calling.id}/complete`);
        toast(`Antrian #${qData.calling.queueNumber} selesai`);
        route();
      } catch (e) { alert(e.message); }
    };
  }

  if ($('#btn_q_skip')) {
    $('#btn_q_skip').onclick = async () => {
      if (!qData.calling) return;
      try {
        await api('POST', `/queue/${qData.calling.id}/skip`);
        toast(`Antrian #${qData.calling.queueNumber} dilewati`);
        route();
      } catch (e) { alert(e.message); }
    };
  }
}

async function recallQ(id) {
  try { await api('POST', `/queue/${id}/recall`); route(); } catch (e) { alert(e.message); }
}
async function doneQ(id) {
  try { await api('POST', `/queue/${id}/complete`); route(); } catch (e) { alert(e.message); }
}
async function skipQ(id) {
  try { await api('POST', `/queue/${id}/skip`); route(); } catch (e) { alert(e.message); }
}

let stuQ = '', stuClass = '';
async function pageStudents(el) {
  const rows = await api('GET', `/students?q=${encodeURIComponent(stuQ)}&classId=${stuClass}`);
  el.innerHTML = `<div class="row" style="margin-bottom:12px"><input class="grow" id="sq" placeholder="Cari nama / NIS" value="${esc(stuQ)}">
    <select id="sc" style="width:auto">${classOpts(stuClass, 'Semua kelas')}</select>
    <button class="btn primary" onclick="editStudent()">+ Tambah</button></div>
  <div class="tbl"><table><tr><th>NIS</th><th>Nama</th><th>Kelas</th><th>L/P</th><th>Orang Tua</th><th>No. WA</th><th>Tabungan</th><th></th></tr>
  ${rows.map(s => `<tr><td>${esc(s.nis)}</td><td>${esc(s.name)} ${s.isActive ? '' : '<span class="badge">nonaktif</span>'}</td><td>${esc(s.className || '-')}</td><td>${s.gender}</td>
    <td>${esc(s.parentName)}</td><td>${esc(s.parentPhone)}</td><td>${rp(s.savingsBalance)}</td>
    <td class="row"><button class="btn sm" onclick='editStudent(${JSON.stringify(s)})'>Edit</button>
    ${ME.role === 'admin' ? `<button class="btn sm danger" onclick="del('/students/${s.id}','siswa ini')">Hapus</button>` : ''}</td></tr>`).join('')}
  </table></div><p class="muted small">${rows.length} siswa</p>`;
  let tm; $('#sq').oninput = e => { clearTimeout(tm); tm = setTimeout(() => { stuQ = e.target.value; route(); }, 400); };
  $('#sc').onchange = e => { stuClass = e.target.value; route(); };
}
function editStudent(s = {}) {
  openModal(s.id ? 'Edit Siswa' : 'Tambah Siswa', `<div class="two">
    <label>NIS<input id="f_nis" value="${esc(s.nis)}"></label><label>NISN<input id="f_nisn" value="${esc(s.nisn)}"></label></div>
    <label>Nama Lengkap<input id="f_name" value="${esc(s.name)}"></label>
    <div class="two"><label>Kelas<select id="f_class">${classOpts(s.classId ?? stuClass)}</select></label>
    <label>Jenis Kelamin<select id="f_gender"><option value="L">Laki-laki</option><option value="P" ${s.gender === 'P' ? 'selected' : ''}>Perempuan</option></select></label>
    <label>UID Kartu RFID<input id="f_rfid" value="${esc(s.rfidUid)}"></label><label>Token QR (kosong=otomatis)<input id="f_qr" value="${esc(s.qrToken)}"></label>
    <label>Nama Orang Tua<input id="f_pn" value="${esc(s.parentName)}"></label><label>No. WA Orang Tua<input id="f_pp" value="${esc(s.parentPhone)}"></label></div>
    <label><input type="checkbox" id="f_act" style="width:auto" ${s.isActive === false ? '' : 'checked'}> Aktif</label>`,
    () => api(s.id ? 'PUT' : 'POST', '/students' + (s.id ? '/' + s.id : ''), {
      nis: val('f_nis'), nisn: val('f_nisn'), name: val('f_name'), classId: val('f_class') ? +val('f_class') : null,
      gender: val('f_gender'), rfidUid: val('f_rfid'), qrToken: val('f_qr'), parentName: val('f_pn'), parentPhone: val('f_pp'),
      isActive: document.getElementById('f_act').checked }));
}

async function pageClasses(el) {
  const admin = ME.role === 'admin';
  el.innerHTML = `<div class="row end" style="margin-bottom:12px">${admin ? '<button class="btn primary" onclick="editClass()">+ Tambah Kelas</button>' : ''}</div>
  <div class="tbl"><table><tr><th>Kelas</th><th>Tingkat</th><th>Siswa</th><th>Wali Kelas</th><th></th></tr>
  ${CLASSES.map(c => `<tr><td>${esc(c.name)}</td><td>${esc(c.level)}</td><td>${c.studentCount}</td><td>${esc(c.waliName || '-')}</td>
  <td class="row">${admin ? `<button class="btn sm" onclick='editClass(${JSON.stringify(c)})'>Edit</button><button class="btn sm danger" onclick="del('/classes/${c.id}','kelas ini')">Hapus</button>` : ''}</td></tr>`).join('')}</table></div>`;
}
function editClass(c = {}) {
  openModal(c.id ? 'Edit Kelas' : 'Tambah Kelas', `<label>Nama Kelas<input id="f_cn" value="${esc(c.name)}"></label><label>Tingkat<input id="f_cl" value="${esc(c.level)}"></label>`,
    () => api(c.id ? 'PUT' : 'POST', '/classes' + (c.id ? '/' + c.id : ''), { name: val('f_cn'), level: val('f_cl') }));
}

async function pageTeachers(el) {
  const rows = await api('GET', '/teachers');
  el.innerHTML = `<div class="row end" style="margin-bottom:12px"><button class="btn primary" onclick="editTeacher()">+ Tambah Guru</button></div>
  <div class="tbl"><table><tr><th>Nama</th><th>Username</th><th>Role</th><th>No. WA</th><th>Wali Kelas</th><th></th></tr>
  ${rows.map(u => `<tr><td>${esc(u.name)}</td><td>${esc(u.username)}</td><td><span class="badge">${u.role}</span></td><td>${esc(u.phone)}</td><td>${esc(u.className || '-')}</td>
  <td class="row"><button class="btn sm" onclick='editTeacher(${JSON.stringify(u)})'>Edit</button><button class="btn sm danger" onclick="del('/teachers/${u.id}','akun ini')">Hapus</button></td></tr>`).join('')}</table></div>`;
}
function editTeacher(u = {}) {
  openModal(u.id ? 'Edit Guru' : 'Tambah Guru', `<label>Nama<input id="f_tn" value="${esc(u.name)}"></label>
    <div class="two"><label>Username<input id="f_tu" value="${esc(u.username)}"></label><label>${u.id ? 'Password baru (opsional)' : 'Password'}<input id="f_tp" type="password"></label>
    <label>No. WA<input id="f_tw" value="${esc(u.phone)}"></label>
    <label>Role<select id="f_tr"><option value="guru">Guru</option><option value="admin" ${u.role === 'admin' ? 'selected' : ''}>Admin</option></select></label></div>
    <label>Wali Kelas<select id="f_tc">${classOpts(u.classId)}</select></label>`,
    () => api(u.id ? 'PUT' : 'POST', '/teachers' + (u.id ? '/' + u.id : ''), {
      name: val('f_tn'), username: val('f_tu'), password: val('f_tp') || null, phone: val('f_tw'), role: val('f_tr'), classId: val('f_tc') ? +val('f_tc') : null }));
}

async function pageWhatsApp(el) {
  const settings = await api('GET', '/settings');
  const queue = await api('GET', '/whatsapp/queue');
  el.innerHTML = `<h2>WhatsApp Gateway & Antrian Pesan</h2>
  <div class="card" style="margin-bottom:16px">
    <p><b>Gateway Terpasang:</b> <span class="badge" style="color:#14b8a6">${esc((settings.wa_provider||'none').toUpperCase())}</span></p>
    <div class="row" style="margin:10px 0">
      <input class="grow" id="wtest_phone" placeholder="Nomor HP untuk tes pengiriman (misal: 08123456789)...">
      <button class="btn primary" id="wbtn_test">Kirim Tes</button>
      <button class="btn" id="wbtn_alpa">Broadcast ALPA</button>
      <button class="btn" id="wbtn_recap">Kirim Rekap Wali Kelas</button>
      <button class="btn danger" id="wbtn_retry">Retry Gagal</button>
    </div>
    <div id="wtest_res" style="margin-top:6px"></div>
  </div>
  <h3>Daftar Antrian & Riwayat Pesan Terkini</h3>
  <div class="tbl"><table><tr><th>Tujuan</th><th>Tipe</th><th>Status</th><th>Percobaan</th><th>Isi Pesan</th></tr>
  ${queue.map(q => `<tr><td>${esc(q.phone)}</td><td><span class="badge">${esc(q.type)}</span></td>
    <td><span class="badge" style="color:${q.status==='sent'?'#4ade80':q.status==='sending'?'#38bdf8':q.status==='failed'?'#f87171':'#fbbf24'}">${esc(q.status.toUpperCase())}</span></td>
    <td>${q.attempts}x</td><td class="muted small">${esc(q.message.slice(0, 80))}${q.message.length > 80 ? '...' : ''}</td></tr>`).join('')}
  </table></div><p class="muted small">${queue.length} antrian/riwayat ditampilkan</p>`;

  $('#wbtn_test').onclick = async () => {
    const phone = $('#wtest_phone').value.trim();
    if (!phone) return alert('Masukkan nomor tujuan');
    $('#wtest_res').innerHTML = '<span class="muted">Sedang mengirim pesan tes...</span>';
    try {
      const res = await api('POST', '/whatsapp/test', { phone });
      const color = res.success ? '#4ade80' : '#f87171';
      $('#wtest_res').innerHTML = `<span style="color:${color};font-weight:600">${esc(res.message)}</span>`;
    } catch (e) {
      $('#wtest_res').innerHTML = `<span class="err">${esc(e.message)}</span>`;
    }
  };

  $('#wbtn_alpa').onclick = async () => {
    if (!confirm('Kirim notifikasi ALPA hari ini ke seluruh orang tua siswa yang tidak hadir?')) return;
    try {
      const res = await api('POST', '/whatsapp/broadcast-alpa');
      alert(`Berhasil memasukkan ${res.count} notifikasi ALPA ke dalam antrian.`);
      route();
    } catch (e) { alert(e.message); }
  };

  $('#wbtn_recap').onclick = async () => {
    if (!confirm('Kirim ringkasan rekap kehadiran hari ini ke semua wali kelas?')) return;
    try {
      const res = await api('POST', '/whatsapp/send-recap');
      alert(`Rekap absensi berhasil dikirim ke ${res.count} wali kelas.`);
      route();
    } catch (e) { alert(e.message); }
  };

  $('#wbtn_retry').onclick = async () => {
    try {
      await api('POST', '/whatsapp/retry');
      toast('Pesan yang gagal berhasil dikembalikan ke antrian');
      route();
    } catch (e) { alert(e.message); }
  };
}

async function pageSettings(el) {
  const s = await api('GET', '/settings');
  const f = (k, l, type = 'text') => `<label>${l}<input data-k="${k}" type="${type}" value="${esc(s[k])}"></label>`;
  el.innerHTML = `<form id="sf" class="card">
    <h3>Profil Sekolah</h3><div class="mbox two" style="width:auto;max-height:none">${f('school_name', 'Nama Sekolah')}${f('principal_name', 'Kepala Sekolah')}${f('school_address', 'Alamat')}${f('school_phone', 'Telepon')}</div>
    <h3>Jam Sekolah</h3><div class="mbox two" style="width:auto;max-height:none">${f('jam_masuk', 'Jam Masuk (HH:mm)')}${f('toleransi_menit', 'Toleransi (menit)', 'number')}${f('jam_pulang', 'Jam Pulang')}${f('jam_rekap_guru', 'Jam Rekap ke Wali Kelas')}</div>
    <h3>Notifikasi WhatsApp</h3><div class="mbox two" style="width:auto;max-height:none">
      <label>Gateway<select data-k="wa_provider">${['fonnte', 'wablas', 'none'].map(p => `<option ${s.wa_provider === p ? 'selected' : ''}>${p}</option>`).join('')}</select></label>
      ${f('wa_token', 'Token API', 'password')}${f('wa_wablas_domain', 'Domain Wablas')}${f('wa_secret_key', 'Secret Key Wablas', 'password')}</div>

    <h3>Lokasi & Jadwal Sholat (Hisab Falak Kemenag RI)</h3>
    <p class="muted small">Pilih lokasi sekolah langsung pada peta interaktif Indonesia atau pilih kota preset. Sistem akan otomatis menentukan zona waktu (WIB, WITA, WIT) dan menghitung jadwal sholat secara akurat tanpa internet.</p>
    
    <!-- Peta Interaktif & Preset -->
    <div style="background:#061325;border:1px solid #1f2937;border-radius:10px;padding:12px;margin:10px 0">
      <div class="row wrap" style="gap:10px;margin-bottom:10px;align-items:center">
        <label style="margin:0;color:#e2e8f0;font-weight:600">Pilih Preset Kota:
          <select id="map_preset_city" style="width:auto;display:inline-block;margin-left:8px">
            <option value="">-- Pilih Kota / Wilayah --</option>
            <optgroup label="WIB (Waktu Indonesia Barat - UTC+7)">
              <option value="-6.2088,106.8456,7">Jakarta Pusat (DKI Jakarta)</option>
              <option value="-6.9175,107.6191,7">Bandung (Jawa Barat)</option>
              <option value="-6.3263,108.3200,7">Indramayu (Jawa Barat)</option>
              <option value="-6.9667,110.4167,7">Semarang (Jawa Tengah)</option>
              <option value="-7.7956,110.3695,7">Yogyakarta (DIY)</option>
              <option value="-7.2575,112.7521,7">Surabaya (Jawa Timur)</option>
              <option value="3.5952,98.6722,7">Medan (Sumatera Utara)</option>
              <option value="-0.9471,100.4172,7">Padang (Sumatera Barat)</option>
              <option value="-2.9761,104.7754,7">Palembang (Sumatera Selatan)</option>
              <option value="-0.0263,109.3425,7">Pontianak (Kalimantan Barat)</option>
            </optgroup>
            <optgroup label="WITA (Waktu Indonesia Tengah - UTC+8)">
              <option value="-5.1477,119.4327,8">Makassar (Sulawesi Selatan)</option>
              <option value="-8.6705,115.2126,8">Denpasar (Bali)</option>
              <option value="-8.5833,116.1167,8">Mataram (Lombok / NTB)</option>
              <option value="-10.1772,123.6070,8">Kupang (NTT)</option>
              <option value="-3.3194,114.5908,8">Banjarmasin (Kalimantan Selatan)</option>
              <option value="-1.2379,116.8289,8">Balikpapan (Kalimantan Timur)</option>
              <option value="1.4748,124.8428,8">Manado (Sulawesi Utara)</option>
            </optgroup>
            <optgroup label="WIT (Waktu Indonesia Timur - UTC+9)">
              <option value="-2.5489,140.7181,9">Jayapura (Papua)</option>
              <option value="-3.6554,128.1906,9">Ambon (Maluku)</option>
              <option value="0.7893,127.3610,9">Ternate (Maluku Utara)</option>
              <option value="-0.8762,131.2558,9">Sorong (Papua Barat Daya)</option>
              <option value="-4.0833,138.8333,9">Timika (Papua Tengah)</option>
            </optgroup>
          </select>
        </label>
        <span class="small muted">Atau klik langsung pada peta di bawah:</span>
      </div>

      <!-- Kanvas Peta SVG Interaktif Indonesia -->
      <div id="interactive_map_box" style="position:relative;width:100%;height:240px;background:#09192f;border-radius:8px;border:1px solid #14b8a6;cursor:crosshair;overflow:hidden">
        <svg viewBox="94 -12 48 19" style="width:100%;height:100%;display:block">
          <!-- Batas Zona Waktu (Garis Bujur 114 & 125) -->
          <line x1="114" y1="-12" x2="114" y2="7" stroke="#38bdf8" stroke-dasharray="0.6,0.6" stroke-width="0.2"/>
          <line x1="125" y1="-12" x2="125" y2="7" stroke="#fbbf24" stroke-dasharray="0.6,0.6" stroke-width="0.2"/>
          <text x="103" y="-10" fill="#38bdf8" font-size="1.2" font-weight="bold">ZONA WIB (UTC+7)</text>
          <text x="117.5" y="-10" fill="#38bdf8" font-size="1.2" font-weight="bold">ZONA WITA (UTC+8)</text>
          <text x="131" y="-10" fill="#fbbf24" font-size="1.2" font-weight="bold">ZONA WIT (UTC+9)</text>

          <!-- Siluet Kepulauan Indonesia Sederhana -->
          <!-- Sumatera -->
          <path d="M95.3,5.5 L98,2 L101,0.5 L104,-3 L106,-5.5 L104,-5.8 L101,-2.5 L97,1.5 Z" fill="#1e3a5f" stroke="#334155" stroke-width="0.15"/>
          <!-- Jawa & Madura -->
          <path d="M105.5,-6 L108,-6.5 L111,-7 L114.5,-8 L114,-8.7 L109,-7.5 L106,-7 Z" fill="#1e3a5f" stroke="#334155" stroke-width="0.15"/>
          <!-- Kalimantan -->
          <path d="M109,-3 L112,-4 L116,-3 L117.5,-1 L116,3 L112,4 L109,1 Z" fill="#1e3a5f" stroke="#334155" stroke-width="0.15"/>
          <!-- Sulawesi -->
          <path d="M119.5,-5 L120.5,-3 L122,-1 L124,1.5 L121,0 L119,0 L120,-3 Z" fill="#1e3a5f" stroke="#334155" stroke-width="0.15"/>
          <!-- Bali & Nusa Tenggara -->
          <path d="M115,-8.5 L117,-8.5 L119,-8.5 L121,-9 L124,-10 L123.5,-10.5 L115,-8.8 Z" fill="#1e3a5f" stroke="#334155" stroke-width="0.15"/>
          <!-- Maluku -->
          <path d="M127,-3 L129,-3 L128,-1 L127,1 L129,-6 Z" fill="#1e3a5f" stroke="#334155" stroke-width="0.15"/>
          <!-- Papua -->
          <path d="M131,-1 L134,-2 L137,-2.5 L141,-2.5 L141,-9 L138,-8 L135,-4 L131,-2.5 Z" fill="#1e3a5f" stroke="#334155" stroke-width="0.15"/>
        </svg>

        <!-- Pin Lokasi Sekolah -->
        <div id="map_pin" style="position:absolute;width:14px;height:14px;background:#ef4444;border:2px solid #fff;border-radius:50%;transform:translate(-50%,-50%);pointer-events:none;box-shadow:0 0 10px #ef4444"></div>
      </div>
    </div>

    <!-- Parameter Koordinat & Zona Waktu -->
    <div class="mbox two" style="width:auto;max-height:none">
      <label>Latitude (Lintang)
        <input data-k="prayer_lat" id="cfg_plat" type="number" step="0.0001" value="${esc(s.prayer_lat || '-6.2088')}">
      </label>
      <label>Longitude (Bujur)
        <input data-k="prayer_lng" id="cfg_plng" type="number" step="0.0001" value="${esc(s.prayer_lng || '106.8456')}">
      </label>
      <label>Zona Waktu Indonesia
        <select data-k="prayer_tz" id="cfg_ptz">
          <option value="7" ${String(s.prayer_tz) === '7' ? 'selected' : ''}>WIB (UTC+7) - Sumatera, Jawa, Madura, Kalbar, Kalteng</option>
          <option value="8" ${String(s.prayer_tz) === '8' ? 'selected' : ''}>WITA (UTC+8) - Bali, NTB, NTT, Kalsel, Kaltim, Sulawesi</option>
          <option value="9" ${String(s.prayer_tz) === '9' ? 'selected' : ''}>WIT (UTC+9) - Maluku, Maluku Utara, Papua</option>
        </select>
      </label>
      <div style="display:flex;align-items:flex-end">
        <div id="prayer_preview_box" class="small muted" style="padding:10px;background:#061325;border-radius:6px;width:100%">
          <span style="color:#14b8a6;font-weight:600">Jadwal Hari Ini:</span> Menghitung...
        </div>
      </div>
    </div>

    <h3>Pengaturan QRIS Statis Sekolah</h3>
    <p class="muted small">Upload gambar barcode QRIS sekolah (PNG/JPG) atau masukkan teks payload EMVCo untuk generate QRIS dinamis otomatis.</p>
    <div class="mbox two" style="width:auto;max-height:none">
      <label>Payload QRIS Statis
        <textarea data-k="qris_static" id="cfg_qris_val" rows="3" style="width:100%;background:#0b1220;color:#fff;border:1px solid #1f2937;border-radius:6px;padding:8px" placeholder="0002010102112658...">${esc(s.qris_static || '')}</textarea>
      </label>
      <div>
        <label>Upload Gambar QRIS (Auto-Scan)
          <input type="file" id="cfg_qris_img" accept="image/*" style="margin-top:4px">
        </label>
        <div id="qris_upload_status" class="small muted" style="margin-top:6px">
          ${s.qris_static ? '<span style="color:#4ade80">✓ QRIS sekolah sudah aktif terpasang</span>' : '<span style="color:#fbbf24">⚠ Belum ada QRIS yang diatur</span>'}
        </div>
      </div>
    </div>
    <div class="row end" style="margin-bottom:20px"><button class="btn primary">Simpan Pengaturan</button></div>
  </form>

  <div class="card" style="margin-top:20px">
    <h3>Uji Coba Generate Dynamic QRIS Sesuai Nominal</h3>
    <p class="muted small">Generate QRIS dinamis instan dengan nominal bebas untuk keperluan kasir/pembayaran offline.</p>
    <div class="row wrap" style="gap:10px;align-items:flex-end">
      <div style="flex:1;min-width:200px">
        <label>Nominal Pembayaran (Rp)
          <input type="number" id="test_qris_amt" placeholder="Contoh: 150000" min="1000" step="1000">
        </label>
      </div>
      <button type="button" class="btn primary" id="btn_test_qris" style="height:38px">⚡ Generate QRIS Dinamis</button>
    </div>
    <div id="test_qris_res" style="margin-top:14px"></div>
  </div>

  <div class="card" style="margin-top:20px">
    <h3>Backup & Restore Database SQLite</h3>
    <p class="muted small">Unduh file database (.db) ke komputer/flashdisk, atau restore file database sebelumnya.</p>
    <div class="row wrap" style="gap:12px;margin-top:12px">
      <a href="/api/backup/download" class="btn primary" download>📥 Unduh File Backup (.db)</a>
      <label class="btn" style="cursor:pointer">
        📤 Restore Database (.db)
        <input type="file" id="restore_file" accept=".db" style="display:none">
      </label>
      <button type="button" class="btn danger" onclick="resetDemo()">⚠ Reset Data Demo</button>
    </div>
    <div id="restore_res" style="margin-top:10px"></div>
  </div>`;

  $('#restore_file').onchange = async e => {
    const file = e.target.files[0];
    if (!file) return;
    if (!confirm(`Yakin ingin me-restore database dari file "${file.name}"? Data saat ini akan digantikan.`)) return;
    $('#restore_res').innerHTML = '<span class="muted">Sedang membaca dan mengunggah database...</span>';
    const reader = new FileReader();
    reader.onload = async () => {
      try {
        const base64 = reader.result.split(',')[1];
        const res = await api('POST', '/backup/restore', { data: base64 });
        alert(res.message || 'Database berhasil direstore.');
        location.reload();
      } catch (err) {
        $('#restore_res').innerHTML = `<span class="err">${esc(err.message)}</span>`;
      }
    };
    reader.readAsDataURL(file);
  };

  $('#cfg_qris_img').onchange = e => {
    const file = e.target.files[0];
    if (!file) return;
    $('#qris_upload_status').innerHTML = '<span class="muted">Memindai barcode dari gambar...</span>';
    const reader = new FileReader();
    reader.onload = async () => {
      try {
        const res = await api('POST', '/finance/qris/decode-image', { image: reader.result });
        $('#cfg_qris_val').value = res.payload;
        $('#qris_upload_status').innerHTML = '<span style="color:#4ade80;font-weight:600">✓ Berhasil memindai barcode QRIS dan disimpan otomatis!</span>';
        toast('QRIS sekolah berhasil diperbarui');
      } catch (err) {
        $('#qris_upload_status').innerHTML = `<span class="err">✕ ${esc(err.message)}</span>`;
      }
    };
    reader.readAsDataURL(file);
  };

  $('#btn_test_qris').onclick = async () => {
    const amt = +$('#test_qris_amt').value;
    if (!amt || amt <= 0) return alert('Masukkan nominal pembayaran yang valid');
    const staticQris = $('#cfg_qris_val').value.trim();
    $('#test_qris_res').innerHTML = '<span class="muted">Mengonversi ke QRIS dinamis...</span>';
    try {
      const res = await api('POST', '/finance/qris/generate-custom', { amount: amt, staticQris });
      const qris = res.qris;
      $('#test_qris_res').innerHTML = `
        <div class="card row wrap" style="gap:20px;align-items:center;background:#061325;border:1px solid #14b8a6">
          <img src="https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=${encodeURIComponent(qris)}" style="width:180px;height:180px;background:#fff;padding:8px;border-radius:10px" alt="QRIS">
          <div style="flex:1;min-width:260px">
            <h3 style="color:#14b8a6">QRIS Dinamis Siap Scan</h3>
            <p style="font-size:22px;font-weight:700;margin:6px 0">${rp(amt)}</p>
            <p class="muted small">Nominal sudah terkunci otomatis ke dalam QRIS. Saat customer scan via m-Banking/e-Wallet, nominal Rp ${amt.toLocaleString('id-ID')} akan langsung muncul otomatis tanpa perlu input manual.</p>
            <label class="small muted">Payload EMVCo Dinamis (Tag 54 = ${amt}):
              <input value="${esc(qris)}" readonly style="background:#111a2e;font-size:11px" onclick="this.select()">
            </label>
          </div>
        </div>
      `;
    } catch (err) {
      $('#test_qris_res').innerHTML = `<span class="err">${esc(err.message)}</span>`;
    }
  };

  // Logika Peta Interaktif & Perhitungan Hisab Waktu Sholat
  const mapBox = $('#interactive_map_box');
  const pin = $('#map_pin');
  const latInput = $('#cfg_plat');
  const lngInput = $('#cfg_plng');
  const tzInput = $('#cfg_ptz');

  function updatePin() {
    const lat = parseFloat(latInput.value) || -6.2088;
    const lng = parseFloat(lngInput.value) || 106.8456;
    // Bounding Box SVG Indonesia: Longitude [94 .. 142], Latitude [-12 .. 7]
    const xPct = ((lng - 94) / (142 - 94)) * 100;
    const yPct = ((7 - lat) / (7 - (-12))) * 100;
    pin.style.left = Math.max(0, Math.min(100, xPct)) + '%';
    pin.style.top = Math.max(0, Math.min(100, yPct)) + '%';
    renderPrayerPreview();
  }

  function autoDetectTz(lng) {
    if (lng < 114.0) return 7;      // WIB
    if (lng < 125.0) return 8;      // WITA
    return 9;                       // WIT
  }

  mapBox.onclick = e => {
    const rect = mapBox.getBoundingClientRect();
    const xRatio = (e.clientX - rect.left) / rect.width;
    const yRatio = (e.clientY - rect.top) / rect.height;
    const lng = +(94 + xRatio * (142 - 94)).toFixed(4);
    const lat = +(7 - yRatio * (7 - (-12))).toFixed(4);
    latInput.value = lat;
    lngInput.value = lng;
    const tz = autoDetectTz(lng);
    tzInput.value = tz;
    $('#map_preset_city').value = '';
    updatePin();
    toast(`Koordinat terpilih: ${lat}, ${lng} (Zona UTC+${tz})`);
  };

  $('#map_preset_city').onchange = e => {
    const val = e.target.value;
    if (!val) return;
    const [lat, lng, tz] = val.split(',').map(Number);
    latInput.value = lat;
    lngInput.value = lng;
    tzInput.value = tz;
    updatePin();
    toast(`Preset diterapkan: ${lat}, ${lng} (UTC+${tz})`);
  };

  latInput.oninput = updatePin;
  lngInput.oninput = () => {
    const lng = parseFloat(lngInput.value);
    if (!isNaN(lng)) tzInput.value = autoDetectTz(lng);
    updatePin();
  };
  tzInput.onchange = renderPrayerPreview;

  function renderPrayerPreview() {
    const lat = parseFloat(latInput.value) || -6.2088;
    const lng = parseFloat(lngInput.value) || 106.8456;
    const tz = parseFloat(tzInput.value) || 7.0;
    
    // Hitung hisab lokal Kemenag untuk tanggal hari ini
    const now = new Date();
    const d2r = d => (d * Math.PI) / 180;
    const r2d = r => (r * 180) / Math.PI;
    const fixH = h => { h = h - 24 * Math.floor(h / 24); return h < 0 ? h + 24 : h; };
    const fixA = a => { a = a - 360 * Math.floor(a / 360); return a < 0 ? a + 360 : a; };
    
    let y = now.getFullYear(), m = now.getMonth() + 1, d = now.getDate();
    if (m <= 2) { y -= 1; m += 12; }
    const aVal = Math.floor(y / 100);
    const bVal = 2 - aVal + Math.floor(aVal / 4);
    const jd = Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + bVal - 1524.5;
    const days = jd - 2451545.0;

    const g = fixA(357.529 + 0.98560028 * days);
    const q = fixA(280.459 + 0.98564736 * days);
    const L = fixA(q + 1.915 * Math.sin(d2r(g)) + 0.02 * Math.sin(d2r(2 * g)));
    const e = 23.439 - 0.00000036 * days;
    const delta = r2d(Math.asin(Math.sin(d2r(e)) * Math.sin(d2r(L))));
    const ra = fixA(r2d(Math.atan2(Math.cos(d2r(e)) * Math.sin(d2r(L)), Math.cos(d2r(L))))) / 15.0;
    const eqT = (q / 15.0) - ra;
    const midday = fixH(12.0 + tz - (lng / 15.0) - eqT);

    const getHourAngle = alt => {
      const cosH = (Math.sin(d2r(alt)) - Math.sin(d2r(lat)) * Math.sin(d2r(delta))) / (Math.cos(d2r(lat)) * Math.cos(d2r(delta)));
      return (cosH > 1) ? 0 : (cosH < -1) ? 180 : r2d(Math.acos(cosH));
    };

    const tSubuh = getHourAngle(-20.0) / 15.0;
    const tTerbit = getHourAngle(-0.833) / 15.0;
    const tIsya = getHourAngle(-18.0) / 15.0;
    const asrAlt = r2d(Math.atan(1.0 / (1.0 + Math.tan(d2r(Math.abs(lat - delta))))));
    const tAshar = getHourAngle(asrAlt) / 15.0;
    const iht = 2.0 / 60.0;

    const fmt = h => {
      h = fixH(h);
      const hh = Math.floor(h);
      const mm = Math.floor((h - hh) * 60);
      return String(hh).padStart(2, '0') + ':' + String(mm).padStart(2, '0');
    };

    const subuh = fmt(midday - tSubuh + iht);
    const dzuhur = fmt(midday + iht);
    const ashar = fmt(midday + tAshar + iht);
    const maghrib = fmt(midday + tTerbit + iht);
    const isya = fmt(midday + tIsya + iht);
    const tzLabel = tz === 7 ? 'WIB' : tz === 8 ? 'WITA' : 'WIT';

    $('#prayer_preview_box').innerHTML = `
      <b style="color:#14b8a6">Hisab Hari Ini (${tzLabel}):</b><br>
      Subuh <b>${subuh}</b> • Dzuhur <b>${dzuhur}</b> • Ashar <b>${ashar}</b> • Maghrib <b>${maghrib}</b> • Isya <b>${isya}</b>
    `;
  }

  updatePin();

  $('#sf').onsubmit = async e => {
    e.preventDefault(); const body = {};
    el.querySelectorAll('[data-k]').forEach(i => body[i.dataset.k] = i.value);
    try { await api('POST', '/settings', body); toast('Pengaturan tersimpan'); } catch (er) { alert(er.message); }
  };


}
async function resetDemo() {
  if (!confirm('Hapus SEMUA data dan isi ulang data dummy?')) return;
  await api('POST', '/demo/reset'); alert('Data demo dibuat ulang. Silakan login kembali.'); showLogin();
}

/* ---------- start ---------- */
(async () => {
  api('GET', '/settings/public').then(s => $('#schoolName').textContent = s.school_name || 'Smart School').catch(() => {});
  try { ME = await api('GET', '/auth/me'); boot(); } catch { showLogin(); }
})();
