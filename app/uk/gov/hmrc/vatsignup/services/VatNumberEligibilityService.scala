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

package uk.gov.hmrc.vatsignup.services

import javax.inject.{Inject, Singleton}

import cats.data.EitherT
import cats.implicits._
import play.api.mvc.Request
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.vatsignup.config.AppConfig
import uk.gov.hmrc.vatsignup.config.featureswitch.AlreadySubscribedCheck
import uk.gov.hmrc.vatsignup.connectors.{KnownFactsAndControlListInformationConnector, MandationStatusConnector}
import uk.gov.hmrc.vatsignup.httpparsers.KnownFactsAndControlListInformationHttpParser.{ControlListInformationVatNumberNotFound, KnownFactsAndControlListInformation, KnownFactsInvalidVatNumber}
import uk.gov.hmrc.vatsignup.models._
import uk.gov.hmrc.vatsignup.models.controllist.{Ineligible, Migratable, NonMigratable}
import uk.gov.hmrc.vatsignup.models.monitoring.ControlListAuditing._
import uk.gov.hmrc.vatsignup.services.VatNumberEligibilityService._
import uk.gov.hmrc.vatsignup.services.monitoring.AuditService

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class VatNumberEligibilityService @Inject()(mandationStatusConnector: MandationStatusConnector,
                                            knownFactsAndControlListInformationConnector: KnownFactsAndControlListInformationConnector,
                                            auditService: AuditService,
                                            appConfig: AppConfig)(implicit ec: ExecutionContext) {

  def checkVatNumberEligibility(vatNumber: String)(implicit hc: HeaderCarrier, request: Request[_]): Future[VatNumberEligibility] = {
    for {
      _ <- checkExistingVatSubscription(vatNumber)
      _ <- getEligibilityStatus(vatNumber)
    } yield VatNumberEligible
  }.value

  private def checkExistingVatSubscription(vatNumber: String
                                          )(implicit hc: HeaderCarrier): EitherT[Future, VatNumberEligibilityFailure, NotSubscribed.type] = {
    import uk.gov.hmrc.vatsignup.httpparsers.GetMandationStatusHttpParser._
    if (appConfig.isEnabled(AlreadySubscribedCheck)) {
      EitherT(mandationStatusConnector.getMandationStatus(vatNumber)) transform {
        case Right(NonMTDfB | NonDigital) | Left(VatNumberNotFound) => Right(NotSubscribed)
        case Right(MTDfBMandated | MTDfBVoluntary) => Left(AlreadySubscribed)
        case _ => Left(GetVatCustomerInformationFailure)
      }
    } else {
      EitherT.pure[Future, VatNumberEligibilityFailure](NotSubscribed)
    }
  }

  private def getEligibilityStatus(vatNumber: String
                                  )(implicit hc: HeaderCarrier, request: Request[_]): EitherT[Future, VatNumberEligibilityFailure, VatNumberEligible.type] = {
    EitherT(knownFactsAndControlListInformationConnector.getKnownFactsAndControlListInformation(vatNumber)) transform {
      case Right(KnownFactsAndControlListInformation(businessPostcode, vatRegistrationDate, controlList)) =>
        controlList.validate(appConfig.eligibilityConfig) match {
          case Right(Migratable) =>
            auditService.audit(ControlListAuditModel(
              vatNumber = vatNumber,
              isSuccess = true
            ))
            Right(VatNumberEligible)
          case Right(NonMigratable(reasons)) =>
            auditService.audit(ControlListAuditModel(
              vatNumber = vatNumber,
              isSuccess = true,
              nonMigratableReasons = reasons.map(_.toString)
            ))
            Right(VatNumberEligible)
          case Left(Ineligible(reasons)) =>
            auditService.audit(ControlListAuditModel(
              vatNumber = vatNumber,
              isSuccess = false,
              failureReasons = reasons.map(_.toString)
            ))
            Left(VatNumberIneligible)
        }
      case Left(KnownFactsInvalidVatNumber) =>
        auditService.audit(ControlListAuditModel(
          vatNumber = vatNumber,
          isSuccess = false,
          failureReasons = Seq(invalidVatNumber)
        ))
        Left(InvalidVatNumber)
      case Left(ControlListInformationVatNumberNotFound) =>
        auditService.audit(ControlListAuditModel(
          vatNumber = vatNumber,
          isSuccess = false,
          failureReasons = Seq(vatNumberNotFound)
        ))
        Left(VatNumberNotFound)
      case _ =>
        auditService.audit(ControlListAuditModel(
          vatNumber = vatNumber,
          isSuccess = false,
          failureReasons = Seq(unexpectedError)
        ))
        Left(KnownFactsAndControlListFailure)
    }
  }
}

object VatNumberEligibilityService {

  type VatNumberEligibility = Either[VatNumberEligibilityFailure, VatNumberEligible.type]

  case object VatNumberEligible

  case object NotSubscribed

  sealed trait VatNumberEligibilityFailure

  case object AlreadySubscribed extends VatNumberEligibilityFailure

  case object VatNumberIneligible extends VatNumberEligibilityFailure

  case object VatNumberNotFound extends VatNumberEligibilityFailure

  case object InvalidVatNumber extends VatNumberEligibilityFailure

  case object GetVatCustomerInformationFailure extends VatNumberEligibilityFailure

  case object KnownFactsAndControlListFailure extends VatNumberEligibilityFailure

}
