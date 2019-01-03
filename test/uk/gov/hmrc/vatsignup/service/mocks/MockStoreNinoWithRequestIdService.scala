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

package uk.gov.hmrc.vatsignup.service.mocks

import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Suite}
import play.api.mvc.Request
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.vatsignup.models.{NinoSource, UserDetailsModel}
import uk.gov.hmrc.vatsignup.services.StoreNinoWithRequestIdService
import uk.gov.hmrc.vatsignup.services.StoreNinoWithRequestIdService._

import scala.concurrent.Future

trait MockStoreNinoWithRequestIdService extends MockitoSugar with BeforeAndAfterEach {
  self: Suite =>

  val mockStoreNinoWithRequestIdService: StoreNinoWithRequestIdService = mock[StoreNinoWithRequestIdService]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockStoreNinoWithRequestIdService)
  }

  def mockStoreNino(requestId: String,
                    userDetails: UserDetailsModel,
                    enrolments: Enrolments,
                    ninoSource: NinoSource
                   )(response: Future[Either[StoreNinoFailure, StoreNinoSuccess.type]]): Unit = {
    when(mockStoreNinoWithRequestIdService.storeNino(
      ArgumentMatchers.eq(requestId),
      ArgumentMatchers.eq(userDetails),
      ArgumentMatchers.any[Enrolments],
      ArgumentMatchers.eq(ninoSource)
    )(ArgumentMatchers.any[HeaderCarrier],
      ArgumentMatchers.any[Request[_]])) thenReturn response
  }

}
