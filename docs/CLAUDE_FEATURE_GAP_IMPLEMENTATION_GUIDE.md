# EcoStep 功能缺口最小改动实施指南（交给 Claude）

> 文档用途：把当前已经存在但尚未接通的算法、外部 API、传感器、数据库和界面串起来。
> 
> 本文是实施说明，不代表这些功能已经完成。执行者必须以执行时的工作树为准，不能覆盖团队成员的未提交修改。

## 1. 给 Claude 的执行指令

你正在维护一个 Kotlin、Jetpack Compose、MVVM、单 `:app` 模块的 Android 项目。项目使用手工依赖注入 `AppContainer`，没有 Hilt。请遵守以下硬性约束：

1. **先检查再修改。** 开始每个阶段前运行 `git status --short`，阅读目标文件及相关测试，确认接口没有被其他成员继续修改。

2. **不得回退或覆盖现有改动。** 当前工作树包含大量未提交文件。禁止使用 `git reset --hard`、`git checkout -- <file>`、批量格式化整个项目或重写他人文件。

3. **不改架构。** 保持现有调用方向：
   
   ```text
   Screen -> ViewModel -> Repository/Coordinator -> Data source / Algorithm
                         ^
                         AppContainer 提供依赖
   ```

4. **Composable 不直接访问网络、Firestore 或算法实现。** Composable 只展示 state、发出事件。

5. **继续复用现有实现。** 不要重新实现路线 API、天气 API、公共交通 API、JourneyTracker、碳计算、AI 校验或 Firebase repository。

6. **生产路径不再使用 mock。** Preview 和单元测试可以继续使用 mock。

7. **一次只完成一个阶段。** 每阶段先编译和测试，再进入下一阶段，避免把所有修改堆进 `NavGraph.kt`。

8. **所有失败必须有 fallback。** AI、天气、路线、公交、Geocoder 和通知调度失败都不能阻止行程保存、Review 或本地非 AI 周报。

9. **保护隐私。** 不把原始 GPS 轨迹或传感器流发送给 AI；AI 只接收 `MissionContext` 中已有的聚合数据。

10. **不要为了完成本文而改变 Firestore schema。** 优先把现有 `Mission`、`MissionOccurrence`、`JourneySummary` 和 preferences 字段接起来。

## 2. 当前代码事实（修改前必须复核）

以下结论来自 2026-10-04 的工作树，执行时如有变化，应调整实现而不是强行套用本文代码片段。

| 已存在能力                                                                   | 当前断点                                                   |
| ----------------------------------------------------------------------- | ------------------------------------------------------ |
| `HomeScreen` 已有 `onStartJourney: (RouteInfo) -> Unit`，路线卡 Start 已调用它    | `NavGraph` 创建 `HomeScreen` 时没有传这个回调，因此默认空函数吞掉点击        |
| `TrackingViewModel.start()/stop()` 和 `JourneyTrackingService` 已可录制普通行程  | 目前主要通过 active mission 的 tracking panel 进入              |
| `LocationTracker` 的实时位置已经进入 `TrackingViewModel.currentLocation`         | `HomeViewModel` 天气仍使用固定墨尔本坐标                           |
| `ExternalDataRepository` 已实现天气、路线和公共交通三个生产接口                            | 手动路线搜索仍调用 `MockHomeRouteDataSource`                    |
| `DefaultCarbonCalculator`、`DefaultEcoPointsCalculator` 已存在              | Home mock 路线仍带固定估算值；Review 没显示完整低碳替代列表                 |
| `AiModule` 能提供 mission generator 和 AI weekly coach                      | `AppContainer` 从未创建 `AiModule`                         |
| `DefaultRecurringJourneyDetector` 和 `DefaultMissionTriggerPlanner` 已有测试 | 生产调用链未使用它们                                             |
| Firebase mission repositories 和 `RepositoryMissionStore` 已存在            | AI 生成的 `EcoMission` 尚未映射并写入 `MissionRepository`        |
| Profile preferences 已写入 Firestore                                       | 开关没有驱动通知、routine learning 或自动录制                        |
| Activity Recognition receiver 已存在                                       | 它只在录制服务启动后注册，无法负责“移动后自动开始录制”                           |
| Weekly Insight 页面已显示本地统计和 fallback 文案                                   | `DefaultAiWeeklyCoach` 的 `insight/action` 未进入 UI state |

## 3. 总体改动策略

将工作拆成六个可单独合并、可单独回滚的阶段：

1. **P0：普通行程入口和实时天气位置**
2. **P0：真实路线、公共交通和真实路线估算**
3. **P0：Review 低碳替代结果**
4. **P1：AI EcoMission 端到端链路**
5. **P1：任务提醒、设置生效和自动行程检测**
6. **P2：AI Weekly Coach、清理和文档状态**

