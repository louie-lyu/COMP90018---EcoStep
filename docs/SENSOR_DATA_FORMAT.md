# 传感器特征数据格式（SensorFeatures v1）

- **负责人**：Zongcheng Jiang（采集与存储）；Duo Lyu（交通方式识别，数据使用方）
- **状态**：提议中，等待 Duo 和 Chi Hong Tam 确认
- **标签**：`interface-change`
- **关联文件**：`data/model/JourneySummary.kt`、`algorithm/TransportClassifier.kt`、`data/firebase/FirestoreJourneyRepository.kt`

---

## 1. 为什么要改

`TransportClassifier.classify(journey: JourneySummary)` 需要根据传感器数据判断交通方式，但现在的 `JourneySummary` 只有起终点、时间、距离和交通方式，**没有任何字段能放加速度计和陀螺仪的数据**。

这会带来两个后果：

1. Duo 的识别只能用"距离 ÷ 时间"这一个数，走路和慢速骑车、公交和堵车时的汽车很难区分。
2. 评分标准的 Sensors 项要求 GPS、加速度计、陀螺仪**三个都要用上**。陀螺仪数据如果没地方放，就等于没用。

## 2. 设计原则

| 原则 | 做法 |
|---|---|
| 不破坏现有代码 | 新字段设成可空，默认值为 `null`。现有构造 `JourneySummary(...)` 的代码不用改，Firestore 里的旧数据也能正常读取 |
| 只存特征，不存原始数据 | 原始 GPS 轨迹和传感器采样只在手机内存里实时计算，不写入本地文件，也不上传。这符合 `docs/DEPENDENCIES.md` 里"原始 GPS 和传感器数据只留在设备上"的要求 |
| 与手机朝向无关 | 加速度和角速度都取三轴向量的**模长**。手机放口袋、拿在手上、放支架上，结果都可以比较 |
| 单位统一 | 速度 m/s，加速度 m/s²，角速度 rad/s，距离和精度 m |
| 可演进 | 带一个 `featureVersion` 字段，以后改算法不会和旧数据混淆 |

## 3. 推荐格式

### 3.1 Kotlin 定义

新建 `data/model/SensorFeatures.kt`：

```kotlin
package com.ecostep.app.data.model

import kotlinx.serialization.Serializable

/**
 * 一次行程的传感器特征汇总。由 Sensors 模块（Zongcheng）在行程结束时计算，
 * 交给 Algorithm 模块（Duo）的 TransportClassifier 使用。
 * 只包含统计特征，不包含原始 GPS 轨迹或原始传感器采样。
 */
@Serializable
data class SensorFeatures(
    /** 特征算法版本，当前为 1。 */
    val featureVersion: Int = 1,

    // ---- GPS ----
    /** 平均速度 = distanceMeters / 行程时长（秒），单位 m/s。 */
    val averageSpeedMps: Double,
    /** GPS 速度的第 95 百分位，单位 m/s。比最大值更能抵抗 GPS 跳点。 */
    val p95SpeedMps: Double,
    /** 过滤后的最大速度，单位 m/s。 */
    val maxSpeedMps: Double,
    /** 停止比例：速度 < 0.5 m/s 的 GPS 采样占比，范围 0.0–1.0。 */
    val stopRatio: Double,
    /** GPS 定位精度的平均值，单位 m，越小越准。 */
    val averageGpsAccuracyMeters: Double,
    /** 参与计算的有效 GPS 点数。 */
    val gpsSampleCount: Int,

    // ---- 加速度计 ----
    /** 动态加速度均值：| |a| − g | 的平均值，单位 m/s²。 */
    val accelMagnitudeMean: Double,
    /** 加速度模长的标准差，单位 m/s²。反映晃动剧烈程度。 */
    val accelMagnitudeStd: Double,
    /** 加速度计采样数。 */
    val accelSampleCount: Int,

    // ---- 陀螺仪 ----
    /** 角速度模长 |ω| 的平均值，单位 rad/s。 */
    val gyroMagnitudeMean: Double,
    /** 角速度模长的标准差，单位 rad/s。 */
    val gyroMagnitudeStd: Double,
    /** 陀螺仪采样数。设备没有陀螺仪时为 0。 */
    val gyroSampleCount: Int,
)
```

### 3.2 JourneySummary 的改动（只加一行）

