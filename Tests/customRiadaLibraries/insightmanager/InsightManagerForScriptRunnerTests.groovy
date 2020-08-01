package customRiadaLibraries.insightmanager

import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.config.util.JiraHome
import com.atlassian.jira.user.ApplicationUser
import com.onresolve.scriptrunner.runner.customisers.WithPlugin
import com.riadalabs.jira.plugins.insight.services.imports.model.ImportSource
import com.riadalabs.jira.plugins.insight.services.model.MutableObjectAttributeBean
import com.riadalabs.jira.plugins.insight.services.model.MutableObjectBean
import com.riadalabs.jira.plugins.insight.services.model.ObjectAttributeBean
import com.riadalabs.jira.plugins.insight.services.model.ObjectBean
import com.riadalabs.jira.plugins.insight.services.model.ObjectTypeAttributeBean
import com.riadalabs.jira.plugins.insight.services.progress.model.Progress
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.joda.time.DateTime
import customRiadaLibraries.insightmanager.InsightManagerForScriptrunner
import java.time.Instant
import java.time.LocalDateTime
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.apache.groovy.json.internal.LazyMap

@WithPlugin("com.riadalabs.jira.plugins.insight")

//TODO should IM have try/catch or leave that up to the implementer?
//TODO document how to catch the logs.
//TODO Verify that Status attributes gets returned as expected.

//An ugly method for clearing Groovy/Java cache in JIRA so that changes to the IM library always gets picked up.
//Good to have when updating IM, but never run on production environments.
boolean clearCodeCache = true
if (clearCodeCache) {
    String host = "http://jiratest-im84.stuxnet.se"
    String restUser = "anders"
    String restPw = restUser
    HttpURLConnection cacheClearConnection = new URL(host + "/rest/scriptrunner/latest/canned/com.onresolve.scriptrunner.canned.jira.admin.JiraClearCaches").openConnection() as HttpURLConnection
    String auth = restUser + ":" + restPw
    auth = "Basic " + auth.bytes.encodeBase64().toString()
    cacheClearConnection.setRequestProperty("Authorization", auth)
    cacheClearConnection.setDoOutput(true)
    cacheClearConnection.setRequestMethod("POST")
    cacheClearConnection.setRequestProperty("Content-Type", "application/json")
    cacheClearConnection.setRequestProperty("Accept", "application/json")
    byte[] jsonByte = new JsonBuilder(["FIELD_WHICH_CACHE": "gcl", "canned-script": "com.onresolve.scriptrunner.canned.jira.admin.JiraClearCaches"]).toPrettyString().getBytes("UTF-8")
    cacheClearConnection.outputStream.write(jsonByte, 0, jsonByte.length)
    LazyMap rawReturn = new JsonSlurper().parse(cacheClearConnection.getInputStream())
    println rawReturn.output
}


InsightManagerForScriptrunner im = new InsightManagerForScriptrunner()
im.log.setLevel(Level.ALL)






//Should IMs import functionality be tested?
boolean testImports = false

//The ID of an Object Scheme that has at least 1 import
int objectSchemeIdWithImports = 2

//The ID of an Import to test.
// This import will be run several times and must belong to the objectSchemeIdWithImports-scheme
int importToTest = 3

//Max expected duration of import
int importTimeOutMs = 30000

//Name of an object type that is valid, objects of this type will be created and subsequently deleted
//No preexisting objects will be harmed
String validObjectTypeName = "Test Object Type"

//Object schemeId where validObjectTypeName belongs
int validSchemeId = 1


