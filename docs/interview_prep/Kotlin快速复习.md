# Kotlin 快速复习（口语化）

> 🎯 你说Kotlin不熟但Swift很熟。好消息是它俩很像。过一遍核心概念，面试能聊就行。

---

## 1. Kotlin vs Swift 对照

| 概念 | Kotlin | Swift |
|------|--------|-------|
| 可空类型 | `String?` | `String?` |
| 空安全调用 | `?.` | `?.` |
| 强制解包 | `!!` | `!` |
| 空合并 | `?:` (Elvis) | `??` |
| 数据类 | `data class` | `struct` (Swift没完全对应) |
| 扩展函数 | `fun String.xxx()` | `extension String` |
| Lambda | `{ x -> x + 1 }` | `{ x in x + 1 }` |
| 单例 | `object` | `class + static` |

**怎么说**：
"Kotlin和Swift设计理念很像，都是现代静态类型语言。我Swift写了好几年，Kotlin上手很快，语法细节可能需要查，但思路是通的。"

---

## 2. 空安全（Null Safety）

**一句话**：Kotlin编译器强制你处理null，不会有突然的NullPointerException。

```kotlin
var name: String = "hello"   // 不能为null
var name: String? = null     // 可以为null

// 安全调用
name?.length                 // null就返回null
name!!.length               // 强制解包，null会崩

// Elvis操作符
val len = name?.length ?: 0  // null就用默认值
```

---

## 3. 数据类（data class）

**一句话**：自动生成equals、hashCode、toString、copy。

```kotlin
data class User(val id: String, val name: String)

val u1 = User("1", "张三")
val u2 = u1.copy(name = "李四")  // 复制并修改
```

**怎么说**：
"data class就是帮你省写样板代码，类似Swift的struct自动Equatable。"

---

## 4. 密封类（sealed class）

**一句话**：有限的子类，when表达式必须穷举。

```kotlin
sealed class Result {
    data class Success(val data: String) : Result()
    data class Error(val msg: String) : Result()
}

when (result) {
    is Result.Success -> println(result.data)
    is Result.Error -> println(result.msg)
    // 不需要else，编译器知道只有这两种
}
```

**怎么说**：
"sealed class用来表示有限的几种状态，比如网络请求结果就是成功或失败。when必须写全，不会漏掉情况。"

---

## 5. 协程（Coroutines）

**一句话**：Kotlin的异步方案，比线程轻量，比回调好写。

```kotlin
suspend fun fetchData(): String {
    delay(1000)  // 挂起，不阻塞线程
    return "data"
}

// 启动协程
lifecycleScope.launch {
    val data = fetchData()
    println(data)
}
```

**关键概念**：
- `suspend` — 可挂起函数
- `launch` — 启动协程，不返回结果
- `async` — 启动协程，返回Deferred，可以await
- `Dispatchers.IO` — IO密集型任务用
- `Dispatchers.Main` — 更新UI用

**怎么说**：
"协程是Kotlin的异步方案，比回调好写。suspend函数可以挂起但不阻塞线程。类似Swift的async/await。"

---

## 6. 扩展函数（Extension Functions）

**一句话**：给现有类加方法，不用继承。

```kotlin
fun String.addExclamation(): String {
    return this + "!"
}

"hello".addExclamation()  // "hello!"
```

**怎么说**：
"扩展函数让你给任何类加方法，包括第三方库的类。这个Swift也有，叫extension。"

---

## 7. 高阶函数

**一句话**：函数可以当参数传，也可以当返回值。

```kotlin
fun process(data: String, transform: (String) -> String): String {
    return transform(data)
}

process("hello") { it.uppercase() }  // "HELLO"
```

**常用高阶函数**：
- `map` — 转换每个元素
- `filter` — 过滤
- `fold/reduce` — 聚合
- `let/run/apply/also` — 作用域函数

---

## 8. 作用域函数（Scope Functions）

| 函数 | 对象引用 | 返回值 | 典型用途 |
|------|---------|--------|---------|
| `let` | `it` | Lambda结果 | 空安全处理 |
| `run` | `this` | Lambda结果 | 对象配置并计算 |
| `apply` | `this` | 对象本身 | 对象配置 |
| `also` | `it` | 对象本身 | 额外操作（如日志） |

```kotlin
// let常用于空安全
user?.let { println(it.name) }

// apply常用于配置
val dialog = Dialog().apply {
    title = "提示"
    message = "确定吗？"
}
```

---

## 9. Compose 基础（你项目用到了）

**一句话**：声明式UI，状态变了界面自动更新。

```kotlin
@Composable
fun Greeting(name: String) {
    Text("Hello, $name")
}
```

**核心概念**：
- `@Composable` — 标记可组合函数
- `remember` — 在重组中保留状态
- `mutableStateOf` — 可观察状态，变化触发重组
- `LaunchedEffect` — 在Composable里启动协程

```kotlin
var count by remember { mutableStateOf(0) }
Button(onClick = { count++ }) {
    Text("点击: $count")
}
```

**怎么说**：
"Compose是声明式UI，跟SwiftUI一个思路——你描述界面应该是什么样，框架帮你处理更新。状态变了界面自动刷新。"

---

## 10. Flow（响应式流）

**一句话**：冷流，订阅才开始发射，类似RxJava的Observable。

```kotlin
val flow = flow {
    emit(1)
    emit(2)
    emit(3)
}

flow.collect { value ->
    println(value)
}
```

**常用操作**：
- `map` — 转换
- `filter` — 过滤
- `collect` — 收集/订阅
- `StateFlow` — 热流，有当前值，UI常用

**怎么说**：
"Flow是Kotlin的响应式流，StateFlow在Compose里用得多，ViewModel里持有StateFlow，UI层collect它。"

---

## 快速自测

能用自己的话说清楚吗？

- [ ] Kotlin空安全怎么处理
- [ ] data class有什么好处
- [ ] 协程是什么，suspend是什么意思
- [ ] Compose的声明式UI是什么意思
- [ ] StateFlow和普通Flow有什么区别
- [ ] 作用域函数let/apply什么时候用
