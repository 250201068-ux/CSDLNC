package com.blockchain.experiment.controller;

import com.blockchain.experiment.repository.EthereumNodeRepository;
import com.blockchain.experiment.service.BlockMonitorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.Web3ClientVersion;

import java.math.BigInteger;
import java.util.*;

/**
 * REST controller for blockchain node operations.
 * Supports: node info, block retrieval, sync status, fault tolerance checks.
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/blockchain")
public class BlockchainController {

    private final EthereumNodeRepository nodeRepository;
    private final BlockMonitorService blockMonitorService;

    public BlockchainController(EthereumNodeRepository nodeRepository,
                                BlockMonitorService blockMonitorService) {
        this.nodeRepository = nodeRepository;
        this.blockMonitorService = blockMonitorService;
    }

    // ── PRIVATE CHAIN ───────────────────────────────────────────

    /**
     * GET /api/blockchain/nodes
     * Retrieve info for all nodes: client version, block number, peer count, online status.
     */
    @GetMapping("/nodes")
    public ResponseEntity<List<Map<String, Object>>> getAllNodeInfo() {
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (String nodeName : nodeRepository.getNodeNames()) {
            Map<String, Object> info = buildNodeInfo(nodeName);
            nodes.add(info);
        }
        return ResponseEntity.ok(nodes);
    }

    /**
     * GET /api/blockchain/nodes/{nodeName}
     * Retrieve info for a single node.
     */
    @GetMapping("/nodes/{nodeName}")
    public ResponseEntity<Map<String, Object>> getNodeInfo(@PathVariable("nodeName") String nodeName) {
        try {
            nodeRepository.getNode(nodeName); // validate name
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error("Unknown node: " + nodeName));
        }
        return ResponseEntity.ok(buildNodeInfo(nodeName));
    }

    /**
     * GET /api/blockchain/block-number
     * Retrieve current block number from all nodes.
     */
    @GetMapping("/block-number")
    public ResponseEntity<Map<String, Object>> getBlockNumbers() {
        Map<String, BigInteger> blockNumbers = blockMonitorService.getAllBlockNumbers();
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, BigInteger> entry : blockNumbers.entrySet()) {
            Map<String, Object> nodeBlock = new LinkedHashMap<>();
            boolean online = entry.getValue().compareTo(BigInteger.ZERO) >= 0;
            nodeBlock.put("blockNumber", online ? entry.getValue() : null);
            nodeBlock.put("online", online);
            result.put(entry.getKey(), nodeBlock);
        }
        return ResponseEntity.ok(result);
    }

    // ── BLOCK ───────────────────────────────────────────────────

    /**
     * GET /api/blockchain/blocks/{number}
     * Get block by number from a specified node (default: node1).
     */
    @GetMapping("/blocks/{number}")
    public ResponseEntity<Map<String, Object>> getBlockByNumber(
            @PathVariable("number") BigInteger number,
            @RequestParam(name = "node", defaultValue = "node1") String node) {
        try {
            EthBlock.Block block = nodeRepository.getBlock(node, number);
            if (block == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(blockToMap(block));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error("Unknown node: " + node));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(error(e.getMessage()));
        }
    }

    /**
     * GET /api/blockchain/blocks/hash/{hash}
     * Get block by hash from a specified node (default: node1).
     */
    @GetMapping("/blocks/hash/{hash}")
    public ResponseEntity<Map<String, Object>> getBlockByHash(
            @PathVariable("hash") String hash,
            @RequestParam(name = "node", defaultValue = "node1") String node) {
        try {
            Web3j web3j = nodeRepository.getNode(node);
            EthBlock ethBlock = web3j.ethGetBlockByHash(hash, true).send();
            EthBlock.Block block = ethBlock.getBlock();
            if (block == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(blockToMap(block));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error("Unknown node: " + node));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(error(e.getMessage()));
        }
    }

    /**
     * GET /api/blockchain/blocks/latest
     * Get the latest block from a specified node (default: node1).
     */
    @GetMapping("/blocks/latest")
    public ResponseEntity<Map<String, Object>> getLatestBlock(
            @RequestParam(name = "node", defaultValue = "node1") String node) {
        try {
            EthBlock.Block block = nodeRepository.getLatestBlock(node);
            if (block == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(blockToMap(block));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(error(e.getMessage()));
        }
    }

    // ── SYNC / DISTRIBUTED ──────────────────────────────────────

    /**
     * GET /api/blockchain/sync-status
     * Compare block heights across all nodes and report synchronization state.
     */
    @GetMapping("/sync-status")
    public ResponseEntity<Map<String, Object>> getSyncStatus() {
        Map<String, BigInteger> blockNumbers = blockMonitorService.getAllBlockNumbers();
        boolean synced = blockMonitorService.areNodesSynchronized();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("synchronized", synced);

        Map<String, Object> nodesStatus = new LinkedHashMap<>();
        for (Map.Entry<String, BigInteger> entry : blockNumbers.entrySet()) {
            Map<String, Object> nodeInfo = new LinkedHashMap<>();
            boolean online = entry.getValue().compareTo(BigInteger.ZERO) >= 0;
            nodeInfo.put("online", online);
            nodeInfo.put("blockNumber", online ? entry.getValue() : null);
            nodesStatus.put(entry.getKey(), nodeInfo);
        }
        result.put("nodes", nodesStatus);

        // Calculate block height difference
        BigInteger maxBlock = BigInteger.ZERO;
        BigInteger minBlock = null;
        for (BigInteger bn : blockNumbers.values()) {
            if (bn.compareTo(BigInteger.ZERO) >= 0) {
                if (bn.compareTo(maxBlock) > 0) maxBlock = bn;
                if (minBlock == null || bn.compareTo(minBlock) < 0) minBlock = bn;
            }
        }
        result.put("maxBlockHeight", maxBlock);
        result.put("minBlockHeight", minBlock);
        result.put("blockDifference", minBlock != null ? maxBlock.subtract(minBlock) : null);

        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/blockchain/verify-immutability/{blockNumber}
     * Verify that a specific block has the same hash across all online nodes.
     */
    @GetMapping("/verify-immutability/{blockNumber}")
    public ResponseEntity<Map<String, Object>> verifyImmutability(@PathVariable("blockNumber") BigInteger blockNumber) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("blockNumber", blockNumber);

        Map<String, String> hashes = new LinkedHashMap<>();
        for (String nodeName : nodeRepository.getNodeNames()) {
            try {
                if (nodeRepository.isNodeAlive(nodeName)) {
                    EthBlock.Block block = nodeRepository.getBlock(nodeName, blockNumber);
                    if (block != null) {
                        hashes.put(nodeName, block.getHash());
                    } else {
                        hashes.put(nodeName, null);
                    }
                } else {
                    hashes.put(nodeName, "OFFLINE");
                }
            } catch (Exception e) {
                hashes.put(nodeName, "ERROR: " + e.getMessage());
            }
        }
        result.put("blockHashes", hashes);

        // Check consistency
        String referenceHash = null;
        boolean consistent = true;
        for (Map.Entry<String, String> entry : hashes.entrySet()) {
            String hash = entry.getValue();
            if (hash == null || hash.startsWith("OFFLINE") || hash.startsWith("ERROR")) continue;
            if (referenceHash == null) {
                referenceHash = hash;
            } else if (!referenceHash.equals(hash)) {
                consistent = false;
                break;
            }
        }
        result.put("consistent", consistent);
        result.put("immutabilityVerified", consistent && referenceHash != null);

        return ResponseEntity.ok(result);
    }

    // ── FAULT TOLERANCE ─────────────────────────────────────────

    /**
     * GET /api/blockchain/fault-tolerance/check
     * Check which nodes are online/offline and test reconnection.
     */
    @GetMapping("/fault-tolerance/check")
    public ResponseEntity<Map<String, Object>> checkFaultTolerance() {
        Map<String, Object> result = new LinkedHashMap<>();
        int onlineCount = 0;
        int totalCount = 0;

        Map<String, Object> nodesHealth = new LinkedHashMap<>();
        for (String nodeName : nodeRepository.getNodeNames()) {
            totalCount++;
            Map<String, Object> health = new LinkedHashMap<>();
            boolean alive = nodeRepository.isNodeAlive(nodeName);
            health.put("online", alive);
            if (alive) {
                onlineCount++;
                try {
                    health.put("blockNumber", nodeRepository.getBlockNumber(nodeName));
                    health.put("peerCount", nodeRepository.getPeerCount(nodeName));
                    Web3j web3j = nodeRepository.getNode(nodeName);
                    Web3ClientVersion version = web3j.web3ClientVersion().send();
                    health.put("clientVersion", version.getWeb3ClientVersion());
                } catch (Exception e) {
                    health.put("error", e.getMessage());
                }
            } else {
                health.put("blockNumber", null);
                health.put("peerCount", null);
                health.put("clientVersion", null);
            }
            nodesHealth.put(nodeName, health);
        }

        result.put("nodes", nodesHealth);
        result.put("onlineNodes", onlineCount);
        result.put("totalNodes", totalCount);
        result.put("networkHealthy", onlineCount >= 3); // Majority of 5 nodes online
        result.put("allNodesOnline", onlineCount == totalCount);

        return ResponseEntity.ok(result);
    }

    // ── TRANSACTIONS ────────────────────────────────────────────

    /**
     * GET /api/blockchain/transactions/{hash}?node=node1
     * Fetch a transaction by hash with its receipt from the specified node.
     */
    @GetMapping("/transactions/{hash}")
    public ResponseEntity<Map<String, Object>> getTransactionByHash(
            @PathVariable("hash") String hash,
            @RequestParam(name = "node", defaultValue = "node1") String node) {
        try {
            Web3j web3j = nodeRepository.getNode(node);
            var txOpt = web3j.ethGetTransactionByHash(hash).send().getTransaction();
            if (txOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            var tx = txOpt.get();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("hash", tx.getHash());
            result.put("from", tx.getFrom());
            result.put("to", tx.getTo());
            result.put("value", org.web3j.utils.Convert
                    .fromWei(tx.getValue().toString(), org.web3j.utils.Convert.Unit.ETHER).toPlainString());
            result.put("valueWei", tx.getValue().toString());
            result.put("gas", tx.getGas());
            result.put("gasPrice", tx.getGasPrice());
            result.put("nonce", tx.getNonce());
            result.put("transactionIndex", tx.getTransactionIndex());
            result.put("blockNumber", tx.getBlockNumber());
            result.put("blockHash", tx.getBlockHash());
            result.put("input", tx.getInput());

            var receiptOpt = web3j.ethGetTransactionReceipt(hash).send().getTransactionReceipt();
            if (receiptOpt.isPresent()) {
                var r = receiptOpt.get();
                Map<String, Object> receipt = new LinkedHashMap<>();
                receipt.put("status", r.getStatus());
                receipt.put("gasUsed", r.getGasUsed());
                receipt.put("cumulativeGasUsed", r.getCumulativeGasUsed());
                result.put("receipt", receipt);
            }
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error("Unknown node: " + node));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(error(e.getMessage()));
        }
    }

    /**
     * GET /api/blockchain/transactions/recent?count=15&node=node1
     * Scan the last N blocks and return all transactions found.
     */
    @GetMapping("/transactions/recent")
    public ResponseEntity<Map<String, Object>> getRecentTransactions(
            @RequestParam(name = "count", defaultValue = "15") int count,
            @RequestParam(name = "node", defaultValue = "node1") String node) {
        if (count > 100) count = 100;
        try {
            Web3j web3j = nodeRepository.getNode(node);
            BigInteger latestBlockNum = nodeRepository.getBlockNumber(node);

            List<Map<String, Object>> transactions = new ArrayList<>();
            int blocksScanned = 0;
            BigInteger scanBlock = latestBlockNum;

            // Scan backwards through up to 100 blocks or until we have enough txs
            while (transactions.size() < count && blocksScanned < 100 && scanBlock.compareTo(BigInteger.ZERO) >= 0) {
                EthBlock.Block block = nodeRepository.getBlock(node, scanBlock);
                blocksScanned++;
                if (block != null && block.getTransactions() != null) {
                    for (EthBlock.TransactionResult txResult : block.getTransactions()) {
                        Object raw = txResult.get();
                        if (raw instanceof org.web3j.protocol.core.methods.response.Transaction tx) {
                            Map<String, Object> txMap = new LinkedHashMap<>();
                            txMap.put("hash", tx.getHash());
                            txMap.put("from", tx.getFrom());
                            txMap.put("to", tx.getTo());
                            txMap.put("value", org.web3j.utils.Convert
                                    .fromWei(tx.getValue().toString(), org.web3j.utils.Convert.Unit.ETHER).toPlainString());
                            txMap.put("blockNumber", tx.getBlockNumber());
                            txMap.put("blockHash", tx.getBlockHash());
                            txMap.put("gas", tx.getGas());
                            txMap.put("gasPrice", tx.getGasPrice());
                            transactions.add(txMap);
                            if (transactions.size() >= count) break;
                        }
                    }
                }
                if (scanBlock.compareTo(BigInteger.ZERO) == 0) break;
                scanBlock = scanBlock.subtract(BigInteger.ONE);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("transactions", transactions);
            result.put("blocksScanned", blocksScanned);
            result.put("latestBlock", latestBlockNum);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error("Unknown node: " + node));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(error(e.getMessage()));
        }
    }

    // ── Helpers ─────────────────────────────────────────────────

    private Map<String, Object> buildNodeInfo(String nodeName) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("node", nodeName);
        boolean alive = nodeRepository.isNodeAlive(nodeName);
        info.put("online", alive);

        if (alive) {
            try {
                Web3j web3j = nodeRepository.getNode(nodeName);
                Web3ClientVersion version = web3j.web3ClientVersion().send();
                info.put("clientVersion", version.getWeb3ClientVersion());
                info.put("blockNumber", nodeRepository.getBlockNumber(nodeName));
                info.put("peerCount", nodeRepository.getPeerCount(nodeName));
                info.put("chainId", nodeRepository.getChainId(nodeName));
            } catch (Exception e) {
                info.put("error", e.getMessage());
            }
        } else {
            info.put("clientVersion", null);
            info.put("blockNumber", null);
            info.put("peerCount", null);
            info.put("chainId", null);
        }
        return info;
    }

    /**
     * Convert an EthBlock.Block to a serializable map.
     *
     * Blocks are fetched with fullTransactionObjects=true, so each entry in
     * block.getTransactions() is a full Transaction object. We expose both:
     *   - "transactions" : list of full TX detail objects (hash, from, to, value…)
     *   - "transactionHashes" : plain list of hashes (convenient for quick lookup)
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> blockToMap(EthBlock.Block block) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("number", block.getNumber());
        map.put("hash", block.getHash());
        map.put("parentHash", block.getParentHash());
        map.put("timestamp", block.getTimestamp());
        map.put("miner", block.getMiner());
        map.put("gasUsed", block.getGasUsed());
        map.put("gasLimit", block.getGasLimit());
        map.put("size", block.getSize());
        map.put("transactionCount", block.getTransactions() != null ? block.getTransactions().size() : 0);

        List<Map<String, Object>> txDetails = new ArrayList<>();
        List<String> txHashes = new ArrayList<>();

        if (block.getTransactions() != null) {
            for (EthBlock.TransactionResult txResult : block.getTransactions()) {
                Object raw = txResult.get();
                if (raw instanceof org.web3j.protocol.core.methods.response.Transaction tx) {
                    // Full transaction object — include all relevant fields
                    Map<String, Object> txMap = new LinkedHashMap<>();
                    txMap.put("hash", tx.getHash());
                    txMap.put("from", tx.getFrom());
                    txMap.put("to", tx.getTo());
                    txMap.put("value", org.web3j.utils.Convert
                            .fromWei(tx.getValue().toString(), org.web3j.utils.Convert.Unit.ETHER)
                            .toPlainString());
                    txMap.put("valueWei", tx.getValue().toString());
                    txMap.put("gas", tx.getGas());
                    txMap.put("gasPrice", tx.getGasPrice());
                    txMap.put("nonce", tx.getNonce());
                    txMap.put("transactionIndex", tx.getTransactionIndex());
                    txMap.put("input", tx.getInput());
                    txDetails.add(txMap);
                    txHashes.add(tx.getHash());
                } else if (txResult instanceof EthBlock.TransactionHash th) {
                    // Fallback: only hash available
                    String h = th.get().toString();
                    Map<String, Object> txMap = new LinkedHashMap<>();
                    txMap.put("hash", h);
                    txDetails.add(txMap);
                    txHashes.add(h);
                }
            }
        }

        map.put("transactions", txDetails);
        map.put("transactionHashes", txHashes);
        map.put("nonce", block.getNonceRaw());
        map.put("difficulty", block.getDifficulty());
        map.put("extraData", block.getExtraData());
        return map;
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        return body;
    }
}
