package customRiadaLibraries.insightmanager

import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.config.properties.APKeys
import com.atlassian.jira.config.util.JiraHome
import com.atlassian.jira.plugin.PluginVersionStore
import com.atlassian.jira.security.JiraAuthenticationContext
import com.atlassian.jira.user.ApplicationUser
import com.atlassian.jira.user.util.UserManager
import com.onresolve.scriptrunner.runner.customisers.WithPlugin
import com.riadalabs.jira.plugins.insight.channel.external.api.facade.impl.*
import com.riadalabs.jira.plugins.insight.services.events.EventDispatchOption
import com.riadalabs.jira.plugins.insight.services.imports.model.ImportSource
import com.riadalabs.jira.plugins.insight.services.model.*
import com.riadalabs.jira.plugins.insight.services.model.factory.ObjectAttributeBeanFactory
import com.riadalabs.jira.plugins.insight.services.model.factory.ObjectAttributeBeanFactoryImpl
import com.riadalabs.jira.plugins.insight.services.progress.model.Progress
import com.riadalabs.jira.plugins.insight.services.progress.model.ProgressId
import org.apache.log4j.Logger
import org.joda.time.DateTime

import java.nio.file.Files
import java.text.DateFormat
import java.time.LocalDateTime

/**
 * Breaking changes in 8.4
 *  getObjectAttributeValues() doesnt return empty values
 *                                                                     <=8.3                           >=8.4
 * im.getObjectAttributeValues("KEY-123", "An Empty Attribute")        []                              []
 * im.getObjectAttributeValues("KEY-123", ["An Empty Attribute"])      [A Empty Attribute:[]]          [:]
 * im.getObjectAttributeValues("KEY-123", [])                          [...,A Empty Attribute:[],..]   [..](A map containing all attributes with values)

 * createObject, updateObjectAttribute, updateObjectAttributes no longer accepts username as input when updating a user attribute,
 instead user key or an ApplicationUser object is accepted.
 *
 */


@WithPlugin("com.riadalabs.jira.plugins.insight")

class InsightManagerForScriptrunner {


    Logger log
    ApplicationUser initialUser
    ApplicationUser serviceUser
    Class objectFacadeClass
    ObjectFacadeImpl objectFacade
    Class iqlFacadeClass
    IQLFacadeImpl iqlFacade
    Class objectTypeFacadeClass
    ObjectTypeFacadeImpl objectTypeFacade
    Class objectTypeAttributeFacadeClass
    ObjectTypeAttributeFacadeImpl objectTypeAttributeFacade
    Class objectAttributeBeanFactoryClass
    ObjectAttributeBeanFactory objectAttributeBeanFactory
    Class ImportSourceConfigurationFacadeClass
    ImportSourceConfigurationFacadeImpl importFacade
    Class ProgressFacadeClass
    ProgressFacadeImpl progressFacade
    Class objectTicketFacadeClass
    ObjectTicketFacadeImpl objectTicketFacade
    public boolean readOnly
    public boolean autoEscalate = true//should Insight requests be automatically escalated?
    private boolean currentlyEscalate = false
    String baseUrl
    String jiraDataPath
    JiraAuthenticationContext authContext
    UserManager userManager
    EventDispatchOption eventDispatchOption


    InsightManagerForScriptrunner() {

        PluginVersionStore pluginVersionStore = ComponentAccessor.getComponentOfType(PluginVersionStore)
        ArrayList<Integer> currentInsightVersion = pluginVersionStore.getAll().find { it.name == "Insight" }.version.split(/\./).collect { it.toInteger() }
        ArrayList<Integer> minInsightVersion = [8, 4, 0]
        if (currentInsightVersion[0] < minInsightVersion[0] ||
                currentInsightVersion[1] < minInsightVersion[1] ||
                currentInsightVersion[2] < minInsightVersion[2]
        ) {

            throw new InputMismatchException("Unsupported Insight verion ${currentInsightVersion.join(".")}, minimum supported verion is  ${minInsightVersion.join(".")}")
        }


        //The facade classes
        objectFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.ObjectFacade")
        objectTypeFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.ObjectTypeFacade")
        objectTypeAttributeFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.ObjectTypeAttributeFacade")
        objectAttributeBeanFactoryClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.services.model.factory.ObjectAttributeBeanFactory")
        iqlFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.IQLFacade");
        ImportSourceConfigurationFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.ImportSourceConfigurationFacade")
        ProgressFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.ProgressFacade")
        objectTicketFacadeClass = ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.ObjectTicketFacade")

        //The facade instances
        objectFacade = ComponentAccessor.getOSGiComponentInstanceOfType(objectFacadeClass) as ObjectFacadeImpl
        objectTypeFacade = ComponentAccessor.getOSGiComponentInstanceOfType(objectTypeFacadeClass) as ObjectTypeFacadeImpl
        objectTypeAttributeFacade = ComponentAccessor.getOSGiComponentInstanceOfType(objectTypeAttributeFacadeClass) as ObjectTypeAttributeFacadeImpl
        objectAttributeBeanFactory = ComponentAccessor.getOSGiComponentInstanceOfType(objectAttributeBeanFactoryClass) as ObjectAttributeBeanFactoryImpl
        iqlFacade = ComponentAccessor.getOSGiComponentInstanceOfType(iqlFacadeClass) as IQLFacadeImpl
        importFacade = ComponentAccessor.getOSGiComponentInstanceOfType(ImportSourceConfigurationFacadeClass) as ImportSourceConfigurationFacadeImpl
        progressFacade = ComponentAccessor.getOSGiComponentInstanceOfType(ProgressFacadeClass) as ProgressFacadeImpl
        objectTicketFacade = ComponentAccessor.getOSGiComponentInstanceOfType(objectTicketFacadeClass) as ObjectTicketFacadeImpl

        //Atlassian Managers
        authContext = ComponentAccessor.getJiraAuthenticationContext();
        userManager = ComponentAccessor.getUserManager()



        //Static Paths
        baseUrl = ComponentAccessor.getApplicationProperties().getString(APKeys.JIRA_BASEURL)
        jiraDataPath = ComponentAccessor.getComponentOfType(JiraHome).getDataDirectory().path



        log = Logger.getLogger(this.class.name)
        //log.setLevel(Level.TRACE)
        log.trace("InsightManager has been instantiated")


        eventDispatchOption = EventDispatchOption.DISPATCH


    }


