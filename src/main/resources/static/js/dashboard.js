const API_ROOT = '/foods';
let page = 0;
let size = 6;
let totalPages = 0;
let allData = [];
let chart = null;

const foodBody = document.getElementById('foodBody');
const spinner = document.getElementById('spinner');
const emptyState = document.getElementById('emptyState');
const prevBtn = document.getElementById('prevBtn');
const nextBtn = document.getElementById('nextBtn');
const currentPage = document.getElementById('currentPage');
const totalPagesEl = document.getElementById('totalPages');
const pageInfo = document.getElementById('pageInfo');

const searchInput = document.getElementById('searchInput');
const searchClear = document.getElementById('searchClear');
const categoryFilter = document.getElementById('categoryFilter');
const sortField = document.getElementById('sortField');
const sortDir = document.getElementById('sortDir');

const foodModal = document.getElementById('foodModal');
const modalTitle = document.getElementById('modalTitle');
const foodForm = document.getElementById('foodForm');
const foodIdEl = document.getElementById('foodId');
const foodNameEl = document.getElementById('foodName');
const foodPriceEl = document.getElementById('foodPrice');
const foodCategoryModal = document.getElementById('foodCategoryModal');

const toastContainer = document.getElementById('toastContainer');
const exportCsvBtn = document.getElementById('exportCsv');
const addBtn = document.getElementById('btn-add');
const themeToggle = document.getElementById('themeToggle');

document.addEventListener('DOMContentLoaded', ()=> {
    attachHandlers();
    loadFoods();
    initChart();
    applyGlowTheme();
    animateEntry();
});

function attachHandlers(){
    prevBtn.addEventListener('click', ()=> { if(page>0){ page--; loadFoods(); }});
    nextBtn.addEventListener('click', ()=> { if(page < totalPages-1){ page++; loadFoods(); }});
    addBtn.addEventListener('click', ()=> openModal());
    foodForm.addEventListener('submit', onSave);
    document.getElementById('modalCancel').addEventListener('click', closeModal);
    searchInput.addEventListener('input', debounce(onSearch, 220));
    searchClear.addEventListener('click', ()=>{ searchInput.value=''; onSearch();});
    categoryFilter.addEventListener('change', onSearch);
    sortField.addEventListener('change', ()=>{ page=0; loadFoods();});
    sortDir.addEventListener('change', ()=>{ page=0; loadFoods();});
    exportCsvBtn.addEventListener('click', exportCSV);
    themeToggle.addEventListener('click', toggleTheme);
    document.querySelectorAll('.nav-item').forEach(a => a.addEventListener('click', navClick));
}

function navClick(e){
    document.querySelectorAll('.nav-item').forEach(n=>n.classList.remove('active'));
    e.currentTarget.classList.add('active');
}

function showSpinner(on = true){ spinner.style.display = on ? "flex" : "none"; }

function showToast(text, type='info', timeout=3000){
    const t = document.createElement('div');
    t.className = `toast ${type} show`;
    t.textContent = text;
    toastContainer.appendChild(t);
    setTimeout(()=> t.classList.remove('show'), timeout);
    setTimeout(()=> t.remove(), timeout+300);
}

function loadFoods(){
    showSpinner(true);
    emptyState.style.display = 'none';
    const sf = encodeURIComponent(sortField.value);
    const sd = encodeURIComponent(sortDir.value);
    fetch(`${API_ROOT}?page=${page}&size=${size}&sortField=${sf}&sortDir=${sd}`)
        .then(r => r.json())
        .then(data => {
            totalPages = data.totalPages ?? 1;
            currentPage.textContent = (page+1);
            totalPagesEl.textContent = totalPages;
            pageInfo.textContent = `Showing ${data.numberOfElements} of ${data.totalElements} items`;
            allData = data.content || [];
            renderTable(allData);
            updateChart(allData);
            showSpinner(false);
        })
        .catch(err => {
            showSpinner(false);
            showToast('Failed to load items','error',4000);
        });
}

