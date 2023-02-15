package tech.relaycorp.vera.pki

import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.InvalidKeySpecException
import org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPrivateKey
import org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPublicKey
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class KeysTest {
    @Nested
    inner class GenerateRSAKeyPair {
        @Test
        fun `Key pair should be returned when a valid modulus is passed`() {
            val keyPair = generateRSAKeyPair(4096)

            assert(keyPair.private is RSAPrivateKey)
            (keyPair.private as RSAPrivateKey).modulus.bitLength() shouldBe 4096

            assert(keyPair.public is RSAPublicKey)
            (keyPair.public as RSAPublicKey).modulus.bitLength() shouldBe 4096
        }

        @Test
        fun `Modulus should be 2048 by default`() {
            val keyPair = generateRSAKeyPair()

            (keyPair.private as RSAPrivateKey).modulus.bitLength() shouldBe 2048

            (keyPair.public as RSAPublicKey).modulus.bitLength() shouldBe 2048
        }

        @Test
        fun `Modulus should be 2048 or greater`() {
            val exception = assertThrows<KeyException> {
                generateRSAKeyPair(2047)
            }
            exception.message shouldBe "Modulus should be at least 2048 (got 2047)"
        }

        @Test
        fun `BouncyCastle provider should be used`() {
            val keyPair = generateRSAKeyPair()

            keyPair.public should beInstanceOf<BCRSAPublicKey>()
            keyPair.private should beInstanceOf<BCRSAPrivateKey>()
        }
    }

    @Nested
    inner class DeserializeRSAKeyPair {
        @Test
        fun `Deserialize invalid key`() {
            val exception =
                assertThrows<KeyException> { "s".toByteArray().deserializeRSAKeyPair() }

            exception.message shouldBe "Value is not a valid RSA private key"
            exception.cause should beInstanceOf<InvalidKeySpecException>()
        }

        @Test
        fun `Deserialize valid private key`() {
            val keyPair = generateRSAKeyPair()
            val privateKeySerialized = keyPair.private.encoded

            val keyPairDeserialized = privateKeySerialized.deserializeRSAKeyPair()

            keyPairDeserialized.private.encoded.asList() shouldBe keyPair.private.encoded.asList()
            keyPairDeserialized.public.encoded.asList() shouldBe keyPair.public.encoded.asList()
        }

        @Test
        fun `BouncyCastle provider should be used`() {
            val keyPair = generateRSAKeyPair()
            val privateKeySerialized = keyPair.private.encoded

            val keyPairDeserialized = privateKeySerialized.deserializeRSAKeyPair()

            keyPairDeserialized.public should beInstanceOf<BCRSAPublicKey>()
            keyPairDeserialized.private should beInstanceOf<BCRSAPrivateKey>()
        }
    }

    @Nested
    inner class DeserializeRSAPublicKey {
        @Test
        fun `Deserialize invalid key`() {
            val exception =
                assertThrows<KeyException> { "s".toByteArray().deserializeRSAPublicKey() }

            exception.message shouldBe "Value is not a valid RSA public key"
            exception.cause should beInstanceOf<InvalidKeySpecException>()
        }

        @Test
        fun `Deserialize valid key`() {
            val keyPair = generateRSAKeyPair()
            val publicKeySerialized = keyPair.public.encoded

            val publicKeyDeserialized = publicKeySerialized.deserializeRSAPublicKey()

            publicKeyDeserialized.encoded.asList() shouldBe publicKeySerialized.asList()
        }

        @Test
        fun `BouncyCastle provider should be used`() {
            val keyPair = generateRSAKeyPair()
            val publicKeySerialized = keyPair.public.encoded

            val publicKeyDeserialized = publicKeySerialized.deserializeRSAPublicKey()

            publicKeyDeserialized should beInstanceOf<BCRSAPublicKey>()
        }
    }
}
