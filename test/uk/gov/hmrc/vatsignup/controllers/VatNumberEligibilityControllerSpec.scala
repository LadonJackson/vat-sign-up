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

package uk.gov.hmrc.vatsignup.controllers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import play.api.http.Status._
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.vatsignup.connectors.mocks.MockAuthConnector
import uk.gov.hmrc.vatsignup.helpers.TestConstants.{testMigratableDate, testVatNumber}
import uk.gov.hmrc.vatsignup.models.MigratableDates
import uk.gov.hmrc.vatsignup.service.mocks.MockVatNumberEligibilityService
import uk.gov.hmrc.vatsignup.services.VatNumberEligibilityService._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class VatNumberEligibilityControllerSpec extends UnitSpec with MockAuthConnector with MockVatNumberEligibilityService{

  object TestVatNumberEligibilityController extends VatNumberEligibilityController(mockAuthConnector, mockVatNumberEligibilityService)

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  "checkVatNumberEligibility" when {
    "the service returns VAT number eligible" should {
      "return NO_CONTENT" in {
        mockAuthorise()(Future.successful(Unit))
        mockCheckVatNumberEligibility(testVatNumber)(Future.successful(Right(VatNumberEligible)))
        val res = await(TestVatNumberEligibilityController.checkVatNumberEligibility(testVatNumber)(FakeRequest()))
        status(res) shouldBe NO_CONTENT
      }
    }
    "the service returns VAT number ineligible with no migration dates" should {
      "return BAD_REQUEST" in {
        mockAuthorise()(Future.successful(Unit))
        mockCheckVatNumberEligibility(testVatNumber)(Future.successful(Left(VatNumberIneligible(MigratableDates.empty))))
        val res = await(TestVatNumberEligibilityController.checkVatNumberEligibility(testVatNumber)(FakeRequest()))
        status(res) shouldBe BAD_REQUEST
        jsonBodyOf(res) shouldBe Json.obj()
      }
    }

    "the service returns VAT number ineligible with migratable dates included" should {
      "return BAD_REQUEST with the migratable dates" in {
        val migratableDates = MigratableDates(
          migratableDate = Some(testMigratableDate),
          migratableCutoffDate = Some(testMigratableDate)
        )

        mockAuthorise()(Future.successful(Unit))
        mockCheckVatNumberEligibility(testVatNumber)(Future.successful(Left(VatNumberIneligible(migratableDates))))
        val res = await(TestVatNumberEligibilityController.checkVatNumberEligibility(testVatNumber)(FakeRequest()))
        status(res) shouldBe BAD_REQUEST
        jsonBodyOf(res) shouldBe Json.toJson(migratableDates)
      }
    }

    "the service returns VAT Number not found" should {
      "return NOT_FOUND" in {
        mockAuthorise()(Future.successful(Unit))
        mockCheckVatNumberEligibility(testVatNumber)(Future.successful(Left(VatNumberNotFound)))
        val res = await(TestVatNumberEligibilityController.checkVatNumberEligibility(testVatNumber)(FakeRequest()))
        status(res) shouldBe NOT_FOUND
      }
    }
    "the service returns InvalidVatNumber" should {
      "return NOT_FOUND" in {
        mockAuthorise()(Future.successful(Unit))
        mockCheckVatNumberEligibility(testVatNumber)(Future.successful(Left(InvalidVatNumber)))
        val res = await(TestVatNumberEligibilityController.checkVatNumberEligibility(testVatNumber)(FakeRequest()))
        status(res) shouldBe NOT_FOUND
      }
    }

    "the service returns anything else" should {
      "return BAD_GATEWAY" in {
        mockAuthorise()(Future.successful(Unit))
        mockCheckVatNumberEligibility(testVatNumber)(Future.successful(Left(GetVatCustomerInformationFailure)))
        val res = await(TestVatNumberEligibilityController.checkVatNumberEligibility(testVatNumber)(FakeRequest()))
        status(res) shouldBe BAD_GATEWAY
      }
    }
  }

}