建议每个阶段独立提交。`AppContainer.kt` 和 `NavGraph.kt` 是高冲突文件，应由 Chi Hong 最后合并，其他负责人尽量提交独立类、adapter、ViewModel 和测试。

---

## 4. 阶段一：普通行程入口和实时天气位置

### 4.1 普通行程 Start 按钮接入录制

负责人：Yu-Han Wang（入口）＋ Zongcheng Jiang（录制）  
主要文件：

- `app/src/main/java/com/ecostep/app/core/navigation/NavGraph.kt`
- `app/src/main/java/com/ecostep/app/ui/screens/HomeScreen.kt`（预计无需结构性修改）
- `app/src/main/java/com/ecostep/app/sensors/ui/TrackingViewModel.kt`（预计无需修改）

#### 修改步骤

1. 在 Home route 的 `HomeScreen(...)` 调用处传入 `onStartJourney`。
2. 回调收到已确认的 `RouteInfo` 后：
   - 清理旧消息；
   - 复用 `requestTrackingPermissions()`；
   - 权限已经满足时由现有 permission callback 调用 `trackingViewModel.start()`；
   - 不创建 mission，不写 `linkedMissionId`；
   - 不另外导航到旧的 `TrackingRoutes.TRACKING`，继续使用 Home 上已有的 `JourneyTrackingPanel`。
3. 调整 `showTrackingPanel` 条件：普通路线点击 Start 后、`tracking.isRecording == true` 时应立即显示；现有条件已经包含该状态，优先不改。
4. 确认 Stop 后仍通过 `savedJourneyId` 导航到 `JourneyReviewScreen`。

#### 注意

- `requestTrackingPermissions()` 是否在权限已存在时直接调用 `TrackingViewModel.start()`，必须阅读 `rememberTrackingPermissionRequest` 的当前实现后确认。
- `RouteInfo` 当前不需要写进 Journey；录制结果由真实 GPS 生成。不要为了保存“计划路线”扩大 Firestore schema。
- mission Start 流程保持原样：先 `missionStore.startMission()`，随后录制，这样 `RecordedJourneySaver` 才能取得 active mission ID。

#### 展示和验收

- Home → 搜索路线 → Directions → Start。
- 显示已有 `JourneyTrackingPanel`，距离和时间开始变化。
- End 后打开 Review；该 journey 的 `linkedMissionId == null`。
- active mission 的录制流程不回归。

### 4.2 天气使用实时位置

负责人：Yu-Han Wang；位置流由 Zongcheng Jiang 提供  
主要文件：

- `app/src/main/java/com/ecostep/app/ui/viewmodels/HomeViewModel.kt`
- `app/src/main/java/com/ecostep/app/core/navigation/NavGraph.kt`

#### 推荐最小接口

在 `HomeViewModel` 增加幂等入口，例如：

```kotlin
fun updateCurrentLocation(location: GeoPoint)
```

行为要求：

1. 更新 `HomeUiState.currentLocation`。
2. 只有坐标发生有意义变化时才重新加载天气，避免每个 GPS sample 请求一次网络。
3. 可使用简单距离/经纬度阈值，或者只在 ViewModel 生命周期第一次拿到 live location 时刷新。
4. 固定墨尔本坐标只作为没有权限、尚无定位或 Preview 的 fallback。
5. 保持 `ExternalDataRepository` 自带缓存策略，不要在 ViewModel 再建第二套缓存。

在 `NavGraph`：

```kotlin
LaunchedEffect(liveLocation) {
    liveLocation?.let(homeViewModel::updateCurrentLocation)
}
```

#### 展示和验收

- Home 天气卡显示设备当前位置天气。
- 拒绝位置权限时不崩溃，继续显示 fallback 或 unavailable 状态。
- 快速连续位置更新不会产生连续天气请求。

---

## 5. 阶段二：真实路线、公共交通和真实估算

### 5.1 替换 `MockHomeRouteDataSource`

负责人：Jianing Xia（API）＋ Yu-Han Wang（UI flow）  
主要文件：

- `app/src/main/java/com/ecostep/app/ui/mock/HomeRouteDataSource.kt`
- 建议新增：`app/src/main/java/com/ecostep/app/ui/adapters/RepositoryHomeRouteDataSource.kt`
- `app/src/main/java/com/ecostep/app/ui/viewmodels/HomeViewModel.kt`
- `app/src/main/java/com/ecostep/app/core/navigation/NavGraph.kt`

#### 最小接口调整

