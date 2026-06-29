import { useState, useEffect, useCallback, useRef } from 'react'
import {
  Lock, User, Shield, Send, History, LogOut, CheckCircle2, AlertTriangle,
  RefreshCw, XCircle, Search, ChevronLeft, ChevronRight, Download, BarChart3,
  Radio, Info, Wallet, Users, Receipt, Calendar, Home, IndianRupee, Plus, Trash2, Zap
} from 'lucide-react'

interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
}

interface JwtResponse {
  token: string
  refreshToken: string
  username: string
  roles: string[]
}

interface TokenRefreshResponse {
  accessToken: string
  refreshToken: string
}

interface WalletData {
  username: string
  upiId: string
  balance: number
  monthlySpent: number
  monthlyReceived: number
}

interface MonthlySummary {
  month: string
  totalSpent: number
  totalReceived: number
  transactionCount: number
  categoryBreakdown: Record<string, number>
  dailyActivity: Record<string, number>
}

interface Transaction {
  id: number
  transactionId: string
  sender: string
  receiver: string
  amount: number
  status: string
  hopCount: number
  createdAt: string
  syncTime: string | null
  failureReason: string | null
  transactionType?: string
  category?: string
  note?: string
}

interface BillReminder {
  id: number
  title: string
  amount: number
  category: string
  dueDay: number
  active: boolean
}

interface AdminStatsResponse {
  totalTransactions: number
  pendingCount: number
  syncedCount: number
  failedCount: number
  successRate: number
  totalAmount: number
  dailyTransactions: Record<string, number>
  monthlyTransactions: Record<string, number>
}

interface Toast {
  id: number
  message: string
  type: 'success' | 'error' | 'info'
}

type Tab = 'home' | 'send' | 'split' | 'bills' | 'history' | 'admin'

const CATEGORIES = ['FOOD', 'RENT', 'UTILITIES', 'SHOPPING', 'TRAVEL', 'ENTERTAINMENT', 'SPLIT', 'OTHER']

