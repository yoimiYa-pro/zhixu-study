# 今日学习首页改版

视觉主张：以出版物的中文排版和真实纸张的触感，做一张安静、清晰、有辨识度的公考学习工作台。

第一阶段只改今日学习首页及该页的导航外壳。后续已按用户确认将视觉系统延展到全站，并按补充要求改为日期开场；最新规则与核验见 [全站设计记录](frontend-design.md)。沿用 Vue 3、TypeScript、现有登录机制与 API。首页数字、任务、趋势、资料全部读取真实接口，不为展示补造数据。空状态保留实际含义。

## 设计系统

| 角色 | 色值 | 用途 |
| --- | --- | --- |
| 画布 | `#ffffff` | 主工作区 |
| 导航 | `#edf5fb` | 侧边栏 |
| 主文字 | `#203449` | 标题、正文、数字 |
| 辅助文字 | `#64748b` | 时间、说明、目标 |
| 操作 | `#bf4f2b` | 主按钮、完成标记、当前导航指示 |
| 结构线 | `#e3eaf0` | 清单和内容区分隔 |
| 复习背景 | `#dcecf7` | 纸张插画底色 |

中文宋体用于品牌、首页标题和区块标题，中文无衬线用于导航、表单和正文。数字使用宋体中的等宽数字。首页标题 45px / 700、区块标题 23px / 600、导航和操作 14px、正文 14px。桌面导航宽 212px，内容横向留白 36px；页面在窄桌面和手机按内容顺序收拢。

结构：日期和记录操作 → 开放式数字栏 → 到期复习与纸山书本插画 → 今日任务 / 最近七天 → 今日时政 / 需要巩固 / 今日词语 → 申论素材。除复习入口外，不用容器框住每一块内容。

插画是独立位图，文本和按钮全部由 Vue 渲染；不为插画增加色彩覆盖层。图标沿用项目 AppIcon，统一线宽。该处与概念图的个别图标形状有意不同，以保持现有产品导航语义。

动效：首次进入时一次整体显现；任务完成时圆形勾选反馈；按钮与原文链接的轻微状态变化；桌面导航固定、内容滚动。系统设置减少动态效果时取消非必要动画。

## 实现与数据约束

- `useDashboard` 管理读取、任务更新与学习记录提交，处理加载、错误和并发点击。
- 首页按区块拆分复习入口、任务清单、学习趋势和资料区域。
- 原有记录学习弹窗保持原生 dialog 的焦点管理与 Escape 行为。
- 真实任务按练习、复习、阅读排列；目标为零时明确显示，无需伪造待复习数量。
- 概念图中的示例数字与新闻在实现中以实时接口内容替换。连续学习天数、任务目标、进度数据和原文链接是现有功能保留的必要文案。
- 深色主题使用对应语义色；书本插画作为独立原色作品呈现。

## 素材与来源

- 视觉概念：`docs/design/zhixu-overview-concept.png`，1448 × 1086。
- 独立插画：`frontend/public/images/study-paper-mountains.webp`，2170 × 725，约 55 KiB。使用内置 imagegen，以概念图为参考生成；文本与控件没有烘焙进图片。
- 字体：Noto Sans SC 与 Noto Serif SC 官方可变字体，本机转为 WOFF2 子集。界面中文覆盖 GB2312，标题另用小子集；动态词语标题按需加载中文宋体。没有远程字体请求。许可位于 `frontend/public/fonts/OFL.txt`。
- 插画仅在左右/上缘使用透明遮罩衔接背景，没有色彩覆盖层。手机保持书页山峰为焦点，部分书本边缘按版式裁切。

插画的最终生成 prompt（内置 imagegen，参考概念图）：

> Use case: background-extraction / product-mockup. The reference image is a web interface concept. Create a NEW standalone production illustration asset faithfully reproducing ONLY the powder-blue book artwork used in the wide review strip. Output the illustration only, absolutely no web UI, no buttons, labels, numbers, borders, sidebar, or app text. A wide panoramic image around 3:1 aspect ratio. Composition: the left 43% is empty soft powder blue background #dcecf7 with subtle natural lighting only, a safe area for code-native text; the RIGHT 57% has the beautiful realistic pale blue open book from the concept with pages folded into angular paper mountains rising from its middle, a burnt-orange satin bookmark curved over the spine, a single burnt-orange pencil lying diagonally in front, and two pale-blue closed books stacked behind at the far right. All objects grounded naturally on the matching powder-blue tabletop, subtle paper fibers and page edges, photographic studio light from upper left, delicate real shadows, pale blue atmospheric backdrop. Match the exact art direction, arrangement, scale, camera angle, blue palette, matte paper material and orange accents of the reference review strip. This is sophisticated tactile academic editorial still life. Fully rendered production art; no cartoon, no flat vector, no abstract sphere, no watermark, no graphic panels, no visible typography or readable lettering on any page. Keep all important objects fully visible with a generous 7% margin at right and bottom. The asset must blend as one continuous blue image with the UI, no colored overlay will be added.

