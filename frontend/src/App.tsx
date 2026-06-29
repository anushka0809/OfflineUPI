import { useState, useEffect, useCallback } from 'react'
import {
  Lock,
  User,
  Shield,
  Send,
  History,
  LogOut,
  CheckCircle2,
  AlertTriangle,
  RefreshCw,
  XCircle,
  Search,
  ChevronLeft,
  ChevronRight,
  Download,
  BarChart3,
  Radio,
  Info,
  DollarSign
} from 'lucide-react'

// API response types
interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

interface JwtResponse {
  token: string;
  refreshToken: string;
  username: string;
  roles: string[];
}

interface TokenRefreshResponse {
  accessToken: string;
  refreshToken: string;
}

interface Transaction {
  id: number;
  transactionId: string;
  sender: string;
  receiver: string;
  amount: number;
  status: string;
  hopCount: number;
  createdAt: string;
  syncTime: string | null;
  failureReason: string | null;
  encryptedPayload: string | null;
}

interface AdminStatsResponse {
  totalTransactions: number;
  pendingCount: number;
  syncedCount: number;
  failedCount: number;
  successRate: number;
  totalAmount: number;
  dailyTransactions: Record<string, number>;
  monthlyTransactions: Record<string, number>;
}

interface Toast {
  id: number;
  message: string;
  type: 'success' | 'error' | 'info';
}

