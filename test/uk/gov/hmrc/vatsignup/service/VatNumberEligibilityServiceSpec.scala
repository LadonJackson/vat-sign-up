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

import org.scalatest.EitherValues
import play.api.http.Status
import play.api.mvc.Request
import play.api.test.FakeRequest
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.vatsignup.config.featureswitch.AlreadySubscribedCheck
import uk.gov.hmrc.vatsignup.config.mocks.{MockConfig, MockEligibilityConfig}
import uk.gov.hmrc.vatsignup.connectors.mocks.{MockKnownFactsAndControlListInformationConnector, MockMandationStatusConnector}
import uk.gov.hmrc.vatsignup.helpers.TestConstants._
import uk.gov.hmrc.vatsignup.httpparsers.GetMandationStatusHttpParser.GetMandationStatusHttpFailure
import uk.gov.hmrc.vatsignup.httpparsers.KnownFactsAndControlListInformationHttpParser._
import uk.gov.hmrc.vatsignup.httpparsers._
import uk.gov.hmrc.vatsignup.models._
import uk.gov.hmrc.vatsignup.models.controllist.{ControlListInformation, DeRegOrDeath, Ineligible, Stagger1}
import uk.gov.hmrc.vatsignup.models.monitoring.ControlListAuditing._
import uk.gov.hmrc.vatsignup.service.mocks.monitoring.MockAuditService
import uk.gov.hmrc.vatsignup.services.VatNumberEligibilityService
import uk.gov.hmrc.vatsignup.services.VatNumberEligibilityService._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class VatNumberEligibilityServiceSpec extends UnitSpec with EitherValues
  with MockMandationStatusConnector with MockKnownFactsAndControlListInformationConnector with MockConfig with MockAuditService with MockEligibilityConfig {

  object TestVatNumberEligibilityService extends VatNumberEligibilityService(
    mockMandationStatusConnector,
    mockKnownFactsAndControlListInformationConnector,
    mockAuditService,
    mockConfig,
    mockEligibilityConfig
  )

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val request: Request[_] = FakeRequest()

  "checkVatNumberEligibility" when {
    "the AlreadySubscribedCheck feature switch is enabled" when {
      "the mandation status service returns NonMTDfB" when {
        "the MTDEligibilityCheck feature switch is enabled" when {
          "the known facts and control list service returns Migratable" should {
            "return VatNumberEligible" in {
              enable(AlreadySubscribedCheck)

              mockGetMandationStatus(testVatNumber)(Future.successful(Right(NonMTDfB)))
              mockGetKnownFactsAndControlListInformation(testVatNumber)(Future.successful(Right(testKnownFactsAndControlListInformation)))

              await(TestVatNumberEligibilityService.checkVatNumberEligibility(testVatNumber)).right.value shouldBe VatNumberEligible
              verifyAudit(ControlListAuditModel(testVatNumber, isSuccess = true))
            }
          }
          "the known facts and control list service returns NonMigratable" should {
            "return VatNumberEligible" in {
              mockNonMigratableParameters(Set(Stagger1))

              enable(AlreadySubscribedCheck)

              mockGetMandationStatus(testVatNumber)(Future.successful(Right(NonMTDfB)))
              mockGetKnownFactsAndControlListInformation(testVatNumber)(Future.successful(Right(testKnownFactsAndControlListInformation)))

              await(TestVatNumberEligibilityService.checkVatNumberEligibility(testVatNumber)).right.value shouldBe VatNumberEligible
              verifyAudit(ControlListAuditModel(testVatNumber, isSuccess = true, nonMigratableReasons = Seq(Stagger1.errorMessage)))
            }
          }
          "the known facts and control list service returns a control list information that is ineligible" should {
            "return VatNumberIneligible" in {
              enable(AlreadySubscribedCheck)
              mockIneligibleParameters(Set(DeRegOrDeath))

              val testIneligible = testKnownFactsAndControlListInformation.copy(controlListInformation =
                ControlListInformation(testKnownFactsAndControlListInformation.controlListInformation.controlList + DeRegOrDeath)
              )
              val failures = testIneligible.controlListInformation.validate(mockEligibilityConfig)
              assert(!failures.isRight)
              val ineligibilityReasons = failures match {
                case Left(Ineligible(err)) => err.toList.map(_.toString)
              }

              mockGetMandationStatus(testVatNumber)(Future.successful(Right(NonMTDfB)))
              mockGetKnownFactsAndControlListInformation(testVatNumber)(Future.successful(Right(testIneligible)))

              await(TestVatNumberEligibilityService.checkVatNumberEligibility(testVatNumber)).left.value shouldBe VatNumberIneligible
              verifyAudit(ControlListAuditModel(testVatNumber, isSuccess = false, ineligibilityReasons))
            }
          }
          "the known facts and control list service returns KnownFactsInvalidVatNumber" should {
            "return InvalidVatNumber" in {
              enable(AlreadySubscribedCheck)

              mockGetMandationStatus(testVatNumber)(Future.successful(Right(NonMTDfB)))
              mockGetKnownFactsAndControlListInformation(testVatNumber)(Future.successful(Left(KnownFactsInvalidVatNumber)))

              await(TestVatNumberEligibilityService.checkVatNumberEligibility(testVatNumber)).left.value shouldBe InvalidVatNumber
              verifyAudit(ControlListAuditModel(testVatNumber, isSuccess = false, failureReasons = Seq(invalidVatNumber)))
            }
          }
          "the known facts and control list service returns ControlListInformationVatNumberNotFound" should {
            "return VatNumberNotFound" in {
              enable(AlreadySubscribedCheck)

              mockGetMandationStatus(testVatNumber)(Future.successful(Right(NonMTDfB)))
              mockGetKnownFactsAndControlListInformation(testVatNumber)(Future.successful(Left(ControlListInformationVatNumberNotFound)))

              await(TestVatNumberEligibilityService.checkVatNumberEligibility(testVatNumber)).left.value shouldBe VatNumberNotFound
              verifyAudit(ControlListAuditModel(testVatNumber, isSuccess = false, failureReasons = Seq(vatNumberNotFound)))
            }
          }
          "the known facts and control list service returns any other error" should {
            "return KnownFactsAndControlListFailure" in {
              enable(AlreadySubscribedCheck)

              mockGetMandationStatus(testVatNumber)(Future.successful(Right(NonMTDfB)))
              mockGetKnownFactsAndControlListInformation(testVatNumber)(Future.successful(Left(UnexpectedKnownFactsAndControlListInformationFailure(Status.INTERNAL_SERVER_ERROR, ""))))

              await(TestVatNumberEligibilityService.checkVatNumberEligibility(testVatNumber)).left.value shouldBe KnownFactsAndControlListFailure
              verifyAudit(ControlListAuditModel(testVatNumber, isSuccess = false, failureReasons = Seq(unexpectedError)))
            }
          }
        }
      }
      "the mandation status service returns NonDigital" when {
        "the MTDEligibilityCheck feature switch is enabled" when {
          "the known facts and control list service returns MtdEligible" should {
            "return VatNumberEligible" in {
              enable(AlreadySubscribedCheck)

              mockGetMandationStatus(testVatNumber)(Future.successful(Right(NonDigital)))
              mockGetKnownFactsAndControlListInformation(testVatNumber)(Future.successful(Right(testKnownFactsAndControlListInformation)))

              await(TestVatNumberEligibilityService.checkVatNumberEligibility(testVatNumber)).right.value shouldBe VatNumberEligible
            }
          }
        }
      }
      "the mandation status service returns VatNumberNotFound" should {
        "return AlreadySubscribed" in {
          enable(AlreadySubscribedCheck)

          mockGetMandationStatus(testVatNumber)(Future.successful(Left(GetMandationStatusHttpParser.VatNumberNotFound)))
          mockGetKnownFactsAndControlListInformation(testVatNumber)(Future.successful(Right(testKnownFactsAndControlListInformation)))

          await(TestVatNumberEligibilityService.checkVatNumberEligibility(testVatNumber)).right.value shouldBe VatNumberEligible
          verifyAudit(ControlListAuditModel(testVatNumber, isSuccess = true))
        }
      }
      "the mandation status service returns MTDfBMandated" should {
        "return AlreadySubscribed" in {
          enable(AlreadySubscribedCheck)

          mockGetMandationStatus(testVatNumber)(Future.successful(Right(MTDfBMandated)))
          await(TestVatNumberEligibilityService.checkVatNumberEligibility(testVatNumber)).left.value shouldBe AlreadySubscribed
        }
      }
      "the mandation status service returns MTDfBVoluntary" should {
        "return AlreadySubscribed" in {
          enable(AlreadySubscribedCheck)

          mockGetMandationStatus(testVatNumber)(Future.successful(Right(MTDfBVoluntary)))
          await(TestVatNumberEligibilityService.checkVatNumberEligibility(testVatNumber)).left.value shouldBe AlreadySubscribed
        }
      }
      "the mandation status service returns any other error" should {
        "return GetVatCustomerInformationFailure" in {
          enable(AlreadySubscribedCheck)

          mockGetMandationStatus(testVatNumber)(Future.successful(Left(GetMandationStatusHttpFailure(Status.INTERNAL_SERVER_ERROR, ""))))
          await(TestVatNumberEligibilityService.checkVatNumberEligibility(testVatNumber)).left.value shouldBe GetVatCustomerInformationFailure
        }
      }
    }
  }

}