    /**
     * Get list of all ImportSource for a Schema
     * @param SchemaID Schema ID
     * @return ArrayList with ImportSources
     */
    ArrayList<ImportSource> listImports(int SchemaID) {


        log.debug("Getting import jobs for schema $SchemaID")

        escalatePrivilage("\t")

        ArrayList<ImportSource> imports = importFacade.findImportSourcesBySchema(SchemaID)

        log.trace("\tFound Imports:")
        imports.each {
            log.trace("\t\tName:\"" + it.name + "\" ID:" + it.id)
        }

        log.debug("Found " + imports.size() + " imports")
        dropPrivilage("\t")


        return imports

    }

    /**
     * Starts an Insight Import and by default waits for it to finish
     * @param Import The import to be run, can be an int ImportSourceID or ImportSource object.
     * @param ObjectsToImport What objects to import, can be a mix of ObjectTypeIDs,ObjectBeans and ObjectTypeNames in a List. If left empty all objects will be imported
     * @param timeOut (millis) defaults to 10min, if set to 0millis wont wait for import to finish just start it and return immediately.
     * @return Progress object
     */
    Progress runImport(def Import, List ObjectsToImport = [], long timeOut = 600000) {

        log.info("Running import")

        int importSourceId
        ImportSource importSourceObject
        Progress progress = null


        escalatePrivilage("\t")

        try {
            if (Import instanceof Integer) {
                importSourceId = Import
                importSourceObject = importFacade.loadImportSource(importSourceId)

            } else if (Import instanceof ImportSource) {
                importSourceId = Import.id
                importSourceObject = Import

            }

            log.trace("\tImportSource ID:" + importSourceId)
            log.trace("\timportSource Name:" + importSourceObject.name)


            if (ObjectsToImport.isEmpty()) {
                log.trace("\tImporting all Objects")
                if (readOnly) {
                    log.debug("\tCurrently in read only mode, wont run import. Will return previous import result")
                    progress = getImportProgress(importSourceId)
                } else {
                    progress = importFacade.startImportSource(importSourceId)
                }

            } else {

                log.trace("\tImport only objects:" + ObjectsToImport)
                Map<Object, Integer> AllOTS = [:]
                List<Integer> OTStoImport = []


                importSourceObject.importSourceOTS.each { OTS ->

                    AllOTS[OTS.objectTypeBean.name] = OTS.id
                    AllOTS[OTS.objectTypeBean.id] = OTS.id
                }


                ObjectsToImport.each { importObject ->
                    if (importObject instanceof ObjectBean || importObject instanceof MutableObjectBean) {


                        if (!AllOTS.containsKey(importObject.getId())) {
                            throw new RuntimeException("Object to import could not be found:" + importObject)
                        } else {
                            OTStoImport.add(AllOTS.get(importObject.getId()))
                        }

                    } else if (importObject instanceof Integer) {
                        if (!AllOTS.containsKey(importObject)) {
                            throw new RuntimeException("Object to import could not be found:" + importObject)
                        } else {
                            OTStoImport.add(AllOTS.get(importObject))
                        }
                    } else if (importObject instanceof String) {
                        if (!AllOTS.containsKey(importObject)) {
                            throw new RuntimeException("Object to import could not be found:" + importObject)
                        } else {
                            OTStoImport.add(AllOTS.get(importObject))
                        }
                    } else {
                        throw new RuntimeException("Object to import $importObject is of unkown type " + importObject.getClass())
                    }

                }


                log.trace("\t\tDetermined OTS IDs of Objects to import to be:" + OTStoImport)
                if (readOnly) {
                    log.debug("\tCurrently in read only mode, wont run import. Will return previous import result")
                    progress = getImportProgress(importSourceId)
                } else {
                    progress = importFacade.startImportSourceForSpecificOTs(importSourceId, OTStoImport)
                }


            }


            if (progress == null) {
                throw new RuntimeException("Failed to run import $importSourceId")
            }

            if (timeOut != 0) {
                long startOfImport = System.currentTimeMillis()

                while (System.currentTimeMillis() < startOfImport + timeOut) {

                    if (!progress.inProgress.toBoolean()) {
                        break
                    } else {
                        sleep(500)
                    }
                    log.trace("\tImport status:" + progress.status.toString() + " " + progress.progressInPercent + "%")

                }

                if ((startOfImport + timeOut) < System.currentTimeMillis()) {
                    throw new RuntimeException("Import timed out, it took longer than " + timeOut / 1000 + " seconds")
                } else {
                    log.info("\tImport took " + (System.currentTimeMillis() - startOfImport) / 1000 + " seconds to complete")
                }
            }


        } catch (all) {
            log.error("\tError running import:" + all.message)
            logRelevantStacktrace(all.stackTrace)
            dropPrivilage("\t")

        }


        dropPrivilage("\t")
        if (timeOut == 0) {
            log.info("Import started")
        } else {
            log.info("Import finished")
        }

        return progress
    }


