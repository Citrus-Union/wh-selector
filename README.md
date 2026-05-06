# Warehouse Selector
[![License](https://img.shields.io/github/license/Citrus-Union/Carpet-LMS-Addition.svg)](http://www.gnu.org/licenses/gpl-3.0.html)

Warehouse Selector 是一个 Minecraft Fabric 客户端辅助模组，用于为 [Carpet LMS Addition](https://carpet.lms.nm.cn/docs/) 的 `checkStorage` 功能生成和维护容器清单 JSON 文件。

它通过金斧头在游戏内选择区域，扫描选区内的箱子、桶、潜影盒等容器，并把容器坐标写入 Carpet LMS Addition 使用的 `checkStorageList` 文件中。

## 适用场景

Carpet LMS Addition 的仓储检查功能会读取世界目录下的容器清单文件：

```text
world/carpetlmsaddition/checkStorageList/*.json
```

这些文件由 `world/carpetlmsaddition/checkStorageConfig.json` 中的 `storageList` 字段指定。例如：

```json
{
  "port": 7000,
  "autoStartWebsite": false,
  "customWebsite": false,
  "noPassword": false,
  "expireDay": 0,
  "storageList": ["example.json"]
}
```

此时 Carpet LMS Addition 会读取：

```text
world/carpetlmsaddition/checkStorageList/example.json
```

Warehouse Selector 的作用就是帮助你在客户端游戏内快速生成或维护这个 `example.json` 之类的容器清单文件。

## 功能特性

- **区域选点**：手持金斧头左键设置 `pos1`，右键设置 `pos2`。
- **容器扫描**：扫描选区内的容器方块并写入 JSON。
- **本地文件读写**：不依赖 API，不连接服务器接口，只读写你指定的本地 JSON 文件。
- **按维度保存**：自动按 `overworld`、`end`、`nether` 分类保存坐标。
- **容器高亮**：可在客户端高亮显示已记录的容器线框。
- **透视线框**：高亮线框可透过其他方块看到，方便检查隐藏容器。
- **单行坐标格式**：坐标以 `[x,y,z]` 的形式保存，避免被格式化成多行。

## 运行环境

- Minecraft `1.21.10`
- Fabric Loader `>=0.17.3`
- Fabric API
- Java `21` 或更高版本
- 客户端模组，仅需要安装在客户端

## 安装方法

1. 构建模组：

```powershell
.\gradlew.bat build
```

2. 构建完成后，在以下目录找到 jar：

```text
build/libs/
```

3. 将生成的模组 jar 放入客户端的 `mods` 文件夹。

4. 确保客户端同时安装 Fabric API。

## JSON 文件格式

Warehouse Selector 生成的文件格式与 Carpet LMS Addition 的 `checkStorageList` 格式一致：

```json
{
  "overworld": [
    [188,49,8],
    [188,49,9]
  ],
  "end": [],
  "nether": []
}
```

字段含义：

- **`overworld`**：主世界容器坐标列表。
- **`end`**：末地容器坐标列表。
- **`nether`**：下界容器坐标列表。
- **坐标项**：每个容器坐标为 `[x,y,z]`。

维度映射：

| Minecraft 维度 ID | JSON 字段 |
| --- | --- |
| `minecraft:overworld` | `overworld` |
| `minecraft:the_end` | `end` |
| `minecraft:the_nether` | `nether` |

## 与 Carpet LMS Addition 配合使用

假设你的世界目录中有：

```text
world/carpetlmsaddition/checkStorageConfig.json
```

并且其中配置了：

```json
{
  "storageList": ["main.json"]
}
```

那么你应该让 Warehouse Selector 写入：

```text
world/carpetlmsaddition/checkStorageList/main.json
```

进入游戏后执行：

```text
/wh file <你的世界目录>/carpetlmsaddition/checkStorageList/main.json
```

之后即可使用金斧头选择区域，并执行 `/wh add` 写入容器坐标。

## 基本使用流程

1. **指定 JSON 文件**

```text
/wh file <path>
```

示例：

```text
/wh file C:\Minecraft\saves\world\carpetlmsaddition\checkStorageList\main.json
```

2. **使用金斧头选择区域**

- 左键方块：设置 `pos1`
- 右键方块：设置 `pos2`

3. **查看当前选区**

```text
/wh sel
```

4. **把选区内容器写入 JSON**

```text
/wh add
```

5. **从 JSON 中移除选区内坐标**

```text
/wh rm
```

6. **重新加载 JSON 并显示高亮**

```text
/wh reload
/wh show
```

7. **在 Carpet LMS Addition 中更新仓储数据**

根据 Carpet LMS Addition 文档，相关功能包括：

```text
/checkStorage updateData
```

是否可用取决于服务器端 Carpet 规则，例如 `commandCheckStorageData`。

## 命令列表

| 命令 | 说明 |
| --- | --- |
| `/wh help` | 显示帮助信息 |
| `/wh file <path>` | 设置要读写的容器清单 JSON 文件 |
| `/wh file` | 查看当前 JSON 文件路径 |
| `/wh sel` | 查看当前选区 |
| `/wh clear` | 清空当前选区 |
| `/wh add` | 扫描选区内的容器并加入 JSON |
| `/wh rm` | 从 JSON 中移除选区内的坐标 |
| `/wh count` | 统计当前 JSON 中的容器数量 |
| `/wh reload` | 从 JSON 文件重新加载容器列表 |
| `/wh show` | 开启容器高亮 |
| `/wh hide` | 关闭容器高亮 |
| `/wh range <blocks>` | 设置高亮渲染距离，范围 `8` 到 `512` |
| `/wh debug` | 显示调试信息 |

## 会被识别的容器

当前会扫描并记录以下类型的方块：

- 箱子
- 陷阱箱
- 木桶
- 各色潜影盒
- 漏斗
- 发射器
- 投掷器
- 酿造台

## 注意事项

- 这是客户端辅助模组，不会直接修改服务器端配置。
- 你需要确保 `/wh file` 指向 Carpet LMS Addition 实际读取的 `checkStorageList` 文件。
- 如果 JSON 文件不存在，模组会在写入时创建它。
- 如果 JSON 文件已有内容，模组会尽量保留并按固定格式重新输出。
- 坐标会根据玩家当前所在维度写入对应字段。
- 高亮只显示当前维度的容器坐标。

## 参考资料

- [Carpet LMS Addition 文档](https://carpet.lms.nm.cn/docs/)
- [Carpet LMS Addition 配置说明](https://carpet.lms.nm.cn/docs/config)
- [Carpet LMS Addition GitHub](https://github.com/Citrus-Union/Carpet-LMS-Addition)

## 许可证

GPL 3.0
