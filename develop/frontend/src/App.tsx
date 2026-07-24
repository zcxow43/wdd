import { Navigate, Route, Routes } from 'react-router-dom'
import { CurrencyPage } from './pages/CurrencyPage'

function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/currencies" replace />} />
      <Route path="/currencies" element={<CurrencyPage />} />
    </Routes>
  )
}

export default App
