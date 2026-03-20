import { NavLink } from 'react-router-dom'
import { Activity, Waves, ScrollText, Zap } from 'lucide-react'

const NAV_ITEMS = [
  { to: '/',          label: 'Estado',     Icon: Activity   },
  { to: '/streams',   label: 'Streams',    Icon: Waves      },
  { to: '/logs',      label: 'Logs',       Icon: ScrollText },
  { to: '/simulator', label: 'Simulador',  Icon: Zap        }
] as const

export function Sidebar() {
  return (
    <nav className="w-56 min-h-screen bg-gray-900 text-gray-300 flex flex-col shrink-0">
      <div className="px-4 py-5 border-b border-gray-700">
        <h1 className="text-white font-bold text-lg leading-tight">
          AWS Local<br />Dashboard
        </h1>
      </div>
      <ul className="flex-1 py-4 space-y-1 px-2">
        {NAV_ITEMS.map(({ to, label, Icon }) => (
          <li key={to}>
            <NavLink
              to={to}
              end={to === '/'}
              className={({ isActive }) =>
                `flex items-center gap-3 px-3 py-2 rounded-md text-sm transition-colors ${
                  isActive
                    ? 'bg-blue-600 text-white'
                    : 'hover:bg-gray-800 hover:text-white'
                }`
              }
            >
              <Icon size={16} />
              {label}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  )
}
