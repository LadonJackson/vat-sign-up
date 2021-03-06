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

import java.util.UUID

import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.vatsignup.helpers._
import uk.gov.hmrc.vatsignup.helpers.servicemocks.AuthStub._

class GetRequestIdControllerISpec extends ComponentSpecBase with CustomMatchers with TestSubmissionRequestRepository {

  "POST /sign-up-request/" when {
    "request id is successfully returned from mongo" should {
      "Return 200" in {
        val testInternalId: String = UUID.randomUUID().toString

        stubAuth(OK, successfulAuthResponse(internalId = testInternalId))

        val res = await(post(s"/sign-up-request/")(Json.obj()))

        res should have(
          httpStatus(OK)
        )
      }
    }
  }

}
