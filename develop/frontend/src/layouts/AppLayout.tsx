import { NavLink, Outlet } from 'react-router-dom'
import './AppLayout.css'

interface NavItem {
  label: string
  path: string
  enabled: boolean
}

const EXCHANGE_RATE_CENTER_ITEMS: NavItem[] = [
  { label: '幣別管理', path: '/currencies', enabled: true },
  { label: '幣別對管理', path: '/currency-pairs', enabled: false },
  { label: '價差群組管理', path: '/spreads', enabled: false },
  { label: '品牌管理', path: '/brands', enabled: true },
  { label: '審核紀錄', path: '/audit-requests', enabled: false },
]

function AppLayout() {
  return (
    <div className="app-layout">
      <nav className="app-layout__sidebar" aria-label="主選單">
        <div className="app-layout__brand">匯率中心 WDD</div>
        <div className="app-layout__group-title">匯率中心</div>
        <ul className="app-layout__nav-list">
          {EXCHANGE_RATE_CENTER_ITEMS.map((item) =>
            item.enabled ? (
              <li key={item.path}>
                <NavLink
                  to={item.path}
                  className={({ isActive }) =>
                    `app-layout__nav-item${isActive ? ' app-layout__nav-item--active' : ''}`
                  }
                >
                  {item.label}
                </NavLink>
              </li>
            ) : (
              <li key={item.path}>
                <span className="app-layout__nav-item app-layout__nav-item--disabled">
                  {item.label}
                </span>
              </li>
            ),
          )}
        </ul>
      </nav>
      <main className="app-layout__content">
        <Outlet />
      </main>
    </div>
  )
}

export default AppLayout
