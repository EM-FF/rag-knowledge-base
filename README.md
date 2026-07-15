# rag-knowledge-base

## 整体框架设计
┌─────────────────────────────────────────────────────────────────┐
│                          用户层                                  │
│            Vue3 界面（文档管理 + 对话界面）                       │
└──────────────────┬──────────────────────┬───────────────────────┘
│ 文档上传              │ 提问（HTTP / SSE）
│                      │
┌──────────────────▼──────────────────────▼───────────────────────┐
│                       接入层（Controller）                       │
│   DocumentController          ChatController                    │
│   KnowledgeBaseController     HealthController                  │
│   ├── Sa-Token 认证拦截器（JWT Token → UserContext）             │
│   └── 知识库权限拦截器（校验用户有无该 KB 的访问权）              │
└──────────────────┬──────────────────────┬───────────────────────┘
│                      │
┌─────────────▼────────┐   ┌─────────▼──────────────────────┐
│   离线索引管道         │   │   在线查询管道                  │
│                      │   │                                 │
│ 1. 文档上传 → MinIO   │   │ 1. 查询改写（HyDE + 多路）      │
│ 2. 创建 IndexTask     │   │ 2. 混合检索                     │
│ 3. 异步执行：          │   │    ├── PGVector 向量检索        │
│    ├─ 格式解析         │   │    └── PG 全文检索              │
│    ├─ 分块            │   │ 3. RRF 融合排序                 │
│    ├─ Embedding      │   │ 4. Reranker 精排（降级兜底）    │
│    └─ 写入 PGVector   │   │ 5. 上下文裁剪（Token 预算）     │
│ 4. 更新任务状态        │   │ 6. 引用溯源组装                 │
│                      │   │ 7. 生成回答（流式）              │
└──────────────────────┘   └─────────────────────────────────┘
│                      │
┌──────────────────▼──────────────────────▼───────────────────────┐
│                       Service 层                                 │
│  IndexService    ChunkService    EmbeddingService               │
│  RagQueryService  RerankerService  PermissionService            │
└──────────────────────────────────┬──────────────────────────────┘
│
┌──────────────────────────────────▼──────────────────────────────┐
│                       数据访问层                                  │
│  KbDocumentRepository   DocChunkRepository                      │
│  ChatSessionRepository  IndexTaskRepository                     │
└──────────┬────────────────────────────────────┬─────────────────┘
│                                    │
┌──────────▼──────────┐              ┌──────────▼──────────────────┐
│   PostgreSQL         │              │   Redis 7                   │
│   + PGVector 扩展    │              │   ├── Embedding 缓存         │
│   ├── 业务表         │              │   ├── 查询结果缓存            │
│   └── 向量表         │              │   └── IndexTask 进度推送     │
└─────────────────────┘              └──────────────────────────────┘
│
┌──────────▼──────────┐
│   MinIO              │
│   原始文档存储        │
└─────────────────────┘

## 各层职责设计
### 2.1 接入层

- **Sa-Token**：解析 JWT，把 `userId / departmentId / role` 注入 `UserContext`（ThreadLocal）
- **权限拦截器**：每个涉及知识库操作的请求，校验当前用户对目标 `kb_id` 是否有权限
- **限流**：防止单用户滥用问答接口（按 userId 限制 QPS）

### 2.2 **离线索引管道**

上传文档
→ 存 MinIO（原始文件永久保存）
→ 创建 KbDocument 记录（status = PENDING）
→ 创建 IndexTask，放入任务队列

IndexTaskExecutor（异步线程池）
→ 拉取 PENDING 任务
→ 格式解析（DocumentLoader）
→ 分块（ChunkSplitter）
→ 批量 Embedding（EmbeddingService）
→ 写入 doc_chunk 表（含向量）
→ 更新 KbDocument status = DONE

失败处理：
→ status = FAILED，记录 error_msg
→ 支持手动触发重试
→ 自动重试最多 3 次（指数退避）

### 2.3 在线查询管道

用户提问
↓
① 查询改写（QueryRewriter）
└── 把用户的口语化问题标准化
└── HyDE：生成假设性回答，用它来检索（效果比原问题好）
└── 多路查询：扩展成 3 个角度不同的查询
↓
② 混合检索（HybridRetriever）
├── 向量检索：用 query embedding 在 PGVector 中检索 Top 20
└── 全文检索：用关键词在 PostgreSQL 全文索引中检索 Top 20
↓
③ RRF 融合排序（RrfFusion）
└── 两路结果合并去重，按 RRF 分数排序
↓
④ Reranker 精排（RerankerService）
├── 调用 gte-rerank API，对 Top 20 精排
└── 超时降级：Reranker 超时则用 RRF 分数直接截取 Top 5
↓
⑤ 上下文裁剪（ContextTrimmer）
└── 按 Token 预算裁剪，保证传给模型的 context ≤ 3000 Token
└── 过低置信度的 chunk 过滤掉
↓
⑥ 引用溯源组装（SourceBuilder）
└── 记录每个 chunk 来自哪个文档、哪页、哪个段落
↓
⑦ Prompt 组装 + 生成（ChatClient）
└── 流式输出（SSE）
└── 同时推送引用来源信息

## Tech Stack

- Spring Boot 3.5.1
- Spring AI 1.1.2
- Maven
- Java 21