//These are attributes and values that are valid for validObjectTypeName
ArrayList<Map> validAttributeValues = [
        [
                "Name"                            : "A test object with named attributes",
                "A Date Attribute"                : new Date(),
                "A Date Time Attribute"           : DateTime.now(),
                "A Local Date Time Attribute"     : LocalDateTime.now(),
                "A referenced Object Key"         : "TS-1",
                "A referenced Object Id"          : 2,
                "A referenced Object Bean"        : im.getObjectBean("TS-3"),
                "A boolean attribute"             : true,
                "Multiple referenced Object Keys" : ["TS-1", "TS-2", "TS-3"],
                "Multiple referenced Object Id"   : [1, 2, 3],
                "Multiple referenced Object Beans": [im.getObjectBean("TS-1"), im.getObjectBean("TS-2"), im.getObjectBean("TS-3")],
                "A text attribute"                : "Some text",
                "A select attribute"              : ["First Element", "Second Element"],
                "A Status attribute"              : "RUNNING",
                "A Username attribute"            : "anders",
                "A user key attribute"            : "JIRAUSER10000",
                "An ApplicationUser Attribute"    : im.userManager.getUserByName("jsduser"),
               // "Multiple User attribute"         : [im.userManager.getUserByName("jsduser")]
                "Multiple User attribute"         : ["anders", im.userManager.getUserByName("jsduser")]
                //TODO look over multi user attribute problem

        ],
        [
                2 : "A test object with numeric attributes",
                5 : new Date(),
                6 : DateTime.now(),
                95: [im.userManager.getUserByName("jsduser")]
               // 95: ["anders", im.userManager.getUserByName("jsduser")]

        ]
]
//These are attributes where Cardinality Minimum = 0, ie attributes that doesnt have to have a value
ArrayList attributesThatMayBeCleared = ["A Date Attribute", 6, "A select attribute", "A user key attribute"]


boolean globalFail = false
boolean globalWarning = false
String globalFailString = ""
String globalWarningString = ""

Logger log = Logger.getLogger("im.scriptrunner.test") //To filter logs in a linux server: tail -f /var/atlassian/application-data/jira/log/atlassian-jira.log | sed -n -e 's/^.*scriptrunner.test]//p'
log.setLevel(Level.ALL)

/*
ApplicationUser testUser = im.userManager.getUserByName("jsduser")
MutableObjectBean testObject = im.getObjectBean("TS-330") as MutableObjectBean

log.debug("[\"anders\", testUser.getKey()]" + ["anders", testUser.getKey()])

ObjectTypeAttributeBean objectTypeAttributeBean = im.getObjectTypeAttributeBean( "Multiple User attribute", testObject.objectTypeId)
MutableObjectAttributeBean attributeBean = im.objectAttributeBeanFactory.createObjectAttributeBeanForObject(testObject, objectTypeAttributeBean, *["anders", testUser.getKey()])
MutableObjectAttributeBean attributeBean2 = im.objectAttributeBeanFactory.createObjectAttributeBeanForObject(testObject, objectTypeAttributeBean, *["anders", "JIRAUSER10200"])
log.debug("attributeValue.value:" + attributeBean.objectAttributeValueBeans)
log.debug("attributeValue2.value:" + attributeBean2.objectAttributeValueBeans)

return
*/

