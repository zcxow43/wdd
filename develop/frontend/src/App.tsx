import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from './layout/AppShell'
import { CurrencyPage } from './pages/CurrencyPage'
import { BrandPage } from './pages/BrandPage'
import { CurrencyPairPage } from './pages/CurrencyPairPage'
import { AuditPage } from './audit/AuditPage'

function App() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route path="/" element={<Navigate to="/currencies" replace />} />
        <Route path="/currencies" element={<CurrencyPage />} />
        <Route path="/brands" element={<BrandPage />} />
        <Route path="/currency-pairs" element={<CurrencyPairPage />} />
        <Route path="/audit-requests" element={<AuditPage />} />
      </Route>
    </Routes>
  )
}

export default App
