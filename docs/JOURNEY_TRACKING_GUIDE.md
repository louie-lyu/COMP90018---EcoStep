# 行程记录模块开发手册（传感器 + 前台服务）

- **负责人**：Zongcheng Jiang
- **目标日期**：9/26 完成，9/27 功能冻结
- **前置条件**：
  - `feature/zongcheng-auth` 已完成（登录 + `FirestoreJourneyRepository` 已验证可用）
  - `SensorFeatures` 数据格式已提出，见 `docs/SENSOR_DATA_FORMAT.md`

---

## 0. 目标和验收标准

**一句话目标**：用户点击"开始记录"后，App 在前台服务里同时采集 GPS、加速度计和陀螺仪；点击"结束"后生成带 `sensorFeatures` 的 `JourneySummary`，保存到 Firestore，并跳转到行程确认页。

| # | 验收项 | 对应评分项 |
|---|---|---|
| 1 | 同时用到 GPS、加速度计、陀螺仪，Logcat 里能看到三者的采样数都在增长 | Sensors |
| 2 | 锁屏或切到别的 App，记录不中断；通知栏显示"正在记录" | Sensors / Quality |
| 3 | 记录页实时显示时长、距离、GPS 状态、三个传感器的采样数（至少每秒刷新一次） | Reactiveness |
| 4 | 所有传感器处理和网络操作都不在主线程执行，界面不卡顿 | Responsiveness |
| 5 | 权限被拒绝时，有清楚的提示和去设置页的按钮，不闪退 | Quality / UI Flow |
| 6 | 结束后自动保存，并跳转到 `journeyReview(新的 journeyId)`，替换掉现在写死的 `test_1` | Connectivity |
| 7 | 断网时也能结束记录并进入确认页，联网后数据自动同步到 Firestore | Connectivity |
| 8 | 不上传原始轨迹，只上传起终点和统计特征 | 隐私 |

---

## 1. 整体架构

```
TrackingRoute（Compose 界面）
   │  开始 / 结束按钮；显示实时状态
   ▼
TrackingViewModel ──── 通过 StateFlow<TrackingUiState> 暴露状态
   │ start(): 检查权限 → 启动 JourneyTrackingService
   │ stop():  停止服务 → tracker.finish() → saveJourney() → 返回 journeyId
   ▼
JourneyTracker（AppContainer 里的单例，保存本次记录的状态）
   ▲  onLocation() / onAccel() / onGyro()
   │
JourneyTrackingService（前台服务，类型为 location）
   ├── LocationTracker        FusedLocationProviderClient → Flow<Location>
   └── MotionSensorTracker    SensorManager → Flow<SensorSample>（加速度计 + 陀螺仪）

finish() 的输出：
   RecordingResult → JourneySummaryBuilder → JourneySummary(sensorFeatures = …)
   → journeyRepository.saveJourney() → navController.navigate(journeyReview(id))
```

**为什么把状态放在 `JourneyTracker` 单例里**：服务和界面的生命周期不同（界面可能被销毁，服务还在运行）。两边都从 `AppContainer` 拿同一个 `JourneyTracker`，服务往里写数据，界面从它的 `StateFlow` 读，不用做 Service 绑定，代码最少。

---

## 2. 文件清单

### 2.1 新增文件（全部在你自己的包里）

```
app/src/main/java/com/ecostep/app/
├── data/model/SensorFeatures.kt                 ← 共享模型，按接口变更文档新增
└── sensors/
    ├── tracking/
    │   ├── RunningStats.kt                      ← Welford 在线均值和标准差（纯 Kotlin，可单测）
    │   ├── FeatureAccumulator.kt                ← 汇总 GPS、加速度、陀螺仪特征（纯 Kotlin，可单测）
    │   ├── JourneyTracker.kt                    ← 记录状态机 + StateFlow
    │   ├── TrackingState.kt                     ← 实时状态数据类
    │   └── JourneySummaryBuilder.kt             ← RecordingResult → JourneySummary
    ├── location/LocationTracker.kt              ← Fused Location 封装成 Flow
    ├── motion/MotionSensorTracker.kt            ← SensorManager 封装成 Flow
    ├── service/JourneyTrackingService.kt        ← 前台服务
    └── ui/
        ├── TrackingRoutes.kt                    ← const val TRACKING = "tracking"（不改 Yu-Han 的 Routes.kt）
        ├── TrackingViewModel.kt
        └── TrackingRoute.kt                     ← 开始/结束页面 + 权限请求

app/src/test/java/com/ecostep/app/sensors/
├── RunningStatsTest.kt
├── FeatureAccumulatorTest.kt
└── JourneySummaryBuilderTest.kt
```

