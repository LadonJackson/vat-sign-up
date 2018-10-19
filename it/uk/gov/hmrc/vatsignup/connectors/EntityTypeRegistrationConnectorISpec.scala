/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.vatsignup.connectors

import org.scalatest.EitherValues
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.vatsignup.helpers.ComponentSpecBase
import uk.gov.hmrc.vatsignup.helpers.IntegrationTestConstants._
import uk.gov.hmrc.vatsignup.helpers.servicemocks.EntityTypeRegistrationStub._
import uk.gov.hmrc.vatsignup.httpparsers.RegisterWithMultipleIdentifiersHttpParser.RegisterWithMultipleIdsSuccess
import uk.gov.hmrc.vatsignup.models._

class EntityTypeRegistrationConnectorISpec extends ComponentSpecBase with EitherValues {
  private lazy val registrationConnector: EntityTypeRegistrationConnector = app.injector.instanceOf[EntityTypeRegistrationConnector]

  private implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  "registerPartnership" when {
    "DES returns a successful response" should {
      "return a RegistrationSuccess with the SAFE ID" in {
        stubRegisterBusinessEntity(testVatNumber, GeneralPartnership(testUtr))(testSafeId)

        val res = await(registrationConnector.registerBusinessEntity(testVatNumber, GeneralPartnership(testUtr)))

        res shouldBe Right(RegisterWithMultipleIdsSuccess(testSafeId))
      }
    }
  }

  "registerLimitedPartnership" when {
    "DES returns a successful response" should {
      "return a RegistrationSuccess with the SAFE ID" in {
        stubRegisterBusinessEntity(testVatNumber, LimitedPartnership(testUtr, testCompanyNumber))(testSafeId)

        val res = await(registrationConnector.registerBusinessEntity(testVatNumber, LimitedPartnership(testUtr, testCompanyNumber)))

        res shouldBe Right(RegisterWithMultipleIdsSuccess(testSafeId))
      }
    }
  }

  "registerLimitedLiabilityPartnership" when {
    "DES returns a successful response" should {
      "return a RegistrationSuccess with the SAFE ID" in {
        stubRegisterBusinessEntity(testVatNumber, LimitedLiabilityPartnership(testUtr, testCompanyNumber))(testSafeId)

        val res = await(registrationConnector.registerBusinessEntity(testVatNumber, LimitedLiabilityPartnership(testUtr, testCompanyNumber)))

        res shouldBe Right(RegisterWithMultipleIdsSuccess(testSafeId))
      }
    }
  }

  "registerScottishLimitedPartnership" when {
    "DES returns a successful response" should {
      "return a RegistrationSuccess with the SAFE ID" in {
        stubRegisterBusinessEntity(testVatNumber, ScottishLimitedPartnership(testUtr, testCompanyNumber))(testSafeId)

        val res = await(registrationConnector.registerBusinessEntity(testVatNumber, ScottishLimitedPartnership(testUtr, testCompanyNumber)))

        res shouldBe Right(RegisterWithMultipleIdsSuccess(testSafeId))
      }
    }
  }

  "registerCompany" when {
    "DES returns a successful response" should {
      "return a RegistrationSuccess with the SAFE ID" in {
        stubRegisterBusinessEntity(testVatNumber, LimitedCompany(testCompanyNumber))(testSafeId)

        val res = await(registrationConnector.registerBusinessEntity(testVatNumber, LimitedCompany(testCompanyNumber)))

        res shouldBe Right(RegisterWithMultipleIdsSuccess(testSafeId))
      }
    }
  }

  "registerIndividual" when {
    "DES returns a successful response" should {
      "return a RegistrationSuccess with the SAFE ID" in {
        stubRegisterBusinessEntity(testVatNumber, SoleTrader(testNino))(testSafeId)

        val res = await(registrationConnector.registerBusinessEntity(testVatNumber, SoleTrader(testNino)))

        res shouldBe Right(RegisterWithMultipleIdsSuccess(testSafeId))
      }
    }
  }
}
