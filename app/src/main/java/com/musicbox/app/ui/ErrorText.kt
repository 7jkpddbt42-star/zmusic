package com.musicbox.app.ui

import com.musicbox.app.source.SourceError

/** 音源错误 → 用户可见文案。所有页面共用一份，避免文案各写一套。 */
internal fun Throwable.toUserMessage(): String = when (this) {
    is SourceError.Network -> "网络不可用，请检查网络"
    is SourceError.Api -> "音源暂时不可用，请稍后重试"
    is SourceError.Parse -> "解析失败，可能接口已变更"
    is SourceError.Unplayable -> "这首歌暂无版权或需要登录"
    is SourceError.NotImplementedYet -> "该功能尚未实现"
    else -> "出错了"
}
