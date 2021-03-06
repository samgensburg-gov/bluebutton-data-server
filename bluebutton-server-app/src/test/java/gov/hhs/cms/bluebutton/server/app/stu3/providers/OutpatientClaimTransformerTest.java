package gov.hhs.cms.bluebutton.server.app.stu3.providers;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.hl7.fhir.dstu3.model.ExplanationOfBenefit;
import org.hl7.fhir.dstu3.model.ExplanationOfBenefit.ItemComponent;
import org.hl7.fhir.exceptions.FHIRException;
import org.junit.Assert;
import org.junit.Test;

import gov.hhs.cms.bluebutton.data.model.rif.OutpatientClaim;
import gov.hhs.cms.bluebutton.data.model.rif.OutpatientClaimLine;
import gov.hhs.cms.bluebutton.data.model.rif.samples.StaticRifResource;
import gov.hhs.cms.bluebutton.data.model.rif.samples.StaticRifResourceGroup;
import gov.hhs.cms.bluebutton.server.app.ServerTestUtils;

/**
 * Unit tests for {@link OutpatientClaimTransformer}.
 */
public final class OutpatientClaimTransformerTest {
	/**
	 * Verifies that {@link OutpatientClaimTransformer#transform(Object)} works
	 * as expected when run against the
	 * {@link StaticRifResource#SAMPLE_A_OUTPATIENT} {@link OutpatientClaim}.
	 * 
	 * @throws FHIRException
	 *             (indicates test failure)
	 */
	@Test
	public void transformSampleARecord() throws FHIRException {
		List<Object> parsedRecords = ServerTestUtils
				.parseData(Arrays.asList(StaticRifResourceGroup.SAMPLE_A.getResources()));
		OutpatientClaim claim = parsedRecords.stream().filter(r -> r instanceof OutpatientClaim)
				.map(r -> (OutpatientClaim) r).findFirst().get();

		ExplanationOfBenefit eob = OutpatientClaimTransformer.transform(claim);
		assertMatches(claim, eob);
	}

