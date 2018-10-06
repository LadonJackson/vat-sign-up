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

package uk.gov.hmrc.vatsignup.config.featureswitch

import FeatureSwitch._

sealed trait FeatureSwitch {
  val name: String
  val displayName: String
}

object FeatureSwitch {
  val prefix = "feature-switch"

  val switches: Set[FeatureSwitch] = Set(
    EmailNotification,
    StubDESFeature,
    StubAgentServicesFeature,
    ClaimSubscription,
    HybridSolution,
    EtmpEntityType
  )

  def apply(str: String): FeatureSwitch =
    switches find (_.name == str) match {
      case Some(switch) => switch
      case None => throw new IllegalArgumentException("Invalid feature switch: " + str)
    }

  def apply(setting: FeatureSwitchSetting): FeatureSwitch =
    switches find (_.displayName == setting.feature) match {
      case Some(switch) => switch
      case None => throw new IllegalArgumentException("Invalid feature switch: " + setting.feature)
    }
}

object StubDESFeature extends FeatureSwitch {
  val displayName = s"Use stub for DES connection"
  val name = s"$prefix.stub-des"
}

object StubAgentServicesFeature extends FeatureSwitch {
  val displayName = s"Use stub for Agent Services connection"
  val name = s"$prefix.stub-agent-services"
}

object EmailNotification extends FeatureSwitch {
  val displayName = s"Send notification e-mail on tax enrolments callback"
  val name = s"$prefix.email-notification"
}

object ClaimSubscription extends FeatureSwitch {
  val displayName = s"Allow users to claim their subscription"
  val name = s"$prefix.claim-subscription"
}

object HybridSolution extends FeatureSwitch {
  val displayName = s"Enable the hybrid solution"
  val name = s"$prefix.hybrid-solution"
}

object EtmpEntityType extends FeatureSwitch {
  val displayName = s"Send the Entity Type to ETMP on the Registration API (1369)"
  val name = s"$prefix.etmp-entity-type"
}
