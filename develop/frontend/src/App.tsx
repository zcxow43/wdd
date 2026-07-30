import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from './layout/AppShell'
import { CurrencyPage } from './pages/CurrencyPage'
import { BrandPage } from './pages/BrandPage'
import { CurrencyPairPage } from './pages/CurrencyPairPage'
import { CurrencyPairDefinitionPage } from './pages/CurrencyPairDefinitionPage'
import { SpreadPage } from './pages/SpreadPage'
import { AuditPage } from './audit/AuditPage'

function App() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route path="/" element={<Navigate to="/currencies" replace />} />
        <Route path="/currencies" element={<CurrencyPage />} />
        <Route path="/brands" element={<BrandPage />} />
        <Route path="/currency-pairs" element={<CurrencyPairPage />} />
        <Route path="/currency-pair-definitions" element={<CurrencyPairDefinitionPage />} />
        <Route path="/spread-groups" element={<SpreadPage />} />
        <Route path="/audit-requests" element={<AuditPage />} />
      </Route>
    </Routes>
  )
}

export default App
