import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { AppLayout } from '@/components/layout/AppLayout'
import { StatusPage } from '@/pages/StatusPage'
import { StreamsPage } from '@/pages/StreamsPage'
import { LogsPage } from '@/pages/LogsPage'
import { SimulatorPage } from '@/pages/SimulatorPage'

export function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppLayout />}>
          <Route index          element={<StatusPage />}    />
          <Route path="streams"   element={<StreamsPage />} />
          <Route path="logs"      element={<LogsPage />}    />
          <Route path="simulator" element={<SimulatorPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
