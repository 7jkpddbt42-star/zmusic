package com.musicbox.app.source.netease

import org.junit.Assert.assertEquals
import org.junit.Test

class NetEaseCryptoTest {

    @Test
    fun `md5 returns lowercase hex`() {
        assertEquals(
            "900150983cd24fb0d6963f7d28e17f72",
            NetEaseCrypto.md5Hex("abc"),
        )
    }

    @Test
    fun `aes128EcbPkcs7 returns uppercase hex`() {
        assertEquals(
            "3329CABA8FB561FF0086A5823D14F529",
            NetEaseCrypto.aes128EcbPkcs7Hex(NetEaseCrypto.EAPI_KEY, "hello"),
        )
    }

    @Test
    fun `eapiParams matches known vector (ascii)`() {
        val text = """{"s":"hello","type":1,"limit":30,"offset":0,"total":true,"e_r":false,"header":{}}"""
        assertEquals(
            "2B5D64177AA6460FBAA3DCB1285E28954BBB4F7556E09B0FB25750F12398BB50" +
                "FCCCA3BBD139F9F4CFD33CF4D5C3FD2E6B6A421FBF3C9F27628217E612454733" +
                "266459E5F9F3C9B60A2B275D0A17AB340EA7C9AC352BE2430014FF1A9D81964D" +
                "4BFD64C4A08E9C0CD130F2B72E3AAB1E2BA5716B5036AD446E7BCF5FDE0B92F6" +
                "722B4BB7C225A22A4A59F729599859FC76B0218F40FA6C7143E91FF69E404D94",
            NetEaseCrypto.eapiParams("/api/cloudsearch/pc", text),
        )
    }

    @Test
    fun `eapiParams handles utf8 (chinese) correctly`() {
        val text = """{"s":"海阔天空","type":1,"limit":30,"offset":0,"total":true,"e_r":false,"header":{}}"""
        assertEquals(
            "2B5D64177AA6460FBAA3DCB1285E28954BBB4F7556E09B0FB25750F12398BB50" +
                "327BB1B330EEC8D8681261926D2FE69CB676D727589A690810B7C3D0C2835446" +
                "D753A81DA5F0FA39074199F024F0F2E407C432A9B3ECF4ECA5FBFBD501E02684" +
                "2A0BE5EE12C5ACDB01AECD5781C70154D6515A806FBA5C2B47F32E7D47D12864" +
                "3557228FF76C8FFC7CCDA7AE5778AEEF4BE1613C84A7AD720DCC33222A157581" +
                "421CB711F5002053AA0D3F07EB593C9B",
            NetEaseCrypto.eapiParams("/api/cloudsearch/pc", text),
        )
    }
}
