import { useState } from 'react'
import { putRecord } from '@/api/kinesis'
import type { InputMessage } from '@/types/messages'

const TEMPLATES: Record<'registration' | 'baseline', InputMessage> = {
  registration: {
    nodeId:         'node-001',
    dttmUtc:        new Date().toISOString(),
    registrationId: 'reg-001'
  },
  baseline: {
    nodeId:    'node-001',
    dttmUtc:   new Date().toISOString(),
    baselineId:'base-001'
  }
}

export function StreamWriter() {
  const [json, setJson]         = useState(JSON.stringify(TEMPLATES.registration, null, 2))
  const [sending, setSending]   = useState(false)
  const [feedback, setFeedback] = useState<{ type: 'ok' | 'error'; msg: string } | null>(null)

  const setTemplate = (t: keyof typeof TEMPLATES) =>
    setJson(JSON.stringify({ ...TEMPLATES[t], dttmUtc: new Date().toISOString() }, null, 2))

  const send = async () => {
    try {
      setSending(true)
      setFeedback(null)
      const message: InputMessage = JSON.parse(json) as InputMessage
      if (!!message.registrationId === !!message.baselineId) {
        throw new Error('El mensaje debe tener exactamente uno de: registrationId o baselineId')
      }
      await putRecord('input-stream', message)
      setFeedback({ type: 'ok', msg: 'Mensaje publicado correctamente' })
    } catch (e) {
      setFeedback({ type: 'error', msg: e instanceof Error ? e.message : 'Error desconocido' })
    } finally {
      setSending(false)
    }
  }

  return (
    <div className="space-y-3">
      <div className="flex gap-2">
        <button onClick={() => setTemplate('registration')} className="btn-secondary text-xs">
          Template: Registration
        </button>
        <button onClick={() => setTemplate('baseline')} className="btn-secondary text-xs">
          Template: Baseline
        </button>
      </div>

      <textarea
        value={json}
        onChange={e => setJson(e.target.value)}
        rows={12}
        className="w-full font-mono text-sm border rounded p-3 bg-gray-50"
        spellCheck={false}
      />

      {feedback && (
        <p className={`text-sm ${feedback.type === 'ok' ? 'text-green-700' : 'text-red-700'}`}>
          {feedback.type === 'ok' ? '✓' : '✗'} {feedback.msg}
        </p>
      )}

      <button onClick={send} disabled={sending} className="btn-primary">
        {sending ? 'Enviando…' : '➤ Publicar en input-stream'}
      </button>
    </div>
  )
}
