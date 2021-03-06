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

import javax.inject.{Inject, Singleton}

import play.api.libs.json._
import play.api.mvc.Action
import uk.gov.hmrc.auth.core.retrieve.Retrievals
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions}
import uk.gov.hmrc.http.InternalServerException
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.vatsignup.models._
import uk.gov.hmrc.vatsignup.services.StorePartnershipInformationService
import uk.gov.hmrc.vatsignup.services.StorePartnershipInformationService._
import uk.gov.hmrc.vatsignup.utils.EnrolmentUtils._
import StorePartnershipInformationController._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class StorePartnershipInformationController @Inject()(val authConnector: AuthConnector,
                                                      storePartnershipUtrService: StorePartnershipInformationService
                                                     )(implicit ec: ExecutionContext) extends BaseController with AuthorisedFunctions {

  def storePartnershipInformation(vatNumber: String): Action[StorePartnershipRequest] =
    Action.async(parse.json[StorePartnershipRequest](StorePartnershipRequest.reader)) { implicit req =>
      authorised().retrieve(Retrievals.allEnrolments) {
        enrolments =>
          (enrolments.partnershipUtr, req.body.postCode) match {
            case (Some(enrolmentUtr), _) =>
              storePartnershipUtrService.storePartnershipInformationWithEnrolment(vatNumber, req.body.partnership, enrolmentUtr) map {
                case Right(_) => NoContent
                case Left(EnrolmentMatchFailure) => Forbidden
                case Left(PartnershipInformationDatabaseFailureNoVATNumber) => PreconditionFailed
                case Left(_) => InternalServerError
              }
            case (None, Some(postCode)) =>
              storePartnershipUtrService.storePartnershipInformation(vatNumber, req.body.partnership, postCode) map {
                case Right(_) => NoContent
                case Left(KnownFactsMismatch) => Forbidden
                case Left(InsufficientData) => throw new InternalServerException("No postcodes returned for the partnership")
                case Left(InvalidSautr) => PreconditionFailed(Json.obj("statusCode" -> PRECONDITION_FAILED, "message" -> invalidSautrKey))
                case Left(PartnershipInformationDatabaseFailureNoVATNumber) => PreconditionFailed
                case Left(_) => InternalServerError
              }
            case _ =>
              Future.successful(PreconditionFailed(Json.obj("statusCode" -> PRECONDITION_FAILED, "message" -> "no enrolment or postcode")))
          }
      }
    }

}

object StorePartnershipInformationController {
  val invalidSautrKey = "Invalid Sautr"
}