## 视觉核对

使用 `view_image` 同时检查原始概念与最新浏览器截图，并额外检查隔离的有数据状态。主视口按概念原生 1448 × 1086 验证；另检查 1191 × 668、980 × 800、800 × 900、390 × 844、320 × 760。

| 比较点 | 概念与实现 | 修正或有意差异 |
| --- | --- | --- |
| 构图 | 冷蓝导航、白色工作区、四列数字、横向复习区、开放任务/图表与三列资料 | 收紧首屏留白，保持下一区块进入首屏 |
| 中文排版 | 品牌与标题宋体、导航与控件无衬线 | 提高首页标题和数字字重；自托管字体避免环境字体不一致 |
| 色彩 | 墨蓝文字、粉蓝插画、橙色操作 | 保留白色画布；将小字、链接与主按钮调整到至少 4.5:1 的对比度，这是可访问性所需的有意色值微调；单独校准深色图表坐标和提示框 |
| 插画 | 书页山峰、橙色书签与铅笔 | 修正桌面裁切以保留书本；手机使用上缘透明遮罩消除硬接缝 |
| 容器与间距 | 无嵌套卡片，普通任务行与开放资料栏目 | 去掉冗余任务说明，校准图表高度和区块节奏 |
| 内容 | 同一组导航、记录/录题操作与首页栏目 | 数字、新闻、资料由实际接口替换概念示例；零到期复习使用“查看复习安排” |
| 既有信息保留 | 任务目标、连续学习、原文链接、错题趋势和趋势数据表 | 是现有功能的必要保留；不同于概念只绘制的练习/复习两组数据 |
| 图标 | 统一描边、同一尺寸体系 | 沿用 AppIcon 中对应产品语义的图标，不复制图像里的近似字形 |
| 响应式 | 桌面侧栏固定，手机顺序堆叠、导航横向滚动 | 修正图表 resize 的瞬时溢出与 320px 导航边距 |

首屏文案核对：没有增加营销标题、英文标签、装饰徽章或虚构指标。对概念的差异限于实时数据、空状态和上述现有功能保留；桌面和手机均完成视觉核对，没有遗留的文字遮挡、素材丢失或横向溢出。

## 验证

Browser 插件未提供，本次使用项目已安装的 Playwright / Chromium；临时检查脚本与中间截图位于 `/tmp`，没有加入源码。

| 项目 | 结果 |
| --- | --- |
| 页面身份与内容 | `/`，标题“知序 · 公考学习”，首页与三个任务正确渲染 |
| 错误覆盖层 | 无 Vite 或 Vue 错误覆盖层 |
| 控制台与资源 | 无非预期错误；字体和插画加载成功 |
| 桌面/手机 | 六个尺寸均无横向溢出；桌面与 320px 手机深色主题通过 |
| 记录学习 | 打开弹窗、Escape 关闭与焦点恢复；填写 10 道 / 2 道错题 / 15 分钟，提交 payload 为 900 秒并显示保存状态 |
| 任务勾选 | 隔离 PATCH 响应后，aria-pressed、圆形勾选和完成数量更新；再次点击恢复 |
| 路由 | 录入错题进入现有表单；离开首页后撤去首页专属外壳样式 |
| 失败重试 | 隔离 503 响应后出现可操作错误提示；重试恢复真实首页 |
| 有数据状态 | 隔离 fixture 检查 12 道复习入口、实际趋势数据表、60% 知识点与动态宋体词语标题 |
| 构建与单测 | Vue / TypeScript 与 Vite 构建通过；现有 5 项单元测试通过 |

交互写入测试均由浏览器拦截为隔离响应，没有写入用户数据库。真实首页使用现有本地后端；线上容器未更新。本次只验证 Chromium，没有检查其他浏览器。构建保留既有 ECharts 分包超过 500 KiB 的提示。
