import com.atlassian.jira.bc.issue.search.SearchService
import com.atlassian.jira.bc.issue.worklog.DeletedWorklog
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.config.util.JiraHome
import com.atlassian.jira.issue.CustomFieldManager
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.IssueManager
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
boolean runExport = true
boolean runImport = false

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

    File exportedJson = restoreInsightField.exportFromBackup(jql, insightFieldId, ["OLD KEY"] as ArrayList)
    log.info("The export has been placed here:" + exportedJson.canonicalPath)
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
    Logger log = Logger.getLogger("restore.insight.fields")


    RestoreInsightField() {

        log.setLevel(Level.ALL)
        serviceUser = jiraAuthenticationContext.getLoggedInUser()

    }

    void importFromBackup(String fileName = "exportedInsightFieldValues.json") {

        File exportJson = new File(jiraImportPath + "/" + fileName)
        assert exportJson.exists(): "The export cant be found:" + exportJson.canonicalPath
        new JsonSlurper().parse(exportJson)

    }


    //jqlMatchingIssues This should match all the related issues and check that the "insightField IS NOT EMPTY"
    File exportFromBackup(String jqlMatchingIssues, String insightFieldId, String fileName = "exportedInsightFieldValues.json", ArrayList attributesToExport) {

        log.info("Exporting field values from a previous back")
        log.info("\tUsing the following JQL to find all issues with field values:" + jqlMatchingIssues)
        log.info("\tGetting field values from customField:" + insightFieldId)

        File exportJson = new File(jiraExportPath + "/" + fileName)
        assert !exportJson.exists(): "The export file already exists:" + exportJson.canonicalPath
        if (!readOnly) {
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

        Issue issue
        CustomField field
        ArrayList<ObjectBean> value
        Map valueExpanded = [:]

        InsightFieldValue(Issue issue, CustomField field, ArrayList attributesToExpand = []) {

            this.issue = issue
            this.field = field

            value = this.issue.getCustomFieldValue(this.field) as ArrayList<ObjectBean>

            value.each { objectBean ->

                valueExpanded.put(objectBean.objectKey, im.getObjectAttributeValues(objectBean, attributesToExpand))
            }


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