	/**
	 * Verifies that the {@link ExplanationOfBenefit} "looks like" it should, if
	 * it were produced from the specified {@link OutpatientClaim}.
	 * 
	 * @param claim
	 *            the {@link OutpatientClaim} that the
	 *            {@link ExplanationOfBenefit} was generated from
	 * @param eob
	 *            the {@link ExplanationOfBenefit} that was generated from the
	 *            specified {@link OutpatientClaim}
	 * @throws FHIRException
	 *             (indicates test failure)
	 */
	static void assertMatches(OutpatientClaim claim, ExplanationOfBenefit eob) throws FHIRException {
		TransformerTestUtils.assertNoEncodedOptionals(eob);

		Assert.assertEquals(TransformerUtils.buildEobId(ClaimType.OUTPATIENT, claim.getClaimId()),
				eob.getIdElement().getIdPart());

		TransformerTestUtils.assertIdentifierExists(TransformerConstants.CODING_SYSTEM_CCW_CLAIM_ID, claim.getClaimId(),
				eob.getIdentifier());
		TransformerTestUtils.assertIdentifierExists(TransformerConstants.CODING_SYSTEM_CCW_CLAIM_GRP_ID,
				claim.getClaimGroupId().toPlainString(), eob.getIdentifier());
		Assert.assertEquals(TransformerUtils.referencePatient(claim.getBeneficiaryId()).getReference(),
				eob.getPatient().getReference());
		TransformerTestUtils.assertExtensionCodingEquals(eob.getType(),
				TransformerConstants.CODING_SYSTEM_CCW_RECORD_ID_CD,
				TransformerConstants.CODING_SYSTEM_CCW_RECORD_ID_CD, "" + claim.getNearLineRecordIdCode());
		TransformerTestUtils.assertHasCoding(TransformerConstants.CODING_SYSTEM_CCW_CLAIM_TYPE,
				claim.getClaimTypeCode(), eob.getType());
		Assert.assertEquals(
				TransformerUtils.referenceCoverage(claim.getBeneficiaryId(), MedicareSegment.PART_B).getReference(),
				eob.getInsurance().getCoverage().getReference());
		Assert.assertEquals("active", eob.getStatus().toCode());

		TransformerTestUtils.assertDateEquals(claim.getDateFrom(),
				eob.getBillablePeriod().getStartElement());
		TransformerTestUtils.assertDateEquals(claim.getDateThrough(),
				eob.getBillablePeriod().getEndElement());

		TransformerTestUtils.assertExtensionCodingEquals(eob.getBillablePeriod(),
				TransformerConstants.CODING_SYSTEM_QUERY_CD, TransformerConstants.CODING_SYSTEM_QUERY_CD,
				String.valueOf(claim.getClaimQueryCode()));
		TransformerTestUtils.assertExtensionCodingEquals(eob,
				TransformerConstants.CODING_SYSTEM_CCW_INP_PAYMENT_DENIAL_CD,
				TransformerConstants.CODING_SYSTEM_CCW_INP_PAYMENT_DENIAL_CD,
				claim.getClaimNonPaymentReasonCode().get());

		Assert.assertEquals(claim.getPaymentAmount(), eob.getPayment().getAmount().getValue());

		Assert.assertEquals(claim.getTotalChargeAmount(), eob.getTotalCost().getValue());

		TransformerTestUtils.assertBenefitBalanceEquals(TransformerConstants.BENEFIT_BALANCE_TYPE,
				TransformerConstants.CODED_ADJUDICATION_PRIMARY_PAYER_PAID_AMOUNT, claim.getPrimaryPayerPaidAmount(),
				eob.getBenefitBalanceFirstRep().getFinancial());
		TransformerTestUtils.assertBenefitBalanceEquals(TransformerConstants.BENEFIT_BALANCE_TYPE,
				TransformerConstants.CODING_NCH_BEN_PART_B_DED_AMT_URL, claim.getDeductibleAmount(),
				eob.getBenefitBalanceFirstRep().getFinancial());
		TransformerTestUtils.assertBenefitBalanceEquals(TransformerConstants.BENEFIT_BALANCE_TYPE,
				TransformerConstants.CODING_NCH_BENEFIT_BLOOD_DED_AMT_URL, claim.getBloodDeductibleLiabilityAmount(),
				eob.getBenefitBalanceFirstRep().getFinancial());
		TransformerTestUtils.assertBenefitBalanceEquals(TransformerConstants.BENEFIT_BALANCE_TYPE,
				TransformerConstants.CODING_NCH_PROFFESIONAL_CHARGE_URL, claim.getProfessionalComponentCharge(),
				eob.getBenefitBalanceFirstRep().getFinancial());
		TransformerTestUtils.assertBenefitBalanceEquals(TransformerConstants.BENEFIT_BALANCE_TYPE,
				TransformerConstants.CODING_NCH_BEN_PART_B_COINSUR_AMT_URL, claim.getCoinsuranceAmount(),
				eob.getBenefitBalanceFirstRep().getFinancial());
		TransformerTestUtils.assertBenefitBalanceEquals(TransformerConstants.BENEFIT_BALANCE_TYPE,
				TransformerConstants.CODED_ADJUDICATION_PAYMENT_B, claim.getProviderPaymentAmount(),
				eob.getBenefitBalanceFirstRep().getFinancial());
		TransformerTestUtils.assertBenefitBalanceEquals(TransformerConstants.BENEFIT_BALANCE_TYPE,
				TransformerConstants.CODING_CLAIM_OUTPAT_BEN__PAYMENT_AMT_URL, claim.getBeneficiaryPaymentAmount(),
				eob.getBenefitBalanceFirstRep().getFinancial());

		TransformerTestUtils.assertReferenceIdentifierEquals(TransformerConstants.CODING_SYSTEM_NPI_US,
				claim.getOrganizationNpi().get(), eob.getOrganization());
		TransformerTestUtils.assertReferenceIdentifierEquals(TransformerConstants.CODING_SYSTEM_NPI_US,
				claim.getOrganizationNpi().get(), eob.getFacility());

		TransformerTestUtils.assertExtensionCodingEquals(eob.getFacility(),
				TransformerConstants.CODING_SYSTEM_CCW_FACILITY_TYPE_CD,
				TransformerConstants.CODING_SYSTEM_CCW_FACILITY_TYPE_CD,
				String.valueOf(claim.getClaimFacilityTypeCode()));

		TransformerTestUtils.assertExtensionCodingEquals(eob.getType(),
				TransformerConstants.CODING_SYSTEM_CCW_CLAIM_SERVICE_CLASSIFICATION_TYPE_CD,
				TransformerConstants.CODING_SYSTEM_CCW_CLAIM_SERVICE_CLASSIFICATION_TYPE_CD,
				String.valueOf(claim.getClaimServiceClassificationTypeCode()));

		TransformerTestUtils.assertCareTeamEquals(claim.getAttendingPhysicianNpi().get(),
				TransformerConstants.CARE_TEAM_ROLE_PRIMARY, eob);
		TransformerTestUtils.assertCareTeamEquals(claim.getOperatingPhysicianNpi().get(),
				TransformerConstants.CARE_TEAM_ROLE_ASSISTING, eob);

		Assert.assertEquals(6, eob.getDiagnosis().size());

		Assert.assertEquals(1, eob.getProcedure().size());
		CCWProcedure ccwProcedure = new CCWProcedure(claim.getProcedure1Code(), claim.getProcedure1CodeVersion(),
				claim.getProcedure1Date().get());
		TransformerTestUtils.assertHasCoding(ccwProcedure.getFhirSystem().toString(), claim.getProcedure1Code().get(),
				eob.getProcedure().get(0).getProcedureCodeableConcept());
		Assert.assertEquals(Date.from(claim.getProcedure1Date().get().atStartOfDay(ZoneId.systemDefault()).toInstant()),
				eob.getProcedure().get(0).getDate());

		Assert.assertEquals(1, eob.getItem().size());
		ItemComponent eobItem0 = eob.getItem().get(0);
		OutpatientClaimLine claimLine1 = claim.getLines().get(0);
		Assert.assertEquals(new Integer(claimLine1.getLineNumber().intValue()),
				new Integer(eobItem0.getSequence()));

		TransformerTestUtils.assertExtensionCodingEquals(eobItem0,
				TransformerConstants.CODING_SYSTEM_FHIR_EOB_ITEM_TYPE,
				TransformerConstants.CODING_SYSTEM_FHIR_EOB_ITEM_TYPE,
				(TransformerConstants.CODED_EOB_ITEM_TYPE_CLINICAL_SERVICES_AND_PRODUCTS));

		Assert.assertEquals(claim.getProviderStateCode(), eobItem0.getLocationAddress().getState());

		TransformerTestUtils.assertHasCoding(TransformerConstants.CODING_SYSTEM_NDC,
				claimLine1.getNationalDrugCode().get(),
				eobItem0.getService());

		TransformerTestUtils.assertAdjudicationReasonEquals(TransformerConstants.CODED_ADJUDICATION_1ST_ANSI_CD,
				TransformerConstants.CODING_SYSTEM_ADJUDICATION_CMS, claimLine1.getRevCntr1stAnsiCd().get(),
				eobItem0.getAdjudication());
		TransformerTestUtils.assertAdjudicationReasonEquals(TransformerConstants.CODED_ADJUDICATION_2ND_ANSI_CD,
				TransformerConstants.CODING_SYSTEM_ADJUDICATION_CMS, claimLine1.getRevCntr2ndAnsiCd().get(),
				eobItem0.getAdjudication());
		TransformerTestUtils.assertAdjudicationNotPresent(TransformerConstants.CODED_ADJUDICATION_3RD_ANSI_CD,
				eobItem0.getAdjudication());
		TransformerTestUtils.assertAdjudicationNotPresent(TransformerConstants.CODED_ADJUDICATION_4TH_ANSI_CD,
				eobItem0.getAdjudication());

		TransformerTestUtils.assertHasCoding(TransformerConstants.CODING_SYSTEM_REVENUE_CENTER,
				claimLine1.getRevenueCenterCode(), eobItem0.getRevenue());
		TransformerTestUtils.assertHasCoding(TransformerConstants.CODING_SYSTEM_HCPCS, claimLine1.getHcpcsCode().get(),
				eobItem0.getModifier().get(0));
		TransformerTestUtils.assertHasCoding(TransformerConstants.HCPCS_INITIAL_MODIFIER_CODE1,
				claimLine1.getHcpcsInitialModifierCode().get(), eobItem0.getModifier().get(1));
		Assert.assertFalse(claimLine1.getHcpcsSecondModifierCode().isPresent());

		TransformerTestUtils.assertAdjudicationEquals(TransformerConstants.CODED_ADJUDICATION_RATE_AMOUNT,
				claimLine1.getRateAmount(), eobItem0.getAdjudication());
		TransformerTestUtils.assertAdjudicationEquals(TransformerConstants.CODED_ADJUDICATION_BLOOD_DEDUCTIBLE,
				claimLine1.getBloodDeductibleAmount(), eobItem0.getAdjudication());
		TransformerTestUtils.assertAdjudicationEquals(TransformerConstants.CODED_ADJUDICATION_CASH_DEDUCTIBLE,
				claimLine1.getCashDeductibleAmount(), eobItem0.getAdjudication());
		TransformerTestUtils.assertAdjudicationEquals(
				TransformerConstants.CODED_ADJUDICATION_WAGE_ADJ_COINSURANCE_AMOUNT,
				claimLine1.getWageAdjustedCoinsuranceAmount(), eobItem0.getAdjudication());
		TransformerTestUtils.assertAdjudicationEquals(
				TransformerConstants.CODED_ADJUDICATION_REDUCED_COINSURANCE_AMOUNT,
				claimLine1.getReducedCoinsuranceAmount(),
				eobItem0.getAdjudication());
		TransformerTestUtils.assertAdjudicationEquals(TransformerConstants.CODED_ADJUDICATION_1ST_MSP_AMOUNT,
				claimLine1.getFirstMspPaidAmount(), eobItem0.getAdjudication());
		TransformerTestUtils.assertAdjudicationEquals(TransformerConstants.CODED_ADJUDICATION_2ND_MSP_AMOUNT,
				claimLine1.getSecondMspPaidAmount(), eobItem0.getAdjudication());
		TransformerTestUtils.assertAdjudicationEquals(TransformerConstants.CODED_ADJUDICATION_PROVIDER_PAYMENT_AMOUNT,
				claimLine1.getProviderPaymentAmount(), eobItem0.getAdjudication());
		TransformerTestUtils.assertAdjudicationEquals(
				TransformerConstants.CODED_ADJUDICATION_BENEFICIARY_PAYMENT_AMOUNT,
				claimLine1.getBenficiaryPaymentAmount(),
				eobItem0.getAdjudication());
		TransformerTestUtils.assertAdjudicationEquals(
				TransformerConstants.CODED_ADJUDICATION_PATIENT_RESPONSIBILITY_AMOUNT,
				claimLine1.getPatientResponsibilityAmount(), eobItem0.getAdjudication());
		TransformerTestUtils.assertAdjudicationEquals(TransformerConstants.CODED_ADJUDICATION_PAYMENT,
				claimLine1.getPaymentAmount(), eobItem0.getAdjudication());
		TransformerTestUtils.assertAdjudicationEquals(TransformerConstants.CODED_ADJUDICATION_TOTAL_CHARGE_AMOUNT,
				claimLine1.getTotalChargeAmount(), eobItem0.getAdjudication());
		TransformerTestUtils.assertAdjudicationEquals(TransformerConstants.CODED_ADJUDICATION_NONCOVERED_CHARGE,
				claimLine1.getNonCoveredChargeAmount(), eobItem0.getAdjudication());

		TransformerTestUtils.assertCareTeamEquals(claimLine1.getRevenueCenterRenderingPhysicianNPI().get(),
				TransformerConstants.CARE_TEAM_ROLE_PRIMARY, eob);
	}

}
