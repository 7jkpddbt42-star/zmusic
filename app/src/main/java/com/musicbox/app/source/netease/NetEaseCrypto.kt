package com.musicbox.app.source.netease

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 网易云 eapi 签名。
 *
 * 事实依据：Folia 所打包的 neteasecloudmusicapienhanced/api 中 encrypt.eapi：
 *   text    = JSON.stringify(data)
 *   digest  = MD5("nobody" + uri + "use" + text + "md5forencrypt")      // 小写 hex
 *   payload = uri + "-36cd479b6b5-" + text + "-36cd479b6b5-" + digest
 *   params  = AES-128-ECB/PKCS7(payload, "e82ckenh8dichen8") 的大写 hex
 */
object NetEaseCrypto {

    /** eapi 的 AES-128 密钥。 */
    const val EAPI_KEY = "e82ckenh8dichen8"

    /** payload 中 URL 与 JSON 文本之间的固定分隔符。 */
    const val EAPI_SEP = "-36cd479b6b5-"

    private const val MD5_SUFFIX = "md5forencrypt"
    private const val MESSAGE_PREFIX = "nobody"

    fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.toHexLower()
    }

    /** AES-128-ECB + PKCS7，输出大写十六进制。 */
    fun aes128EcbPkcs7Hex(key: String, plain: String): String {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        require(keyBytes.size == 16) { "eapi key must be 16 bytes, got ${keyBytes.size}" }
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"))
        return cipher.doFinal(plain.toByteArray(Charsets.UTF_8)).toHexUpper()
    }

    /** 生成请求体里的 params（大写 hex）。 */
    fun eapiParams(uri: String, jsonText: String): String {
        val digest = md5Hex(MESSAGE_PREFIX + uri + "use" + jsonText + MD5_SUFFIX)
        val payload = uri + EAPI_SEP + jsonText + EAPI_SEP + digest
        return aes128EcbPkcs7Hex(EAPI_KEY, payload)
    }

    private fun ByteArray.toHexUpper(): String =
        joinToString("") { "%02x".format(it.toInt() and 0xFF) }.uppercase()

    private fun ByteArray.toHexLower(): String =
        joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}