function renderTable(items){
    const query = (searchInput.value || '').trim().toLowerCase();
    const cat = categoryFilter.value;
    let filtered = items.filter(it => {
        const m1 = !query || it.name.toLowerCase().includes(query) || (it.category || '').toLowerCase().includes(query);
        const m2 = !cat || it.category === cat;
        return m1 && m2;
    });
    foodBody.innerHTML = '';
    if(filtered.length === 0){ emptyState.style.display = 'block'; return; } else { emptyState.style.display = 'none'; }
    filtered.forEach(f => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
      <td>${f.id}</td>
      <td>${escapeHtml(f.name)}</td>
      <td>${(f.price != null) ? Number(f.price).toFixed(2) : ''}</td>
      <td>${escapeHtml(f.category || '')}</td>
      <td>
        <button class="action-btn edit" onclick="onEdit(${f.id})">Edit</button>
        <button class="action-btn delete" onclick="onDelete(${f.id})">Delete</button>
      </td>
    `;
        foodBody.appendChild(tr);
    });
}

function onEdit(id){
    const found = allData.find(x => x.id === id);
    if(!found){ showToast('Item not in current page','info'); return; }
    openModal(found);
}

function onDelete(id){
    if(!confirm('Delete this food item?')) return;
    fetch(`${API_ROOT}/${id}`, { method:'DELETE' })
        .then(r => { if(!r.ok) throw new Error('Delete failed'); showToast('Deleted','success'); loadFoods(); })
        .catch(() => showToast('Delete failed','error',4000));
}

function openModal(item = null){
    foodModal.setAttribute('aria-hidden','false');
    if(item){
        modalTitle.textContent = 'Edit Food';
        foodIdEl.value = item.id;
        foodNameEl.value = item.name;
        foodPriceEl.value = item.price;
        foodCategoryModal.value = item.category || 'Other';
    } else {
        modalTitle.textContent = 'Add Food';
        foodIdEl.value = '';
        foodForm.reset();
    }
}

function closeModal(){ foodModal.setAttribute('aria-hidden','true'); }

function onSave(e){
    e.preventDefault();
    const id = foodIdEl.value;
    const payload = { name: foodNameEl.value.trim(), price: parseFloat(foodPriceEl.value), category: foodCategoryModal.value };
    if(!payload.name){ showToast('Name required','error'); return; }
    if(isNaN(payload.price)){ showToast('Valid price required','error'); return; }
    const method = id ? 'PUT' : 'POST';
    const url = id ? `${API_ROOT}/${id}` : API_ROOT;
    fetch(url, { method, headers:{'Content-Type':'application/json'}, body: JSON.stringify(payload) })
        .then(r => r.json())
        .then(() => { showToast(id ? 'Updated' : 'Created', 'success'); closeModal(); loadFoods(); })
        .catch(() => showToast('Save failed','error',4000));
}

function onSearch(){ renderTable(allData); }

function escapeHtml(s){ return String(s).replace(/[&<>"']/g, m => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m])); }
function debounce(fn, t){ let to; return (...a)=>{ clearTimeout(to); to=setTimeout(()=>fn(...a), t); }; }

function exportCSV(){
    const rows = Array.from(foodBody.querySelectorAll('tr')).map(tr=> Array.from(tr.children).slice(0,4).map(td=>td.textContent.trim()));
    if(rows.length===0){ showToast('No data to export','info'); return; }
    let csv = 'ID,Name,Price,Category\n' + rows.map(r=>r.map(c=>`"${c.replace(/"/g,'""')}"`).join(',')).join('\n');
    const blob = new Blob([csv], {type:'text/csv'});
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a'); a.href = url; a.download = 'foods.csv'; a.click(); URL.revokeObjectURL(url);
}

function initChart(){
    const ctx = document.getElementById('categoryChart').getContext('2d');
    chart = new Chart(ctx, { type:'pie', data:{labels:[],datasets:[{data:[],backgroundColor:[],borderColor:[]}]}, options:{plugins:{legend:{display:false}},responsive:true} });
}

function updateChart(items){
    const counts = {};
    (items || []).forEach(it => { const c = it.category || 'Other'; counts[c] = (counts[c] || 0) + 1; });
    const labels = Object.keys(counts);
    const data = labels.map(l => counts[l]);
    const colors = labels.map((_,i)=> `hsl(${(i*60)%360} 80% 60%)`);
    if(chart){ chart.data.labels = labels; chart.data.datasets[0].data = data; chart.data.datasets[0].backgroundColor = colors; chart.update(); renderLegend(labels, colors); }
}

function renderLegend(labels, colors){
    const legend = document.getElementById('chartLegend');
    legend.innerHTML = '';
    labels.forEach((l,i) => {
        const item = document.createElement('div'); item.className='item';
        item.innerHTML = `<div class="swatch" style="background:${colors[i]}"></div><div>${l} (${chart.data.datasets[0].data[i]})</div>`;
        legend.appendChild(item);
    });
}

let glowOn = true;
function applyGlowTheme(){ document.documentElement.style.setProperty('--neon', glowOn ? '#20bfff' : '#7f8c8d'); document.documentElement.style.setProperty('--glow', glowOn ? '#8feeff' : '#dcdcdc'); }
function toggleTheme(){ glowOn = !glowOn; applyGlowTheme(); showToast(glowOn ? 'Glow enabled' : 'Glow disabled','info',1200); }

function animateEntry(){
    document.querySelectorAll('.neon-card').forEach((c,i)=>{ c.style.opacity=0; c.style.transform='translateY(12px)'; setTimeout(()=>{ c.style.transition='all 600ms cubic-bezier(.2,.8,.2,1)'; c.style.opacity=1; c.style.transform='translateY(0)'; }, 120 + i*80); });
}