### 2.2 需要改动的共享文件（只改最少的行）

| 文件 | 改动 |
|---|---|
| `AndroidManifest.xml` | 加 3 个权限 + 1 个 `<service>` 声明 |
| `data/model/JourneySummary.kt` | 加 `val sensorFeatures: SensorFeatures? = null`（等接口变更确认后） |
| `data/firebase/FirestoreJourneyRepository.kt`（你自己的文件） | Map 转换加上 `sensorFeatures` |
| `core/di/AppContainer.kt` | 追加 `val journeyTracker by lazy { JourneyTracker() }` |
| `core/navigation/NavGraph.kt` | ① 加 `TRACKING` 路由；② 登录成功后跳到 `TRACKING`，替换 `test_1` |
| `app/build.gradle.kts` | 如果缺少 `kotlinx-coroutines-play-services`，就加上（用于 `await()`） |

---

## 3. Manifest 和权限

### 3.1 AndroidManifest.xml

```xml
<!-- 已有 -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- 新增 -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />  <!-- Android 14+ -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />           <!-- Android 13+ -->

<application ...>
    <service
        android:name=".sensors.service.JourneyTrackingService"
        android:exported="false"
        android:foregroundServiceType="location" />
</application>
```

**不需要的权限**：

- `ACCESS_BACKGROUND_LOCATION`：服务是用户在前台点按钮启动的，而且类型是 `location`，切到后台后仍然可以继续定位。申请后台定位反而会让审核和权限流程变复杂。
- `HIGH_SAMPLING_RATE_SENSORS`：只有采样率超过 200 Hz 才需要。我们用 `SENSOR_DELAY_GAME`（约 50 Hz）就够了。
- 加速度计和陀螺仪本身**不需要任何权限**。

### 3.2 运行时权限流程

```
点击"开始记录"
  → 没有精确定位权限？请求 FINE + COARSE（Android 13+ 同时请求 POST_NOTIFICATIONS）
      ├── 允许了精确定位 → 启动服务
      ├── 只允许了大致定位 → 提示"需要精确位置才能准确计算距离"，不启动服务
      ├── 拒绝了（还能再问）→ 显示说明文字 + "重试"按钮
      └── 永久拒绝了 → 显示"去设置开启"按钮，跳转到应用设置页
  → 通知权限被拒绝：不阻止记录（前台服务照样运行，只是通知不在通知栏显示）
```

```kotlin
val permissions = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
) { result ->
    val fineGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
    viewModel.onPermissionResult(fineGranted)   // 允许后 ViewModel 再启动服务
}

// 按钮点击
Button(onClick = {
    if (hasFineLocation(context)) viewModel.start() else launcher.launch(permissions)
}) { Text("Start journey") }

// 跳转到应用设置页
context.startActivity(
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
)
```

> ⚠️ **最容易踩的坑**：Android 14 及以上，如果在没有定位权限的情况下调用 `startForeground(... TYPE_LOCATION)`，会直接抛出 `SecurityException` 导致闪退。**必须先确认权限已授予，再启动服务。**

---

## 4. 前台服务

```kotlin
class JourneyTrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // 所有回调串行处理，JourneyTracker 就不需要加锁
    private val serial = Dispatchers.Default.limitedParallelism(1)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_STOP -> stop()
        }
        return START_NOT_STICKY   // 被系统杀掉后不自动重启，避免产生残缺行程
    }

    private fun start() {
        createChannel()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        val container = (application as EcoStepApp).appContainer
        val tracker = container.journeyTracker
        tracker.begin(System.currentTimeMillis())

        scope.launch(serial) {
            LocationTracker(this@JourneyTrackingService).locations()
                .collect { tracker.onLocation(it) }
        }
        scope.launch(serial) {
            MotionSensorTracker(this@JourneyTrackingService).samples()
                .collect { tracker.onMotion(it) }
        }
    }

    private fun stop() {
        scope.coroutineContext.cancelChildren()   // 触发 awaitClose，注销所有监听
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Journey tracking", NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setContentTitle("EcoStep is recording your journey")
        .setOngoing(true)
        .setContentIntent(/* 点击回到 MainActivity 的 PendingIntent */)
        .build()

    companion object {
        const val ACTION_START = "com.ecostep.app.tracking.START"
        const val ACTION_STOP = "com.ecostep.app.tracking.STOP"
        private const val CHANNEL_ID = "journey_tracking"
        private const val NOTIFICATION_ID = 1001
    }
}
```