    /**
     * Get the progress object of an import (without triggering it)
     * @param Import can be an int ImportSourceID or ImportSource object.
     * @return progress object. progress.finished (true/false) will tell you if currently running.
     */
    Progress getImportProgress(def Import) {


        log.info("Getting Progress object for Import")

        ImportSource importSourceObject

        if (Import instanceof Integer) {

            importSourceObject = importFacade.loadImportSource(Import)

        } else if (Import instanceof ImportSource) {

            importSourceObject = Import

        } else {
            throw new NullPointerException(Import + " is not a valid import")
        }


        log.debug("\tDetermined import to be: ${importSourceObject.name} (${importSourceObject.id})")
        ProgressId progressId = ProgressId.create(importSourceObject.id.toString(), "imports")


        Progress progress = progressFacade.getProgress(progressId)
        log.debug("Got progress, ${progress.progressInPercent}%")

        return progress


    }


    /**
     * Used to set the Service Account to be used when interacting with Insight
     * @param userSuppliedServiceUser can be an ApplicationUser or a String containing the user Key or user Name
     */
    void setServiceAccount(def userSuppliedServiceUser = "") {

        initialUser = authContext.getLoggedInUser()

        if (initialUser == null) {
            log.info("No initial user found, likely because the script is running as service, setting initialuser = ServiceAccount")
            initialUser = userManager.getUserByKey(userSuppliedServiceUser.toString())
            if (initialUser == null) {
                initialUser = userManager.getUserByName(userSuppliedServiceUser.toString())
            }

            log.info("\t Initial user set to: " + initialUser.username)
        } else {
            log.info("Current User is " + initialUser.username)
        }


        if (userSuppliedServiceUser == "") {
            log.info("No service user has been supplied, will run as " + initialUser.username)
            serviceUser = initialUser
        } else if (userSuppliedServiceUser instanceof ApplicationUser) {
            serviceUser = userSuppliedServiceUser
            log.info("serviceUser set to " + serviceUser.username)
        } else {
            log.trace("A non ApplicationUser userSuppliedServiceUser has been supplied, attempting to retrieve ApplicationUser object")
            serviceUser = userManager.getUserByKey(userSuppliedServiceUser.toString())
            if (serviceUser == null) {
                log.trace("Failed retrieving service user by key, trying by name")
                serviceUser = userManager.getUserByName(userSuppliedServiceUser.toString())
            }
            if (serviceUser == null) {
                log.error("Failed retrieving ApplicationUser for user provided service user \"$userSuppliedServiceUser\" by key and name")
                throw new IllegalArgumentException("Failed retrieving ApplicationUser for user provided service user \"$userSuppliedServiceUser\" by key and name")
            } else {
                log.info("serviceUser set to " + serviceUser.username)
            }

        }
    }

    //Switch user context from initial user to service user
    private boolean escalatePrivilage(String logPrepend = "") {

        if (autoEscalate && serviceUser != null && initialUser != null && !currentlyEscalate) {
            log.trace(logPrepend + "Escalating user privileges to service account: $serviceUser from initial account: $initialUser")
            authContext.setLoggedInUser(serviceUser)

            currentlyEscalate = authContext.getLoggedInUser() == serviceUser

            return currentlyEscalate
        } else {
            log.trace(logPrepend + "Escalation not performed")
            return false
        }

    }

    //Switch user context from service user to initial user
    private boolean dropPrivilage(String logPrepend = "") {

        if (autoEscalate && serviceUser != null && initialUser != null && currentlyEscalate) {
            log.trace(logPrepend + "Descalating user privileges from initial account: $initialUser to service account: $serviceUser")
            authContext.setLoggedInUser(initialUser)

            currentlyEscalate = false


            return authContext.getLoggedInUser() == initialUser
        } else {
            log.trace(logPrepend + "Descalation not performed")
            return false
        }

    }


    /**
     * Should events be dispatched when you create/update/delete objects?
     * @param dispatch true or false, default is true
     */
    void dispatchEvents(boolean dispatch) {

        if (dispatch) {
            this.eventDispatchOption = EventDispatchOption.DISPATCH
        } else {
            this.eventDispatchOption = EventDispatchOption.DO_NOT_DISPATCH
        }

    }

    /**
     * Runs an IQL and returns matching objects
     * @param schemaId What scheme to run the IQL on
     * @param iql The IQL to be run
     * @return An array containing ObjectBeans
     */
    ArrayList<ObjectBean> iql(int schemaId, String iql) {

        log.debug("Running IQL \"" + iql + "\" on schema " + schemaId)
        ArrayList<ObjectBean> objects = []
        escalatePrivilage()
        objects = iqlFacade.findObjectsByIQLAndSchema(schemaId, iql)
        dropPrivilage()

        log.trace("\t Objects:")
        objects.each {
            log.trace("\t\t" + it)
        }
        log.debug(objects.size() + " objects returned")

        return objects

    }


    /**
     * Clears value of attribute
     * @param object Can be object ID, Object Key or ObjectBean
     * @param attribute name (string) or id (int)
     */

