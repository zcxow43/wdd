import type { ReactNode } from 'react'
import { NavLink } from 'react-router-dom'
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

interface AppShellProps {
  children: ReactNode
}

/** Persistent sidebar + top-header shell wrapping every routed page. */
export function AppShell({ children }: AppShellProps) {
  return (
    <div className="app-shell">
      <aside className="app-sidebar">
        <div className="app-sidebar-logo">
          <img src={owsLogo} alt="OWS" className="app-logo-image" />
        </div>
        <nav className="app-sidebar-nav" aria-label="主選單">
          <ul>
            {NAV_ITEMS.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  className={({ isActive }) =>
                    `app-sidebar-link${isActive ? ' app-sidebar-link--active' : ''}`
                  }
                >
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
      </aside>

      <div className="app-main-wrapper">
        <header className="app-top-header">
          <div className="app-header-spacer" />
          <div className="app-header-user">
            <div className="app-user-avatar" aria-hidden="true">
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                <circle cx="12" cy="7" r="4" />
              </svg>
            </div>
            <span className="app-username">使用者</span>
          </div>
        </header>

        <main className="app-content">{children}</main>
      </div>
    </div>
  )
}
