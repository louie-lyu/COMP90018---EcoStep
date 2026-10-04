# EcoStep Firebase 全链路改造：Claude 施工与测试 Prompt

> 用法：将本文从“角色与目标”开始完整交给 Claude Code。不要只截取某一个阶段；Claude 必须先审计当前仓库，再按阶段实施并验证。

## 角色与目标

你是一名资深 Android/Kotlin/Firebase 工程师。请在当前 EcoStep 仓库中完成 Firebase 数据层的生产化改造，并提供可重复的自动化测试。你必须直接检查和修改仓库，而不是只给建议或伪代码。

最终目标：

1. 打通真实行程数据闭环：Tracking、Review、History 全部使用同一个 `FirestoreJourneyRepository`。
2. 将 Mission、MissionResult、Profile、跨设备偏好、EcoPoints 流水、Reward 兑换、好友关系和排行榜从 Mock/内存状态迁移到清晰的生产数据接口。
3. 保留适合本地存储的数据：天气、路线、公共交通和地图缓存不得迁移到 Firestore。
4. 使用 Firestore Security Rules、幂等写入和服务端可信计算保护积分、兑换及排行榜。
5. 提供单元测试、Firebase Emulator 集成测试、安全规则测试、离线场景测试和人工验收步骤。

不要连接或修改真实生产 Firebase 数据。自动化测试必须使用 Fake、内存实现或 Firebase Emulator。

---

## 一、开始前必须完成的仓库审计

在修改代码前，先阅读并总结以下文件的当前行为：

- `app/src/main/java/com/ecostep/app/core/di/AppContainer.kt`
- `app/src/main/java/com/ecostep/app/core/navigation/NavGraph.kt`
- `app/src/main/java/com/ecostep/app/data/firebase/FirebaseAuthRepository.kt`
- `app/src/main/java/com/ecostep/app/data/firebase/FirestoreJourneyRepository.kt`
- `app/src/main/java/com/ecostep/app/data/repository/AuthRepository.kt`
- `app/src/main/java/com/ecostep/app/data/repository/JourneyRepository.kt`
- `app/src/main/java/com/ecostep/app/data/model/JourneySummary.kt`
- `app/src/main/java/com/ecostep/app/sensors/ui/TrackingViewModel.kt`
- `app/src/main/java/com/ecostep/app/ui/viewmodels/JourneyReviewViewModel.kt`
- `app/src/main/java/com/ecostep/app/ui/viewmodels/JourneyHistoryViewModel.kt`
- `app/src/main/java/com/ecostep/app/ui/mock/MockJourneyRepository.kt`
- `app/src/main/java/com/ecostep/app/ui/mock/MockMissionRepository.kt`
- `app/src/main/java/com/ecostep/app/ui/mock/ProfileDataSource.kt`
- `app/src/main/java/com/ecostep/app/ui/mock/RewardsDataSource.kt`
- `app/src/main/java/com/ecostep/app/ui/mock/WeeklyInsightDataSource.kt`
- `app/src/main/java/com/ecostep/app/algorithm/DefaultCarbonCalculator.kt`
- `app/src/main/java/com/ecostep/app/algorithm/DefaultEcoPointsCalculator.kt`
- `app/src/main/java/com/ecostep/app/data/model/EcoMission.kt`
- `app/src/main/java/com/ecostep/app/data/model/MissionResult.kt`
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`

执行并记录基线：

```powershell
git status --short --branch
git log -8 --oneline --decorate
java -version
.\gradlew.bat test
```

环境要求：JDK 21。当前 Windows 环境可能已有 6 个 DataStore 测试因 `.preferences_pb.tmp` 原子重命名文件锁失败。必须记录真实基线，不得删除、忽略或伪造测试结果。新改动相关测试必须独立通过。

保护规则：

- 不得执行 `git reset --hard`、`git checkout -- .` 或覆盖用户现有改动。
- 不得提交 `app/google-services.json`、Firebase token、密码、服务账号或任何真实密钥。
- 不得让测试默认连接生产 Firebase。
- 不得将 UI Mock 类型直接作为 Firestore 持久化模型。
- 不得在客户端直接授予积分、修改可信余额、确认兑换或写排行榜聚合值。
- 不得把天气、路线、公共交通或地图瓦片缓存上传 Firestore。
- 不得上传高频原始加速度计/陀螺仪流；保留聚合后的 `sensorFeatures` 即可。

先输出一份不超过 20 行的审计摘要和实施顺序，然后立即施工。除非缺少真正影响架构的产品决策，否则不要停下来提问；采用本文默认值并在最终报告中列出假设。

---

## 二、必须承认并修复的当前断点

当前代码是“半接通”状态：

- 登录/注册使用真实 Firebase Auth。
- Tracking 停止记录后使用真实 `appContainer.journeyRepository` 写 Firestore。
- Journey Review 使用 `MockJourneyRepository`。
- Journey History 使用 `MockJourneyRepository` 和硬编码 `mock_user`。
- Mission、Weekly Insight、Profile、Rewards、Friends、Ranking 仍使用 Mock 或内存状态。
- `JourneySummary` 尚未持久化最终 `ecoPoints` 和 `carbonSavedGrams`。
- 仓库中没有可审计的 `firestore.rules`。

必须优先修复这种不一致，不能先做排行榜等外围功能。

---

## 三、目标分层架构

采用以下依赖方向：

```text
Compose Screen
    -> ViewModel
        -> Repository interface (data/repository)
            -> Firestore implementation (data/firebase)
            -> Fake implementation (test only)
        -> Algorithm interface (algorithm)
