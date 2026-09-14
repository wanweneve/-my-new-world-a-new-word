# 第三方声明

本项目（MIT，见 LICENSE）的**实现思路与协议细节**参考了以下开源项目。协议细节（端点、签名算法、返回字段）属于接口事实，不构成作品本身；凡直接借鉴的代码片段均在对应源文件头注释中标注并保留原始版权声明。

| 项目 | 用途 | 许可 |
| --- | --- | --- |
| [ProbiusOfficial/Skland_API](https://github.com/ProbiusOfficial/Skland_API) | 森空岛接口文档、字段解读、凭证链说明 | MIT |
| [AEtherside/rhodes-headquarters](https://github.com/AEtherside/rhodes-headquarters) | 森空岛请求签名、体力/回满数据读取与展示逻辑参考 | MIT |
| [AEtherside/skland-daily-attendance](https://github.com/AEtherside/skland-daily-attendance) | 登录/凭证刷新流程参考 | 无 LICENSE（仅参考，未复制代码） |
| [FrostN0v0/nonebot-plugin-skland](https://github.com/FrostN0v0/nonebot-plugin-skland) | 方舟角色卡数据结构、凭证获取文档链接 | MIT |
| [AEtherside/skland-kit](https://www.npmjs.com/package/skland-kit)（author: enpitsulin） | **协议移植的直接依据**：`DeviceProfile.java`（数美 dId）与 `SklandClient.java`（凭证链/签名/体力查询）逐字符照译自其 0.3.5 版 `dist/index.js` | MIT（已存档于 `reference/skland-kit-0.3.5/`） |

如你发现遗漏或署名有误，欢迎提交 Issue/PR 更正。