    void clearObjectAttribute(def object, def attribute) {

        log.debug("Clearing attribute $attribute of object ${object}")

        escalatePrivilage()
        ObjectBean objectBean = getObjectBean(object)


        if (readOnly) {
            log.debug("Object Attribute not updated as currently in read only mode")
        } else {

            try {
                long attributeBeanId = objectFacade.loadObjectAttributeBean(objectBean.id, attribute).id
                if (attributeBeanId != null) {
                    objectFacade.deleteObjectAttributeBean(attributeBeanId as int, this.eventDispatchOption)
                } else {
                    log.debug("\tAttribute is already empty")
                }

            } catch (Exception vie) {
                log.debug("Exception" + vie)
                log.warn("Could not update object attribute due to validation exception:" + vie.getMessage());
            }
        }

        dropPrivilage()

    }


    /**
     * Creates a new object with Attribute Values
     * The label attribute must be populated (as an attribute)
     * @param schemeId id of the scheme where you want to create your object
     * @param objectTypeName Name of the object type you want to create
     * @param AttributeValues A map containing the Attributes and values to be set. The Attribute can be represented as an ID or a case insensitive string [AttributeID1: ValueA, attributename: ValueB]
     * @return The created ObjectBean
     */
    ObjectBean createObject(int schemeId, String objectTypeName, Map AttributeValues) {

        int objectTypeId = objectTypeFacade.findObjectTypeBeansFlat(schemeId).find { it.name == objectTypeName }.id

        return createObject(objectTypeId, AttributeValues)
    }

    /**
     * Creates a new object with Attribute Values
     * The label attribute must be populated (as an attribute)
     * @param ObjectTypeId ID of the object type you want to create
     * @param AttributeValues A map containing the Attributes and values to be set. The Attribute can be represented as an ID or a case insensitive string [AttributeID1: ValueA, attributename: ValueB]
     * @return The created ObjectBean
     */
    ObjectBean createObject(Integer ObjectTypeId, Map AttributeValues) {

        log.debug("Creating object with ObjectTypeId: $ObjectTypeId and attribute values $AttributeValues")

        ObjectTypeBean objectTypeBean
        ObjectBean newObject = null
        MutableObjectBean mutableObjectBean = null

        try {

            escalatePrivilage("\t")
            objectTypeBean = objectTypeFacade.loadObjectTypeBean(ObjectTypeId)


            if (objectTypeBean == null) {
                throw new RuntimeException("Could not find ObjectType with ID " + ObjectTypeId)
            }

            mutableObjectBean = objectTypeBean.createMutableObjectBean()


            log.debug("\t" + AttributeValues.size() + " AttributeValues have been supplied, adding them to new object")


            ArrayList availableAttributes = objectTypeAttributeFacade.findObjectTypeAttributeBeans(ObjectTypeId).collectMany {
                [it.getId(), it.getName().toLowerCase()]
            }
            ArrayList<ObjectAttributeBean> objectAttributeBeans = []


            AttributeValues.each { attributeValue ->

                ObjectTypeAttributeBean objectTypeAttributeBean
                if (attributeValue.key instanceof Integer) {
                    log.trace("\tCreating Attribute Bean with ID:" + attributeValue.key + " and value:" + attributeValue.value + ", attribute input value type:" + attributeValue.value.class.simpleName)

                    if (!availableAttributes.contains(attributeValue.key)) {
                        throw new RuntimeException("Attribute " + attributeValue.key + " could not be found for objectID:" + ObjectTypeId)
                    }

                    objectTypeAttributeBean = getObjectTypeAttributeBean(attributeValue.key, ObjectTypeId)
                } else if (attributeValue.key instanceof String) {
                    log.trace("\tCreating Attribute Bean with Name:" + attributeValue.key + " and value:" + attributeValue.value + ", attribute input value type:" + attributeValue.value.class.simpleName)

                    if (!availableAttributes.contains(attributeValue.key.toLowerCase())) {
                        throw new RuntimeException("Attribute " + attributeValue.key + " could not be found for objectID:" + ObjectTypeId)
                    }

                    objectTypeAttributeBean = getObjectTypeAttributeBean(attributeValue.key, ObjectTypeId)
                } else {
                    throw new RuntimeException("Attribute " + attributeValue.key + " could not be found for objectID:" + ObjectTypeId)
                }


                MutableObjectAttributeBean attributeBean
                if (attributeValue.value instanceof ArrayList) {

                    ArrayList<String> valueStrings = []


                    attributeValue.value.each {
                        if (it instanceof ObjectBean) {
                            valueStrings.add(it.objectKey)
                        } else if (it instanceof ApplicationUser) {
                            valueStrings.add(it.key)
                        } else {
                            valueStrings.add(it.toString())
                        }
                    }

                    attributeBean = objectAttributeBeanFactory.createObjectAttributeBeanForObject(mutableObjectBean, objectTypeAttributeBean, *valueStrings)


                } else if (attributeValue.value instanceof Date || attributeValue.value instanceof DateTime || attributeValue.value instanceof LocalDateTime) {


                    DateFormat dateFormat = DateFormat.getDateInstance()
                    attributeBean = objectAttributeBeanFactory.createObjectAttributeBeanForObject(mutableObjectBean, objectTypeAttributeBean, dateFormat, dateFormat, attributeValue.value as String)


                } else {

                    if (attributeValue.value instanceof ObjectBean) {

                        attributeBean = objectAttributeBeanFactory.createObjectAttributeBeanForObject(mutableObjectBean, objectTypeAttributeBean, attributeValue.value.objectKey)

                    } else if (attributeValue.value instanceof ApplicationUser) {
                        attributeBean = objectAttributeBeanFactory.createObjectAttributeBeanForObject(mutableObjectBean, objectTypeAttributeBean, attributeValue.value.key)

                    } else {
                        attributeBean = objectAttributeBeanFactory.createObjectAttributeBeanForObject(mutableObjectBean, objectTypeAttributeBean, attributeValue.value as String)

                    }

                }


                log.trace("\t\tCreated Attribute Bean:" + attributeBean.collect { ["Attribute ID: " + it.objectTypeAttributeId, "Values:" + it.objectAttributeValueBeans.value] }.flatten())
                log.trace("\t" * 3 + "Input Attribute Name was:" + attributeValue.key + ", Input Value was:" + attributeValue.value)

                if ([attributeValue.value].flatten().size() != attributeBean.objectAttributeValueBeans.size()) {

                    throw new InputMismatchException("Failed to create ObjectAttributeBean based on input data:" + attributeValue)
                }

                objectAttributeBeans.add(attributeBean)

            }


            mutableObjectBean.setObjectAttributeBeans(objectAttributeBeans)
            if (readOnly) {
                log.debug("\tCurrently in readOnly mode, not storing object")
                dropPrivilage("\t")
                return null

            } else {
                log.trace("\tStoring object")
                newObject = objectFacade.storeObjectBean(mutableObjectBean, this.eventDispatchOption)
                log.info(newObject.objectKey + " created.")

                dropPrivilage("\t")
                return newObject
            }


        } catch (all) {
            log.error("Error creating object:" + all.message)
            logRelevantStacktrace(all.stackTrace)
            dropPrivilage("\t")


        }

        dropPrivilage("\t")

        return newObject
    }