function formatCurrency(amount: number): string {
  return '₹' + amount.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function parseStoredRoles(): string[] {
  try {
    const raw = localStorage.getItem('roles')
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

async function parseJsonResponse<T>(res: Response): Promise<T | null> {
  const contentType = res.headers.get('content-type') || ''
  if (!contentType.includes('application/json')) return null
  try {
    return await res.json() as T
  } catch {
    return null
  }
}

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
  const [lastPaymentResult, setLastPaymentResult] = useState<{
    success: boolean; transactionId?: string; status?: string; hopCount?: number; payload?: string
  } | null>(null)

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
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('username')
    localStorage.removeItem('roles')
    setToken(null)
    setRefreshToken(null)
    setUsername(null)
    setRoles([])
    setWallet(null)
  }, [])

  const fetchWithAuth = useCallback(async (url: string, options: RequestInit = {}): Promise<Response> => {
    const headers = new Headers(options.headers || {})
    const currentToken = localStorage.getItem('token')
    if (currentToken) headers.set('Authorization', `Bearer ${currentToken}`)
    const isGetOrHead = !options.method || options.method === 'GET' || options.method === 'HEAD'
    if (!isGetOrHead && !headers.has('Content-Type') && !(options.body instanceof FormData)) {
      headers.set('Content-Type', 'application/json')
    }

    const response = await fetch(url, { ...options, headers })
    if (response.status !== 401) return response

    const storedRefreshToken = localStorage.getItem('refreshToken')
    if (!storedRefreshToken) {
      clearAuthState()
      addToast('Session expired. Please log in again.', 'error')
      throw new Error('Unauthorized')
    }

    if (!refreshPromiseRef.current) {
      refreshPromiseRef.current = (async () => {
        try {
          const refreshRes = await fetch('/api/auth/refreshtoken', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ refreshToken: storedRefreshToken })
          })
          if (!refreshRes.ok) return false
          const refreshData = await parseJsonResponse<ApiResponse<TokenRefreshResponse>>(refreshRes)
          if (!refreshData?.data?.accessToken) return false
          localStorage.setItem('token', refreshData.data.accessToken)
          localStorage.setItem('refreshToken', refreshData.data.refreshToken)
          setToken(refreshData.data.accessToken)
          setRefreshToken(refreshData.data.refreshToken)
          return true
        } catch {
          return false
        } finally {
          refreshPromiseRef.current = null
        }
      })()
    }

    const refreshed = await refreshPromiseRef.current
    if (!refreshed) {
      clearAuthState()
      addToast('Session expired. Please log in again.', 'error')
      throw new Error('Unauthorized')
    }

    const newToken = localStorage.getItem('token')
    if (newToken) headers.set('Authorization', `Bearer ${newToken}`)
    const retryResponse = await fetch(url, { ...options, headers })
    if (retryResponse.status === 401) {
      clearAuthState()
      addToast('Session expired. Please log in again.', 'error')
      throw new Error('Unauthorized')
    }
    return retryResponse
  }, [addToast, clearAuthState])

  const fetchWallet = useCallback(async () => {
    try {
      const res = await fetchWithAuth('/api/wallet/balance')
      const result = await parseJsonResponse<ApiResponse<WalletData>>(res)
      if (res.ok && result?.data) setWallet(result.data)
    } catch { /* handled by fetchWithAuth */ }
  }, [fetchWithAuth])

  const fetchMonthlySummary = useCallback(async () => {
    try {
      const res = await fetchWithAuth('/api/wallet/monthly-summary')
      const result = await parseJsonResponse<ApiResponse<MonthlySummary>>(res)
      if (res.ok && result?.data) setMonthlySummary(result.data)
    } catch { /* handled */ }
  }, [fetchWithAuth])

  const fetchUsers = useCallback(async () => {
    try {
      const res = await fetchWithAuth('/api/wallet/users')
      const result = await parseJsonResponse<ApiResponse<string[]>>(res)
      if (res.ok && result?.data) {
        setAllUsers(result.data.filter(u => u !== username))
      }
    } catch { /* handled */ }
  }, [fetchWithAuth, username])

  const fetchBills = useCallback(async () => {
    try {
      const res = await fetchWithAuth('/api/wallet/bills')
      const result = await parseJsonResponse<ApiResponse<BillReminder[]>>(res)
      if (res.ok && result?.data) setBills(result.data)
    } catch { /* handled */ }
  }, [fetchWithAuth])

  const fetchHistory = useCallback(async (page = 0) => {
    setLoadingHistory(true)
    try {
      let url = `/api/payment/history?page=${page}&size=8&sort=createdAt,desc`
      if (filterStatus) url += `&status=${filterStatus}`
      if (filterSearch) url += `&search=${encodeURIComponent(filterSearch)}`
      const res = await fetchWithAuth(url)
      const result = await parseJsonResponse<ApiResponse<{ content: Transaction[]; number: number; totalPages: number; totalElements: number }>>(res)
      if (res.ok && result?.data) {
        setHistory(result.data.content)
        setHistoryPage(result.data.number)
        setHistoryTotalPages(result.data.totalPages)
        setHistoryTotalElements(result.data.totalElements)
      } else {
        addToast('Failed to fetch transaction history', 'error')
      }
    } finally {
      setLoadingHistory(false)
    }
  }, [fetchWithAuth, filterStatus, filterSearch, addToast])

  const fetchStats = useCallback(async () => {
    if (!isAdmin) return
    setLoadingStats(true)
    try {
      const res = await fetchWithAuth('/api/payment/stats')
      const result = await parseJsonResponse<ApiResponse<AdminStatsResponse>>(res)
      if (res.ok && result?.data) setStats(result.data)
      else addToast('Failed to fetch admin stats', 'error')
    } finally {
      setLoadingStats(false)
    }
  }, [fetchWithAuth, isAdmin, addToast])

  useEffect(() => {
    if (!token) return
    fetchWallet()
    fetchUsers()
  }, [token, fetchWallet, fetchUsers])

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
    if (!authUsername || !authPassword) {
      addToast('Please fill in all fields', 'error')
      return
    }
    try {
      const endpoint = isLogin ? '/api/auth/login' : '/api/auth/register'
      const res = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: authUsername, password: authPassword, role: 'USER' })
      })
      const result = await parseJsonResponse<ApiResponse<JwtResponse | string>>(res)
      if (!result) {
        addToast('Server returned a non-JSON response', 'error')
        return
      }
      if (isLogin) {
        if (res.ok && result.success) {
          const data = result.data as JwtResponse
          localStorage.setItem('token', data.token)
          localStorage.setItem('refreshToken', data.refreshToken)
          localStorage.setItem('username', data.username)
          localStorage.setItem('roles', JSON.stringify(data.roles))
          setToken(data.token)
          setRefreshToken(data.refreshToken)
          setUsername(data.username)
          setRoles(data.roles)
          addToast(`Welcome back, ${data.username}!`, 'success')
          setAuthPassword('')
        } else {
          addToast(result.message || 'Login failed', 'error')
        }
      } else {
        if (res.ok && result.success) {
          addToast('Registration successful! You can now log in.', 'success')
          setIsLogin(true)
          setAuthPassword('')
        } else {
          addToast(result.message || 'Registration failed', 'error')
        }
      }
    } catch (err: unknown) {
      addToast('Authentication failed: ' + (err instanceof Error ? err.message : 'Unknown error'), 'error')
    }
  }

  const handleLogout = async () => {
    const rt = localStorage.getItem('refreshToken')
    try {
      if (rt) {
        await fetch('/api/auth/logout', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken: rt })
        })
      }
    } catch { /* ignore */ }
    clearAuthState()
    addToast('Logged out successfully', 'info')
  }

  const handleSendPayment = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!receiver || !amount) {
      addToast('Please fill in all fields', 'error')
      return
    }
    const amt = parseFloat(amount)
    if (isNaN(amt) || amt <= 0) {
      addToast('Enter a valid amount', 'error')
      return
    }
    setSendingPayment(true)
    setLastPaymentResult({ success: false, payload: 'Encrypting offline payload...' })
    try {
      const res = await fetchWithAuth('/api/payment/send', {
        method: 'POST',
        body: JSON.stringify({ receiver, amount: amt, category, note: note || undefined })
      })
      const result = await parseJsonResponse<ApiResponse<{ transactionId: string; status: string; hopCount: number }>>(res)
      if (res.ok && result?.success && result.data) {
        setLastPaymentResult({
          success: true,
          transactionId: result.data.transactionId,
          status: result.data.status,
          hopCount: result.data.hopCount,
          payload: `Payment of ${formatCurrency(amt)} to ${receiver} encrypted & saved offline.`
        })
        addToast('Payment saved offline!', 'success')
        setReceiver('')
        setAmount('')
        setNote('')
        fetchWallet()
      } else {
        addToast(result?.message || 'Payment failed', 'error')
        setLastPaymentResult({ success: false, payload: result?.message || 'Payment failed' })
      }
    } catch (err: unknown) {
      addToast('Payment failed: ' + (err instanceof Error ? err.message : 'Error'), 'error')
    } finally {
      setSendingPayment(false)
    }
  }

  const handleSplit = async (e: React.FormEvent) => {
    e.preventDefault()
    const total = parseFloat(splitAmount)
    if (isNaN(total) || total <= 0) {
      addToast('Enter a valid total amount', 'error')
      return
    }
    const participants = splitParticipants.split(',').map(p => p.trim()).filter(Boolean)
    if (participants.length === 0) {
      addToast('Add at least one participant', 'error')
      return
    }
    setSplitting(true)
    try {
      const res = await fetchWithAuth('/api/payment/split', {
        method: 'POST',
        body: JSON.stringify({
          totalAmount: total,
          participants,
          description: splitDescription || 'Split expense',
          category: 'SPLIT'
        })
      })
      const result = await parseJsonResponse<ApiResponse<{ sharePerPerson: number; participantCount: number; transactions: unknown[] }>>(res)
      if (res.ok && result?.success && result.data) {
        addToast(`Split created! Each person owes ${formatCurrency(result.data.sharePerPerson)}`, 'success')
        setSplitAmount('')
        setSplitParticipants('')
        setSplitDescription('')
        fetchWallet()
      } else {
        addToast(result?.message || 'Split failed', 'error')
      }
    } catch (err: unknown) {
      addToast('Split failed: ' + (err instanceof Error ? err.message : 'Error'), 'error')
    } finally {
      setSplitting(false)
    }
  }

  const handleAddBill = async (e: React.FormEvent) => {
    e.preventDefault()
    const amt = parseFloat(billAmount)
    if (!billTitle || isNaN(amt) || amt <= 0) {
      addToast('Fill in bill title and amount', 'error')
      return
    }
    try {
      const res = await fetchWithAuth('/api/wallet/bills', {
        method: 'POST',
        body: JSON.stringify({ title: billTitle, amount: amt, category: billCategory, dueDay: parseInt(billDueDay) })
      })
      const result = await parseJsonResponse<ApiResponse<BillReminder>>(res)
      if (res.ok && result?.success) {
        addToast('Bill reminder added!', 'success')
        setBillTitle('')
        setBillAmount('')
        fetchBills()
      } else {
        addToast(result?.message || 'Failed to add bill', 'error')
      }
    } catch (err: unknown) {
      addToast('Failed: ' + (err instanceof Error ? err.message : 'Error'), 'error')
    }
  }

  const handleDeleteBill = async (id: number) => {
    try {
      const res = await fetchWithAuth(`/api/wallet/bills/${id}`, { method: 'DELETE' })
      const result = await parseJsonResponse<ApiResponse<string>>(res)
      if (res.ok && result?.success) {
        addToast('Bill removed', 'info')
        fetchBills()
      }
    } catch { /* handled */ }
  }

  const handleSync = async () => {
    setSyncing(true)
    addToast('Syncing offline transactions...', 'info')
    try {
      const res = await fetchWithAuth('/api/payment/sync', { method: 'POST' })
      const result = await parseJsonResponse<ApiResponse<{ syncedCount: number }>>(res)
      if (res.ok && result?.success) {
        addToast(`${result.data?.syncedCount ?? 0} transactions synced!`, 'success')
        fetchStats()
        fetchWallet()
      } else {
        addToast(result?.message || 'Sync failed', 'error')
      }
    } finally {
      setSyncing(false)
    }
  }

  const handleRetry = async (id: number) => {
    try {
      const res = await fetchWithAuth(`/api/payment/retry/${id}`, { method: 'POST' })
      const result = await parseJsonResponse<ApiResponse<unknown>>(res)
      if (res.ok && result?.success) {
        addToast('Transaction queued for retry', 'success')
        fetchHistory(historyPage)
      } else {
        addToast(result?.message || 'Retry failed', 'error')
      }
    } catch (err: unknown) {
      addToast('Action failed', 'error')
    }
  }

  const handleCancel = async (id: number) => {
    try {
      const res = await fetchWithAuth(`/api/payment/cancel/${id}`, { method: 'POST' })
      const result = await parseJsonResponse<ApiResponse<unknown>>(res)
      if (res.ok && result?.success) {
        addToast('Transaction cancelled', 'success')
        fetchHistory(historyPage)
        fetchWallet()
      } else {
        addToast(result?.message || 'Cancel failed', 'error')
      }
    } catch { /* handled */ }
  }

  const handleDownload = async (format: 'csv' | 'excel' | 'pdf') => {
    const filename = `payment_history_${Date.now()}.${format === 'excel' ? 'xlsx' : format}`
    try {
      const res = await fetchWithAuth(`/api/payment/export/${format}`)
      if (!res.ok) throw new Error(`Server returned ${res.status}`)
      const blob = await res.blob()
      const blobUrl = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = blobUrl
      a.download = filename
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(blobUrl)
      addToast(`${format.toUpperCase()} downloaded!`, 'success')
    } catch (err: unknown) {
      addToast('Export failed', 'error')
    }
  }

  const ToastContainer = () => (
    <div className="toast-container">
      {toasts.map(toast => (
        <div key={toast.id} className={`toast ${toast.type}`}>
          {toast.type === 'success' && <CheckCircle2 size={16} />}
          {toast.type === 'error' && <AlertTriangle size={16} />}
          {toast.type === 'info' && <Info size={16} />}
          <span>{toast.message}</span>
        </div>
      ))}
    </div>
  )

  if (!token) {
    return (
      <div className="auth-container">
        <div className="auth-bg-orbs" />
        <div className="glass-panel auth-card">
          <div className="auth-header">
            <div className="auth-logo">
              <Zap size={36} />
            </div>
            <h1 className="brand-title">OfflineUPI</h1>
            <p className="brand-subtitle">Pay anyone, anywhere — even without internet</p>
          </div>
          <form onSubmit={handleAuth}>
            <div className="form-group">
              <label className="form-label">Username</label>
              <div className="input-container">
                <User size={16} className="input-icon" />
                <input type="text" className="form-input" placeholder="Enter username"
                  value={authUsername} onChange={e => setAuthUsername(e.target.value)}
                  autoComplete="username" required />
              </div>
            </div>
            <div className="form-group">
              <label className="form-label">Password</label>
              <div className="input-container">
                <Lock size={16} className="input-icon" />
                <input type="password" className="form-input" placeholder="••••••••"
                  value={authPassword} onChange={e => setAuthPassword(e.target.value)}
                  autoComplete="current-password" required />
              </div>
            </div>
            {!isLogin && (
              <p className="auth-hint">New accounts start with ₹10,000 wallet balance</p>
            )}
            <button type="submit" className="btn-primary">
              {isLogin ? 'Sign In' : 'Create Account'}
            </button>
          </form>
          <div className="auth-footer">
            {isLogin ? (
              <>New here? <button className="auth-link" onClick={() => setIsLogin(false)}>Sign Up</button></>
            ) : (
              <>Have an account? <button className="auth-link" onClick={() => setIsLogin(true)}>Sign In</button></>
            )}
          </div>
          <div className="demo-credentials">
            <p>Demo: <strong>user</strong> / <strong>password</strong> &nbsp;|&nbsp; <strong>admin</strong> / <strong>password</strong></p>
          </div>
        </div>
        <ToastContainer />
      </div>
    )
  }

  const tabs: { id: Tab; label: string; icon: React.ReactNode; adminOnly?: boolean }[] = [
    { id: 'home', label: 'Home', icon: <Home size={15} /> },
    { id: 'send', label: 'Pay', icon: <Send size={15} /> },
    { id: 'split', label: 'Split', icon: <Users size={15} /> },
    { id: 'bills', label: 'Bills', icon: <Receipt size={15} /> },
    { id: 'history', label: 'History', icon: <History size={15} /> },
    { id: 'admin', label: 'Admin', icon: <BarChart3 size={15} />, adminOnly: true },
  ]

  return (
    <div className="dashboard-layout">
      <header className="top-navbar">
        <h1 className="brand-title navbar-brand" onClick={() => setCurrentTab('home')}>
          <Zap size={22} style={{ color: '#a855f7' }} />
          OfflineUPI
        </h1>
        <div className="nav-user-area">
          {wallet && (
            <div className="nav-balance">
              <IndianRupee size={14} />
              <span>{formatCurrency(wallet.balance)}</span>
            </div>
          )}
          <div className="user-badge">
            <span className="avatar">{username?.substring(0, 2).toUpperCase()}</span>
            <span className="user-name">{username}</span>
            <span className={`role-tag ${isAdmin ? 'admin' : 'user'}`}>{isAdmin ? 'Admin' : 'User'}</span>
          </div>
          <button onClick={handleLogout} className="btn-secondary btn-logout">
            <LogOut size={14} /> Exit
          </button>
        </div>
      </header>

      <main className="dashboard-content">
        <nav className="tab-navigation">
          {tabs.filter(t => !t.adminOnly || isAdmin).map(tab => (
            <button key={tab.id} className={`tab-btn ${currentTab === tab.id ? 'active' : ''}`}
              onClick={() => setCurrentTab(tab.id)}>
              {tab.icon} {tab.label}
            </button>
          ))}
        </nav>

        {/* HOME TAB */}
        {currentTab === 'home' && (
          <div className="home-layout">
            <div className="wallet-card">
              <div className="wallet-card-bg" />
              <div className="wallet-card-content">
                <div className="wallet-card-header">
                  <Wallet size={20} />
                  <span>My Wallet</span>
                </div>
                <div className="wallet-balance">{wallet ? formatCurrency(wallet.balance) : '...'}</div>
                <div className="wallet-upi-id">{wallet?.upiId || ''}</div>
                <div className="wallet-stats-row">
                  <div className="wallet-stat">
                    <span className="wallet-stat-label">Spent this month</span>
                    <span className="wallet-stat-value spent">{formatCurrency(wallet?.monthlySpent ?? 0)}</span>
                  </div>
                  <div className="wallet-stat">
                    <span className="wallet-stat-label">Received</span>
                    <span className="wallet-stat-value received">{formatCurrency(wallet?.monthlyReceived ?? 0)}</span>
                  </div>
                </div>
              </div>
            </div>

            <div className="quick-actions">
              <button className="quick-action-btn" onClick={() => setCurrentTab('send')}>
                <Send size={22} /><span>Pay</span>
              </button>
              <button className="quick-action-btn" onClick={() => setCurrentTab('split')}>
                <Users size={22} /><span>Split</span>
              </button>
              <button className="quick-action-btn" onClick={() => setCurrentTab('bills')}>
                <Receipt size={22} /><span>Bills</span>
              </button>
              <button className="quick-action-btn" onClick={() => setCurrentTab('history')}>
                <History size={22} /><span>History</span>
              </button>
            </div>

            {monthlySummary && (
              <div className="glass-panel home-panel">
                <h2 className="panel-title"><Calendar size={20} style={{ color: 'var(--accent-cyan)' }} /> {monthlySummary.month} Summary</h2>
                <div className="summary-grid">
                  <div className="summary-item">
                    <span className="summary-label">Transactions</span>
                    <span className="summary-value">{monthlySummary.transactionCount}</span>
                  </div>
                  <div className="summary-item">
                    <span className="summary-label">Total Spent</span>
                    <span className="summary-value spent">{formatCurrency(monthlySummary.totalSpent)}</span>
                  </div>
                  <div className="summary-item">
                    <span className="summary-label">Total Received</span>
                    <span className="summary-value received">{formatCurrency(monthlySummary.totalReceived)}</span>
                  </div>
                </div>
                {Object.keys(monthlySummary.categoryBreakdown).length > 0 && (
                  <div className="category-breakdown">
                    <h3 className="section-subtitle">Spending by Category</h3>
                    {Object.entries(monthlySummary.categoryBreakdown).map(([cat, amt]) => (
                      <div key={cat} className="category-bar-row">
                        <span className="category-name">{cat}</span>
                        <div className="category-bar-track">
                          <div className="category-bar-fill" style={{
                            width: `${Math.min(100, (amt / (monthlySummary.totalSpent || 1)) * 100)}%`
                          }} />
                        </div>
                        <span className="category-amount">{formatCurrency(amt)}</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        )}

        {/* SEND TAB */}
        {currentTab === 'send' && (
          <div className="send-layout">
            <div className="glass-panel form-panel">
              <h2 className="panel-title"><Send style={{ color: 'var(--primary)' }} /> Send Money</h2>
              <form onSubmit={handleSendPayment}>
                <div className="form-group">
                  <label className="form-label">From</label>
                  <div className="input-container">
                    <User size={16} className="input-icon" />
                    <input type="text" className="form-input" value={username || ''} disabled />
                  </div>
                </div>
                <div className="form-group">
                  <label className="form-label">To</label>
                  <div className="input-container">
                    <User size={16} className="input-icon" />
                    <select className="form-input form-select" value={receiver}
                      onChange={e => setReceiver(e.target.value)} required>
                      <option value="">Select recipient</option>
                      {allUsers.map(u => <option key={u} value={u}>{u}</option>)}
                    </select>
                  </div>
                </div>
                <div className="form-group">
                  <label className="form-label">Amount (₹)</label>
                  <div className="input-container">
                    <IndianRupee size={16} className="input-icon" />
                    <input type="number" className="form-input" placeholder="0.00" min="1" step="0.01"
                      value={amount} onChange={e => setAmount(e.target.value)} required />
                  </div>
                </div>
                <div className="form-group">
                  <label className="form-label">Category</label>
                  <select className="form-input form-select" value={category} onChange={e => setCategory(e.target.value)}>
                    {CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
                  </select>
                </div>
                <div className="form-group">
                  <label className="form-label">Note (optional)</label>
                  <input type="text" className="form-input" style={{ paddingLeft: 16 }}
                    placeholder="What's this for?" value={note} onChange={e => setNote(e.target.value)} />
                </div>
                <button type="submit" className="btn-primary" disabled={sendingPayment}>
                  {sendingPayment ? <><RefreshCw size={16} className="animate-spin" /> Processing...</> : 'Pay Now'}
                </button>
              </form>
            </div>
            <div className="glass-panel encryption-panel">
              <h2 className="panel-title"><Shield style={{ color: 'var(--accent-cyan)' }} /> Security Status</h2>
              <p className="panel-desc">Transactions are AES-encrypted and stored offline until network sync.</p>
              <div className={`encrypted-payload-area custom-scrollbar ${sendingPayment ? 'encrypting' : ''}`}>
                {lastPaymentResult ? (
                  <div className={lastPaymentResult.success ? 'payload-success' : 'payload-info'}>
                    {lastPaymentResult.payload}
                    {lastPaymentResult.success && (
                      <div className="payload-details">
                        <p><strong>ID:</strong> {lastPaymentResult.transactionId}</p>
                        <p><strong>Hops:</strong> {lastPaymentResult.hopCount}</p>
                        <p><strong>Status:</strong> {lastPaymentResult.status}</p>
                      </div>
                    )}
                  </div>
                ) : (
                  <div className="payload-empty">
                    <Radio size={36} className="animate-pulse" />
                    <span>Ready for offline payment</span>
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        {/* SPLIT TAB */}
        {currentTab === 'split' && (
          <div className="glass-panel form-panel split-panel">
            <h2 className="panel-title"><Users style={{ color: 'var(--accent-purple)' }} /> Split Expense</h2>
            <p className="panel-desc">Split a bill equally among friends. Each person&apos;s share is deducted from their wallet.</p>
            <form onSubmit={handleSplit}>
              <div className="form-group">
                <label className="form-label">Total Amount (₹)</label>
                <div className="input-container">
                  <IndianRupee size={16} className="input-icon" />
                  <input type="number" className="form-input" placeholder="1200" min="1" step="0.01"
                    value={splitAmount} onChange={e => setSplitAmount(e.target.value)} required />
                </div>
              </div>
              <div className="form-group">
                <label className="form-label">Participants (comma-separated usernames)</label>
                <input type="text" className="form-input" style={{ paddingLeft: 16 }}
                  placeholder="e.g. bob, charlie" value={splitParticipants}
                  onChange={e => setSplitParticipants(e.target.value)} required />
                <p className="field-hint">Available: {allUsers.join(', ') || 'loading...'}</p>
              </div>
              <div className="form-group">
                <label className="form-label">Description</label>
                <input type="text" className="form-input" style={{ paddingLeft: 16 }}
                  placeholder="Dinner at restaurant" value={splitDescription}
                  onChange={e => setSplitDescription(e.target.value)} />
              </div>
              {splitAmount && splitParticipants && (
                <div className="split-preview">
                  Each person pays: <strong>{formatCurrency(parseFloat(splitAmount) / (splitParticipants.split(',').filter(Boolean).length + 1))}</strong>
                </div>
              )}
              <button type="submit" className="btn-primary" disabled={splitting}>
                {splitting ? <><RefreshCw size={16} className="animate-spin" /> Splitting...</> : 'Split Expense'}
              </button>
            </form>
          </div>
        )}

        {/* BILLS TAB */}
        {currentTab === 'bills' && (
          <div className="bills-layout">
            <div className="glass-panel form-panel">
              <h2 className="panel-title"><Plus style={{ color: 'var(--success)' }} /> Add Bill Reminder</h2>
              <form onSubmit={handleAddBill}>
                <div className="form-group">
                  <label className="form-label">Bill Title</label>
                  <input type="text" className="form-input" style={{ paddingLeft: 16 }}
                    placeholder="Electricity Bill" value={billTitle} onChange={e => setBillTitle(e.target.value)} required />
                </div>
                <div className="form-row">
                  <div className="form-group">
                    <label className="form-label">Amount (₹)</label>
                    <input type="number" className="form-input" style={{ paddingLeft: 16 }}
                      placeholder="1500" min="1" value={billAmount} onChange={e => setBillAmount(e.target.value)} required />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Due Day</label>
                    <input type="number" className="form-input" style={{ paddingLeft: 16 }}
                      min="1" max="28" value={billDueDay} onChange={e => setBillDueDay(e.target.value)} />
                  </div>
                </div>
                <div className="form-group">
                  <label className="form-label">Category</label>
                  <select className="form-input form-select" value={billCategory} onChange={e => setBillCategory(e.target.value)}>
                    {CATEGORIES.filter(c => c !== 'SPLIT').map(c => <option key={c} value={c}>{c}</option>)}
                  </select>
                </div>
                <button type="submit" className="btn-primary">Add Reminder</button>
              </form>
            </div>
            <div className="glass-panel bills-list-panel">
              <h2 className="panel-title"><Receipt style={{ color: 'var(--pending)' }} /> Monthly Bills</h2>
              {bills.length === 0 ? (
                <div className="empty-state"><Info size={28} /><span>No bill reminders yet</span></div>
              ) : (
                <div className="bills-list">
                  {bills.map(bill => (
                    <div key={bill.id} className="bill-card">
                      <div className="bill-info">
                        <span className="bill-title">{bill.title}</span>
                        <span className="bill-meta">{bill.category} · Due day {bill.dueDay}</span>
                      </div>
                      <div className="bill-right">
                        <span className="bill-amount">{formatCurrency(bill.amount)}</span>
                        <button className="btn-icon-danger" onClick={() => handleDeleteBill(bill.id)}>
                          <Trash2 size={14} />
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}

        {/* HISTORY TAB */}
        {currentTab === 'history' && (
          <div className="glass-panel history-panel">
            <div className="history-header">
              <h2 className="panel-title"><History style={{ color: 'var(--primary)' }} /> Transaction History</h2>
              <div className="export-btns">
                {(['csv', 'excel', 'pdf'] as const).map(fmt => (
                  <button key={fmt} className="btn-secondary" onClick={() => handleDownload(fmt)}>
                    <Download size={14} /> {fmt.toUpperCase()}
                  </button>
                ))}
              </div>
            </div>
            <div className="filter-bar">
              <div className="search-input-wrap">
                <Search size={16} className="input-icon" />
                <input type="text" className="filter-input" placeholder="Search..."
                  value={filterSearch} onChange={e => setFilterSearch(e.target.value)} />
              </div>
              <select className="filter-input" value={filterStatus} onChange={e => setFilterStatus(e.target.value)}>
                <option value="">All Statuses</option>
                <option value="PENDING">Pending</option>
                <option value="WAITING_FOR_SYNC">Waiting for Sync</option>
                <option value="SYNCED">Synced</option>
                <option value="FAILED">Failed</option>
                <option value="REJECTED">Rejected</option>
              </select>
              <button className="btn-primary btn-filter" onClick={() => fetchHistory(0)}>Apply</button>
            </div>
            <div className="table-wrapper">
              {loadingHistory ? (
                <div className="empty-state"><RefreshCw className="animate-spin" size={24} /><span>Loading...</span></div>
              ) : history.length === 0 ? (
                <div className="empty-state"><Info size={28} /><span>No transactions found</span></div>
              ) : (
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>ID</th><th>From</th><th>To</th><th>Amount</th><th>Type</th><th>Status</th><th>Date</th><th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {history.map(tx => (
                      <tr key={tx.id}>
                        <td className="mono">{tx.transactionId.substring(0, 8)}...</td>
                        <td>{tx.sender}</td>
                        <td>{tx.receiver}</td>
                        <td className="amount-cell">{formatCurrency(tx.amount)}</td>
                        <td><span className="type-tag">{tx.transactionType || 'TRANSFER'}</span></td>
                        <td><span className={`badge ${tx.status === 'WAITING_FOR_SYNC' ? 'waiting_for_sync' : tx.status.toLowerCase()}`}>
                          {tx.status === 'WAITING_FOR_SYNC' ? 'SYNC' : tx.status}
                        </span></td>
                        <td className="date-cell">{new Date(tx.createdAt).toLocaleString()}</td>
                        <td>
                          <div className="row-actions">
                            {tx.status === 'FAILED' && (
                              <button className="btn-action success" onClick={() => handleRetry(tx.id)}>Retry</button>
                            )}
                            {(tx.status === 'PENDING' || tx.status === 'WAITING_FOR_SYNC') && (
                              <button className="btn-action danger" onClick={() => handleCancel(tx.id)}>Cancel</button>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
            {historyTotalPages > 1 && (
              <div className="pagination-controls">
                <span className="pagination-info">Page {historyPage + 1} of {historyTotalPages} ({historyTotalElements} records)</span>
                <div className="pagination-btn-group">
                  <button className="btn-secondary" disabled={historyPage === 0} onClick={() => fetchHistory(historyPage - 1)}>
                    <ChevronLeft size={16} />
                  </button>
                  <button className="btn-secondary" disabled={historyPage >= historyTotalPages - 1} onClick={() => fetchHistory(historyPage + 1)}>
                    <ChevronRight size={16} />
                  </button>
                </div>
              </div>
            )}
          </div>
        )}

        {/* ADMIN TAB */}
        {currentTab === 'admin' && isAdmin && (
          <div>
            <div className="glass-panel sync-console-panel">
              <div>
                <h2 className="panel-title"><Radio className="animate-pulse" style={{ color: 'var(--primary)' }} /> Sync Console</h2>
                <p className="panel-desc">Process all pending offline transactions when connectivity is restored.</p>
              </div>
              <button onClick={handleSync} disabled={syncing} className="btn-primary pulse-sync-btn btn-sync">
                {syncing ? <><RefreshCw className="animate-spin" size={16} /> Syncing...</> : <><RefreshCw size={16} /> Sync All</>}
              </button>
            </div>
            {loadingStats ? (
              <div className="empty-state"><RefreshCw className="animate-spin" size={24} /><span>Loading stats...</span></div>
            ) : stats ? (
              <>
                <div className="metrics-grid">
                  {[
                    { label: 'Total', value: stats.totalTransactions, icon: <History size={16} />, color: 'var(--primary)' },
                    { label: 'Pending', value: stats.pendingCount, icon: <Radio size={16} />, color: 'var(--pending)' },
                    { label: 'Synced', value: stats.syncedCount, icon: <CheckCircle2 size={16} />, color: 'var(--success)' },
                    { label: 'Failed', value: stats.failedCount, icon: <XCircle size={16} />, color: 'var(--danger)' },
                  ].map(m => (
                    <div key={m.label} className="glass-panel metric-card">
                      <div className="metric-header"><span>{m.label}</span>{m.icon}</div>
                      <div className="metric-value">{m.value}</div>
                    </div>
                  ))}
                  <div className="glass-panel metric-card span-2">
                    <div className="metric-header"><span>Total Settled</span><IndianRupee size={16} /></div>
                    <div className="metric-value">{formatCurrency(stats.totalAmount)}</div>
                  </div>
                  <div className="glass-panel metric-card">
                    <div className="metric-header"><span>Success Rate</span><Shield size={16} /></div>
                    <div className="metric-value">{stats.successRate.toFixed(1)}%</div>
                    <div className="progress-bar"><div className="progress-fill" style={{ width: `${stats.successRate}%` }} /></div>
                  </div>
                </div>
                {Object.keys(stats.dailyTransactions).length > 0 && (
                  <div className="glass-panel chart-panel">
                    <h2 className="panel-title"><BarChart3 size={20} /> Daily Activity</h2>
                    <div className="bar-chart">
                      {Object.entries(stats.dailyTransactions).slice(-7).map(([day, count]) => (
                        <div key={day} className="bar-chart-item">
                          <div className="bar-chart-bar" style={{ height: `${Math.max(8, count * 20)}px` }} />
                          <span className="bar-chart-label">{day.slice(5)}</span>
                          <span className="bar-chart-count">{count}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </>
            ) : (
              <div className="empty-state"><span>Failed to load stats</span></div>
            )}
          </div>
        )}
      </main>
      <ToastContainer />
    </div>
  )
}

export default App
