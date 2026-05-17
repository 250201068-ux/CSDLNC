import { useState } from 'react'
import {
  getBlockByNumber,
  getBlockByHash,
  getLatestBlock,
} from '../services/blockchainService'
import LoadingSpinner from '../components/LoadingSpinner'
import AlertMessage from '../components/AlertMessage'

function truncate(str, start = 10, end = 6) {
  if (!str || str.length <= start + end + 3) return str
  return `${str.slice(0, start)}...${str.slice(-end)}`
}

export default function BlockExplorerPage() {
  const [searchType, setSearchType] = useState('number')
  const [searchValue, setSearchValue] = useState('')
  const [node, setNode] = useState('node1')
  const [block, setBlock] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  async function handleSearch(e) {
    e.preventDefault()
    setLoading(true)
    setError(null)
    setBlock(null)

    try {
      let result
      if (searchType === 'number') {
        result = await getBlockByNumber(searchValue, node)
      } else if (searchType === 'hash') {
        result = await getBlockByHash(searchValue, node)
      } else {
        result = await getLatestBlock(node)
      }
      setBlock(result)
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Block not found')
    } finally {
      setLoading(false)
    }
  }

  async function handleLatest() {
    setLoading(true)
    setError(null)
    setBlock(null)
    try {
      const result = await getLatestBlock(node)
      setBlock(result)
      setSearchValue(result.number?.toString() || '')
      setSearchType('number')
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to get latest block')
    } finally {
      setLoading(false)
    }
  }

  function formatTimestamp(ts) {
    if (!ts) return '—'
    return new Date(Number(ts) * 1000).toLocaleString()
  }

  return (
    <div className="page">
      <h1>Block Explorer</h1>
      <p className="page-subtitle">Search blocks by number or hash across all nodes.</p>

      <form className="form-card" onSubmit={handleSearch}>
        <div className="form-row">
          <div className="form-group" style={{ flex: 1 }}>
            <label>Search By</label>
            <select value={searchType} onChange={(e) => setSearchType(e.target.value)}>
              <option value="number">Block Number</option>
              <option value="hash">Block Hash</option>
            </select>
          </div>
          <div className="form-group" style={{ flex: 1 }}>
            <label>Node</label>
            <select value={node} onChange={(e) => setNode(e.target.value)}>
              <option value="node1">Node 1 (Validator)</option>
              <option value="node2">Node 2 (Full)</option>
              <option value="node3">Node 3 (Recovery)</option>
            </select>
          </div>
        </div>

        <div className="form-group">
          <label>{searchType === 'number' ? 'Block Number' : 'Block Hash'}</label>
          <input
            type="text"
            value={searchValue}
            onChange={(e) => setSearchValue(e.target.value)}
            placeholder={searchType === 'number' ? 'e.g. 42' : 'e.g. 0xabc...'}
            required
          />
        </div>

        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <button type="submit" className="btn btn-primary" disabled={loading}>
            {loading ? 'Searching...' : 'Search Block'}
          </button>
          <button type="button" className="btn btn-secondary" onClick={handleLatest} disabled={loading}>
            Latest Block
          </button>
        </div>
      </form>

      {loading && <LoadingSpinner message="Fetching block data..." />}
      <AlertMessage type="error" message={error} onClose={() => setError(null)} />

      {block && (
        <div className="block-detail">
          <h2>Block #{block.number}</h2>
          <div className="detail-grid">
            <div className="detail-item">
              <label>Hash</label>
              <span className="mono">{block.hash}</span>
            </div>
            <div className="detail-item">
              <label>Parent Hash</label>
              <span className="mono">{block.parentHash}</span>
            </div>
            <div className="detail-item">
              <label>Timestamp</label>
              <span>{formatTimestamp(block.timestamp)}</span>
            </div>
            <div className="detail-item">
              <label>Miner</label>
              <span className="mono">{block.miner}</span>
            </div>
            <div className="detail-item">
              <label>Gas Used / Limit</label>
              <span>{block.gasUsed} / {block.gasLimit}</span>
            </div>
            <div className="detail-item">
              <label>Size</label>
              <span>{block.size} bytes</span>
            </div>
            <div className="detail-item">
              <label>Transactions</label>
              <span>{block.transactionCount}</span>
            </div>
            <div className="detail-item">
              <label>Difficulty</label>
              <span>{block.difficulty}</span>
            </div>
            <div className="detail-item">
              <label>Nonce</label>
              <span className="mono">{block.nonce}</span>
            </div>
          </div>

          {block.transactions && block.transactions.length > 0 && (
            <div className="block-transactions">
              <h3>Transactions in Block</h3>
              <div className="table-wrapper">
                <table className="account-table">
                  <thead>
                    <tr>
                      <th>#</th>
                      <th>Hash</th>
                      <th>From</th>
                      <th>To</th>
                      <th>Value (ETH)</th>
                      <th>Gas</th>
                    </tr>
                  </thead>
                  <tbody>
                    {block.transactions.map((tx, i) => (
                      <tr key={i}>
                        <td>{i + 1}</td>
                        <td><span className="mono">{truncate(tx.hash)}</span></td>
                        <td><span className="mono">{truncate(tx.from)}</span></td>
                        <td><span className="mono">{truncate(tx.to)}</span></td>
                        <td>{tx.value}</td>
                        <td>{tx.gas?.toString()}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
