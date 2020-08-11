import com.riadalabs.jira.plugins.insight.services.model.ObjectBean
import customRiadaLibraries.insightmanager.InsightManagerForScriptrunner
import org.apache.commons.io.FileUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger



boolean readOnly = false
boolean runImport = true
boolean runExport = false
boolean runCleanup = false //NOTE!! This is only intended for dev testing, it will delete and undo all changes.


//Export parameters
String sourceIQL = "ObjectTypeId = 97" //An IQL matching all the objects that are to be exported
int sourceSchemaId = 8 //The Object schema ID to export from
String exportDirectoryPath = "/opt/atlassian/jiraim84/temp/migrateAttachments"  //Where should the exported attachments be placed


//Import parameters
String importDirectoryPath = exportDirectoryPath //Where should the exported attachments be imported from

//See IM source code documentation for explanation of these importObjectAttachments() parameters
String machiningIQL = "\"Old Object key\" = SOURCE_OBJECT_KEY"
String attachmentComment = "Imported from SOURCE_OBJECT_KEY"
boolean deleteSourceFiles = false
boolean ignoreDuplicates = true



//tail -F /var/atlassian/application-data/jiraim8*/log/atlassian-jira.log | sed -n -e 's/^.*attachments]//p'
Logger log = Logger.getLogger("im.migrate.object.attachments")
log.setLevel(Level.ALL)

InsightManagerForScriptrunner im = new InsightManagerForScriptrunner()
im.readOnly = readOnly
im.log.setLevel(Level.ALL)

if (runExport) {
    log.info("Starting Export of object attachments")
    log.info("\tAttachments in scheme $sourceSchemaId from objects matching this IQL will be exported:" + sourceIQL)
    log.info("\tThe exported files will be placed here: $exportDirectoryPath")

    ArrayList<ObjectBean>exportObjects = im.iql(sourceSchemaId, sourceIQL)

    log.debug("\tAttachments from ${exportObjects.size()} objects will be exported")

    ArrayList<File>exportedFiles = []
    log.debug("\tStarting export")
    exportObjects.each {
        log.trace("\t"*2 + "Exporting attachments from $it")
        ArrayList<File>objectAttachments = im.exportObjectAttachments(it, exportDirectoryPath)
        exportedFiles += objectAttachments

        log.trace("\t"*3 + objectAttachments.size() + " attachments where exported")
    }

    log.info("\tAttachment export has finished")
    log.debug("\t"*2 + exportedFiles.size() + " attachments where exported")
    log.debug("\t"*2 + exportedFiles.collect{it.size()}.sum() + " Bytes where exported")

}


ArrayList<InsightManagerForScriptrunner.SimplifiedAttachmentBean> attachmentBeans = []
if (runImport) {

    log.info("Starting Import of object attachments")


    attachmentBeans = im.importObjectAttachments(importDirectoryPath, machiningIQL, attachmentComment, deleteSourceFiles, ignoreDuplicates)
    log.info("\tImport completed, a total of " + attachmentBeans.size() + " attachments where imported.")

    ArrayList<Integer>updatedObjects = attachmentBeans.attachmentBean.objectId.unique()

    log.debug("\t" + updatedObjects.size() + " objects where updated")

    updatedObjects.each {
        log.trace("\tID:"*2 + it)
    }

}


if (runCleanup && !readOnly) {

    FileUtils.deleteQuietly(new File(importDirectoryPath))
    FileUtils.deleteQuietly(new File(exportDirectoryPath))

    attachmentBeans.each {
        im.objectFacade.deleteAttachmentBean(it.attachmentBean.id)
    }


}