    /**
     * Updates a single objects single attribute with one or multiple values.
     * @param object Can be object ID, Object Key or ObjectBean
     * @param attribute Can be name of Attribute or AttributeID
     * @param value Can be an array of values or a single object such as a string, ApplicationUser
     * @return Retuns the new/updated ObjectAttributeBean
     */
    ObjectAttributeBean updateObjectAttribute(def object, def attribute, def value) {

        log.info("Updating Object $object attribute $attribute with value $value")


        ObjectBean objectBean = getObjectBean(object)
        ObjectAttributeBean newObjectAttributeBean = null

        escalatePrivilage("\t")

        try {

            log.trace("\tObjectbean:" + objectBean)

            MutableObjectTypeAttributeBean attributeBean = getObjectTypeAttributeBean(attribute, objectBean.objectTypeId).createMutable()

            MutableObjectAttributeBean newAttributeBean
            if (value instanceof ArrayList) {
                //make sure everything is a string
                value = value.collect { it.toString() }

                newAttributeBean = objectAttributeBeanFactory.createObjectAttributeBeanForObject(objectBean, attributeBean, *value)

            } else {
                newAttributeBean = objectAttributeBeanFactory.createObjectAttributeBeanForObject(objectBean, attributeBean, value as String)
            }

            ObjectAttributeBean oldAttributeBean = objectFacade.loadObjectAttributeBean(objectBean.id, attributeBean.id)

            // If attribute exist reuse the old id for the new attribute
            if (oldAttributeBean != null) {
                newAttributeBean.setId(oldAttributeBean.id)
            }

            if (readOnly) {
                log.info("Attribute not updated, currently in read only mode")
                return null
            } else {

                newObjectAttributeBean = objectFacade.storeObjectAttributeBean(newAttributeBean, this.eventDispatchOption)

                if (newObjectAttributeBean != null) {
                    log.info("Successfully updated attribute")
                    return newObjectAttributeBean
                } else {
                    log.error("Failed to update attribute")
                    return newObjectAttributeBean
                }
            }


        } catch (all) {
            log.error("\tError updating object attribute:" + all.message)
            logRelevantStacktrace(all.stackTrace)
            dropPrivilage("\t")

        }

        dropPrivilage("\t")

        return newObjectAttributeBean

    }

    /**
     * Updates a single objects multiple attributes with one or multiple values.
     * @param object Can be object ID, Object Key or ObjectBean
     * @param attributeValueMap is a map containing Keys representing AttributeID or Attribute Name and values that are either an array or a single string. [AttributeID1: ValueA, attributename: [ValueB1,ValueB2]]
     * @return Returns and array of the new/updated ObjectAttributeBean
     */
    ArrayList<ObjectAttributeBean> updateObjectAttributes(def object, Map attributeValueMap) {

        log.info("Updating ${attributeValueMap.size()} Object $object attributes")


        ObjectBean objectBean = getObjectBean(object)

        ArrayList<ObjectAttributeBean> newObjectAttributeBeans = []

        escalatePrivilage("\t")

        try {

            log.trace("\tObjectbean:" + objectBean)


            attributeValueMap.each { map ->

                MutableObjectTypeAttributeBean attributeBean = getObjectTypeAttributeBean(map.key, objectBean.objectTypeId).createMutable()

                MutableObjectAttributeBean newAttributeBean
                if (map.value instanceof ArrayList) {
                    //make sure everything is a string
                    map.value = map.value.collect { it.toString() }

                    newAttributeBean = objectAttributeBeanFactory.createObjectAttributeBeanForObject(objectBean, attributeBean, *map.value)

                } else {
                    newAttributeBean = objectAttributeBeanFactory.createObjectAttributeBeanForObject(objectBean, attributeBean, map.value as String)
                }

                ObjectAttributeBean oldAttributeBean = objectFacade.loadObjectAttributeBean(objectBean.id, attributeBean.id)

                // If attribute exist reuse the old id for the new attribute
                if (oldAttributeBean != null) {
                    newAttributeBean.setId(oldAttributeBean.id)
                }

                if (readOnly) {
                    log.info("Attribute not updated, currently in read only mode")
                    return null
                } else {

                    ObjectAttributeBean newObjectAttributeBean = objectFacade.storeObjectAttributeBean(newAttributeBean, this.eventDispatchOption)

                    if (newObjectAttributeBean != null) {
                        newObjectAttributeBeans.add(newObjectAttributeBean)
                        log.info("Successfully updated attribute")

                    } else {
                        log.error("Failed to update attribute")
                        throw new RuntimeException("Failed to update Object (${objectBean.objectKey}) attribute: ${map.key} with value: ${map.value}")

                    }
                }


            }


        } catch (all) {
            log.error("\tError updating object attribute:" + all.message)
            logRelevantStacktrace(all.stackTrace)
            dropPrivilage("\t")

        }

        dropPrivilage("\t")

        return newObjectAttributeBeans

    }

