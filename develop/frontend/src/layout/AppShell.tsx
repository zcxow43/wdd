import { NavLink, Outlet } from 'react-router-dom'
import owsLogo from '../assets/ows-logo.png'
import './AppShell.css'

interface NavItem {
  to: string
  label: string
}

const NAV_ITEMS: NavItem[] = [
  { to: '/brands', label: '品牌管理' },
  { to: '/currencies', label: '幣種管理' },
  { to: '/currency-pair-definitions', label: '幣種對主檔' },
  { to: '/currency-pairs', label: '品牌幣種對' },
  { to: '/spread-groups', label: '點差管理' },
  { to: '/audit-requests', label: '審核作業' },
]

export function AppShell() {
  return (
    <div className="app-shell">
      <aside className="app-sidebar">
        <div className="app-sidebar-logo">
          <img src={owsLogo} alt="OWS" className="app-logo-image" />
        </div>
        <nav className="app-sidebar-nav">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) => `app-nav-item${isActive ? ' app-nav-item--active' : ''}`}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>

      <div className="app-main-wrapper">
        <header className="app-top-header">
          <div className="app-header-user">
            <span className="app-user-avatar" aria-hidden="true">
              OW
            </span>
            <span className="app-username">Admin</span>
          </div>
        </header>

        <main className="app-content-area">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
