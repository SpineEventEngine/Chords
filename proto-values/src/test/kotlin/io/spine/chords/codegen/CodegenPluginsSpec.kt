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

package io.spine.chords.codegen

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.spine.chords.proto.value.money.BankAccount
import io.spine.chords.proto.value.money.BankAccountDef
import io.spine.chords.proto.value.money.PaymentCardNumber
import io.spine.chords.proto.value.money.PaymentCardNumberDef
import io.spine.chords.proto.value.money.PaymentMethod
import io.spine.chords.proto.value.money.PaymentMethodDef
import io.spine.chords.proto.value.net.IpAddress
import io.spine.chords.proto.value.net.IpAddressDef
import io.spine.chords.proto.value.net.Ipv4Address
import io.spine.chords.proto.value.net.Ipv4AddressDef
import io.spine.chords.proto.value.net.Ipv6Address
import io.spine.chords.proto.value.net.Ipv6AddressDef
import io.spine.chords.runtime.MessageDef
import io.spine.chords.runtime.MessageField
import io.spine.chords.runtime.MessageOneof
import io.spine.chords.runtime.messageDef
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Checks various use-cases on code generation for [MessageField], [MessageOneof],
 * and [MessageDef]  implementations.
 */
@DisplayName("CodegenPlugins should")
internal class CodegenPluginsSpec {

    /**
     * The goal of this test is checking that the generated fields
     * are exist and compilable.
     */
    @Test
    fun `generate 'MessageField', 'MessageOneof', and 'MessageDef' implementations`() {

        BankAccountDef.number shouldNotBe null
        BankAccount.newBuilder().messageDef() shouldBe BankAccountDef

        PaymentCardNumberDef.value shouldNotBe null
        PaymentCardNumber.newBuilder().messageDef() shouldBe PaymentCardNumberDef

        PaymentMethodDef.paymentCard shouldNotBe null
        PaymentMethodDef.bankAccount shouldNotBe null
        PaymentMethodDef.method shouldNotBe null
        PaymentMethod.newBuilder().messageDef() shouldBe PaymentMethodDef

        IpAddressDef.ipv4 shouldNotBe null
        IpAddressDef.ipv6 shouldNotBe null
        IpAddressDef.value shouldNotBe null
        IpAddress.newBuilder().messageDef() shouldBe IpAddressDef

        Ipv4AddressDef.value shouldNotBe null
        Ipv4Address.newBuilder().messageDef() shouldBe Ipv4AddressDef

        Ipv6AddressDef.value shouldNotBe null
        Ipv6Address.newBuilder().messageDef() shouldBe Ipv6AddressDef
    }
}
