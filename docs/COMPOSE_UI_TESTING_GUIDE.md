# Compose Multiplatform UI 测试完整指南

> 来源: https://kotlinlang.org/docs/multiplatform/compose-test.html 及相关官方文档
>
> **注意**: 这个 API 是 Experimental，未来可能会变化。

---

## 1. 概述

Compose Multiplatform UI 测试使用与 Jetpack Compose 测试 API 相同的:
- **Finders** (查找器)
- **Assertions** (断言)  
- **Actions** (动作)
- **Matchers** (匹配器)

### 1.1 与 Jetpack Compose 的区别

| Jetpack Compose | Compose Multiplatform |
|-----------------|----------------------|
| 使用 JUnit 的 `TestRule` | 使用 `runComposeUiTest {}` 函数 |
| `createComposeRule()` | `ComposeUiTest` receiver |

**但是**: Desktop 目标可以使用 JUnit 风格的 API！

---

## 2. 两种测试方式

### 2.1 通用测试 (commonTest) - 跨平台

**目录**: `composeApp/src/commonTest/kotlin`

**特点**:
- 不依赖 JUnit TestRule
- 使用 `runComposeUiTest {}` 函数
- 可在所有平台运行

**依赖配置** (`build.gradle.kts`):
```kotlin
kotlin {
    sourceSets { 
        val jvmTest by getting

        commonTest.dependencies {
            implementation(kotlin("test"))

            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }

        jvmTest.dependencies { 
            implementation(compose.desktop.currentOs)
        }
    }
}
```

**测试示例**:
```kotlin
import androidx.compose.ui.test.*
import kotlin.test.Test

class CommonUITest {
    @Test
    fun myTest() = runComposeUiTest {
        setContent {
            var text by remember { mutableStateOf("Hello") }

            Text(
                text = text,
                modifier = Modifier.testTag("text")
            )
            Button(
                onClick = { text = "Compose" },
                modifier = Modifier.testTag("button")
            ) {
                Text("Click me")
            }
        }

        onNodeWithTag("text").assertTextEquals("Hello")
        onNodeWithTag("button").performClick()
        onNodeWithTag("text").assertTextEquals("Compose")
    }
}
```

**运行命令**:
```bash
./gradlew :composeApp:jvmTest
```

---

### 2.2 Desktop JUnit 测试 (desktopTest) - 仅限 Desktop

**目录**: `composeApp/src/desktopTest/kotlin`

**特点**:
- 使用 JUnit TestRule
- 使用 `createComposeRule()` 
- **可以看到真实 UI 窗口运行！**

**依赖配置** (`build.gradle.kts`):
```kotlin
kotlin { 
    sourceSets { 
        val desktopTest by getting { 
            dependencies {
                implementation(compose.desktop.uiTestJUnit4)
                implementation(compose.desktop.currentOs)
            }
        }
    }
}
```

**测试示例**:
```kotlin
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class DesktopExampleTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun myTest() {
        rule.setContent {
            var text by remember { mutableStateOf("Hello") }

            Text(
                text = text,
                modifier = Modifier.testTag("text")
            )
            Button(
                onClick = { text = "Compose" },
                modifier = Modifier.testTag("button")
            ) {
                Text("Click me")
            }
        }

        rule.onNodeWithTag("text").assertTextEquals("Hello")
        rule.onNodeWithTag("button").performClick()
        rule.onNodeWithTag("text").assertTextEquals("Compose")
    }
}
```

**运行命令**:
```bash
./gradlew desktopTest
```

---

## 3. 核心 API

### 3.1 Finders (查找器)

用于查找 UI 元素:

```kotlin
// 查找单个节点
onNode(matcher)
onNodeWithTag("myTag")
onNodeWithText("Button")
onNodeWithContentDescription("Close")

// 查找多个节点
onAllNodes(matcher)
onAllNodesWithText("Item")
onAllNodesWithTag("listItem")
```

### 3.2 Assertions (断言)

验证元素状态:

```kotlin
// 存在性
.assertExists()
.assertDoesNotExist()
.assertIsDisplayed()
.assertIsNotDisplayed()

// 状态
.assertIsEnabled()
.assertIsNotEnabled()
.assertIsSelected()
.assertIsNotSelected()
.assertIsFocused()

// 文本
.assertTextEquals("Expected")
.assertTextContains("partial")

// 集合
.assertCountEquals(4)
.assertAny(hasClickAction())
.assertAll(hasText("Item"))
```

### 3.3 Actions (动作)

模拟用户交互:

```kotlin
// 点击
.performClick()
.performDoubleClick()
.performLongClick()

// 滚动
.performScrollTo()
.performScrollToIndex(5)
.performScrollToNode(hasText("Target"))

// 输入
.performTextInput("Hello")
.performTextClearance()
.performTextReplacement("New text")

// 手势
.performTouchInput {
    swipeLeft()
    swipeRight()
    swipeUp()
    swipeDown()
}

// 键盘
.performKeyPress(KeyEvent(...))
```

### 3.4 Matchers (匹配器)

组合条件:

