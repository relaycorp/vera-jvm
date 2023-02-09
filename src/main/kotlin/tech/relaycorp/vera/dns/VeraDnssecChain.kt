package tech.relaycorp.vera.dns

import kotlin.jvm.Throws
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERSet

/**
 * Vera DNSSEC chain.
 *
 * It contains the DNSSEC chain for the Vera TXT RRSet (e.g., `_vera.example.com./TXT`).
 */
public class VeraDnssecChain internal constructor(internal val responses: List<ByteArray>) {
    /**
     * Serialise the chain.
     */
    public fun serialise(): ByteArray {
        val responsesWrapped = responses.map { DEROctetString(it) }
        val vector = ASN1EncodableVector(responsesWrapped.size)
        vector.addAll(responsesWrapped.toTypedArray())
        return DERSet(vector).encoded
    }

    public companion object {
        private const val VERA_RECORD_TYPE = "TXT"
        private const val CLOUDFLARE_RESOLVER = "1.1.1.1"

        internal var dnssecChainRetriever: ChainRetriever = DnssecChain.Companion::retrieve

        /**
         * Retrieve Vera DNSSEC chain for [organisationName].
         *
         * @param organisationName The domain name of the organisation
         * @param resolverHost The IPv4 address for the DNSSEC-aware, recursive resolver
         * @throws DnsException if there was a DNS- or DNSSEC-related error
         */
        @JvmStatic
        @Throws(DnsException::class)
        public suspend fun retrieve(
            organisationName: String,
            resolverHost: String = CLOUDFLARE_RESOLVER
        ): VeraDnssecChain {
            val domainName = "_vera.${organisationName.trimEnd('.')}."
            val dnssecChain = dnssecChainRetriever(domainName, VERA_RECORD_TYPE, resolverHost)
            return VeraDnssecChain(dnssecChain.responses)
        }
    }
}
