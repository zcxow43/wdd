import { NavLink, Outlet } from 'react-router-dom'
import owsLogo from '../assets/ows-logo.png'
import './AppShell.css'

interface NavItem {
  to: string
  label: string
}

const NAV_ITEMS: NavItem[] = [
  { to: '/currencies', label: 'Currency Management' },
  { to: '/currency-pairs', label: 'Currency Pair List' },
  { to: '/brands', label: 'Brand Management' },
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
