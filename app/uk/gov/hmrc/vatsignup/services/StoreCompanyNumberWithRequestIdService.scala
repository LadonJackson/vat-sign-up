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

import java.util.NoSuchElementException

import cats.data.EitherT
import cats.implicits._
import javax.inject.{Inject, Singleton}
import play.api.mvc.Request
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.vatsignup.repositories.UnconfirmedSubscriptionRequestRepository
import uk.gov.hmrc.vatsignup.services.CompanyMatchService.GetCtReferenceFailure

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class StoreCompanyNumberWithRequestIdService @Inject()(subscriptionRequestRepository: UnconfirmedSubscriptionRequestRepository,
                                                       companyMatchService: CompanyMatchService
                                                      )(implicit ec: ExecutionContext) {

  import StoreCompanyNumberWithRequestIdService._

  def storeCompanyNumber(requestId: String, companyNumber: String): Future[StoreCompanyResponse[StoreCompanyNumberSuccess.type]] =
    upsertCompanyNumber(requestId, companyNumber)

  def storeCompanyNumber(requestId: String,
                         companyNumber: String,
                         ctReference: String
                        )(implicit hc: HeaderCarrier, request: Request[_]): Future[StoreCompanyResponse[StoreCompanyNumberSuccess.type]] = {
    for {
      _ <- EitherT(companyMatchService.checkCompanyMatch(companyNumber, ctReference)) leftMap {
        case CompanyMatchService.CtReferenceMismatch => StoreCompanyNumberWithRequestIdService.CtReferenceMismatch
        case GetCtReferenceFailure => MatchCtReferenceFailure
      }
      _ <- EitherT(upsertCompanyNumber(requestId, companyNumber))
      _ <- EitherT(upsertCtReference(requestId, ctReference))
    } yield StoreCompanyNumberSuccess
  }.value

  private def upsertCompanyNumber(requestId: String, companyNumber: String): Future[StoreCompanyResponse[StoreCompanyNumberSuccess.type]] =
    subscriptionRequestRepository.upsertCompanyNumber(requestId, companyNumber) map {
      _ => Right(StoreCompanyNumberSuccess)
    } recover {
      case e: NoSuchElementException => Left(DatabaseFailureNoRequestId)
      case _ => Left(CompanyNumberDatabaseFailure)
    }

  private def upsertCtReference(requestId: String, ctReference: String): Future[StoreCompanyResponse[StoreCtReferenceSuccess.type]] =
    subscriptionRequestRepository.upsertCtReference(requestId, ctReference) map {
      _ => Right(StoreCtReferenceSuccess)
    } recover {
      case e: NoSuchElementException => Left(DatabaseFailureNoRequestId)
      case _ => Left(CtReferenceDatabaseFailure)
    }

}

object StoreCompanyNumberWithRequestIdService {

  type StoreCompanyResponse[A] = Either[StoreCompanyNumberFailure, A]

  case object StoreCompanyNumberSuccess

  case object StoreCtReferenceSuccess

  sealed trait StoreCompanyNumberFailure

  case object CompanyNumberDatabaseFailure extends StoreCompanyNumberFailure

  case object CtReferenceDatabaseFailure extends StoreCompanyNumberFailure

  case object DatabaseFailureNoRequestId extends StoreCompanyNumberFailure

  case object CtReferenceMismatch extends StoreCompanyNumberFailure

  case object MatchCtReferenceFailure extends StoreCompanyNumberFailure

}




