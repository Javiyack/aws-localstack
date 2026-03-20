import { useEffect, useRef } from 'react'

/** Ejecuta `callback` inmediatamente y luego cada `intervalMs` ms.
 *  Se detiene automáticamente al desmontar el componente o cuando `enabled` es false.
 */
export function usePolling(callback: () => void, intervalMs: number, enabled = true) {
  const savedCallback = useRef(callback)

  useEffect(() => { savedCallback.current = callback }, [callback])

  useEffect(() => {
    if (!enabled) return
    savedCallback.current()
    const id = setInterval(() => savedCallback.current(), intervalMs)
    return () => clearInterval(id)
  }, [intervalMs, enabled])
}