当前无参数接口无法调用生产路线服务：

```kotlin
suspend fun getRouteOptions(): List<HomeRouteOption>
```

将其改为输入解析后的坐标：

```kotlin
suspend fun getRouteOptions(
    start: GeoPoint,
    end: GeoPoint,
): HomeRouteSearchResult
```

推荐返回一个聚合结果，以免路线和公交分别触发多套 loading/error state：

```kotlin
data class HomeRouteSearchResult(
    val routes: List<HomeRouteOption>,
    val publicTransportOptions: List<PublicTransportInfo> = emptyList(),
)
```

如果团队希望改动更小，也可以保留返回 `List<HomeRouteOption>`，另给 `HomeViewModel` 注入 public transport lookup。不要让 `HomeScreen` 直接调用 repository。

#### 地名解析位置

`PlaceNameResolver.locate(query, near)` 已经存在。生产 adapter 或 ViewModel 应按以下规则解析：

- `startLocation` 为空或等于 `Current location`：使用 live `currentLocation`；
- 用户输入起点：调用 `locate(startText, near = currentLocation)`；
- 终点：调用 `locate(destination, near = startPoint)`；
- 任一解析失败：显示明确错误，例如 `Could not find that starting point.`，不要回落到墨尔本假坐标。

建议 adapter 接收两个函数，而不是持有 Android `Context`：

```kotlin
class RepositoryHomeRouteDataSource(
    private val externalDataRepository: ExternalDataRepository,
    private val locate: suspend (String, GeoPoint) -> GeoPoint?,
    private val carbonCalculator: CarbonCalculator,
    private val ecoPointsCalculator: EcoPointsCalculator,
)
```

如果 EcoPoints 规则要求 mission ID，普通路线预计积分必须明确标注为 estimate，不能伪装成已授予积分。若现有 calculator 对无 mission 行程返回 0，则 Home 应显示 0 或隐藏积分，不得硬编码正数。

#### 真实路线转换

对 `externalDataRepository.getRouteOptions(start, end)` 返回的每个 `RouteInfo`：

1. 构造临时 `JourneySummary` 或增加一个纯计算 helper，以路线距离和 mode 调用 `DefaultCarbonCalculator`。
2. Home 的 `estimatedCarbonSavedKg` 应采用“相对汽车”的节省量，而不是当前 mode 的 emissions。
3. EcoPoints 必须复用 `DefaultEcoPointsCalculator` 的同一规则；如果该规则只奖励已完成 mission，应按产品规则显示 0，不能另造公式。
4. 保留 `RouteInfo.path`，Home map 使用真实 geometry。

#### `NavGraph` 修改

- 删除生产路径的 `remember { MockHomeRouteDataSource() }`。
- 用 `remember(appContainer, placeNameResolver)` 创建 production adapter，或由 `AppContainer` factory 提供。
- Preview/test 仍可显式传 `MockHomeRouteDataSource`。

### 5.2 公共交通时刻表显示

负责人：Jianing Xia（数据）＋ Yu-Han Wang（UI）  
主要文件：

- `HomeViewModel.kt`
- `HomeScreen.kt`
- production Home route adapter

#### State 建议

```kotlin
data class HomeUiState(
    // existing fields...
    val publicTransportOptions: List<PublicTransportInfo> = emptyList(),
    val publicTransportErrorMessage: String? = null,
)
```

#### 行为

1. 路线和公共交通使用同一组解析后的 start/end。
2. 两个请求可在同一个 ViewModel coroutine 中用 `async` 并行，但一个失败不应取消另一个的成功结果。
3. 只有选择 `PUBLIC_TRANSPORT` 时显示时刻表。
4. 过滤已经发车的结果，按 `departureTimeMillis` 排序，初版显示最早 3 条即可。
5. 显示字段：`line`、本地出发时间、`estimatedDurationSeconds`。
6. 公交 API 不可用时仍展示 walking/cycling/car 路线，并在 PT 区域显示 unavailable 文案。

#### 展示和验收

- Home 路线卡显示生产 API 的距离、时长和真实路径。
- 选择 Public Transport 后显示真实线路及班次。
- 断网时使用 repository 现有缓存；缓存也不可用时显示错误而非 mock 数据。
- Production navigation 中搜索不到 `MockHomeRouteDataSource()` 的实例化。

---

## 6. 阶段三：Review 低碳替代方案

负责人：Duo Lyu（计算）＋ Yu-Han Wang（展示）  
主要文件：

- `app/src/main/java/com/ecostep/app/ui/viewmodels/JourneyReviewViewModel.kt`
- `app/src/main/java/com/ecostep/app/ui/screens/JourneyReviewScreen.kt`
- `app/src/main/java/com/ecostep/app/core/navigation/NavGraph.kt`
- `app/src/main/java/com/ecostep/app/core/di/AppContainer.kt`

