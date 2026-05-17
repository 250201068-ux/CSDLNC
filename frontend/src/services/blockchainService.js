import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
})

// ── Accounts ────────────────────────────────────────────────────
export async function createAccount(name) {
  const params = name ? { name } : {}
  const { data } = await api.post('/api/accounts', null, { params })
  return data
}

export async function getAllAccounts() {
  const { data } = await api.get('/api/accounts')
  return data
}

export async function getAccount(address) {
  const { data } = await api.get(`/api/accounts/${address}`)
  return data
}

export async function getBalance(address) {
  const { data } = await api.get(`/api/accounts/${address}/balance`)
  return data
}

// ── Transactions (via backend) ───────────────────────────────────
export async function sendTransaction(from, to, amount) {
  const { data } = await api.post('/api/transactions', { from, to, amount: parseFloat(amount) })
  return data
}

// ── Transactions (direct Ethereum JSON-RPC — nodes have corsdomain=*) ──
const NODE_PORTS = { node1: 8545, node2: 8547, node3: 8549 }

async function ethRpc(method, params, node = 'node1') {
  const port = NODE_PORTS[node] ?? 8545
  const { data } = await axios.post(`http://localhost:${port}`, {
    jsonrpc: '2.0', method, params, id: Date.now(),
  })
  if (data.error) throw new Error(data.error.message)
  return data.result
}

function hexToNum(hex) {
  if (hex == null) return null
  return typeof hex === 'string' && hex.startsWith('0x') ? parseInt(hex, 16) : Number(hex)
}

function weiToEth(weiHex) {
  if (!weiHex) return '0'
  try {
    const eth = Number(BigInt(weiHex)) / 1e18
    return eth.toFixed(8).replace(/\.?0+$/, '') || '0'
  } catch {
    return '0'
  }
}

export async function getTransactionByHash(hash, node = 'node1') {
  const tx = await ethRpc('eth_getTransactionByHash', [hash], node)
  if (!tx) {
    const err = new Error('Transaction not found')
    err.response = { data: { error: 'Transaction not found' } }
    throw err
  }
  const receipt = await ethRpc('eth_getTransactionReceipt', [hash], node)
  return {
    hash: tx.hash,
    from: tx.from,
    to: tx.to,
    value: weiToEth(tx.value),
    gas: hexToNum(tx.gas),
    gasPrice: tx.gasPrice,
    nonce: hexToNum(tx.nonce),
    transactionIndex: hexToNum(tx.transactionIndex),
    blockNumber: hexToNum(tx.blockNumber),
    blockHash: tx.blockHash,
    input: tx.input,
    receipt: receipt ? {
      status: receipt.status,
      gasUsed: hexToNum(receipt.gasUsed),
      cumulativeGasUsed: hexToNum(receipt.cumulativeGasUsed),
    } : null,
  }
}

export async function getRecentTransactions(count = 15, node = 'node1') {
  const latestHex = await ethRpc('eth_blockNumber', [], node)
  let blockNum = hexToNum(latestHex)
  const transactions = []
  let blocksScanned = 0

  while (transactions.length < count && blocksScanned < 100 && blockNum >= 0) {
    const block = await ethRpc('eth_getBlockByNumber', ['0x' + blockNum.toString(16), true], node)
    blocksScanned++
    if (block?.transactions?.length) {
      for (const tx of block.transactions) {
        transactions.push({
          hash: tx.hash,
          from: tx.from,
          to: tx.to,
          value: weiToEth(tx.value),
          blockNumber: hexToNum(tx.blockNumber),
          blockHash: tx.blockHash,
          gas: hexToNum(tx.gas),
          gasPrice: tx.gasPrice,
        })
        if (transactions.length >= count) break
      }
    }
    blockNum--
  }

  return { transactions, blocksScanned, latestBlock: hexToNum(latestHex) }
}

// ── Blocks ──────────────────────────────────────────────────────
export async function getBlockByNumber(number, node = 'node1') {
  const { data } = await api.get(`/api/blockchain/blocks/${number}`, { params: { node } })
  return data
}

export async function getBlockByHash(hash, node = 'node1') {
  const { data } = await api.get(`/api/blockchain/blocks/hash/${hash}`, { params: { node } })
  return data
}

export async function getLatestBlock(node = 'node1') {
  const { data } = await api.get('/api/blockchain/blocks/latest', { params: { node } })
  return data
}

// ── Network / Nodes ──────────────────────────────────────────────
export async function getAllNodeInfo() {
  const { data } = await api.get('/api/blockchain/nodes')
  return data
}

export async function getSyncStatus() {
  const { data } = await api.get('/api/blockchain/sync-status')
  return data
}

export async function checkFaultTolerance() {
  const { data } = await api.get('/api/blockchain/fault-tolerance/check')
  return data
}

// ── Experiments ──────────────────────────────────────────────────
export async function runDataIntegrityExperiment(params = {}) {
  const { data } = await api.post('/api/experiments/data-integrity', params)
  return data
}

export async function runImmutabilityExperiment(blockNumber) {
  const params = blockNumber != null ? { blockNumber } : {}
  const { data } = await api.get('/api/experiments/immutability', { params })
  return data
}

export async function runSyncTest() {
  const { data } = await api.get('/api/experiments/sync-test')
  return data
}

export async function runFaultToleranceTest() {
  const { data } = await api.get('/api/experiments/fault-tolerance')
  return data
}

export async function runTraceabilityExperiment(params = {}) {
  const { data } = await api.post('/api/experiments/traceability', params)
  return data
}

export async function runPerformanceExperiment(params = {}) {
  const { data } = await api.post('/api/experiments/performance', params)
  return data
}

export async function runRogueNodeExperiment(params = {}) {
  const { data } = await api.post('/api/experiments/rogue-node', params)
  return data
}