从 ViewModel 启动服务：

```kotlin
ContextCompat.startForegroundService(
    context, Intent(context, JourneyTrackingService::class.java).setAction(ACTION_START)
)
```

停止时发送 `ACTION_STOP`。

---

## 5. GPS 采集

```kotlin
class LocationTracker(context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")   // 调用前已在 UI 层检查过权限
    fun locations(): Flow<Location> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
            .setMinUpdateIntervalMillis(1_000L)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { trySend(it) }
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }
}
```

**过滤规则**（在 `FeatureAccumulator.onLocation` 里实现）：

1. `accuracy > 30 m` 的点直接丢弃。
2. 和上一个有效点比较，推算速度 > 60 m/s 的视为跳点，丢弃。
3. 距离累加：只有当两点之间距离 ≥ 3 m 时才累加 `prev.distanceTo(cur)`，用来抑制静止时 GPS 的漂移。
4. 速度：`hasSpeed()` 为真时用 `location.speed`，否则用"两点距离 ÷ 时间差"。
5. 第一个有效点作为 `startLocation`，最后一个有效点作为 `endLocation`。

---

## 6. 加速度计和陀螺仪采集

```kotlin
data class MotionSample(val type: Int, val x: Float, val y: Float, val z: Float)

class MotionSensorTracker(context: Context) {
    private val sm = context.getSystemService(SensorManager::class.java)
    val hasGyroscope = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null

    fun samples(): Flow<MotionSample> = callbackFlow {
        // 专用线程接收回调，保证不占用主线程
        val thread = HandlerThread("EcoStepSensors").apply { start() }
        val handler = Handler(thread.looper)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                trySend(MotionSample(e.sensor.type, e.values[0], e.values[1], e.values[2]))
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        listOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE).forEach { type ->
            sm.getDefaultSensor(type)?.let {
                sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME, handler)
            }
        }
        awaitClose {
            sm.unregisterListener(listener)
            thread.quitSafely()
        }
    }.buffer(256)   // 有界缓冲；处理不过来时 trySend 会丢弃新采样，不会阻塞传感器线程
}
```

**说明**：

- 用原始 `TYPE_ACCELEROMETER`，不用 `TYPE_LINEAR_ACCELERATION`。后者是软件合成的传感器，部分设备没有。取模长后再减去重力，既和手机朝向无关，也所有设备都能用。
- 设备没有陀螺仪时，`getDefaultSensor` 返回 `null`，直接跳过，`gyroSampleCount` 就是 0。界面上显示"本设备没有陀螺仪"。
- **不要把原始采样存进列表**，每来一个就更新 `RunningStats`。50 Hz × 1 小时 = 18 万个点，全部保存会浪费内存。

---

## 7. 特征计算（纯 Kotlin，可以写单元测试）

### 7.1 RunningStats：Welford 在线算法

```kotlin
class RunningStats {
    var count = 0L; private set
    private var mean = 0.0
    private var m2 = 0.0

    fun add(x: Double) {
        count++
        val delta = x - mean
        mean += delta / count
        m2 += delta * (x - mean)
    }

    fun mean(): Double = if (count == 0L) 0.0 else mean
    /** 总体标准差 */
    fun std(): Double = if (count == 0L) 0.0 else sqrt(m2 / count)
}
```

### 7.2 FeatureAccumulator 骨架

