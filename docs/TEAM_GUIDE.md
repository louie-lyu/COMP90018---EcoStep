# EcoStep 開發指南

給團隊成員看的「現況說明 + 我該做什麼」文件。技術決策的完整說明在
[ARCHITECTURE.md](ARCHITECTURE.md)，任務與截止日的完整清單在 [WORK_PLAN.md](WORK_PLAN.md)，
模組間資料流與 mock 資料策略在 [DEPENDENCIES.md](DEPENDENCIES.md)——這份文件是三者的導覽入口。

---

## 1. 這個專案在做什麼

EcoStep 是一個 Android App：用手機的 GPS、加速度計、陀螺儀記錄使用者的移動旅程，判斷交通方式，
計算碳排放，再透過 AI 建議更低碳的替代方案（EcoMission），使用者完成任務可以拿 EcoPoints。

**核心流程**：記錄旅程 → 判斷交通方式 → 使用者確認/修正 → 抓天氣與路線資料 → 計算碳排放 →
AI 產生低碳任務建議 → 使用者接受並完成 → 拿到 EcoPoints。

## 2. 技術架構總覽

| 項目 | 選擇 |
|---|---|
| 語言 | Kotlin |
| UI | Jetpack Compose + Material3 |
| 架構模式 | MVVM |
| 模組結構 | 單一 `:app` 模組，用套件（package）分工，不是多模組 |
| 依賴注入 | 沒有用框架，手動寫一個 `AppContainer` |
| minSdk / compileSdk | 26 / 37 |
| 建置工具 | Gradle 9.7.1 + AGP 9.4.0 |

## 3. 現有程式碼骨架長什麼樣子

```
com.ecostep.app
├── data/model/        資料格式（JourneySummary、TransportResult、CarbonResult...）— 全部已寫好
├── data/repository/   資料存取「介面」— 已定義好，等 Zongcheng / Jianing 實作
├── algorithm/          演算法「介面」— 已定義好，等 Duo 實作
├── ui/screens/         6 個畫面 — 目前都是空殼，等 Yu-Han 填內容
├── core/theme/          Compose 主題（顏色、字體）— 已有預設值，可換
├── core/navigation/     6 個畫面之間怎麼切換 — 已接好
├── core/di/             AppContainer：共用的網路 client，也是大家掛自己 repository 的地方
├── EcoStepApp.kt        App 進入點
└── MainActivity.kt      唯一的畫面容器（Compose 單一 Activity）
```

現在的狀態：**專案可以直接編譯、安裝、在畫面間切換**（雖然每個畫面內容都只顯示「XXX — TODO」），
所有共用的資料格式跟介面都已經定義好，Firebase/網路/演算法的相依套件也都在
[app/build.gradle.kts](../app/build.gradle.kts) 裡準備好了。**這代表你不用等任何人，現在就可以開始寫自己的部分。**

哪個檔案該由誰改、能不能改，全部列在下面第 4 節。

## 4. 各成員任務清單與完成方式

> 每個要實作的介面檔案裡都有 KDoc 註明負責人跟截止日；`AppContainer.kt` 裡用 `TODO(你的名字)`
> 標記你要加東西的地方——直接搜尋你的名字即可，不用對照行號。

### 🔵 Zongcheng Jiang — Sensors, Journey Tracking, Auth & Database

**要做什麼**：收集 GPS/加速度計/陀螺儀資料、組成 `JourneySummary`、接 Firebase Auth + Firestore。

**完成步驟**：
1. 在新建立的 [`app/src/main/java/com/ecostep/app/sensors/`](../app/src/main/java/com/ecostep/app/sensors/README.md) 套件裡寫感測器收集邏輯。
2. 在 `data/repository/` 底下新建一個類別（例如 `FirebaseJourneyRepository.kt`），實作
   [`JourneyRepository.kt`](../app/src/main/java/com/ecostep/app/data/repository/JourneyRepository.kt) 介面。
3. 到 Firebase 主控台建立專案，下載 `google-services.json`，取代
   [`app/google-services.json.example`](../app/google-services.json.example)，存成 `app/google-services.json`（此檔已被排除在版控外，不會不小心上傳）。
