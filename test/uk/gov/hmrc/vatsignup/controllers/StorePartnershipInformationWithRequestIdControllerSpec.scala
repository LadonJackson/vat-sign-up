/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.mvc.{Request, Result}
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.vatsignup.connectors.mocks.MockAuthConnector
import uk.gov.hmrc.vatsignup.helpers.TestConstants._
import uk.gov.hmrc.vatsignup.models.ExplicitEntityType.GeneralPartnership
import uk.gov.hmrc.vatsignup.models.PartnershipInformation
import uk.gov.hmrc.vatsignup.service.mocks.MockStorePartnershipInformationWithRequestIdService
import uk.gov.hmrc.vatsignup.services.StorePartnershipInformationWithRequestIdService._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class StorePartnershipInformationWithRequestIdControllerSpec extends UnitSpec with MockAuthConnector with MockStorePartnershipInformationWithRequestIdService {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  object TestStorePartnershipInformationWithRequestIdController extends StorePartnershipInformationWithRequestIdController(
    mockAuthConnector,
    mockStorePartnershipInformationWithRequestIdService
  )

  val testPartnershipInformation = PartnershipInformation(GeneralPartnership, testUtr, Some(testCompanyNumber))

  val request: Request[PartnershipInformation] = FakeRequest().withBody[PartnershipInformation](testPartnershipInformation)

  "storePartnershipInformation" when {
    "the UTR in the request json does not match the UTR in the enrolment" should {
      "return FORBIDDEN" in {
        mockAuthRetrievePartnershipEnrolment()
        mockStorePartnershipInformationSuccess(testToken, testPartnershipInformation)

        val request = FakeRequest().withBody[PartnershipInformation](testPartnershipInformation.copy(sautr = testUtr.drop(1)))

        val result: Result = await(TestStorePartnershipInformationWithRequestIdController.storePartnershipInformation(testToken)(request))

        status(result) shouldBe FORBIDDEN
      }
    }
    "the UTR in the request json matches the UTR in the enrolment" should {
      "store partnership information returns StorePartnershipInformationSuccess" should {
        "return NO_CONTENT" in {
          mockAuthRetrievePartnershipEnrolment()
          mockStorePartnershipInformationSuccess(testToken, testPartnershipInformation)

          val result: Result = await(TestStorePartnershipInformationWithRequestIdController.storePartnershipInformation(testToken)(request))

          status(result) shouldBe NO_CONTENT
        }
      }
      "store partnership information returns PartnershipInformationDatabaseFailureNoToken" should {
        "return NOT_FOUND" in {
          mockAuthRetrievePartnershipEnrolment()
          mockStorePartnershipInformation(testToken, testPartnershipInformation)(Future.successful(Left(PartnershipInformationDatabaseFailureNoToken)))

          val result: Result = await(TestStorePartnershipInformationWithRequestIdController.storePartnershipInformation(testToken)(request))

          status(result) shouldBe NOT_FOUND
        }
      }
      "store partnership information returns PartnershipInformationDatabaseFailure" should {
        "return INTERNAL_SERVER_ERROR" in {
          mockAuthRetrievePartnershipEnrolment()
          mockStorePartnershipInformation(testToken, testPartnershipInformation)(Future.successful(Left(PartnershipInformationDatabaseFailure)))

          val result: Result = await(TestStorePartnershipInformationWithRequestIdController.storePartnershipInformation(testToken)(request))

          status(result) shouldBe INTERNAL_SERVER_ERROR
        }
      }
    }
  }

}