```

约束：

- ViewModel 只能依赖 Repository 接口，不能直接调用 `FirebaseFirestore.getInstance()`。
- `AppContainer` 负责生产依赖注入。
- 测试通过构造函数注入 Fake。
- Firestore DTO/Map 转换与 UI 模型分离。
- 所有 Firestore 映射必须向后兼容旧文档，新增字段缺失时不能崩溃。
- 所有用户私有路径必须从当前登录 UID 计算，不能信任客户端传入的 `userId`。
- 时间统一使用 UTC epoch millis；审计字段同时使用 Firestore server timestamp。
- 金额、积分、距离和碳排数值必须验证：有限、非负、范围合理。

建议新增接口：

```text
ProfileRepository
MissionRepository
MissionResultRepository
EcoPointsRepository
RewardsRepository
FriendsRepository
LeaderboardRepository
```

如现有 UI 接口命名不同，可增加 Adapter，逐步迁移；不要一次性破坏所有 UI。

---

## 四、Firestore 数据模型

### 4.1 用户私有资料

路径：

```text
users/{uid}
```

建议字段：

```text
schemaVersion: 1
displayName: string
createdAt: server timestamp
updatedAt: server timestamp
preferences: {
  routineLearningEnabled: boolean,
  missionNotificationsEnabled: boolean,
  defaultReminderMinutes: number,
  automaticJourneyDetectionEnabled: boolean,
  communityRankingEnabled: boolean
}
```

规则：

- Email 以 Firebase Auth 为唯一真实来源，不在私有资料中重复维护。
- 注册成功后幂等创建用户文档；已存在时不得覆盖用户配置。
- 设备专属设置可继续使用 DataStore；跨设备设置才写 Firestore。

### 4.2 公共资料

路径：

```text
publicProfiles/{uid}
```

建议字段：

```text
displayName: string
communityRankingEnabled: boolean
updatedAt: server timestamp
```

不得暴露 Email、精确位置、偏好或行程详情。好友搜索若需要 Email，必须通过受控后端接口，而不是允许所有客户端枚举用户文档。

### 4.3 行程

保留现有路径：

```text
users/{uid}/journeys/{journeyId}
```

现有字段必须兼容。新增：

```text
schemaVersion: 2
detectedTransportMode: string
confirmedTransportMode: string
confirmationStatus: "pending" | "confirmed"
carbonSavedGrams: number
ecoPoints: number
linkedMissionId: string | null
routePolyline: string | null
createdAt: server timestamp
updatedAt: server timestamp
```

实现规则：

- 旧 `transportMode` 字段继续读取；迁移期间可写入 `confirmedTransportMode`，读取优先级为 confirmed -> transportMode -> UNKNOWN。
- Tracking 首次写入 `detectedTransportMode`，状态为 `pending`。
- Review 确认后更新 `confirmedTransportMode` 和 `confirmationStatus=confirmed`。
- Review/History 必须使用真实 `appContainer.journeyRepository`。
- History 必须使用当前 `authRepository.currentUserId`，删除 `mock_user`。
- `createdAt` 首次写入后不得被覆盖；`updatedAt` 每次变更更新。
- `routePolyline` 只有现有产品确实需要历史地图重放时才实现；使用编码 polyline，不保存高频原始 GPS 点。若当前 UI 不消费路线，保持 null 并记录为后续扩展。
- Firestore pending offline write 的 server timestamp 可能暂时为 null，映射必须容忍。
- `saveJourney()` 不应无条件覆盖未来新增字段。区分 create/upsert 与 confirm/update，或使用 merge/update。

### 4.4 Mission 定义和实例

路径：

```text
users/{uid}/missions/{missionId}
```

不要持久化 `ui.mock.MissionPageItem`。先建立 `data/model` 中的生产模型，并为 UI 做映射。

建议字段：

```text
schemaVersion: 1
title: string
description: string
missionType: string
targetTransportMode: string | null
targetDistanceMeters: number | null
status: "suggested" | "accepted" | "active" | "completed" | "dismissed" | "archived"
recurrence: {
  type: "none" | "daily" | "weekly" | "custom",
  interval: number,
  daysOfWeek: [number],
  timezone: string
}
nextOccurrenceDate: "YYYY-MM-DD" | null
createdAt: server timestamp
updatedAt: server timestamp
```

`skip today` 不得删除 recurring mission。单独记录 occurrence。

### 4.5 MissionResult / occurrence

路径：

```text
users/{uid}/missionResults/{resultId}
```

建议字段：

```text
schemaVersion: 1
missionId: string
occurrenceDate: "YYYY-MM-DD"
accepted: boolean
completed: boolean
skipped: boolean
linkedJourneyId: string | null
actualTransportMode: string | null
actualCarbonSavingGrams: number | null
ecoPointsAwarded: number
completedAtMillis: number | null
createdAt: server timestamp
updatedAt: server timestamp
```

要求：

- `(missionId, occurrenceDate)` 必须幂等，不能重复完成或重复发积分。
- Weekly Insight 改为读取真实 `MissionResult`，再调用现有 `DefaultWeeklyCoach`。
- `DefaultEcoPointsCalculator` 只对 accepted + completed 且具有有效结果的任务发积分。

### 4.6 EcoPoints 流水

路径：

```text
users/{uid}/pointTransactions/{transactionId}
```

建议字段：

```text
type: "mission_award" | "reward_redemption" | "adjustment"
amount: integer
sourceId: string
idempotencyKey: string
createdAt: server timestamp
metadata: map
```

要求：

- 余额由流水聚合，或由可信后端事务维护摘要；流水是审计源。
- Mission 发分使用确定性 ID，例如 `mission_{resultId}`。
- Reward 扣分使用确定性/唯一兑换 ID。
- 同一 `idempotencyKey` 只能产生一次积分变化。
- 客户端只能请求操作，不能直接创建任意正积分流水。

可在私有用户文档或独立受保护摘要中维护：

```text
userStats/{uid}
  pointsBalance
  totalJourneys
  carbonSavedGrams
  completedMissions
  updatedAt