```kotlin
@Serializable
data class JourneySummary(
    val journeyId: String,
    val userId: String,
    val startLocation: GeoPoint,
    val endLocation: GeoPoint,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val distanceMeters: Double,
    val transportMode: TransportMode,
    /** 行程的传感器特征。手动创建或旧版本的行程为 null。 */
    val sensorFeatures: SensorFeatures? = null,   // ← 新增
)
```

### 3.3 和最初提议的对照

| 最初提议 | v1 推荐 | 调整原因 |
|---|---|---|
| `averageSpeedMps` | 保留 | — |
| `maxSpeedMps` | 保留，另加 `p95SpeedMps` | 单个 GPS 跳点就能把最大值拉到很离谱，p95 更稳定 |
| `accelerationVariance` | `accelMagnitudeStd` + `accelMagnitudeMean` | 标准差和原始数据同单位（m/s²），设阈值更直观；标准差的平方就是方差 |
| `gyroscopeVariance` | `gyroMagnitudeStd` + `gyroMagnitudeMean` | 同上 |
| `sampleCount` | 三个传感器各自的采样数 | 能看出是否"某个传感器没数据"，比如设备没有陀螺仪 |
| — | `stopRatio` | 区分公交、堵车（走走停停）和骑车（持续移动）很有用 |
| — | `averageGpsAccuracyMeters` | 精度差的行程，识别结果的置信度应该降低 |
| — | `featureVersion` | 算法以后变了，也能区分新旧数据 |

## 4. 各字段的计算方法

| 字段 | 计算方法 |
|---|---|
| `averageSpeedMps` | `distanceMeters / ((endTimeMillis − startTimeMillis) / 1000.0)`；时长为 0 时取 0 |
| `p95SpeedMps` | 所有有效 GPS 点的速度排序后，取第 95 百分位 |
| `maxSpeedMps` | 有效 GPS 点速度的最大值 |
| `stopRatio` | 速度 < 0.5 m/s 的有效 GPS 点数 ÷ 有效 GPS 点总数 |
| `averageGpsAccuracyMeters` | 有效 GPS 点 `accuracy` 的平均值 |
| `accelMagnitudeMean` | 每个采样算 `m = sqrt(x² + y² + z²)`，再取 `abs(m − 9.80665)` 的平均值 |
| `accelMagnitudeStd` | 所有采样模长 `m` 的总体标准差 |
| `gyroMagnitudeMean` / `gyroMagnitudeStd` | 每个采样算 `sqrt(x² + y² + z²)`，再取平均值和总体标准差 |

**有效 GPS 点**需要同时满足：

- `accuracy ≤ 30 m`；
- 和上一个有效点相比，推算速度 ≤ 60 m/s，超过的视为跳点。

**GPS 点的速度**：`location.hasSpeed()` 为真时用 `location.speed`，否则用"两点距离 ÷ 时间差"推算。

**实现方式**：均值和标准差用 Welford 在线算法，边采边算，内存占用固定，不需要保存原始采样。GPS 速度列表数据量很小（每 2 秒一个点，1 小时约 1800 个），可以放在内存里用来算 p95。

**边界情况**：某个传感器采样数为 0 时，它对应的均值和标准差都填 `0.0`。**使用方必须先检查 `*SampleCount > 0`，再使用这些值。**

## 5. 数据示例

### 5.1 JSON（kotlinx.serialization）

```json
{
  "journeyId": "5f1c2b1e-8a0e-4a5c-9b8e-2f7d1f0c1234",
  "userId": "PglpHhCR04ZcnkMA3LlrWoj8sEs2",
  "startLocation": { "latitude": -37.7963, "longitude": 144.9614 },
  "endLocation":   { "latitude": -37.8183, "longitude": 144.9671 },
  "startTimeMillis": 1790300000000,
  "endTimeMillis":   1790301200000,
  "distanceMeters": 2600.0,
  "transportMode": "UNKNOWN",
  "sensorFeatures": {
    "featureVersion": 1,
    "averageSpeedMps": 2.17,
    "p95SpeedMps": 5.9,
    "maxSpeedMps": 7.2,
    "stopRatio": 0.18,
    "averageGpsAccuracyMeters": 8.4,
    "gpsSampleCount": 598,
    "accelMagnitudeMean": 0.62,
    "accelMagnitudeStd": 0.81,
    "accelSampleCount": 59210,
    "gyroMagnitudeMean": 0.35,
    "gyroMagnitudeStd": 0.29,
    "gyroSampleCount": 59188
  }
}
```

