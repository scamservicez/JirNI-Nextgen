# JirNI-nextgen — внешний доступ к JVM без JNI

[![JDK](https://img.shields.io/badge/JDK-22%2B-orange?logo=openjdk)](https://openjdk.org/projects/jdk/25/)
[![FFM](https://img.shields.io/badge/Foreign%20Function%20%26%20Memory-JEP%20454-blue)](https://openjdk.org/jeps/454)
[![Platform](https://img.shields.io/badge/platform-Windows%20x64-lightgrey?logo=windows)]()
[![Build](https://img.shields.io/badge/build-Maven-C71A36?logo=apache-maven)]()

> Полная переноска C++ библиотеки **`JirNI-nextgen`** на Java 22+ с использованием
> Foreign Function & Memory API. Цель — **побайтовая совместимость поведения с оригиналом**.

---

## ✨ Что это такое

`JirNI-nextgen` позволяет **читать и изменять объекты внутри чужой JVM из соседнего процесса** —
без JNI, без агентов, без `sun.misc.Unsafe` и без модификации целевого процесса.

Вся «магия» построена на двух китах:

| Технология | Зачем |
|---|---|
| **WinAPI** (`ReadProcessMemory` / `WriteProcessMemory`) | Чтение/запись памяти чужого процесса |
| **Foreign Function & Memory API** (JEP 454, JDK 22) | Современная замена JNI — биндинги к `kernel32.dll` и `psapi.dll` прямо из Java |

Иначе говоря — это «**внешний дебаггер уровня HotSpot**», который понимает внутренние структуры JVM
(`Klass`, `Symbol`, `oopDesc`, `VMStructs`) и умеет лазить по ним так, словно ваш код работает внутри неё.

---

## 🧠 Как это работает изнутри

```
   ┌─────────────────┐        ReadProcessMemory          ┌─────────────────┐
   │   ВАШ процесс   │ ───────────────────────────────►  │  ЧУЖАЯ JVM      │
   │ (JirNI-nextgen) │ ◄───────────────────────────────  │  (Minecraft и   │
   └────────┬────────┘        WriteProcessMemory         │   что угодно)   │
            │                                            └─────────────────┘
            ▼
   ┌────────────────────────────────────────┐
   │ FFM-биндинги Win32 (kernel32 + psapi)  │
   │ ─ OpenProcess, EnumProcessModulesEx…   │
   ├────────────────────────────────────────┤
   │ AutoOffsetParser                       │
   │ ─ парсит PE-экспорты `jvm.dll` и       │
   │   таблицу `gHotSpotVMStructs`          │
   ├────────────────────────────────────────┤
   │ Jvm: findClass / findField / oop / …   │
   │ ─ воспроизводит логику HotSpot         │
   ├────────────────────────────────────────┤
   │ Scanner (IDA-style "?? AA BB" patterns)│
   └────────────────────────────────────────┘
```

1. **`Win32`** — оборачивает `OpenProcess`, `EnumProcessModulesEx`, `GetModuleBaseNameA` через FFM.
   Никакого `System.loadLibrary` — только `Linker.nativeLinker()`.
2. **`AutoOffsetParser`** — открывает `jvm.dll` цели, разбирает PE-экспорты и таблицу `gHotSpotVMStructs`,
   автоматически вычисляя оффсеты полей `Klass`, `InstanceKlass`, `Symbol` и т. д. —
   **не нужно хардкодить смещения под каждую версию JVM**.
3. **`Memory`** — кеширует хэндл процесса и предоставляет `readMemory(addr, T)` / `writeMemory(addr, T, val)`
   для всех примитивов.
4. **`Jvm`** — высокоуровневый API: `findClass`, `findField`, `getObjectField`, `instanceOf`,
   `encodeOop` / `decodeOop` (с учётом compressed oops).
5. **`MinecraftMappings`** — поверх всего лежат Fabric-маппинги: можно обращаться к классам Minecraft
   по человеческим именам (`net.minecraft.client.MinecraftClient`), а не по обфусцированным.

---

## 📋 Требования

* **JDK 22** или новее (рекомендуется **JDK 25**).
* **Windows x64** — тот же таргет, что и у оригинала.
* **Maven 3.9+** (опционально — можно собрать и `javac`-ом напрямую).

---

## 📁 Структура проекта

```
src/main/java/com/javaexternal/
├── Main.java                       — соответствие ExternelJava.cpp
├── win32/Win32.java                — FFM-биндинги kernel32 + psapi
├── memory/
│   ├── Memory.java                 — readMemory0 / readMemory / writeMemory + jvm/handle
│   ├── MemUtil.java                — getPointerFromAddress, getPidFromProcessName,
│   │                                 spoonGetJvmBase, getHexOfPointer
│   └── Scanner.java                — сигнатурный сканер (IDA-style "?? AA BB")
├── jvm/
│   ├── Symbol.java                 — HotSpot Symbol (_hash_and_refcount/_length/_body)
│   ├── AccessFlags.java
│   ├── FieldFlags.java
│   ├── FieldInfo17.java            — упакованные 6×uint16 (JVM ≤ 17)
│   ├── FieldInfo20.java            — UNSIGNED5-поток (JVM 20+)
│   ├── VMStructEntry.java
│   ├── ExportFunction.java
│   ├── AutoOffsetParser.java       — парсер экспортов PE + gHotSpotVMStructs
│   ├── Jvm.java                    — все функции jvm.cpp (findClass/findField/…,
│   │                                 encodeOop/decodeOop, get/setObjectField,
│   │                                 getObjectArrayElement, instanceOf и т. д.)
│   └── JavaLang.java               — jString2String (java.cpp)
├── parser/Parser.java              — split / parseBytes / parseClasses / parseFields
└── minecraft/MinecraftMappings.java — Fabric-маппинги (initMappings / findMinecraft*)

src/main/resources/mappings/mappingsExt.txt — встроенная таблица Fabric → Mojang
```

---

## ⚙️ Сборка

```powershell
mvn -f java\pom.xml clean package
```

или вручную, без Maven:

```powershell
$out = "java\target\classes"
New-Item -ItemType Directory -Force $out | Out-Null
$files = Get-ChildItem -Recurse "java\src\main\java" -Filter *.java |
         ForEach-Object { $_.FullName }
& javac --release 25 -d $out @files
Copy-Item -Recurse "java\src\main\resources\*" $out
```

---

## 🚀 Запуск

JEP 472 требует разрешения нативного доступа:

```powershell
java --enable-native-access=ALL-UNNAMED `
     -cp java\target\classes `
     com.javaexternal.Main
# далее введите PID процесса с JVM
```

> ⚠️ Без флага `--enable-native-access` FFM-вызовы упадут с `IllegalCallerException`.

---

## 💡 Примеры использования

### 1. Подключиться к чужой JVM и получить базу `jvm.dll`
```java
int pid = MemUtil.getPidFromProcessName("javaw.exe");
Memory.attach(pid);

long jvmBase = MemUtil.spoonGetJvmBase();
AutoOffsetParser.init(jvmBase);   // распарсить VMStructs автоматически
```

### 2. Найти класс и поле в чужой JVM
```java
long klass = Jvm.findClass("java/lang/String");
int  offset = Jvm.findField(klass, "value");   // массив байт строки

System.out.printf("String.value @ +0x%X%n", offset);
```

### 3. Прочитать поле живого объекта
```java
long player = MinecraftMappings.findMinecraftClient();   // oop игрока
float health = Jvm.getFloatField(player, "health");

System.out.println("HP = " + health);
```

### 4. Записать значение обратно — изменить состояние чужой JVM
```java
Jvm.setFloatField(player, "health", 20.0f);   // полное HP
```

### 5. Сканер сигнатур в IDA-стиле
```java
long addr = Scanner.find(jvmBase, "48 8B 05 ?? ?? ?? ?? 48 85 C0 74");
```

---

## 📐 Маппинг C → Java

| C++ (`jvmTypes.h`)               | Java                                          |
|----------------------------------|-----------------------------------------------|
| `jbool`                          | `boolean`                                     |
| `jbyte / jshort / jint / jlong`  | `byte / short / int / long`                   |
| `jfloat / jdouble`               | `float / double`                              |
| `jclass / jobject` (`void*`)     | `long` (адрес в чужой памяти)                 |
| `jfieldid` (формально uint16)    | `int` (смещение)                              |
| `Symbol*`                        | `Symbol` (Java POJO, прочитанный через FFM)   |
| `HANDLE`                         | `MemorySegment` (адрес процесса)              |
| шаблоны `getGenericField<T>`     | `getIntField` / `getLongField` / …            |

---

## 🎯 Для чего это нужно

- **Реверс-инжиниринг и аналитика Minecraft** — основной кейс автора.
- **Внешние дебаггеры и инспекторы** уровня HotSpot без подключения JVMTI-агента.
- **Снапшоты состояния** чужой JVM в продакшене, когда нельзя присоединить `jdb` / `jcmd`.
- **Образовательная ценность** — открытый учебник по тому, *как HotSpot хранит классы и объекты в памяти*,
  написанный на современном Java.

---

## 🔍 Особенности и тонкости

* Все обращения к чужому процессу идут через `ReadProcessMemory` / `WriteProcessMemory`
  так же, как в оригинале — **никакого JNI и `Unsafe`**.
* Кэши классов/полей реализованы на `HashMap` / `ConcurrentHashMap`
  и зеркалят `unordered_map` из C++.
* `FieldInfo17` и `FieldInfo20` покрывают изменения layout-а полей между JDK 17 и 20+.
* **Compressed oops** учитываются автоматически (`encodeOop` / `decodeOop`).
* `findField` исправляет потенциальную бесконечную рекурсию (если у `Klass` нет `_super`),
  но в остальном эквивалентен оригиналу.
* `EnumProcessModulesEx` / `GetModuleBaseNameA` ищутся сначала в `psapi.dll`,
  затем по `K32`-префиксу в `kernel32.dll` для совместимости с разными версиями Windows.

---

## 👤 Авторство

**`JirNI-nextgen`** — Java-переписывание (Java 22+ / FFM) оригинальной C++ библиотеки
**[`JavaExternalAccess`](https://github.com/dino939/JavaExternalAccess)** автора
**[dino939](https://github.com/dino939)**.

---
---

# 🇬🇧 English version

# JirNI-nextgen — external JVM access without JNI

[![JDK](https://img.shields.io/badge/JDK-22%2B-orange?logo=openjdk)](https://openjdk.org/projects/jdk/25/)
[![FFM](https://img.shields.io/badge/Foreign%20Function%20%26%20Memory-JEP%20454-blue)](https://openjdk.org/jeps/454)
[![Platform](https://img.shields.io/badge/platform-Windows%20x64-lightgrey?logo=windows)]()
[![Build](https://img.shields.io/badge/build-Maven-C71A36?logo=apache-maven)]()

> A full port of the C++ library **`JirNI-nextgen`** to Java 22+ on top of the
> Foreign Function & Memory API. The goal — **byte-for-byte behavioral compatibility
> with the original**.

---

## ✨ What it is

`JirNI-nextgen` lets you **read and modify objects inside another JVM from a neighboring
process** — without JNI, without agents, without `sun.misc.Unsafe`, and without modifying
the target process.

The whole thing stands on two pillars:

| Technology | Purpose |
|---|---|
| **WinAPI** (`ReadProcessMemory` / `WriteProcessMemory`) | Read/write the memory of another process |
| **Foreign Function & Memory API** (JEP 454, JDK 22) | A modern replacement for JNI — bindings to `kernel32.dll` and `psapi.dll` straight from Java |

In other words — this is an "**external HotSpot-level debugger**" that understands the JVM's
internal structures (`Klass`, `Symbol`, `oopDesc`, `VMStructs`) and walks through them as if
your code were running inside it.

---

## 🧠 How it works under the hood

```
   ┌─────────────────┐        ReadProcessMemory          ┌─────────────────┐
   │   YOUR process  │ ───────────────────────────────►  │  TARGET JVM     │
   │ (JirNI-nextgen) │ ◄───────────────────────────────  │  (Minecraft or  │
   └────────┬────────┘        WriteProcessMemory         │   anything)     │
            │                                            └─────────────────┘
            ▼
   ┌────────────────────────────────────────┐
   │ FFM bindings to Win32 (kernel32+psapi) │
   │ ─ OpenProcess, EnumProcessModulesEx…   │
   ├────────────────────────────────────────┤
   │ AutoOffsetParser                       │
   │ ─ parses the PE exports of `jvm.dll`   │
   │   and the `gHotSpotVMStructs` table    │
   ├────────────────────────────────────────┤
   │ Jvm: findClass / findField / oop / …   │
   │ ─ reproduces HotSpot's logic           │
   ├────────────────────────────────────────┤
   │ Scanner (IDA-style "?? AA BB" patterns)│
   └────────────────────────────────────────┘
```

1. **`Win32`** — wraps `OpenProcess`, `EnumProcessModulesEx`, `GetModuleBaseNameA` via FFM.
   No `System.loadLibrary` — just `Linker.nativeLinker()`.
2. **`AutoOffsetParser`** — opens the target's `jvm.dll`, walks the PE exports and the
   `gHotSpotVMStructs` table, and automatically computes the field offsets of `Klass`,
   `InstanceKlass`, `Symbol`, etc. — **no need to hardcode offsets for every JVM version**.
3. **`Memory`** — caches the process handle and exposes `readMemory(addr, T)` /
   `writeMemory(addr, T, val)` for every primitive.
4. **`Jvm`** — the high-level API: `findClass`, `findField`, `getObjectField`, `instanceOf`,
   `encodeOop` / `decodeOop` (compressed-oops aware).
5. **`MinecraftMappings`** — on top of it all sits a layer of Fabric mappings, so Minecraft
   classes can be referenced by human-readable names (`net.minecraft.client.MinecraftClient`)
   rather than obfuscated ones.

---

## 📋 Requirements

* **JDK 22** or newer (**JDK 25** recommended).
* **Windows x64** — the same target as the original.
* **Maven 3.9+** (optional — you can also build with plain `javac`).

---

## 📁 Project layout

```
src/main/java/com/javaexternal/
├── Main.java                       — counterpart of ExternelJava.cpp
├── win32/Win32.java                — FFM bindings for kernel32 + psapi
├── memory/
│   ├── Memory.java                 — readMemory0 / readMemory / writeMemory + jvm/handle
│   ├── MemUtil.java                — getPointerFromAddress, getPidFromProcessName,
│   │                                 spoonGetJvmBase, getHexOfPointer
│   └── Scanner.java                — pattern scanner (IDA-style "?? AA BB")
├── jvm/
│   ├── Symbol.java                 — HotSpot Symbol (_hash_and_refcount/_length/_body)
│   ├── AccessFlags.java
│   ├── FieldFlags.java
│   ├── FieldInfo17.java            — packed 6×uint16 (JVM ≤ 17)
│   ├── FieldInfo20.java            — UNSIGNED5 stream (JVM 20+)
│   ├── VMStructEntry.java
│   ├── ExportFunction.java
│   ├── AutoOffsetParser.java       — PE export parser + gHotSpotVMStructs
│   ├── Jvm.java                    — every function from jvm.cpp (findClass/findField/…,
│   │                                 encodeOop/decodeOop, get/setObjectField,
│   │                                 getObjectArrayElement, instanceOf, etc.)
│   └── JavaLang.java               — jString2String (java.cpp)
├── parser/Parser.java              — split / parseBytes / parseClasses / parseFields
└── minecraft/MinecraftMappings.java — Fabric mappings (initMappings / findMinecraft*)

src/main/resources/mappings/mappingsExt.txt — built-in Fabric → Mojang table
```

---

## ⚙️ Build

```powershell
mvn -f java\pom.xml clean package
```

or manually, without Maven:

```powershell
$out = "java\target\classes"
New-Item -ItemType Directory -Force $out | Out-Null
$files = Get-ChildItem -Recurse "java\src\main\java" -Filter *.java |
         ForEach-Object { $_.FullName }
& javac --release 25 -d $out @files
Copy-Item -Recurse "java\src\main\resources\*" $out
```

---

## 🚀 Running

JEP 472 requires explicit permission for native access:

```powershell
java --enable-native-access=ALL-UNNAMED `
     -cp java\target\classes `
     com.javaexternal.Main
# then enter the PID of the target JVM process
```

> ⚠️ Without the `--enable-native-access` flag, FFM calls will fail with `IllegalCallerException`.

---

## 💡 Usage examples

### 1. Attach to a target JVM and resolve the `jvm.dll` base
```java
int pid = MemUtil.getPidFromProcessName("javaw.exe");
Memory.attach(pid);

long jvmBase = MemUtil.spoonGetJvmBase();
AutoOffsetParser.init(jvmBase);   // parse VMStructs automatically
```

### 2. Look up a class and a field in the target JVM
```java
long klass = Jvm.findClass("java/lang/String");
int  offset = Jvm.findField(klass, "value");   // the underlying byte array

System.out.printf("String.value @ +0x%X%n", offset);
```

### 3. Read a field from a live object
```java
long player = MinecraftMappings.findMinecraftClient();   // player oop
float health = Jvm.getFloatField(player, "health");

System.out.println("HP = " + health);
```

### 4. Write a value back — mutate the foreign JVM's state
```java
Jvm.setFloatField(player, "health", 20.0f);   // full HP
```

### 5. IDA-style signature scanner
```java
long addr = Scanner.find(jvmBase, "48 8B 05 ?? ?? ?? ?? 48 85 C0 74");
```

---

## 📐 C → Java mapping

| C++ (`jvmTypes.h`)               | Java                                                     |
|----------------------------------|----------------------------------------------------------|
| `jbool`                          | `boolean`                                                |
| `jbyte / jshort / jint / jlong`  | `byte / short / int / long`                              |
| `jfloat / jdouble`               | `float / double`                                         |
| `jclass / jobject` (`void*`)     | `long` (address in foreign memory)                       |
| `jfieldid` (formally uint16)     | `int` (offset)                                           |
| `Symbol*`                        | `Symbol` (Java POJO read through FFM)                    |
| `HANDLE`                         | `MemorySegment` (process address)                        |
| `getGenericField<T>` templates   | `getIntField` / `getLongField` / …                       |

---

## 🎯 What it is for

- **Reverse engineering and Minecraft analytics** — the original author's primary use case.
- **External debuggers and inspectors** at the HotSpot level without attaching a JVMTI agent.
- **State snapshots** of a foreign JVM in production when you can't attach `jdb` / `jcmd`.
- **Educational value** — an open textbook on *how HotSpot lays out classes and objects in memory*,
  written in modern Java.

---

## 🔍 Notable details

* Every access to the foreign process goes through `ReadProcessMemory` / `WriteProcessMemory`,
  just like in the original — **no JNI and no `Unsafe`**.
* Class/field caches use `HashMap` / `ConcurrentHashMap` and mirror the `unordered_map` from C++.
* `FieldInfo17` and `FieldInfo20` cover the field-layout changes between JDK 17 and 20+.
* **Compressed oops** are handled automatically (`encodeOop` / `decodeOop`).
* `findField` fixes a potential infinite recursion (when a `Klass` has no `_super`)
  but is otherwise equivalent to the original.
* `EnumProcessModulesEx` / `GetModuleBaseNameA` are first looked up in `psapi.dll`,
  then by the `K32` prefix in `kernel32.dll` for compatibility with different Windows versions.

---

## 👤 Credits

**`JirNI-nextgen`** is a Java 22+ / FFM rewrite of the original C++ library
**[`JavaExternalAccess`](https://github.com/dino939/JavaExternalAccess)** by
**[dino939](https://github.com/dino939)**.