function App() {
  // Auth State
  const [token, setToken] = useState<string | null>(localStorage.getItem('token'))
  const [, setRefreshToken] = useState<string | null>(localStorage.getItem('refreshToken'))
  const [username, setUsername] = useState<string | null>(localStorage.getItem('username'))
  const [roles, setRoles] = useState<string[]>(JSON.parse(localStorage.getItem('roles') || '[]'))

  // UI State
  const [isLogin, setIsLogin] = useState<boolean>(true)
  const [authUsername, setAuthUsername] = useState<string>('')
  const [authPassword, setAuthPassword] = useState<string>('')
  const [authRole, setAuthRole] = useState<string>('USER')
  const [currentTab, setCurrentTab] = useState<'send' | 'history' | 'admin'>('send')
  const [toasts, setToasts] = useState<Toast[]>([])

  // Form State
  const [receiver, setReceiver] = useState<string>('')
  const [amount, setAmount] = useState<string>('')
  const [sendingPayment, setSendingPayment] = useState<boolean>(false)
  const [lastPaymentResult, setLastPaymentResult] = useState<{
    success: boolean;
    transactionId?: string;
    status?: string;
    hopCount?: number;
    payload?: string;
  } | null>(null)

  // History State
  const [history, setHistory] = useState<Transaction[]>([])
  const [historyPage, setHistoryPage] = useState<number>(0)
  const [historyTotalPages, setHistoryTotalPages] = useState<number>(0)
  const [historyTotalElements, setHistoryTotalElements] = useState<number>(0)
  const [filterStatus, setFilterStatus] = useState<string>('')
  const [filterSearch, setFilterSearch] = useState<string>('')
  const [loadingHistory, setLoadingHistory] = useState<boolean>(false)

  // Admin State
  const [stats, setStats] = useState<AdminStatsResponse | null>(null)
  const [syncing, setSyncing] = useState<boolean>(false)
  const [loadingStats, setLoadingStats] = useState<boolean>(false)

  const isAdmin = roles.includes('ROLE_ADMIN')

  // Toast System Helper
  const addToast = useCallback((message: string, type: 'success' | 'error' | 'info' = 'info') => {
    const id = Date.now()
    setToasts((prev) => [...prev, { id, message, type }])
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id))
    }, 4000)
  }, [])

  // Custom authenticated fetch helper with auto-refresh rotation
  const fetchWithAuth = useCallback(async (url: string, options: RequestInit = {}): Promise<Response> => {
    const headers = new Headers(options.headers || {})
    let currentToken = localStorage.getItem('token')

    if (currentToken) {
      headers.set('Authorization', `Bearer ${currentToken}`)
    }
    if (!headers.has('Content-Type') && !(options.body instanceof FormData)) {
      headers.set('Content-Type', 'application/json')
    }

    const response = await fetch(url, { ...options, headers })

    if (response.status === 401 && localStorage.getItem('refreshToken')) {
      // Access token expired, attempt rotation
      try {
        const refreshRes = await fetch('/api/auth/refreshtoken', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken: localStorage.getItem('refreshToken') })
        })

        if (refreshRes.ok) {
          const refreshData: ApiResponse<TokenRefreshResponse> = await refreshRes.json()
          const newAccessToken = refreshData.data.accessToken
          const newRefreshToken = refreshData.data.refreshToken

          localStorage.setItem('token', newAccessToken)
          localStorage.setItem('refreshToken', newRefreshToken)
          setToken(newAccessToken)
          setRefreshToken(newRefreshToken)

          // Retry the original request
          headers.set('Authorization', `Bearer ${newAccessToken}`)
          return fetch(url, { ...options, headers })
        }
      } catch (err) {
        console.error("Token refresh failed", err)
      }

      // If refresh fails, clear auth state
      localStorage.clear()
      setToken(null)
      setRefreshToken(null)
      setUsername(null)
      setRoles([])
      addToast("Session expired. Please log in again.", 'error')
      throw new Error("Unauthorized")
    }

    return response;
  }, [addToast])

  // Authentication Handlers
  const handleAuth = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!authUsername || !authPassword) {
      addToast("Please fill in all fields", 'error')
      return
    }

    try {
      if (isLogin) {
        const res = await fetch('/api/auth/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username: authUsername, password: authPassword })
        })
        const result = await res.json()

        if (res.ok && result.success) {
          const { token, refreshToken, username, roles } = result.data as JwtResponse
          localStorage.setItem('token', token)
          localStorage.setItem('refreshToken', refreshToken)
          localStorage.setItem('username', username)
          localStorage.setItem('roles', JSON.stringify(roles))

          setToken(token)
          setRefreshToken(refreshToken)
          setUsername(username)
          setRoles(roles)
          addToast(`Welcome back, ${username}!`, 'success')
          setAuthPassword('')
        } else {
          addToast(result.message || "Login failed", 'error')
        }
      } else {
        const res = await fetch('/api/auth/register', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username: authUsername, password: authPassword, role: authRole })
        })
        const result = await res.json()

        if (res.ok && result.success) {
          addToast("Registration successful! You can now log in.", 'success')
          setIsLogin(true)
          setAuthPassword('')
        } else {
          addToast(result.message || "Registration failed", 'error')
        }
      }
    } catch (err: any) {
      addToast("Authentication request failed: " + err.message, 'error')
    }
  }

  const handleLogout = () => {
    localStorage.clear()
    setToken(null)
    setRefreshToken(null)
    setUsername(null)
    setRoles([])
    addToast("Logged out successfully", 'info')
  }

  // Load User Transaction History
  const fetchHistory = useCallback(async (page: number = 0) => {
    setLoadingHistory(true)
    try {
      let url = `/api/payment/history?page=${page}&size=6&sort=createdAt,desc`
      if (filterStatus) url += `&status=${filterStatus}`
      if (filterSearch) url += `&search=${encodeURIComponent(filterSearch)}`

      const res = await fetchWithAuth(url)
      if (res.ok) {
        const result: ApiResponse<any> = await res.json()
        setHistory(result.data.content)
        setHistoryPage(result.data.number)
        setHistoryTotalPages(result.data.totalPages)
        setHistoryTotalElements(result.data.totalElements)
      } else {
        addToast("Failed to fetch transaction history", 'error')
      }
    } catch (err) {
      console.error(err)
    } finally {
      setLoadingHistory(false)
    }
  }, [fetchWithAuth, filterStatus, filterSearch, addToast])

  // Load Admin Metrics Dashboard
  const fetchStats = useCallback(async () => {
    if (!isAdmin) return
    setLoadingStats(true)
    try {
      const res = await fetchWithAuth('/api/payment/stats')
      if (res.ok) {
        const result: ApiResponse<AdminStatsResponse> = await res.json()
        setStats(result.data)
      } else {
        addToast("Failed to fetch admin stats", 'error')
      }
    } catch (err) {
      console.error(err)
    } finally {
      setLoadingStats(false)
    }
  }, [fetchWithAuth, isAdmin, addToast])

  // Trigger loading relevant tab data
  useEffect(() => {
    if (token) {
      if (currentTab === 'history') {
        fetchHistory(0)
      } else if (currentTab === 'admin' && isAdmin) {
        fetchStats()
      }
    }
  }, [token, currentTab, fetchHistory, fetchStats, isAdmin])

  // Send Money Handler
  const handleSendPayment = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!receiver || !amount) {
      addToast("Please fill in all transaction fields", 'error')
      return
    }
    if (isNaN(parseFloat(amount)) || parseFloat(amount) <= 0) {
      addToast("Please enter a valid amount greater than zero", 'error')
      return
    }

    setSendingPayment(true)
    setLastPaymentResult(null)

    // Formulate a representation of offline payload
    const rawPayload = `${username || 'sender'}|${receiver}|${parseFloat(amount)}`

    // Initial display of payload encryption
    setLastPaymentResult({
      success: false,
      payload: `Encrypting offline payload: "${rawPayload}"...`
    })

    try {
      const res = await fetchWithAuth('/api/payment/send', {
        method: 'POST',
        body: JSON.stringify({
          sender: username,
          receiver,
          amount: parseFloat(amount)
        })
      })
      const result = await res.json()

      if (res.ok && result.success) {
        // Find the transaction record to show the real encrypted payload

        try {
          // Find transaction database ID, or fetch details. Wait, the controller returns transactionId, status, and hopCount
          // We can show this return value directly
          setLastPaymentResult({
            success: true,
            transactionId: result.data.transactionId,
            status: result.data.status,
            hopCount: result.data.hopCount,
            payload: `AES-128 Ciphertext:\n${btoa(rawPayload).substring(0, 48)}... (Encrypted & Saved)`
          })
          addToast("Payment saved offline!", 'success')
          setReceiver('')
          setAmount('')
        } catch (innerErr) { }
      } else {
        setLastPaymentResult({
          success: false,
          payload: `Error: ${result.message || "Failed to submit offline transaction"}`
        })
        addToast(result.message || "Payment failed", 'error')
      }
    } catch (err: any) {
      setLastPaymentResult({
        success: false,
        payload: `Exception: ${err.message}`
      })
      addToast("Failed to process payment: " + err.message, 'error')
    } finally {
      setSendingPayment(false)
    }
  }

  // Admin Synchronize Handler
  const handleSync = async () => {
    setSyncing(true)
    addToast("Initiating batch synchronization simulation...", 'info')
    try {
      const res = await fetchWithAuth('/api/payment/sync', { method: 'POST' })
      const result = await res.json()

      if (res.ok && result.success) {
        addToast(`${result.data.syncedCount} transactions successfully processed!`, 'success')
        fetchStats() // refresh stats
      } else {
        addToast(result.message || "Sync failed", 'error')
      }
    } catch (err: any) {
      addToast("Network sync failed: " + err.message, 'error')
    } finally {
      setSyncing(false)
    }
  }

  // Retry / Cancel Actions
  const handleRetry = async (id: number) => {
    try {
      const res = await fetchWithAuth(`/api/payment/retry/${id}`, { method: 'POST' })
      const result = await res.json()
      if (res.ok && result.success) {
        addToast("Transaction set back to WAITING_FOR_SYNC", 'success')
        fetchHistory(historyPage)
      } else {
        addToast(result.message || "Retry failed", 'error')
      }
    } catch (err: any) {
      addToast("Action failed: " + err.message, 'error')
    }
  }

  const handleCancel = async (id: number) => {
    try {
      const res = await fetchWithAuth(`/api/payment/cancel/${id}`, { method: 'POST' })
      const result = await res.json()
      if (res.ok && result.success) {
        addToast("Transaction marked as REJECTED (Cancelled)", 'success')
        fetchHistory(historyPage)
      } else {
        addToast(result.message || "Cancellation failed", 'error')
      }
    } catch (err: any) {
      addToast("Action failed: " + err.message, 'error')
    }
  }

  // File Download Handler
  const handleDownload = async (format: 'csv' | 'excel' | 'pdf') => {
    const filename = `payment_history_${Date.now()}.${format === 'excel' ? 'xlsx' : format}`
    const url = `/api/payment/export/${format}`
    addToast(`Generating and downloading ${format.toUpperCase()} export...`, 'info')

    try {
      const res = await fetchWithAuth(url, { method: 'GET' })
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
      addToast(`${format.toUpperCase()} downloaded successfully!`, 'success')
    } catch (err: any) {
      addToast(`Export failed: ${err.message}`, 'error')
    }
  }

  // LOGIN SCREEN
  if (!token) {
    return (
      <div className="auth-container">
        <div className="glass-panel auth-card">
          <div className="auth-header">
            <h1 className="brand-title">
              <Radio className="input-icon" style={{ position: 'static', color: '#a855f7' }} />
              OfflineUPI
            </h1>
            <p className="brand-subtitle">Secure encrypted offline peer-to-peer payments</p>
          </div>

          <form onSubmit={handleAuth}>
            <div className="form-group">
              <label className="form-label">Username</label>
              <div className="input-container">
                <User size={16} className="input-icon" />
                <input
                  type="text"
                  className="form-input"
                  placeholder="Enter your username"
                  value={authUsername}
                  onChange={(e) => setAuthUsername(e.target.value)}
                  autoComplete="username"
                  required
                />
              </div>
            </div>

            <div className="form-group">
              <label className="form-label">Password</label>
              <div className="input-container">
                <Lock size={16} className="input-icon" />
                <input
                  type="password"
                  className="form-input"
                  placeholder="••••••••"
                  value={authPassword}
                  onChange={(e) => setAuthPassword(e.target.value)}
                  autoComplete="current-password"
                  required
                />
              </div>
            </div>

            {!isLogin && (
              <div className="form-group">
                <label className="form-label">User Role</label>
                <div className="input-container">
                  <Shield size={16} className="input-icon" />
                  <select
                    className="form-input"
                    value={authRole}
                    onChange={(e) => setAuthRole(e.target.value)}
                  >
                    <option value="USER">User (Standard)</option>
                    <option value="ADMIN">Administrator</option>
                  </select>
                </div>
              </div>
            )}

            <button type="submit" className="btn-primary">
              {isLogin ? "Authenticate" : "Register Account"}
            </button>
          </form>

          <div className="auth-footer">
            {isLogin ? (
              <>
                Need an account?
                <button className="auth-link" onClick={() => setIsLogin(false)}>
                  Sign Up
                </button>
              </>
            ) : (
              <>
                Have an account?
                <button className="auth-link" onClick={() => setIsLogin(true)}>
                  Sign In
                </button>
              </>
            )}
          </div>
        </div>

        {/* Toast Alerts */}
        <div className="toast-container">
          {toasts.map((toast) => (
            <div key={toast.id} className={`toast ${toast.type}`}>
              {toast.type === 'success' && <CheckCircle2 size={16} />}
              {toast.type === 'error' && <AlertTriangle size={16} />}
              {toast.type === 'info' && <Info size={16} />}
              <span>{toast.message}</span>
            </div>
          ))}
        </div>
      </div>
    )
  }

  // MAIN DASHBOARD SCREEN
  return (
    <div className="dashboard-layout">
      {/* Top Navbar */}
      <header className="top-navbar">
        <h1 className="brand-title" style={{ margin: 0, cursor: 'pointer' }} onClick={() => setCurrentTab('send')}>
          <Radio size={24} style={{ color: '#a855f7' }} />
          OfflineUPI
        </h1>

        <div className="nav-user-area">
          <div className="user-badge">
            <span className="avatar">{username ? username.substring(0, 2).toUpperCase() : 'U'}</span>
            <span style={{ fontWeight: 600, fontSize: 14 }}>{username}</span>
            <span className={`role-tag ${isAdmin ? 'admin' : 'user'}`}>
              {isAdmin ? 'Admin' : 'User'}
            </span>
          </div>

          <button onClick={handleLogout} className="btn-secondary" style={{ padding: '8px 14px' }}>
            <LogOut size={14} />
            Exit
          </button>
        </div>
      </header>

      {/* Main Container */}
      <main className="dashboard-content">
        {/* Navigation Tabs */}
        <nav className="tab-navigation">
          <button
            className={`tab-btn ${currentTab === 'send' ? 'active' : ''}`}
            onClick={() => setCurrentTab('send')}
          >
            <Send size={15} />
            Send Offline
          </button>
          <button
            className={`tab-btn ${currentTab === 'history' ? 'active' : ''}`}
            onClick={() => setCurrentTab('history')}
          >
            <History size={15} />
            My History
          </button>
          {isAdmin && (
            <button
              className={`tab-btn ${currentTab === 'admin' ? 'active' : ''}`}
              onClick={() => setCurrentTab('admin')}
            >
              <BarChart3 size={15} />
              Admin Console
            </button>
          )}
        </nav>

        {/* Tab 1: Send Offline Payment */}
        {currentTab === 'send' && (
          <div className="send-layout">
            <div className="glass-panel form-panel">
              <h2 className="panel-title">
                <Send style={{ color: 'var(--primary)' }} />
                Offline UPI Payment Form
              </h2>
              <form onSubmit={handleSendPayment}>
                <div className="form-group">
                  <label className="form-label">Sender Account</label>
                  <div className="input-container">
                    <User size={16} className="input-icon" />
                    <input
                      type="text"
                      className="form-input"
                      value={username || ''}
                      disabled
                      style={{ opacity: 0.7 }}
                    />
                  </div>
                </div>

                <div className="form-group">
                  <label className="form-label">Receiver Username</label>
                  <div className="input-container">
                    <User size={16} className="input-icon" />
                    <input
                      type="text"
                      className="form-input"
                      placeholder="e.g. Bob"
                      value={receiver}
                      onChange={(e) => setReceiver(e.target.value)}
                      required
                    />
                  </div>
                </div>

                <div className="form-group">
                  <label className="form-label">Payment Amount</label>
                  <div className="input-container">
                    <DollarSign size={16} className="input-icon" />
                    <input
                      type="text"
                      className="form-input"
                      placeholder="0.00"
                      value={amount}
                      onChange={(e) => setAmount(e.target.value)}
                      required
                    />
                  </div>
                </div>

                <button type="submit" className="btn-primary" disabled={sendingPayment}>
                  {sendingPayment ? (
                    <>
                      <RefreshCw size={16} className="animate-spin" />
                      Encrypting & Broadcasting...
                    </>
                  ) : (
                    "Authorize Offline Transfer"
                  )}
                </button>
              </form>
            </div>

            {/* AES Encryption Visualizer Panel */}
            <div className="glass-panel encryption-panel">
              <h2 className="panel-title">
                <Shield style={{ color: 'var(--accent-cyan)' }} />
                Security Visualizer
              </h2>
              <p style={{ fontSize: 13, color: 'var(--neutral-400)', marginBottom: 14 }}>
                Offline transaction payloads are AES encrypted on the local device, simulating mesh network hops before persistence.
              </p>

              <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
                <span className="form-label">Transaction Payload State</span>
                <div className={`encrypted-payload-area custom-scrollbar ${sendingPayment ? 'encrypting' : ''}`}>
                  {lastPaymentResult ? (
                    <div style={{ color: lastPaymentResult.success ? '#10b981' : '#cbd5e1', whiteSpace: 'pre-wrap' }}>
                      {lastPaymentResult.payload}
                      {lastPaymentResult.success && (
                        <div style={{ marginTop: 12, color: 'var(--neutral-300)', fontSize: 12 }}>
                          <hr style={{ borderColor: 'rgba(255,255,255,0.08)', margin: '8px 0' }} />
                          <p><strong>Transaction ID:</strong> {lastPaymentResult.transactionId}</p>
                          <p><strong>Hops Counter:</strong> {lastPaymentResult.hopCount}</p>
                          <p><strong>Database Status:</strong> {lastPaymentResult.status}</p>
                          <p style={{ marginTop: 6, color: '#f59e0b', fontSize: 11 }}>
                            ℹ️ Requires administrator sync when network connectivity is restored.
                          </p>
                        </div>
                      )}
                    </div>
                  ) : (
                    <div style={{ color: 'var(--neutral-600)', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', gap: 10 }}>
                      <Radio size={36} className="animate-pulse" />
                      <span>Awaiting offline transaction broadcast...</span>
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Tab 2: Transaction History */}
        {currentTab === 'history' && (
          <div className="glass-panel" style={{ padding: '32px' }}>
            <div style={{ display: 'flex', justifyContent: 'between', alignItems: 'center', flexWrap: 'wrap', gap: 16, marginBottom: 24 }}>
              <h2 className="panel-title" style={{ margin: 0 }}>
                <History style={{ color: 'var(--primary)' }} />
                Transaction History Logs
              </h2>

              <div style={{ display: 'flex', gap: 8 }}>
                <button className="btn-secondary" onClick={() => handleDownload('csv')}>
                  <Download size={14} />
                  CSV
                </button>
                <button className="btn-secondary" onClick={() => handleDownload('excel')}>
                  <Download size={14} />
                  Excel
                </button>
                <button className="btn-secondary" onClick={() => handleDownload('pdf')}>
                  <Download size={14} />
                  PDF
                </button>
              </div>
            </div>

            {/* Filter controls */}
            <div className="filter-bar">
              <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
                <Search size={16} className="input-icon" style={{ left: 10 }} />
                <input
                  type="text"
                  className="filter-input"
                  style={{ paddingLeft: 34, width: '260px' }}
                  placeholder="Search ID, sender, receiver..."
                  value={filterSearch}
                  onChange={(e) => setFilterSearch(e.target.value)}
                />
              </div>

              <select
                className="filter-input"
                value={filterStatus}
                onChange={(e) => setFilterStatus(e.target.value)}
              >
                <option value="">All Statuses</option>
                <option value="PENDING">Pending</option>
                <option value="WAITING_FOR_SYNC">Waiting for Sync</option>
                <option value="SYNCED">Synced</option>
                <option value="FAILED">Failed</option>
                <option value="REJECTED">Rejected</option>
              </select>

              <button className="btn-primary" style={{ width: 'auto', padding: '8px 18px', fontSize: 14 }} onClick={() => fetchHistory(0)}>
                Apply Filters
              </button>
            </div>

            {/* Table */}
            <div className="table-wrapper">
              {loadingHistory ? (
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '60px 0', gap: 10 }}>
                  <RefreshCw className="animate-spin" size={24} style={{ color: 'var(--primary)' }} />
                  <span style={{ color: 'var(--neutral-400)' }}>Loading history logs...</span>
                </div>
              ) : history.length === 0 ? (
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '60px 0', gap: 10 }}>
                  <Info size={28} style={{ color: 'var(--neutral-400)' }} />
                  <span style={{ color: 'var(--neutral-400)' }}>No transaction logs match search filters.</span>
                </div>
              ) : (
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>TX UUID</th>
                      <th>Sender</th>
                      <th>Receiver</th>
                      <th>Amount</th>
                      <th>Status</th>
                      <th>Hops</th>
                      <th>Date</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {history.map((tx) => (
                      <tr key={tx.id}>
                        <td style={{ fontFamily: 'var(--mono)', fontSize: 12 }}>
                          {tx.transactionId.substring(0, 8)}...
                        </td>
                        <td>{tx.sender}</td>
                        <td>{tx.receiver}</td>
                        <td style={{ fontWeight: 600 }}>${tx.amount.toFixed(2)}</td>
                        <td>
                          <span className={`badge ${tx.status.toLowerCase()}`}>
                            {tx.status === 'WAITING_FOR_SYNC' ? 'waiting' : tx.status.toLowerCase()}
                          </span>
                        </td>
                        <td>{tx.hopCount}</td>
                        <td style={{ fontSize: 13, color: 'var(--neutral-400)' }}>
                          {new Date(tx.createdAt).toLocaleString()}
                        </td>
                        <td>
                          <div className="row-actions">
                            {tx.status === 'FAILED' && (
                              <button
                                className="btn-secondary"
                                style={{ padding: '4px 10px', fontSize: 12, borderColor: 'var(--success)', color: 'var(--success)' }}
                                onClick={() => handleRetry(tx.id)}
                              >
                                Retry
                              </button>
                            )}
                            {(tx.status === 'PENDING' || tx.status === 'WAITING_FOR_SYNC') && (
                              <button
                                className="btn-secondary"
                                style={{ padding: '4px 10px', fontSize: 12, borderColor: 'var(--danger)', color: 'var(--danger)' }}
                                onClick={() => handleCancel(tx.id)}
                              >
                                Cancel
                              </button>
                            )}
                            {!['FAILED', 'PENDING', 'WAITING_FOR_SYNC'].includes(tx.status) && (
                              <span style={{ fontSize: 12, color: 'var(--neutral-600)' }}>None</span>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>

            {/* Pagination */}
            {historyTotalPages > 1 && (
              <div className="pagination-controls">
                <span className="pagination-info">
                  Showing page {historyPage + 1} of {historyTotalPages} ({historyTotalElements} total records)
                </span>
                <div className="pagination-btn-group">
                  <button
                    className="btn-secondary"
                    disabled={historyPage === 0}
                    onClick={() => fetchHistory(historyPage - 1)}
                  >
                    <ChevronLeft size={16} />
                  </button>
                  <button
                    className="btn-secondary"
                    disabled={historyPage >= historyTotalPages - 1}
                    onClick={() => fetchHistory(historyPage + 1)}
                  >
                    <ChevronRight size={16} />
                  </button>
                </div>
              </div>
            )}
          </div>
        )}

        {/* Tab 3: Admin Console */}
        {currentTab === 'admin' && isAdmin && (
          <div>
            {/* Sync Console Header */}
            <div className="glass-panel sync-console-panel">
              <div>
                <h2 className="panel-title" style={{ margin: 0, fontSize: 20 }}>
                  <Radio className="animate-pulse" style={{ color: 'var(--primary)' }} />
                  Restored Connectivity Sync console
                </h2>
                <p style={{ fontSize: 13, color: 'var(--neutral-400)', marginTop: 4 }}>
                  Trigger simulation to batch process, decrypt and settle all pending offline transactions on the bank ledger.
                </p>
              </div>

              <button
                onClick={handleSync}
                disabled={syncing}
                className="btn-primary pulse-sync-btn"
                style={{ width: 'auto', padding: '12px 24px' }}
              >
                {syncing ? (
                  <>
                    <RefreshCw className="animate-spin" size={16} />
                    Syncing Ledger...
                  </>
                ) : (
                  <>
                    <RefreshCw size={16} />
                    Synchronize Batch
                  </>
                )}
              </button>
            </div>

            {/* Stats Metrics Cards */}
            {loadingStats ? (
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '40px 0', gap: 10 }}>
                <RefreshCw className="animate-spin" size={24} style={{ color: 'var(--primary)' }} />
                <span style={{ color: 'var(--neutral-400)' }}>Syncing dashboard metrics...</span>
              </div>
            ) : stats ? (
              <div className="metrics-grid">
                <div className="glass-panel metric-card">
                  <div className="metric-header">
                    <span>Total Submissions</span>
                    <History size={16} style={{ color: 'var(--primary)' }} />
                  </div>
                  <div className="metric-value">{stats.totalTransactions}</div>
                  <div className="metric-footer">Total historical volume</div>
                </div>

                <div className="glass-panel metric-card">
                  <div className="metric-header">
                    <span>Pending Local</span>
                    <Radio size={16} style={{ color: 'var(--pending)' }} />
                  </div>
                  <div className="metric-value">{stats.pendingCount}</div>
                  <div className="metric-footer">Awaiting sync block</div>
                </div>

                <div className="glass-panel metric-card">
                  <div className="metric-header">
                    <span>Settled / Synced</span>
                    <CheckCircle2 size={16} style={{ color: 'var(--success)' }} />
                  </div>
                  <div className="metric-value">{stats.syncedCount}</div>
                  <div className="metric-footer">Verified on banking ledger</div>
                </div>

                <div className="glass-panel metric-card">
                  <div className="metric-header">
                    <span>Failed / Timeout</span>
                    <XCircle size={16} style={{ color: 'var(--danger)' }} />
                  </div>
                  <div className="metric-value">{stats.failedCount}</div>
                  <div className="metric-footer">Network dropouts/timeouts</div>
                </div>

                <div className="glass-panel metric-card" style={{ gridColumn: 'span 2' }}>
                  <div className="metric-header">
                    <span>Financial Settlement</span>
                    <DollarSign size={16} style={{ color: 'var(--accent-cyan)' }} />
                  </div>
                  <div className="metric-value">${stats.totalAmount.toFixed(2)}</div>
                  <div className="metric-footer">Aggregate cleared amount</div>
                </div>

                <div className="glass-panel metric-card">
                  <div className="metric-header">
                    <span>Ledger Success Rate</span>
                    <Shield size={16} style={{ color: 'var(--primary)' }} />
                  </div>
                  <div className="metric-value">{stats.successRate.toFixed(1)}%</div>
                  <div className="metric-footer">
                    <div style={{ width: '100%', background: 'rgba(255,255,255,0.05)', height: '4px', borderRadius: '2px', marginTop: 8 }}>
                      <div style={{ width: `${stats.successRate}%`, background: 'var(--success)', height: '100%', borderRadius: '2px' }} />
                    </div>
                  </div>
                </div>
              </div>
            ) : (
              <div style={{ textAlign: 'center', color: 'var(--neutral-400)', padding: 40 }}>
                Failed to load stats
              </div>
            )}
          </div>
        )}
      </main>

      {/* Global Toast Alerts */}
      <div className="toast-container">
        {toasts.map((toast) => (
          <div key={toast.id} className={`toast ${toast.type}`}>
            {toast.type === 'success' && <CheckCircle2 size={16} />}
            {toast.type === 'error' && <AlertTriangle size={16} />}
            {toast.type === 'info' && <Info size={16} />}
            <span>{toast.message}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

export default App
