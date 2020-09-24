import com.atlassian.jira.bc.issue.search.SearchService
import com.atlassian.jira.bc.issue.worklog.DeletedWorklog
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.config.util.JiraHome
import com.atlassian.jira.event.type.EventDispatchOption
import com.atlassian.jira.issue.CustomFieldManager
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.IssueManager
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.issue.changehistory.ChangeHistoryManager
import com.atlassian.jira.issue.fields.CustomField
import com.atlassian.jira.issue.search.SearchResults
import com.atlassian.jira.issue.worklog.Worklog
import com.atlassian.jira.issue.worklog.WorklogManager
import com.atlassian.jira.security.JiraAuthenticationContext
import com.atlassian.jira.user.ApplicationUser
import com.atlassian.jira.user.util.UserManager
import com.atlassian.jira.web.bean.PagerFilter
import com.onresolve.scriptrunner.runner.customisers.WithPlugin
import com.riadalabs.jira.plugins.insight.services.model.ObjectBean
import customRiadaLibraries.insightmanager.InsightManagerForScriptrunner
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.ToString
import groovy.json.internal.LazyMap
import org.apache.log4j.Level
import org.apache.log4j.Logger

@WithPlugin("com.riadalabs.jira.plugins.insight")


//Setup the basics of RestoreInsightField
boolean readOnly = false
boolean runExport = false
boolean runImport = true

RestoreInsightField restoreInsightField = new RestoreInsightField()
restoreInsightField.readOnly = readOnly
Logger log = Logger.getLogger("RestoreInsightField.runner")
log.setLevel(Level.ALL)


//To export from a restored JIRA that still has the field values
if (runExport) {
    //This JQL should find all the relevant issues where the field has a value
    String jql = ""

    //This should be the field-ID of the field from which the export should be made.
    String insightFieldId = "customfield_12508"

    File exportedJson = restoreInsightField.exportFromBackup(jql, insightFieldId, ["Article Number"] as ArrayList)
    log.info("The export has been placed here:" + exportedJson.canonicalPath)
}

if (runImport) {

    restoreInsightField.importFromBackup()

}


class RestoreInsightField {

    CustomFieldManager customFieldManager = ComponentAccessor.getCustomFieldManager()
    ChangeHistoryManager changeHistoryManager = ComponentAccessor.getChangeHistoryManager()
    IssueManager issueManager = ComponentAccessor.getIssueManager()
    UserManager userManager = ComponentAccessor.getUserManager()
    SearchService searchService = ComponentAccessor.getComponentOfType(SearchService)
    JiraAuthenticationContext jiraAuthenticationContext = ComponentAccessor.getJiraAuthenticationContext()

    InsightManagerForScriptrunner im = new InsightManagerForScriptrunner()
    String jiraExportPath = ComponentAccessor.getComponentOfType(JiraHome).getExportDirectory().getCanonicalPath() + "/insightField"
    String jiraImportPath = ComponentAccessor.getComponentOfType(JiraHome).getImportDirectory().getCanonicalPath() + "/insightField"

    public ApplicationUser serviceUser
    public boolean readOnly = true
    Logger log = Logger.getLogger("restore.insight.fields") //tail -F /var/atlassian/application-data/jira/log/atlassian-jira.log | sed -n -e 's/^.*insight.fields]//p'


    RestoreInsightField() {

        log.setLevel(Level.ALL)
        serviceUser = jiraAuthenticationContext.getLoggedInUser()

    }

