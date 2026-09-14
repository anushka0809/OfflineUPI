import { useState, useEffect, useCallback, useRef } from 'react'
import MeshPanel from './MeshPanel'
import {
  Lock, User, Shield, Send, History, LogOut, CheckCircle2, AlertTriangle,
  RefreshCw, XCircle, Search, ChevronLeft, ChevronRight, Download, BarChart3,
  Radio, Info, Wallet, Users, Receipt, Calendar, Home, IndianRupee, Plus, Trash2, Zap,
  X, ShieldCheck, Wifi, Lock as LockIcon, ArrowUpRight, ArrowDownLeft, Layers, FileText, Eye
} from 'lucide-react'

/* ── Types ── */
interface ApiResponse<T> { success: boolean; message: string; data: T }
interface JwtResponse { token: string; refreshToken: string; username: string; roles: string[] }
interface TokenRefreshResponse { accessToken: string; refreshToken: string }
interface WalletData { username: string; upiId: string; balance: number; monthlySpent: number; monthlyReceived: number }
interface MonthlySummary { month: string; totalSpent: number; totalReceived: number; transactionCount: number; categoryBreakdown: Record<string, number>; dailyActivity: Record<string, number> }
interface Transaction {
  id: number; transactionId: string; sender: string; receiver: string;
  amount: number; status: string; hopCount: number; createdAt: string;
  syncTime: string | null; failureReason: string | null;
  transactionType?: string; category?: string; note?: string;
  lifecycleState?: string; idempotencyKey?: string; bridgeNodeId?: string; meshHopPath?: string
}
interface BillReminder { id: number; title: string; amount: number; category: string; dueDay: number; active: boolean }
interface AdminStatsResponse {
  totalTransactions: number; pendingCount: number; syncedCount: number; failedCount: number;
  successRate: number; totalAmount: number;
  dailyTransactions: Record<string, number>; monthlyTransactions: Record<string, number>
}
interface Toast { id: number; message: string; type: 'success' | 'error' | 'info' }
type Tab = 'home' | 'send' | 'split' | 'bills' | 'history' | 'admin'

const CATEGORIES = ['FOOD', 'RENT', 'UTILITIES', 'SHOPPING', 'TRAVEL', 'ENTERTAINMENT', 'SPLIT', 'OTHER']

const BILL_ICONS: Record<string, string> = {
  UTILITIES: '💡', FOOD: '🍽️', RENT: '🏠', SHOPPING: '🛍️',
  TRAVEL: '✈️', ENTERTAINMENT: '🎬', OTHER: '📋'
}

function formatCurrency(amount: number) {
  return '₹' + amount.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function parseStoredRoles(): string[] {
  try { const r = localStorage.getItem('roles'); if (!r) return []; const p = JSON.parse(r); return Array.isArray(p) ? p : [] } catch { return [] }
}

async function parseJsonResponse<T>(res: Response): Promise<T | null> {
  const ct = res.headers.get('content-type') || ''
  if (!ct.includes('application/json')) return null
  try { return await res.json() as T } catch { return null }
}

function getBadgeClass(status: string) {
  const s = status.toLowerCase()
  if (s === 'synced') return 'badge badge-synced'
  if (s === 'pending') return 'badge badge-pending'
  if (s === 'waiting_for_sync') return 'badge badge-waiting'
  if (s === 'failed' || s === 'rejected' || s === 'tampered') return 'badge badge-failed'
  if (s === 'expired' || s === 'duplicate') return 'badge badge-waiting'
  return 'badge badge-pending'
}

function getBadgeLabel(status: string) {
  if (status === 'WAITING_FOR_SYNC') return 'Sync Pending'
  return status.charAt(0) + status.slice(1).toLowerCase()
}

/* ── Tx Detail Modal ── */
function TxDetailModal({ tx, onClose }: { tx: Transaction; onClose: () => void }) {
  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-card" onClick={e => e.stopPropagation()} style={{ maxWidth: 480 }}>
        <div className="modal-header">
          <div className="modal-title-row">
            <div className="modal-check">
              {tx.status === 'SYNCED' ? <CheckCircle2 size={20} /> : tx.status === 'FAILED' ? <XCircle size={20} style={{ color: 'var(--rose)' }} /> : <Radio size={20} style={{ color: 'var(--amber)' }} />}
            </div>
            <div>
              <div className="modal-title">Transaction Details</div>
              <div className="modal-subtitle">{new Date(tx.createdAt).toLocaleString()}</div>
            </div>
          </div>
          <button className="modal-close" onClick={onClose}><X size={18} /></button>
        </div>
        <div className="modal-body">
          <div className="conf-amount">
            <div className="conf-amount-val">{formatCurrency(tx.amount)}</div>
            <div className="conf-amount-label">{tx.sender} → {tx.receiver}</div>
          </div>
          <div className="tx-detail-rows">
            {[
              ['Transaction ID', <span className="mono-sm">{tx.transactionId}</span>],
              ['Status', <span className={getBadgeClass(tx.status)}>{getBadgeLabel(tx.status)}</span>],
              ['Type', tx.transactionType || 'TRANSFER'],
              ['Category', tx.category || '—'],
              ['Hops', tx.hopCount],
              ['Note', tx.note || '—'],
              ...(tx.lifecycleState ? [['Lifecycle State', <span className="mono-sm">{tx.lifecycleState}</span>]] : []),
              ...(tx.idempotencyKey ? [['Idempotency Key', <span className="mono-sm">{tx.idempotencyKey.substring(0, 20)}…</span>]] : []),
              ...(tx.bridgeNodeId ? [['Bridge Node', <span className="mono-sm">{tx.bridgeNodeId}</span>]] : []),
              ...(tx.meshHopPath ? [['Mesh Path', <span className="mono-sm">{tx.meshHopPath}</span>]] : []),
              ...(tx.syncTime ? [['Synced At', new Date(tx.syncTime).toLocaleString()]] : []),
              ...(tx.failureReason ? [['Failure Reason', <span style={{ color: 'var(--rose)' }}>{tx.failureReason}</span>]] : []),
            ].map(([k, v], i) => (
              <div key={i} className="tx-detail-row">
                <span className="tx-detail-key">{k as string}</span>
                <span className="tx-detail-val">{v as React.ReactNode}</span>
              </div>
            ))}
          </div>
        </div>
        <div className="modal-footer">
          <button className="btn btn-ghost" style={{ flex: 1 }} onClick={onClose}>Close</button>
        </div>
      </div>
    </div>
  )
}

