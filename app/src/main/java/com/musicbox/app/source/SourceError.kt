package com.musicbox.app.source

/** 音源统一失败原因。绝不把底层异常直接抛给 UI。 */
sealed class SourceError(message: String? = null, cause: Throwable? = null) :
    Exception(message, cause) {

    /** 网络/连接层失败。 */
    class Network(cause: Throwable? = null) : SourceError("网络不可用", cause)

    /** 接口返回了非成功 code。 */
    class Api(val code: Int, message: String?) : SourceError(message ?: "接口返回 $code")

    /** 响应解析失败（结构变化或脏数据）。 */
    class Parse(message: String?, cause: Throwable? = null) : SourceError(message, cause)

    /** 当前里程碑尚未实现的接口。 */
    class NotImplementedYet(val milestone: String) :
        SourceError("该能力计划在 $milestone 实现")
}
