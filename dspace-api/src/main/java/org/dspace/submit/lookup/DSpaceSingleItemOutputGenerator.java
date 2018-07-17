/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.submit.lookup;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import gr.ekt.bte.core.DataOutputSpec;
import gr.ekt.bte.core.OutputGenerator;
import gr.ekt.bte.core.Record;
import gr.ekt.bte.core.RecordSet;
import gr.ekt.bte.core.Value;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.dspace.app.util.DCInput;
import org.dspace.app.util.DCInputSet;
import org.dspace.app.util.DCInputsReader;
import org.dspace.app.util.DCInputsReaderException;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.AdditionalMetadataUpdateProcessPlugin;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataSchema;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.MetadataFieldService;
import org.dspace.content.service.MetadataSchemaService;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.core.Context;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Andrea Bollini
 * @author Kostas Stamatis
 * @author Luigi Andrea Pascarelli
 * @author Panagiotis Koutsourakis
 */
public class DSpaceSingleItemOutputGenerator implements OutputGenerator {

    private static Logger log = Logger.getLogger(DSpaceSingleItemOutputGenerator.class);

    protected Context context;

    protected String formName;

    protected Item item;

    Map<String, String> outputMap;

    protected List<String> extraMetadataToKeep;

    @Autowired(required = true)
    protected ItemService itemService;
    @Autowired(required = true)
    protected MetadataFieldService metadataFieldService;
    @Autowired(required = true)
    protected MetadataSchemaService metadataSchemaService;
    @Autowired(required = true)
    protected WorkspaceItemService workspaceItemService;

    @Override
    public List<String> generateOutput(RecordSet recordSet) {

        log.info("BTE OutputGenerator started. Records to output: " + recordSet.getRecords().size());

        // Printing debug message
        String totalString = "";
        for (Record record : recordSet.getRecords()) {
            totalString += SubmissionLookupUtils.getPrintableString(record) + "\n";
        }
        log.debug("Records to output:\n" + totalString);

        for (Record rec : recordSet.getRecords()) {
            merge(formName, getItem(), rec);

        }

        return new ArrayList<String>();
    }