```kotlin
class FeatureAccumulator {
    private val accelMag = RunningStats()       // 模长 |a|，用来算标准差
    private val accelDyn = RunningStats()       // | |a| − g |，用来算均值
    private val gyroMag = RunningStats()
    private val gpsAccuracy = RunningStats()
    private val speeds = ArrayList<Double>()    // GPS 点数量少，可以保存，用于算 p95
    var distanceMeters = 0.0; private set
    var first: LocationSample? = null; private set   // 自定义数据类，见下方说明
    var last: LocationSample? = null; private set

    fun onMotion(s: MotionSample) {
        val m = sqrt((s.x * s.x + s.y * s.y + s.z * s.z).toDouble())
        when (s.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelMag.add(m)
                accelDyn.add(abs(m - SensorManager.GRAVITY_EARTH))
            }
            Sensor.TYPE_GYROSCOPE -> gyroMag.add(m)
        }
    }

    fun onLocation(loc: LocationSample) { /* 按第 5 节的规则过滤、累加距离、记录速度 */ }

    fun toFeatures(durationMillis: Long): SensorFeatures {
        val sorted = speeds.sorted()
        val p95 = if (sorted.isEmpty()) 0.0 else sorted[((sorted.size - 1) * 0.95).toInt()]
        return SensorFeatures(
            averageSpeedMps = if (durationMillis > 0) distanceMeters / (durationMillis / 1000.0) else 0.0,
            p95SpeedMps = p95,
            maxSpeedMps = sorted.lastOrNull() ?: 0.0,
            stopRatio = if (speeds.isEmpty()) 0.0 else speeds.count { it < 0.5 }.toDouble() / speeds.size,
            averageGpsAccuracyMeters = gpsAccuracy.mean(),
            gpsSampleCount = speeds.size,
            accelMagnitudeMean = accelDyn.mean(),
            accelMagnitudeStd = accelMag.std(),
            accelSampleCount = accelMag.count.toInt(),
            gyroMagnitudeMean = gyroMag.mean(),
            gyroMagnitudeStd = gyroMag.std(),
            gyroSampleCount = gyroMag.count.toInt(),
        )
    }
}
```

> `Location` 和 `Sensor` 是 Android 类，本地单元测试里没法直接用。建议 `onLocation` 接收你自己定义的 `LocationSample(lat, lon, accuracy, speed?, timeMillis)`，由服务负责把 `Location` 转换过去。距离用 `Location.distanceBetween` 或自己写 haversine 公式，这样 `FeatureAccumulator` 就能完全在 JVM 上测试。

---

## 8. JourneyTracker 状态机

```
Idle ──begin()──▶ Recording ──finish()──▶ Idle
                     │
                     └─ 每秒更新一次 StateFlow：时长、距离、GPS 精度、三个传感器的采样数
```

```kotlin
data class TrackingState(
    val isRecording: Boolean = false,
    val startTimeMillis: Long = 0L,
    val elapsedSeconds: Long = 0L,
    val distanceMeters: Double = 0.0,
    val lastAccuracyMeters: Float? = null,   // null 表示还没定位到
    val gpsCount: Int = 0,
    val accelCount: Long = 0,
    val gyroCount: Long = 0,
)

class JourneyTracker {
    private val _state = MutableStateFlow(TrackingState())
    val state: StateFlow<TrackingState> = _state.asStateFlow()
    private var acc = FeatureAccumulator()

    fun begin(now: Long) { acc = FeatureAccumulator(); _state.value = TrackingState(true, now) }
    fun onLocation(l: Location) { acc.onLocation(l.toSample()); publishThrottled() }
    fun onMotion(s: MotionSample) { acc.onMotion(s); publishThrottled() }   // 最多每秒发布一次
    fun finish(now: Long): RecordingResult? { /* 生成结果，重置为 Idle；数据不足时返回 null */ }
}
```

---

## 9. 结束、保存和跳转

### 9.1 生成 JourneySummary

```kotlin
JourneySummary(
    journeyId = UUID.randomUUID().toString(),
    userId = authRepository.currentUserId ?: error("Not signed in"),
    startLocation = GeoPoint(first.lat, first.lon),
    endLocation = GeoPoint(last.lat, last.lon),
    startTimeMillis = start,
    endTimeMillis = end,
    distanceMeters = acc.distanceMeters,
    transportMode = TransportMode.UNKNOWN,      // 等 Duo 的识别器，见下文
    sensorFeatures = acc.toFeatures(end - start),
)
```

- **数据太少时不保存**：时长 < 30 秒或有效 GPS 点 < 2 个时，提示"行程太短，未保存"，并提供"丢弃"按钮。
- **交通方式**：如果 Duo 的 `TransportClassifier` 已经有实现，就调用它得到 `transportMode`；还没有的话先填 `UNKNOWN`，让用户在确认页自己选。**提前确认 Yu-Han 的确认页能正常显示 `UNKNOWN`**，比如默认不选中任何选项。

### 9.2 保存：注意离线时的坑 ⚠️

Firestore 开启离线缓存后，`set()` **会立刻写进本地缓存**，但返回的 Task **要等到服务器确认才会完成**。断网时如果直接 `await()`，界面会一直卡在"保存中"。

推荐写法：

```kotlin
viewModelScope.launch {
    _ui.update { it.copy(isSaving = true) }
    val summary = builder.build(result)
    // 本地缓存已经写入；最多等 3 秒服务器确认，超时也继续往下走
    withTimeoutOrNull(3_000) { journeyRepository.saveJourney(summary) }
    _ui.update { it.copy(isSaving = false, savedJourneyId = summary.journeyId) }
}
```

