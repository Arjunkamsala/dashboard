let DATA = [];
let currentFilter = 'all';
let currentSearch = '';
let currentDist = '';

function fetchData() {
    const loadingEl = document.getElementById('loading');
    if (loadingEl) loadingEl.style.display = 'block';
    fetch('/api/nodes')
        .then(res => res.json())
        .then(json => {
            if (loadingEl) loadingEl.style.display = 'none';
            DATA = json.data;
            const reportDateEl = document.getElementById('reportDate');
            if (reportDateEl) reportDateEl.textContent = "Report Date:" + (json.date || "N/A");
            populateDistricts();
            render();
        });
}

function populateDistricts() {
    const dists = [...new Set(DATA.map(d => d.district))].sort();
    const distSel = document.getElementById('distFilter');
    if (distSel) {
        distSel.innerHTML = '<option value="">All Districts</option>';
        dists.forEach(d => {
            const o = document.createElement('option');
            o.value = d; o.textContent = d;
            distSel.appendChild(o);
        });
    }
}

function filter(f, btn) {
    currentFilter = f;
    document.querySelectorAll('.btn-group .btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    render();
}

function render() {
    const tbody = document.getElementById('tbody');
    if (!tbody) return;

    const filtered = DATA.filter(d => {
        if (currentFilter !== 'all' && d.status !== currentFilter) return false;
        if (currentDist && d.district !== currentDist) return false;
        if (currentSearch) {
            const s = currentSearch;
            const gpMatch = d.gps && d.gps.some(g => g.loc.toLowerCase().includes(s));
            if (!d.name.toLowerCase().includes(s) && !(d.blockCode || "").toLowerCase().includes(s) && !d.ip.includes(s) && !d.district.toLowerCase().includes(s) && !gpMatch) return false;
        }
        return true;
    });

    let html = '';
    let totalU = 0, totalR = 0;
    DATA.forEach(d => { if(d.status === 'UNREACHABLE') totalU++; else totalR++; });

    filtered.forEach((d, i) => {
        const isDown = d.status === 'UNREACHABLE';
        const badge = isDown
            ? '<span class="status-badge down">DOWN</span>'
            : '<span class="status-badge up">UP</span>';
        const gpHtml = (d.gps || []).map(g => `<span class="gp-tag ${g.status === 'DOWN' ? 'down' : ''}">${g.loc}</span>`).join('');
        html += `<tr>
            <td>${i + 1}</td>
            <td>${d.district || ''}</td>
            <td>${d.name || ''}</td>
            <td>${d.blockCode || ''}</td>
            <td>${d.ip || ''}</td>
            <td>${d.gpBlock || ''}</td>
            <td>${badge}</td>
            <td>${d.gpCount}</td>
            <td>
                <button class="gp-view-btn" onclick="openGPsOnly('${d.ip}')">
                    View Locations
                </button>
            </td>
            <td style="text-align:center;">
                <button class="preview-btn" onclick="openPreview('${d.ip}')" title="View Details">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                </button>
            </td>
        </tr>`;
    });

    tbody.innerHTML = html || '<tr><td colspan="10" style="text-align:center;padding:40px;">No data found</td></tr>';

    document.getElementById('totalNum').textContent = DATA.length;
    document.getElementById('upNum').textContent = totalR;
    document.getElementById('downNum').textContent = totalU;
}

function openGPsOnly(ip) {
    const node = DATA.find(d => d.ip === ip);
    if (!node) return;
    const gpTags = (node.gps || []).map(g => `<span class="gp-tag ${g.status === 'DOWN' ? 'down' : ''}">${g.loc}</span>`).join('');

    document.getElementById('previewTitle').textContent = "GP Locations: " + node.name;
    document.getElementById('previewBody').innerHTML = `
        <div class="gp-container" style="max-height:300px; border:1px solid #eee; padding:15px; border-radius:8px; background:#fdfdfd;">
            ${gpTags || '<span style="color:#999;">No locations found.</span>'}
        </div>
    `;
    document.getElementById('previewModal').style.display = 'flex';
}

function openPreview(ip) {
    const node = DATA.find(d => d.ip === ip);
    if (!node) return;
    const isDown = node.status === 'UNREACHABLE';
    const statusColor = isDown ? '#f44336' : '#4caf50';
    const gpTags = (node.gps || []).map(g => `<span class="gp-tag ${g.status === 'DOWN' ? 'down' : ''}">${g.loc}</span>`).join('');

    document.getElementById('previewTitle').textContent = "Node: " + node.name;
    document.getElementById('previewBody').innerHTML = `
        <div class="attr-row"><span class="attr-key">District:</span><span class="attr-val">${node.district || '—'}</span></div>
        <div class="attr-row"><span class="attr-key">Block Node Name:</span><span class="attr-val">${node.name || '—'}</span></div>
        <div class="attr-row"><span class="attr-key">Block Code:</span><span class="attr-val">${node.blockCode || '—'}</span></div>
        <div class="attr-row"><span class="attr-key">IP Address:</span><span class="attr-val">${node.ip || '—'}</span></div>
        <div class="attr-row"><span class="attr-key">Status:</span><span class="attr-val" style="color:${statusColor}; font-weight:bold;">${node.status}</span></div>
        <div class="attr-row"><span class="attr-key">GP Count:</span><span class="attr-val">${node.gpCount}</span></div>
        <div style="margin-top:15px; font-weight:bold; color:#666; font-size:12px;">GP LOCATIONS:</div>
        <div class="gp-container" style="max-height:150px; margin-top:10px; border:1px solid #eee; padding:10px; border-radius:5px;">${gpTags || '—'}</div>
    `;
    document.getElementById('previewModal').style.display = 'flex';
}

function closePreview() {
    document.getElementById('previewModal').style.display = 'none';
}

async function syncLiveApi() {
    const btn = document.getElementById('refreshBtn');
    if (btn) {
        btn.disabled = true;
        btn.textContent = "Refreshing...";
    }
    const loadingEl = document.getElementById('loading');
    if (loadingEl) loadingEl.style.display = 'block';
    
    try {
        await fetch('/api/sync-live', { method: 'POST' });
        pollSync();
    } catch (e) {
        if (btn) {
            btn.disabled = false;
            btn.textContent = "Refresh";
        }
        if (loadingEl) loadingEl.style.display = 'none';
    }
}

async function pollSync() {
    const res = await fetch('/api/sync-status');
    const data = await res.json();
    if (data.status === "Completed" || data.status === "Stopped") {
        const btn = document.getElementById('refreshBtn');
        if (btn) {
            btn.disabled = false;
            btn.textContent = "Refresh";
        }
        const loadingEl = document.getElementById('loading');
        if (loadingEl) loadingEl.style.display = 'none';
        fetchData();
        setTimeout(() => { alert("Data Refreshed Successfully!"); }, 500);
    } else {
        setTimeout(pollSync, 2000);
    }
}

document.addEventListener('DOMContentLoaded', () => {
    fetchData();
    const distFilter = document.getElementById('distFilter');
    if (distFilter) distFilter.onchange = (e) => { currentDist = e.target.value; render(); };
    const searchInput = document.getElementById('search');
    if (searchInput) searchInput.oninput = (e) => { currentSearch = e.target.value.toLowerCase(); render(); };
});
