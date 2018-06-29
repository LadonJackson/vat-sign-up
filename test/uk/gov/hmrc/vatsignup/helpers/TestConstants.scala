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

package uk.gov.hmrc.vatsignup.helpers

import java.util.UUID

import uk.gov.hmrc.auth.core.Enrolment
import uk.gov.hmrc.vatsignup.config.Constants._
import uk.gov.hmrc.vatsignup.httpparsers.KnownFactsAndControlListInformationHttpParser.KnownFactsAndControlListInformation
import uk.gov.hmrc.vatsignup.models.ControlListInformation.{Company, Stagger1}
import uk.gov.hmrc.vatsignup.models.SignUpRequest.{LimitedCompany, SoleTrader, EmailAddress}
import uk.gov.hmrc.vatsignup.models.{ControlListInformation, CustomerDetails}
import uk.gov.hmrc.vatsignup.utils.controllist.ControlListInformationParser.ControlListInformationIndices._


object TestConstants {
  val testVatNumber: String = UUID.randomUUID().toString
  val testNino: String = UUID.randomUUID().toString
  val testCompanyNumber: String = UUID.randomUUID().toString
  val testCtReference: String = UUID.randomUUID().toString
  val testEmail: String = UUID.randomUUID().toString
  val testAgentReferenceNumber: String = UUID.randomUUID().toString
  val testSafeId: String = UUID.randomUUID().toString
  val testToken = UUID.randomUUID().toString
  val testJourneyLink = s"/mdtp/journey/journeyId/${UUID.randomUUID().toString}"
  val testBusinessEntityLTD = LimitedCompany(testCompanyNumber)
  val testBusinessEntitySole = SoleTrader(testNino)
  val testSignUpEmail = EmailAddress(testEmail, true)

  val testPostCode = "ZZ11 1ZZ"
  val testDateOfRegistration = "2017-01-01"

  val testAgentEnrolment: Enrolment = Enrolment(AgentEnrolmentKey).withIdentifier(AgentReferenceNumberKey, testAgentReferenceNumber)
  val testPrincipalEnrolment: Enrolment = Enrolment(VatDecEnrolmentKey).withIdentifier(VatReferenceKey, testVatNumber)

  val testErrorMsg = "this is an error"

  val testCustomerDetails = CustomerDetails(Some("testFirstName"),
    Some("testLastName"),
    Some("testOrganisationName"),
    Some("testTradingName"))

  val testControlListInformation = ControlListInformation(
    false, false, false, false, false, false,
    false, false, false, false, false, false,
    Stagger1, false, false, false, Company,
    false, false, false, false
  )

  val testKnownFactsAndControlListInformation = KnownFactsAndControlListInformation(
    testPostCode,
    testDateOfRegistration,
    testControlListInformation
  )

  object ControlList {
    val allFalse: String = "1" * CONTROL_INFORMATION_STRING_LENGTH
    val valid: String = setupTestDataCore(allFalse)(STAGGER_1 -> '0', COMPANY -> '0')
    val businessEntityConflict: String = setupTestData(COMPANY -> '0', SOLE_TRADER -> '0')
    val staggerConflict: String = setupTestData(ANNUAL_STAGGER -> '0', STAGGER_1 -> '0')

    def setupTestData(amendments: (Int, Character)*): String = setupTestDataCore(valid)(amendments: _*)

    private def setupTestDataCore(startString: String)(amendments: (Int, Character)*): String = {
      require(amendments.forall { case (index, _) => index >= 0 && index < CONTROL_INFORMATION_STRING_LENGTH })
      require(amendments.forall { case (_, newValue) => newValue == '0' || newValue == '1' })

      amendments.foldLeft[String](startString) {
        case (pre: String, (index: Int, value: Character)) =>
          pre.substring(0, index) + value + pre.substring(index + 1, pre.length)
      }
    }
  }

}