确认页调用 `getJourney()` 读取时，断网情况下 Firestore 会自动从本地缓存返回数据，所以跳转后也能正常显示。

> 顺带检查一下：`FirestoreJourneyRepository.saveJourney` 现在是不是直接 `await()`？如果是，Yu-Han 确认页上的"保存"按钮在断网时也会一直卡住。修复方法同上：在 ViewModel 层加超时，或者让仓库只等本地写入完成。

### 9.3 跳转

```kotlin
// NavGraph 里新增的路由
composable(TrackingRoutes.TRACKING) {
    TrackingRoute(
        viewModel = /* 用 ViewModelFactory 创建 */,
        onJourneySaved = { id -> navController.navigate(Routes.journeyReview(id)) },
    )
}
// 登录成功后改为：navController.navigate(TrackingRoutes.TRACKING) { popUpTo(Routes.LOGIN) { inclusive = true } }
```

在确认页按返回键，会回到记录页，可以接着开始下一次记录。

---

## 10. FirestoreJourneyRepository 映射补充

```kotlin
// 写入
"sensorFeatures" to journey.sensorFeatures?.let { f ->
    mapOf(
        "featureVersion" to f.featureVersion,
        "averageSpeedMps" to f.averageSpeedMps,
        // …其余字段同名
    )
}

// 读取：字段不存在时返回 null
val sf = (data["sensorFeatures"] as? Map<*, *>)?.let { m ->
    SensorFeatures(
        featureVersion = (m["featureVersion"] as? Number)?.toInt() ?: 1,
        averageSpeedMps = (m["averageSpeedMps"] as Number).toDouble(),
        // …Int 字段用 (x as Number).toInt()
    )
}
```

---

## 11. 测试方案

### 11.1 单元测试（必做，报告里的 Quality 证据）

| 测试 | 用例 |
|---|---|
| `RunningStatsTest` | `[2, 4, 4, 4, 5, 5, 7, 9]` → 均值 5.0，总体标准差 2.0；空输入 → 0 |
| `FeatureAccumulatorTest` | 精度差的点被丢弃；跳点被丢弃；静止漂移不累加距离；p95 和 stopRatio 计算正确；没有陀螺仪时计数为 0 |
| `JourneySummaryBuilderTest` | 行程太短返回 null；起终点取第一个和最后一个有效点；`sensorFeatures` 不为空 |
| Firestore 映射测试 | `sensorFeatures` 为 null 和不为 null 时都能正确往返转换 |

### 11.2 模拟器测试

1. **GPS**：模拟器右侧 `⋯` → Extended Controls → **Location** → **Routes**。随便规划一条路线，点 Play Route，选一个播放速度来模拟移动。
2. **加速度计和陀螺仪**：Extended Controls → **Virtual sensors**，拖动或旋转手机模型，采样数会增长。
3. **锁屏测试**：记录过程中按电源键锁屏 30 秒再解锁，时长和距离应该连续没有中断。
4. **断网测试**：记录中打开飞行模式 → 结束记录 → 应该能正常跳到确认页 → 关闭飞行模式 → 在 Firestore 控制台看到这条新数据。

### 11.3 真机测试（报告证据，冻结后也可以继续做）

| 场景 | 记录内容 |
|---|---|
| 步行 5–10 分钟 | App 算的距离 vs Google Maps 量出的距离；三个特征值 |
| 骑车或公交各一次 | 同上 |
| 静止放置 5 分钟 | 距离应该接近 0（验证漂移过滤是否有效） |

每次记录都截图：App 记录页、确认页、Firestore 文档。在 Logcat 里用 `EcoStepTracking` 标签输出每 10 秒的采样计数，这些日志也可以当作证据。

### 11.4 延迟（配合 Rui）

用 `LatencyTracker` 测量 `journey.stop_to_review`，即从点击"结束"到确认页显示出来的耗时，在线和离线各测 10 次。

---

## 12. 常见坑速查

