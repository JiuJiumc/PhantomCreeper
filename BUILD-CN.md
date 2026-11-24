# PhantomCreeper 幻翼苦力怕：本地性能修复工程

版本：1.0.5-jiujiu.3（测试候选：保留飞行修复，新增火把联动）。成品文件名沿用 PhantomCreeper-1.0.5.jar，内部 plugin.yml 版本区分修订。

源码范围：che11111/PhantomCreeper main 提交 51c3095758af7274e4ba5850a64f2b0d6173ddb4 的完整插件 Java 源码、构建所需资源、Maven Wrapper、测试及本次本地修订。此包为作者源码基础上的修改版，不是未修改作者原版。未包含本插件成品、服务器、日志或其他插件源码；compile-deps/StarryHuanYi-api.jar仅包含公开接口、仅供编译，不是可安装插件；其余依赖由Maven下载。

环境：Java 25；保留原项目Maven构建体系，不迁移Gradle。仓库POM仍以Paper 1.21.4 API编译，本次另外用本地Folia 26.2.build.7-beta API及Adventure 5.2.0成功做过全量Java源码编译核对；这不等于26.2实服/客户端验收通过。

Windows构建：在工程目录打开命令行运行 `mvnw.cmd -B -ntp clean package`。
Linux构建：`sh mvnw -B -ntp clean package`。
输出：`target/PhantomCreeper-1.0.5.jar`。首次构建需联网下载Maven、Paper、BetterModel、ModelEngine等POM声明的构建依赖。Mockito和JUnit仅用于测试，不打入成品。

修改范围：Folia任务取消；实体外部移除与退役名额清理；玩家所属线程位置快照；无目标搜敌节流；避让节流及坐标快照；速度移动替代逐tick传送；生成命令目标线程调度；ModelEngine模型创建失败的清理。

配置：新增 targeting.search-interval-ticks（默认10）和 movement.separation-interval-ticks（默认5），均限制1～100 tick；已有配置按缺失字段补充，不重置既有业务设置。玩家位置在有生物时每5tick发布一次，过期5秒即失效；短时目标响应与原先逐tick读取有所区别。默认关闭的LOS若开启，Folia跨区域目标暂不通过LOS，回到同一区域后再检测。

移动改为正常实体速度，会受方块碰撞和原版移动流程影响；需要实际模型引擎、目标核心验证飞行速度、转向、载体与模型同步。安装/卸载使用完整重启；不支持Folia插件管理器热卸载。/phantomcreeper reload仅重载配置，可以使用。

2026-11-24修订：从既有1.0.5-jiujiu.1工作副本增量修复，保留全部先前修改。生成载体改为AI=true、Aware=false、Gravity=false，使26.2执行速度位移积分但不运行原版自主AI；每tick清零原版引信，插件自有引信不变。info速度改为连续真实坐标位移（格/tick）。没有逐tick传送，没有修改BetterModel、字节码或模型资源。

本机实际验证：Java25、mizuki/Shiroha 26.2-DEV f10fd16；无模型100tick对照NoAI移动0格、AI=true/Aware=false移动25格；配BetterModel3.6.0实际插件巡航100tick移动22.966238427格，模型加载/命中箱1/主动点燃后引信清零/移除后计数0。31项JUnit通过，另以本地Folia26.2 API全量编译通过。本机核心具体构建不等于用户VPS；真人客户端、跨Region迁移、追击/自有引信战斗和方块碰撞场景仍需目标环境验收。

现有服务器config.yml原地保留，本次不提供默认主配置覆盖文件。用户现有15生命、巡航0.4、追击0.5、最大0.68、world及32格幻翼阈值1→1和6→4不得用包内默认配置覆盖；本次内置默认配置与上一版保持逐字节一致，配置迁移/读取回归覆盖上述用户值。

所属线：本地性能修复测试候选；未经实际部署核对，不代表正式服版本。未自动推送或创建PR。

本次联动修订：以StarryHuanYi公开的UUID快照接口过滤TargetSelector.find/resolve；受保护目标触发原有追击/引信取消流程，取消保护后可重新搜敌。没有新增任何苦力怕伤害取消处理，爆炸波及伤害保留。服务端应同时更新StarryHuanyi-1.0.0.jar与PhantomCreeper-1.0.5.jar并完整重启；无需改模型或资源包。配置文件与上一版逐字节相同，不覆盖现有配置。

接口编译依赖：compile-deps/StarryHuanYi-api.jar由配套火把插件正常源码编译的apiJar任务生成，仅含cn.starryhuanyi.api接口；Maven system scope不打入本成品，运行时由StarryHuanYi提供。缺少火把插件或接口版本过旧时，PhantomCreeper仍可运行，旧接口会给出明确警告并不启用联动。

本次本地验证：34项JUnit零失败、零错误、零跳过；火把工程2项JUnit通过（含64种开关/装备组合）。隔离Shiroha26.2、Java25、BetterModel3.6.0实测两个插件成功加载并连接API，100tick巡航累计真实位移22.817694187格；不装火把插件也成功加载，100tick移动22.693545043格。引信取消/恢复、目标过滤及插件生命周期为Mock单元测试；真人持火把、多人爆炸波及、跨区域战斗尚未实服/客户端验证。隔离服系统性能计数器有环境错误，不是本插件加载异常。