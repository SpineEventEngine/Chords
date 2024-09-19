/*
 * Copyright 2024, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.chords.proto.value.net

import com.google.protobuf.ByteString
import io.spine.chords.proto.value.net.IpAddress
import io.spine.chords.proto.value.net.IpAddressKt
import io.spine.chords.proto.value.net.Ipv4Address
import io.spine.chords.proto.value.net.Ipv4AddressKt
import io.spine.chords.proto.value.net.Ipv6Address
import io.spine.chords.proto.value.net.Ipv6AddressKt
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.regex.Pattern

private const val Ipv4ByteLength = 4
private const val Ipv6ByteLength = 16

/**
 * Parses an IP address string and provides a respective [IpAddress] value.
 *
 * @param str
 *         an IP address string that should be parsed.
 * @return an IP address created by parsing the given string
 * @throws IllegalArgumentException
 *         if the given string cannot be parsed.
 */
// The receiver is needed to specify a "static" context for the function.
@Suppress("UnusedReceiverParameter")
public fun IpAddressKt.parse(str: String): IpAddress {
    require(isValidIPv4Address(str)) {
        "Couldn't parse an IP address: $str"
    }

    val inetAddress = try {
        InetAddress.getByName(str)
    } catch (
        // Exception value itself is not needed.
        @Suppress("SwallowedException")
        e: UnknownHostException
    ) {
        throw IllegalArgumentException("Couldn't parse an IP address: $str", e)
    }
    val addressBytes = inetAddress.address
    return when (addressBytes.size) {
        Ipv4ByteLength -> IpAddress.newBuilder()
            .setIpv4(
                Ipv4AddressKt.of(addressBytes)
            )
            .vBuild()

        Ipv6ByteLength -> IpAddress.newBuilder()
            .setIpv6(
                Ipv6AddressKt.of(addressBytes)
            )
            .vBuild()

        else -> throw IllegalArgumentException("Couldn't parse an IP address: $str")
    }
}

/**
 * Checks if passed string is a valid IPv4 address.
 *
 * @param str
 *         an IPv4 string.
 * @return [Boolean], which indicates if passed string is valid IPv4 address.
 */
private fun isValidIPv4Address(str: String): Boolean {
    val regex = "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$"
    val pattern = Pattern.compile(regex)
    val matcher = pattern.matcher(str)

    return matcher.matches()
}

/**
 * Formats [IpAddress] as a string.
 *
 * @receiver the IP address that should be formatted as a string.
 * @return a respective string representation of this IP address.
 */
public fun IpAddress.format(): String {
    val inetAddress = try {
        InetAddress.getByAddress(
            (this.ipv4?.value ?: this.ipv6!!.value).toByteArray()
        )
    } catch (
        // Exception value itself is not needed.
        @Suppress("SwallowedException")
        e: UnknownHostException
    ) {
        return ""
    }
    return inetAddress.hostAddress
}

/**
 * Provides an [Ipv4Address] value for the IPv4 address specified as
 * a byte array.
 *
 * @param bytes
 *         a byte array to create an IP address from
 */
// The receiver is needed to specify a "static" context for the function.
@Suppress("UnusedReceiverParameter")
public fun Ipv4AddressKt.of(bytes: ByteArray): Ipv4Address {
    return Ipv4Address.newBuilder()
        .setValue(ByteString.copyFrom(bytes))
        .vBuild()
}

/**
 * Provides an [Ipv6Address] value for the IPv6 address specified as
 * a byte array.
 *
 * @param bytes
 *         a byte array to create an IP address from.
 */
// The receiver is needed to specify a "static" context for the function.
@Suppress("UnusedReceiverParameter")
public fun Ipv6AddressKt.of(bytes: ByteArray): Ipv6Address {
    return Ipv6Address.newBuilder()
        .setValue(ByteString.copyFrom(bytes))
        .vBuild()
}