    @Override
    public List<String> generateOutput(RecordSet records, DataOutputSpec spec) {
        return generateOutput(records);
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public void setFormName(String formName) {
        this.formName = formName;
    }

    public void setOutputMap(Map<String, String> outputMap) {
        // Reverse the key-value pairs
        this.outputMap = new HashMap<String, String>();
        for (String key : outputMap.keySet()) {
            this.outputMap.put(outputMap.get(key), key);
        }
    }

    public void setExtraMetadataToKeep(List<String> extraMetadataToKeep) {
        this.extraMetadataToKeep = extraMetadataToKeep;
    }

    // Methods
    public void merge(String formName, Item item, Record record) {
        try {
            Record itemLookup = record;

            Set<String> addedMetadata = new HashSet<String>();
            for (String field : itemLookup.getFields()) {
                String metadata = getMetadata(formName, itemLookup, field);
                if (StringUtils.isBlank(metadata)) {
                    continue;
                }
                if (itemService.getMetadataByMetadataString(item, metadata).size() == 0
                        || addedMetadata.contains(metadata)) {
                    addedMetadata.add(metadata);
                    String[] md = splitMetadata(metadata);
                    if (isValidMetadata(formName, md)) { // if in extra metadata or in the spefific form
                        List<Value> values = itemLookup.getValues(field);
                        if (values != null && values.size() > 0) {
                            if (isRepeatableMetadata(formName, md)) { // if metadata is repeatable in form
                                for (Value value : values) {
                                    String[] splitValue = splitValue(value.getAsString());
                                    if (splitValue[3] != null) {
                                        itemService.addMetadata(context, item, md[0], md[1], md[2], md[3],
                                                splitValue[0], splitValue[1], Integer.parseInt(splitValue[2]));
                                    } else {
                                        itemService.addMetadata(context, item, md[0], md[1], md[2], md[3],
                                                value.getAsString());
                                    }
                                }
                            } else {
                                String value = values.iterator().next().getAsString();
                                String[] splitValue = splitValue(value);
                                if (splitValue[3] != null) {
                                    itemService.addMetadata(context, item, md[0], md[1], md[2], md[3], splitValue[0],
                                            splitValue[1], Integer.parseInt(splitValue[2]));
                                } else {
                                    itemService.addMetadata(context, item, md[0], md[1], md[2], md[3], value);
                                }
                            }
                        }
                    }
                }
            }
            itemService.update(context, item);

            String providerName = "";
            List<Value> providerNames = itemLookup.getValues("provider_name_field");
            if (providerNames != null && providerNames.size() > 0) {
                providerName = providerNames.get(0).getAsString();
            }
            List<AdditionalMetadataUpdateProcessPlugin> additionalMetadataUpdateProcessPlugins =
                    (List<AdditionalMetadataUpdateProcessPlugin>) DSpaceServicesFactory
                    .getInstance().getServiceManager().getServicesByType(AdditionalMetadataUpdateProcessPlugin.class);
            for (AdditionalMetadataUpdateProcessPlugin
                    additionalMetadataUpdateProcessPlugin : additionalMetadataUpdateProcessPlugins) {
                additionalMetadataUpdateProcessPlugin.process(this.context, item, providerName);
            }

            itemService.update(context, item);
        } catch (SQLException | NullPointerException e) {
            log.error(e.getMessage(), e);
        } catch (AuthorizeException e) {
            log.error(e.getMessage(), e);
        }

    }

    protected String getMetadata(String formName, Record itemLookup, String name) {
        String type = SubmissionLookupService.getType(itemLookup);

        String md = outputMap.get(type + "." + name);
        if (StringUtils.isBlank(md)) {
            md = outputMap.get(formName + "." + name);
            if (StringUtils.isBlank(md)) {
                md = outputMap.get(name);
            }
        }

        // KSTA:ToDo: Make this a modifier
        if (md != null && md.contains("|")) {
            String[] cond = md.trim().split("\\|");
            for (int idx = 1; idx < cond.length; idx++) {
                boolean temp = itemLookup.getFields().contains(cond[idx]);
                if (temp) {
                    return null;
                }
            }
            return cond[0];
        }
        return md;
    }

    protected String[] splitMetadata(String metadata) {
        String[] mdSplit = new String[3];
        if (StringUtils.isNotBlank(metadata)) {
            String tmpSplit[] = metadata.split("\\.");
            if (tmpSplit.length == 4) {
                mdSplit = new String[4];
                mdSplit[0] = tmpSplit[0];
                mdSplit[1] = tmpSplit[1];
                mdSplit[2] = tmpSplit[2];
                mdSplit[3] = tmpSplit[3];
            } else if (tmpSplit.length == 3) {
                mdSplit = new String[4];
                mdSplit[0] = tmpSplit[0];
                mdSplit[1] = tmpSplit[1];
                mdSplit[2] = tmpSplit[2];
                mdSplit[3] = null;
            } else if (tmpSplit.length == 2) {
                mdSplit = new String[4];
                mdSplit[0] = tmpSplit[0];
                mdSplit[1] = tmpSplit[1];
                mdSplit[2] = null;
                mdSplit[3] = null;
            }
        }
        return mdSplit;
    }

    protected boolean isValidMetadata(String formName, String[] md) {
        try {
            if (extraMetadataToKeep != null
                    && extraMetadataToKeep.contains(StringUtils.join(Arrays.copyOfRange(md, 0, 3), "."))) {
                return true;
            }
            return getDCInput(formName, md[0], md[1], md[2]) != null;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }

    protected DCInput getDCInput(String formName, String schema, String element, String qualifier)
            throws DCInputsReaderException {
        List<DCInputSet> dcinputsets = new DCInputsReader().getInputsBySubmissionName(formName);
        for (DCInputSet dcinputset : dcinputsets) {
            for (DCInput[] dcrow : dcinputset.getFields()) {
                for (DCInput dcinput : dcrow) {
                    if (dcinput.getSchema().equals(schema) && dcinput.getElement().equals(element)
                            && ((dcinput.getQualifier() != null && dcinput.getQualifier().equals(qualifier))
                                    || (dcinput.getQualifier() == null && qualifier == null))) {
                        return dcinput;
                    }
                }
            }
        }
        return null;
    }

    protected boolean isRepeatableMetadata(String formName, String[] md) {
        try {
            DCInput dcinput = getDCInput(formName, md[0], md[1], md[2]);
            if (dcinput != null) {
                return dcinput.isRepeatable();
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    protected String[] splitValue(String value) {
        String[] splitted = value.split(SubmissionLookupService.SEPARATOR_VALUE_REGEX);
        String[] result = new String[6];
        result[0] = splitted[0];
        result[2] = "-1";
        result[3] = "-1";
        result[4] = "-1";
        if (splitted.length > 1) {
            result[5] = "splitted";
            if (StringUtils.isNotBlank(splitted[1])) {
                result[1] = splitted[1];
            }
            if (splitted.length > 2) {
                result[2] = String.valueOf(Integer.parseInt(splitted[2]));
                if (splitted.length > 3) {
                    result[3] = String.valueOf(Integer.parseInt(splitted[3]));
                    if (splitted.length > 4) {
                        result[4] = String.valueOf(Integer.parseInt(splitted[4]));
                    }
                }
            }
        }
        return result;
    }

    protected void makeSureMetadataExist(Context context, String schema, String element, String qualifier) {
        try {
            context.turnOffAuthorisationSystem();
            boolean create = false;
            MetadataSchema mdschema = metadataSchemaService.find(context, schema);
            MetadataField mdfield = null;
            if (mdschema == null) {
                mdschema = metadataSchemaService.create(context, schema,
                        SubmissionLookupService.SL_NAMESPACE_PREFIX + schema);
                create = true;
            } else {
                mdfield = metadataFieldService.findByElement(context, mdschema, element, qualifier);
            }

            if (mdfield == null) {
                metadataFieldService.create(context, mdschema, element, qualifier,
                        "Field used for cache provider of submission-lookup: " + schema);
                create = true;
            }
            if (create) {
                context.complete();
            }
            context.restoreAuthSystemState();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }
}