    //Find ObjectTypeAttributeBean
    ObjectTypeAttributeBean getObjectTypeAttributeBean(def attribute, Integer ObjectTypeId = null) {

        log.trace("Getting ObjectTypeAttributeBean for objectType " + ObjectTypeId + " and attribute:" + attribute)
        ObjectTypeAttributeBean objectTypeAttributeBean = null

        try {
            if (attribute instanceof Integer) {
                objectTypeAttributeBean = objectTypeAttributeFacade.loadObjectTypeAttributeBean(attribute as Integer)
            } else {

                objectTypeAttributeBean = objectTypeAttributeFacade.loadObjectTypeAttribute(ObjectTypeId, attribute as String)
            }


            if (objectTypeAttributeBean == null) {
                if (ObjectTypeId == null) {
                    throw new RuntimeException("Attribute " + attribute + " could not be found")
                } else {
                    throw new RuntimeException("Attribute " + attribute + " could not be found for object $ObjectTypeId")
                }

            }
        } catch (all) {
            log.error("\tError getting object attribute:" + all.message)
            logRelevantStacktrace(all.stackTrace)
            throw all

        }


        return objectTypeAttributeBean

    }

    /**
     *  A flexible method for returning an ObjectBean
     * @param object can be ObjectBean/MutableObjectBean,Object Key (string) or object ID (int).
     * @return ObjectBean
     */
    ObjectBean getObjectBean(def object) {

        ObjectBean objectBean = null


        if (object instanceof ObjectBean || object instanceof MutableObjectBean) {

            //Refreshes the object
            escalatePrivilage("\t")
            objectBean = objectFacade.loadObjectBean(object.id)
            dropPrivilage("\t")
        } else if (object instanceof String || object instanceof Integer) {

            escalatePrivilage("\t")
            objectBean = objectFacade.loadObjectBean(object)
            dropPrivilage("\t")
        }

        if (objectBean == null) {
            throw new RuntimeException("Failed to find object $object")
        }

        return objectBean
    }

    /**
     * Gets all values of single object attribute. Returns the values as list
     * @param Object Can be ObjectBean/MutableObjectBean,Object Key (string) or object ID (int).
     * @param Attribute can be attribute ID (int) or attribute name (string)
     * @return List of results, if a referenced object is part of the result an ObjectBean will be returned, if empty an empty list.
     */
    List getObjectAttributeValues(def Object, def Attribute) {


        escalatePrivilage("\t")

        ObjectBean object = getObjectBean(Object)
        ObjectTypeAttributeBean objectTypeAttributeBean = getObjectTypeAttributeBean(Attribute, object.objectTypeId)

        log.info("Getting object (${object.objectKey}) attribute value (${objectTypeAttributeBean.name})")

        //if there are attribute beans, return them if not return empty list
        List<ObjectAttributeValueBean> valueBeans = objectFacade.loadObjectAttributeBean(object.id, objectTypeAttributeBean.id) ? objectFacade.loadObjectAttributeBean(object.id, objectTypeAttributeBean.id).getObjectAttributeValueBeans() : []

        dropPrivilage("\t")


        log.trace("\tGot values:" + valueBeans.collect { it.value })

        List values = valueBeans.collect {
            if (it.referencedObjectBeanId != null) {
                return getObjectBean(it.value)
            } else {
                return it.value
            }
        }


        return values


    }


    /**
     * Get multiple attribute values from object
     * @param Object id, Objectbean, key
     * @param Attributes name of attributes (string) or id (integer), if left empty all attributes with values will be returned.
     * @return A map with where the key is the name of the attribute (not id) and the value is an array of values.
     *          The values are generally of the type they are in Insight, so referenced objects are returned as objects.
     *          If the attribute is empty the key will still be in in the map but with an empty array.
     *          [Created:[Mon Jan 27 09:47:56 UTC 2020], projectID:[], Involved Brands:[Brand1 (STI-30), Brand2 (STI-31)]]
     */
    Map<String, List> getObjectAttributeValues(def Object, List Attributes = []) {

        Map<String, List> returnData = [:]

        escalatePrivilage("\t")

        ObjectBean object = getObjectBean(Object)
        ArrayList<ObjectAttributeBean> attributeBeans = object.getObjectAttributeBeans()

        if (Attributes != []) {
            log.info("For object \"$object\", getting attribute values:" + Attributes)
            List<ObjectTypeAttributeBean> filteredObjectTypeAttributeBeans = []
            Attributes.each {
                filteredObjectTypeAttributeBeans.add(getObjectTypeAttributeBean(it, object.objectTypeId))
            }


            attributeBeans.removeAll { !filteredObjectTypeAttributeBeans.id.contains(it.objectTypeAttributeId) }

        }


        attributeBeans.each { attributeBean ->


            ObjectTypeAttributeBean objectTypeAttributeBean = getObjectTypeAttributeBean(attributeBean.objectTypeAttributeId, object.objectTypeId)
            List<ObjectAttributeValueBean> valueBeans = attributeBean.objectAttributeValueBeans

            List values = valueBeans.collect {


                if (it.referencedObjectBeanId != null) {
                    return getObjectBean(it.value)
                } else {
                    return it.value
                }

            }

            returnData.put(objectTypeAttributeBean.name, values)
            log.debug("\tGot:" + objectTypeAttributeBean.name + ":" + values)


        }


        dropPrivilage("\t")

        return returnData
    }


