import { renderHook, act } from '@testing-library/react'
import { vi, describe, it, expect, beforeEach, afterEach } from 'vitest'
import { usePolling } from '../usePolling'

describe('usePolling', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(()  => vi.useRealTimers())

  it('llama al callback inmediatamente al montar', () => {
    const cb = vi.fn()
    renderHook(() => usePolling(cb, 5000))
    expect(cb).toHaveBeenCalledTimes(1)
  })

  it('llama al callback en cada intervalo', () => {
    const cb = vi.fn()
    renderHook(() => usePolling(cb, 1000))
    act(() => { vi.advanceTimersByTime(3000) })
    expect(cb).toHaveBeenCalledTimes(4)   // 1 inicial + 3 ticks
  })

  it('no llama al callback cuando enabled=false', () => {
    const cb = vi.fn()
    renderHook(() => usePolling(cb, 1000, false))
    act(() => { vi.advanceTimersByTime(5000) })
    expect(cb).not.toHaveBeenCalled()
  })

  it('cancela el intervalo al desmontar', () => {
    const cb = vi.fn()
    const { unmount } = renderHook(() => usePolling(cb, 1000))
    unmount()
    act(() => { vi.advanceTimersByTime(3000) })
    expect(cb).toHaveBeenCalledTimes(1)   // solo la llamada inicial
  })
})
