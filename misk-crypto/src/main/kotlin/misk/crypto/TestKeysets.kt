package misk.crypto

import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.JsonKeysetReader
import com.google.crypto.tink.JsonKeysetWriter
import com.google.crypto.tink.aead.KmsEnvelopeAead
import misk.config.MiskConfig
import java.io.ByteArrayOutputStream

/**
 * These test keys are used by the [FakeKeyResolver] class.
 * *DO NOT USE IN ANY OTHER SITUATION*
 */
@Deprecated("Use misk-crypto-testing instead",
  replaceWith = ReplaceWith("TestKeysets", imports = ["misk.crypto.testing"]))
internal class TestKeysets {
  companion object {
    fun encryptSecret(key: MiskConfig.RealSecret<String>): MiskConfig.RealSecret<String> {
      val kek = KmsEnvelopeAead(KeyReader.KEK_TEMPLATE, FakeMasterEncryptionKey())
      val keyOutput = ByteArrayOutputStream()
      val plaintextKey = CleartextKeysetHandle.read(JsonKeysetReader.withString(key.value))
      plaintextKey.write(JsonKeysetWriter.withOutputStream(keyOutput), kek)
      return MiskConfig.RealSecret(keyOutput.toString())
    }
    /**
     * Created with tikney:
     * `tinkey create-keyset --key-template AES256_GCM`
     */
    val AEAD = MiskConfig.RealSecret(
      """{
    "primaryKeyId": 287541552,
    "key": [{
        "keyData": {
            "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey",
            "keyMaterialType": "SYMMETRIC",
            "value": "GiCQjc0CLkz8Fyn39oheg30dtDGqOVLPmol476EVuWtSPw=="
        },
        "outputPrefixType": "TINK",
        "keyId": 287541552,
        "status": "ENABLED"
    }]
}"""
    )

    /**
     * Created with tikney:
     * `tinkey create-keyset --key-template AES256_SIV`
     */
    val DAEAD = MiskConfig.RealSecret(
      """{
    "primaryKeyId": 1677617234,
    "key": [{
        "keyData": {
            "typeUrl": "type.googleapis.com/google.crypto.tink.AesSivKey",
            "keyMaterialType": "SYMMETRIC",
            "value": "EkBrZ6w6t7hh4gCVw36fYiPJXgJqxoBpHV2/fWhluU9b3pAUBE3i3f3gYXv0im8TacC1L8zbsm9ppJJMiue2f8pF"
        },
        "outputPrefixType": "TINK",
        "keyId": 1677617234,
        "status": "ENABLED"
    }]
}"""
    )

    /**
     * Created with tikney:
     * `tinkey create-keyset --key-template ECDSA_P256`
     */
    val DIGITAL_SIGNATURE = MiskConfig.RealSecret(
      """{
    "primaryKeyId": 1279591183,
    "key": [{
        "keyData": {
            "typeUrl": "type.googleapis.com/google.crypto.tink.EcdsaPrivateKey",
            "keyMaterialType": "ASYMMETRIC_PRIVATE",
            "value": "Ek0SBggDEAIYAhogQKqZ2vtpkUK0SP01B46zkZhLdldFtc4IOPo5g3JXsssiIQD5/kxKXYRAT4o7XD7/ic7ydQ7d3RohaDeNqyQPZehuRRogCoYkMyycYhu3/EIlD4SNSLazZD/y4vNCZGzGkQ6GlQY="
        },
        "outputPrefixType": "TINK",
        "keyId": 1279591183,
        "status": "ENABLED"
    }]
}"""
    )

    /**
     * Created with tikney:
     * `tinkey create-keyset --key-template ECIES_P256_HKDF_HMAC_SHA256_AES128_CTR_HMAC_SHA256`
     */
    val HYBRID = MiskConfig.RealSecret(
      """{
    "primaryKeyId": 1026740700,
    "key": [{
        "keyData": {
            "typeUrl": "type.googleapis.com/google.crypto.tink.EciesAeadHkdfPrivateKey",
            "keyMaterialType": "ASYMMETRIC_PRIVATE",
            "value": "EqMBElwKBAgCEAMSUhJQCjh0eXBlLmdvb2dsZWFwaXMuY29tL2dvb2dsZS5jcnlwdG8udGluay5BZXNDdHJIbWFjQWVhZEtleRISCgYKAggQEBASCAoECAMQEBAgGAEYARohANfrDi8rKtmSFZlvRS6kss61EdTCuR4fm5mYQsqQQaxTIiA66YQj+HxV8wjj7YFD19Rcd+hdTzyCaQNxJBAnxDZS3xohALz7hT5rkQHNT1X5coQvn74CtOV1w+/iRS2TiTCKauoR"
        },
        "outputPrefixType": "TINK",
        "keyId": 1026740700,
        "status": "ENABLED"
    }]
}"""
    )

    /**
     * Created with tikney:
     * `tinkey create-keyset --key-template HMAC_SHA256_256BITTAG`
     */
    val MAC = MiskConfig.RealSecret(
      """{
    "primaryKeyId": 1113231046,
    "key": [{
        "keyData": {
            "typeUrl": "type.googleapis.com/google.crypto.tink.HmacKey",
            "keyMaterialType": "SYMMETRIC",
            "value": "EgQIAxAgGiD7iTus1Y3HeUGgC+IUuOWjugyv1Dtdn+wxZyY0Y/JHjw=="
        },
        "outputPrefixType": "TINK",
        "keyId": 1113231046,
        "status": "ENABLED"
    }]
}"""
    )

    /**
     * Created with tikney:
     * `tinkey create-keyset --key-template AES256_GCM_HKDF_4KB`
     */
    val STREAMING_AEAD = MiskConfig.RealSecret(
      """{
    "primaryKeyId": 1557709997,
    "key": [{
        "keyData": {
            "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmHkdfStreamingKey",
            "keyMaterialType": "SYMMETRIC",
            "value": "EgcIgCAQIBgDGiCOpS4385YjQj0pnWtXjW0nekgs8ztrvn2zfyBS3JiKdQ=="
        },
        "outputPrefixType": "RAW",
        "keyId": 1557709997,
        "status": "ENABLED"
    }]
}"""
    )

    val PGP_DECRYPT = MiskConfig.RealSecret(
      """{
  "region": "",
  "encrypted_private_key": "AAAAMEdpQ0llcHNwN2oyVHYwRDEzU1JDR3RTZ2NwSm9wMVBuSG45K0NNUWtSeGF5YkE9PSOwASx+69NjCuEqxLViUb3+WrALUi9fTPPrvjKTmIOXbNUCWB8RiiLsdPpgYbB7BYFoP9yCBa23A+G4T1NJlCxSPpRsyNAIXVSY4NDBfTQo73aIANTj9L+Fz1z5QfZNyDixzDdHel+8TrMt8WJXM3hC+c/CknSWi29Q6T72SmyQb0J4XPvu/Xb5/9cwcmsH1JBtlkCXF3yNT/u1txsDrFgWBkFGVN8lBqQwsx2G8o7s/R5GshDJKgyPkV1bZnzAm883e48w8XAVkckbhzP3/cFiSLXUFqMO+CGvJrGpWxS0p+YsckP4u3Mt9alp/mk0MawzZGVnV92jjqUgqVQ7L44s8sgu8Vpu561iAPjHwBAbYV+2KzQhu6gv7yjioC3YYen5NCIM6lAcr8hdF8F9OnUFBAmwZgaT7mtii8KxZthdpSKItXhysnhIVwXAj/1o8PWr6pottNLKN7C73TVINUkx5MPChooMuTPQpSANLWxJp5k53Z05bdRCbFiiiAVC5BJXmGgUgJW+f2/3cw+h7PqSBcVFQ3viMBz+Uo+EhDppE29LJRQvv3yqEOFGIIDxxieY9gRzwzdqe4mv9AReq2l3ThkHBsJBMnjenoeLN3oWYg/OaNc5zVpKrRzFl08gp6eQ2DYIVRAWOLKn+7O3eiOOt+vHV4+yKftADGcIM+wexiNvXvq1GvP3x3O9N0qliMIeMN37iDLjQteBJJvZsKuxe8GXn4KZxezb7otKDbgVL7dAycuSShC2j7BZ/BEjB1z41Gv9SQKscKRMaeLuxSOwWvywLy2pqYHYFfeWvHOxQujm44bPWVRIL/0tsHGrTxPbVWNpKC0S81hF88kUGg/w3zb41TGHDisa/BpWtRUT/VkxflOnblIvMyjVYqm32eVgsY6C/iIAMA9nX0rOkLEdGtPRXQiyZibZ3RRaCCY4vaBgZhMgCQiH4RuVfPLyQDcvv1SDyObkfmGaAvF5y76LA3d184E/+fIwdeWwAFGC9Vr/2Qce3Y8aNXpQKiUbyGdgDLAfvwYzrwWPp/W1chC9wdrkDE7r7KEHDy3hoFNhxp9X9DBY11eRMILyn1VT+tCKNfRcOGUBkeIo7pORfrFNnUvx/tgNtrTRrFrjYMZZNQ5pGRlUOfbp28YrfVJY2EXZihZ2Lx7iyjl2r2oEyVp8llshI013dS3yEFNNmPL/vIP1Xd6FYmzd86qpD0nrBq7lTp8fYZG3wdXa0U6GkGtTGX/Pn2Zq3K3H3cn2jQIOiu4c53vTPRKmyd2HhdaRrphqcMyL0iIhsTnmraRJ1WbDsMaPuPH2ZwdHo+faNxZbAWlF40TVPynE5hr1wtVV2hZcuZ4ex9MLt7ofykGRtfHePany+Cpymw9WoJRrTcUG9Va/utXLYg3/pZjCEoJuOGEuQaOf4lekLnhbw0THQI3cV9am13VOpQHJsbI+PLSztDWxfph16cDO5qmmHDKs52ll5trOlqHdpQUS2BM0MI/OPUgdewA0gDqJAZKVvHeE9C9aacL8tO61uKxs0peE6CH7a/L0rwR5zhVeIvXxhZfR1/vEQi788IWLJNdtqxU4VH/H0+F593JmW008PEV1ge2EG0ctmczPKSaKgVndzG61g661rdBO38S5yCbytuq5Perd4IP4voGMHAlxw8KthydTZREDmBdvWaXSQiGPGiKKVbeVsU/8lHQmwHX5ietxKoLeQX0nbPs+Ck+0qI8xm1xf4pCPmVdo9RhfI3m1f7sq6zcEMuXkin8SpXE9hL5uc6n0h45cC+afGKnJEcdzZvvCaerLgoc1d82I4GEleyqkAkrvRBrBqZlbuVS7/ag4XVoRbxKwsJ0JJ8nzrea2/dAP+7rpKJ+YDfP/cIMqbO1Q4M4sAFvDHNf2qg9BpHME1QjNTeIdBpVs7KdE2Vgqi+VyARXZ6RAkbn4Z6XGu0ZmyMpN7C5ellSL8t2ZPu3QsMzFhU4pL1vsZ5mn4gVPUo7Bz5z5Tx86ULocD8DNa6jtw1dk80Ts+4cNyaJDg1iGtGoJZfBBrb5O8yfbQdxXH6Y2dEvKsL1ZSbMyWvQIRI8DD3IbaIaOE5449aA//TPRHp/ddnLw5TFAGp8q8yeHG8SKF3ySjxHuAnBVkuW85JdKNQW0r1Gj37ors2NknjPdok10Gehk7QWinEL6f7ViHLzFnGSypjphPPwGx/jqOJslzfZ9qyTPaVPQnFhN2WH1x2sZeFpsz7JxdqdButJrwtrdhQCfZSG4AvRE1IY1hgTgBLPyj2CkiHLd/IXMQgi7fi9c7ZCP7SL6ptXJReP0SFwxpUqM78k0EhpsYNByEkSITYckZxmDXJ/fSY7JyEWhDA1EMsyrb1k4cXS+VBZbswif5Y+LtH98MuXq5ZF0FvUwLzCf7QSk/Lx/HyUfCubXX8mRbywZ2OqYPg9OPsCaGVRYLa85hjjxvaAiTjoV+4UxfLVFHsQ2o1sxFfoGz89mGZhpnm+9UM6yyZQ/7A15N9sE2I8KhyR2S1g9YlqyFiiIyqlqBrxdFeBNNFiLg11CwxBPD1ZrZOJQPkb/PuAus/2/5tMACjbmU21HEfHSgWf1JHKAAtkERUpI8yBnU5GVi4a4OskCSqH0uJyennoPFgc/iOoXV2cf7mZ/RalHgYXHeDKpGijZoVRarDXXZtDyLAdM+QPu6lqGDLnWNuZZ6rAOADeDNk8fyt+861EblbJXPpguidUUlmYn0hUL6qYi2kWTauW7jHIscmuEk9q9Tctpuh8xIZSRE7pYE4okIvBD6wLnWUwH0VofIuw2bsrwXj75Koh3UF2Z/WObiaywTReHTpCOgqOgd7fGxElIMN4/lRscRLYQzl1QUPD/JRHNmhG3UAL8PM3KnX8QQZ9B28Z1L/q69dlaxjb4AHcjwk7UBwnLIjHpvLtXmbwFBj++XBnZnc27YfqJheCKsU21grOkW4KNu8OqIwHTsZnn96oZgNU/KbYmhMc4L+pFXKyCJghQMNKCewdQ5/ahx2XO0ZNZZG3fJDumRabm4uuwXN2qoolbbFzO7vXzvhAhaJF2oZBVcvo3hkCs8KaQ6R7SEYnuYROlANXDUrbBhJbtyETIRUJx2D7zYtbV1HGFjdEVkMGHx6DhDnD/k4u8ZMz7257KuXOGq7o5nS8aAsJdrXZrigJjng3aRKQByutn6PPIKNSNiF5W8cCRNd8stRTCGowPuM+IUSbkdLSv1DibFu2f9H9yTELxMUHRXer8RpwutecfIVVhGUqdMEE+c9PtXs4cP9FoOUKpiqaH4gxW+5XFK8ROF5c8bSlFgwa/yAlbPNdWC5AkZ48vWcnq7AzgnXaWCI0jz8Tn54K7qLJdvdmiHIb9Jta5Fw7Jtkp40Q0tJsOZuaFwvXlfZeY9HPpKsKqeaNwNuywmx18ui1uTd/GdI+CcFg9fhw/sDlluR/FEIkNaummcU+ZErleV9mbdHBlmZcGCYpi8DKGy2FvBAcjj+6XhfTqkFCg6zRIOMpVrnFbe/l5dMAB/Gu+gWLUk5lEhc/kD0GERDFw0PZtbhhrv2+lCWEVIX7BCf67DDiEvkBVhICLRI/j4PdQLAsfo0zflUrZWufeC8QqxvYhxrOqgJqr2Pi/CsUFsvZlJGjYYiwjVcS8zlMLexc6X9Be3t7fca1qHVFFELeudi0jrE6UgT3nd3LfIQWTicP/LHYDF5/oIP+2fxSw8sJSJbvtSzf27TJW79F+G/UwGRG4wPH9K7S71Lx1rg1e2VS1gdJZphKhJo/YVOFkF+8hP9qOG9s/hyTb0OQruOMdbIS3mAeuL9Q+vxTMCzVv6wphBg8RD3IC7nyeOLko4mfSxD2CEgiAhCiRl21hxYWxcJr4EeJnpB6+K4XG+UUcdm7oz8bCHecyydb1yaR6lzIbpFVQEhpKQnkbVtdrfny0voDTbhXYoq7ybg+ugZ+mMX/gmPHBADAC79S04Sz4a31xnIe5PvFqj4bEJB5ea1Pz/BKTHtuN4N1KA7Wnw1YP0VxxVdOfw7yN5UY8jgVqnfSP5PG1nmSoY8rzj9gpf/Nh2FVPZnfMRBxQeXZxJOqA5aYUimFfxf8FWXebRKI3jaVVoMvLUxcDtDNaNjBW6FDch4uFlb7NIX91iQk+NGFeH6zpTxmpQwKNTdR4OT9ChCckis7pKQ1vw/zUT9fgHAPKQFarqQzZgVfSMGFY1ndM61JWN/SueebJn39sINX8TndbDVFskwn42uiN8/ENKJarz5shdbEaDAqLT8eMaqeAni1110gxELCfAa6hMGz0ypSIhKtONoWyypMPqZwlJZOITVP4V+wQzk4IHoAnWfSQphu00p5n3WXWauTUqHF4vbY6Mox409P1Ha0pxg6AIkATMXdLCLcBMzojnaIfzjYobaJPTGFeESyfbsktBA3fOEjQiVAJL5zQARUS5fIAdNMcp6DeL3nSet+Q42SOujF6fPq4VJxoSVo5qE045bIKfZFLdxp5A20j3Vj5DqxFkpiC3SwX6VzWXWAS+m6GsTdR+1qqaq87/sc1/sTqAkoF7uOmLD6SqGm8OGCnYGucvL5udLbf+FrPyda+r77EEtTV+N2rVXwGg+WgueDXl9cyJZvsLtNFtYFukiuVaaiysBVe7nFr4uea4Z4XxpGnbNRRvOuTIioqsIu5nD5MMNO+tEA60PTf72/BaqllREzEP+mCV6CbQBsdUGCY4HAecYaJBVSBmaD5V8HbM2AFo6S17rRk0YDbm2dtkQj0dh3/zswFzKuCTv8t99GDIQ13sfqa16BZDh3Zj03aFnsQHsCIL+CpTdhiZ9DAmK356Uome2ShpGfrDXThRmE9zcC0ar4b4kXMWbiWt1lTwsSFyHXhHLHXGUaSXiSr7oQ8rhGp0gS1rAPmv12+nGQamHuQk+U3EYoxPXhv9zKqMTsSingIPptdbSYVnX8PLH8g4XpcFVRJR02LFrrz9cAocTwDPrs2vzIUA3DSM6VJMW2YTrEibLMCca39nfx9ldMhfTvhYk44v6Q7bgc+C0qeowestkLwrRIqErKpzERGY1jHpbfi+JOtPxpzq/qxXBniVlCFlLd/UajVEzAU5bWpC/DMJF0kqv/qOFOwodloVpiWeAkEPIg4pOpM1SB/ZMHUxCaI8zrMOeC1J+7IUS5wwMQA9eKpV0OnwXKbHLuAk//WTFBv1zmuppdJMhZxirwOtxuuPB/qhP1XzrplMl8Qcm9QqOcxgw4nN71eS5/J8O3gdJ2QR2GqDInNkUi2ZPQcXkVTLRafd2BVceOhTfVyY9GAHiyb6VKkTK7uHZDPtx/tKmHUgJQmRZ+e0LzAStufEcNdqO/YP2iHYa81Kdt8BKBAnlSkuqOS6pzWKlDLvUrDoNjXhifkkwIXLMdCSdSzq7E7P5CmmvfYiLQXyGyV9BeZ5jYFXWcmDWcOzd+GkLdZmhKh2CQ9ocTW7HT8IfwHHh/hi61g9YzzCcG8jfXodsl5llBRT5mLXWfsLlQ4ZFnpHGbdA1IobjINTXDxe6RUc846ArHFQjl9G4ddCC+HRIT5egURcnyKkqSlgqwiHpFcdtBBTBaZrse3BpVFjF/PzqrES2tCu0NxrSvRRO7AtgCCdpX9LjaaKIfS2ILkMpFed1S1gtrXiNjR3bBZrLykssNW9Xh60li1u47804FdnVynY39PluCtSyNG4jy09qjB0RH2C8AEu9hyTnW1tDaUv58B6ngfhVA5tVqZiPhwEP/sFyuxTCDOdiOvbimHiYPhb5JYoUIbf7LHokuqFgD/rb8PQe1abq6he1TF5QusNWr3lUqzWPUWVSbqifG9dPbateQirjjmmNalKzNApcrhBzm1pvtH/7B2hBRiVySYBehpUmZxRPhkf8/mQZNKGQdx8gg/GmuL/Q59JpuAK/3Anot0MNxdY40sqcGZOiCov4YIdrCR6MaxCwZksV0KviCHKcdu8c+zhuUlzCxZLxhViXKBnPxIE7y0r30Wl6VWWz6FREl/YSoU2R23FWlHtAzpBDe1s7cIlQiXLhQkXEH6FjITw/loCSRuS65WjpHQ378fq3pDKcy9jySlYOd8+90EjBGP8sAghBF49zW6DfmOFC6L1VEr1EiYrrAm+sxfN+DEso3mikIN67ZdUh7Cp0oW8gY6BcTFqCJA8IPTJwP1WdS40+dWIpUTaAim6eOO32CjqHI8nX4crraTkpYAy+7LLOwKzfE7gYHiIWKvofx7T1fXE0U5ZID4aN+GcEaZrQm9w8+e4Jx2Q5KRKt2SW8hfsGRDbJRrMRxXU0si2uuasDjdMv8eYenhur87hfH3xofHShPZotv87/n74Oc5CNRCnUtMRs52iY+KwqvoqafLcC7VR7btYe38sSPt8zQHKFWKcw2yMSih7BOBA8lghT/VwhSyo/4qGRXB05Sf8dVzlAmTmPRsjz3izwhvevc5yMOseisX074dEqtEhhShqR5Bij9mccnkWt1zkGltnlqzMSZKpfcVXfrMyt71Qf0NAHo9d7uM7S5BJCuUBa+T9s40qtrjPb3KbTTAq8036Aeu0gtYrz1r743JqeXsld+JdUwDnWcc9IHpr65PP4mvqAxvxIcuJZKvTRJjhegg0PWA9fiVzJc/4i0hM99S2e+vsxtPD16OR5AzESzwC4KKpxhKcNievZzKYqsXl5/TnPXxZjRbKuJN6jR7GEL+R4FW0USN/kAEUeqMtb1e9YVDiMixsGBvK821ALbvtey9NlF2jDtHidKJpiiLPMs0hhM0KHmyQfGm4g7YAbwACQDTSvaTnllRZNX/yMY8AXNY8d97A13kHAT0TieoaFPgFyFCcwCisYhkTqZZjw2ZQs6zS5oMZMpnNK7UTUYMwkT62f/UtNs/Ap/FMlRVA9G9jS4fGz4pv37rn5tEvt02cHYHuHFOknV3JjehPrIj4PMrbAjZN9Wcn6VDJJNec/1+lV1Su4WPrRGEF59xaTNOZgiyhNzRvXXi0YR8Jbeq56aLjnwbHUIzwjMPP4aYGSHc18h2LqHAXft3FOaUhIR0XfrtN+eoBAa7HVAI8gxWhjosBxE8p9B+vQ+LzBMUt/t22p+uMkvBbooJpkG6EzAWLSgbPZNqNSiTKDCILSkp40lPGu64d5sPNpgMBeaaGKtE/iLSoY6fcMdB3Cdjlwiw+Rpnr9UW2ZwS8WURrYvPHjY2w54LAIwbba7uc+fWYA8G++VRh2TBieq9ttBBFwVLPxTTAYR3YOBFV9pEIH1+4S/XOaHyj3j9jfQiMjuI7aJsklssqo54M14Olh2WjqxCkSynnSnV/oo921ryHmU8gYnmb2kVIIw00xdr3JXWow2VgVn2MhLBaHiXEkz8RamnNLv+VThFApdn00AdN6IVunDfB8jQPlOrOoa0TFJlip10XXMVozK1HtcGrSftjACrxUMlkP210AK+4QTwF4xE4e0fZ/TooKUNywI5SfHwbZL3afqB7oqYxsDNKPi0Bt8YomG0meNXHFwQ81m+gt6tZrLex3gR/xp1TNcw8YFTaDW66khn/I8BZzw0vWr0VhLCyV41Js+GdIxY+IN1Zkv+3MX/pIvcl7y+8VZmVPdtka8YqCOJg2CgWdJXYCZUkCqNqQl8yAEqKKOwBMhbcAx0s6Uw9UFt9podZa4GZBa9wdQcEfULISgJ3TS5GoAMdgVh3Ia2vVI29u4/vur3ucN1wQcJo2h3OiIAZDKgOHiH+B/d2r2yt1oPx2qIr2qY73so4FvLMxGOaM0Ep5mLp7TnDEtbGMIP/ig9l0Tbl1NwB0BGkLkK1JRK0+MFnBT3i+3LnWOZCy7xj9fIjleIVMNGFaEUwKi1Je29jyKLvs8SCUYUiJU4dBXTZGTZOzPT5DpuD9nUoc5Bps/OA972FOVBUxxWNCmrayB5SOAVblEjUGpWEaE6hRby8E1AjNPrmvsGoYphU3gHxKMMDFcntvG4MM9TFSf2bYjSZ1jbS7PyeDQTowLgJBM2FuQPMkAv4H5JIStQEGC2G3aG4wy/AVaS66WCjVBgHAVX8ddcm1YTXYAvWG0tIhEjVlO5fvtw5Hdwl+09S5mHwSjkrQPkQsdBR2POP+gk7KDR7I7FC7wRva56Xjpj4uo0P/Zprb2KCIiTOwcA4Fulx+gERz4X1UlODFxWKYpwdHFYKhbBJhCaISieTazh8flCCMLg7wMD79SPA8zJtg5BeBoofYPCPZM1e1faf+D4blKOreOZsXvXhgAzOg6HXW6WkR+Dhn4UJGk5Zd0y6ctfeRpR7zm87f6oIXtfYCje85DgeQoPlr8Ja61hk3SCs4s7HFUWojCytCYNzfDKYUiiw57CXVSvswN2y1QsjR/DAicKhYUkw842McpdzrKheaMaYl6DMWC7yutiZUdoBpUM622NPJUJTLAcacdISInTO/3y1Eed35m2ifFUcRm30VEoz4L4/WJlDLln4bDZC3QJofHekuRVUnY0xqx4BjVX1xMvao1HGwF3U4sg/Ocn8rnNYOho0WRnpWwlTTtnLvX9pNVxfi3SW3ppCeEKHb+Jf2fhCGrxAUH68gLyOXjwlXGGma/MoM5e49altIFFqohMj+mlLKtNQHmrWM80DoGE+EarACd5j+Jx9RLmdNds0j2HxKPawYZet3hgytck93gA6J9xFTc2pxpTuJJJV2dd5AiQhp8oyviit7dYzNQZae5hKmuLf6uxytS73z5YYoc6tRhFpbWO7EaV8T5lo08q3O04WZ6nEHaX48zkmyRLuwfCcnE2/mRJDIIN6ZvaB76TODAImkQ/4223yWJJ++uZxzQcIq",
  "public_key": "LS0tLS1CRUdJTiBQR1AgUFVCTElDIEtFWSBCTE9DSy0tLS0tCgp4c0ZOQkY5dVBUSUJFQUN0MkRzTTFhNS9Ca2NmVFBMZ1VBaWp0M0QxNnNIQit3cUNOTVdHK2VTOGVOS3h6dW05CjZvbXBGb0YrOEFnc2laTjU4QWhMOFdSYkVSNEdjSzNOT1ZHbjBYalNQYWRWOXFHQ1QwYTVkZTdnYkRkSFMyd0gKTmJvWDhRQ3RhQ295NkdEZ05iZFZFV1UxN1JocXBIajljY3AvZEhiclJVVFErdHFTbnBKU2RVV3VRaCtOMzVFcAo0VmtKNi8yTUJWaW5xdmZLTUVtd2grWTVTdlQ5ZXBiQndVN0tvTHhmVHVkT085Mzh0Ykt4aDZuaFVXUWlsYjBFClZvV21OdEZIUWZpYUZ0bWZvRENsN3N2cGMxV01iQStwdFRVSVRnMG5oY3RQWU00N0Zlb1ovZjRWcjlNazhVQnUKamJDUzdDZEkwYlpoQUZnK2lWbmtPdjMxQk9YVFZPdy9aeDUvK0dEZTF2M3VUSkdJcWo2THZkR2xGbG01NTNxdgphLzVmcTlUTC85dUp5U1MzRGY5V2JiTVAvcnRxVGNHRzZGZDlWT2MrRGpHbTJjallWcEp4aGhlVW1wZ1JoNngzCmlLbzRaeXdZTDhZa3E5OGlsUUlyWlhEekNjbVNST2FlaHFWYm9GTVFwTnUxdURlUzRPOGJFQVM1Tm5teWZ1TlIKLytWc1lEbndQOG40S1hYUmY4QWpEK2tSOFZRc2xoNGpTWU84b3RBOW9ybHNSS2YxWkJhaXJGQmVSblB0N29CNQpCbGxSU1hVeFp1NlB5Qy9EVzNzMzlHTldGMEZjd3BZeDdFc1kyZVdGYjc1aHhxRzBZOEZSQ3I2S1J6NUExbHVDCnZUZHhHaEdGSkNVbXVEd3prcWo4cWc1Tk5VemxJTjN6OEdkQmxMVVVaQWJvS282ZTBoOEtFOU1qaFFBUkFRQUIKelM5dGFYTnJMV055ZVhCMGJ5MWxlR0Z0Y0d4bElDaDBaWE4wSUd0bGVTa2dQSFJsYzNSQVpXMWhhV3d1WTI5dApQc0xCWWdRVEFRZ0FGZ1VDWDI0OU1na1FTMHpobFRPa29LUUNHd01DR1FFQUFHWElFQUNhZ1JUQ09OazY0b0FBCk9kTi9UMDg3VXJxV3NGNEcxbDVOZDY4RjZHbFJUR29uMi85YUc4YjNFckV6Tmg1bzdhTEN1MGs4SkU0SU5idXIKc2JKdlVabjNGTzNVZkEveVlhYXVtNFgwaDVpand4emVWU1o0R1BHOTFUQUdlajJvZXh5NUM1Y0xkNC91dkJFaQpMWTdsV1dYTW8rR1haa3QxQlNPT1BDRC9EN0o5UWhIT1VuSFo1cEtxTnVWazdiaTA3Z2xtZjJqSXVPV2RxdGFmCmltNnZuR0o1bXZtdDJMRFdIc3hGVHQ1ajdsbEJKdGxhR1hSeGVkVHlCTENLY1FNN2ZSZGNjZGxvbUEzeDg2VlQKTlBFYk5vTjl0ZHgxNlFZL3NzYnJvaGFBVmd1T2RVR0MwRGxBaVNrdW0zdmg0WG1DcXppOEFUZ3BEaWt2aWZrUwpYMUdzaDc1RUZzL3pQYXBZTDNCaWhBUHg2eVZrY1VEMEV2dS9wRU9xdUhROUhFZEFDTXp1TWw0Yng0VW9uOEVDCjBUQjdhWkRDWE90WlF2UlY0Z2c3dkRTaXpFL0M1aFNLU2FRSFpwK1IzMDZqYmRmN0RJeTlxRFp0ODRPakFIRGEKZUtjSXZKYm1UM0lnUzJDdE9oVGdVWVcrbC9GZjdNSWNXRTlGMG9nUEdZUTlmSG1wS1hsc3VTSm1QRW9ZYmRhRwpZdk92RlhjN0drVU1SekNMeWhUMGR4MXpVY0lLbFBrVWVEVEoybk81UU1kdHVodFUxSGlvRUpESEIvTTRuS0hRClhmUUtoZm1aWC9SbFNMR2k2SkQwMDRWbUR2VURaWTB4alY2UGVrZTdab1RsZ3JLWTNkdHY3L21uQ0N0QjhpWFcKZzRlYU9odmRMQ2JwVVpqT1RDZ1FvVUxrR0NhUUk4N0JUUVJmYmoweUFSQUFzTWszWWVsTkxQOWdEVHhOTXRlbApaU0IxZUwyVTQ3R2pHL2RaQnBEQ3VEayszenp2aFBmcFNnWEcxUXZ5dHZCU2xrMENxeTNkb1o3b1R0RmxiOE9HCjBLdWJ4OVZzYzFzNmNUQWRmOUtPbGZXaUlsRnZLUndaRjlodzhvTkVSK2N1eU1YVXZzUkJoU1ZudTI5eGx4VHgKbG0wbTRHNFh4TDlCM0IxRGcvaGdkcHdqaVE0SFRWdldEZDNiRHhrWlhLOG1VbTVGNkVpY3dyZW1PeDJlcmNmMApwYUZ6RWRpeDUwUTljN3d5Q1FoYUVYSXBvMzhoOGR4Ykh0Q3c1Z0F6Wm9MaWM3Z1RaYnZjL1ArdUJROUFvVmdwCjdLOWVIM1hPclNOQkFQbUp6RWd1NGF5QXlURWRqQm1OL3diM3FnSjhRQzN0ZW93SjVQNjJYc1RmMnk5cUFiMkkKQTN1RVN2RTRGZ1paVmp0RG9OT0YwY3BkSGp4MDBqTEZBcFo0b3pTWXhndnZVMERCZEN0VE1XdmgramFFM0lobQoxVXloRHRwTmhTVkJVMGRxQ2w2N1ZIT1pUbkdrajJRU08rT2JvY2ZITTFaMXdiQytJMkU2akt2YWNYZkxvSmxvCm1tai8xK2VQVitLSUttV1pxMmpsRWFBT3FVemR2UkdOOVhPak92YXh1MEZhQ1RMQlBvUlVicUlhcHBTeHBSM0wKOU8wUjFlcmgwV1BBSm5hSGhBR2lWK24vcXZiQmVKaGNxbnUzYUlZTXpZbDZyZzd2M0NPYUd2eW5aYzFJM1VsYwpqMHJMb0tPMEdwdElINGpUbXo4MU8rZ0x4TFZQZWVTUk9uRjA2Uk1ZaGdpcEtSUVJUYzRYOHphTUpHZW1IbWVyCisxK2JqSnY3alFUR05SaVhYbWh2WDFFQUVRRUFBY0xCWHdRWUFRZ0FFd1VDWDI0OU1na1FTMHpobFRPa29LUUMKR3d3QUFKaFdFQUNCeWh3NUQ4WXNoaDRESnhnWTh0byt6TWdhdm81ZGNHVWxodlFybmJtRHlQeXJPejREQW5ZegpUQ3BKa1E2WGZ6Z1NjUGw5ZVRQdnNrU3ZkMjlxd2RlZE82bkJ0T0JwRE9ndGRwYm01RGtXMEd2bjFsUjhmNklHClUvenVETkJrMnh6eXgxTjM0Q3NmVkplb2d5aVIwSzcyZTJEQTN6aHJXaE1BUDJ5OUlEdWNTRWhBSVpHblZLSVoKSng0UzhIYmlHRWxNMWsvb014YVpYNEdPL3VYMmlUdzZMTDIyaEZHM0I5TE5YK2RSZWNSRGV5d1lrWmhRMG5zcApVaDZTWEozZVNHN3loRHFoamNBSU0xTW5lWjRNK0wxSXQyWGdOeVEyVFR4bmExcDhkZ2E5TjlkM0ZLZ0FMa2tECjJ3dXlEYjlkR042NGtOMDJHMUcrci9seU52MTdyRy9xMCtLMDhmc29FUzRZZ2pJZzlMZlRRUDI1NUV0RG5lK2sKTUZ1MVRMa3o1OHlBVE9kc2NHdUZ3Z095SENoVmxJb3hyeXJrZE51QWI4c1NGMEx3MGc2QnpLYStOelpWVitsRwpaYnVreWZFUEU5ZGc2cVVUdEFJVmUxc2htMjNnS2kyeTRFandPYzM3SVkxVmtpUm1jYnlqcWxCOFZDbS8vVm5OCkVEWTNkTk1qU1U2M0N1cThYdm80YlNSRjRIdW8zeU50c3lCWnl2blcyS2ZUZmFYZFQzanB0ZVkyZThRdWZzb28KT2tyK2lzQUNic0xaZVNWUEhWV3RmWG5yS3lEcm5mNjkxVGdCb1lXK2pVU1E5WXZEOXNNZ3BOZ0h4S0MvOUdTVQpCeURhQTQ2dUNHeU5OcC9DWnZWRkpwbU5nczgvaGhEa0s5UmQwMlhOZklieGxYUk9VR3cxdlE9PQo9cjdkTQotLS0tLUVORCBQR1AgUFVCTElDIEtFWSBCTE9DSy0tLS0t",
  "pgp": {
    "name": "misk-crypto-example",
    "email": "test@email.com",
    "comment": "test key"
  },
  "aws_kms_key_id": ""
}"""
    )

    val PGP_ENCRYPT = MiskConfig.RealSecret(
      """-----BEGIN PGP PUBLIC KEY BLOCK-----

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
=r7dM
-----END PGP PUBLIC KEY BLOCK-----"""
    )
  }
}