    /**
     * This will create a HTML table showing some or all of an objects attributes
     * @param Object id, Objectbean, key
     * @param Attributes name of attributes (string) or id (integer), if left empty all attributes will be returned.
     * @return A HTML string
     */
    String renderObjectToHtml(def Object, List Attributes = []) {

        ObjectBean object = getObjectBean(Object)
        Map<String, ArrayList> attributes = getObjectAttributeValues(object, Attributes)

        String returnHtml

        if (object.hasAvatar) {
            returnHtml = "" +
                    "<p style = 'line-height: 20px'>\n" +
                    "   <img src = '${baseUrl}/rest/insight/1.0/object/${object.id}/avatar.png?size=48' style='vertical-align: middle' /><b> ${object.label}</b>\n" +
                    "</p>" +
                    "<p>" +
                    "<table>"

        } else {
            returnHtml = "" +
                    "<p style = 'line-height: 20px'>\n" +
                    "   <img src = '${baseUrl}/rest/insight/1.0/objecttype/${object.objectTypeId}/icon.png?size=48' style='vertical-align: middle' /><b> ${object.label}</b>\n" +
                    "</p>\n" +
                    "<p>\n" +
                    "<table>\n"

        }

        attributes.each {

            if (!it.value.isEmpty() && (it.value.first() instanceof ObjectBean || it.value.first() instanceof MutableObjectBean)) {
                returnHtml += "" +
                        "   <tr>\n" +
                        "        <td valign=\"top\"><b>${it.key}:</b></td>\n" +
                        "        <td valign=\"top\">\n"
                it.value.each { referencedObject ->

                    if (referencedObject.hasAvatar) {

                        returnHtml += "     <img src = '${baseUrl}/rest/insight/1.0/object/${referencedObject.id}/avatar.png?size=16' style='vertical-align: middle' />${referencedObject.label}"
                    } else (
                            returnHtml += "        <img src = '${baseUrl}/rest/insight/1.0/objecttype/${referencedObject.objectTypeId}/icon.png?size=16' style='vertical-align: middle' />${referencedObject.label}"
                    )

                }

                returnHtml += "    </tr>"

            } else {
                returnHtml += "" +
                        "   <tr>\n" +
                        "        <td valign=\"top\"><b>${it.key}:</b></td>\n" +
                        "        <td valign=\"top\">${it.value.join(",")}</td>\n" +
                        "    </tr>"
            }


        }

        returnHtml += "" +
                "</table>" +
                "</p>"


        return returnHtml

    }


    /**
     * Deletes an object
     * @param object Can be object ID, Object Key or ObjectBean
     * @return boolean representing success or failure
     */
    boolean deleteObject(def object) {

        log.debug("Deleting object:" + object)
        escalatePrivilage("\t")


        ObjectBean objectBean = getObjectBean(object)


        log.trace("\tObject id:" + objectBean.id)

        if (readOnly) {
            log.info("\tCurrently in readOnly mode, not deleting object $objectBean")
            dropPrivilage("\t")
            return false
        } else {
            objectFacade.deleteObjectBean(objectBean.id, this.eventDispatchOption)
            if (objectFacade.loadObjectBean(objectBean.id) == null) {
                log.info("\tDeleted object $objectBean")
                dropPrivilage("\t")
                return true
            } else {
                log.error("\tFailed to delete object $objectBean")
                dropPrivilage("\t")
                return false
            }
        }


    }


    /**
     * Returns all history beans for an object
     * @param object key, id or objectbean
     * @return
     */
    ArrayList<ObjectHistoryBean> getObjectHistory(def object) {

        ArrayList<ObjectHistoryBean> historyBeans = []

        escalatePrivilage("\t")

        ObjectBean objectBean = getObjectBean(object)


        historyBeans = objectFacade.findObjectHistoryBean(objectBean.id)


        dropPrivilage("\t")

        return historyBeans

    }


    /**
     * <h3>This is a class intended to simplify working with Insight AttachmentBeans</h3>
     * Once instantiated it will give you easy access to the AttachmentBean it self as well as the related File object
     */
    public class SimplifiedAttachmentBean {

        public AttachmentBean attachmentBean
        public Integer id
        public File attachmentFile
        public String originalFileName

        SimplifiedAttachmentBean(AttachmentBean attachmentBean) {

            this.attachmentBean = attachmentBean
            this.id = attachmentBean.id
            this.attachmentFile = getAttachmentBeanFile(this.attachmentBean)
            this.originalFileName = attachmentBean.filename

        }

        boolean isValid() {

            return this.attachmentBean != null && this.id > 0 && this.attachmentFile.canRead()

        }