/* ── Payment Confirm Modal ── */
function PaymentConfirmModal({ result, receiver, amount, onClose }: {
  result: { success: boolean; transactionId?: string; status?: string; hopCount?: number; payload?: string; idempotencyKey?: string; encryptedPayload?: string; lifecycleState?: string };
  receiver: string; amount: string; onClose: () => void
}) {
  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-card" onClick={e => e.stopPropagation()}>
        <div className="modal-header">
          <div className="modal-title-row">
            <div className="modal-check">
              {result.success ? <CheckCircle2 size={20} /> : <AlertTriangle size={20} style={{ color: 'var(--amber)' }} />}
            </div>
            <div>
              <div className="modal-title">{result.success ? 'Payment Saved' : 'Payment Status'}</div>
              <div className="modal-subtitle">{result.success ? 'Encrypted & stored offline' : 'Review details below'}</div>
            </div>
          </div>
          <button className="modal-close" onClick={onClose}><X size={18} /></button>
        </div>
        <div className="modal-body">
          <div className="conf-amount">
            <div className="conf-amount-val">{formatCurrency(parseFloat(amount) || 0)}</div>
            <div className="conf-amount-label">to {receiver}</div>
          </div>
          <div className="conf-rows">
            {result.transactionId && <div className="conf-row"><span className="conf-row-label">Transaction ID</span><span className="conf-row-val mono-sm">{result.transactionId.substring(0, 16)}…</span></div>}
            {result.status && <div className="conf-row"><span className="conf-row-label">Status</span><span className={getBadgeClass(result.status)}>{getBadgeLabel(result.status)}</span></div>}
            {result.lifecycleState && <div className="conf-row"><span className="conf-row-label">Lifecycle</span><span className="conf-row-val mono-sm">{result.lifecycleState}</span></div>}
            {result.hopCount !== undefined && <div className="conf-row"><span className="conf-row-label">Network Hops</span><span className="conf-row-val">{result.hopCount}</span></div>}
          </div>
          {result.success && (
            <>
              <hr className="conf-divider" />
              <div className="enc-box">
                <div className="enc-header"><ShieldCheck size={14} /> AES-256-GCM + RSA-OAEP Encrypted Payload</div>
                <div className="enc-payload">{result.encryptedPayload ? result.encryptedPayload.substring(0, 88) + '…' : 'unavailable'}</div>
              </div>
              {result.idempotencyKey && (
                <div className="enc-box" style={{ marginTop: 10 }}>
                  <div className="enc-header"><ShieldCheck size={14} /> Idempotency Key (SHA-256 of ciphertext)</div>
                  <div className="enc-payload">{result.idempotencyKey}</div>
                </div>
              )}
            </>
          )}
          {!result.success && result.payload && (
            <div style={{ marginTop: 14, padding: '10px 14px', background: 'var(--rose-dim)', borderRadius: 8, fontSize: 13, color: 'var(--rose)' }}>
              {result.payload}
            </div>
          )}
        </div>
        <div className="modal-footer">
          <button className="btn btn-violet" style={{ flex: 1 }} onClick={onClose}>Done</button>
        </div>
      </div>
    </div>
  )
}

