import { useState } from 'react'
import {
  runDataIntegrityExperiment,
  runImmutabilityExperiment,
  runSyncTest,
  runFaultToleranceTest,
  runTraceabilityExperiment,
  runPerformanceExperiment,
  runRogueNodeExperiment,
} from '../services/blockchainService'
import LoadingSpinner from '../components/LoadingSpinner'
import AlertMessage from '../components/AlertMessage'

const EXPERIMENTS = [
  {
    id: 'data-integrity',
    title: 'Data Integrity',
    description: 'Submit a transaction and verify it is recorded consistently across all 5 nodes.',
    run: () => runDataIntegrityExperiment(),
  },
  {
    id: 'immutability',
    title: 'Data Immutability',
    description: 'Verify that block hashes are identical across all nodes (tamper-proof).',
    run: () => runImmutabilityExperiment(),
  },
  {
    id: 'sync',
    title: 'Distributed Synchronization',
    description: 'Compare block heights and peer counts across all 5 nodes.',
    run: () => runSyncTest(),
  },
  {
    id: 'fault-tolerance',
    title: 'Fault Tolerance',
    description: 'Check node health, responsiveness, and network resilience across 5 nodes.',
    run: () => runFaultToleranceTest(),
  },
  {
    id: 'traceability',
    title: 'Transaction Traceability',
    description: 'Send a transaction and trace it end-to-end across all nodes.',
    run: () => runTraceabilityExperiment(),
  },
  {
    id: 'performance',
    title: 'Performance (Batch)',
    description: 'Send batch transactions and measure throughput (TPS).',
    run: () => runPerformanceExperiment({ count: 5 }),
  },
  {
    id: 'rogue-node',
    title: '\ud83d\udea8 Rogue Node Attack',
    description:
      'Node5 (unauthorized rogue) attempts to submit a fake transaction. ' +
      'Clique PoA + ECDSA validation rejects it. All honest nodes (1\u20134) maintain identical block hashes, proving immutability.',
    run: () => runRogueNodeExperiment(),
  },
]

export default function ExperimentsPage() {
  const [running, setRunning] = useState(null)
  const [results, setResults] = useState({})
  const [error, setError] = useState(null)

  async function runExperiment(experiment) {
    setRunning(experiment.id)
    setError(null)

    try {
      const result = await experiment.run()
      setResults((prev) => ({ ...prev, [experiment.id]: result }))
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Experiment failed')
    } finally {
      setRunning(null)
    }
  }

  function clearResults() {
    setResults({})
    setError(null)
  }

  return (
    <div className="page">
      <div className="page-header">
        <h1>Experiments</h1>
        <button className="btn btn-secondary" onClick={clearResults}>
          Clear Results
        </button>
      </div>
      <p className="page-subtitle">
        Run blockchain experiments to demonstrate data integrity, immutability,
        synchronization, fault tolerance, traceability, and performance.
      </p>

      <AlertMessage type="error" message={error} onClose={() => setError(null)} />

      <div className="experiment-grid">
        {EXPERIMENTS.map((exp) => (
          <div key={exp.id} className="experiment-card">
            <div className="experiment-card-header">
              <h3>{exp.title}</h3>
              <button
                className="btn btn-primary btn-sm"
                onClick={() => runExperiment(exp)}
                disabled={running !== null}
              >
                {running === exp.id ? 'Running...' : 'Run'}
              </button>
            </div>
            <p className="experiment-desc">{exp.description}</p>

            {running === exp.id && <LoadingSpinner message="Running experiment..." />}

            {results[exp.id] && (
              <div className="experiment-result">
                <div className={`result-badge ${results[exp.id].success ? 'result-pass' : 'result-fail'}`}>
                  {results[exp.id].success ? 'PASSED' : 'FAILED'}
                </div>
                <pre className="result-json">
                  {JSON.stringify(results[exp.id], null, 2)}
                </pre>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
