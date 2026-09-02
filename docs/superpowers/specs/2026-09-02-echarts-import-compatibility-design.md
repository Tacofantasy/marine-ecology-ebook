# ECharts 导入兼容性修复设计

## 目标

恢复 `StatsPanel.vue` 的统计趋势图在 Vite 中的模块解析与生产构建能力，且维持现有按需注册的折线图、网格、提示框、图例、标题、工具栏和 Canvas 渲染器。

## 原因与范围

统计面板使用 ECharts 5 的按需导入路径（包括 `echarts/components`）。合并提交将依赖升级到 ECharts 6，导致 Vite 根据该版本的包导出表解析时拒绝该路径；本修复不改图表逻辑、样式或后端统计接口。

## 方案

将前端 `echarts` 固定为 5.6.0，并由 npm 重新生成 `package-lock.json`。该版本与现有按需导入 API 兼容，修改范围最小且保持产物的按需加载特性。

## 验证

1. 安装锁文件中声明的依赖。
2. 执行 `npm run build`。
3. 构建成功即表明 Vite 可解析全部 ECharts 子模块，并且 TypeScript 与生产打包均通过。