| 现象 | 原因 | 解决办法 |
|---|---|---|
| 点"开始"就闪退，报 `SecurityException` | 没有定位权限就启动了 location 类型的前台服务 | 先检查权限，再启动服务 |
| 报 `MissingForegroundServiceTypeException` | Manifest 里没写 `foregroundServiceType` | 按第 3.1 节补上 |
| 锁屏后停止记录 | 没用前台服务，或没调用 `startForeground` | 按第 4 节实现 |
| 界面卡顿 | 传感器回调在主线程上执行 | 用 `HandlerThread`，加上 `limitedParallelism(1)` |
| 耗电快、停止后仍然在采集 | 没有注销监听 | 在 `awaitClose` 里注销，并在 `stop()` 里取消协程 |
| 模拟器一直没有定位 | 模拟器默认不会自动产生位置 | 用 Extended Controls → Location 设置位置 |
| 静止时距离还在增加 | GPS 漂移 | 过滤精度差的点，并且小于 3 m 的位移不累加 |
| 断网时一直显示"保存中" | 在等服务器确认 | 按第 9.2 节加超时 |
| App 被系统杀掉后行程丢失 | 用的是 `START_NOT_STICKY` | 接受这个限制，在报告里写成已知限制 |

---

## 13. 时间安排（9/26）

| 时间 | 任务 | 完成标志 |
|---|---|---|
| 上午 1h | `SensorFeatures` 模型 + `RunningStats` + `FeatureAccumulator` + 单元测试 | 测试全部通过 |
| 上午 1.5h | `LocationTracker` + `MotionSensorTracker` + `JourneyTracker` | Logcat 里三种采样数都在增长 |
| 下午 1.5h | 前台服务 + Manifest + 权限流程 | 锁屏后记录不中断 |
| 下午 1h | `TrackingRoute` 页面 + 保存 + 跳转 + Firestore 映射 | 结束后跳到确认页，控制台出现新文档 |
| 晚上 1h | 模拟器完整测试 + 断网测试 + 提交 PR | 验收表 8 项全部通过 |

**如果时间不够，按这个顺序砍**：

1. 先不做"永久拒绝后跳设置页"，只显示提示文字；
2. 先不做通知点击回到 App；
3. **最后的保底方案**：不用前台服务，直接在 ViewModel 里采集，只保证亮屏时能记录。这样三个传感器照样都用上，评分项不受影响，只是锁屏会中断，在报告里写成已知限制。

---

## 附录：交给 AI 的 prompt

````markdown
# 任务：实现 EcoStep 行程记录模块（GPS + 加速度计 + 陀螺仪 + 前台服务）

请先完整阅读 `docs/JOURNEY_TRACKING_GUIDE.md` 和 `docs/SENSOR_DATA_FORMAT.md`，严格按照这两份文档实现。

## 第 0 步：同步代码（完成后先汇报，再继续）
1. `git status`；如果有未提交的改动，先告诉我，不要擅自 stash 或丢弃
2. 在当前的 `feature/zongcheng-auth` 分支上（或者如果它已经合并到 main，就从最新的 main 新建 `feature/zongcheng-tracking`）
3. `git fetch origin`，确认是否有 Yu-Han / Duo 的新提交需要合并；有冲突立刻停下告诉我
4. 禁止使用 `reset --hard`、`clean`、`push --force`

## 改动原则
- 新代码全部放在 `sensors/` 包和 `data/model/SensorFeatures.kt`
- 只允许对这些共享文件做最少的改动：
  - `AndroidManifest.xml`：3 个权限 + 1 个 service
  - `JourneySummary.kt`：加 1 个可空字段
  - `AppContainer.kt`：追加 `journeyTracker`
  - `NavGraph.kt`：加 TRACKING 路由；登录成功改为跳到 TRACKING
  - `build.gradle.kts`：仅在确实需要时添加依赖
- 以下内容禁止修改：`network/`、`data/cache/`、`algorithm/`、`evaluation/`、`ui/`、`core/theme/`、`Routes.kt`、`libs.versions.toml` 中已有的版本号
- 最小实现：不做地图显示、不做后台定位权限、不做 Room、不做 UI 美化
- 传感器回调不能在主线程执行；ViewModel 用 StateFlow 暴露状态，通过 ViewModelFactory 创建

## 验证
1. `./gradlew assembleDebug` 通过
2. 新增的单元测试全部通过（已知 Windows 上 DataStore 的 2 个测试失败与本任务无关）
3. 列出我在模拟器上手动验证的步骤（参照手册第 11.2 节）

## 完成后按以下格式反馈
1. 第 0 步结果（分支、是否合并、有无冲突）
2. 新增文件列表，每个文件一句话说明
3. 共享文件的完整 diff
4. 手册第 0 节验收表 8 项逐项状态：✅ / ⚠️ / ❌，并说明原因
5. 构建和测试结果摘要
6. 需要我手动做的事、需要通知组员的事
7. 建议的 commit message（不要执行 commit）
````