4. 到 [app/build.gradle.kts](../app/build.gradle.kts) 找 `TODO(Zongcheng)`，取消註解 `google-services` plugin。
5. 到 [core/di/AppContainer.kt](../app/src/main/java/com/ecostep/app/core/di/AppContainer.kt) 找 `TODO(Zongcheng)`，把你的 repository 接進去。
6. 截止日：JourneySummary 20 Sep、Firebase 設定 13 Sep（見 WORK_PLAN.md）。

**卡住時怎麼辦**：先用 [`app/src/test/assets/mock_journeys.json`](../app/src/test/assets/mock_journeys.json) 的假資料讓別人先測試。

### 🟢 Jianing Xia — Weather, Maps, Public Transport APIs

**要做什麼**：串接天氣、地圖路線、大眾運輸 API。

**完成步驟**：
1. 在新建立的 [`app/src/main/java/com/ecostep/app/network/`](../app/src/main/java/com/ecostep/app/network/README.md) 套件裡寫各家 API 的 Retrofit client。
2. 在 `data/repository/` 底下新建類別，實作
   [`ExternalDataRepository.kt`](../app/src/main/java/com/ecostep/app/data/repository/ExternalDataRepository.kt) 介面，回傳的資料格式對應
   [`WeatherData.kt`](../app/src/main/java/com/ecostep/app/data/model/WeatherData.kt)、[`RouteInfo.kt`](../app/src/main/java/com/ecostep/app/data/model/RouteInfo.kt)、[`PublicTransportInfo.kt`](../app/src/main/java/com/ecostep/app/data/model/PublicTransportInfo.kt)。
3. 可以直接重用 [core/di/AppContainer.kt](../app/src/main/java/com/ecostep/app/core/di/AppContainer.kt) 裡現成的 `okHttpClient`（已設定好連線池跟除錯模式才印 log），不用自己重新建一個。
4. 到 `AppContainer.kt` 找 `TODO(Jianing)`，把你的 repository 接進去。
5. 截止日：27 Sep（見 WORK_PLAN.md）。

**卡住時怎麼辦**：先用假的天氣/路線資料讓 Duo 先測試 MissionContext 組裝邏輯。

### 🟡 Duo Lyu — Algorithms, Carbon, EcoPoints, AI

**要做什麼**：交通方式判斷、碳排計算、EcoPoints 計算、AI 任務生成與驗證。

**完成步驟**（依截止日排序）：
1. **14 Sep**：新建類別實作 [`CarbonCalculator.kt`](../app/src/main/java/com/ecostep/app/algorithm/CarbonCalculator.kt)（依交通方式算碳排+替代方案）。
2. **17 Sep**：新建類別實作 [`EcoPointsCalculator.kt`](../app/src/main/java/com/ecostep/app/algorithm/EcoPointsCalculator.kt)。
3. **20 Sep**：新建類別實作 [`TransportClassifier.kt`](../app/src/main/java/com/ecostep/app/algorithm/TransportClassifier.kt) 和 [`MissionGenerator.kt`](../app/src/main/java/com/ecostep/app/algorithm/MissionGenerator.kt)。
4. **23 Sep**：新建類別實作 [`MissionValidator.kt`](../app/src/main/java/com/ecostep/app/algorithm/MissionValidator.kt)（擋掉 AI 亂改資料的回應，觸發 fallback）。
5. 每個實作都要在新建立的 [`app/src/test/java/com/ecostep/app/algorithm/`](../app/src/test/java/com/ecostep/app/algorithm/README.md) 底下寫單元測試，可以參考
   [`JourneySummaryTest.kt`](../app/src/test/java/com/ecostep/app/data/model/JourneySummaryTest.kt) 的寫法（JUnit + kotlinx.serialization）。
6. 在動工前，先用 [`app/src/test/assets/mock_journeys.json`](../app/src/test/assets/mock_journeys.json) 當輸入資料，不用等 Zongcheng 的感測器做完。

**注意**：`TransportResult.confidence` 和 `EcoMission.confidence` 統一用 **0–100** 的尺度（不是 0.0–1.0），兩個檔案裡都有寫這個提醒。

