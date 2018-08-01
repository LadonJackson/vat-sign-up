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

import cats.data._
import cats.implicits._
import play.api.mvc.Request
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.http.{BadGatewayException, HeaderCarrier}
import uk.gov.hmrc.vatsignup.config.AppConfig
import uk.gov.hmrc.vatsignup.config.featureswitch.AlreadySubscribedCheck
import uk.gov.hmrc.vatsignup.connectors.{AgentClientRelationshipsConnector, KnownFactsAndControlListInformationConnector, MandationStatusConnector}
import uk.gov.hmrc.vatsignup.httpparsers.GetMandationStatusHttpParser.VatNumberNotFound
import uk.gov.hmrc.vatsignup.httpparsers.KnownFactsAndControlListInformationHttpParser._
import uk.gov.hmrc.vatsignup.models._
import uk.gov.hmrc.vatsignup.models.controllist.{Migratable, NonMigratable}
import uk.gov.hmrc.vatsignup.models.monitoring.AgentClientRelationshipAuditing.AgentClientRelationshipAuditModel
import uk.gov.hmrc.vatsignup.models.monitoring.ControlListAuditing._
import uk.gov.hmrc.vatsignup.models.monitoring.KnownFactsAuditing.KnownFactsAuditModel
import uk.gov.hmrc.vatsignup.repositories.SubscriptionRequestRepository
import uk.gov.hmrc.vatsignup.services.StoreVatNumberService._
import uk.gov.hmrc.vatsignup.services.monitoring.AuditService
import uk.gov.hmrc.vatsignup.utils.EnrolmentUtils._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class StoreVatNumberService @Inject()(subscriptionRequestRepository: SubscriptionRequestRepository,
                                      agentClientRelationshipsConnector: AgentClientRelationshipsConnector,
                                      mandationStatusConnector: MandationStatusConnector,
                                      knownFactsAndControlListInformationConnector: KnownFactsAndControlListInformationConnector,
                                      auditService: AuditService,
                                      appConfig: AppConfig
                                     )(implicit ec: ExecutionContext) {

  def storeVatNumber(vatNumber: String,
                     enrolments: Enrolments,
                     businessPostcode: Option[String],
                     vatRegistrationDate: Option[String]
                    )(implicit hc: HeaderCarrier, request: Request[_]): Future[Either[StoreVatNumberFailure, StoreVatNumberSuccess.type]] = {
    for {
      _ <- checkUserAuthority(vatNumber, enrolments, businessPostcode, vatRegistrationDate)
      _ <- checkExistingVatSubscription(vatNumber)
      migrtableType <- checkEligibility(vatNumber, enrolments, businessPostcode, vatRegistrationDate)
      isMigratable = migrtableType match {
        case MigratableRecord => true
        case NonMigratableRecord => false
      }
      _ <- insertVatNumber(vatNumber, isMigratable)
    } yield StoreVatNumberSuccess
  }.value

  private def checkUserAuthority(vatNumber: String,
                                 enrolments: Enrolments,
                                 businessPostcode: Option[String],
                                 vatRegistrationDate: Option[String]
                                )(implicit request: Request[_], hc: HeaderCarrier): EitherT[Future, StoreVatNumberFailure, Any] = {
    EitherT((enrolments.vatNumber, enrolments.agentReferenceNumber) match {
      case (Some(vatNumberFromEnrolment), _) =>
        if (vatNumber == vatNumberFromEnrolment) Future.successful(Right(UserHasMatchingEnrolment))
        else Future.successful(Left(DoesNotMatchEnrolment))
      case (_, None) if businessPostcode.isDefined && vatRegistrationDate.isDefined =>
        Future.successful(Right(UserHasKnownFacts))
      case (_, Some(agentReferenceNumber)) =>
        checkAgentClientRelationship(vatNumber, agentReferenceNumber)
      case _ =>
        Future.successful(Left(InsufficientEnrolments))
    })
  }

  private def checkAgentClientRelationship(vatNumber: String,
                                           agentReferenceNumber: String
                                          )(implicit hc: HeaderCarrier,
                                            request: Request[_]) = {
    agentClientRelationshipsConnector.checkAgentClientRelationship(agentReferenceNumber, vatNumber) map {
      case Right(HaveRelationshipResponse) =>
        auditService.audit(AgentClientRelationshipAuditModel(vatNumber, agentReferenceNumber, haveRelationship = true))
        Right(HaveRelationshipResponse)
      case Right(NoRelationshipResponse) =>
        auditService.audit(AgentClientRelationshipAuditModel(vatNumber, agentReferenceNumber, haveRelationship = false))
        Left(RelationshipNotFound)
      case _ =>
        Left(AgentServicesConnectionFailure)
    }
  }

  // todo move this to service???? depends on how to union the types of failures
  private def checkEligibility(vatNumber: String,
                               enrolments: Enrolments,
                               optBusinessPostcode: Option[String],
                               optVatRegistrationDate: Option[String]
                              )(implicit hc: HeaderCarrier, request: Request[_]): EitherT[Future, StoreVatNumberFailure, EligibilitySuccess] =
    EitherT[Future, KnownFactsAndControlListInformationFailure, KnownFactsAndControlListInformation](
      knownFactsAndControlListInformationConnector.getKnownFactsAndControlListInformation(vatNumber: String)
    ).transform[StoreVatNumberFailure, EligibilitySuccess] {
      case Right(KnownFactsAndControlListInformation(businessPostcode, vatRegistrationDate, controlListInformation)) =>
        controlListInformation.validate(appConfig.eligibilityConfig) match {
          case Right(eligible) =>
            val eligibilitySuccessType = eligible match {
              case Migratable =>
                auditService.audit(ControlListAuditModel(
                  vatNumber = vatNumber,
                  isSuccess = true
                ))
                MigratableRecord
              case NonMigratable(reasons) =>
                auditService.audit(ControlListAuditModel(
                  vatNumber = vatNumber,
                  isSuccess = true,
                  nonMigratableReasons = reasons.map(_.toString)
                ))
                NonMigratableRecord
            }
            (optBusinessPostcode, optVatRegistrationDate) match {
              case (Some(enteredPostCode), Some(enteredVatRegistrationDate)) =>
                val knownFactsMatched =
                  (enteredPostCode filterNot (_.isWhitespace)).equalsIgnoreCase(businessPostcode filterNot (_.isWhitespace)) &&
                    (enteredVatRegistrationDate == vatRegistrationDate)
                auditService.audit(KnownFactsAuditModel(
                  vatNumber = vatNumber,
                  enteredPostCode = enteredPostCode,
                  enteredVatRegistrationDate = enteredVatRegistrationDate,
                  desPostCode = businessPostcode,
                  desVatRegistrationDate = vatRegistrationDate,
                  matched = knownFactsMatched
                ))
                if (knownFactsMatched) Right[StoreVatNumberFailure, EligibilitySuccess](eligibilitySuccessType)
                else Left[StoreVatNumberFailure, EligibilitySuccess](KnownFactsMismatch)
              case _ =>
                Right[StoreVatNumberFailure, EligibilitySuccess](eligibilitySuccessType)
            }
          case Left(ineligibilityReasons) =>
            auditService.audit(ControlListAuditModel(
              vatNumber = vatNumber,
              isSuccess = false,
              failureReasons = ineligibilityReasons.reasons.toList.map(_.toString)
            ))
            Left(Ineligible)
        }
      case Left(ControlListInformationVatNumberNotFound) =>
        auditService.audit(ControlListAuditModel(
          vatNumber = vatNumber,
          isSuccess = false,
          failureReasons = Seq(vatNumberNotFound)
        ))
        Left(VatNotFound)
      case Left(KnownFactsInvalidVatNumber) =>
        auditService.audit(ControlListAuditModel(
          vatNumber = vatNumber,
          isSuccess = false,
          failureReasons = Seq(invalidVatNumber)
        ))
        Left(VatInvalid)
      case Left(err: UnexpectedKnownFactsAndControlListInformationFailure) =>
        auditService.audit(ControlListAuditModel(
          vatNumber = vatNumber,
          isSuccess = false,
          failureReasons = Seq(unexpectedError)
        ))
        throw new BadGatewayException(s"Known facts & control list returned ${err.status} ${err.body}")
    }

  private def checkExistingVatSubscription(vatNumber: String
                                          )(implicit hc: HeaderCarrier): EitherT[Future, StoreVatNumberFailure, NotSubscribed.type] =
    if (appConfig.isEnabled(AlreadySubscribedCheck)) {
      EitherT(mandationStatusConnector.getMandationStatus(vatNumber) map {
        case Right(NonMTDfB | NonDigital) | Left(VatNumberNotFound) => Right(NotSubscribed)
        case Right(MTDfBMandated | MTDfBVoluntary) => Left(AlreadySubscribed)
        case _ => Left(VatSubscriptionConnectionFailure)
      })
    } else {
      EitherT.pure(NotSubscribed)
    }

  private def insertVatNumber(vatNumber: String,
                              isMigratable: Boolean
                             )(implicit hc: HeaderCarrier): EitherT[Future, StoreVatNumberFailure, StoreVatNumberSuccess.type] =
    EitherT(subscriptionRequestRepository.upsertVatNumber(vatNumber, isMigratable) map {
      _ => Right(StoreVatNumberSuccess)
    } recover {
      case _ => Left(VatNumberDatabaseFailure)
    })
}

object StoreVatNumberService {

  case object StoreVatNumberSuccess

  case object NotSubscribed

  case object UserHasMatchingEnrolment

  case object UserHasKnownFacts

  sealed trait EligibilitySuccess

  case object MigratableRecord extends EligibilitySuccess

  case object NonMigratableRecord extends EligibilitySuccess

  sealed trait StoreVatNumberFailure

  case object AlreadySubscribed extends StoreVatNumberFailure

  case object DoesNotMatchEnrolment extends StoreVatNumberFailure

  case object InsufficientEnrolments extends StoreVatNumberFailure

  case object KnownFactsMismatch extends StoreVatNumberFailure

  case object Ineligible extends StoreVatNumberFailure

  case object VatNotFound extends StoreVatNumberFailure

  case object VatInvalid extends StoreVatNumberFailure

  case object RelationshipNotFound extends StoreVatNumberFailure

  case object AgentServicesConnectionFailure extends StoreVatNumberFailure

  case object VatSubscriptionConnectionFailure extends StoreVatNumberFailure

  case object VatNumberDatabaseFailure extends StoreVatNumberFailure

}


