# speed_calculator
# ⚡ AccMeter — 加速度 & GPS 双模测速仪

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9%2B-7F52FF?logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-8.0%2B-34A853?logo=android)](https://developer.android.com)
[![Material You](https://img.shields.io/badge/Material%20You-Dark%20Theme-00E5FF)](https://m3.material.io)
[![License](https://img.shields.io/badge/License-MIT-green)](./LICENSE)

**AccMeter** 是一款运行于 Android 设备上的实时速度测量应用，同时利用 **加速度传感器** 和 **GPS** 进行测速，去除重力影响，提供三轴加速度/分速度图表、平滑显示与单位切换等专业功能
---

## 🎯 核心特性

### 加速度测量（纯传感器）
- ✅ 优先使用 **线性加速度传感器**（`TYPE_LINEAR_ACCELERATION`），自动去除重力
- ✅ 回退至 **加速度计 + 低通滤波** 手动分离重力
- ✅ 实时积分计算 **三轴分速度**（vₓ / vᵧ / v𝓏）
- ✅ **静止检测**：加速度低于阈值时速度快速衰减归零
- ✅ **速度衰减**：模拟空气阻力，防止长时间累积漂移

### GPS 测速
- ✅ 使用 Android `LocationManager.GPS_PROVIDER` 获取精确速度
- ✅ 一键开启/关闭监听，首次自动请求定位权限
- ✅ 独立图表展示 GPS 速度变化

### 图表与数据展示
- 📈 **合速度图表**（单线，实时/平滑可选）
- 📈 **三轴加速度图表**（X 红色 / Y 绿色 / Z 青色）
- 📈 **三轴分速度图表**（同上配色）
- 📈 **GPS 速度图表**（霓虹蓝单线）
- 🔄 图表自动滚动最近时间段，自适应 Y 轴范围

### 实用功能
- 🔘 **单位自由切换**：m/s ↔ km/h（×3.6）
- 🔘 **平滑显示**：3 秒滑动平均，消除瞬时波动
- 🔘 **刷新率可选**：10fps / 30fps / 60fps
- 🔘 **清零按钮**：一键重置所有速度数据和图表
- 🔘 **可折叠设置面板**：点击收起/展开所有开关，释放图表空间

### UI / UX
- 🎨 **Material You 风格**，深色炫彩卡片界面
- 🎨 霓虹青 (#00E5FF) 主色调，搭配红/绿/青三色图表
- 🎨 **Bottom Tab 导航**：加速度 / GPS 双页面
- 🎨 胶囊式 TabLayout 背景，流畅动效

---

## 🔧 技术栈

| 层级 | 技术 |
|------|------|
| 语言 | Kotlin |
| 架构 | Fragment + ViewPager2 + TabLayout |
| UI | Material Design 3 (MaterialCardView, SwitchMaterial, MaterialButton) |
| 传感器 | SensorManager (TYPE_LINEAR_ACCELERATION / TYPE_ACCELEROMETER) |
| 定位 | LocationManager (GPS_PROVIDER) + ActivityResultContracts |
| 图表 | 自定义 View (Canvas 实时绘制折线图) |
| 权限 | ACCESS_FINE_LOCATION (运行时请求) |

---

## 📦 构建与安装

### 环境要求
- **Android Studio** Hedgehog (2023.1.1) 或更高版本
- **Kotlin** 1.9+
- **Gradle** 8.x
- **Target SDK** 34+ (Android 14/15)
- **Minimum SDK** 21 (Android 5.0)

### 构建步骤

```bash
# 克隆项目
git clone https://github.com/yourusername/accmeter.git
cd accmeter

# 用 Android Studio 打开，或命令行构建
./gradlew assembleDebug

# APK 位于
# app/build/outputs/apk/debug/app-debug.apk