### 6.1 不要重复计算公式

当前 Review 的 `withImpact()` 直接调用 `EmissionFactors.carbonSavedVersusCarGrams(...)`。应注入已有 `CarbonCalculator`：

```kotlin
private val carbonCalculator: CarbonCalculator = DefaultCarbonCalculator()
```

为了获得用户当前选择 mode 的结果，应创建 `journey.copy(transportMode = mode)` 后调用 calculator。扩展 UI state：

```kotlin
val emissionsGrams: Double = 0.0
val lowerCarbonAlternatives: List<CarbonAlternative> = emptyList()
```

现有 `carbonSavedKg` 有不同语义：它是相对汽车或后端已验证的 saving；不要把 `CarbonResult.emissionsGrams` 直接写入它。

### 6.2 UI 展示

在交通方式选择区和 Confirm 按钮之间增加小型 `LowerCarbonAlternativesCard`：

- 标题：`Lower-carbon alternatives`；
- 每行显示交通方式和 `savingsGrams / 1000` kg CO₂；
- 按 savings 从高到低；
- 没有替代方式时不显示卡片；
- `readOnly` 页面也可显示，帮助历史详情解释影响；
- 明确使用 `Estimated` 标签，后端 verified 值仍使用现有标识。

### 6.3 验收

- Car 行程至少能看到 calculator 返回的低碳方式。
- Walking 行程通常没有更低碳选项，页面不显示空卡。
- 用户切换确认方式时 alternatives 同步更新。
- 原有 backend verified carbon/EcoPoints 优先级不变。
- `DefaultCarbonCalculatorTest` 和 Review ViewModel 测试通过。

---

## 7. 阶段四：AI EcoMission 端到端链路

负责人：Duo Lyu（算法和映射）＋ Chi Hong Tam（集成）＋ Jianing Xia（外部数据）  
这是风险最高的一段，不要直接把所有逻辑写进 `NavGraph`。

### 7.1 在 `AppContainer` 实例化 AI

主要文件：`app/src/main/java/com/ecostep/app/core/di/AppContainer.kt`

增加单例：

```kotlin
val aiModule: AiModule by lazy {
    AiModule(okHttpClient)
}

val carbonCalculator: CarbonCalculator by lazy {
    DefaultCarbonCalculator()
}

val recurringJourneyDetector: RecurringJourneyDetector by lazy {
    DefaultRecurringJourneyDetector()
}
```

如果这些属性已经被其他改动加入，复用现有实例，不要重复创建。

### 7.2 新增轻量协调器

建议新增：

`app/src/main/java/com/ecostep/app/core/integration/MissionGenerationCoordinator.kt`

它负责 orchestration，不负责重新实现算法。建议依赖：

```kotlin
class MissionGenerationCoordinator(
    private val authRepository: AuthRepository,
    private val journeyRepository: JourneyRepository,
    private val missionRepository: MissionRepository,
    private val externalDataRepository: ExternalDataRepository,
    private val recurringJourneyDetector: RecurringJourneyDetector,
    private val carbonCalculator: CarbonCalculator,
    private val missionGenerator: DefaultAiMissionGenerator,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
)
```

对外只暴露一个容易测试的 suspend 方法：

```kotlin
suspend fun onJourneyConfirmed(journey: JourneySummary): MissionGenerationOutcome
```

`MissionGenerationOutcome` 应区分：created、not recurring、no alternative、disabled、failed/fallback 等，便于测试和日志；不要把异常直接显示为 Review 保存失败。

### 7.3 生成流程

严格按以下顺序：

1. 检查已登录用户，并验证 `journey.userId` 与当前用户一致。
2. 读取用户 preferences；`routineLearningEnabled == false` 时立即返回 disabled。
3. 从 `JourneyRepository.observeJourneyHistory(uid).first()` 取得近期历史。
4. 调用 `DefaultRecurringJourneyDetector.detect(journey, history)`。
5. 没有 pattern 时正常返回 `not recurring`，不是错误。
6. 调用 `DefaultCarbonCalculator.calculate(journey)`。
7. 没有 `lowerCarbonAlternatives` 时返回 `no alternative`。
8. 获取外部数据：
   - weather：建议以 journey start 或 end location 获取；
   - route：start → end；选择与当前 mode 匹配的 route，找不到时用 journey 距离和耗时构造 fallback `RouteInfo`；
   - PT：只有 lower-carbon candidates 包含 public transport 时必须请求，失败时传空列表，让 generator 过滤 PT candidate；
   - 外部数据失败不应破坏 journey confirmation。
