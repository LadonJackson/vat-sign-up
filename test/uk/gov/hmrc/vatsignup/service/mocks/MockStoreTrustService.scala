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
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.vatsignup.services.StoreTrustService
import uk.gov.hmrc.vatsignup.services.StoreTrustService.{StoreTrustFailure, StoreTrustSuccess}

import scala.concurrent.Future

trait MockStoreTrustService extends MockitoSugar with BeforeAndAfterEach {
  self: Suite =>

  val mockStoreTrustService: StoreTrustService = mock[StoreTrustService]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockStoreTrustService)
  }

  def mockStoreTrust(vatNumber: String)(response: Future[Either[StoreTrustFailure, StoreTrustSuccess.type]]): Unit = {
    when(mockStoreTrustService.storeTrust(
      ArgumentMatchers.eq(vatNumber)
    )(ArgumentMatchers.any[HeaderCarrier])) thenReturn response
  }

}