if (testImports) {

    try {
        log.info("Testing if IM can find imports in Object Scheme " + objectSchemeIdWithImports)
        ArrayList<ImportSource> importSources = im.listImports(objectSchemeIdWithImports)

        assert !importSources.isEmpty(): "Could not find Imports for Scheme:" + objectSchemeIdWithImports
        ImportSource importSourceToTest = importSources.find { it.id == importToTest }
        assert importSourceToTest: "Could not find the import with ID $importToTest in the Scheme with ID: $objectSchemeIdWithImports"
        log.info("\tFound ${importSources.size()} imports in scheme $objectSchemeIdWithImports:")
        importSources.each {
            log.debug("\t\tName:" + it.name)
            log.trace("\t\t\tId:" + it.id)
            log.trace("\t\t\tDescription:" + it.description)
        }


        log.info("Testing import \"${importSourceToTest.name}\" ($importToTest) with all objects and with default timeout")
        Instant importStart = Instant.now()
        Progress importProgress = im.runImport(importSourceToTest)

        assert importProgress != null: "Running the import \"${importSourceToTest.name}\" ($importToTest) failed"
        long importDuration = Instant.now().toEpochMilli() - importStart.toEpochMilli()
        assert (importDuration > 10): "The import seems to have finished to quickly ($importDuration ms)"
        assert importProgress.resultData.isOK(): "The import finished with not OK status"

        log.info("\tFirst import test was successful, duration:" + importDuration)


        String objectTypeNameToImport = importSourceToTest.enabledImportSourceOTS.first().objectTypeBean.name
        log.info("Testing import \"${importSourceToTest.name}\" ($importToTest) with one Objecttype Name (${objectTypeNameToImport}) and with imediate timeout")

        importStart = Instant.now()
        importProgress = im.runImport(importSourceToTest, [objectTypeNameToImport], 0)
        assert importProgress != null: "Running the import \"${importSourceToTest.name}\" ($importToTest) with ObjectType Name $objectTypeNameToImport failed"
        importDuration = Instant.now().toEpochMilli() - importStart.toEpochMilli()
        assert (importDuration < 1000): "The import seems to have returned to slowly ($importDuration ms), was expecting emidate return"

        while (importProgress.inProgress && (importDuration < importTimeOutMs)) {

            log.debug("\tImport Progress ${importProgress.progressInPercent}%, duration $importDuration ms")

            sleep(100)
            importDuration = Instant.now().toEpochMilli() - importStart.toEpochMilli()
        }

        log.debug("\tImport Progress ${importProgress.progressInPercent}%, duration $importDuration ms")

        assert importDuration < importTimeOutMs: "The import seems to have run for to long ($importDuration ms)"
        assert !importProgress.inProgress: "The import is still inProgress even though it should have finished"
        assert importProgress.resultData.isOK(): "The import finished with not OK status"

        log.info("\tThe second import test was successful, duration:" + importDuration)


        log.info("Testing getting progress of Import (${importSourceToTest.name}) based on ImportSource-object , without triggering it")
        Progress staleProgress = im.getImportProgress(importSourceToTest)

        assert staleProgress != null: "Failed to get progress of Import (${importSourceToTest.name}) based on ImportSource-object , without triggering it"

        log.debug("\tImport Progress ${staleProgress.progressInPercent}%, In progress: ${staleProgress.inProgress} ")
        log.info("\tSuccessfully tested getting progress of Import based on ImportSource-object , without triggering it")


        log.info("Testing import \"${importSourceToTest.name}\" ($importToTest) with read only")

        im.readOnly = true
        Progress readOnlyProgress = im.runImport(importSourceToTest)
        im.readOnly = false

        assert staleProgress.finishedDate == readOnlyProgress.finishedDate: "Running Import in read only mode seems to have triggered the import!"
        log.info("\tSuccessfully tested running Import in read only mode")

    } catch (AssertionError error) {

        log.error("There was error testing Imports:" + error.message)
        globalFail = true
        globalFailString += "There was error testing Imports:" + error.message + "\n"


    }


    log.info("Testing of IM Import has finished successfully!")


}