9. 从 journey 已有字段构造 `TransportResult`：
   - mode 使用 confirmed/effective `transportMode`；
   - confidence 若当前模型没有持久化字段，使用保守值并在代码注释说明，或从现有 classifier result 接入；不得假装是后端 verified confidence；
   - `alternativesConsidered` 使用 carbon candidates 的 modes。
10. 构造 `MissionContext`，其中 `recentJourneyHistory` 必须限制数量和时间范围，避免把完整历史发送给 AI。
11. 调用 `missionGenerator.generateRecommendation(context)`。该实现已经负责两次 AI 尝试、校验和非 AI fallback。
12. 把 `EcoMission` + recurring pattern + journey endpoint 转换为生产 `Mission`。
13. 调用 `missionRepository.createMission(mission)`。

### 7.4 `EcoMission` → `Mission` 映射规则

建议单独放纯函数，便于 Duo 写测试，例如：

`app/src/main/java/com/ecostep/app/ui/adapters/EcoMissionMapper.kt` 或 `core/integration/EcoMissionMapper.kt`。

映射要求：

| `Mission` 字段                     | 来源                                                                                 |
| -------------------------------- | ---------------------------------------------------------------------------------- |
| `missionId`                      | `EcoMission.missionId`；保持 deterministic，防止同一 journey 重复创建                          |
| `title`                          | `startLabel → destinationLabel` 或短的推荐标题                                            |
| `description`                    | `EcoMission.explanation`                                                           |
| `startLabel`, `destinationLabel` | `PlaceNameResolver.resolve()`，失败时使用简短坐标文本                                          |
| `targetTransportMode`            | `EcoMission.recommendedMode`                                                       |
| `targetDistanceMeters`           | reference journey distance                                                         |
| `status`                         | `MissionStatus.SUGGESTED`                                                          |
| `recurrence`                     | `MissionRecurrence.weekly(pattern.activeDays, zoneId.id)`                          |
| `scheduledMinuteOfDay`           | `pattern.typicalDepartureMinuteOfDay`                                              |
| `nextOccurrenceDate`             | 按 recurrence 和当前日期计算                                                               |
| `estimates`                      | 至少包含推荐 mode；carbon saving 来自 matching `CarbonAlternative`；EcoPoints 用共享 calculator |

不要把 AI 返回的任意 title、时间、路线或数值直接作为可信数据。推荐 mode 必须已由 validator 限制在 calculator candidates 中。

### 7.5 接入触发点

现有 `JourneyReviewViewModel` 已有：

```kotlin
onJourneyConfirmed: suspend (JourneySummary) -> Unit
```

在 `NavGraph` 原有 mission completion 逻辑之后调用 coordinator：

```kotlin
onJourneyConfirmed = { journey ->
    journey.linkedMissionId?.let { missionId ->
        missionStore.completeOccurrence(missionId, journey.journeyId)
    }
    missionGenerationCoordinator.onJourneyConfirmed(journey)
}
```

要求：

- `confirmTransportMode` 保存成功是主流程；AI 失败不能让 `saveJourney()` 报失败。
- coordinator 内部捕获非 cancellation 异常并返回 outcome。
- 避免对同一 confirmed snapshot 重复生成；deterministic mission ID + repository create semantics 是最后防线，最好再在 coordinator 做幂等检查。

### 7.6 展示位置

- 新 mission：Missions 页 `Suggested Mission`。
- 用户接受：Missions upcoming 列表和 Home upcoming 卡片。
- AI 失败：同一个位置显示 fallback mission，不弹错误阻断 Review。
- 调试信息只写简短类型日志，不记录 prompt、精确坐标、token 或用户历史。

### 7.7 测试

至少增加：

1. 非重复路线不创建 mission。
2. routine learning 关闭不创建 mission。
3. 无低碳 candidate 不创建 mission。
4. PT API 失败时仍可选 walking/cycling candidate。
5. AI 失败时 fallback mission 被持久化。
6. 相同 journey 重复回调不重复创建。
7. mapping 的 recurrence、时间、mode 和 estimates 正确。
8. journey confirmation 成功但 mission generation 失败时 Review 仍显示保存成功。

---

## 8. 阶段五：触发计划、提醒、设置生效和自动检测

### 8.1 Mission reminder scheduler

负责人：Duo Lyu / Rui Fang（触发规则）＋ Yu-Han Wang（通知体验）＋ Chi Hong Tam（集成）

建议新增文件：

- `core/notifications/MissionReminderScheduler.kt`
- `core/notifications/AndroidMissionReminderScheduler.kt`
- `core/notifications/MissionReminderReceiver.kt`

