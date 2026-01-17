# C# 快速复习（口语化）

> 🎯 两年没写C#了，快速过一遍核心概念，找回手感。不是背题，是让你能自然地聊。

---

## 1. 异步编程（async/await）

**一句话**：让程序在等IO的时候不傻等，去干别的事。

**怎么说**：
"async/await本质是个语法糖，让你写异步代码像写同步代码一样。await一个Task的时候，线程不会阻塞，会去做别的事，等结果回来再继续执行后面的代码。"

**常见坑**：
- 别用`.Result`或`.Wait()`，会死锁
- async方法返回`Task`或`Task<T>`，不要返回`void`（除了事件处理）
- `ConfigureAwait(false)`在库代码里用，避免上下文切换开销

---

## 2. 泛型（Generics）

**一句话**：写一份代码，让它能处理多种类型。

**怎么说**：
"泛型让你不用为每种类型写重复代码。比如`List<T>`，T可以是int、string、任何类型。编译器会帮你做类型检查，比用object安全。"

**约束**：
- `where T : class` — T必须是引用类型
- `where T : struct` — T必须是值类型
- `where T : new()` — T必须有无参构造函数
- `where T : ISomething` — T必须实现某接口

---

## 3. 依赖注入（DI）

**一句话**：不要自己new对象，让容器帮你管理。

**怎么说**：
"依赖注入就是把对象的创建和使用分开。你在类里声明'我需要一个IService'，具体用哪个实现由外部决定。好处是解耦、好测试。"

**三种生命周期**：
- `Singleton` — 全局一个实例
- `Scoped` — 每个请求一个实例（Web场景常用）
- `Transient` — 每次注入都新建

---

## 4. LINQ

**一句话**：用链式调用处理集合数据，类似SQL。

**常用方法**：
```csharp
var result = list
    .Where(x => x.Age > 18)      // 过滤
    .Select(x => x.Name)         // 投影
    .OrderBy(x => x)             // 排序
    .ToList();                   // 执行
```

**延迟执行**：`Where`、`Select`不会立即执行，`ToList()`、`Count()`才会真正执行。

---

## 5. 值类型 vs 引用类型

**一句话**：值类型存在栈上，引用类型存在堆上。

- **值类型**：`int`、`double`、`bool`、`struct`、枚举
- **引用类型**：`class`、`string`、数组、委托

**怎么说**：
"值类型赋值是复制，引用类型赋值是共享同一块内存。struct适合小的、不可变的数据，class适合复杂对象。"

---

## 6. 委托和事件

**一句话**：委托是方法的指针，事件是委托的封装。

**怎么说**：
"委托让你把方法当参数传。Action是无返回值的委托，Func是有返回值的。事件就是委托加了保护，外部只能订阅和取消订阅，不能直接调用。"

```csharp
// 委托
Action<string> log = Console.WriteLine;
Func<int, int, int> add = (a, b) => a + b;

// 事件
public event EventHandler DataReceived;
```

---

## 7. 接口 vs 抽象类

**接口**：定义契约，一个类可以实现多个接口
**抽象类**：可以有实现，只能单继承

**怎么选**：
"如果是'能做什么'用接口（IDisposable、IEnumerable），如果是'是什么'用抽象类。C# 8以后接口也能有默认实现了，界限模糊了一些。"

---

## 8. IDisposable 和 using

**一句话**：用完资源要释放，using帮你自动调Dispose。

```csharp
using (var stream = new FileStream(...))
{
    // 用完自动释放
}

// C# 8简写
using var stream = new FileStream(...);
```

**怎么说**：
"IDisposable用于非托管资源的释放，比如文件句柄、数据库连接。using语句保证即使出异常也会调Dispose。"

---

## 9. 常见设计模式

**单例**：全局唯一实例
```csharp
public class Singleton
{
    private static readonly Singleton _instance = new();
    public static Singleton Instance => _instance;
    private Singleton() { }
}
```

**工厂**：把创建逻辑封装起来，返回接口而非具体类型

**观察者**：事件驱动，发布-订阅模式

---

## 10. .NET 8/10 新东西（能提一嘴加分）

- **原生AOT编译**：直接编译成机器码，启动快
- **Minimal API**：更简洁的Web API写法
- **Source Generator**：编译时生成代码，替代反射
- **Record**：不可变数据类型，自动生成Equals/GetHashCode

**怎么说**：
"我知道.NET这几年变化挺大的，原生AOT、Minimal API这些我了解概念，具体用可能需要查一查文档。"

---

## 11. P/Invoke（这个你项目用到了）

**一句话**：C#调用非托管DLL的方式。

```csharp
[DllImport("libcatalytic", CallingConvention = CallingConvention.Cdecl)]
public static extern int cat_engine_create(out IntPtr engine);
```

**怎么说**：
"Catalyst项目里Host调Engine就是用P/Invoke。.NET 7以后推荐用`[LibraryImport]`，性能更好，还能用Source Generator。"

---

## 快速自测

能用自己的话说清楚吗？

- [ ] async/await是什么，为什么不能用.Result
- [ ] 泛型约束有哪些
- [ ] 依赖注入三种生命周期
- [ ] LINQ延迟执行是什么意思
- [ ] 值类型和引用类型的区别
- [ ] 什么时候用接口什么时候用抽象类
