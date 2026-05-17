import { useState, useEffect } from 'react'
import {
  getTransactionByHash,
  getRecentTransactions,
} from '../services/blockchainService'
import LoadingSpinner from '../components/LoadingSpinner'
import AlertMessage from '../components/AlertMessage'

export default function TransactionExplorerPage() {
  const [hash, setHash] = useState('')
  const [node, setNode] = useState('node1')
  const [transaction, setTransaction] = useState(null)
  const [recentTxs, setRecentTxs] = useState(null)
  const [loading, setLoading] = useState(false)
  const [loadingRecent, setLoadingRecent] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    fetchRecent()
  }, [])

  async function fetchRecent() {
    setLoadingRecent(true)
    try {
      const data = await getRecentTransactions(15, 'node1')
      setRecentTxs(data)
    } catch (err) {
      // Silent fail, recent transactions is supplementary
    } finally {
      setLoadingRecent(false)
    }
  }

  async function handleSearch(e) {
    e.preventDefault()
    if (!hash.trim()) return
    setLoading(true)
    setError(null)
    setTransaction(null)

    try {
      const result = await getTransactionByHash(hash.trim(), node)
      setTransaction(result)
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Transaction not found')
    } finally {
      setLoading(false)
    }
  }

  function selectTx(txHash) {
    setHash(txHash)
    setTransaction(null)
    setError(null)
    // Auto-search
    getTransactionByHash(txHash, node)
      .then(setTransaction)
      .catch((err) => setError(err.response?.data?.error || 'Transaction not found'))
  }

  return (
    <div className="page">
      <h1>Transaction Explorer</h1>
      <p className="page-subtitle">Search transactions by hash or view recent activity.</p>

      {/* Search Form */}
      <form className="form-card" onSubmit={handleSearch}>
        <div className="form-group">
          <label>Transaction Hash</label>
          <input
            type="text"
            value={hash}
            onChange={(e) => setHash(e.target.value)}
            placeholder="0x..."
            required
          />
        </div>
        <div className="form-group">
          <label>Node</label>
          <select value={node} onChange={(e) => setNode(e.target.value)}>
            <option value="node1">Node 1 (Validator)</option>
            <option value="node2">Node 2 (Full)</option>
            <option value="node3">Node 3 (Recovery)</option>
          </select>
        </div>
        <button type="submit" className="btn btn-primary" disabled={loading}>
          {loading ? 'Searching...' : 'Search Transaction'}
        </button>
      </form>

      {loading && <LoadingSpinner message="Fetching transaction..." />}
      <AlertMessage type="error" message={error} onClose={() => setError(null)} />

      {/* Transaction Detail */}
      {transaction && (
        <div className="block-detail">
          <h2>Transaction Details</h2>
          <div className="detail-grid">
            <div className="detail-item">
              <label>Hash</label>
              <span className="mono">{transaction.hash}</span>
            </div>
            <div className="detail-item">
              <label>From</label>
              <span className="mono">{transaction.from}</span>
            </div>
            <div className="detail-item">
              <label>To</label>
              <span className="mono">{transaction.to}</span>
            </div>
            <div className="detail-item">
              <label>Value</label>
              <span>{transaction.value} ETH</span>
            </div>
            <div className="detail-item">
              <label>Block Number</label>
              <span>{transaction.blockNumber}</span>
            </div>
            <div className="detail-item">
              <label>Block Hash</label>
              <span className="mono">{transaction.blockHash}</span>
            </div>
            <div className="detail-item">
              <label>Gas</label>
              <span>{transaction.gas}</span>
            </div>
            <div className="detail-item">
              <label>Gas Price</label>
              <span>{transaction.gasPrice} wei</span>
            </div>
            <div className="detail-item">
              <label>Nonce</label>
              <span>{transaction.nonce}</span>
            </div>
            <div className="detail-item">
              <label>Transaction Index</label>
              <span>{transaction.transactionIndex}</span>
            </div>
          </div>

          {transaction.receipt && (
            <>
              <h3 style={{ marginTop: '1rem' }}>Receipt</h3>
              <div className="detail-grid">
                <div className="detail-item">
                  <label>Status</label>
                  <span className={transaction.receipt.status === '0x1' ? 'text-success' : 'text-error'}>
                    {transaction.receipt.status === '0x1' ? 'SUCCESS' : 'FAILED'}
                  </span>
                </div>
                <div className="detail-item">
                  <label>Gas Used</label>
                  <span>{transaction.receipt.gasUsed}</span>
                </div>
                <div className="detail-item">
                  <label>Cumulative Gas Used</label>
                  <span>{transaction.receipt.cumulativeGasUsed}</span>
                </div>
              </div>
            </>
          )}
        </div>
      )}

      {/* Recent Transactions */}
      <div style={{ marginTop: '2rem' }}>
        <div className="page-header">
          <h2>Recent Transactions</h2>
          <button className="btn btn-secondary" onClick={fetchRecent} disabled={loadingRecent}>
            {loadingRecent ? 'Loading...' : 'Refresh'}
          </button>
        </div>

        {loadingRecent ? (
          <LoadingSpinner message="Loading recent transactions..." />
        ) : recentTxs && recentTxs.transactions && recentTxs.transactions.length > 0 ? (
          <div className="table-wrapper" style={{ marginTop: '1rem' }}>
            <table className="account-table">
              <thead>
                <tr>
                  <th>Hash</th>
                  <th>From</th>
                  <th>To</th>
                  <th>Value (ETH)</th>
                  <th>Block</th>
                </tr>
              </thead>
              <tbody>
                {recentTxs.transactions.map((tx, i) => (
                  <tr key={i} onClick={() => selectTx(tx.hash)} style={{ cursor: 'pointer' }}>
                    <td><span className="mono">{truncate(tx.hash)}</span></td>
                    <td><span className="mono">{truncate(tx.from)}</span></td>
                    <td><span className="mono">{truncate(tx.to)}</span></td>
                    <td>{tx.value}</td>
                    <td>{tx.blockNumber}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="empty-state">No recent transactions found.</p>
        )}

        {recentTxs && (
          <p className="muted" style={{ marginTop: '0.5rem', fontSize: '0.85rem' }}>
            Scanned {recentTxs.blocksScanned} blocks from latest block #{recentTxs.latestBlock?.toString()}
          </p>
        )}
      </div>
    </div>
  )
}

function truncate(str, start = 8, end = 6) {
  if (!str || str.length <= start + end + 3) return str
  return `${str.slice(0, start)}...${str.slice(-end)}`
}
