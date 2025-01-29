package com.cyxbs.components.config.serializable

/**
 * 用于在 commonMain 模块中防止数据类字段被混淆，主要目的是为了兼容安卓旧代码，让 Gson 能正常进行序列化
 *
 * @author 985892345
 * @date 2025/1/29
 */
interface GsonDataBean {
}