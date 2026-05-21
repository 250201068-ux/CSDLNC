# Thực Nghiệm Blockchain — Bất Biến & Chịu Lỗi

Demo mạng Ethereum private 5 node với backend Spring Boot và frontend React, phục vụ thực nghiệm các tính chất của blockchain: bất biến dữ liệu, đồng bộ phân tán và chịu lỗi.

---

## Mục lục

- [Kiến trúc hệ thống](#kiến-trúc-hệ-thống)
- [Yêu cầu cài đặt](#yêu-cầu-cài-đặt)
- [Cài đặt nhanh (Docker Compose)](#cài-đặt-nhanh-docker-compose)
- [Cài đặt thủ công](#cài-đặt-thủ-công)
- [Cấu trúc project](#cấu-trúc-project)
- [API Endpoints](#api-endpoints)
- [Tài khoản được nạp sẵn ETH](#tài-khoản-được-nạp-sẵn-eth)
- [Xử lý sự cố](#xử-lý-sự-cố)

---

## Kiến trúc hệ thống

```
┌──────────────────────────────────────────────────────┐
│           Frontend React (port 3000)                 │
│  Accounts / Transactions / Block Explorer / Network  │
└──────────────────────┬───────────────────────────────┘
                       │ HTTP
┌──────────────────────▼───────────────────────────────┐
│        Backend Spring Boot (port 8080)               │
│        Web3j 4.10.3 — REST API + Swagger UI          │
└──────┬──────┬──────┬──────┬──────────────────────────┘
       │      │      │      │  JSON-RPC
  ┌────▼─┐ ┌──▼──┐ ┌▼────┐ ┌▼────┐ ┌────────┐
  │node1 │ │node2│ │node3│ │node4│ │ node5  │
  │8545  │ │8547 │ │8549 │ │8551 │ │ 8553   │
  │Signer│ │Full │ │Recov│ │Obs. │ │ Rogue  │
  └──────┘ └─────┘ └─────┘ └─────┘ └────────┘
        Mạng nội bộ Docker (eth-private-net)
        Chain ID: 2025 | Clique PoA | 5 giây/block
```

| Node | Container | Cổng RPC | Vai trò |
|------|-----------|----------|---------|
| node1 | eth-node1-validator | 8545 | Validator/Miner (Clique signer) |
| node2 | eth-node2-full | 8547 | Full node |
| node3 | eth-node3-recovery | 8549 | Test tắt/bật (fault tolerance) |
| node4 | eth-node4-observer | 8551 | Observer read-only |
| node5 | eth-node5-rogue | 8553 | Thử nghiệm node giả mạo |

---

## Yêu cầu cài đặt

| Phần mềm | Phiên bản tối thiểu | Kiểm tra |
|----------|---------------------|---------|
| Docker | 20.10+ | `docker --version` |
| Docker Compose | 2.0+ (plugin) | `docker compose version` |
| Java JDK | 17+ | `java -version` |
| Apache Maven | 3.6+ | `mvn -version` |
| Node.js *(frontend thủ công)* | 18+ | `node -v` |

> **Lưu ý**: Maven 3.9.9 đã được bundled trong `java-app/.tools/`. Có thể dùng trực tiếp nếu chưa cài Maven hệ thống.

### RAM & Disk

- RAM: tối thiểu 4 GB (khuyến nghị 8 GB)
- Disk: tối thiểu 5 GB trống

---

## Cài đặt nhanh (Docker Compose)

Cách nhanh nhất — Docker Compose sẽ build và khởi động toàn bộ hệ thống (5 node Ethereum + backend + frontend).

### Bước 1 — Clone project

```bash
git clone <repo-url>
cd "CSDLNC Demo"
```

### Bước 2 — Khởi động toàn bộ hệ thống

```bash
docker compose up --build -d
```

Lần đầu chạy sẽ mất 2–5 phút để tải image và build. Theo dõi tiến trình:

```bash
docker compose logs -f
```

### Bước 3 — Kiểm tra hệ thống đã chạy

```bash
# Xem tất cả container đang chạy
docker compose ps

# Kiểm tra backend API
curl http://localhost:8080/api/blockchain/sync-status

# Mở frontend
# Trình duyệt: http://localhost:3000
# Swagger UI:  http://localhost:8080/swagger-ui.html
```

### Dừng hệ thống

```bash
# Dừng (giữ dữ liệu)
docker compose down

# Dừng và xóa toàn bộ dữ liệu blockchain
docker compose down -v
```

---

## Cài đặt thủ công

Dùng khi cần debug từng thành phần riêng lẻ.

### Bước 1 — Khởi động mạng Ethereum

```bash
# Từ thư mục gốc project
docker compose up -d node1-validator node2-full node3-recovery node4-observer node5-rogue

# Chờ ~10 giây rồi kiểm tra nodes
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"net_peerCount","params":[],"id":1}' \
  http://localhost:8545
# Kết quả: "result":"0x4" (4 peers) là thành công
```

### Bước 2 — Build và chạy Backend

```bash
cd java-app

# Build (bỏ qua tests)
mvn clean package -DskipTests

# Chạy
java -jar target/ethereum-fault-tolerance-1.0-SNAPSHOT.jar
```

Hoặc dùng Maven bundled sẵn:

```bash
# Windows
.tools\apache-maven-3.9.9\bin\mvn.cmd clean package -DskipTests

# Linux/macOS
.tools/apache-maven-3.9.9/bin/mvn clean package -DskipTests
```

Backend sẽ chạy trên `http://localhost:8080`.

### Bước 3 — Chạy Frontend

```bash
cd frontend

# Cài dependencies (lần đầu)
npm install

# Chạy dev server
npm run dev
```

Frontend sẽ chạy trên `http://localhost:5173` (dev) hoặc `http://localhost:3000` (Docker).

#### Cấu hình URL backend

Tạo file `.env` trong thư mục `frontend/`:

```env
VITE_API_BASE_URL=http://localhost:8080
```

---

## Cấu trúc project

```
CSDLNC Demo/
├── docker-compose.yml          # Orchestration toàn bộ hệ thống
├── docker/
│   ├── genesis.json            # Cấu hình genesis block (Chain ID 2025)
│   ├── static-nodes.json       # Danh sách peer tĩnh cho P2P
│   ├── keystore/               # Keystore của validator account
│   └── password.txt            # Mật khẩu unlock validator
├── java-app/
│   ├── pom.xml                 # Maven: Spring Boot 3.3.5 + Web3j 4.10.3
│   ├── Dockerfile
│   ├── src/main/java/com/blockchain/experiment/
│   │   ├── Application.java
│   │   ├── controller/         # REST controllers (Blockchain, Transaction, Account, Experiment)
│   │   ├── service/            # Business logic (BlockMonitor, Transaction, Account, Experiment)
│   │   ├── repository/         # EthereumNodeRepository, AccountRepository
│   │   └── config/             # CORS, Exception handler
│   └── src/main/resources/
│       └── application.properties  # Cấu hình port và URL các node
├── frontend/
│   ├── src/
│   │   ├── App.jsx
│   │   ├── pages/              # AccountsList, CreateAccount, Transaction, BlockExplorer, NetworkStatus, Experiments
│   │   ├── components/         # Navbar, AccountCard, AlertMessage, LoadingSpinner
│   │   └── services/           # Axios API clients
│   └── .env.example
├── scripts/
│   ├── run-experiment.sh       # Chạy thực nghiệm có menu
│   └── verify-sync.sh          # Kiểm tra đồng bộ nhanh
└── docs/
    └── experiment-results.md
```

---

## API Endpoints

Swagger UI đầy đủ tại: `http://localhost:8080/swagger-ui.html`

### Blockchain

| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET | `/api/blockchain/nodes` | Thông tin tất cả 5 node |
| GET | `/api/blockchain/nodes/{nodeName}` | Thông tin 1 node (node1..node5) |
| GET | `/api/blockchain/sync-status` | Trạng thái đồng bộ toàn mạng |
| GET | `/api/blockchain/block/{number}` | Chi tiết block theo số |
| GET | `/api/blockchain/block/latest` | Block mới nhất |

### Tài khoản

| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET | `/api/accounts` | Danh sách tài khoản |
| POST | `/api/accounts/create` | Tạo tài khoản mới |
| GET | `/api/accounts/{address}/balance` | Số dư ETH |

### Giao dịch

| Method | Endpoint | Mô tả |
|--------|----------|-------|
| POST | `/api/transactions/send` | Gửi ETH |
| GET | `/api/transactions/{txHash}` | Chi tiết giao dịch |
| GET | `/api/transactions/history/{address}` | Lịch sử giao dịch của address |

### Thực nghiệm

| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET | `/api/experiments/network-status` | TN1 — Trạng thái mạng |
| POST | `/api/experiments/send-transaction` | TN2/TN3 — Gửi TX và mine |
| GET | `/api/experiments/immutability` | TN4 — Kiểm tra bất biến |
| GET | `/api/experiments/data-integrity` | TN5 — Toàn vẹn dữ liệu |
| GET | `/api/experiments/traceability` | TN6 — Truy vết giao dịch |
| POST | `/api/experiments/performance-test` | TN7 — Hiệu năng batch TX |
| GET | `/api/experiments/sync-test` | TN10 — Đồng bộ tổng hợp |

---

## Tài khoản được nạp sẵn ETH

Dùng cho thực nghiệm — **không dùng trên mainnet**.

| Địa chỉ | Vai trò |
|---------|---------|
| `0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266` | Validator / Người gửi |
| `0x70997970C51812dc3A010C7d01b50e0d17dc79C8` | Người nhận |
| `0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC` | Dự phòng |

---

## Xử lý sự cố

### Nodes không kết nối với nhau (peerCount = 0)

```bash
# Kiểm tra static-nodes.json đã được mount đúng chưa
docker exec eth-node1-validator cat /root/.ethereum/static-nodes.json

# Xem peers hiện tại của node1
docker exec eth-node1-validator geth attach --exec "admin.peers" /root/.ethereum/geth.ipc
```

### Backend không kết nối được Ethereum node

```bash
# Kiểm tra RPC node1 phản hồi
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}' \
  http://localhost:8545

# Kiểm tra file cấu hình
cat java-app/src/main/resources/application.properties
```

### Maven build lỗi

```bash
cd java-app

# Xóa cache và build lại
mvn clean install -U -DskipTests

# Kiểm tra phiên bản Java (cần >= 17)
java -version
```

### Port đã bị chiếm

```bash
# Kiểm tra port 8080 (Linux/macOS)
lsof -i :8080

# Windows
netstat -ano | findstr :8080
```

### Reset toàn bộ dữ liệu blockchain

```bash
docker compose down -v
docker compose up --build -d
```

---

## Thông tin kỹ thuật

| Thành phần | Chi tiết |
|------------|---------|
| Ethereum client | Geth v1.13.15-stable |
| Consensus | Clique Proof-of-Authority |
| Chain ID / Network ID | 2025 |
| Block time | 5 giây |
| Gas limit | 8,000,000 |
| Backend | Spring Boot 3.3.5 + Web3j 4.10.3 |
| Java | 17 (compile) |
| Frontend | React 18 + Vite |
| Swagger | springdoc-openapi 2.5.0 |