/* ══════════════════════════════════════════
   MAIN APP
═══════════════════════════════════════════ */
function App() {
  const [token, setToken] = useState<string | null>(localStorage.getItem('token'))
  const [, setRefreshToken] = useState<string | null>(localStorage.getItem('refreshToken'))
  const [username, setUsername] = useState<string | null>(localStorage.getItem('username'))
  const [roles, setRoles] = useState<string[]>(parseStoredRoles())

  const [isLogin, setIsLogin] = useState(true)
  const [authUsername, setAuthUsername] = useState('')
  const [authPassword, setAuthPassword] = useState('')
  const [currentTab, setCurrentTab] = useState<Tab>('home')
  const [toasts, setToasts] = useState<Toast[]>([])

  const [wallet, setWallet] = useState<WalletData | null>(null)
  const [monthlySummary, setMonthlySummary] = useState<MonthlySummary | null>(null)
  const [allUsers, setAllUsers] = useState<string[]>([])

  const [receiver, setReceiver] = useState('')
  const [amount, setAmount] = useState('')
  const [category, setCategory] = useState('OTHER')
  const [note, setNote] = useState('')
  const [sendingPayment, setSendingPayment] = useState(false)
  const [paymentResult, setPaymentResult] = useState<{
    success: boolean; transactionId?: string; status?: string; hopCount?: number; payload?: string;
    idempotencyKey?: string; encryptedPayload?: string; lifecycleState?: string
  } | null>(null)
  const [showPayConfirm, setShowPayConfirm] = useState(false)
  const [lastReceiver, setLastReceiver] = useState('')
  const [lastAmount, setLastAmount] = useState('')

  const [splitAmount, setSplitAmount] = useState('')
  const [splitParticipants, setSplitParticipants] = useState('')
  const [splitDescription, setSplitDescription] = useState('')
  const [splitting, setSplitting] = useState(false)

  const [bills, setBills] = useState<BillReminder[]>([])
  const [billTitle, setBillTitle] = useState('')
  const [billAmount, setBillAmount] = useState('')
  const [billCategory, setBillCategory] = useState('UTILITIES')
  const [billDueDay, setBillDueDay] = useState('1')

  const [history, setHistory] = useState<Transaction[]>([])
  const [historyPage, setHistoryPage] = useState(0)
  const [historyTotalPages, setHistoryTotalPages] = useState(0)
  const [historyTotalElements, setHistoryTotalElements] = useState(0)
  const [filterStatus, setFilterStatus] = useState('')
  const [filterSearch, setFilterSearch] = useState('')
  const [loadingHistory, setLoadingHistory] = useState(false)
  const [selectedTx, setSelectedTx] = useState<Transaction | null>(null)

  const [stats, setStats] = useState<AdminStatsResponse | null>(null)
  const [syncing, setSyncing] = useState(false)
  const [loadingStats, setLoadingStats] = useState(false)

  const isAdmin = roles.includes('ROLE_ADMIN')
  const refreshPromiseRef = useRef<Promise<boolean> | null>(null)

  const addToast = useCallback((message: string, type: 'success' | 'error' | 'info' = 'info') => {
    const id = Date.now()
    setToasts(prev => [...prev, { id, message, type }])
    setTimeout(() => setToasts(prev => prev.filter(t => t.id !== id)), 4000)
  }, [])

  const clearAuthState = useCallback(() => {
    localStorage.removeItem('token'); localStorage.removeItem('refreshToken')
    localStorage.removeItem('username'); localStorage.removeItem('roles')
    setToken(null); setRefreshToken(null); setUsername(null); setRoles([]); setWallet(null)
  }, [])

  const fetchWithAuth = useCallback(async (url: string, options: RequestInit = {}): Promise<Response> => {
    const headers = new Headers(options.headers || {})
    const ct = localStorage.getItem('token')
    if (ct) headers.set('Authorization', `Bearer ${ct}`)
    const isGet = !options.method || options.method === 'GET' || options.method === 'HEAD'
    if (!isGet && !headers.has('Content-Type') && !(options.body instanceof FormData))
      headers.set('Content-Type', 'application/json')
    const response = await fetch(url, { ...options, headers })
    if (response.status !== 401) return response
    const rt = localStorage.getItem('refreshToken')
    if (!rt) { clearAuthState(); addToast('Session expired. Please log in again.', 'error'); throw new Error('Unauthorized') }
    if (!refreshPromiseRef.current) {
      refreshPromiseRef.current = (async () => {
        try {
          const rr = await fetch('/api/auth/refreshtoken', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken: rt }) })
          if (!rr.ok) return false
          const rd = await parseJsonResponse<ApiResponse<TokenRefreshResponse>>(rr)
          if (!rd?.data?.accessToken) return false
          localStorage.setItem('token', rd.data.accessToken); localStorage.setItem('refreshToken', rd.data.refreshToken)
          setToken(rd.data.accessToken); setRefreshToken(rd.data.refreshToken); return true
        } catch { return false } finally { refreshPromiseRef.current = null }
      })()
    }
    const refreshed = await refreshPromiseRef.current
    if (!refreshed) { clearAuthState(); addToast('Session expired. Please log in again.', 'error'); throw new Error('Unauthorized') }
    const nt = localStorage.getItem('token')
    if (nt) headers.set('Authorization', `Bearer ${nt}`)
    const retry = await fetch(url, { ...options, headers })
    if (retry.status === 401) { clearAuthState(); addToast('Session expired. Please log in again.', 'error'); throw new Error('Unauthorized') }
    return retry
  }, [addToast, clearAuthState])

  const fetchWallet = useCallback(async () => {
    try { const r = await fetchWithAuth('/api/wallet/balance'); const d = await parseJsonResponse<ApiResponse<WalletData>>(r); if (r.ok && d?.data) setWallet(d.data) } catch { }
  }, [fetchWithAuth])

  const fetchMonthlySummary = useCallback(async () => {
    try { const r = await fetchWithAuth('/api/wallet/monthly-summary'); const d = await parseJsonResponse<ApiResponse<MonthlySummary>>(r); if (r.ok && d?.data) setMonthlySummary(d.data) } catch { }
  }, [fetchWithAuth])

  const fetchUsers = useCallback(async () => {
    try { const r = await fetchWithAuth('/api/wallet/users'); const d = await parseJsonResponse<ApiResponse<string[]>>(r); if (r.ok && d?.data) setAllUsers(d.data.filter(u => u !== username)) } catch { }
  }, [fetchWithAuth, username])

  const fetchBills = useCallback(async () => {
    try { const r = await fetchWithAuth('/api/wallet/bills'); const d = await parseJsonResponse<ApiResponse<BillReminder[]>>(r); if (r.ok && d?.data) setBills(d.data) } catch { }
  }, [fetchWithAuth])

  const fetchHistory = useCallback(async (page = 0) => {
    setLoadingHistory(true)
    try {
      let url = `/api/payment/history?page=${page}&size=8&sort=createdAt,desc`
      if (filterStatus) url += `&status=${filterStatus}`
      if (filterSearch) url += `&search=${encodeURIComponent(filterSearch)}`
      const r = await fetchWithAuth(url)
      const d = await parseJsonResponse<ApiResponse<{ content: Transaction[]; number: number; totalPages: number; totalElements: number }>>(r)
      if (r.ok && d?.data) { setHistory(d.data.content); setHistoryPage(d.data.number); setHistoryTotalPages(d.data.totalPages); setHistoryTotalElements(d.data.totalElements) }
      else addToast('Failed to fetch history', 'error')
    } finally { setLoadingHistory(false) }
  }, [fetchWithAuth, filterStatus, filterSearch, addToast])

  const fetchStats = useCallback(async () => {
    if (!isAdmin) return
    setLoadingStats(true)
    try { const r = await fetchWithAuth('/api/payment/stats'); const d = await parseJsonResponse<ApiResponse<AdminStatsResponse>>(r); if (r.ok && d?.data) setStats(d.data); else addToast('Failed to fetch stats', 'error') }
    finally { setLoadingStats(false) }
  }, [fetchWithAuth, isAdmin, addToast])

  useEffect(() => { if (!token) return; fetchWallet(); fetchUsers() }, [token, fetchWallet, fetchUsers])
  useEffect(() => {
    if (!token) return
    if (currentTab === 'home') fetchMonthlySummary()
    else if (currentTab === 'history') fetchHistory(0)
    else if (currentTab === 'bills') fetchBills()
    else if (currentTab === 'admin' && isAdmin) fetchStats()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, currentTab, isAdmin])

  const handleAuth = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!authUsername || !authPassword) { addToast('Please fill in all fields', 'error'); return }
    try {
      const endpoint = isLogin ? '/api/auth/login' : '/api/auth/register'
      const r = await fetch(endpoint, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: authUsername, password: authPassword, role: 'USER' }) })
      const result = await parseJsonResponse<ApiResponse<JwtResponse | string>>(r)
      if (!result) { addToast('Server returned a non-JSON response', 'error'); return }
      if (isLogin) {
        if (r.ok && result.success) {
          const data = result.data as JwtResponse
          localStorage.setItem('token', data.token); localStorage.setItem('refreshToken', data.refreshToken)
          localStorage.setItem('username', data.username); localStorage.setItem('roles', JSON.stringify(data.roles))
          setToken(data.token); setRefreshToken(data.refreshToken); setUsername(data.username); setRoles(data.roles)
          addToast(`Welcome back, ${data.username}!`, 'success'); setAuthPassword('')
        } else addToast(result.message || 'Login failed', 'error')
      } else {
        if (r.ok && result.success) { addToast('Account created! You can now log in.', 'success'); setIsLogin(true); setAuthPassword('') }
        else addToast(result.message || 'Registration failed', 'error')
      }
    } catch (err) { addToast('Auth failed: ' + (err instanceof Error ? err.message : 'Unknown error'), 'error') }
  }

  const handleLogout = async () => {
    const rt = localStorage.getItem('refreshToken')
    try { if (rt) await fetch('/api/auth/logout', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken: rt }) }) } catch { }
    clearAuthState(); addToast('Logged out successfully', 'info')
  }

  const handleSendPayment = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!receiver || !amount) { addToast('Please fill in all fields', 'error'); return }
    const amt = parseFloat(amount)
    if (isNaN(amt) || amt <= 0) { addToast('Enter a valid amount', 'error'); return }
    setLastReceiver(receiver); setLastAmount(amount)
    setSendingPayment(true)
    try {
      const r = await fetchWithAuth('/api/payment/send', { method: 'POST', body: JSON.stringify({ receiver, amount: amt, category, note: note || undefined }) })
      const result = await parseJsonResponse<ApiResponse<{ transactionId: string; status: string; hopCount: number; idempotencyKey?: string; encryptedPayload?: string; lifecycleState?: string }>>(r)
      if (r.ok && result?.success && result.data) {
        setPaymentResult({
          success: true, transactionId: result.data.transactionId, status: result.data.status, hopCount: result.data.hopCount,
          idempotencyKey: result.data.idempotencyKey, encryptedPayload: result.data.encryptedPayload, lifecycleState: result.data.lifecycleState
        })
        addToast('Payment saved offline!', 'success')
        setReceiver(''); setAmount(''); setNote(''); fetchWallet()
      } else {
        setPaymentResult({ success: false, payload: result?.message || 'Payment failed' })
        addToast(result?.message || 'Payment failed', 'error')
      }
    } catch (err) {
      setPaymentResult({ success: false, payload: err instanceof Error ? err.message : 'Error' })
      addToast('Payment failed', 'error')
    } finally {
      setSendingPayment(false); setShowPayConfirm(true)
    }
  }

  const handleSplit = async (e: React.FormEvent) => {
    e.preventDefault()
    const total = parseFloat(splitAmount)
    if (isNaN(total) || total <= 0) { addToast('Enter a valid total amount', 'error'); return }
    const participants = splitParticipants.split(',').map(p => p.trim()).filter(Boolean)
    if (participants.length === 0) { addToast('Add at least one participant', 'error'); return }
    setSplitting(true)
    try {
      const r = await fetchWithAuth('/api/payment/split', { method: 'POST', body: JSON.stringify({ totalAmount: total, participants, description: splitDescription || 'Split expense', category: 'SPLIT' }) })
      const result = await parseJsonResponse<ApiResponse<{ sharePerPerson: number; participantCount: number; transactions: unknown[] }>>(r)
      if (r.ok && result?.success && result.data) {
        addToast(`Split done! Each person owes ${formatCurrency(result.data.sharePerPerson)}`, 'success')
        setSplitAmount(''); setSplitParticipants(''); setSplitDescription(''); fetchWallet()
      } else addToast(result?.message || 'Split failed', 'error')
    } catch (err) { addToast('Split failed: ' + (err instanceof Error ? err.message : 'Error'), 'error') }
    finally { setSplitting(false) }
  }

  const handleAddBill = async (e: React.FormEvent) => {
    e.preventDefault()
    const amt = parseFloat(billAmount)
    if (!billTitle || isNaN(amt) || amt <= 0) { addToast('Fill in bill title and amount', 'error'); return }
    try {
      const r = await fetchWithAuth('/api/wallet/bills', { method: 'POST', body: JSON.stringify({ title: billTitle, amount: amt, category: billCategory, dueDay: parseInt(billDueDay) }) })
      const result = await parseJsonResponse<ApiResponse<BillReminder>>(r)
      if (r.ok && result?.success) { addToast('Bill reminder added!', 'success'); setBillTitle(''); setBillAmount(''); fetchBills() }
      else addToast(result?.message || 'Failed to add bill', 'error')
    } catch (err) { addToast('Failed: ' + (err instanceof Error ? err.message : 'Error'), 'error') }
  }

  const handleDeleteBill = async (id: number) => {
    try {
      const r = await fetchWithAuth(`/api/wallet/bills/${id}`, { method: 'DELETE' })
      const result = await parseJsonResponse<ApiResponse<string>>(r)
      if (r.ok && result?.success) { addToast('Bill removed', 'info'); fetchBills() }
    } catch { }
  }

  const handleSync = async () => {
    setSyncing(true); addToast('Syncing offline transactions...', 'info')
    try {
      const r = await fetchWithAuth('/api/payment/sync', { method: 'POST' })
      const result = await parseJsonResponse<ApiResponse<{ syncedCount: number }>>(r)
      if (r.ok && result?.success) { addToast(`${result.data?.syncedCount ?? 0} transactions synced!`, 'success'); fetchStats(); fetchWallet() }
      else addToast(result?.message || 'Sync failed', 'error')
    } finally { setSyncing(false) }
  }

  const handleRetry = async (id: number) => {
    try {
      const r = await fetchWithAuth(`/api/payment/retry/${id}`, { method: 'POST' })
      const result = await parseJsonResponse<ApiResponse<unknown>>(r)
      if (r.ok && result?.success) { addToast('Transaction queued for retry', 'success'); fetchHistory(historyPage) }
      else addToast(result?.message || 'Retry failed', 'error')
    } catch { addToast('Action failed', 'error') }
  }

  const handleCancel = async (id: number) => {
    try {
      const r = await fetchWithAuth(`/api/payment/cancel/${id}`, { method: 'POST' })
      const result = await parseJsonResponse<ApiResponse<unknown>>(r)
      if (r.ok && result?.success) { addToast('Transaction cancelled', 'success'); fetchHistory(historyPage); fetchWallet() }
      else addToast(result?.message || 'Cancel failed', 'error')
    } catch { }
  }

  const handleDownload = async (format: 'csv' | 'excel' | 'pdf') => {
    const filename = `payment_history_${Date.now()}.${format === 'excel' ? 'xlsx' : format}`
    try {
      const r = await fetchWithAuth(`/api/payment/export/${format}`)
      if (!r.ok) throw new Error(`Server returned ${r.status}`)
      const blob = await r.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a'); a.href = url; a.download = filename
      document.body.appendChild(a); a.click(); document.body.removeChild(a); URL.revokeObjectURL(url)
      addToast(`${format.toUpperCase()} downloaded!`, 'success')
    } catch { addToast('Export failed', 'error') }
  }

  /* ── Toast ── */
  const Toasts = () => (
    <div className="toast-stack">
      {toasts.map(t => (
        <div key={t.id} className={`toast ${t.type}`}>
          {t.type === 'success' && <CheckCircle2 size={15} />}
          {t.type === 'error' && <AlertTriangle size={15} />}
          {t.type === 'info' && <Info size={15} />}
          <span>{t.message}</span>
        </div>
      ))}
    </div>
  )

  /* ── AUTH SCREEN ── */
  if (!token) {
    const heroFeatures = [
      { icon: <Wifi size={18} />, title: 'Works Offline', desc: 'Pay without internet using mesh networking.' },
      { icon: <Shield size={18} />, title: 'Secure Encryption', desc: 'End-to-end AES-256-GCM + RSA-OAEP.' },
      { icon: <Radio size={18} />, title: 'Peer-to-Peer Mesh', desc: 'Transactions travel across nearby devices.' },
      { icon: <ShieldCheck size={18} />, title: 'Exactly Once', desc: 'Idempotency protection prevents duplicates.' },
      { icon: <Users size={18} />, title: 'Split & Settle', desc: 'Easily split bills with friends.' },
      { icon: <RefreshCw size={18} />, title: 'Sync When Online', desc: 'Auto-settles with bank on reconnect.' },
    ]
    return (
      <div className="auth-root">
        {/* ── Left hero ── */}
        <div className="auth-hero">
          <div className="auth-hero-petals" />
          <div className="auth-hero-brand">
            <div className="auth-hero-logo"><Zap size={22} /></div>
            <div>
              <div className="auth-hero-appname">OfflineUPI</div>
              <div className="auth-hero-tagline">Pay Beyond Connectivity</div>
            </div>
          </div>
          <h1 className="auth-hero-heading">Offline Payments.<br />Real Connections.</h1>
          <p className="auth-hero-sub">Fast. Secure. Anywhere.</p>
          <div className="auth-features-grid">
            {heroFeatures.map((f, i) => (
              <div key={i} className="auth-feature-card">
                <div className="auth-feature-icon">{f.icon}</div>
                <div className="auth-feature-title">{f.title}</div>
                <div className="auth-feature-desc">{f.desc}</div>
              </div>
            ))}
          </div>
        </div>

        {/* ── Right card ── */}
        <div className="auth-panel">
          <div className="auth-card">
            <div className="auth-card-header">
              <div className="auth-card-title">{isLogin ? 'Welcome Back' : 'Create Account'}</div>
              <div className="auth-card-sub">{isLogin ? 'Sign in to continue' : 'Start your journey'}</div>
            </div>

            <form onSubmit={handleAuth}>
              <div className="form-group">
                <label className="form-label">Username</label>
                <div className="input-wrap">
                  <User size={15} className="input-icon-l" />
                  <input className="field" type="text" placeholder="Enter username"
                    value={authUsername} onChange={e => setAuthUsername(e.target.value)} autoComplete="username" required />
                </div>
              </div>
              <div className="form-group">
                <label className="form-label">Password</label>
                <div className="input-wrap">
                  <Lock size={15} className="input-icon-l" />
                  <input className="field" type="password" placeholder="••••••••"
                    value={authPassword} onChange={e => setAuthPassword(e.target.value)} autoComplete="current-password" required />
                </div>
              </div>
              {!isLogin && <div className="auth-hint-box">New accounts start with ₹10,000 wallet balance</div>}
              <button type="submit" className="btn btn-violet btn-full" style={{ marginTop: 4 }}>
                {isLogin ? 'Sign In' : 'Create Account'}
              </button>
            </form>

            <div className="auth-switch">
              {isLogin ? <>New here?<button className="auth-switch-btn" onClick={() => setIsLogin(false)}>Create account</button></>
                : <>Have an account?<button className="auth-switch-btn" onClick={() => setIsLogin(true)}>Sign in</button></>}
            </div>
            <div className="auth-demo">
              Demo: <strong>user</strong> / <strong>password</strong> &nbsp;·&nbsp; <strong>admin</strong> / <strong>password</strong>
            </div>
          </div>
        </div>

        <Toasts />
      </div>
    )
  }

  /* ── TABS ── */
  const tabs: { id: Tab; label: string; icon: React.ReactNode; adminOnly?: boolean }[] = [
    { id: 'home', label: 'Home', icon: <Home size={14} /> },
    { id: 'send', label: 'Pay', icon: <Send size={14} /> },
    { id: 'split', label: 'Split', icon: <Users size={14} /> },
    { id: 'bills', label: 'Bills', icon: <Receipt size={14} /> },
    { id: 'history', label: 'History', icon: <History size={14} /> },
    { id: 'admin', label: 'Admin', icon: <BarChart3 size={14} />, adminOnly: true },
  ]

  /* ── TODAY'S DUE CALCULATION ── */
  const today = new Date().getDate()

  return (
    <div className="app-shell">
      {/* NAVBAR */}
      <header className="navbar">
        <div className="navbar-brand" onClick={() => setCurrentTab('home')}>
          <div className="navbar-logo"><Zap size={16} /></div>
          <span className="navbar-name">OfflineUPI</span>
        </div>
        <div className="navbar-right">
          <div className="offline-pill">
            <span className="offline-dot" />
            Offline Ready
          </div>
          {wallet && (
            <div className="nav-balance">
              <IndianRupee size={13} />
              <span>{formatCurrency(wallet.balance)}</span>
            </div>
          )}
          <div className="nav-avatar-area">
            <div className="avatar">{username?.substring(0, 2).toUpperCase()}</div>
            <span className="nav-username">{username}</span>
            <span className={`role-chip ${isAdmin ? 'admin' : 'user'}`}>{isAdmin ? 'Admin' : 'User'}</span>
          </div>
          <button className="btn btn-ghost btn-sm" onClick={handleLogout} style={{ gap: 6 }}>
            <LogOut size={14} /> Sign out
          </button>
        </div>
      </header>

      <main className="page">
        {/* TABS */}
        <nav className="tabs">
          {tabs.filter(t => !t.adminOnly || isAdmin).map(tab => (
            <button key={tab.id} className={`tab ${currentTab === tab.id ? 'active' : ''}`}
              onClick={() => setCurrentTab(tab.id)}>
              {tab.icon} {tab.label}
            </button>
          ))}
        </nav>

        {/* ── HOME ── */}
        {currentTab === 'home' && (
          <div className="home-col">
            {/* Wallet card */}
            <div className="wallet-card">
              <div className="wallet-noise" />
              <div className="wallet-shine" />
              <div className="wallet-body">
                <div className="wallet-header"><Wallet size={16} /> My Wallet</div>
                <div className="wallet-bal">{wallet ? formatCurrency(wallet.balance) : '—'}</div>
                <div className="wallet-upi">{wallet?.upiId || ''}</div>
                <div className="wallet-stats">
                  <div>
                    <div className="wstat-label">Spent this month</div>
                    <div className="wstat-val spent">{formatCurrency(wallet?.monthlySpent ?? 0)}</div>
                  </div>
                  <div>
                    <div className="wstat-label">Received</div>
                    <div className="wstat-val received">{formatCurrency(wallet?.monthlyReceived ?? 0)}</div>
                  </div>
                </div>
                <div className="wallet-info">
                  <div className="wallet-info-item">
                    <span className="wallet-info-label">Last Sync</span>
                    <span className="wallet-info-value">Just Now</span>
                  </div>

                  <div className="wallet-info-item">
                    <span className="wallet-info-label">Offline Status</span>
                    <span className="wallet-info-value online">🟢 Ready</span>
                  </div>

                  <div className="wallet-info-item">
                    <span className="wallet-info-label">Security</span>
                    <span className="wallet-info-value">AES-128</span>
                  </div>
                </div>
              </div>
            </div>

            {/* Quick actions */}
            <div className="quick-grid">
              {[
                { id: 'send' as Tab, label: 'Pay', icon: <Send size={20} />, color: '#e85d8a', bg: 'rgba(232,93,138,0.12)' },
                { id: 'split' as Tab, label: 'Split', icon: <Users size={20} />, color: '#9b5fc0', bg: 'rgba(155,95,192,0.12)' },
                { id: 'bills' as Tab, label: 'Bills', icon: <Receipt size={20} />, color: '#c9842a', bg: 'rgba(201,132,42,0.12)' },
                { id: 'history' as Tab, label: 'History', icon: <History size={20} />, color: '#1e9cb5', bg: 'rgba(30,156,181,0.12)' },
              ].map(qa => (
                <button key={qa.id} className="qa-btn" onClick={() => setCurrentTab(qa.id)}>
                  <div className="qa-icon" style={{ background: qa.bg, color: qa.color }}>{qa.icon}</div>
                  {qa.label}
                </button>
              ))}
            </div>

            {/* Monthly summary */}
            {monthlySummary && (
              <div className="glass panel">
                <div className="section-head">
                  <Calendar size={18} style={{ color: 'var(--pink-500)' }} />
                  <h2>{monthlySummary.month} Summary</h2>
                </div>
                <div className="summary-3">
                  <div className="sstat"><span className="sstat-label">Transactions</span><span className="sstat-val">{monthlySummary.transactionCount}</span></div>
                  <div className="sstat"><span className="sstat-label">Total Spent</span><span className="sstat-val spent">{formatCurrency(monthlySummary.totalSpent)}</span></div>
                  <div className="sstat"><span className="sstat-label">Total Received</span><span className="sstat-val received">{formatCurrency(monthlySummary.totalReceived)}</span></div>
                </div>
                {Object.keys(monthlySummary.categoryBreakdown).length > 0 && (
                  <>
                    <p style={{ fontSize: 12, color: 'var(--charcoal-400)', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.6px', marginBottom: 12 }}>Spending by category</p>
                    {Object.entries(monthlySummary.categoryBreakdown).map(([cat, amt]) => (
                      <div key={cat} className="cat-row">
                        <span className="cat-name">{cat}</span>
                        <div className="cat-track"><div className="cat-fill" style={{ width: `${Math.min(100, (amt / (monthlySummary.totalSpent || 1)) * 100)}%` }} /></div>
                        <span className="cat-amt">{formatCurrency(amt)}</span>
                      </div>
                    ))}
                  </>
                )}
              </div>
            )}
          </div>
        )}

        {/* ── SEND ── */}
        {currentTab === 'send' && (
          <div className="send-grid">
            <div className="glass panel">
              <div className="section-head"><Send size={18} className="section-icon" /><h2>Send Money</h2></div>
              <form onSubmit={handleSendPayment}>
                <div className="form-group">
                  <label className="form-label">From</label>
                  <div className="input-wrap"><User size={15} className="input-icon-l" /><input className="field" type="text" value={username || ''} disabled /></div>
                </div>
                <div className="form-group">
                  <label className="form-label">To</label>
                  <div className="input-wrap">
                    <User size={15} className="input-icon-l" />
                    <select className="field" value={receiver} onChange={e => setReceiver(e.target.value)} required>
                      <option value="">Select recipient</option>
                      {allUsers.map(u => <option key={u} value={u}>{u}</option>)}
                    </select>
                  </div>
                </div>
                <div className="form-group">
                  <label className="form-label">Amount (₹)</label>
                  <div className="input-wrap"><IndianRupee size={15} className="input-icon-l" /><input className="field" type="number" placeholder="0.00" min="1" step="0.01" value={amount} onChange={e => setAmount(e.target.value)} required /></div>
                </div>
                <div className="form-group">
                  <label className="form-label">Category</label>
                  <div className="input-wrap">
                    <Layers size={15} className="input-icon-l" />
                    <select className="field" value={category} onChange={e => setCategory(e.target.value)}>
                      {CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
                    </select>
                  </div>
                </div>
                <div className="form-group">
                  <label className="form-label">Note (optional)</label>
                  <input className="field field-plain" type="text" placeholder="What's this for?" value={note} onChange={e => setNote(e.target.value)} />
                </div>
                <button type="submit" className="btn btn-violet btn-full" disabled={sendingPayment}>
                  {sendingPayment ? <><RefreshCw size={15} className="spinner" /> Processing…</> : <><Send size={15} /> Pay Now</>}
                </button>
              </form>
            </div>

            {/* Security panel */}
            <div className="glass panel">
              <div className="section-head"><ShieldCheck size={18} style={{ color: 'var(--pink-500)' }} /><h2>Security</h2></div>
              <p style={{ fontSize: 13, color: 'var(--charcoal-400)', marginBottom: 18 }}>
                Transactions are AES-256 encrypted before being stored locally, then synced when connectivity returns.
              </p>
              <div className="security-idle">
                <div className="sec-icon-ring"><Shield size={26} /></div>
                <span style={{ fontSize: 13.5, fontWeight: 700, color: 'var(--charcoal-500)' }}>End-to-end encrypted</span>
                <div className="sec-features">
                  {[
                    { icon: <LockIcon size={14} />, label: 'AES-256 payload encryption' },
                    { icon: <Wifi size={14} />, label: 'Works fully offline' },
                    { icon: <ShieldCheck size={14} />, label: 'Auto-sync on reconnect' },
                    { icon: <FileText size={14} />, label: 'Immutable audit trail' },
                  ].map((f, i) => (
                    <div key={i} className="sec-feat">{f.icon}<span>{f.label}</span></div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* ── SPLIT ── */}
        {currentTab === 'split' && (
          <div className="split-wrap glass panel">
            <div className="section-head"><Users size={18} style={{ color: 'var(--violet)' }} /><h2>Split Expense</h2></div>
            <p style={{ fontSize: 13, color: 'var(--charcoal-400)', marginBottom: 20 }}>
              Split a bill equally. Each person's share is deducted from their wallet.
            </p>
            <form onSubmit={handleSplit}>
              <div className="form-group">
                <label className="form-label">Total Amount (₹)</label>
                <div className="input-wrap"><IndianRupee size={15} className="input-icon-l" /><input className="field" type="number" placeholder="1200" min="1" step="0.01" value={splitAmount} onChange={e => setSplitAmount(e.target.value)} required /></div>
              </div>
              <div className="form-group">
                <label className="form-label">Participants (comma-separated usernames)</label>
                <input className="field field-plain" type="text" placeholder="e.g. bob, charlie" value={splitParticipants} onChange={e => setSplitParticipants(e.target.value)} required />
                <p className="field-hint">Available: {allUsers.join(', ') || 'loading…'}</p>
              </div>
              <div className="form-group">
                <label className="form-label">Description</label>
                <input className="field field-plain" type="text" placeholder="Dinner at restaurant" value={splitDescription} onChange={e => setSplitDescription(e.target.value)} />
              </div>
              {splitAmount && splitParticipants && (
                <div className="split-preview">
                  Each person pays: <strong>{formatCurrency(parseFloat(splitAmount) / (splitParticipants.split(',').filter(Boolean).length + 1))}</strong>
                </div>
              )}
              <button type="submit" className="btn btn-violet btn-full" disabled={splitting}>
                {splitting ? <><RefreshCw size={15} className="spinner" /> Splitting…</> : <><Users size={15} /> Split Expense</>}
              </button>
            </form>
          </div>
        )}

        {/* ── BILLS ── */}
        {currentTab === 'bills' && (
          <div className="bills-grid">
            <div className="glass panel">
              <div className="section-head"><Plus size={18} style={{ color: 'var(--green)' }} /><h2>Add Bill Reminder</h2></div>
              <form onSubmit={handleAddBill}>
                <div className="form-group">
                  <label className="form-label">Bill Title</label>
                  <input className="field field-plain" type="text" placeholder="Electricity Bill" value={billTitle} onChange={e => setBillTitle(e.target.value)} required />
                </div>
                <div className="form-row">
                  <div className="form-group">
                    <label className="form-label">Amount (₹)</label>
                    <input className="field field-plain" type="number" placeholder="1500" min="1" value={billAmount} onChange={e => setBillAmount(e.target.value)} required />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Due Day</label>
                    <input className="field field-plain" type="number" min="1" max="28" value={billDueDay} onChange={e => setBillDueDay(e.target.value)} />
                  </div>
                </div>
                <div className="form-group">
                  <label className="form-label">Category</label>
                  <select className="field field-plain" value={billCategory} onChange={e => setBillCategory(e.target.value)}>
                    {CATEGORIES.filter(c => c !== 'SPLIT').map(c => <option key={c} value={c}>{c}</option>)}
                  </select>
                </div>
                <button type="submit" className="btn btn-violet btn-full">Add Reminder</button>
              </form>
            </div>

            <div className="glass panel">
              <div className="section-head"><Receipt size={18} style={{ color: 'var(--amber)' }} /><h2>Monthly Bills</h2></div>
              {bills.length === 0 ? (
                <div className="bills-empty">
                  <Receipt size={36} />
                  <span style={{ fontSize: 14 }}>No bills yet</span>
                  <span style={{ fontSize: 13, opacity: 0.6 }}>Add a reminder to track recurring payments</span>
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  {bills.map(bill => {
                    const daysUntil = bill.dueDay >= today ? bill.dueDay - today : (28 - today + bill.dueDay)
                    const soon = daysUntil <= 5
                    return (
                      <div key={bill.id} className="bill-item">
                        <div className="bill-left">
                          <div className="bill-icon" style={{ background: soon ? 'rgba(251,191,36,0.12)' : 'rgba(255,255,255,0.05)' }}>
                            {BILL_ICONS[bill.category] || '📋'}
                          </div>
                          <div>
                            <div className="bill-name">{bill.title}</div>
                            <div className="bill-meta">{bill.category} · Due day {bill.dueDay}</div>
                          </div>
                        </div>
                        <div className="bill-right">
                          <span className={`due-chip ${soon ? 'soon' : 'ok'}`}>{soon ? `${daysUntil}d` : `in ${daysUntil}d`}</span>
                          <span className="bill-amount">{formatCurrency(bill.amount)}</span>
                          <button className="btn btn-danger-ghost" onClick={() => handleDeleteBill(bill.id)}><Trash2 size={13} /></button>
                        </div>
                      </div>
                    )
                  })}
                </div>
              )}
            </div>
          </div>
        )}

        {/* ── HISTORY ── */}
        {currentTab === 'history' && (
          <div className="glass panel history-wrap">
            <div className="history-top">
              <div className="section-head" style={{ marginBottom: 0 }}>
                <History size={18} className="section-icon" /><h2>Transaction History</h2>
              </div>
              <div className="export-group">
                {(['csv', 'excel', 'pdf'] as const).map(fmt => (
                  <button key={fmt} className="btn btn-ghost btn-sm" onClick={() => handleDownload(fmt)}>
                    <Download size={13} /> {fmt.toUpperCase()}
                  </button>
                ))}
              </div>
            </div>
            <div className="filter-row" style={{ marginTop: 18 }}>
              <div className="search-wrap">
                <Search size={14} className="input-icon-l" />
                <input className="search-field" type="text" placeholder="Search transactions…" value={filterSearch} onChange={e => setFilterSearch(e.target.value)} />
              </div>
              <select className="status-select" value={filterStatus} onChange={e => setFilterStatus(e.target.value)}>
                <option value="">All statuses</option>
                <option value="PENDING">Pending</option>
                <option value="WAITING_FOR_SYNC">Waiting for sync</option>
                <option value="SYNCED">Synced</option>
                <option value="FAILED">Failed</option>
                <option value="REJECTED">Rejected</option>
              </select>
              <button className="btn btn-violet btn-sm" onClick={() => fetchHistory(0)}>Apply</button>
            </div>

            <div className="tbl-wrap">
              {loadingHistory ? (
                <div className="empty-state"><RefreshCw size={22} className="spinner" /><span className="empty-label">Loading…</span></div>
              ) : history.length === 0 ? (
                <div className="empty-state">
                  <History size={32} />
                  <span className="empty-label">No transactions found</span>
                  <span className="empty-sub">Try adjusting your filters</span>
                </div>
              ) : (
                <table className="tbl">
                  <thead>
                    <tr>
                      <th>ID</th><th>From</th><th>To</th><th>Amount</th><th>Type</th><th>Status</th><th>Date</th><th></th>
                    </tr>
                  </thead>
                  <tbody>
                    {history.map(tx => (
                      <tr key={tx.id} onClick={() => setSelectedTx(tx)}>
                        <td><span className="tx-id">{tx.transactionId.substring(0, 8)}…</span></td>
                        <td style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                          <ArrowUpRight size={13} style={{ color: 'var(--rose)', flexShrink: 0 }} />{tx.sender}
                        </td>
                        <td style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                          <ArrowDownLeft size={13} style={{ color: 'var(--green)', flexShrink: 0 }} />{tx.receiver}
                        </td>
                        <td><span className="tx-amount">{formatCurrency(tx.amount)}</span></td>
                        <td><span className="type-chip">{tx.transactionType || 'TRANSFER'}</span></td>
                        <td><span className={getBadgeClass(tx.status)}>{getBadgeLabel(tx.status)}</span></td>
                        <td><span className="tx-date">{new Date(tx.createdAt).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })}</span></td>
                        <td onClick={e => e.stopPropagation()}>
                          <div className="tx-actions">
                            <button className="btn-tbl" style={{ color: 'var(--text-muted)', borderColor: 'var(--border)' }} onClick={() => setSelectedTx(tx)}><Eye size={12} /></button>
                            {tx.status === 'FAILED' && <button className="btn-tbl btn-tbl-success" onClick={() => handleRetry(tx.id)}>Retry</button>}
                            {(tx.status === 'PENDING' || tx.status === 'WAITING_FOR_SYNC') && <button className="btn-tbl btn-tbl-danger" onClick={() => handleCancel(tx.id)}>Cancel</button>}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
            {historyTotalPages > 1 && (
              <div className="pager">
                <span className="pager-info">Page {historyPage + 1} of {historyTotalPages} · {historyTotalElements} records</span>
                <div className="pager-btns">
                  <button className="btn btn-ghost btn-sm" disabled={historyPage === 0} onClick={() => fetchHistory(historyPage - 1)}><ChevronLeft size={15} /></button>
                  <button className="btn btn-ghost btn-sm" disabled={historyPage >= historyTotalPages - 1} onClick={() => fetchHistory(historyPage + 1)}><ChevronRight size={15} /></button>
                </div>
              </div>
            )}
          </div>
        )}

        {/* ── ADMIN ── */}
        {currentTab === 'admin' && isAdmin && (
          <div>
            <div className="glass admin-sync-bar">
              <div className="sync-info">
                <h3><Radio size={16} style={{ display: 'inline', marginRight: 8, color: 'var(--pink-500)' }} />Sync Console</h3>
                <p>Process all pending offline transactions when connectivity is restored.</p>
              </div>
              <button onClick={handleSync} disabled={syncing} className={`btn btn-violet ${!syncing ? 'btn-sync-pulse' : ''}`}>
                {syncing ? <><RefreshCw size={15} className="spinner" /> Syncing…</> : <><RefreshCw size={15} /> Sync All</>}
              </button>
            </div>

            {loadingStats ? (
              <div className="empty-state"><RefreshCw size={22} className="spinner" /><span className="empty-label">Loading stats…</span></div>
            ) : stats ? (
              <>
                <div className="metrics-grid">
                  {[
                    { label: 'Total', value: stats.totalTransactions, icon: <History size={14} />, color: 'var(--charcoal-800)' },
                    { label: 'Pending', value: stats.pendingCount, icon: <Radio size={14} />, color: 'var(--amber)' },
                    { label: 'Synced', value: stats.syncedCount, icon: <CheckCircle2 size={14} />, color: 'var(--green)' },
                    { label: 'Failed', value: stats.failedCount, icon: <XCircle size={14} />, color: 'var(--rose)' },
                  ].map(m => (
                    <div key={m.label} className="glass metric-card">
                      <div className="metric-label"><span>{m.label}</span><span style={{ color: m.color || 'var(--charcoal-400)' }}>{m.icon}</span></div>
                      <div className="metric-val" style={{ color: m.color || 'var(--charcoal-800)' }}>{m.value}</div>
                    </div>
                  ))}
                  <div className="glass metric-card span-2">
                    <div className="metric-label"><span>Total Settled</span><IndianRupee size={14} /></div>
                    <div className="metric-val">{formatCurrency(stats.totalAmount)}</div>
                  </div>
                  <div className="glass metric-card">
                    <div className="metric-label"><span>Success Rate</span><Shield size={14} /></div>
                    <div className="metric-val" style={{ color: stats.successRate >= 80 ? 'var(--green)' : 'var(--amber)' }}>{stats.successRate.toFixed(1)}%</div>
                    <div className="metric-bar"><div className="metric-bar-fill" style={{ width: `${stats.successRate}%`, background: stats.successRate >= 80 ? 'var(--green)' : 'var(--amber)' }} /></div>
                  </div>
                </div>

                {Object.keys(stats.dailyTransactions).length > 0 && (
                  <div className="glass chart-wrap">
                    <div className="section-head"><BarChart3 size={18} style={{ color: 'var(--pink-500)' }} /><h2>Daily Activity</h2></div>
                    <div className="bar-chart">
                      {Object.entries(stats.dailyTransactions).slice(-7).map(([day, count]) => (
                        <div key={day} className="bar-item">
                          <div className="bar-fill" style={{ height: `${Math.max(6, (count as number) * 18)}px` }} />
                          <span className="bar-lbl">{day.slice(5)}</span>
                          <span className="bar-cnt">{count as number}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </>
            ) : (
              <div className="empty-state"><Info size={28} /><span className="empty-label">Failed to load stats</span></div>
            )}

            <MeshPanel fetchWithAuth={fetchWithAuth} addToast={addToast} />
          </div>
        )}
      </main>

      {/* Modals */}
      {showPayConfirm && paymentResult && (
        <PaymentConfirmModal
          result={paymentResult}
          receiver={lastReceiver}
          amount={lastAmount}
          onClose={() => { setShowPayConfirm(false); setPaymentResult(null) }}
        />
      )}
      {selectedTx && <TxDetailModal tx={selectedTx} onClose={() => setSelectedTx(null)} />}

      <Toasts />
    </div>
  )
}

export default App
