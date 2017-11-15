/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.submit.step;

import org.atteo.evo.inflector.English;
import org.dspace.app.rest.model.BitstreamRest;
import org.dspace.app.rest.model.step.DataLicense;
import org.dspace.app.rest.submit.AbstractRestProcessingStep;
import org.dspace.app.rest.submit.factory.PatchOperationFactory;
import org.dspace.app.rest.submit.factory.impl.PatchOperation;
import org.dspace.app.util.SubmissionStepConfig;
import org.dspace.content.Bitstream;
import org.dspace.content.WorkspaceItem;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.services.model.Request;

/**
 * License step for DSpace Spring Rest. Expose the license information about the in progress submission.
 * 
 * @author Luigi Andrea Pascarelli (luigiandrea.pascarelli at 4science.it)
 *
 */
public class LicenseStep extends org.dspace.submit.step.LicenseStep implements AbstractRestProcessingStep {

	private static final String DCTERMS_RIGHTSDATE = "dcterms.accessRights";

	@Override
	public DataLicense getData(WorkspaceItem obj, SubmissionStepConfig config) throws Exception {
		DataLicense result = new DataLicense();
		Bitstream bitstream = bitstreamService.getBitstreamByName(obj.getItem(), Constants.LICENSE_BUNDLE_NAME, Constants.LICENSE_BITSTREAM_NAME);
		if(bitstream!=null) {
			String acceptanceDate = bitstreamService.getMetadata(bitstream, DCTERMS_RIGHTSDATE);
			result.setAcceptanceDate(acceptanceDate);
			result.setUrl(configurationService.getProperty("dspace.url")+"/api/"+BitstreamRest.CATEGORY +"/"+ English.plural(BitstreamRest.NAME) + "/" + bitstream.getID() + "/content");
		}
		return result;
	}

	@Override
	public void doPatchProcessing(Context context, Request currentRequest, WorkspaceItem source, String operation,
			String path, Object value) throws Exception {
        
		if("acceptanceDate".equals(path)) {
			
			PatchOperation<String> patchOperation = new PatchOperationFactory().instanceOf(path, operation);
			patchOperation.perform(context, currentRequest, source, path, value);
				
		}
	}
}