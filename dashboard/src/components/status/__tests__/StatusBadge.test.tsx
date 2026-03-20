import { render, screen } from '@testing-library/react'
import { StatusBadge } from '../StatusBadge'

describe('StatusBadge', () => {
  it('muestra "Operativo" para healthy', () => {
    render(<StatusBadge status="healthy" />)
    expect(screen.getByText('Operativo')).toBeInTheDocument()
  })
  it('muestra "Error" para error', () => {
    render(<StatusBadge status="error" />)
    expect(screen.getByText('Error')).toBeInTheDocument()
  })
  it('muestra "Degradado" para degraded', () => {
    render(<StatusBadge status="degraded" />)
    expect(screen.getByText('Degradado')).toBeInTheDocument()
  })
  it('muestra "Desconocido" para unknown', () => {
    render(<StatusBadge status="unknown" />)
    expect(screen.getByText('Desconocido')).toBeInTheDocument()
  })
})
