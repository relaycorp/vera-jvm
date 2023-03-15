@file:JvmName("Periods")

package tech.relaycorp.veraid

import org.bouncycastle.asn1.ASN1Sequence
import tech.relaycorp.veraid.utils.asn1.ASN1Utils
import tech.relaycorp.veraid.utils.asn1.toGeneralizedTime
import java.time.Instant
import java.time.ZonedDateTime

internal typealias InstantPeriod = ClosedRange<Instant>
public typealias DatePeriod = ClosedRange<ZonedDateTime>

internal fun DatePeriod.toInstantPeriod(): InstantPeriod =
    start.toInstant()..endInclusive.toInstant()

internal fun DatePeriod.encode(): ASN1Sequence = ASN1Utils.makeSequence(
    listOf(start.toGeneralizedTime(), endInclusive.toGeneralizedTime()),
    false,
)