```kotlin
// 基础匹配
hasText("text")
hasTestTag("tag")
hasContentDescription("description")
hasClickAction()
isEnabled()
isFocused()

// 层级匹配
hasParent(matcher)
hasAnyAncestor(matcher)
hasAnyDescendant(matcher)
hasAnySibling(matcher)

// 组合
hasText("Submit") and isEnabled()
hasTestTag("btn") or hasTestTag("button")
```

---

## 4. 给 Composable 添加 testTag

要让测试能找到 UI 元素，需要添加 `testTag`:

```kotlin
@Composable
fun SlotCard(
    state: SlotState,
    onStart: () -> Unit
) {
    Card(
        modifier = Modifier.testTag("slot_card_${state.id}")
    ) {
        Text(
            text = state.status.name,
            modifier = Modifier.testTag("slot_status_${state.id}")
        )
        
        Button(
            onClick = onStart,
            modifier = Modifier.testTag("start_button_${state.id}")
        ) {
            Text("Start")
        }
    }
}
```

---

## 5. 调试: 打印语义树

```kotlin
// 打印整个语义树
rule.onRoot().printToLog("TAG")

// 打印未合并的语义树 (显示所有子节点)
rule.onRoot(useUnmergedTree = true).printToLog("TAG")
```

输出示例:
```
Node #1 at (...)px
|-Node #2 at (...)px
  Role = 'Button'
  Text = '[Hello, World]'
  Actions = [OnClick, GetTextLayoutResult]
```

---

## 6. Selectors 链式选择

```kotlin
rule.onNode(hasTestTag("Players"))
    .onChildren()
    .filter(hasClickAction())
    .assertCountEquals(4)
    .onFirst()
    .assert(hasText("John"))
```

---

## 7. 运行测试

### 命令行

```bash
# 通用测试 (jvmTest)
./gradlew :composeApp:jvmTest

# Desktop JUnit 测试
./gradlew desktopTest

# 所有测试
./gradlew allTests

# 指定测试类
./gradlew desktopTest --tests "io.example.SlotCardTest"
```

### IDE

1. 点击测试函数旁的绿色运行图标
2. 选择目标平台 (JVM)
3. 查看测试结果

---

## 8. 测试报告

测试完成后，HTML 报告生成在:
```
composeApp/build/reports/tests/
├── allTests/
│   └── index.html
├── testDebugUnitTest/
└── testReleaseUnitTest/
```

---

## 9. 本地化测试

对于 Desktop，可以在测试前设置 JVM 默认 Locale:

```kotlin
@Before
fun setup() {
    Locale.setDefault(Locale.CHINA)
}
```

---

## 10. 最佳实践

1. **通用代码测试放 commonTest**
   - 只使用 `kotlin.test` 和 `compose.uiTest`
   - 不使用平台特定 API

2. **Desktop 专属测试放 desktopTest**
   - 使用 JUnit 4 API (`@Rule`, `@Test`)
   - 可使用 `createComposeRule()`

3. **添加 testTag**
   - 给需要测试的元素加 `Modifier.testTag("唯一ID")`
   - 使用有意义的命名，如 `slot_card_0`, `start_button`

4. **打印语义树调试**
   - 使用 `printToLog()` 查看 UI 结构
   - 有助于理解 Compose 如何合并语义

5. **使用层级选择器**
   - `onChildren()`, `onParent()`, `onAncestors()`
   - 减少对绝对路径的依赖

---

## 11. 实际示例: 测试 SlotCard

```kotlin
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class SlotCardTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun slotCard_displaysCorrectStatus() {
        val mockState = SlotState(
            id = 0,
            sn = "ABC123",
            status = SlotStatus.RUNNING,
            currentStep = 3,
            totalSteps = 7
        )

        rule.setContent {
            SlotCard(
                state = mockState,
                onStart = {},
                onStop = {},
                onPause = {}
            )
        }

        // 验证槽位 ID 显示
        rule.onNodeWithTag("slot_id_0").assertTextContains("Slot 0")
        
        // 验证 SN 显示
        rule.onNodeWithTag("slot_sn_0").assertTextEquals("ABC123")
        
        // 验证状态
        rule.onNodeWithTag("slot_status_0").assertTextEquals("RUNNING")
        
        // 验证进度
        rule.onNodeWithTag("slot_progress_0").assertTextContains("3/7")
    }

    @Test
    fun slotCard_clickStart_triggersCallback() {
        var startClicked = false
        val mockState = SlotState(id = 0, status = SlotStatus.IDLE)

        rule.setContent {
            SlotCard(
                state = mockState,
                onStart = { startClicked = true },
                onStop = {},
                onPause = {}
            )
        }

        rule.onNodeWithTag("start_button_0").performClick()
        
        assert(startClicked) { "Start button callback not triggered" }
    }

    @Test
    fun slotCard_runningState_showsStopButton() {
        val mockState = SlotState(id = 0, status = SlotStatus.RUNNING)

        rule.setContent {
            SlotCard(state = mockState, onStart = {}, onStop = {}, onPause = {})
        }

        rule.onNodeWithTag("stop_button_0").assertExists()
        rule.onNodeWithTag("start_button_0").assertDoesNotExist()
    }
}
```