接口保持平台实现可替换：

```kotlin
interface MissionReminderScheduler {
    fun schedule(mission: Mission, trigger: MissionTrigger, title: String, message: String)
    fun cancel(missionId: String)
    fun cancelAll()
}
```

#### 调度来源

1. `DefaultMissionTriggerPlanner` 使用 recurring pattern 或从 persisted `Mission` 重建等价 pattern。
2. notification lead time 使用 profile 的 `defaultReminderMinutes`，不要固定 5 分钟；可创建对应参数的 planner 实例。
3. 在以下事件后重新调度：
   - suggested mission 被接受；
   - mission schedule 被编辑；
   - mission 被 dismiss/archive；
   - reminder 开关或 lead time 改变；
   - 登录后恢复当前用户的 accepted missions。
4. 开关关闭或登出时取消该用户所有 mission reminders。

#### Android 约束

- Manifest 已声明 `POST_NOTIFICATIONS`，但 Android 13+ 仍需 runtime permission。
- 使用独立 channel，例如 `mission_reminders`，不要复用 journey foreground channel。
- 如果使用 `AlarmManager`，优先非 exact alarm；除非确有必要，不增加 exact-alarm permission。
- `PendingIntent` requestCode 应由稳定 mission ID 派生，更新同一任务时覆盖旧计划。
- 点击通知打开 `MainActivity`，并尽可能带 route extra 导向 Missions/Home；若现有导航不支持 deep link，初版打开 app 即可。
- 重启恢复不是 P0；若实现，增加 BOOT receiver 前必须说明权限和测试。

### 8.2 Preferences 真正驱动功能

负责人：Yu-Han Wang（设置）＋ Chi Hong Tam（协调）

不要从 `ProfileScreen` 直接调用 scheduler。建议在 app scope 添加 `UserPreferenceCoordinator`，观察 `ProfileRepository.observeProfile()`（以当前实际接口为准）：

| Preference                         | 生效行为                                        |
| ---------------------------------- | ------------------------------------------- |
| `routineLearningEnabled`           | AI mission coordinator 在生成前检查；关闭时不再学习或生成新任务 |
| `missionNotificationsEnabled`      | 开启时恢复提醒，关闭时取消提醒                             |
| `defaultReminderMinutes`           | 重新计算并调度所有 accepted mission                  |
| `automaticJourneyDetectionEnabled` | 启停自动检测协调器                                   |
| `communityRankingEnabled`          | 保持现有 Profile/leaderboard 行为，不在本任务扩大范围       |

Profile UI 应增加必要状态：

- Android 13+ 通知权限未授予但开关打开：显示 supporting/error text，并提供请求或跳转设置入口；
- 精确定位或 Activity Recognition 权限缺失时，Automatic Detection 不应静默显示为“工作中”；
- Firestore 写入失败继续使用现有错误显示。

### 8.3 自动行程检测

负责人：Zongcheng Jiang（检测和录制）＋ Yu-Han Wang（开关/权限）

当前 `ActivityRecognitionReceiver` 由 `JourneyTrackingService.startActivityRecognition()` 注册，因此只有录制开始后才收到 activity updates。要实现自动开始，必须在录制服务之外注册。

建议新增：

- `sensors/autodetect/AutoJourneyDetectionCoordinator.kt`
- 如有必要，新增专用 receiver；不要让现有 receiver 同时承担太多生命周期逻辑。

#### 最小可接受行为

1. 用户登录、开关启用且权限完整时，coordinator 请求低频 Activity Recognition updates。
2. 单次结果不能立刻开始录制。至少要求连续两个高置信 moving event，或维持移动约 20–30 秒，降低误触发。
3. 检测到 `WALKING`、`ON_BICYCLE` 或 `IN_VEHICLE` 后启动已有 `JourneyTrackingService.ACTION_START`。
4. 必须通过与 `TrackingViewModel.start()` 相同的前置条件：登录、fine location permission、当前没有录制。
5. 自动录制仍使用现有 foreground notification，使用户能看到并进入 app。
6. 初版不要自动停止并静默保存。优先让用户从 Home panel 手动 End/Abort，以免后台误保存；自动停止可作为后续增强。
7. 开关关闭、登出或权限撤销时移除 activity updates。
8. coordinator 不能直接创建 `JourneySummary`；保存继续走现有 `TrackingViewModel`/`RecordedJourneySaver` 逻辑。若后台停止必须保存，则先抽取可共享 recording session controller，不能复制保存逻辑。

#### 产品限制必须写进 UI/文档

