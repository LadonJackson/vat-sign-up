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

package uk.gov.hmrc.vatsignup.config

import javax.inject.{Inject, Singleton}

import play.api.Mode.Mode
import play.api.{Configuration, Environment}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.vatsignup.config.featureswitch.{FeatureSwitch, FeatureSwitching}

@Singleton
class AppConfig @Inject()(val runModeConfiguration: Configuration, environment: Environment) extends ServicesConfig with FeatureSwitching {
  override protected def mode: Mode = environment.mode

  private def loadConfig(key: String) = runModeConfiguration.getString(key).getOrElse(throw new Exception(s"Missing configuration key: $key"))

  lazy val agentClientRelationshipUrl: String =
    loadConfig(
      if (isEnabled(featureswitch.StubDESFeature)) "microservice.services.agent-client-relationships.stub-url"
      else "microservice.services.agent-client-relationships.url"
    ) + "/agent-client-relationships"

  lazy val taxEnrolmentsUrl: String = baseUrl("tax-enrolments") + "/tax-enrolments"

  def desUrl: String =
    loadConfig(
      if (isEnabled(featureswitch.StubDESFeature)) "microservice.services.des.stub-url"
      else "microservice.services.des.url"
    )

  lazy val desAuthorisationToken: String = s"Bearer ${loadConfig("microservice.services.des.authorisation-token")}"

  lazy val desEnvironmentHeader: (String, String) =
    "Environment" -> loadConfig("microservice.services.des.environment")

  def registerWithMultipleIdentifiersUrl: String = s"$desUrl/cross-regime/register/VATC"

  lazy val authenticatorUrl: String = baseUrl("authenticator")

  lazy val emailVerificationUrl: String = baseUrl("email-verification")

  def getEmailVerifiedUrl(email: String): String = s"$emailVerificationUrl/email-verification/verified-email-addresses/$email"

  lazy val verifyEmailUrl = s"$emailVerificationUrl/email-verification/verification-requests"

  lazy val frontendBaseUrl: String = loadConfig("microservice.services.vat-sign-up-frontend.url")

  lazy val principalVerifyEmailContinueUrl = s"$frontendBaseUrl/vat-through-software/sign-up/email-verified"

  lazy val delegatedVerifyEmailContinueUrl = s"$frontendBaseUrl/vat-through-software/sign-up/client/email-verified"

  lazy val agentVerifyEmailContinueUrl = s"$frontendBaseUrl/vat-through-software/sign-up/client/you-have-verified-your-email"

  lazy val identityVerificationFrontendUrl: String = baseUrl("identity-verification-frontend")

  lazy val timeToLiveSeconds: Long = loadConfig("mongodb.timeToLiveSeconds").toLong

  lazy val emailTimeToLiveSeconds: Long = loadConfig("mongodb.email.emailTimeToLiveSeconds").toLong

  def vatSubscriptionUrl: String =
    if (isEnabled(featureswitch.StubDESFeature)) desUrl
    else baseUrl("vat-subscription")

  lazy val baseUrl: String = baseUrl("base")

  lazy val emailUrl: String = baseUrl("email")

  lazy val sendEmailUrl: String = s"$emailUrl/hmrc/email"

  def mandationStatusUrl(vatNumber: String): String = s"$vatSubscriptionUrl/vat-subscription/$vatNumber/mandation-status"

  def getCtReferenceUrl(companyNumber: String): String = s"$desUrl/corporation-tax/identifiers/crn/$companyNumber"

  def allocateEnrolmentUrl(groupId: String, enrolmentKey: String): String = s"$taxEnrolmentsUrl/groups/$groupId/enrolments/$enrolmentKey"

  override def isEnabled(featureSwitch: FeatureSwitch): Boolean = super.isEnabled(featureSwitch)

  private def loadConfigFromEnvFirst(key:String):Option[String] = {
    sys.props.get(key) match {
      case r@Some(result) if result.nonEmpty => r
      case _ => runModeConfiguration.getString(key)
    }
  }
  private def loadEligibilityConfig(key: String): EligibilityConfiguration = {
    loadConfigFromEnvFirst(s"control-list.eligible.$key") match {
      case Some("Migratable") => MigratableParameter
      case Some("NonMigratable") => NonMigratableParameter
      case Some("Ineligible") => IneligibleParameter
      case _ => throw new Exception(s"Missing eligibility configuration key: $key")
    }
  }

  def eligibilityConfig: EligibilityConfig = EligibilityConfig(
    belowVatThresholdConfig = loadEligibilityConfig("below_vat_threshold"),
    annualStaggerConfig = loadEligibilityConfig("annual_stagger"),
    missingReturnsConfig = loadEligibilityConfig("missing_returns"),
    centralAssessmentsConfig = loadEligibilityConfig("central_assessments"),
    criminalInvestigationInhibitsConfig = loadEligibilityConfig("criminal_investigation_inhibits"),
    compliancePenaltiesOrSurchargesConfig = loadEligibilityConfig("compliance_penalties_or_surcharges"),
    insolvencyConfig = loadEligibilityConfig("insolvency"),
    deRegOrDeathConfig = loadEligibilityConfig("dereg_or_death"),
    debtMigrationConfig = loadEligibilityConfig("debt_migration"),
    directDebitConfig = loadEligibilityConfig("direct_debit"),
    euSalesOrPurchasesConfig = loadEligibilityConfig("eu_sales_or_purchases"),
    largeBusinessConfig = loadEligibilityConfig("large_business"),
    missingTraderConfig = loadEligibilityConfig("missing_trader"),
    monthlyStaggerConfig = loadEligibilityConfig("monthly_stagger"),
    nonStandardTaxPeriodConfig = loadEligibilityConfig("none_standard_tax_period"),
    overseasTraderConfig = loadEligibilityConfig("overseas_trader"),
    poaTraderConfig = loadEligibilityConfig("poa_trader"),
    stagger1Config = loadEligibilityConfig("stagger_1"),
    stagger2Config = loadEligibilityConfig("stagger_2"),
    stagger3Config = loadEligibilityConfig("stagger_3"),
    companyConfig = loadEligibilityConfig("company"),
    divisionConfig = loadEligibilityConfig("division"),
    groupConfig = loadEligibilityConfig("group"),
    partnershipConfig = loadEligibilityConfig("partnership"),
    publicCorporationConfig = loadEligibilityConfig("public_corporation"),
    soleTraderConfig = loadEligibilityConfig("sole_trader"),
    localAuthorityConfig = loadEligibilityConfig("local_authority"),
    nonProfitConfig = loadEligibilityConfig("non_profit"),
    dificTraderConfig = loadEligibilityConfig("dific_trader"),
    anythingUnderAppealConfig = loadEligibilityConfig("anything_under_appeal"),
    repaymentTraderConfig = loadEligibilityConfig("repayment_trader"),
    mossTraderConfig = loadEligibilityConfig("oss_trader")
  )

}
