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

package uk.gov.hmrc.vatsignup.service

import play.api.http.Status._
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.vatsignup.config.mocks.MockEligibilityConfig
import uk.gov.hmrc.vatsignup.connectors.mocks.MockKnownFactsAndControlListInformationConnector
import uk.gov.hmrc.vatsignup.helpers.TestConstants._
import uk.gov.hmrc.vatsignup.httpparsers.KnownFactsAndControlListInformationHttpParser._
import uk.gov.hmrc.vatsignup.models.MigratableDates
import uk.gov.hmrc.vatsignup.models.controllist.Stagger1
import uk.gov.hmrc.vatsignup.models.monitoring.ControlListAuditing
import uk.gov.hmrc.vatsignup.models.monitoring.ControlListAuditing.ControlListAuditModel
import uk.gov.hmrc.vatsignup.service.mocks.monitoring.MockAuditService
import uk.gov.hmrc.vatsignup.services.ControlListEligibilityService
import uk.gov.hmrc.vatsignup.services.ControlListEligibilityService._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ControlListEligibilityServiceSpec extends UnitSpec
  with MockKnownFactsAndControlListInformationConnector with MockAuditService with MockEligibilityConfig {

  object TestControlListEligibilityService extends ControlListEligibilityService(
    mockKnownFactsAndControlListInformationConnector,
    mockEligibilityConfig,
    mockAuditService
  )

  implicit val request: Request[AnyContent] = FakeRequest()
  implicit val hc: HeaderCarrier = HeaderCarrier()

  "getEligibilityStatus" when {
    "the Control List indicates the user is eligible and migratable" should {
      "return EligibilitySuccess with the known facts and a migration status of true" in {
        mockGetKnownFactsAndControlListInformation(testVatNumber)(
          Future.successful(Right(KnownFactsAndControlListInformation(testPostCode, testDateOfRegistration, testControlListInformation))))

        val res = await(TestControlListEligibilityService.getEligibilityStatus(testVatNumber))

        res shouldBe Right(EligibilitySuccess(testPostCode, testDateOfRegistration, isMigratable = true))
        verifyAudit(ControlListAuditModel(testVatNumber, isSuccess = true))
      }
    }

    "the Control List indicates the user is eligible but non migratable" should {
      "return EligibilitySuccess with the known facts and a migration status of true" in {
        mockNonMigratableParameters(Set(Stagger1))
        mockGetKnownFactsAndControlListInformation(testVatNumber)(
          Future.successful(Right(KnownFactsAndControlListInformation(testPostCode, testDateOfRegistration, testControlListInformation))))

        val res = await(TestControlListEligibilityService.getEligibilityStatus(testVatNumber))

        res shouldBe Right(EligibilitySuccess(testPostCode, testDateOfRegistration, isMigratable = false))
        verifyAudit(ControlListAuditModel(testVatNumber, isSuccess = true, nonMigratableReasons = Seq(Stagger1.errorMessage)))
      }
    }

    "the control list indicates the user is ineligible with no migratable dates" should {
      "return IneligibleVatNumber" in {
        mockIneligibleParameters(Set(Stagger1))
        mockGetKnownFactsAndControlListInformation(testVatNumber)(
          Future.successful(Right(KnownFactsAndControlListInformation(testPostCode, testDateOfRegistration, testControlListInformation))))

        val res = await(TestControlListEligibilityService.getEligibilityStatus(testVatNumber))

        res shouldBe Left(IneligibleVatNumber(MigratableDates.empty))
        verifyAudit(ControlListAuditModel(testVatNumber, isSuccess = false, failureReasons = Seq(Stagger1.errorMessage)))
      }
    }

    "the control list returns invalid VAT number" should {
      "return InvalidVatNumber" in {
        mockGetKnownFactsAndControlListInformation(testVatNumber)(
          Future.successful(Left(KnownFactsInvalidVatNumber)))

        val res = await(TestControlListEligibilityService.getEligibilityStatus(testVatNumber))

        res shouldBe Left(InvalidVatNumber)
        verifyAudit(ControlListAuditModel(testVatNumber, isSuccess = false, failureReasons = Seq(ControlListAuditing.invalidVatNumber)))
      }
    }

    "the control list returns VAT number not found" should {
      "return InvalidVatNumber" in {
        mockGetKnownFactsAndControlListInformation(testVatNumber)(
          Future.successful(Left(ControlListInformationVatNumberNotFound)))

        val res = await(TestControlListEligibilityService.getEligibilityStatus(testVatNumber))

        res shouldBe Left(VatNumberNotFound)
        verifyAudit(ControlListAuditModel(testVatNumber, isSuccess = false, failureReasons = Seq(ControlListAuditing.vatNumberNotFound)))
      }
    }

    "the control list returns any other error" should {
      "return KnownFactsAndControlListFailure" in {
        mockGetKnownFactsAndControlListInformation(testVatNumber)(
          Future.successful(Left(UnexpectedKnownFactsAndControlListInformationFailure(BAD_REQUEST, ""))))

        val res = await(TestControlListEligibilityService.getEligibilityStatus(testVatNumber))

        res shouldBe Left(KnownFactsAndControlListFailure)
        verifyAudit(ControlListAuditModel(testVatNumber, isSuccess = false, failureReasons = Seq(ControlListAuditing.unexpectedError)))
      }
    }
  }
}
