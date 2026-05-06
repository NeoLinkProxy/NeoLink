# NeoLink Android ProGuard Rules

# 保留 NeoLinkAPI 的回调接口和异常类
-keep class top.ceroxe.api.neolink.** { *; }
-keep class top.ceroxe.api.ceroxe.** { *; }

# 保留 common 模块的状态类（可能通过反射或序列化使用）
-keep class neoproxy.neolink.state.** { *; }
-keep class neoproxy.neolink.config.** { *; }
