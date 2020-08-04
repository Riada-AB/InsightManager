import com.riadalabs.jira.plugins.insight.services.model.ObjectBean
import customRiadaLibraries.insightmanager.InsightManagerForScriptrunner
import org.apache.log4j.Level
import org.apache.log4j.Logger



boolean readOnly = true
String sourceIQL = "" //An IQL matching all the objects that are to be exported
int sourceSchemaId = 2 //The Object schema ID to export from
String exportDirectoryPath = ""  //Where should the exported attachments be placed

Logger log = Logger.getLogger("im.migrate.object.attachments")
log.setLevel(Level.ALL)

log.info("Starting migration of object attachments")
log.info("\tAttachments in scheme $sourceSchemaId from objects matching this IQL will be exported:" + sourceIQL)
log.info("\tThe exported files will be placed here: $exportDirectoryPath")


InsightManagerForScriptrunner im = new InsightManagerForScriptrunner()

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