Android 对后台位置和服务启动有限制。如果不申请 background location，则“app 完全退出后自动检测”可能不可保证。为了最小改动和合规，第一版可以定义为：用户已打开 app/session 活跃时自动检测。不要偷偷增加 `ACCESS_BACKGROUND_LOCATION`。

#### 验收

- 开关关闭：不注册 activity updates，不自动开始。
- 开关开启且权限满足：持续移动满足阈值后启动录制和 foreground notification。
- 单个误报不启动。
- 已在录制时不会二次 start。
- 登出/关闭开关后清理订阅。

---

## 9. 阶段六：AI Weekly Coach、清理和状态文档

### 9.1 AI Weekly Coach

负责人：Duo Lyu（生成）＋ Yu-Han Wang（UI）

主要文件：

- `ui/viewmodels/WeeklyInsightViewModel.kt`
- `ui/screens/WeeklyInsightScreen.kt`
- `core/navigation/NavGraph.kt`
- `core/di/AppContainer.kt`

#### State 调整

```kotlin
data class WeeklyInsightUiState(
    val report: WeeklyCoachReport? = null,
    val insight: String? = null,
    val action: String? = null,
    val usedAi: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)
```

把 ViewModel 依赖从同步 `WeeklyCoach` 改为可执行 AI 的 suspend 入口。最小方案是直接注入 `DefaultAiWeeklyCoach`；更易测试的方案是定义小接口 `WeeklyCoachingGenerator` 并让它实现。

加载时：

1. 从现有 `WeeklyInsightDataSource` 取得 results；
2. 调用 `aiWeeklyCoach.generate(results, weekStart, weekEnd)`；
3. `report` 继续供统计卡使用；
4. `insight` 替代 `report.summary`；
5. `action` 替代 `report.fallbackMessage`；
6. AI 失败时 `DefaultAiWeeklyCoach` 自己返回 fallback，页面不应进入 error；只有数据源失败才显示 load error。

#### 展示

- `This week` 卡显示 `insight`；
- `Coaching tip` 卡显示 `action`；
- 可用一个很轻的 `AI personalised` 标签表示 `usedAi == true`，不要影响布局；
- 无活动时显示本地 fallback。

### 9.2 删除未使用 Placeholder

负责人：Chi Hong Tam

在再次运行全仓库引用搜索后：

```bash
rg "PlaceholderScreen" app/src/main docs
```

若生产代码仍无调用：

1. 删除 `ui/screens/PlaceholderScreen.kt`；
2. 更新 `TEAM_GUIDE.md` 中声称页面仍使用 Placeholder 的过时说明；
3. 不删除任何实际 screen。

### 9.3 更新任务状态

负责人：各负责人更新，Chi Hong Tam 汇总

更新 `docs/WORK_PLAN.md` 时必须使用真实状态：

- 有实现和测试：`Done`；
- 已实现但等待合并/设备测试：`Review`；
- 仍在编码：`In Progress`；
- 不得因为本文列出方案就标记 `Done`。

Evidence 填具体文件、测试类或设备测试记录，不写“已完成”这种不可验证描述。

---

## 10. 文件级修改清单

| 文件/新文件                                             | 推荐改动                                                                            | 主负责人            |
| -------------------------------------------------- | ------------------------------------------------------------------------------- | --------------- |
| `core/navigation/NavGraph.kt`                      | 普通 Start 回调、live location 注入、production adapter、Review coordinator、AI weekly 注入 | Chi Hong 合并     |
| `core/di/AppContainer.kt`                          | 提供 AI、calculator、detector、coordinator、scheduler 单例                              | Chi Hong        |
| `ui/viewmodels/HomeViewModel.kt`                   | live location、真实 route/PT state、错误和 loading                                     | Yu-Han          |
| `ui/screens/HomeScreen.kt`                         | PT timetable 展示；尽量不改变主布局                                                        | Yu-Han          |
| `ui/adapters/RepositoryHomeRouteDataSource.kt`     | 地理编码、生产路线/PT、真实 estimates                                                       | Jianing＋Duo     |
| `ui/viewmodels/JourneyReviewViewModel.kt`          | 注入 calculator，输出 alternatives                                                   | Duo             |
| `ui/screens/JourneyReviewScreen.kt`                | alternatives card                                                               | Yu-Han          |
| `core/integration/MissionGenerationCoordinator.kt` | confirmed journey → context → AI/fallback → Firestore                           | Chi Hong＋Duo    |
| `core/integration/EcoMissionMapper.kt`             | `EcoMission` → production `Mission` 的纯映射                                        | Duo             |
| `core/notifications/*`                             | mission 通知 channel、schedule、receiver                                            | Yu-Han＋Chi Hong |
| `sensors/autodetect/*`                             | service 外 activity recognition 生命周期                                             | Zongcheng       |
| `ui/viewmodels/WeeklyInsightViewModel.kt`          | AI coaching result state                                                        | Duo             |
| `ui/screens/WeeklyInsightScreen.kt`                | insight/action 展示                                                               | Yu-Han/Duo      |
| `docs/WORK_PLAN.md`                                | 真实状态和 evidence                                                                  | 全员/Chi Hong     |

