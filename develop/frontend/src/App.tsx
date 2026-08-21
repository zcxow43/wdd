import { Navigate, Route, Routes } from 'react-router-dom'
import AppLayout from './layouts/AppLayout'
import HealthPage from './pages/HealthPage'
import BrandManagementPage from './pages/BrandManagementPage'
import CurrencyManagementPage from './pages/CurrencyManagementPage'

function App() {
  return (
    <Routes>
      <Route path="/health" element={<HealthPage />} />
      <Route element={<AppLayout />}>
        <Route path="/" element={<Navigate to="/brands" replace />} />
        <Route path="/brands" element={<BrandManagementPage />} />
        <Route path="/currencies" element={<CurrencyManagementPage />} />
      </Route>
    </Routes>
  )
}

export default App