---

## 12. 关于"可观测 UI 操作"

**关键问题**: 运行 Compose Desktop 测试时，是否能看到 UI 窗口在自动操作？

**答案**: 
- **desktopTest (JUnit)**: 测试运行时会创建真实窗口，但通常很快完成
- 如果想观察自动化过程，可以添加 `delay()` 或使用 `waitUntil {}`

```kotlin
@Test
fun watchSlowTest() {
    rule.setContent { MyApp() }
    
    // 等待 UI 稳定
    rule.waitForIdle()
    
    // 可以在这里加断点或 delay 观察
    Thread.sleep(2000)  // 暂停 2 秒观察
    
    rule.onNodeWithTag("button").performClick()
    
    Thread.sleep(2000)  // 观察点击后的效果
    
    rule.onNodeWithTag("result").assertTextEquals("Clicked!")
}
```

**更好的方式**: 使用 `waitUntil` 等待条件满足:

```kotlin
rule.waitUntil(timeoutMillis = 5000) {
    rule.onAllNodesWithTag("loading").fetchSemanticsNodes().isEmpty()
}
```

---

## 13. 截图功能 (Screenshot Capture)

**是的，Compose Test 支持截图！** 使用 `captureToImage()` 方法：

### 13.1 基础截图

```kotlin
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Rule
import org.junit.Test
import java.io.File

class ScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun captureSlotCardScreenshot() {
        rule.setContent {
            SlotCard(
                state = SlotState(id = 0, status = SlotStatus.RUNNING),
                onStart = {},
                onStop = {},
                onPause = {}
            )
        }

        rule.waitForIdle()

        // 捕获整个根节点的截图
        val image = rule.onRoot().captureToImage()
        
        // 转换为 Skia Bitmap 并保存为 PNG
        val skiaBitmap = image.asSkiaBitmap()
        val pngData = skiaBitmap.encodeToData(EncodedImageFormat.PNG)
        
        pngData?.let {
            File("screenshots/slot_card_running.png").apply {
                parentFile?.mkdirs()
                writeBytes(it.bytes)
            }
        }
        
        println("Screenshot saved to screenshots/slot_card_running.png")
    }
}
```

### 13.2 捕获特定组件

```kotlin
@Test
fun captureSpecificComponent() {
    rule.setContent {
        Column {
            Button(
                onClick = {},
                modifier = Modifier.testTag("myButton")
            ) {
                Text("Click me")
            }
            Text("Other content")
        }
    }

    // 只捕获按钮
    val buttonImage = rule.onNodeWithTag("myButton").captureToImage()
    saveImage(buttonImage, "button_only.png")
}

private fun saveImage(image: androidx.compose.ui.graphics.ImageBitmap, filename: String) {
    val skiaBitmap = image.asSkiaBitmap()
    val pngData = skiaBitmap.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)
    pngData?.let {
        File("screenshots/$filename").apply {
            parentFile?.mkdirs()
            writeBytes(it.bytes)
        }
    }
}
```

### 13.3 截图对比测试 (Visual Regression Testing)

使用截图对比可以检测 UI 视觉变化：

```kotlin
@Test
fun compareScreenshot() {
    rule.setContent {
        MyComponent()
    }

    val currentImage = rule.onRoot().captureToImage()
    
    // 保存/对比截图
    val baselinePath = "screenshots/baseline/my_component.png"
    val currentPath = "screenshots/current/my_component.png"
    
    saveImage(currentImage, currentPath)
    
    // 比较两张图片 (需要自己实现或使用库)
    val baseline = File(baselinePath)
    if (baseline.exists()) {
        // 对比逻辑...
        // 如果不同，测试失败
    } else {
        // 首次运行，保存为 baseline
        saveImage(currentImage, baselinePath)
    }
}
```

### 13.4 第三方截图测试库

对于更完善的截图测试，推荐使用：

| 库 | 平台 | 特点 |
|---|------|------|
| **Paparazzi** | JVM | 无需模拟器，快速 |
| **Roborazzi** | JVM/Android | 支持录制回放 |
| **ComposablePreviewScanner** | 跨平台 | 自动从 @Preview 生成 |

---

## 14. 完整项目配置示例

### build.gradle.kts (composeApp 模块)

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm("desktop")
    
    sourceSets {
        val desktopMain by getting
        val desktopTest by getting
        
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }
        
        commonTest.dependencies {
            implementation(kotlin("test"))
            
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
        
        desktopTest.dependencies {
            implementation(compose.desktop.uiTestJUnit4)
            implementation(compose.desktop.currentOs)
        }
    }
}
```

### 目录结构

```
composeApp/
├── src/
│   ├── commonMain/kotlin/       # 共享代码
│   ├── commonTest/kotlin/       # 跨平台测试 (runComposeUiTest)
│   ├── desktopMain/kotlin/      # Desktop 代码
│   └── desktopTest/kotlin/      # Desktop JUnit 测试 (createComposeRule)
│       └── io/example/
│           ├── SlotCardTest.kt
│           └── ScreenshotTest.kt
└── build.gradle.kts
```
```