```

`userStats` 必须由 Admin SDK/Cloud Function 写入，客户端只读。

### 4.7 Reward 目录和兑换

路径：

```text
rewards/{rewardId}
users/{uid}/rewardRedemptions/{redemptionId}
```

Reward 字段：

```text
merchantName
title
description
pointsRequired
category
active
validFrom
validUntil
inventory
```

Redemption 字段：

```text
rewardId
pointsSpent
redemptionCode
status: "available" | "used" | "expired" | "cancelled"
redeemedAt
expiresAt
usedAt
```

兑换必须通过 Callable HTTPS Function 或可信后端事务：

1. 验证 Firebase ID Token。
2. 验证 reward active、有效期和库存。
3. 在事务中检查余额。
4. 创建负积分流水。
5. 创建兑换记录和不可预测的兑换码。
6. 幂等处理重试。

不得在 `RewardsViewModel` 中本地扣余额后假装兑换成功。

### 4.8 好友和请求

路径：

```text
users/{uid}/friends/{friendUid}
users/{uid}/friendRequests/{requestId}
```

请求字段至少包括：

```text
senderUid
receiverUid
status: "pending" | "accepted" | "declined" | "cancelled"
createdAt
updatedAt
```

要求：

- 只有发送者和接收者可读取请求。
- 只有发送者可取消 pending 请求。
- 只有接收者可接受/拒绝。
- 接受时通过后端原子创建双方 friend 文档并更新请求状态。
- 禁止任意客户端把自己加入别人的好友列表。

### 4.9 排行榜

路径：

```text
leaderboardStats/{uid}
leaderboards/{period}/entries/{uid}
```

要求：

- 排行数据由可信后端根据已确认行程或 MissionResult 聚合。
- 客户端不能写 `carbonSavedGrams`、排名或完成次数。
- `communityRankingEnabled=false` 的用户不得出现在社区榜。
- 好友榜只返回当前用户好友集合中的统计。
- 社区榜默认只读取前 10；当前用户不在前 10 时可单独读取自身位置。

---

## 五、可信计算边界

### 客户端可以做

- 收集传感器并形成 `JourneySummary`。
- 提交 detected/confirmed transport mode。
- 提交 Mission 操作请求。
- 读取自己的数据和允许公开的数据。
- 离线排队写入普通用户数据。

### 服务端必须做

- 最终确认并写入可信 `carbonSavedGrams`、`ecoPointsAwarded`。
- 创建积分流水并保证幂等。
- Reward 兑换、扣分、库存变更和兑换码生成。
- 好友接受后的双向关系创建。
- 排行榜聚合。

如仓库目前没有 Cloud Functions 工程：

- 新建独立 `functions/` TypeScript 工程；不要把 Admin SDK 放进 Android 客户端。
- 使用 Firebase Emulator 测试 Functions + Firestore。
- 如果当前环境无法部署，只完成代码、规则、Emulator 测试与部署说明；绝不连接生产项目。

碳减排默认采用现有算法语义：以同距离 CAR 排放为基线，`max(0, carEmissions - actualModeEmissions)`。排放因子复用现有 `DefaultCarbonCalculator` 中的已定义值或抽取为单一共享配置，禁止在多个文件复制不同常量。

---

## 六、分阶段施工计划

每个阶段完成后必须运行该阶段测试，失败则先修复，不得把所有改动堆到最后。

### Phase 1：行程闭环（必须先完成）

1. 扩展 `JourneySummary` 或引入兼容的持久化模型。
2. 扩展 Firestore mapper，兼容 schema v1 和缺失字段。
3. 将 `NavGraph` 中 Review/History 的 `MockJourneyRepository` 换成 `appContainer.journeyRepository`。
4. History 使用当前登录 UID；未登录时给出明确状态，不得回退到 mock UID。
5. Review 保存用户确认交通方式，并重新读取/更新 UI 状态。
6. 添加 create/update/confirm 的明确 Repository 方法，避免全量覆盖未知字段。
7. 验证离线保存、pending write 读取、恢复联网后同步。

Phase 1 验收：真实 Tracking 创建的 journeyId 可以立即进入 Review，Review 能读取同一条记录，确认后 History 能看到更新结果。

### Phase 2：Profile 与跨设备偏好

1. 创建生产 `ProfileRepository`。
2. 注册后幂等创建用户资料。
3. `ProfileViewModel` 改为依赖接口，通过 `AppContainer` 注入真实实现。
4. 保留 Fake 仅用于测试和 Preview。
5. 明确哪些设置用 Firestore、哪些设置用 DataStore。

### Phase 3：Mission 与 Weekly Insight

1. 把生产 Mission 模型移出 `ui.mock`。
2. 实现 Firestore MissionRepository 和 MissionResultRepository。
3. 支持 suggested/accept/dismiss/start/skip occurrence/edit/end/complete。
4. recurring mission 的 `skip today` 只写 occurrence/result，不删除定义。
5. Weekly Insight 读取真实结果。
6. 旧 Mock 数据可作为开发 seed，但不得作为生产默认数据源。

### Phase 4：EcoPoints 与 Rewards

1. 增加 Functions/可信后端积分处理。
2. 使用确定性 transaction ID 和 idempotency key。
3. 实现 Reward 目录读取与兑换 Callable Function。
4. `RewardsViewModel` 不再本地生成兑换码或直接扣余额。
5. 网络失败和重复点击必须安全；重试不得重复扣分。

### Phase 5：Friends、Ranking 与安全规则

1. 实现 FriendRequest 的受控状态机。
2. 后端原子创建双方好友关系。
3. 建立服务端排行榜聚合。
4. Profile/Ranking UI 改为真实 Repository。
5. 完成 `firestore.rules`、索引和规则测试。

---

## 七、Firestore Security Rules 要求

在仓库根目录新增：

```text
firebase.json
firestore.rules
firestore.indexes.json
```

规则至少覆盖：

1. 未登录用户不能访问用户数据。
2. `users/{uid}` 只有本人可读写允许字段。
3. `users/{uid}/journeys/**` 只有本人可读写；禁止伪造不同 `userId`。
4. 普通客户端不能写 `ecoPoints`、可信 `carbonSavedGrams` 或审计字段。
5. `pointTransactions`：客户端只读，服务端写。
6. `userStats`：客户端只读，服务端写。
7. `rewardRedemptions`：客户端只读或仅允许受限状态操作，创建由服务端完成。
8. `rewards`：登录用户可读，客户端不可写。
9. 好友请求只有 sender/receiver 可见，并验证合法状态转换。
10. 排行榜客户端只读。
11. `publicProfiles` 只暴露白名单字段。
12. 对字符串长度、数字范围、枚举值、必需字段和不可变字段做验证。

不要仅依赖 Android Repository 中的 UID 检查；Security Rules 是最终边界。

---

## 八、离线与并发行为

必须明确实现并测试：

- Firestore Android 离线持久化继续启用。
- Tracking 离线停止时，写入本地 Firestore cache，Review 可立即读取 pending document。
- UI 不应把 3 秒超时当作数据库失败；应区分 queued/synced/error。
- ViewModel 状态建议包含：`isSaving`、`isPendingSync`、`isSynced`、`errorMessage`。
- 同一 journey 的重复确认是幂等更新。
- 两台设备同时编辑用户偏好时采用明确策略：字段级 merge + last-write-wins，并通过 `updatedAt` 显示最新状态。
- Reward 兑换、积分和好友接受必须使用事务，不能依赖 last-write-wins。
- Snapshot listener 必须在 Flow 关闭时移除，避免泄漏。
- `Task.await()` 包装必须正确传播取消，但不能错误声明服务端已同步。

---

## 九、测试计划

### 9.1 JVM 单元测试

必须新增或更新：

- Firestore Journey schema v1 -> 新模型映射。
- schema v2 完整 round-trip。
- 缺少新增字段时使用安全默认值。
- 非法 transport mode 回退到 UNKNOWN。
- NaN、Infinity、负距离、负积分被拒绝。
- Tracking/Review/History 使用同一个 Fake JourneyRepository 的闭环测试。
- Review 确认更新 detected/confirmed 字段。
- Mission 状态转换测试。
- recurring mission 的 skip-today 测试。
- MissionResult 幂等测试。
- EcoPoints 只奖励一次。
- Profile 偏好更新成功与回滚测试。
- RewardsViewModel 重复点击只发一个兑换请求。
- Repository 的 auth UID 隔离测试。

### 9.2 Firebase Emulator 集成测试

必须使用 demo project ID，例如 `demo-ecostep`，不得指向生产项目。

覆盖：

- Auth emulator 注册、登录、登出和 session。
- Firestore journey create/read/update/listener。
- 用户 A 不能读取用户 B journey。
- 离线写入后恢复联网同步。
- Mission CRUD 和 occurrence 幂等。
- Callable reward redemption 成功、余额不足、reward inactive、重复请求。
- Friend request 合法和非法状态转换。
- 排行榜客户端写入被拒绝。

### 9.3 Security Rules 测试

使用 `@firebase/rules-unit-testing`，至少测试：

- unauthenticated deny。
- owner allow。
- cross-user deny。
- 客户端伪造 `userId` deny。
- 修改受保护统计/积分字段 deny。
- 非法字段、非法枚举、超长字符串和负数 deny。
- rewards read / client write deny。
- friend request participant visibility。
- leaderboard read / client write deny。

### 9.4 Android UI/导航验收

至少验证：

1. 新用户注册后进入 Home。
2. 记录行程并停止。
3. 自动进入该真实 journeyId 的 Review。
4. 修改交通方式并保存。
5. History 出现同一行程且值已更新。
6. 杀掉 App、重新打开并登录，行程仍存在。
7. 飞行模式下重复 2-4，UI 标记 pending sync；恢复网络后同步。
8. Profile 修改后重启仍保留。
9. Mission 状态重启后仍保留。
10. Reward 重复点击不会重复扣分。

### 9.5 构建命令

根据实现内容运行：

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

如果配置 Firebase 工具：

```powershell
firebase emulators:exec --project demo-ecostep "npm test"
```

无法运行设备测试或 Emulator 时，必须说明具体缺失条件，并仍然完成可运行的 JVM 测试。不得写“应该通过”。

---

## 十、代码质量和兼容性要求

- Kotlin 风格与现有仓库一致。
- 不引入 DI 框架；继续使用 `AppContainer`，除非用户明确要求。
- 不使用已弃用的 Firebase `-ktx` artifact。
- Repository 方法使用 `suspend`/`Flow`，不向 ViewModel 暴露 Firebase `Task`。
- 错误信息可面向用户，底层异常保留给日志/调试。
- 不把 Firebase SDK 类型扩散到 domain/UI 层。
- 所有 listener 都可取消。
- 所有服务端操作都幂等。
- 新增字段提供 schemaVersion 和迁移兼容。
- 不在日志中打印 ID token、密码、精确位置或完整用户数据。
- 不删除现有 Mock；将其移动到 debug/test 或作为 Fake 保留，直到真实实现覆盖所有调用方。
- 每个阶段保持项目可编译。

---

## 十一、验收清单

只有全部满足才能宣称完成：

- [ ] Tracking、Review、History 使用同一个真实 JourneyRepository。
- [ ] 没有 `mock_user` 参与生产导航。
- [ ] 用户确认后的交通方式写回 Firestore。
- [ ] journey mapper 兼容旧文档。
- [ ] Profile 和跨设备设置真实持久化。
- [ ] Mission/occurrence/result 重启后可恢复。
- [ ] Weekly Insight 使用真实 MissionResult。
- [ ] EcoPoints 使用幂等流水，不可由客户端任意增加。
- [ ] Reward 兑换由可信后端事务完成。
- [ ] Friends 状态转换受保护。
- [ ] 排行榜由服务端聚合，客户端只读。
- [ ] `firestore.rules` 和索引进入版本控制。
- [ ] Rules 测试覆盖 owner/cross-user/受保护字段。
- [ ] 自动化测试不访问生产 Firebase。
- [ ] `google-services.json` 和密钥没有被提交。
- [ ] `git diff --check` 通过。
- [ ] 新增测试通过；基线失败被如实说明。

---

## 十二、最终交付格式

完成后按以下格式报告：

1. **结果摘要**：已完成哪些 Phase，哪些未完成。
2. **架构变化**：新增 Repository、模型、Functions、Rules。
3. **Firestore schema**：实际落地的 collection/document/字段，与本文差异。
4. **安全边界**：客户端可写与服务端专属字段。
5. **测试证据**：逐条命令、通过数、失败数、失败原因。
6. **离线验证**：queued/synced 行为。
7. **迁移兼容**：旧 journey 文档如何读取。
8. **人工步骤**：用户需要在 Firebase Console/CLI 做什么。
9. **风险与后续**：不得隐藏未完成项。
10. **改动文件列表**：按模块分组。

不要自动部署生产 Functions、Rules 或索引。先完成代码和 Emulator 验证；只有用户明确授权后才能执行生产部署。
