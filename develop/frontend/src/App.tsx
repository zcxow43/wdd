import { Navigate, Route, Routes } from 'react-router-dom'
import AppLayout from './layouts/AppLayout'
import HealthPage from './pages/HealthPage'
import BrandManagementPage from './pages/BrandManagementPage'
import CurrencyManagementPage from './pages/CurrencyManagementPage'
import CurrencyPairManagementPage from './pages/CurrencyPairManagementPage'
import ExchangeRateSyncPage from './pages/ExchangeRateSyncPage'
import BrandCurrencyPairPage from './pages/BrandCurrencyPairPage'
import SpreadGroupManagementPage from './pages/SpreadGroupManagementPage'
import AuditRequestPage from './pages/AuditRequestPage'

function App() {
  return (
    <Routes>
      <Route path="/health" element={<HealthPage />} />
      <Route element={<AppLayout />}>
        <Route path="/" element={<Navigate to="/brands" replace />} />
        <Route path="/brands" element={<BrandManagementPage />} />
        <Route path="/currencies" element={<CurrencyManagementPage />} />
        <Route path="/currency-pairs" element={<CurrencyPairManagementPage />} />
        <Route path="/exchange-rates" element={<ExchangeRateSyncPage />} />
        <Route
          path="/brand-currency-pairs"
          element={<BrandCurrencyPairPage />}
        />
        <Route path="/spreads" element={<SpreadGroupManagementPage />} />
        <Route path="/audit-requests" element={<AuditRequestPage />} />
      </Route>
    </Routes>
  )
}

export default App
