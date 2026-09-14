import { useCallback, useEffect, useState } from 'react'
import { Radio, RefreshCw, Share2, Smartphone, Wifi, ShieldCheck, Info } from 'lucide-react'

/* ── Types (kept local to this file — mirrors the backend DTOs in
   com.upi.offline.dto.{MeshStateResponse,MeshDeviceStatus,GossipRoundResponse,BridgeSyncResponse}) ── */
interface MeshDeviceStatus { deviceId: string; bridge: boolean; packetCount: number }
interface MeshStateResponse {
  devices: MeshDeviceStatus[]; totalPacketsInFlight: number;
  idempotencyLocalCacheSize: number; serverPublicKeyFingerprint: string
}
interface GossipRoundResponse { transfers: number; duplicatesSuppressed: number; deviceCounts: Record<string, number> }
interface BridgeSyncResult { bridgeNodeId: string; idempotencyKey: string; outcome: string; reason: string | null; transactionId: number | null }
interface BridgeSyncResponse { packetsConsidered: number; settled: number; duplicates: number; rejected: number; results: BridgeSyncResult[] }
interface ApiResponse<T> { success: boolean; message: string; data: T }

async function parseJson<T>(res: Response): Promise<T | null> {
  const ct = res.headers.get('content-type') || ''
  if (!ct.includes('application/json')) return null
  try { return await res.json() as T } catch { return null }
}

/**
 * Visualizes the simulated offline device mesh (see MeshNetworkService on the
 * backend): which simulated phones currently hold which encrypted payment
 * packets, and lets an admin manually advance the simulation — run a gossip
 * propagation round, or flush every bridge-capable node's held packets to
 * the real settlement pipeline (OfflineIngestionService) to demonstrate
 * exactly-once settlement even when the same packet reaches the backend via
 * multiple bridge nodes at once.
 */
