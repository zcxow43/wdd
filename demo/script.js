// Mock data for Currency Management
const mockCurrencies = [
  {
    code: 'USD',
    name: '美元',
    lastOpTime: '2024-12-15 14:32:18',
    operator: 'yuki luo',
    status: 'APPROVED',
    approver: 'davidlou',
    lastApprovalTime: '2024-12-15 15:10:42'
  },
  {
    code: 'TWD',
    name: '新台幣',
    lastOpTime: '2024-12-14 09:21:05',
    operator: 'davidlou',
    status: 'APPROVED',
    approver: 'Irvin',
    lastApprovalTime: '2024-12-14 10:45:33'
  },
  {
    code: 'JPY',
    name: '日圓',
    lastOpTime: '2024-12-13 16:45:22',
    operator: 'testcrmpc6',
    status: 'REJECTED',
    approver: 'phisnu.liao',
    lastApprovalTime: '2024-12-13 17:30:11'
  },
  {
    code: 'EUR',
    name: '歐元',
    lastOpTime: '2024-12-12 11:15:47',
    operator: 'phisnu.liao',
    status: 'APPROVED',
    approver: 'yuki luo',
    lastApprovalTime: '2024-12-12 13:22:08'
  },
  {
    code: 'GBP',
    name: '英鎊',
    lastOpTime: '2024-12-11 13:52:36',
    operator: 'Irvin',
    status: 'APPROVED',
    approver: 'davidlou',
    lastApprovalTime: '2024-12-11 14:58:15'
  },
  {
    code: 'AUD',
    name: '澳幣',
    lastOpTime: '2024-12-10 10:28:19',
    operator: 'phisnu.liao2',
    status: 'REJECTED',
    approver: 'testcrmpc6',
    lastApprovalTime: '2024-12-10 11:40:22'
  },
  {
    code: 'CAD',
    name: '加幣',
    lastOpTime: '2024-12-09 15:14:55',
    operator: 'yuki luo',
    status: 'APPROVED',
    approver: 'phisnu.liao',
    lastApprovalTime: '2024-12-09 16:25:47'
  },
  {
    code: 'CHF',
    name: '瑞士法郎',
    lastOpTime: '2024-12-08 12:33:42',
    operator: 'davidlou',
    status: 'APPROVED',
    approver: 'Irvin',
    lastApprovalTime: '2024-12-08 13:45:26'
  }
];

// Initialize table on page load
document.addEventListener('DOMContentLoaded', function() {
  renderTable(mockCurrencies);
  setupEventListeners();
});

// Render table with data
function renderTable(data) {
  const tableBody = document.getElementById('tableBody');
  tableBody.innerHTML = '';

  data.forEach(item => {
    const row = document.createElement('tr');

    const statusClass = item.status === 'APPROVED' ? 'status-approved' : 'status-rejected';

    row.innerHTML = `
      <td><span class="currency-code">${item.code}</span></td>
      <td>${item.name}</td>
      <td>${item.lastOpTime}</td>
      <td>${item.operator}</td>
      <td>
        <span class="status-badge ${statusClass}">
          <span class="status-dot"></span>
          ${item.status}
        </span>
      </td>
      <td>${item.approver}</td>
      <td>${item.lastApprovalTime}</td>
      <td>
        <div class="action-buttons">
          <button class="action-btn" title="Edit"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path></svg></button>
          <button class="action-btn" title="View"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle></svg></button>
          <button class="action-btn" title="Approve"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg></button>
          <button class="action-btn" title="History"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg></button>
        </div>
      </td>
    `;

    tableBody.appendChild(row);
  });

  // Update total count
  document.getElementById('totalCount').textContent = data.length;
}

// Setup event listeners
function setupEventListeners() {
  // Search button
  const searchBtn = document.querySelector('.btn-search');
  searchBtn.addEventListener('click', function() {
    // Simulate search - just re-render the same data
    // In a real app, this would filter based on the input values
    renderTable(mockCurrencies);
    console.log('Search clicked');
  });

  // Reset button
  const resetBtn = document.querySelector('.btn-reset');
  resetBtn.addEventListener('click', function() {
    // Clear filter inputs
    document.querySelector('.filter-input').value = '';
    document.querySelectorAll('.date-input').forEach(input => {
      input.value = '';
    });
    document.querySelectorAll('.filter-input')[1].value = '';

    // Re-render table with all data
    renderTable(mockCurrencies);
    console.log('Reset clicked');
  });

  // Action buttons - add hover effects and click handlers
  document.addEventListener('click', function(e) {
    if (e.target.classList.contains('action-btn')) {
      const btn = e.target;
      const title = btn.getAttribute('title');
      console.log('Action clicked:', title);
      // Visual feedback
      btn.style.transform = 'scale(0.95)';
      setTimeout(() => {
        btn.style.removeProperty('transform');
      }, 100);
    }
  });

  // Page size select
  const pageSizeSelect = document.querySelector('.page-size-select');
  pageSizeSelect.addEventListener('change', function() {
    console.log('Page size changed to:', this.value);
  });

  // Pagination buttons
  const prevBtn = document.querySelector('.pagination-btn.prev');
  const nextBtn = document.querySelector('.pagination-btn.next');

  prevBtn.addEventListener('click', function() {
    const pageInput = document.querySelector('.page-input');
    const currentPage = parseInt(pageInput.value);
    if (currentPage > 1) {
      pageInput.value = currentPage - 1;
    }
  });

  nextBtn.addEventListener('click', function() {
    const pageInput = document.querySelector('.page-input');
    const totalPages = parseInt(document.querySelector('.total-pages').textContent);
    const currentPage = parseInt(pageInput.value);
    if (currentPage < totalPages) {
      pageInput.value = currentPage + 1;
    }
  });

  // Sidebar nav item click handlers for visual feedback
  document.querySelectorAll('.nav-item, .nav-subitem').forEach(item => {
    if (!item.classList.contains('active')) {
      item.addEventListener('click', function() {
        console.log('Nav item clicked:', this.textContent.trim());
      });
    }
  });

  // Settings button (no-op)
  document.querySelector('.table-settings').addEventListener('click', function() {
    console.log('Table settings clicked');
  });

  // Collapse sidebar button
  document.querySelector('.collapse-btn').addEventListener('click', function() {
    const sidebar = document.querySelector('.sidebar');
    sidebar.classList.toggle('sidebar-collapsed');
    console.log('Sidebar toggled, collapsed:', sidebar.classList.contains('sidebar-collapsed'));
  });

  // Nav parent toggle (submenu expand/collapse)
  const navParent = document.querySelector('.nav-parent');
  if (navParent) {
    navParent.addEventListener('click', function() {
      this.classList.toggle('expanded');
      console.log('Submenu toggled, expanded:', this.classList.contains('expanded'));
    });
  }

  // Tab switching
  document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.addEventListener('click', function() {
      const tabId = this.getAttribute('data-tab');
      switchTab(tabId);
    });
  });

  // User menu (no-op)
  document.querySelector('.header-user').addEventListener('click', function() {
    console.log('User menu clicked');
  });
}

// Tab switching function
function switchTab(tabId) {
  // Remove active class from all tabs and panels
  document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.classList.remove('active');
  });
  document.querySelectorAll('.tab-panel').forEach(panel => {
    panel.classList.remove('active');
  });

  // Add active class to clicked tab and corresponding panel
  document.querySelector(`[data-tab="${tabId}"]`).classList.add('active');
  document.getElementById(tabId).classList.add('active');
  console.log('Switched to tab:', tabId);
}
