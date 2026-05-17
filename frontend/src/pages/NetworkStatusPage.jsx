import { useState, useEffect, useCallback } from 'react'
import {
  getAllNodeInfo,
  getSyncStatus,
  checkFaultTolerance,
} from '../services/blockchainService'
import LoadingSpinner from '../components/LoadingSpinner'
import AlertMessage from '../components/AlertMessage'

export default function NetworkStatusPage() {
  const [nodes, setNodes] = useState([])
  const [syncStatus, setSyncStatus] = useState(null)
  const [faultStatus, setFaultStatus] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [autoRefresh, setAutoRefresh] = useState(false)

  const fetchAll = useCallback(async () => {
    setError(null)
    try {
      const [nodesData, sync, fault] = await Promise.all([
        getAllNodeInfo(),
        getSyncStatus(),
        checkFaultTolerance(),
      ])
      setNodes(nodesData)
      setSyncStatus(sync)
      setFaultStatus(fault)
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to load network status')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchAll()
  }, [fetchAll])

  useEffect(() => {
    if (!autoRefresh) return
    const interval = setInterval(fetchAll, 5000)
    return () => clearInterval(interval)
  }, [autoRefresh, fetchAll])

  return (
    <div className="page">
      <div className="page-header">
        <h1>Network Status</h1>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <button className="btn btn-secondary" onClick={fetchAll} disabled={loading}>
            Refresh
          </button>
          <button
            className={`btn ${autoRefresh ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => setAutoRefresh(!autoRefresh)}
          >
            {autoRefresh ? 'Auto: ON' : 'Auto: OFF'}
          </button>
        </div>
      </div>
      <p className="page-subtitle">
        Monitor all 5 Ethereum nodes, synchronization, and fault tolerance status.
      </p>

      <AlertMessage type="error" message={error} onClose={() => setError(null)} />

      {loading ? (
        <LoadingSpinner message="Loading network status..." />
      ) : (
        <>
          {/* Node Cards */}
          <div className="node-grid">
            {nodes.map((node) => {
              const isRogue = node.node === 'node5'
              const nodeLabel = {
                node1: 'NODE1 — Validator',
                node2: 'NODE2 — Full Sync',
                node3: 'NODE3 — Recovery',
                node4: 'NODE4 — Observer',
                node5: 'NODE5 — ROGUE ⚠',
              }[node.node] || node.node.toUpperCase()

              return (
                <div
                  key={node.node}
                  className={`node-card ${node.online ? 'online' : 'offline'}`}
                  style={isRogue ? { border: '2px solid #f59e0b', boxShadow: '0 0 8px rgba(245,158,11,0.4)' } : {}}
                >
                  <div className="node-card-header">
                    <span className="node-name">{nodeLabel}</span>
                    <span className={`status-badge ${node.online ? 'badge-online' : 'badge-offline'}`}>
                      {node.online ? 'ONLINE' : 'OFFLINE'}
                    </span>
                  </div>
                  {isRogue && (
                    <div style={{ padding: '4px 12px', background: 'rgba(245,158,11,0.12)', fontSize: '0.75rem', color: '#f59e0b' }}>
                      Unauthorized node — rejected by Clique PoA validation
                    </div>
                  )}
                  <div className="node-card-body">
                    <div className="node-field">
                      <label>Block Number</label>
                      <span className="mono">{node.blockNumber ?? '—'}</span>
                    </div>
                    <div className="node-field">
                      <label>Peers</label>
                      <span>{node.peerCount ?? '—'}</span>
                    </div>
                    <div className="node-field">
                      <label>Chain ID</label>
                      <span>{node.chainId ?? '—'}</span>
                    </div>
                    <div className="node-field">
                      <label>Client</label>
                      <span className="client-version">
                        {node.clientVersion ? node.clientVersion.split('/').slice(0, 2).join('/') : '—'}
                      </span>
                    </div>
                  </div>
                </div>
              )
            })}
          </div>

          {/* Sync Status */}
          {syncStatus && (
            <div className="info-card">
              <h2>Synchronization</h2>
              <div className="info-row">
                <label>Status</label>
                <span className={syncStatus.synchronized ? 'text-success' : 'text-warning'}>
                  {syncStatus.synchronized ? 'SYNCHRONIZED' : 'OUT OF SYNC'}
                </span>
              </div>
              <div className="info-row">
                <label>Max Block Height</label>
                <span className="mono">{syncStatus.maxBlockHeight}</span>
              </div>
              {syncStatus.blockDifference > 0 && (
                <div className="info-row">
                  <label>Block Difference</label>
                  <span className="text-warning">{syncStatus.blockDifference} blocks behind</span>
                </div>
              )}
            </div>
          )}

          {/* Fault Tolerance */}
          {faultStatus && (
            <div className="info-card">
              <h2>Fault Tolerance</h2>
              <div className="info-row">
                <label>Online Nodes</label>
                <span>
                  {faultStatus.onlineNodes} / {faultStatus.totalNodes}
                </span>
              </div>
              <div className="info-row">
                <label>Network Healthy</label>
                <span className={faultStatus.networkHealthy ? 'text-success' : 'text-error'}>
                  {faultStatus.networkHealthy ? 'YES' : 'NO'}
                </span>
              </div>
              <div className="info-row">
                <label>All Nodes Online</label>
                <span className={faultStatus.allNodesOnline ? 'text-success' : 'text-warning'}>
                  {faultStatus.allNodesOnline ? 'YES' : 'NO'}
                </span>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}
