package tech.relaycorp.veraid

import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.cms.Attribute
import tech.relaycorp.veraid.dns.DnssecChain
import tech.relaycorp.veraid.dns.InvalidChainException
import tech.relaycorp.veraid.pki.Member
import tech.relaycorp.veraid.pki.MemberCertificate
import tech.relaycorp.veraid.pki.MemberIdBundle
import tech.relaycorp.veraid.pki.OrgCertificate
import tech.relaycorp.veraid.pki.PkiException
import tech.relaycorp.veraid.utils.asn1.ASN1Exception
import tech.relaycorp.veraid.utils.asn1.ASN1Utils
import tech.relaycorp.veraid.utils.cms.SignedData
import tech.relaycorp.veraid.utils.cms.SignedDataException
import tech.relaycorp.veraid.utils.x509.CertificateException
import java.security.PrivateKey
import java.time.ZonedDateTime

public class SignatureBundle internal constructor(
    internal val memberIdBundle: MemberIdBundle,
    internal val signedData: SignedData,
) {
    public fun serialise(): ByteArray = ASN1Utils.serializeSequence(
        listOf(
            ASN1Integer(0),
            memberIdBundle.dnssecChain.encode(),
            memberIdBundle.orgCertificate.encode(),
            signedData.encode(),
        ),
        false,
    )

    public suspend fun verify(
        plaintext: ByteArray,
        serviceOid: String,
        date: ZonedDateTime,
    ): Member = verify(plaintext, serviceOid, date..date)

    public suspend fun verify(
        plaintext: ByteArray,
        serviceOid: String,
        datePeriod: DatePeriod? = null,
    ): Member {
        val now = ZonedDateTime.now()
        val verificationPeriod = datePeriod ?: now..now
        if (verificationPeriod.endInclusive < verificationPeriod.start) {
            throw SignatureException("Verification expiry date cannot be before start date")
        }

        try {
            signedData.verify(plaintext)
        } catch (exc: SignedDataException) {
            throw SignatureException("Signature is invalid", exc)
        }

        val metadata = getSignatureMetadata()

        val signaturePeriodIntersection = metadata.validityPeriod.intersect(verificationPeriod)
            ?: throw SignatureException("Signature period does not overlap with required period")

        if (metadata.service.id != serviceOid) {
            throw SignatureException(
                "Signature is bound to a different service (${metadata.service.id})",
            )
        }

        return try {
            memberIdBundle.verify(ASN1ObjectIdentifier(serviceOid), signaturePeriodIntersection)
        } catch (exc: PkiException) {
            throw SignatureException("Member id bundle is invalid", exc)
        }
    }

    private fun getSignatureMetadata(): SignatureMetadata {
        val signedAttrs = signedData.signedAttrs
        val metadataAttribute = signedAttrs?.get(VeraOids.SIGNATURE_METADATA_ATTR)
            ?: throw SignatureException("SignedData should have VeraId metadata attribute")
        if (metadataAttribute.attrValues.size() == 0) {
            throw SignatureException("Metadata attribute should have at least one value")
        }
        val metadataAttributeValue = metadataAttribute.attrValues.getObjectAt(0)
        return try {
            SignatureMetadata.decode(metadataAttributeValue)
        } catch (exc: SignatureException) {
            throw SignatureException("Metadata attribute is malformed", exc)
        }
    }

    public companion object {
        public fun generate(
            plaintext: ByteArray,
            serviceOid: String,
            memberIdBundle: MemberIdBundle,
            signingKey: PrivateKey,
            expiryDate: ZonedDateTime,
            startDate: ZonedDateTime = ZonedDateTime.now(),
        ): SignatureBundle {
            val metadata = SignatureMetadata(
                ASN1ObjectIdentifier(serviceOid),
                startDate..expiryDate,
            )
            val metadataAttribute = Attribute(
                VeraOids.SIGNATURE_METADATA_ATTR,
                DERSet(metadata.encode()),
            )
            val signedData = SignedData.sign(
                plaintext,
                signingKey,
                memberIdBundle.memberCertificate,
                setOf(memberIdBundle.memberCertificate, memberIdBundle.orgCertificate),
                encapsulatePlaintext = false,
                extraSignedAttrs = listOf(metadataAttribute),
            )
            return SignatureBundle(memberIdBundle, signedData)
        }

        @Throws(SignatureException::class)
        public fun deserialise(serialisation: ByteArray): SignatureBundle {
            val sequence = try {
                ASN1Utils.deserializeHeterogeneousSequence(serialisation)
            } catch (exc: ASN1Exception) {
                throw SignatureException("Signature bundle should be a SEQUENCE", exc)
            }

            if (sequence.size < 4) {
                throw SignatureException("Signature bundle should have at least 4 items")
            }

            val orgCertificate = try {
                OrgCertificate.decode(sequence[2])
            } catch (exc: CertificateException) {
                throw SignatureException("Organisation certificate is malformed", exc)
            }

            val dnssecChain = try {
                DnssecChain.decode(orgCertificate.commonName, sequence[1])
            } catch (exc: InvalidChainException) {
                throw SignatureException("VeraId DNSSEC chain is malformed", exc)
            }

            val signedData = try {
                SignedData.decode(sequence[3])
            } catch (exc: SignedDataException) {
                throw SignatureException("SignedData is malformed", exc)
            }

            val signerCertificate = signedData.signerCertificate
                ?: throw SignatureException("SignedData should have signer certificate attached")

            val memberIdBundle = MemberIdBundle(
                dnssecChain,
                orgCertificate,
                MemberCertificate(signerCertificate.certificateHolder),
            )
            return SignatureBundle(memberIdBundle, signedData)
        }
    }
}