try {

    log.info("Testing creation of objectType $validObjectTypeName in scheme $validSchemeId, based on objectTypeName and schemeId")

    ArrayList<ObjectBean> newObjects = []
    validAttributeValues.each { valueMap ->
        log.trace("\tCreating object with Attributes and values:")
        valueMap.each { log.trace("\t" * 2 + it) }
        ObjectBean newObject = im.createObject(validSchemeId, validObjectTypeName, valueMap)
        log.debug("\tCreated object:" + newObject)
        newObjects.add(newObject)
    }

    log.info("\tTesting of createObject appears successful")

    log.info("Testing getObjectAttributeValues(Object, List) by verifying the previously created objects")
    log.debug("\tThis test is quite superficial, it only tests that the right amount of attributes got values and not their actual values")

    newObjects.eachWithIndex { ObjectBean objectBean, int i ->

        Map exceptedAttributeAndValues = validAttributeValues[i]

        log.debug("\tVerifying object:" + objectBean)
        log.trace("\t\tShould have these values:")
        exceptedAttributeAndValues.each { log.debug("\t" * 3 + it + " (${it.value.class.simpleName})") }
        log.trace("\t\tActually has these values:")
        Map actualAttributeAndValues = im.getObjectAttributeValues(objectBean, exceptedAttributeAndValues.keySet() as List)
        actualAttributeAndValues.each { log.debug("\t" * 3 + it + " (${it?.value?.first()?.class?.simpleName})") }

        assert exceptedAttributeAndValues.size() == actualAttributeAndValues.size(): "The new object $objectBean is missing attribute values"


    }

    log.info("\tThe created objects and getObjectAttributeValues(Object, List) where tested successfully")

    log.info("Testing getObjectAttributeValues(Object, List) with various input types")

    log.debug("\tTesting with object bean and empty [], expecting all attributes")

    Map newObjectAllAttributes = im.getObjectAttributeValues(newObjects.first(), [])
    assert newObjectAllAttributes.size() == newObjects.first().objectAttributeBeans.size(): "Got ${newObjectAllAttributes.size()} attribute values, was expecting " + newObjects.first().objectAttributeBeans.size()
    log.trace("\t" * 2 + "Success!")


    log.debug("\tTesting with object id and empty [], expecting all attributes")

    newObjectAllAttributes = im.getObjectAttributeValues(newObjects.first().id, [])
    assert newObjectAllAttributes.size() == newObjects.first().objectAttributeBeans.size(): "Got ${newObjectAllAttributes.size()} attribute values, was expecting " + newObjects.first().objectAttributeBeans.size()
    log.trace("\t" * 2 + "Success!")


    log.debug("\tTesting with object key and empty [], expecting all attributes")

    newObjectAllAttributes = im.getObjectAttributeValues(newObjects.first().objectKey, [])
    assert newObjectAllAttributes.size() == newObjects.first().objectAttributeBeans.size(): "Got ${newObjectAllAttributes.size()} attribute values, was expecting " + newObjects.first().objectAttributeBeans.size()
    log.trace("\t" * 2 + "Success!")


    ArrayList<String> validAttributeNames = validAttributeValues.collect { it.keySet() }.flatten().findAll { it instanceof String }.unique()
    ArrayList<Integer> validAttributeIds = validAttributeValues.collect { it.keySet() }.flatten().findAll { it instanceof Integer }.unique()

    log.debug("\tTesting with object bean and ${[validAttributeNames.first()]}, expecting one attribute")
    if (!validAttributeNames.isEmpty()) {


        Map newObjectOneAttribute = im.getObjectAttributeValues(newObjects.first(), [validAttributeNames.first()])
        if (newObjectOneAttribute.size() != 1) {

            String localWarning = "Did not get any attribute values back from object \"${newObjects.first()}´s\" attribute " + validAttributeNames.first()
            log.warn(localWarning)
            globalWarning = true
            globalWarningString += localWarning

        } else {
            log.trace("\t" * 2 + "Success!")
        }


    }

    log.debug("\tTesting with object bean and ${[validAttributeIds.first()]}, expecting one attribute")
    if (!validAttributeIds.isEmpty()) {

        Map newObjectOneAttribute = im.getObjectAttributeValues(newObjects.first(), [validAttributeIds.first()])
        if (newObjectOneAttribute.size() != 1) {

            String localWarning = "Did not get any attribute values back from object \"${newObjects.first()}´s\" attribute " + validAttributeIds.first()
            log.warn(localWarning)
            globalWarning = true
            globalWarningString += localWarning

        } else {
            log.trace("\t" * 2 + "Success!")
        }

    }

    log.info("\tTesting of getObjectAttributeValues(Object, List) with various input types was successful!")

    log.info("Testing getObjectAttributeValues(Object, AttributeName) and getObjectAttributeValues(Object, AttributeId)")

    log.debug("\tTesting with object bean and ${validAttributeNames.first()}, expecting one attribute")
    if (!validAttributeNames.isEmpty()) {


        List newObjectOneAttribute = im.getObjectAttributeValues(newObjects.first(), validAttributeNames.first())
        if (newObjectOneAttribute.size() != 1) {

            String localWarning = "Did not get any attribute values back from object \"${newObjects.first()}´s\" attribute " + validAttributeNames.first()
            log.warn(localWarning)
            globalWarning = true
            globalWarningString += localWarning

        } else {
            log.trace("\t" * 2 + "Success!")
        }


    }

    log.debug("\tTesting with object id and ${validAttributeIds.first()}, expecting one attribute")
    if (!validAttributeIds.isEmpty()) {

        List newObjectOneAttribute = im.getObjectAttributeValues(newObjects.first().id, validAttributeIds.first())
        if (newObjectOneAttribute.size() != 1) {

            String localWarning = "Did not get any attribute values back from object \"${newObjects.first()}´s\" attribute " + validAttributeIds.first()
            log.warn(localWarning)
            globalWarning = true
            globalWarningString += localWarning

        } else {
            log.trace("\t" * 2 + "Success!")
        }

    }

    log.debug("\tTesting with object key and ${validAttributeIds.first()}, expecting one attribute")
    if (!validAttributeIds.isEmpty()) {

        List newObjectOneAttribute = im.getObjectAttributeValues(newObjects.first().objectKey, validAttributeIds.first())
        if (newObjectOneAttribute.size() != 1) {

            String localWarning = "Did not get any attribute values back from object \"${newObjects.first()}´s\" attribute " + validAttributeIds.first()
            log.warn(localWarning)
            globalWarning = true
            globalWarningString += localWarning

        } else {
            log.trace("\t" * 2 + "Success!")
        }

    }

    log.info("\tTesting of getObjectAttributeValues(Object, AttributeName) and getObjectAttributeValues(Object, AttributeId) was successful!")


    log.info("Testing clearing of object attributes with clearObjectAttribute")
    ObjectBean objectUnderTest = newObjects.first()
    final Map<String, List> originalAttributeVales = im.getObjectAttributeValues(objectUnderTest, [])
    log.debug("\tTesting on newly created object:" + objectUnderTest)
    log.debug("\tObjects attributes before changes:")
    originalAttributeVales.each { log.debug("\t" * 2 + it) }

    log.debug("\tTesting in read only mode")
    im.readOnly = true
    attributesThatMayBeCleared.each { attribute ->
        log.debug("\t\tClearing attribute:" + attribute)
        im.clearObjectAttribute(objectUnderTest, attribute)

        List attributeAfterClear = im.getObjectAttributeValues(objectUnderTest, attribute)

        log.trace("\t\t\tNew Attribute value:" + attributeAfterClear)

        assert attributeAfterClear.size() != 0: "Attribute \"$attribute\" of object \"$objectUnderTest\" was cleared even though IM is in read only mode"

    }


    log.debug("\tTesting without read only mode")
    im.readOnly = false
    attributesThatMayBeCleared.each { attribute ->
        log.debug("\t\tClearing attribute:" + attribute)
        im.clearObjectAttribute(objectUnderTest, attribute)

        List attributeAfterClear = im.getObjectAttributeValues(objectUnderTest, attribute)

        log.trace("\t\t\tNew Attribute value:" + attributeAfterClear)

        assert attributeAfterClear.size() == 0: "Failed to clear Attribute \"$attribute\" of object \"$objectUnderTest\""
        assert im.getObjectAttributeValues(objectUnderTest, attribute) == (im.getObjectAttributeValues(objectUnderTest, [attribute]).get(attribute) ?: []): "Error confirming clearing of attribute, getObjectAttributeValues(def Object, def Attribute) and getObjectAttributeValues(def Object, List Attributes) returns different values"
    }

    log.info("\tTesting of clearObjectAttribute was successful!")

    log.info("Testing update of a single object attribute with updateObjectAttribute(def object, def attribute, def value)")
    Map.Entry attributeValueToUpdate = originalAttributeVales.find { attributesThatMayBeCleared.contains(it.key) }
    if (attributeValueToUpdate == null) {
        throw new InputMismatchException("Could not find a named attribute to test updateObjectAttribute(def object, def attribute, def value) with, please make sure the first entry of validAttributeValues contains at least one that is not required input")
    }
    log.debug("\tWill update object \"$objectUnderTest's\" attribute \"${attributeValueToUpdate.key}\" with \"${attributeValueToUpdate.value}\"")
    im.clearObjectAttribute(objectUnderTest, attributeValueToUpdate.key)

    assert im.getObjectAttributeValues(objectUnderTest, attributeValueToUpdate.key).size() == 0: "Error during testing of updateObjectAttribute(def object, def attribute, def value), could not clear attribute ${attributeValueToUpdate.key}"

    log.debug("\tTesting in read only mode")
    im.readOnly = true

    im.updateObjectAttribute(objectUnderTest, attributeValueToUpdate.key, attributeValueToUpdate.value)

    List afterReadonlyUpdate = im.getObjectAttributeValues(objectUnderTest, attributeValueToUpdate.key)
    log.trace("\t" * 2 + "Attribute value after updated:" + afterReadonlyUpdate)

    assert afterReadonlyUpdate.size() == 0: "updateObjectAttribute(${[objectUnderTest, attributeValueToUpdate.key, attributeValueToUpdate.value].join(",")}) updated an attribute even though currently in read only mode"

    log.info("\tSuccess!")


    log.debug("\tTesting without read only mode")
    im.readOnly = false

    im.updateObjectAttribute(objectUnderTest, attributeValueToUpdate.key, attributeValueToUpdate.value)

    List afterUpdate = im.getObjectAttributeValues(objectUnderTest, attributeValueToUpdate.key)
    log.trace("\t" * 2 + "Attribute value after updated:" + afterUpdate)

    assert afterUpdate.size() == attributeValueToUpdate.value.size(): "Updating an object using updateObjectAttribute(${[objectUnderTest, attributeValueToUpdate.key, attributeValueToUpdate.value].join(",")}) gave unexpexpeted result: " + afterUpdate

    log.info("\tSuccess!")


    log.info("Testing update of a multiple object attribute with updateObjectAttributes(def object, Map attributeValueMap)")
    log.debug("\tPreparing for test by clearing \"$objectUnderTest\" attributes first")
    ArrayList attributesUnderTest = attributesThatMayBeCleared.findAll { originalAttributeVales.keySet().contains(it) }
    assert attributesUnderTest != null && attributesUnderTest.size() > 0: "Could not find attributes to clear in preparation for testing updateObjectAttributes(def object, Map attributeValueMap)"

    attributesUnderTest.each { attribute ->

        log.trace("\t" * 2 + "Clearing attribute: " + attribute)
        im.clearObjectAttribute(objectUnderTest, attribute)

    }


    log.debug("\tClearing of attributes is complete")
    log.trace("\t" * 2 + "Current Attribute Values")
    im.getObjectAttributeValues(objectUnderTest, attributesUnderTest).each {
        log.trace("\t" * 3 + it)
    }

    log.debug("\tTesting in read only mode")
    im.readOnly = true

    log.debug("\t\tWill now update Attribute Values")
    log.trace("\t" * 3 + "Setting Attribute Values")
    originalAttributeVales.findAll { attributesUnderTest.contains(it.key) }.each {
        log.trace("\t" * 4 + it)
    }
    im.updateObjectAttributes(objectUnderTest, originalAttributeVales.findAll { attributesUnderTest.contains(it.key) })
    Map<String, List> updatedAttributes = im.getObjectAttributeValues(objectUnderTest, attributesUnderTest)
    log.trace("\t" * 3 + "Values after update:")
    if (updatedAttributes.isEmpty()) {
        log.trace("\t" * 4 + updatedAttributes)
    }else {
        updatedAttributes.each { log.trace("\t" * 4 + it) }
    }

    log.debug("updatedAttributes.values().isEmpty():" + updatedAttributes.values().every {it.isEmpty()})
    assert updatedAttributes.values().every {it.isEmpty()}: "An attribute ($updatedAttributes) was updated even though currently in read only mode"

    log.info("\tSuccess!")



    log.debug("\tTesting without read only mode")
    im.readOnly = false

    log.debug("\t\tWill now update Attribute Values")
    log.trace("\t" * 3 + "Setting Attribute Values")
    originalAttributeVales.findAll { attributesUnderTest.contains(it.key) }.each {
        log.trace("\t" * 4 + it)
    }
    im.updateObjectAttributes(objectUnderTest, originalAttributeVales.findAll { attributesUnderTest.contains(it.key) })
    updatedAttributes = null
    updatedAttributes = im.getObjectAttributeValues(objectUnderTest, attributesUnderTest)
    log.trace("\t" * 3 + "Values after update:")
    updatedAttributes.each { log.trace("\t" * 4 + it) }

    attributesUnderTest.each { attribute ->
        if (originalAttributeVales.get(attribute) != updatedAttributes.get(attribute)) {
            log.warn("An object (${objectUnderTest.objectKey}) attribute ($attribute) got an unexpected value after update")
            log.warn("Original value: " + originalAttributeVales.get(attribute) + "(${originalAttributeVales.get(attribute).class.simpleName})")
            log.warn("Updated value: " + updatedAttributes.get(attribute) + "(${updatedAttributes.get(attribute).class.simpleName})")

            globalWarning = true
            globalWarningString += "An object (${objectUnderTest.objectKey}) attribute ($attribute) got an unexpected value after update\n" +
                    "Original value: " + originalAttributeVales.get(attribute) + "(${originalAttributeVales.get(attribute).class.simpleName})\n" +
                    "Updated value: " + updatedAttributes.get(attribute) + "(${updatedAttributes.get(attribute).class.simpleName})\n"

        }

        assert originalAttributeVales.get(attribute).size() == updatedAttributes.get(attribute).size(): "An attribute ($attribute) failed to updated"
    }

    log.info("\tSuccess!")


    log.info("Testing IQL Searches")
    String iqlFindNewObjects = "Key IN (" + newObjects.objectKey.collect { "\"" + it + "\"" }.join(",") + ")"
    log.debug("\tLooking for newly created objects with IQL:" + iqlFindNewObjects + " in scheme $validSchemeId, expecting ${newObjects.size()} objects")

    ArrayList<ObjectBean> iqlResult = im.iql(validSchemeId, iqlFindNewObjects)
    log.trace("\tIQL result:")
    iqlResult.each { log.trace("\t" * 2 + it) }

    assert newObjects.size() == iqlResult.size(): "The IQL result did not return the expected number of objects"
    assert newObjects.objectKey == iqlResult.objectKey: "The IQL result did not return the expected Object Keys, got:" + iqlResult.objectKey
    log.info("\tSuccess!")

    log.info("Testing renderObjectToHtml() with all attributes for object:" + objectUnderTest.objectKey)
    String htmlWithAllAttributes = im.renderObjectToHtml(objectUnderTest)
    log.trace("\tGot HTML:")
    htmlWithAllAttributes.eachLine { log.trace("\t" * 2 + it) }


    assert im.getObjectAttributeValues(objectUnderTest).size() == htmlWithAllAttributes.count("<tr>")
    log.debug("\tAll attributes appears present")
    log.info("\tSuccess!")


    log.info("Testing renderObjectToHtml() with some attributes (${attributesUnderTest})for object:" + objectUnderTest.objectKey)
    String htmlWithSomeAttributes = im.renderObjectToHtml(objectUnderTest, attributesUnderTest)
    log.trace("\tGot HTML:")
    htmlWithSomeAttributes.eachLine { log.trace("\t" * 2 + it) }
    assert im.getObjectAttributeValues(objectUnderTest,attributesUnderTest).size() == htmlWithSomeAttributes.count("<tr>")
    log.debug("\tThe expected attributes appears present")
    log.info("\tSuccess!")

    log.info("Testing deleteObject() by deleting the created test objects " + newObjects.objectKey.join(","))
    log.debug("\tTesting in read only mode")
    im.readOnly = true

    newObjects.each {
        log.debug("\t" * 2 + "Deleting: " + it)
        assert !im.deleteObject(it): "$it was deleted even though currently in read only mode"
    }

    log.debug("\tDeleting complete, confirming with IQL:" + iqlFindNewObjects)
    iqlResult = im.iql(validSchemeId, iqlFindNewObjects)
    log.trace("\t" * 2 + "IQL Returned:" + iqlResult)
    assert iqlResult.size() == newObjects.size(): "Objects where deleted even though currently in read only mode"
    log.debug("\tThe deleted objects could still be found")
    log.info("\tSuccess!")


    log.debug("\tTesting without read only mode")
    im.readOnly = false

    newObjects.each {
        log.debug("\t" * 2 + "Deleting: " + it)
        assert im.deleteObject(it): "Error deleting object:" + it
    }

    log.debug("\tDeleting complete, confirming with IQL:" + iqlFindNewObjects)
    iqlResult = im.iql(validSchemeId, iqlFindNewObjects)
    log.trace("\t" * 2 + "IQL Returned:" + iqlResult)
    assert iqlResult.size() == 0: "Deleting of objects failed."
    log.debug("\tThe deleted objects could no longer be found")
    log.info("\tSuccess!")


} catch (AssertionError error) {

    log.error("There was an error testing Objects:" + error.message)
    globalFail = true
    globalFailString += "There was error testing Objects:" + error.message + "\n"


}
if (globalWarning) {
    log.warn("There where warnings during the execution:")
    globalWarningString.eachLine { log.warn("\t" + it) }
}

if (globalFail) {
    log.error("There where errors during the execution:")
    globalFailString.eachLine { log.error("\t" + it) }
}