### 5.2 Firestore 存储

路径 `users/{uid}/journeys/{journeyId}`。`sensorFeatures` 存为嵌套的 map，字段名和 Kotlin 属性名一致。整数字段在 Firestore 里是 `integer`，读取时用 `(value as Number).toInt()` 转换。

如果文档里没有 `sensorFeatures` 字段（比如手动创建的 `test_1`，或者旧数据），读取时返回 `null`。

## 6. 各交通方式的参考值（仅供设定初始阈值）

> ⚠️ 下表只是粗略的经验范围，用来帮 Duo 起步。**正式阈值必须用 Rui 在真机上实测的数据校准**，报告里也要写实测结果，不能直接引用这张表。

| 交通方式 | averageSpeedMps | p95SpeedMps | accelMagnitudeStd | stopRatio | 特点 |
|---|---|---|---|---|---|
| 步行 | 约 0.8–1.8 | < 2.5 | 高（步伐冲击明显） | 低 | 规律的步伐振动 |
| 骑车 | 约 2.5–7 | < 10 | 中 | 低到中 | 持续移动，转弯时角速度较大 |
| 公共交通 | 约 3–12 | 可达 20 | 低 | 高 | 频繁到站停车 |
| 汽车 | 约 5–20 | 可达 30 以上 | 低 | 中 | 速度高，整体比较平稳 |

## 7. 给使用方（Duo）的约定

1. `journey.sensorFeatures == null` 时，退回到只用 `distanceMeters` 和时长做判断，不能崩溃。
2. `gyroSampleCount == 0` 时，不要使用陀螺仪相关字段。部分设备和模拟器没有陀螺仪。
3. `gpsSampleCount < 5` 或 `averageGpsAccuracyMeters > 30` 时，建议降低 `TransportResult.confidence`。
4. 置信度沿用 `TransportResult` 现有的 0–100 刻度。

## 8. 影响范围

| 文件 / 模块 | 改动 | 负责人 |
|---|---|---|
| `data/model/SensorFeatures.kt` | 新增文件 | Zongcheng |
| `data/model/JourneySummary.kt` | 新增 1 个可空字段 | Zongcheng（需要 Chi Hong Tam 批准） |
| `data/firebase/FirestoreJourneyRepository.kt` | Map 转换加上 `sensorFeatures` | Zongcheng |
| `algorithm/TransportClassifier` 的实现 | 开始使用这些特征 | Duo |
| `evaluation/` | 可以用这些特征做识别准确率评估 | Rui |
| UI（Yu-Han）、外部 API（Jianing） | **不受影响**（有默认值，现有代码不用改） | — |
| `app/src/test/assets/mock_journeys.json` | 不用改。可选：加一条带 `sensorFeatures` 的样例 | Zongcheng |

## 9. 确认流程

1. 在 GitHub 开 issue，标题：`[interface-change] Add SensorFeatures to JourneySummary`，加 `interface-change` 标签，把本文件链接贴上。
2. @Duo 确认字段是否够用；@Chi Hong Tam 确认共享模型的改动；@Rui 知会一声（评估会用到）。
3. 两人确认后就可以合并。**如果到 9/26 中午还没有回复，先按本 v1 实现**，字段有默认值，以后增删的代价很小。

### Issue 正文模板（可以直接复制）

```markdown
## 背景
TransportClassifier 需要传感器特征，但 JourneySummary 没有地方放加速度计和陀螺仪数据。
评分标准的 Sensors 项要求 GPS + 加速度计 + 陀螺仪三个都用上。

## 提议
- 新增 `data/model/SensorFeatures.kt`（v1，共 12 个统计字段，详见 docs/SENSOR_DATA_FORMAT.md）
- JourneySummary 新增 `val sensorFeatures: SensorFeatures? = null`

## 兼容性
- 字段可空且有默认值，现有代码和 Firestore 已有数据都不受影响
- 只上传统计特征，不上传原始轨迹和采样

## 需要确认
- [ ] @Duo 字段是否满足交通方式识别的需要？还需要加什么？
- [ ] @Chi Hong Tam 共享模型的改动是否同意？
- [ ] @Rui 知会：评估时可以使用这些特征

截止：9/26 中午。没有回复的话先按 v1 实现。
```