        /**
         * Compare two SimplifiedAttachmentBean to determine if they are the same
         * @param other another SimplifiedAttachmentBean
         * @return true if equals, false if not
         */
        @Override
        boolean equals(def other) {
            SimplifiedAttachmentBean otherBean

            if (!other instanceof SimplifiedAttachmentBean) {
                return false
            }

            otherBean = other as SimplifiedAttachmentBean
            if (this.attachmentFile.getBytes().sha256() != otherBean.attachmentFile.getBytes().sha256()) {
                return false
            }else if (this.attachmentBean.nameInFileSystem != otherBean.attachmentBean.nameInFileSystem) {
                return false
            }else {
                return true
            }

        }



    }

    /**
     * This method will give you the File object of an AttachmentBean
     * @param attachmentBean The AttachmentBean whose File object you want
     * @return A File object
     */
    File getAttachmentBeanFile(AttachmentBean attachmentBean) {
        log.trace("\tGetting file for attachmentBean:" + attachmentBean.id)
        String expectedPath = jiraDataPath + "/attachments/insight/object/${attachmentBean.objectId}/" + attachmentBean.getNameInFileSystem()

        log.trace("\t"*3 + "Expect file to be located here:" + expectedPath)

        File attachmentFile = new File(expectedPath)
        assert attachmentFile.canRead() : "Cant access attachment file: " + attachmentBean.getNameInFileSystem()
        return attachmentFile
    }

    /**
     * This method will retrieve all SimplifiedAttachmentBeans belonging to an object.
     * @param object key, id or objectbean
     * @return ArrayList containing SimplifiedAttachmentBean
     * <b>Note</b> that the File object will have a different file name than the original file name.
     */
    ArrayList<SimplifiedAttachmentBean> getAllObjectAttachmentBeans(def object) {

        log.info("Will get attachments for object:" + object)
        ArrayList<SimplifiedAttachmentBean> objectAttachments = [:]
        ObjectBean objectBean
        escalatePrivilage("\t")

        try {

            objectBean = getObjectBean(object)
            assert objectBean != null: "Could not find objectbean based on $object"
            ArrayList<AttachmentBean> attachmentBeans = objectFacade.findAttachmentBeans(objectBean.id)
            log.debug("\tFound ${attachmentBeans.size()} attachment beans for the object")

            attachmentBeans.each {
                log.debug("\t" * 2 + it.getFilename() + " (${it.getNameInFileSystem()}) " + it.mimeType)
                objectAttachments.add(new SimplifiedAttachmentBean(it))

            }


        } catch (all) {
            log.error("There was an error trying to retrieve attachments for object:" + object)
            log.error(all.message)

        }


        dropPrivilage("\t")

        log.info("\tSuccessfully retrieved ${objectAttachments.size()} attachments")

        return objectAttachments

    }


    /**
     * Add an attachment to an object
     * @param object key, id or objectbean of the object you want to attatch to
     * @param file the file you´d like to attach
     * @param attachmentComment (Optional) a comment relevant to the attachment, note that this is not the same as an object comment
     * @param deleteSourceFile Should the source file be deleted?
     * @return A SimplifiedAttachmentBean representing the new attachment
     */
    SimplifiedAttachmentBean addObjectAttachment(def object , File file, String attachmentComment = "", boolean deleteSourceFile = false) {

        log.info("Will add attachment ${file.name} to object:" + object)

        ObjectBean objectBean
        escalatePrivilage("\t")
        File sourceFile

        try {
            objectBean = getObjectBean(object)
            assert objectBean != null: "Could not find objectbean based on $object"

            if (deleteSourceFile) {
                sourceFile = file
            }else {
                sourceFile = new File(file.path + "_temp")
                Files.copy(file.toPath(), sourceFile.toPath() )
            }

            AttachmentBean attachmentBean = objectFacade.addAttachmentBean(objectBean.id, sourceFile, file.name, Files.probeContentType(sourceFile.toPath()), attachmentComment)

            assert attachmentBean != null && attachmentBean.nameInFileSystem != null
            log.debug("\tThe attachment was successfully stored and given the name:" + attachmentBean.nameInFileSystem)
            return new SimplifiedAttachmentBean(attachmentBean)

        }catch(all) {
            log.error("There was an error trying add attachment ${sourceFile.name} to object:" + object)
            log.error(all.message)
            return  null
        }


    }

    /**
     * Delete an attachment
     * @param attachment Id, AttachmentBean or SimplifiedAttachmentBean
     * @return true if successful
     */
    boolean deleteObjectAttachment(def attachment) {

        log.info("Will delete attachment ($attachment)")
        int attachmentId = 0

        if (attachment instanceof Integer || attachment instanceof Long ) {
            attachmentId = attachment
        }else if(attachment instanceof  AttachmentBean) {
            attachmentId = attachment.id
        }else if (attachment instanceof SimplifiedAttachmentBean) {
            attachmentId = attachment.id
        }


        if ( attachmentId == 0) {
            throw new InputMismatchException("Could not determine attachment based on $attachment")
        }

        log.debug("\tDetermined AttachmentBean ID to be:" + attachmentId)

        try {

            AttachmentBean deletedBean = objectFacade.deleteAttachmentBean(attachmentId)

            log.info("\t" + deletedBean.filename + " was deleted from object ${deletedBean.objectId}")
            return true

        }catch(all) {
            log.error("There was an error trying to delete attachment ${attachment}")
            log.error(all.message)
            return  null
        }

    }




    void logRelevantStacktrace(StackTraceElement[] stacktrace) {

        stacktrace.each {
            if (it.className.contains(this.class.name)) {
                log.error(it)

            }
        }


    }

}

