package com.blockchain.experiment.repository;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.NetPeerCount;
import org.web3j.protocol.core.methods.response.Web3ClientVersion;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Repository
public class EthereumNodeRepository {
    private static final Logger logger = LoggerFactory.getLogger(EthereumNodeRepository.class);

    private final String node1Url;
    private final String node2Url;
    private final String node3Url;
    private final String node4Url;
    private final String node5Url;

    private final Map<String, Web3j> nodes = new LinkedHashMap<>();

    public EthereumNodeRepository(
            @org.springframework.beans.factory.annotation.Value("${ethereum.node1.url:http://localhost:8545}") String node1Url,
            @org.springframework.beans.factory.annotation.Value("${ethereum.node2.url:http://localhost:8547}") String node2Url,
            @org.springframework.beans.factory.annotation.Value("${ethereum.node3.url:http://localhost:8549}") String node3Url,
            @org.springframework.beans.factory.annotation.Value("${ethereum.node4.url:http://localhost:8551}") String node4Url,
            @org.springframework.beans.factory.annotation.Value("${ethereum.node5.url:http://localhost:8553}") String node5Url) {
        this.node1Url = node1Url;
        this.node2Url = node2Url;
        this.node3Url = node3Url;
        this.node4Url = node4Url;
        this.node5Url = node5Url;
        initializeConnections();
    }

    private void initializeConnections() {
        logger.info("Initializing connections to Ethereum nodes...");
        try {
            nodes.put("node1", Web3j.build(new HttpService(node1Url)));
            logger.info("Connected to Node1 (Validator) at {}", node1Url);

            nodes.put("node2", Web3j.build(new HttpService(node2Url)));
            logger.info("Connected to Node2 (Full) at {}", node2Url);

            nodes.put("node3", Web3j.build(new HttpService(node3Url)));
            logger.info("Connected to Node3 (Recovery) at {}", node3Url);

            nodes.put("node4", Web3j.build(new HttpService(node4Url)));
            logger.info("Connected to Node4 (Observer) at {}", node4Url);

            nodes.put("node5", Web3j.build(new HttpService(node5Url)));
            logger.info("Connected to Node5 (Rogue) at {}", node5Url);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Web3j connections", e);
        }
    }

    public Web3j getNode(String nodeName) {
        Web3j node = nodes.get(nodeName);
        if (node == null) {
            throw new IllegalArgumentException("Unknown node: " + nodeName);
        }
        return node;
    }

    public Set<String> getNodeNames() {
        return nodes.keySet();
    }

    public boolean isNodeAlive(String nodeName) {
        try {
            Web3ClientVersion clientVersion = getNode(nodeName).web3ClientVersion().send();
            return clientVersion.getWeb3ClientVersion() != null;
        } catch (Exception e) {
            logger.warn("Node {} is not responsive: {}", nodeName, e.getMessage());
            return false;
        }
    }

    public BigInteger getBlockNumber(String nodeName) throws IOException {
        EthBlockNumber blockNumber = getNode(nodeName).ethBlockNumber().send();
        return blockNumber.getBlockNumber();
    }

    public EthBlock.Block getBlock(String nodeName, BigInteger blockNumber) throws IOException {
        EthBlock ethBlock = getNode(nodeName).ethGetBlockByNumber(
            DefaultBlockParameter.valueOf(blockNumber), true).send();
        return ethBlock.getBlock();
    }

    public EthBlock.Block getLatestBlock(String nodeName) throws IOException {
        EthBlock ethBlock = getNode(nodeName).ethGetBlockByNumber(
            DefaultBlockParameterName.LATEST, true).send();
        return ethBlock.getBlock();
    }

    public BigInteger getPeerCount(String nodeName) throws IOException {
        NetPeerCount peerCount = getNode(nodeName).netPeerCount().send();
        return peerCount.getQuantity();
    }

    public BigInteger getChainId(String nodeName) throws IOException {
        String chainIdHex = getNode(nodeName).ethChainId().send().getResult();
        if (chainIdHex == null) {
            throw new RuntimeException("Unable to fetch chainId from " + nodeName);
        }
        return Numeric.decodeQuantity(chainIdHex);
    }

    public void printConnectionStatus() {
        logger.info("\n========== Node Connection Status ==========");
        for (String nodeName : getNodeNames()) {
            boolean alive = isNodeAlive(nodeName);
            logger.info("{}: {}", nodeName.toUpperCase(), alive ? "ONLINE" : "OFFLINE");

            if (alive) {
                try {
                    logger.info("  Block Number: {}", getBlockNumber(nodeName));
                    logger.info("  Peer Count: {}", getPeerCount(nodeName));
                } catch (IOException e) {
                    logger.warn("  Unable to fetch details: {}", e.getMessage());
                }
            }
        }
        logger.info("============================================\n");
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down Web3j connections...");
        for (Map.Entry<String, Web3j> entry : nodes.entrySet()) {
            try {
                entry.getValue().shutdown();
                logger.info("Closed connection to {}", entry.getKey());
            } catch (Exception e) {
                logger.error("Failed to close connection to {}", entry.getKey(), e);
            }
        }
    }
}
