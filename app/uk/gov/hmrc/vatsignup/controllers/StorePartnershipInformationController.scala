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

import javax.inject.Inject

import play.api.mvc.Action
import uk.gov.hmrc.auth.core.retrieve.Retrievals
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions}
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.vatsignup.utils.EnrolmentUtils._
import uk.gov.hmrc.vatsignup.models.PartnershipInformation
import uk.gov.hmrc.vatsignup.services.StorePartnershipInformationService
import uk.gov.hmrc.vatsignup.services.StorePartnershipInformationService.PartnershipInformationDatabaseFailureNoVATNumber

import scala.concurrent.{ExecutionContext, Future}

class StorePartnershipInformationController @Inject()(val authConnector: AuthConnector,
                                                      storePartnershipUtrService: StorePartnershipInformationService
                                                     )(implicit ec: ExecutionContext) extends BaseController with AuthorisedFunctions {

  def storePartnershipInformation(vatNumber: String): Action[PartnershipInformation] = {
    Action.async(parse.json[PartnershipInformation]) { implicit req =>
      authorised().retrieve(Retrievals.allEnrolments) {
        enrolments =>
          val utr = req.body.sautr
          enrolments.partnershipUtr match {
            case Some(`utr`) =>
              storePartnershipUtrService.storePartnershipInformation(vatNumber, req.body) map {
                case Right(_) => NoContent
                case Left(PartnershipInformationDatabaseFailureNoVATNumber) => NotFound
                case Left(_) => InternalServerError
              }
            case Some(e) =>
              Future.successful(Forbidden)
            case None =>
              // TODO covered by a future story
              Future.successful(NotImplemented)
          }
      }
    }
  }

}