## 11. 测试策略

### 11.1 单元测试

优先给新协调器使用 fake interfaces，不使用真实 Firebase、Retrofit、Geocoder 或 Android notification service。

建议新增测试：

- `HomeViewModelTest`
  - live location 刷新天气；
  - destination 为空不请求路线；
  - route 成功/PT 失败仍显示 route；
  - stale request 不覆盖新 search（如实现 query token）。
- `RepositoryHomeRouteDataSourceTest`
  - current location 和地名解析；
  - calculator mapping；
  - 无法解析时明确失败。
- `JourneyReviewViewModelTest`
  - mode 改变时 alternatives 改变；
  - verified value 优先级保持。
- `MissionGenerationCoordinatorTest`
  - 见 7.7 的八个场景。
- `EcoMissionMapperTest`
  - recurrence、mode、schedule、estimate 和 ID。
- `WeeklyInsightViewModelTest`
  - AI result 和 fallback result。
- scheduler 测试
  - preference 关闭取消；
  - lead time 修改重新计划；
  - 相同 mission 覆盖旧 alarm。
- auto detection 状态机测试
  - 连续阈值、误报、重复 start、disable cleanup。

### 11.2 集成/手工测试

至少跑以下用户旅程：

```text
登录
  -> Home 获得当前位置和天气
  -> 搜索真实目的地
  -> 查看 route + PT timetable + estimate
  -> Start 普通行程
  -> End
  -> Review 更正交通方式
  -> 查看低碳替代
  -> Confirm
  -> 重复路线达到阈值后生成 Suggested Mission
  -> Accept
  -> 收到 reminder
  -> Start mission journey
  -> Confirm
  -> Mission complete / EcoPoints backend 更新
  -> Weekly Insight 显示统计和 AI/fallback 建议
```

还要测试：无网络、拒绝定位、拒绝通知、AI 401/403/429、Geocoder 无结果、PT 无班次、Firestore pending writes。

### 11.3 每阶段命令

Windows PowerShell：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

如果全量测试因其他成员未完成的代码失败，仍需：

1. 记录原始失败；
2. 运行本阶段相关 test class；
3. 明确区分“本次改动失败”和“工作树已有失败”；
4. 不通过删除测试或放宽断言来获得绿色结果。

## 12. 完成定义（Definition of Done）

一个缺口只有同时满足以下条件才能标记完成：

- Production navigation 不再走对应 mock/空回调；
- UI 有明确展示位置或系统通知；
- loading、empty、error、permission denied 至少有基本处理；
- 不复制已有算法、repository 或 tracking 逻辑；
- 有单元测试或明确的设备测试证据；
- `testDebugUnitTest` 和 `assembleDebug` 对当前阶段通过，或已记录与本改动无关的既有失败；
- 不记录 token、prompt、精确位置或用户历史到日志；
- `WORK_PLAN.md` 状态与实际证据一致。

## 13. 明确禁止的做法

- 不把 `MockHomeRouteDataSource` 的固定值包装成“真实结果”。
- 不在 Composable 中启动 Retrofit、Firestore listener 或 AI request。
- 不在 Review 保存失败路径中等待 AI 请求成功。
- 不用 AI 结果覆盖 calculator 的距离、saving 或可选 mode。
- 不把 profile 开关只写入数据库而不驱动行为。
- 不把录制服务内的 Activity Recognition 当作自动开始功能。
- 不为自动检测悄悄申请 background location。
- 不创建第二套 mission 数据模型或第二套 Firestore collection。
- 不修改 package/application ID，不引入 Hilt，不拆 Gradle module。
- 不清空、stash、reset 或覆盖当前未提交工作树。

## 14. Claude 每阶段回复格式

Claude 完成一个阶段后应按以下格式汇报，便于负责人 review：

```text
阶段：
完成的用户行为：
修改文件：
复用的现有组件：
新增/变更接口：
展示位置：
测试命令与结果：
未完成或风险：
需要哪位负责人确认：
```

不要只回复“implemented”。如果某项因权限、Android 后台限制、API 数据或现有接口冲突无法安全完成，应停止该项、保留已验证改动，并清楚说明 blocker。
