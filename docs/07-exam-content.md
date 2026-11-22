# 备考内容与题库规格

## 目标用户与路径

面向 15–28 岁、主要使用中文的英语学习者。首版提供 CET-4、CET-6、IELTS Academic、TOEFL iBT 四条备考路径和一条通用基础路径。首页优先给出考试目标和「快速测词汇」「直接练习」。用户可切换目标并回到原路径继续。内容以短课节、清晰讲解和完整英文输出为主，适配手机碎片练习与桌面键盘。年龄仅用于内容与体验设计，选题依据用户选择的目标和学习表现。

| 路径及代码 | 首版重点 | 与官方任务的关系 |
| --- | --- | --- |
| 四级 `CET4` | 校园、社会生活和常见中国文化表达。练基本句型、因果、比较、常见搭配与时态。 | 中国教育考试网列出短文写作和段落汉译英。首版练习对应其中的单句表达能力。[CET 笔试](https://cet.neea.edu.cn/html1/folder/16113/1586-1.htm) |
| 六级 `CET6` | 社会现象、文化和科技。练让步、对照、抽象名词搭配、非谓语及复合句。 | 六级也包含短文写作与段落汉译英。使用不同的语料与句法难度梯度。[CET 笔试](https://cet.neea.edu.cn/html1/folder/16113/1586-1.htm) |
| 雅思学术类 `IELTS_ACADEMIC` | 趋势变化、数据比较、观点、理由与让步。讲解常见正式表达和英美拼写变体。 | 学术类写作以图表或过程描述、议论文为内容参照。首版练句子，培训类阅读与写作路径留待扩展。[Academic 写作](https://ielts.org/take-a-test/test-types/ielts-academic-test/ielts-academic-format-writing) · [雅思考试类型](https://ielts.org/take-a-test/test-types) |
| 托福 `TOEFL_IBT` | 校园沟通、邮件中的请求与解释、学术讨论中的主张和举例。重点练词序和表达用途。 | ETS 写作任务包括 Build a Sentence、写邮件和学术讨论。首版用中译英练这些场景的句子。[ETS 写作任务](https://www.ets.org/toefl/test-takers/ibt/about/content/writing.html) |
| 通用基础 `GENERAL` | 日常、校园、时间和地点、问询、基本句法。 | 用户可以直接选择，也用于给各备考路径补基础。 |

产品展示为备考专项练习。词汇检测和整句练习使用各自的产品指标，描述可采取的学习行动。整套模考、段落写作评分及官方分数等值属于独立产品能力。

## 题目标签和难度

公开课节与定制题库共用 `sentence_version`。每个版本采用以下结构化元数据，发布后随版本固定。

| 字段 | 约束和用途 |
| --- | --- |
| `examTracks` | 至少有一个适用路径。仅确实可迁移的基础题标记 `GENERAL`。目标路径的专属题和通用补位题分别计数。 |
| `difficulty` | 整句难度 `D1–D4`。综合词汇、句法与翻译歧义，由编辑按下表评定。 |
| `topicCode, primarySkill, skillCodes` | 题目主题、一个主要训练目标和可选次要技能。主题示例 `CAMPUS`、`CULTURE`、`DATA_TRENDS`、`ACADEMIC_DISCUSSION`。技能示例 `COLLOCATION`、`COMPARISON`、`CONCESSION`。 |
| `targetLexemes, minVocabBand` | 要练的词条或搭配，以及建议的识词起点 `V1–V4`。两者由编辑维护。词汇难度与整句难度分别用于筛选。 |
| `sourceType, sourceRef, licenseStatus` | 记录原创或外部授权、证据及使用范围。正式发布前校验。 |

| 难度 | 编辑参考 | 原创示例 |
| --- | --- | --- |
| D1 | 常用词、单一分句、核心句法 | 请把申请表发给我。 → `Please send me the application form.` |
| D2 | 一个重点搭配、比较或简单连接 | 和去年相比，今年乘公交的学生更多。 → `Compared with last year, more students take the bus this year.` |
| D3 | 较正式表达和多分句，要求准确转述关系 | 尽管预约人数有所增加，实验室的平均等待时间却缩短了。 → `Although bookings increased, the average wait at the lab fell.` |
| D4 | 更密集的信息关系、抽象或学术搭配、明确的语域约束 | 这项调查揭示了用电量与气温之间的关联，但无法证明因果关系。 → `The survey revealed an association between electricity use and temperature, but it could not establish causation.` |

中文提示应提供足够的时态、指代与语域线索。若自然译法会改变目标搭配，可在题干标记「请用给定表达」，否则补录合理变体。每题复核英美拼写、缩写、同义表达与讲解，发布前试答。IELTS 和 TOEFL 路径可以共用适当的训练素材，标签应反映真实场景。

## 内容交付与质量门槛

- 原有少量种子用作开发验证。首发内容基线为每个考试路径在每个 D 档至少 12 道专属或共同署名的原创适配题，并在每个 D 档准备至少 8 道只归通用路径的补位题。按覆盖槽位计为 192 道考试路径题和 32 道通用题，跨路径复用会改变实际独立句数。每个考试路径至少覆盖三个主题、三个主要技能。
- 每条考试路径至少发布三个可直接打开的短课节，每节建议 5–10 题。课节题目可用于定制练习，因为两者引用同一题库版本。词汇量测试另有出题库，避免练习过程中原样看到测评题干和正确选项。
- 运营按 `examTrack × difficulty × primarySkill` 查看可用数和授权状态。发布前从四个考试路径、四档用户和通用入口分别演练出题。定制批次缺题时遵循定制模块的回退顺序。
- 优先开发四级示例贯通页面、标签、讲解和选题，再按相同契约扩充六级、雅思学术类和托福。所有路径达到上述内容基线后开放完整首发入口。