export default function MeshPanel({ fetchWithAuth, addToast }: {
  fetchWithAuth: (url: string, options?: RequestInit) => Promise<Response>
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void
}) {
  const [state, setState] = useState<MeshStateResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [gossiping, setGossiping] = useState(false)
  const [flushing, setFlushing] = useState(false)
  const [resetting, setResetting] = useState(false)
  const [lastGossip, setLastGossip] = useState<GossipRoundResponse | null>(null)
  const [lastFlush, setLastFlush] = useState<BridgeSyncResponse | null>(null)

  const fetchState = useCallback(async () => {
    setLoading(true)
    try {
      const r = await fetchWithAuth('/api/mesh/state')
      const d = await parseJson<ApiResponse<MeshStateResponse>>(r)
      if (r.ok && d?.data) setState(d.data)
      else addToast('Failed to fetch mesh state', 'error')
    } catch {
      addToast('Failed to fetch mesh state', 'error')
    } finally {
      setLoading(false)
    }
  }, [fetchWithAuth, addToast])

  useEffect(() => { fetchState() }, [fetchState])

  const runGossip = async () => {
    setGossiping(true)
    try {
      const r = await fetchWithAuth('/api/mesh/gossip', { method: 'POST' })
      const d = await parseJson<ApiResponse<GossipRoundResponse>>(r)
      if (r.ok && d?.data) {
        setLastGossip(d.data)
        addToast(`Gossip round: ${d.data.transfers} transfers, ${d.data.duplicatesSuppressed} duplicates suppressed`, 'success')
        await fetchState()
      } else {
        addToast(d?.message || 'Gossip round failed', 'error')
      }
    } catch {
      addToast('Gossip round failed', 'error')
    } finally {
      setGossiping(false)
    }
  }

  const flushBridge = async () => {
    setFlushing(true)
    try {
      const r = await fetchWithAuth('/api/mesh/flush-bridge', { method: 'POST' })
      const d = await parseJson<ApiResponse<BridgeSyncResponse>>(r)
      if (r.ok && d?.data) {
        setLastFlush(d.data)
        const { settled, duplicates, rejected } = d.data
        addToast(`Bridge sync: ${settled} settled, ${duplicates} duplicates, ${rejected} rejected`, settled > 0 ? 'success' : 'info')
        await fetchState()
      } else {
        addToast(d?.message || 'Bridge sync failed', 'error')
      }
    } catch {
      addToast('Bridge sync failed', 'error')
    } finally {
      setFlushing(false)
    }
  }

  const resetMesh = async () => {
    setResetting(true)
    try {
      const r = await fetchWithAuth('/api/mesh/reset', { method: 'POST' })
      if (r.ok) {
        addToast('Mesh state reset', 'info')
        setLastGossip(null); setLastFlush(null)
        await fetchState()
      } else {
        addToast('Mesh reset failed', 'error')
      }
    } catch {
      addToast('Mesh reset failed', 'error')
    } finally {
      setResetting(false)
    }
  }

  return (
    <div className="glass admin-sync-bar" style={{ marginTop: 20, flexDirection: 'column', alignItems: 'stretch', gap: 16 }}>
      <div className="section-head" style={{ marginBottom: 0 }}>
        <Share2 size={18} className="section-icon" />
        <h2>Offline Mesh Network</h2>
      </div>
      <p style={{ color: 'var(--text-secondary)', fontSize: 13, margin: 0 }}>
        Simulated phone-to-phone gossip: encrypted packets hop between offline devices until a
        bridge-capable node regains connectivity and uploads them for settlement.
      </p>

      {loading && !state ? (
        <div className="empty-state"><RefreshCw size={20} className="spinner" /><span className="empty-label">Loading mesh state…</span></div>
      ) : state ? (
        <>
          <div className="metrics-grid">
            <div className="glass metric-card">
              <div className="metric-label"><span>Devices</span><Smartphone size={14} /></div>
              <div className="metric-val">{state.devices.length}</div>
            </div>
            <div className="glass metric-card">
              <div className="metric-label"><span>Packets In Flight</span><Radio size={14} /></div>
              <div className="metric-val">{state.totalPacketsInFlight}</div>
            </div>
            <div className="glass metric-card">
              <div className="metric-label"><span>Local Idempotency Cache</span><ShieldCheck size={14} /></div>
              <div className="metric-val">{state.idempotencyLocalCacheSize}</div>
            </div>
          </div>

          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
            {state.devices.map(d => (
              <div key={d.deviceId} className="glass" style={{ padding: '10px 14px', borderRadius: 10, minWidth: 140, display: 'flex', alignItems: 'center', gap: 8 }}>
                {d.bridge ? <Wifi size={16} style={{ color: 'var(--green)' }} /> : <Smartphone size={16} style={{ color: 'var(--text-muted)' }} />}
                <div>
                  <div style={{ fontSize: 12.5, fontWeight: 600 }}>{d.deviceId}</div>
                  <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                    {d.bridge ? 'Bridge (online)' : 'Offline relay'} · {d.packetCount} packet{d.packetCount === 1 ? '' : 's'}
                  </div>
                </div>
              </div>
            ))}
          </div>

          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
            <button className="btn btn-violet" onClick={runGossip} disabled={gossiping}>
              {gossiping ? <><RefreshCw size={15} className="spinner" /> Gossiping…</> : <><Share2 size={15} /> Run Gossip Round</>}
            </button>
            <button className="btn btn-violet" onClick={flushBridge} disabled={flushing}>
              {flushing ? <><RefreshCw size={15} className="spinner" /> Syncing…</> : <><Wifi size={15} /> Flush Bridge to Backend</>}
            </button>
            <button className="btn btn-ghost" onClick={resetMesh} disabled={resetting}>
              {resetting ? <><RefreshCw size={15} className="spinner" /> Resetting…</> : 'Reset Mesh'}
            </button>
            <button className="btn btn-ghost btn-sm" onClick={fetchState} disabled={loading}>
              <RefreshCw size={13} /> Refresh
            </button>
          </div>

          {lastGossip && (
            <div className="glass" style={{ padding: '10px 14px', borderRadius: 10, fontSize: 12.5 }}>
              <strong>Last gossip round:</strong> {lastGossip.transfers} transfer{lastGossip.transfers === 1 ? '' : 's'},{' '}
              {lastGossip.duplicatesSuppressed} duplicate{lastGossip.duplicatesSuppressed === 1 ? '' : 's'} suppressed
              (TTL + per-node duplicate suppression prevent packets circulating forever).
            </div>
          )}

          {lastFlush && (
            <div className="glass" style={{ padding: '10px 14px', borderRadius: 10, fontSize: 12.5 }}>
              <strong>Last bridge sync:</strong> {lastFlush.packetsConsidered} packet{lastFlush.packetsConsidered === 1 ? '' : 's'} considered
              → <span style={{ color: 'var(--green)' }}>{lastFlush.settled} settled</span>,{' '}
              <span style={{ color: 'var(--amber)' }}>{lastFlush.duplicates} duplicate{lastFlush.duplicates === 1 ? '' : 's'}</span>,{' '}
              <span style={{ color: 'var(--rose)' }}>{lastFlush.rejected} rejected</span>.
              {lastFlush.duplicates > 0 && (
                <div style={{ marginTop: 6, color: 'var(--text-muted)' }}>
                  Duplicates prove the idempotency guarantee: the same encrypted packet reached the
                  backend more than once, but the wallet was only ever credited by the first delivery.
                </div>
              )}
            </div>
          )}
        </>
      ) : (
        <div className="empty-state"><Info size={22} /><span className="empty-label">Mesh state unavailable</span></div>
      )}
    </div>
  )
}