### 🟣 Yu-Han Wang — UI

**要做什麼**：把 6 個空畫面做成真正的介面。

**完成步驟**：
1. 逐一打開並改寫 [`LoginScreen.kt`](../app/src/main/java/com/ecostep/app/ui/screens/LoginScreen.kt)、[`HomeScreen.kt`](../app/src/main/java/com/ecostep/app/ui/screens/HomeScreen.kt)、[`JourneyReviewScreen.kt`](../app/src/main/java/com/ecostep/app/ui/screens/JourneyReviewScreen.kt)、[`MissionScreen.kt`](../app/src/main/java/com/ecostep/app/ui/screens/MissionScreen.kt)、[`HistoryScreen.kt`](../app/src/main/java/com/ecostep/app/ui/screens/HistoryScreen.kt)、[`SettingsScreen.kt`](../app/src/main/java/com/ecostep/app/ui/screens/SettingsScreen.kt)（目前每個都只呼叫共用的 `PlaceholderScreen`，直接刪掉那行換成你的內容）。
2. 需要在畫面間傳資料（例如點一個旅程要帶 journeyId 過去）時，到
   [core/navigation/NavGraph.kt](../app/src/main/java/com/ecostep/app/core/navigation/NavGraph.kt) / [Routes.kt](../app/src/main/java/com/ecostep/app/core/navigation/Routes.kt) 加路由參數。
3. 想換配色/字體可以改 [core/theme/](../app/src/main/java/com/ecostep/app/core/theme/)（非必要）。
4. 畫面要拿資料時寫 ViewModel，用 [core/di/AppContainer.kt](../app/src/main/java/com/ecostep/app/core/di/AppContainer.kt) 裡 `ViewModelFactory` 的範例寫法建立（檔案內有完整程式碼範例）。
5. 截止日：Journey review 20 Sep，其餘畫面 27 Sep（見 WORK_PLAN.md）。

### 🔴 Rui Fang — Performance Evaluation

**要做什麼**：測試交通方式判斷的準確率、延遲、重複路線偵測正確性。

**完成步驟**：
1. 在新建立的 [`app/src/androidTest/java/com/ecostep/app/`](../app/src/androidTest/java/com/ecostep/app/README.md) 底下寫裝置端測試（Espresso / Compose UI test 依賴已經在
   [app/build.gradle.kts](../app/build.gradle.kts) 裡準備好，不用自己加）。
2. 等 Duo 把 [`TransportClassifier.kt`](../app/src/main/java/com/ecostep/app/algorithm/TransportClassifier.kt) 的真正實作做出來後，拿它來量測準確率與延遲。
3. 截止日：27 Sep（見 WORK_PLAN.md）。

## 5. 怎麼開始動手

1. `git clone` 這個 repo，在 Android Studio 開啟根目錄。
2. 等 Gradle 同步完成（第一次會下載 Gradle 9.7.1 跟所有依賴，需要幾分鐘）。
3. 如果 Android Studio 跳出 Gradle JDK 版本警告，去 Settings → Build, Execution, Deployment →
   Build Tools → Gradle，選一個 17–21 的 JDK。
4. 直接執行 `app`，應該可以在模擬器/實機上看到 6 個「XXX — TODO」畫面互相切換。
5. 找到你負責的套件（見第 4 節），開始寫。

## 6. 開發時的規則提醒

- **不要隨便改 `data/model/` 底下的欄位**（那是六個人共用的資料格式）。真的需要改的話，照
  [DEPENDENCIES.md「Interface Changes」](DEPENDENCIES.md#interface-changes) 的流程：開 GitHub issue、tag 受影響的人、討論後才能改。
- 每個新套件資料夾裡都有一個 `README.md` 佔位——等你放了真正的檔案進去，記得把它刪掉。
- Git 工作流程：`git checkout -b feature/描述`、PR 連結對應的 issue、合併前要 code review（見
  [README.md「How We Work」](../README.md#how-we-work)）。
- 卡住的話先看 [DEPENDENCIES.md「Using Mock Data」](DEPENDENCIES.md#using-mock-data)，用假資料讓自己跟別人都能繼續往下做，不要空等。
