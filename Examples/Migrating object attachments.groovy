import com.riadalabs.jira.plugins.insight.services.model.AttachmentBean
import com.riadalabs.jira.plugins.insight.services.model.ObjectBean
import customRiadaLibraries.insightmanager.InsightManagerForScriptrunner
import org.apache.log4j.Level
import org.apache.log4j.Logger



boolean readOnly = false
String sourceIQL = "ObjectTypeId = 97" //An IQL matching all the objects that are to be exported
int sourceSchemaId = 8 //The Object schema ID to export from
String exportDirectoryPath = "/opt/atlassian/jiraim84/temp/migrateAttachments"  //Where should the exported attachments be placed


//tail -F /var/atlassian/application-data/jiraim8*/log/atlassian-jira.log | sed -n -e 's/^.*attachments]//p'
Logger log = Logger.getLogger("im.migrate.object.attachments")
log.setLevel(Level.ALL)

log.info("Starting migration of object attachments")
log.info("\tAttachments in scheme $sourceSchemaId from objects matching this IQL will be exported:" + sourceIQL)
log.info("\tThe exported files will be placed here: $exportDirectoryPath")


InsightManagerForScriptrunner im = new InsightManagerForScriptrunner()
im.readOnly = readOnly
im.log.setLevel(Level.ALL)

ArrayList<ObjectBean>exportObjects = im.iql(sourceSchemaId, sourceIQL)

log.debug("\tAttachments from ${exportObjects.size()} will be exported")

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



