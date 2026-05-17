package com.blockchain.experiment.service;

import com.blockchain.experiment.repository.EthereumNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.EthBlock;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

@Service
public class BlockMonitorService {
    private static final Logger logger = LoggerFactory.getLogger(BlockMonitorService.class);
    private final EthereumNodeRepository nodeRepository;

    public BlockMonitorService(EthereumNodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
    }

    public Map<String, BigInteger> getAllBlockNumbers() {
        Map<String, BigInteger> blockNumbers = new HashMap<>();
        for (String nodeName : nodeRepository.getNodeNames()) {
            try {
                if (nodeRepository.isNodeAlive(nodeName)) {
                    blockNumbers.put(nodeName, nodeRepository.getBlockNumber(nodeName));
                } else {
                    blockNumbers.put(nodeName, BigInteger.valueOf(-1));
                }
            } catch (Exception e) {
                logger.warn("Failed to get block number from {}: {}", nodeName, e.getMessage());
                blockNumbers.put(nodeName, BigInteger.valueOf(-1));
            }
        }
        return blockNumbers;
    }

    public void printSyncStatus() {
        logger.info("\n========== Blockchain Synchronization Status ==========");
        Map<String, BigInteger> blockNumbers = getAllBlockNumbers();
        for (Map.Entry<String, BigInteger> entry : blockNumbers.entrySet()) {
            if (entry.getValue().compareTo(BigInteger.ZERO) < 0) {
                logger.info("{}: OFFLINE", entry.getKey().toUpperCase());
            } else {
                logger.info("{}: Block #{}", entry.getKey().toUpperCase(), entry.getValue());
            }
        }
        logger.info("=======================================================\n");
    }

    public boolean areNodesSynchronized() {
        Map<String, BigInteger> blockNumbers = getAllBlockNumbers();
        BigInteger referenceBlock = null;

        for (Map.Entry<String, BigInteger> entry : blockNumbers.entrySet()) {
            BigInteger blockNumber = entry.getValue();
            if (blockNumber.compareTo(BigInteger.ZERO) < 0) {
                continue;
            }
            if (referenceBlock == null) {
                referenceBlock = blockNumber;
            } else if (!referenceBlock.equals(blockNumber)) {
                logger.warn("Sync mismatch: {} has block {}, expected {}",
                    entry.getKey(), blockNumber, referenceBlock);
                return false;
            }
        }
        return true;
    }

    public void waitForSync(int maxWaitSeconds) throws InterruptedException {
        logger.info("Waiting for nodes to synchronize (max {} seconds)...", maxWaitSeconds);
        int attempts = 0;
        int maxAttempts = maxWaitSeconds / 2;
        while (attempts < maxAttempts) {
            if (areNodesSynchronized()) {
                logger.info("All online nodes are synchronized.");
                printSyncStatus();
                return;
            }
            Thread.sleep(2000);
            attempts++;
        }
        logger.warn("Nodes did not synchronize within {} seconds", maxWaitSeconds);
        printSyncStatus();
    }

    public boolean verifyBlockHashConsistency(BigInteger blockNumber) {
        logger.info("\n========== Verifying Block #{} Hash Consistency ==========", blockNumber);
        Map<String, String> blockHashes = new HashMap<>();

        for (String nodeName : nodeRepository.getNodeNames()) {
            try {
                if (nodeRepository.isNodeAlive(nodeName)) {
                    EthBlock.Block block = nodeRepository.getBlock(nodeName, blockNumber);
                    if (block != null) {
                        blockHashes.put(nodeName, block.getHash());
                        logger.info("{}: {}", nodeName.toUpperCase(), block.getHash());
                    } else {
                        logger.warn("{}: Block not found", nodeName.toUpperCase());
                    }
                } else {
                    logger.info("{}: OFFLINE", nodeName.toUpperCase());
                }
            } catch (Exception e) {
                logger.error("Failed to get block from {}: {}", nodeName, e.getMessage());
            }
        }

        String referenceHash = null;
        for (String hash : blockHashes.values()) {
            if (referenceHash == null) {
                referenceHash = hash;
            } else if (!referenceHash.equals(hash)) {
                logger.error("Inconsistency detected in block hashes");
                logger.info("==========================================================\n");
                return false;
            }
        }

        if (referenceHash != null) {
            logger.info("All block hashes are consistent.");
        }
        logger.info("==========================================================\n");
        return true;
    }
}