    void importFromBackup(boolean overwrite = false, String fileName = "exportedInsightFieldValues.json") {

        log.info("Will import insight field values from:" + fileName)
        File exportJsonFile = new File(jiraImportPath + "/" + fileName)
        assert exportJsonFile.exists(): "The export cant be found:" + exportJsonFile.canonicalPath

        log.debug("\tSuccessfully found export file:" + exportJsonFile.canonicalPath)

        ArrayList<Map> exportJson = new JsonSlurper().parse(exportJsonFile) as ArrayList<Map>

        log.debug("\tSuccessfully read the export file")

        ArrayList<InsightFieldValue> exportedFieldValues = exportJson.collect { new InsightFieldValue(it) }
        assert exportedFieldValues.size() == exportJson.size()

        log.debug("\tSuccessfully parsed the export file")
        log.trace("\t\tFound ${exportedFieldValues.size()} field values to import")

        //log.error("WARNING JUST EVALUATING LIMITED FIELD VALUES")
        //Collections.shuffle(exportedFieldValues)
        //exportedFieldValues[0..10].each { exportedFieldValue ->
        exportedFieldValues.each { exportedFieldValue ->

            log.info("Determining new objects for issue:" + exportedFieldValue.issue + ", field:\"" + exportedFieldValue.field.name + "\" (${exportedFieldValue.field.id})")
            log.debug("\tPrevious field value:" + exportedFieldValue.value.join(","))

            ArrayList<ObjectBean> newObjectBeans = []
            exportedFieldValue.valueExpanded.each { originalObjects ->


                originalObjects.each {
                    log.debug("\tDetermining the new object key for " + it.key)
                    log.trace("\t"*2 + "Based on object information:")
                    log.trace("\t"*3 + "Attribute values:" + it.value.attributes)
                    log.trace("\t"*3 + "ObjectTypeId:" + it.value.objectTypeId)
                    log.trace("\t"*3 + "SchemaId:" + it.value.schemaId)


                    String iql = "ObjectTypeId = ${it.value.objectTypeId}"

                    it.value.attributes.each { attributeEntry ->
                        iql += " AND \"${attributeEntry.key}\" IN (${attributeEntry.value.collect { "\"$it\"" }.join(",")})"
                    }

                    log.trace("\t\tUsing the following IQL in scheme ${it.value.schemaId}:" + iql)
                    ArrayList<ObjectBean> objectBeans = im.iql(it.value.schemaId as int, iql)

                    log.trace("\t"*2 + "Determined the new object to be:" + objectBeans.join(","))

                    assert objectBeans.size() == 1: "Could not determine new Object based on:" + it.toString()

                    newObjectBeans += objectBeans


                }

            }

            assert newObjectBeans.size() ==  exportedFieldValue.valueExpanded.size()

            log.info("\tDetermined the new objects to be placed in the field to be::" + newObjectBeans.join(","))

            if (readOnly) {
                log.info("\tCurrently in readOnly mode or would update issue " + exportedFieldValue.issue)
                log.debug("\t\tField:" + exportedFieldValue.field.name + " ($exportedFieldValue.field.id)")
                log.debug("\t\tValue:" + newObjectBeans.objectKey.join(","))
            }else {

                log.info("\tUpdating issue " + exportedFieldValue.issue)
                log.debug("\t\tField:" + exportedFieldValue.field.name + " ($exportedFieldValue.field.id)")
                log.debug("\t\tValue:" + newObjectBeans.objectKey.join(","))

                ArrayList<ObjectBean> currentFieldValue = []
                if (!overwrite) {
                    currentFieldValue =  exportedFieldValue.issue.getCustomFieldValue(exportedFieldValue.field)
                }

                if (overwrite || currentFieldValue == null) {
                    exportedFieldValue.issue.setCustomFieldValue(exportedFieldValue.field, newObjectBeans)
                    exportedFieldValue.issue = issueManager.updateIssue(serviceUser,exportedFieldValue.issue, EventDispatchOption.ISSUE_UPDATED, false) as MutableIssue

                    assert exportedFieldValue.issue != null

                    log.info("\tIssue Successfully updated")

                }else {

                    log.info("\tCancled update of issue (${exportedFieldValue.issue}), the field already has a value:" + currentFieldValue.join(","))

                }


            }


        }

        log.info("Finished importing insight field values")


    }


    //jqlMatchingIssues This should match all the related issues and check that the "insightField IS NOT EMPTY"
    //attributesToExport should be unique non-empty attributes
    File exportFromBackup(String jqlMatchingIssues, String insightFieldId, String fileName = "exportedInsightFieldValues.json", ArrayList attributesToExport) {

        log.info("Exporting field values from a previous back")
        log.info("\tUsing the following JQL to find all issues with field values:" + jqlMatchingIssues)
        log.info("\tGetting field values from customField:" + insightFieldId)

        File exportJson = new File(jiraExportPath + "/" + fileName)

        if (!readOnly) {
            assert !exportJson.exists(): "The export file already exists:" + exportJson.canonicalPath
            exportJson.parentFile.mkdir()
        }


        log.info("\tThe exported data will be placed here:" + exportJson.canonicalPath)

        CustomField insightCustomField = customFieldManager.getCustomFieldObject(insightFieldId)

        ArrayList<Issue> relatedIssues = findRelatedIssues(jqlMatchingIssues)
        ArrayList<InsightFieldValue> insightFields = []

        relatedIssues.each { issue ->
            insightFields.add(new InsightFieldValue(issue, insightCustomField, attributesToExport))
        }

        if (!readOnly) {
            exportJson.write(insightFields.collect { it.toStringMap() }.toPrettyJsonString())

            assert exportJson.size() > 0

            log.info("\tJSON-export successfully created.")
        } else {
            log.info("\tCurrently in readOnly mode or the following JSON-export would have been created:")
            //log.info(insightFields.first().toMap().toPrettyJsonString())
            log.info(JsonOutput.toJson(insightFields.first().toStringMap()))
            //log.info(insightFields.collect { it.toMap() }.toPrettyJsonString())
        }


        return exportJson

    }


    class InsightFieldValue {

        MutableIssue issue
        CustomField field
        ArrayList<ObjectBean> value
        Map<String, Map> valueExpanded = [:]

        InsightFieldValue(Issue issue, CustomField field, ArrayList attributesToExpand = []) {

            this.issue = issue as MutableIssue
            this.field = field

            value = this.issue.getCustomFieldValue(this.field) as ArrayList<ObjectBean>


            value.each { objectBean ->

                Integer schemaId = im.objectTypeFacade.loadObjectType(objectBean.objectTypeId).objectSchemaId

                Map attributes = im.getObjectAttributeValues(objectBean, attributesToExpand)

                assert !attributes.isEmpty()
                assert !attributes.containsValue("") : "attributesToExport must only result in non empty attribute values"
                assert !attributes.containsValue(null) : "attributesToExport must only result in non empty attribute values"
                assert !attributes.containsValue([]) : "attributesToExport must only result in non empty attribute values"

                valueExpanded.put(objectBean.objectKey, [attributes: attributes, objectTypeId: objectBean.objectTypeId, schemaId: schemaId])


            }

            ObjectBean test

        }

        InsightFieldValue(Map fieldValueMap) {

            issue = issueManager.getIssueByCurrentKey(fieldValueMap.issue as String)
            field = customFieldManager.getCustomFieldObject(fieldValueMap.field as String)
            value = fieldValueMap.value as ArrayList
            valueExpanded = fieldValueMap.valueExpanded as Map

        }

        Map toStringMap() {

            return [issue: issue.key, field: field.id, value: value.objectKey, valueExpanded: valueExpanded]
        }

    }


    ArrayList<Issue> findRelatedIssues(String jql) {

        log.info("Finding related issues")
        log.debug("\tUsing jql:" + jql)
        SearchService.ParseResult parseResult = searchService.parseQuery(serviceUser, jql)

        ArrayList<Issue> relevantIssues

        if (parseResult.isValid()) {
            log.trace("\t" * 2 + "JQL is valid")
            SearchResults<Issue> searchResults = searchService.search(serviceUser, parseResult.getQuery(), PagerFilter.unlimitedFilter)

            relevantIssues = searchResults.results.collect { issueManager.getIssueByCurrentKey(it.key) }
        } else {
            throw new InputMismatchException("Invalid JQL generated: " + jql)
        }

        log.info(relevantIssues.size() + " related issues where found")

        return relevantIssues
    }


}



